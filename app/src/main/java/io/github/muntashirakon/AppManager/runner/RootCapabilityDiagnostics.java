// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Reads the active privileged shell's Linux capability state from procfs.
 *
 * <p>This intentionally probes the shell AppManagerNG is about to use for
 * privileged work instead of parsing a root manager's private config format.
 * Magisk/KernelSU/APatch policy names and storage locations vary; CapEff is the
 * kernel-owned result that matters for the current session.</p>
 */
public final class RootCapabilityDiagnostics {
    private static final int ROOT_UID = 0;
    private static final int UNKNOWN_UID = -1;
    private static final int MAGISK_DROP_CAP_OPT_IN_VERSION_CODE = 30007;
    private static final String PROBE_COMMAND =
            "echo UID=$(id -u 2>/dev/null); "
                    + "grep '^CapEff:' /proc/$$/status 2>/dev/null; "
                    + "AM_MAGISK_BIN=\"$(command -v magisk 2>/dev/null)\"; "
                    + "if [ -z \"$AM_MAGISK_BIN\" ] && [ -x /data/adb/magisk/magisk ]; "
                    + "then AM_MAGISK_BIN=/data/adb/magisk/magisk; fi; "
                    + "if [ -n \"$AM_MAGISK_BIN\" ]; then "
                    + "echo MAGISK_VERSION=\"$($AM_MAGISK_BIN -v 2>/dev/null)\"; "
                    + "echo MAGISK_VERSION_CODE=\"$($AM_MAGISK_BIN -V 2>/dev/null)\"; "
                    + "fi; "
                    + "AM_MAGISK_POLICY_BIN=\"$(command -v magiskpolicy 2>/dev/null)\"; "
                    + "if [ -z \"$AM_MAGISK_POLICY_BIN\" ] && [ -x /data/adb/magisk/magiskpolicy ]; "
                    + "then AM_MAGISK_POLICY_BIN=/data/adb/magisk/magiskpolicy; fi; "
                    + "if [ -n \"$AM_MAGISK_POLICY_BIN\" ]; then "
                    + "if $AM_MAGISK_POLICY_BIN --live --print-rules >/dev/null 2>&1; then "
                    + "echo MAGISKPOLICY_STATUS=available; "
                    + "$AM_MAGISK_POLICY_BIN --live --print-rules 2>/dev/null "
                    + "| grep -E '(^allow magisk | magisk .*cap|capability|drop[-_ ]?cap)' "
                    + "| head -n 12 "
                    + "| while IFS= read -r line; do echo MAGISKPOLICY_RULE=\"$line\"; done; "
                    + "else echo MAGISKPOLICY_STATUS=unavailable; fi; "
                    + "else echo MAGISKPOLICY_STATUS=missing; fi";

    public enum State {
        UNAVAILABLE,
        ROOT,
        DROPPED,
        PRESENT,
        UNKNOWN,
    }

    public enum MagiskPolicyState {
        NOT_MAGISK,
        UNAVAILABLE,
        NO_MATCH,
        MATCHED,
    }

    public static final class Result {
        @NonNull
        public final State state;
        public final int uid;
        @Nullable
        public final String capEff;
        @Nullable
        public final String error;
        @Nullable
        public final String magiskVersion;
        @Nullable
        public final String magiskVersionCode;
        @NonNull
        public final MagiskPolicyState magiskPolicyState;
        @NonNull
        public final List<String> magiskPolicyRules;

        private Result(@NonNull State state, int uid, @Nullable String capEff, @Nullable String error,
                       @Nullable String magiskVersion, @Nullable String magiskVersionCode,
                       @NonNull MagiskPolicyState magiskPolicyState,
                       @NonNull List<String> magiskPolicyRules) {
            this.state = state;
            this.uid = uid;
            this.capEff = capEff;
            this.error = error;
            this.magiskVersion = magiskVersion;
            this.magiskVersionCode = magiskVersionCode;
            this.magiskPolicyState = magiskPolicyState;
            this.magiskPolicyRules = Collections.unmodifiableList(new ArrayList<>(magiskPolicyRules));
        }

        public boolean isMagiskDropCapOptInVersion() {
            return RootCapabilityDiagnostics.isMagiskDropCapOptInVersion(magiskVersionCode);
        }
    }

    private RootCapabilityDiagnostics() {
    }

    @WorkerThread
    @NonNull
    public static Result probe() {
        if (!isPrivilegedShellAvailable()) {
            return new Result(State.UNAVAILABLE, UNKNOWN_UID, null, null, null, null,
                    MagiskPolicyState.NOT_MAGISK, Collections.emptyList());
        }
        Runner.Result result = Runner.runCommand(PROBE_COMMAND);
        if (!result.isSuccessful()) {
            return new Result(State.UNKNOWN, UNKNOWN_UID, null, "exit " + result.getExitCode(), null, null,
                    MagiskPolicyState.NOT_MAGISK, Collections.emptyList());
        }
        return parseProbeOutput(result.getOutputAsList());
    }

