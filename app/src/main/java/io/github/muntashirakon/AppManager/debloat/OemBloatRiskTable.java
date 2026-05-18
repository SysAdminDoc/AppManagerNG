// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.SystemProperties;

/**
 * OEM-specific known-bad table for the Debloater. Some packages that the
 * shared android-debloat-list ranks "Replace" or "Caution" actually crash-loop
 * a system surface (Settings → Mobile Networks, Phone, etc.) on a specific
 * vendor + OS combo — at which point a stronger, vendor-aware warning needs
 * to render on top of the standard removal-rating chip.
 *
 * <p>The table is intentionally conservative: each entry is a verified field
 * report (issue tracker, Reddit thread, vendor-policy doc), not a heuristic.
 * Generic "this looks Samsung-y" warnings stay on the upstream warning string;
 * this surface is reserved for "we know this exact package on this exact
 * device family broke for somebody."</p>
 *
 * <p>Match key is a triple of (package, manufacturer, vendor-OS-version) where
 * manufacturer comes from {@link Build#MANUFACTURER} (lower-cased) and the
 * vendor OS version is read from a vendor-specific system property:
 * <ul>
 *   <li>Samsung One UI: {@code ro.build.version.oneui} — encodes 80500 for
 *       One UI 8.5, 80000 for 8.0, 70100 for 7.1, etc.</li>
 *   <li>Xiaomi MIUI / HyperOS: {@code ro.miui.ui.version.code} (ints), or
 *       {@code ro.mi.os.version.name} on HyperOS (strings).</li>
 * </ul>
 * </p>
 *
 * <p>Rule resolution order:
 * <ol>
 *   <li>Exact (package, manufacturer, vendor-version) match — most specific.</li>
 *   <li>(package, manufacturer, *) match — applies on every vendor OS version.</li>
 * </ol>
 * </p>
 *
 * <p>References:
 * <ul>
 *   <li>UAD-NG #1394 — `com.samsung.android.smartsuggestions` crash-loops
 *       Settings → Mobile Networks on Galaxy A57 / One UI 8.5.</li>
 * </ul>
 * </p>
 */
public final class OemBloatRiskTable {
    /** Wildcard token for the vendor-OS-version slot in {@link Rule#matches}. */
    private static final String ANY = "*";
    private static final String PACKAGE_SAMSUNG_SMART_SUGGESTIONS = "com.samsung.android.smartsuggestions";
    private static final String PACKAGE_MIUI_CORE = "com.miui.core";
    private static final String[] MIUI_MANUFACTURERS = {"xiaomi", "redmi", "poco"};
    private static final String[] OPLUS_MANUFACTURERS = {"oppo", "oneplus", "realme", "oplus"};
    private static final String[] OPLUS_BUILD_TOKENS = {"oplus", "coloros", "oxygenos", "realme"};
    private static final String[] OPLUS_UNINSTALL_GUARDED_PACKAGES = {
            "com.coloros.safecenter",
            "com.coloros.sau",
            "com.coloros.sauhelper",
            "com.coloros.securitypermission",
            "com.oplus.appplatform",
            "com.oplus.apprecover",
            "com.oplus.customize.coreapp",
            "com.oplus.ocs",
            "com.oplus.oscenter",
            "com.oplus.ota",
            "com.oplus.romupdate",
            "com.oplus.safecenter",
            "com.oplus.securitypermission",
    };

    /** Known-bad rule, keyed by (manufacturer-lc, packageName, vendorOsVersion). */
    private static final class Rule {
        final String manufacturerLc;
        final String packageName;
        /** Equal to {@link #ANY} when the rule applies regardless of vendor OS version. */
        final String vendorOsVersion;
        final int messageRes;

        Rule(String manufacturerLc, String packageName, String vendorOsVersion, int messageRes) {
            this.manufacturerLc = manufacturerLc;
            this.packageName = packageName;
            this.vendorOsVersion = vendorOsVersion;
            this.messageRes = messageRes;
        }

        boolean matches(@NonNull String mfrLc, @NonNull String pkg, @NonNull String version) {
            return manufacturerLc.equals(mfrLc)
                    && packageName.equals(pkg)
                    && (ANY.equals(vendorOsVersion) || vendorOsVersion.equals(version));
        }
    }

    private static final Map<String, Rule[]> RULES = buildRules();

    public static final class UninstallFallback {
        @StringRes
        public final int messageRes;

        private UninstallFallback(@StringRes int messageRes) {
            this.messageRes = messageRes;
        }
    }

