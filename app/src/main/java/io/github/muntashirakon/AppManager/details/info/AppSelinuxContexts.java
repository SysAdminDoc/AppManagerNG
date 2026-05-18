// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.app.ActivityManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class AppSelinuxContexts {
    interface ProcessContextReader {
        @Nullable
        String read(int pid);
    }

    static final class ProcessContext {
        public final int pid;
        @NonNull
        public final String processName;
        @NonNull
        public final String context;

        ProcessContext(int pid, @NonNull String processName, @NonNull String context) {
            this.pid = pid;
            this.processName = processName;
            this.context = context;
        }
    }

    private AppSelinuxContexts() {
    }

    @NonNull
    static List<ProcessContext> collectProcessContexts(@NonNull String packageName,
                                                       @NonNull List<ActivityManager.RunningAppProcessInfo> processes,
                                                       @NonNull ProcessContextReader reader) {
        if (processes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessContext> contexts = new ArrayList<>();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (!belongsToPackage(packageName, process)) {
                continue;
            }
            String context = normalize(reader.read(process.pid));
            if (context == null) {
                continue;
            }
            String processName = process.processName != null ? process.processName : packageName;
            contexts.add(new ProcessContext(process.pid, processName, context));
        }
        return contexts;
    }

    @Nullable
    static String readProcAttrCurrent(int pid) {
        Path path = Paths.get("/proc/" + pid + "/attr/current");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(path.openInputStream(),
                StandardCharsets.UTF_8))) {
            return normalize(reader.readLine());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    static String readFileContext(@Nullable String filePath) {
        if (filePath == null) {
            return null;
        }
        try {
            ExtendedFile file = Paths.get(filePath).getFile();
            if (file == null) {
                return null;
            }
            return normalize(file.getSelinuxContext());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean belongsToPackage(@NonNull String packageName,
                                            @NonNull ActivityManager.RunningAppProcessInfo process) {
        if (ArrayUtils.contains(process.pkgList, packageName)) {
            return true;
        }
        return process.processName != null
                && (packageName.equals(process.processName) || process.processName.startsWith(packageName + ":"));
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return TextUtils.isEmpty(trimmed) ? null : trimmed;
    }
}
