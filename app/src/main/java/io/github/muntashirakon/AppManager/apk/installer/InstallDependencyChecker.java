// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static helpers that detect install-blocking dependencies before AppManagerNG hands the package to
 * the system {@code PackageInstaller}. Today's coverage is intentionally narrow — only the
 * minSdkVersion check, which is the single most common silent install failure
 * ({@code INSTALL_FAILED_OLDER_SDK}). The class is structured so additional checks (ABI mismatch,
 * required {@code <uses-library>}, target-SDK gating) can be added as separate static helpers
 * without changing the call site in {@link PackageInstallerActivity}.
 *
 * <p>Pure Java; no Android imports so the logic is JVM-testable. See
 * {@code app/src/test/java/io/github/muntashirakon/AppManager/apk/installer/InstallDependencyCheckerTest.java}.
 *
 * <p>Shipped under ROADMAP iter-23 / T4 / Next row "Installer Dependency Resolver".
 */
public final class InstallDependencyChecker {
    private InstallDependencyChecker() {}

    /**
     * Kinds of install-blocking issues. The UI layer maps each kind to a localized warning string.
     */
    public enum IssueKind {
        MIN_SDK_TOO_HIGH,
        MISSING_SHARED_LIBRARY,
    }

    /**
     * One detected install-blocking issue. {@link #kind} identifies the failure mode; {@link
     * #requiredVersion} / {@link #actualVersion} are populated for SDK-style mismatches so the UI
     * can render a "Requires Android X — your device is on Y" message without re-running the
     * check. {@link #missingNames} carries any string-list payload (e.g., missing shared-library
     * names) so the UI does not have to re-run the check to render names.
     */
    public static final class Issue {
        @NonNull
        public final IssueKind kind;
        public final int requiredVersion;
        public final int actualVersion;
        @NonNull
        public final List<String> missingNames;

        Issue(@NonNull IssueKind kind, int requiredVersion, int actualVersion) {
            this(kind, requiredVersion, actualVersion, Collections.emptyList());
        }

        Issue(@NonNull IssueKind kind, int requiredVersion, int actualVersion,
              @NonNull List<String> missingNames) {
            this.kind = kind;
            this.requiredVersion = requiredVersion;
            this.actualVersion = actualVersion;
            this.missingNames = Collections.unmodifiableList(new ArrayList<>(missingNames));
        }
    }

    /**
     * Aggregate every check into a single list. Callers should treat the list as a "warn before
     * commit" surface — even if every check passes, the system installer may still refuse the
     * package for reasons NG cannot see ahead of time.
     *
     * @param apkMinSdk           the APK's declared {@code minSdkVersion}, or {@code 0} when
     *                            unknown (e.g., pre-API-24 devices where {@code
     *                            applicationInfo.minSdkVersion} is not populated).
     * @param deviceApi           {@link android.os.Build.VERSION#SDK_INT} at runtime.
     * @param requiredLibraries   the {@code <uses-library>} entries declared by the APK; {@code
     *                            null} when the platform did not surface the data (pre-API-26).
     * @param installedLibraries  the names returned by {@code PackageManager
     *                            .getSystemSharedLibraryNames()}; {@code null} skips the check.
     * @return zero or more {@link Issue}s ordered minSdk → missing-libraries.
     */
    @NonNull
    public static List<Issue> check(int apkMinSdk, int deviceApi,
                                    @Nullable Collection<String> requiredLibraries,
                                    @Nullable Collection<String> installedLibraries) {
        List<Issue> issues = new ArrayList<>(2);
        Issue minSdkIssue = checkMinSdk(apkMinSdk, deviceApi);
        if (minSdkIssue != null) {
            issues.add(minSdkIssue);
        }
        Issue libraryIssue = checkSharedLibraries(requiredLibraries, installedLibraries);
        if (libraryIssue != null) {
            issues.add(libraryIssue);
        }
        return issues;
    }

    /**
     * Convenience overload for callers that have no shared-library information yet — equivalent to
     * passing {@code null}, {@code null} for the library inputs.
     */
    @NonNull
    public static List<Issue> check(int apkMinSdk, int deviceApi) {
        return check(apkMinSdk, deviceApi, null, null);
    }

    /**
     * @return an {@link Issue} when {@code apkMinSdk > deviceApi}; {@code null} when the device
     *         meets the floor, when the APK's minSdk is unknown, or when {@code apkMinSdk} is
     *         non-positive (treated as "not declared").
     */
    @Nullable
    public static Issue checkMinSdk(int apkMinSdk, int deviceApi) {
        if (apkMinSdk <= 0 || deviceApi <= 0) {
            return null;
        }
        if (apkMinSdk > deviceApi) {
            return new Issue(IssueKind.MIN_SDK_TOO_HIGH, apkMinSdk, deviceApi);
        }
        return null;
    }

    /**
     * @return an {@link Issue} listing every required {@code <uses-library>} that does not appear
     *         in the device's installed shared-library set; {@code null} when every required
     *         library is present, when there are no requirements, or when either input is
     *         unavailable (in which case the check is skipped — the system installer will surface
     *         the failure post-commit, just without the pre-flight warning).
     *         Required-library names are de-duplicated and trimmed before comparison; empty names
     *         are ignored.
     */
    @Nullable
    public static Issue checkSharedLibraries(@Nullable Collection<String> requiredLibraries,
                                             @Nullable Collection<String> installedLibraries) {
        if (requiredLibraries == null || requiredLibraries.isEmpty() || installedLibraries == null) {
            return null;
        }
        Set<String> installed = new HashSet<>(installedLibraries);
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String required : requiredLibraries) {
            if (required == null) continue;
            String trimmed = required.trim();
            if (trimmed.isEmpty() || !seen.add(trimmed)) continue;
            if (!installed.contains(trimmed)) {
                missing.add(trimmed);
            }
        }
        if (missing.isEmpty()) return null;
        return new Issue(IssueKind.MISSING_SHARED_LIBRARY, 0, 0, missing);
    }

    /**
     * Compact comma-separated rendering of {@link Issue#missingNames}, for use in single-line UI
     * callouts. Caller is responsible for localization of any preceding label.
     */
    @NonNull
    public static String joinMissingNames(@NonNull List<String> missingNames) {
        if (missingNames.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missingNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(missingNames.get(i));
        }
        return sb.toString();
    }

    /** Test helper that wraps a varargs string list — keeps callers terse. */
    @NonNull
    static List<String> namesList(@NonNull String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }
}
