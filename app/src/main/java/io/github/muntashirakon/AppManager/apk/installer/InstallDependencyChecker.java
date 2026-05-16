// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    }

    /**
     * One detected install-blocking issue. {@link #kind} identifies the failure mode; {@link
     * #requiredVersion} / {@link #actualVersion} are populated for SDK-style mismatches so the UI
     * can render a "Requires Android X — your device is on Y" message without re-running the
     * check.
     */
    public static final class Issue {
        @NonNull
        public final IssueKind kind;
        public final int requiredVersion;
        public final int actualVersion;

        Issue(@NonNull IssueKind kind, int requiredVersion, int actualVersion) {
            this.kind = kind;
            this.requiredVersion = requiredVersion;
            this.actualVersion = actualVersion;
        }
    }

    /**
     * Aggregate every check into a single list. Callers should treat the list as a "warn before
     * commit" surface — even if every check passes, the system installer may still refuse the
     * package for reasons NG cannot see ahead of time.
     *
     * @param apkMinSdk      the APK's declared {@code minSdkVersion}, or {@code 0} when unknown
     *                       (e.g., pre-API-24 devices where {@code applicationInfo.minSdkVersion}
     *                       is not populated).
     * @param deviceApi      {@link android.os.Build.VERSION#SDK_INT} at runtime.
     * @return zero or more {@link Issue}s ordered by severity (today: just minSdk if blocking).
     */
    @NonNull
    public static List<Issue> check(int apkMinSdk, int deviceApi) {
        List<Issue> issues = new ArrayList<>(1);
        Issue minSdkIssue = checkMinSdk(apkMinSdk, deviceApi);
        if (minSdkIssue != null) {
            issues.add(minSdkIssue);
        }
        return issues;
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
}
