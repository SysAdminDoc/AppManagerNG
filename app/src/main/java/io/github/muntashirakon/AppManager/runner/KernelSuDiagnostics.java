// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.ExportTextUtils;

/**
 * Active KernelSU diagnostics for the Settings -> Privileges health surface.
 */
public final class KernelSuDiagnostics {
    public static final String KERNELSU_PACKAGE = "me.weishu.kernelsu";
    public static final String KERNELSU_NEXT_PACKAGE = "com.rifsxd.ksunext";

    private static final int MAX_SULOG_LINES = 5;
    private static final int PR_GET_SECCOMP = 21;
    private static final int UNKNOWN_ID = -1;
    private static final int ROOT_UID = 0;
    private static final int ROOT_GID = 0;
    private static final ExpectedCapability[] EXPECTED_CAPABILITIES = new ExpectedCapability[]{
            new ExpectedCapability("CAP_CHOWN", 0),
            new ExpectedCapability("CAP_DAC_OVERRIDE", 1),
            new ExpectedCapability("CAP_DAC_READ_SEARCH", 2),
            new ExpectedCapability("CAP_FOWNER", 3),
            new ExpectedCapability("CAP_SETGID", 6),
            new ExpectedCapability("CAP_SETUID", 7),
            new ExpectedCapability("CAP_SYS_ADMIN", 21),
    };
    private static final String SULOG_PROBE_COMMAND =
            "echo APP_PROFILE_UID=\"$(id -u 2>/dev/null)\"; "
                    + "echo APP_PROFILE_GID=\"$(id -g 2>/dev/null)\"; "
                    + "echo APP_PROFILE_GROUPS=\"$(id -G 2>/dev/null)\"; "
                    + "echo APP_PROFILE_ID=\"$(id 2>/dev/null)\"; "
                    + "echo APP_PROFILE_CONTEXT=\"$(id -Z 2>/dev/null || cat /proc/$$/attr/current 2>/dev/null)\"; "
                    + "grep '^CapEff:' /proc/$$/status 2>/dev/null "
                    + "| sed 's/^CapEff:[[:space:]]*/APP_PROFILE_CAPEFF=/'; "
                    + "if [ -r /data/adb/ksu/log/sulog ]; then "
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

    public enum AppProfileState {
        UNAVAILABLE,
        DEFAULT_ROOT,
        RESTRICTED,
        UNKNOWN,
    }

    public enum RecoveryAction {
        NONE,
        REQUEST_ROOT_GRANT,
        REVIEW_APP_PROFILE,
    }

    public static final class ExpectedCapability {
        @NonNull
        public final String name;
        public final int bit;

        private ExpectedCapability(@NonNull String name, int bit) {
            this.name = name;
            this.bit = bit;
        }
    }

    public static final class AppProfile {
        @NonNull
        public final AppProfileState state;
        public final int uid;
        public final int gid;
        @NonNull
        public final List<Integer> groups;
        @Nullable
        public final String selinuxContext;
        @Nullable
        public final String capEff;
        @Nullable
        public final String rawId;
        @NonNull
        public final List<String> missingExpectedCapabilities;

        private AppProfile(@NonNull AppProfileState state, int uid, int gid, @NonNull List<Integer> groups,
                           @Nullable String selinuxContext, @Nullable String capEff, @Nullable String rawId,
                           @NonNull List<String> missingExpectedCapabilities) {
            this.state = state;
            this.uid = uid;
            this.gid = gid;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.selinuxContext = selinuxContext;
            this.capEff = capEff;
            this.rawId = rawId;
            this.missingExpectedCapabilities = Collections.unmodifiableList(
                    new ArrayList<>(missingExpectedCapabilities));
        }

        @NonNull
        private static AppProfile unavailable() {
            return new AppProfile(AppProfileState.UNAVAILABLE, UNKNOWN_ID, UNKNOWN_ID, Collections.emptyList(),
                    null, null, null, Collections.emptyList());
        }
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
        @NonNull
        public final AppProfile appProfile;
        @NonNull
        public final RecoveryAction recoveryAction;
        @Nullable
        public final String error;

