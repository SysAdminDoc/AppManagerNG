// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ExportTextUtils;

/**
 * Read-only module inventory for Magisk/MMRL-style modules and LSPosed module
 * metadata. The probe deliberately reads only module.prop files and local state
 * markers; it does not mutate module state or call any manager-specific APIs.
 */
public final class RootModuleInfo {
    private static final String MODULE_DELIMITER = "__AM_MODULE__";
    private static final String PROBE_COMMAND =
            "emit_module_prop() { "
                    + "p=\"$1\"; source=\"$2\"; dir=\"${p%/module.prop}\"; "
                    + "[ -r \"$p\" ] || return; "
                    + "echo " + MODULE_DELIMITER + "; "
                    + "echo source=\"$source\"; "
                    + "echo path=\"$p\"; "
                    + "if [ -f \"$dir/remove\" ]; then echo status=remove-pending; "
                    + "elif [ -f \"$dir/disable\" ]; then echo status=disabled; "
                    + "else echo status=active; fi; "
                    + "while IFS= read -r line; do "
                    + "case \"$line\" in "
                    + "id=*|name=*|version=*|versionCode=*|author=*|description=*) "
                    + "printf '%s\\n' \"$line\" ;; "
                    + "esac; "
                    + "done < \"$p\"; "
                    + "}; "
                    + "for p in /data/adb/modules/*/module.prop; do "
                    + "[ -e \"$p\" ] && emit_module_prop \"$p\" magisk; "
                    + "done; "
                    + "if [ -d /data/adb/lspd ]; then "
                    + "find /data/adb/lspd -maxdepth 4 -type f -name module.prop 2>/dev/null "
                    + "| while IFS= read -r p; do emit_module_prop \"$p\" lsposed; done; "
                    + "fi";

    public enum State {
        UNAVAILABLE,
        EMPTY,
        ACTIVE,
        UNKNOWN,
    }

    public static final class Module {
        @NonNull
        public final String id;
        @NonNull
        public final String name;
        @Nullable
        public final String version;
        @Nullable
        public final String versionCode;
        @Nullable
        public final String author;
        @Nullable
        public final String description;
        @NonNull
        public final String source;
        @NonNull
        public final String status;
        @Nullable
        public final String path;

        private Module(@NonNull String id, @NonNull String name, @Nullable String version,
                       @Nullable String versionCode, @Nullable String author,
                       @Nullable String description, @NonNull String source,
                       @NonNull String status, @Nullable String path) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.versionCode = versionCode;
            this.author = author;
            this.description = description;
            this.source = source;
            this.status = status;
            this.path = path;
        }

        @NonNull
        public String label() {
            return isBlank(name) ? id : name;
        }

        @NonNull
        public String summaryLine() {
            StringBuilder line = new StringBuilder(label());
            if (!isBlank(version)) {
                line.append(" ").append(version);
            }
            line.append(" (").append(source);
            if (!"active".equals(status)) {
                line.append(", ").append(status);
            }
            line.append(")");
            return line.toString();
        }
    }

    public static final class Result {
        @NonNull
        public final State state;
        @NonNull
        public final List<Module> modules;
        @Nullable
        public final String error;

        private Result(@NonNull State state, @NonNull List<Module> modules, @Nullable String error) {
            this.state = state;
            this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
            this.error = error;
        }
    }

    private RootModuleInfo() {
    }

    @WorkerThread
    @NonNull
    public static Result probe() {
        if (!privilegedShellAvailable()) {
            return new Result(State.UNAVAILABLE, Collections.emptyList(), null);
        }
        Runner.Result result = Runner.runCommand(PROBE_COMMAND);
        if (!result.isSuccessful()) {
            return new Result(State.UNKNOWN, Collections.emptyList(), result.getOutput());
        }
        return parseProbeOutput(result.getOutputAsList());
    }

    @NonNull
    public static Result parseProbeOutput(@NonNull List<String> lines) {
        List<Module> modules = new ArrayList<>();
        MutableModule current = null;
        for (String rawLine : lines) {
            String line = rawLine != null ? rawLine.trim() : "";
            if (MODULE_DELIMITER.equals(line)) {
                addIfValid(modules, current);
                current = new MutableModule();
                continue;
            }
            if (current == null || line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1).trim();
            switch (key) {
                case "id":
                    current.id = value;
                    break;
                case "name":
                    current.name = value;
                    break;
                case "version":
                    current.version = value;
                    break;
                case "versionCode":
                    current.versionCode = value;
                    break;
                case "author":
                    current.author = value;
                    break;
                case "description":
                    current.description = value;
                    break;
                case "source":
                    current.source = normalizeSource(value);
                    break;
                case "status":
                    current.status = normalizeStatus(value);
                    break;
                case "path":
                    current.path = value;
                    break;
                default:
                    break;
            }
        }
        addIfValid(modules, current);
        return new Result(modules.isEmpty() ? State.EMPTY : State.ACTIVE, modules, null);
    }

    @NonNull
    public static String formatForDisplay(@NonNull List<Module> modules) {
        StringBuilder message = new StringBuilder();
        for (Module module : modules) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append(module.summaryLine());
            if (!isBlank(module.author)) {
                message.append("\nAuthor: ").append(module.author);
            }
            if (!isBlank(module.versionCode)) {
                message.append("\nVersion code: ").append(module.versionCode);
            }
            if (!isBlank(module.description)) {
                message.append("\n").append(module.description);
            }
            if (!isBlank(module.path)) {
                message.append("\n").append(module.path);
            }
        }
        return ExportTextUtils.toPlainTextReport(message.toString());
    }

    private static boolean privilegedShellAvailable() {
        return Ops.isDirectRoot() || LocalServices.alive();
    }

    private static void addIfValid(@NonNull List<Module> modules, @Nullable MutableModule current) {
        if (current == null) {
            return;
        }
        String id = !isBlank(current.id) ? current.id : inferIdFromPath(current.path);
        String name = !isBlank(current.name) ? current.name : id;
        if (isBlank(id) && isBlank(name)) {
            return;
        }
        modules.add(new Module(
                !isBlank(id) ? id : name,
                !isBlank(name) ? name : id,
                blankToNull(current.version),
                blankToNull(current.versionCode),
                blankToNull(current.author),
                blankToNull(current.description),
                !isBlank(current.source) ? current.source : "module",
                !isBlank(current.status) ? current.status : "active",
                blankToNull(current.path)));
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @Nullable
    private static String inferIdFromPath(@Nullable String path) {
        if (isBlank(path)) {
            return null;
        }
        String normalized = path;
        if (normalized.endsWith("/module.prop")) {
            normalized = normalized.substring(0, normalized.length() - "/module.prop".length());
        }
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    @NonNull
    private static String normalizeSource(@Nullable String value) {
        if (isBlank(value)) {
            return "module";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("magisk".equals(normalized)) {
            return "Magisk/MMRL";
        }
        if ("lsposed".equals(normalized)) {
            return "LSPosed";
        }
        return value.trim();
    }

    @NonNull
    private static String normalizeStatus(@Nullable String value) {
        if (isBlank(value)) {
            return "active";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class MutableModule {
        @Nullable
        String id;
        @Nullable
        String name;
        @Nullable
        String version;
        @Nullable
        String versionCode;
        @Nullable
        String author;
        @Nullable
        String description;
        @Nullable
        String source;
        @Nullable
        String status;
        @Nullable
        String path;
    }
}
