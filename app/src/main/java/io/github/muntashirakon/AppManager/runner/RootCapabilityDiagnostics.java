// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

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
    private static final String PROBE_COMMAND =
            "echo UID=$(id -u 2>/dev/null); grep '^CapEff:' /proc/$$/status 2>/dev/null";

    public enum State {
        UNAVAILABLE,
        ROOT,
        DROPPED,
        PRESENT,
        UNKNOWN,
    }

    public static final class Result {
        @NonNull
        public final State state;
        public final int uid;
        @Nullable
        public final String capEff;
        @Nullable
        public final String error;

        private Result(@NonNull State state, int uid, @Nullable String capEff, @Nullable String error) {
            this.state = state;
            this.uid = uid;
            this.capEff = capEff;
            this.error = error;
        }
    }

    private RootCapabilityDiagnostics() {
    }

    @WorkerThread
    @NonNull
    public static Result probe() {
        if (!isPrivilegedShellAvailable()) {
            return new Result(State.UNAVAILABLE, UNKNOWN_UID, null, null);
        }
        Runner.Result result = Runner.runCommand(PROBE_COMMAND);
        if (!result.isSuccessful()) {
            return new Result(State.UNKNOWN, UNKNOWN_UID, null, "exit " + result.getExitCode());
        }
        return parseProbeOutput(result.getOutputAsList());
    }

    @VisibleForTesting
    @NonNull
    static Result parseProbeOutput(@NonNull List<String> output) {
        int uid = UNKNOWN_UID;
        String capEff = null;
        for (String line : output) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.startsWith("UID=")) {
                uid = parseUid(trimmed.substring(4));
            } else if (trimmed.startsWith("CapEff:")) {
                capEff = normalizeHex(trimmed.substring("CapEff:".length()));
            }
        }
        if (uid == UNKNOWN_UID || capEff == null) {
            return new Result(State.UNKNOWN, uid, capEff, "missing UID or CapEff");
        }
        if (uid == ROOT_UID) {
            return new Result(State.ROOT, uid, capEff, null);
        }
        return new Result(isAllZeroHex(capEff) ? State.DROPPED : State.PRESENT, uid, capEff, null);
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
