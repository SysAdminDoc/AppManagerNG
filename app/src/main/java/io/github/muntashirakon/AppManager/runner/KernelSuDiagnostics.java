// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Active KernelSU diagnostics for the Settings -> Privileges health surface.
 */
public final class KernelSuDiagnostics {
    public static final String KERNELSU_PACKAGE = "me.weishu.kernelsu";
    public static final String KERNELSU_NEXT_PACKAGE = "com.rifsxd.ksunext";

    private static final int MAX_SULOG_LINES = 5;
    private static final int PR_GET_SECCOMP = 21;
    private static final String SULOG_PROBE_COMMAND =
            "if [ -r /data/adb/ksu/log/sulog ]; then "
                    + "echo SULOG_STATUS=readable; "
                    + "tail -n 80 /data/adb/ksu/log/sulog 2>/dev/null "
                    + "| grep -i -E 'deny|denied|reject|fail|avc' "
                    + "| tail -n " + MAX_SULOG_LINES + " "
                    + "| while IFS= read -r line; do echo SULOG_LINE=\"$line\"; done; "
                    + "else echo SULOG_STATUS=missing; fi";

    public enum State {
        NOT_KERNELSU,
        UNAVAILABLE,
        ACTIVE,
        UNKNOWN,
    }

    public enum SulogState {
        UNAVAILABLE,
        READABLE,
        MISSING,
    }

    public static final class Result {
        @NonNull
        public final State state;
        @NonNull
        public final RootManagerInfo.Source source;
        @Nullable
        public final String seccompMode;
        @NonNull
        public final SulogState sulogState;
        @NonNull
        public final List<String> sulogDenials;
        @Nullable
        public final String error;

        private Result(@NonNull State state, @NonNull RootManagerInfo.Source source,
                       @Nullable String seccompMode, @NonNull SulogState sulogState,
                       @NonNull List<String> sulogDenials, @Nullable String error) {
            this.state = state;
            this.source = source;
            this.seccompMode = seccompMode;
            this.sulogState = sulogState;
            this.sulogDenials = Collections.unmodifiableList(new ArrayList<>(sulogDenials));
            this.error = error;
        }
    }

    private KernelSuDiagnostics() {
    }

    @WorkerThread
    @NonNull
    public static Result probe(@NonNull Context context) {
        RootManagerInfo info = RootManagerInfo.detect(context.getApplicationContext());
        if (info.manager != RootManagerInfo.Manager.KERNELSU) {
            return new Result(State.NOT_KERNELSU, info.source, null, SulogState.UNAVAILABLE,
                    Collections.emptyList(), null);
        }
        if (info.source != RootManagerInfo.Source.MARKER) {
            return new Result(State.UNAVAILABLE, info.source, null, SulogState.UNAVAILABLE,
                    Collections.emptyList(), "KernelSU detected by package only");
        }
        String seccompMode = getCurrentProcessSeccompMode();
        Runner.Result commandResult = Runner.runCommand(SULOG_PROBE_COMMAND);
        if (!commandResult.isSuccessful()) {
            return new Result(State.UNKNOWN, info.source, seccompMode, SulogState.UNAVAILABLE,
                    Collections.emptyList(), "sulog exit " + commandResult.getExitCode());
        }
        return parseProbeOutput(commandResult.getOutputAsList(), info.source, seccompMode);
    }

    @VisibleForTesting
    @NonNull
    static Result parseProbeOutput(@NonNull List<String> output, @NonNull RootManagerInfo.Source source) {
        return parseProbeOutput(output, source, null);
    }

    @VisibleForTesting
    @NonNull
    static Result parseProbeOutput(@NonNull List<String> output, @NonNull RootManagerInfo.Source source,
                                   @Nullable String initialSeccompMode) {
        String seccompMode = initialSeccompMode;
        SulogState sulogState = SulogState.UNAVAILABLE;
        List<String> denials = new ArrayList<>();
        for (String line : output) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.startsWith("SECCOMP=")) {
                seccompMode = normalize(trimmed.substring("SECCOMP=".length()));
            } else if (trimmed.startsWith("SULOG_STATUS=")) {
                sulogState = parseSulogState(trimmed.substring("SULOG_STATUS=".length()));
            } else if (trimmed.startsWith("SULOG_LINE=")) {
                String denial = normalize(trimmed.substring("SULOG_LINE=".length()));
                if (denial != null) {
                    denials.add(denial);
                }
            }
        }
        if (seccompMode == null) {
            return new Result(State.UNKNOWN, source, null, sulogState, denials, "missing Seccomp");
        }
        return new Result(State.ACTIVE, source, seccompMode, sulogState, denials, null);
    }

    @VisibleForTesting
    @NonNull
    public static String formatSeccompMode(@Nullable String rawMode) {
        if (rawMode == null || rawMode.trim().isEmpty() || "unknown".equalsIgnoreCase(rawMode.trim())) {
            return "unknown";
        }
        switch (rawMode.trim()) {
            case "0":
                return "disabled (0)";
            case "1":
                return "strict (1)";
            case "2":
                return "filter (2)";
            default:
                return rawMode.trim();
        }
    }

    @Nullable
    private static String getCurrentProcessSeccompMode() {
        try {
            return String.valueOf(Os.prctl(PR_GET_SECCOMP, 0, 0, 0, 0));
        } catch (ErrnoException | RuntimeException e) {
            return null;
        }
    }

    @NonNull
    private static SulogState parseSulogState(@NonNull String rawState) {
        if ("readable".equals(rawState.trim())) {
            return SulogState.READABLE;
        }
        if ("missing".equals(rawState.trim())) {
            return SulogState.MISSING;
        }
        return SulogState.UNAVAILABLE;
    }

    @Nullable
    private static String normalize(@NonNull String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