    @NonNull
    private static Map<String, Rule[]> buildRules() {
        Map<String, Rule[]> out = new HashMap<>(2);
        // Samsung One UI 8.5 — UAD-NG #1394 [S188]
        out.put("samsung", new Rule[]{
                new Rule("samsung", "com.samsung.android.smartsuggestions", "80500",
                        R.string.oem_bloat_risk_samsung_smartsuggestions_oneui85),
        });
        return out;
    }

    /**
     * Resolve a vendor-aware known-bad warning string for the given package on
     * the current device. Returns {@code null} when nothing in the table
     * applies — the standard removal-rating chip is left to do the talking.
     */
    @AnyThread
    @Nullable
    public static CharSequence getKnownBadWarning(@NonNull Context ctx, @NonNull String packageName) {
        String mfrLc = lower(Build.MANUFACTURER);
        Rule[] vendorRules = RULES.get(mfrLc);
        if (vendorRules == null) return null;
        String vendorVersion = getVendorOsVersion(mfrLc);
        for (Rule rule : vendorRules) {
            if (rule.matches(mfrLc, packageName, vendorVersion)) {
                return ctx.getText(rule.messageRes);
            }
        }
        // Second pass: wildcard rules still apply when we couldn't read the
        // vendor version (e.g. property was empty).
        for (Rule rule : vendorRules) {
            if (ANY.equals(rule.vendorOsVersion)
                    && rule.manufacturerLc.equals(mfrLc)
                    && rule.packageName.equals(packageName)) {
                return ctx.getText(rule.messageRes);
            }
        }
        return null;
    }

    @AnyThread
    @Nullable
    public static UninstallFallback getUninstallFallback(@NonNull String packageName) {
        String manufacturer = Build.MANUFACTURER;
        String buildIncremental = Build.VERSION.INCREMENTAL;
        return getUninstallFallback(packageName, manufacturer, buildIncremental, getVendorOsVersion(lower(manufacturer)));
    }

    @AnyThread
    @Nullable
    public static CharSequence getUninstallFallbackWarning(@NonNull Context ctx, @NonNull String packageName) {
        UninstallFallback fallback = getUninstallFallback(packageName);
        return fallback != null ? ctx.getText(fallback.messageRes) : null;
    }

    @VisibleForTesting
    @Nullable
    static UninstallFallback getUninstallFallback(@NonNull String packageName,
                                                  @Nullable String manufacturer,
                                                  @Nullable String buildIncremental,
                                                  @Nullable String vendorOsVersion) {
        String mfrLc = lower(manufacturer);
        String incrementalLc = lower(buildIncremental);
        String version = vendorOsVersion != null ? vendorOsVersion : "";
        if ("samsung".equals(mfrLc)
                && PACKAGE_SAMSUNG_SMART_SUGGESTIONS.equals(packageName)
                && "80500".equals(version)) {
            return new UninstallFallback(R.string.oem_bloat_action_samsung_smartsuggestions_oneui85);
        }
        if (containsExact(MIUI_MANUFACTURERS, mfrLc) && PACKAGE_MIUI_CORE.equals(packageName)) {
            return new UninstallFallback(R.string.oem_bloat_action_miui_core);
        }
        if (isOplusFirmware(mfrLc, incrementalLc)
                && containsExact(OPLUS_UNINSTALL_GUARDED_PACKAGES, packageName)) {
            return new UninstallFallback(R.string.oem_bloat_action_oplus_reinstall_guard);
        }
        return null;
    }

    @AnyThread
    @NonNull
    private static String getVendorOsVersion(@NonNull String manufacturerLc) {
        switch (manufacturerLc) {
            case "samsung":
                return SystemProperties.get("ro.build.version.oneui", "");
            case "xiaomi":
                String hyperOs = SystemProperties.get("ro.mi.os.version.name", "");
                return !hyperOs.isEmpty() ? hyperOs : SystemProperties.get("ro.miui.ui.version.code", "");
            default:
                return "";
        }
    }

    private static boolean isOplusFirmware(@NonNull String manufacturerLc, @NonNull String buildIncrementalLc) {
        return containsExact(OPLUS_MANUFACTURERS, manufacturerLc) || containsAny(buildIncrementalLc, OPLUS_BUILD_TOKENS);
    }

    private static boolean containsExact(@NonNull String[] haystack, @NonNull String needle) {
        for (String value : haystack) {
            if (value.equals(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(@NonNull String haystack, @NonNull String[] needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String lower(@Nullable String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }

    private OemBloatRiskTable() {
    }
}