    @VisibleForTesting
    @NonNull
    static Result parseProbeOutput(@NonNull List<String> output) {
        int uid = UNKNOWN_UID;
        String capEff = null;
        String magiskVersion = null;
        String magiskVersionCode = null;
        boolean magiskPolicyAvailable = false;
        List<String> magiskPolicyRules = new ArrayList<>();
        for (String line : output) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.startsWith("UID=")) {
                uid = parseUid(trimmed.substring(4));
            } else if (trimmed.startsWith("CapEff:")) {
                capEff = normalizeHex(trimmed.substring("CapEff:".length()));
            } else if (trimmed.startsWith("MAGISK_VERSION=")) {
                magiskVersion = normalizeString(trimmed.substring("MAGISK_VERSION=".length()));
            } else if (trimmed.startsWith("MAGISK_VERSION_CODE=")) {
                magiskVersionCode = normalizeString(trimmed.substring("MAGISK_VERSION_CODE=".length()));
            } else if (trimmed.startsWith("MAGISKPOLICY_STATUS=")) {
                magiskPolicyAvailable = "available".equals(trimmed.substring("MAGISKPOLICY_STATUS=".length()));
            } else if (trimmed.startsWith("MAGISKPOLICY_RULE=")) {
                String rule = normalizeString(trimmed.substring("MAGISKPOLICY_RULE=".length()));
                if (rule != null) {
                    magiskPolicyRules.add(rule);
                }
            }
        }
        MagiskPolicyState magiskPolicyState = classifyMagiskPolicyState(magiskVersion, magiskVersionCode,
                magiskPolicyAvailable, magiskPolicyRules);
        if (uid == UNKNOWN_UID || capEff == null) {
            return new Result(State.UNKNOWN, uid, capEff, "missing UID or CapEff",
                    magiskVersion, magiskVersionCode, magiskPolicyState, magiskPolicyRules);
        }
        if (uid == ROOT_UID) {
            return new Result(State.ROOT, uid, capEff, null,
                    magiskVersion, magiskVersionCode, magiskPolicyState, magiskPolicyRules);
        }
        return new Result(isAllZeroHex(capEff) ? State.DROPPED : State.PRESENT, uid, capEff, null,
                magiskVersion, magiskVersionCode, magiskPolicyState, magiskPolicyRules);
    }

    @AnyThread
    private static boolean isPrivilegedShellAvailable() {
        return Ops.isDirectRoot() || LocalServices.alive();
    }

    @AnyThread
    private static int parseUid(@NonNull String rawUid) {
        try {
            return Integer.parseInt(rawUid.trim());
        } catch (NumberFormatException e) {
            return UNKNOWN_UID;
        }
    }

    @AnyThread
    @VisibleForTesting
    static boolean isMagiskDropCapOptInVersion(@Nullable String rawVersionCode) {
        if (rawVersionCode == null) {
            return false;
        }
        try {
            return Integer.parseInt(rawVersionCode.trim()) >= MAGISK_DROP_CAP_OPT_IN_VERSION_CODE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @AnyThread
    @NonNull
    private static MagiskPolicyState classifyMagiskPolicyState(@Nullable String magiskVersion,
                                                               @Nullable String magiskVersionCode,
                                                               boolean magiskPolicyAvailable,
                                                               @NonNull List<String> magiskPolicyRules) {
        if (magiskVersion == null && magiskVersionCode == null && !magiskPolicyAvailable) {
            return MagiskPolicyState.NOT_MAGISK;
        }
        if (!magiskPolicyAvailable) {
            return MagiskPolicyState.UNAVAILABLE;
        }
        return magiskPolicyRules.isEmpty() ? MagiskPolicyState.NO_MATCH : MagiskPolicyState.MATCHED;
    }

    @AnyThread
    @Nullable
    private static String normalizeString(@NonNull String rawValue) {
        String value = rawValue.trim();
        return value.isEmpty() ? null : value;
    }

    @AnyThread
    @Nullable
    private static String normalizeHex(@NonNull String rawHex) {
        String[] parts = rawHex.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        String hex = parts[0];
        for (int i = 0; i < hex.length(); ++i) {
            char ch = hex.charAt(i);
            boolean digit = ch >= '0' && ch <= '9';
            boolean lower = ch >= 'a' && ch <= 'f';
            boolean upper = ch >= 'A' && ch <= 'F';
            if (!digit && !lower && !upper) {
                return null;
            }
        }
        return hex.toLowerCase(Locale.ROOT);
    }

    @AnyThread
    private static boolean isAllZeroHex(@NonNull String hex) {
        for (int i = 0; i < hex.length(); ++i) {
            if (hex.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