        private Result(@NonNull State state, @NonNull RootManagerInfo.Source source,
                       @Nullable String seccompMode, @NonNull SulogState sulogState,
                       @NonNull List<String> sulogDenials, @NonNull AppProfile appProfile,
                       @Nullable String error) {
            this.state = state;
            this.source = source;
            this.seccompMode = seccompMode;
            this.sulogState = sulogState;
            this.sulogDenials = Collections.unmodifiableList(new ArrayList<>(sulogDenials));
            this.appProfile = appProfile;
            this.recoveryAction = getRecoveryAction(state, source, appProfile);
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
                    Collections.emptyList(), AppProfile.unavailable(), null);
        }
        if (info.source != RootManagerInfo.Source.MARKER) {
            return new Result(State.UNAVAILABLE, info.source, null, SulogState.UNAVAILABLE,
                    Collections.emptyList(), AppProfile.unavailable(), "KernelSU detected by package only");
        }
        String seccompMode = getCurrentProcessSeccompMode();
        Runner.Result commandResult = Runner.runCommand(SULOG_PROBE_COMMAND);
        if (!commandResult.isSuccessful()) {
            return new Result(State.UNKNOWN, info.source, seccompMode, SulogState.UNAVAILABLE,
                    Collections.emptyList(), AppProfile.unavailable(), "sulog exit " + commandResult.getExitCode());
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
        int profileUid = UNKNOWN_ID;
        int profileGid = UNKNOWN_ID;
        List<Integer> profileGroups = Collections.emptyList();
        String profileContext = null;
        String profileCapEff = null;
        String profileRawId = null;
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
            } else if (trimmed.startsWith("APP_PROFILE_UID=")) {
                profileUid = parseId(trimmed.substring("APP_PROFILE_UID=".length()));
            } else if (trimmed.startsWith("APP_PROFILE_GID=")) {
                profileGid = parseId(trimmed.substring("APP_PROFILE_GID=".length()));
            } else if (trimmed.startsWith("APP_PROFILE_GROUPS=")) {
                profileGroups = parseGroups(trimmed.substring("APP_PROFILE_GROUPS=".length()));
            } else if (trimmed.startsWith("APP_PROFILE_CONTEXT=")) {
                profileContext = normalize(trimmed.substring("APP_PROFILE_CONTEXT=".length()));
            } else if (trimmed.startsWith("APP_PROFILE_CAPEFF=")) {
                profileCapEff = normalizeHex(trimmed.substring("APP_PROFILE_CAPEFF=".length()));
            } else if (trimmed.startsWith("APP_PROFILE_ID=")) {
                profileRawId = normalize(trimmed.substring("APP_PROFILE_ID=".length()));
            }
        }
        AppProfile appProfile = buildAppProfile(profileUid, profileGid, profileGroups, profileContext,
                profileCapEff, profileRawId);
        if (seccompMode == null) {
            return new Result(State.UNKNOWN, source, null, sulogState, denials, appProfile, "missing Seccomp");
        }
        return new Result(State.ACTIVE, source, seccompMode, sulogState, denials, appProfile, null);
    }

    @NonNull
    private static RecoveryAction getRecoveryAction(@NonNull State state,
                                                    @NonNull RootManagerInfo.Source source,
                                                    @NonNull AppProfile appProfile) {
        if (state == State.UNAVAILABLE && source == RootManagerInfo.Source.PACKAGE) {
            return RecoveryAction.REQUEST_ROOT_GRANT;
        }
        if (state == State.UNKNOWN) {
            return RecoveryAction.REQUEST_ROOT_GRANT;
        }
        if (state == State.ACTIVE && (appProfile.state == AppProfileState.RESTRICTED
                || appProfile.state == AppProfileState.UNKNOWN)) {
            return RecoveryAction.REVIEW_APP_PROFILE;
        }
        return RecoveryAction.NONE;
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

    @VisibleForTesting
    @NonNull
    public static String sanitizeReportText(@Nullable String reportText) {
        return ExportTextUtils.toPlainTextReport(reportText);
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
    private static AppProfile buildAppProfile(int uid, int gid, @NonNull List<Integer> groups,
                                              @Nullable String selinuxContext, @Nullable String capEff,
                                              @Nullable String rawId) {
        if (uid == UNKNOWN_ID && gid == UNKNOWN_ID && groups.isEmpty() && selinuxContext == null
                && capEff == null && rawId == null) {
            return AppProfile.unavailable();
        }
        if (uid == UNKNOWN_ID || gid == UNKNOWN_ID || capEff == null) {
            return new AppProfile(AppProfileState.UNKNOWN, uid, gid, groups, selinuxContext, capEff, rawId,
                    Collections.emptyList());
        }
        List<String> missing = getMissingExpectedCapabilities(capEff);
        AppProfileState state = uid == ROOT_UID && gid == ROOT_GID && missing.isEmpty()
                ? AppProfileState.DEFAULT_ROOT
                : AppProfileState.RESTRICTED;
        return new AppProfile(state, uid, gid, groups, selinuxContext, capEff, rawId, missing);
    }

    @VisibleForTesting
    @NonNull
    static List<String> getMissingExpectedCapabilities(@Nullable String capEff) {
        if (capEff == null) {
            return Arrays.asList(expectedCapabilityNames());
        }
        BigInteger effectiveCaps;
        try {
            effectiveCaps = new BigInteger(capEff, 16);
        } catch (NumberFormatException e) {
            return Arrays.asList(expectedCapabilityNames());
        }
        List<String> missing = new ArrayList<>();
        for (ExpectedCapability capability : EXPECTED_CAPABILITIES) {
            if (!effectiveCaps.testBit(capability.bit)) {
                missing.add(capability.name);
            }
        }
        return missing;
    }

    @NonNull
    private static String[] expectedCapabilityNames() {
        String[] names = new String[EXPECTED_CAPABILITIES.length];
        for (int i = 0; i < EXPECTED_CAPABILITIES.length; ++i) {
            names[i] = EXPECTED_CAPABILITIES[i].name;
        }
        return names;
    }

    private static int parseId(@NonNull String rawId) {
        try {
            return Integer.parseInt(rawId.trim());
        } catch (NumberFormatException e) {
            return UNKNOWN_ID;
        }
    }

    @NonNull
    private static List<Integer> parseGroups(@NonNull String rawGroups) {
        String[] parts = rawGroups.trim().split("\\s+");
        Set<Integer> groups = new HashSet<>();
        for (String part : parts) {
            int group = parseId(part);
            if (group != UNKNOWN_ID) {
                groups.add(group);
            }
        }
        List<Integer> sortedGroups = new ArrayList<>(groups);
        Collections.sort(sortedGroups);
        return sortedGroups;
    }

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
