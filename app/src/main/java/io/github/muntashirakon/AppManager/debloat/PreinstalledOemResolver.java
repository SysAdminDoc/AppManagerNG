// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashSet;
import java.util.Locale;

import aosp.libcore.util.EmptyArray;

final class PreinstalledOemResolver {
    private static final OemRule[] OEM_RULES = new OemRule[]{
            new OemRule("Samsung",
                    new String[]{"com.samsung.", "com.sec.", "com.samsung.android.", "tv.peel.samsung."},
                    new String[]{"samsung", "one ui", "galaxy"}),
            new OemRule("Xiaomi",
                    new String[]{"com.mi.", "com.miui.", "com.xiaomi.", "com.redmi.", "com.poco."},
                    new String[]{"xiaomi", "miui", "hyperos", "redmi", "poco"}),
            new OemRule("Huawei",
                    new String[]{"com.huawei.", "com.hisi."},
                    new String[]{"huawei", "emui"}),
            new OemRule("Honor",
                    new String[]{"com.hihonor.", "com.honor."},
                    new String[]{"honor", "magic ui"}),
            new OemRule("OPPO",
                    new String[]{"com.oppo.", "com.coloros."},
                    new String[]{"oppo", "coloros"}),
            new OemRule("OnePlus",
                    new String[]{"com.oneplus.", "net.oneplus."},
                    new String[]{"oneplus", "oxygenos"}),
            new OemRule("Realme",
                    new String[]{"com.realme."},
                    new String[]{"realme", "realme ui"}),
            new OemRule("OPlus family",
                    new String[]{"com.oplus.", "com.heytap."},
                    new String[]{"oplus", "heytap"}),
            new OemRule("Vivo",
                    new String[]{"com.vivo.", "com.bbk."},
                    new String[]{"vivo", "funtouch", "originos"}),
            new OemRule("Motorola",
                    new String[]{"com.motorola.", "com.motorola.android."},
                    new String[]{"motorola", "moto"}),
            new OemRule("Sony",
                    new String[]{"com.sony.", "com.sonymobile."},
                    new String[]{"sony", "xperia"}),
            new OemRule("LG",
                    new String[]{"com.lge.", "com.lg."},
                    new String[]{"lge", "lg"}),
            new OemRule("ZTE",
                    new String[]{"com.zte."},
                    new String[]{"zte", "nubia"}),
            new OemRule("Lenovo",
                    new String[]{"com.lenovo."},
                    new String[]{"lenovo", "zui"}),
            new OemRule("Tecno",
                    new String[]{"com.transsion.", "com.tecno."},
                    new String[]{"tecno", "hios"}),
            new OemRule("Infinix",
                    new String[]{"com.infinix."},
                    new String[]{"infinix", "xos"}),
            new OemRule("Nokia",
                    new String[]{"com.hmdglobal.", "com.nokia."},
                    new String[]{"nokia", "hmd"}),
            new OemRule("BLU",
                    new String[]{"com.blu."},
                    new String[]{"blu"}),
            new OemRule("Meizu",
                    new String[]{"com.meizu."},
                    new String[]{"meizu", "flyme"}),
            new OemRule("ASUS",
                    new String[]{"com.asus."},
                    new String[]{"asus", "zenui", "rog phone"}),
            new OemRule("TCL",
                    new String[]{"com.tcl.", "com.tct."},
                    new String[]{"tcl", "alcatel"}),
            new OemRule("Sharp",
                    new String[]{"jp.co.sharp.", "com.sharp."},
                    new String[]{"sharp", "aquos"}),
            new OemRule("HTC",
                    new String[]{"com.htc."},
                    new String[]{"htc"}),
            new OemRule("Nothing",
                    new String[]{"com.nothing."},
                    new String[]{"nothing os"})
    };

    private PreinstalledOemResolver() {
    }

    @NonNull
    static String[] resolve(@Nullable String[] explicitOems, @Nullable String packageName, @Nullable String type,
                            @Nullable String description) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addExplicit(result, explicitOems);
        if (!result.isEmpty()) {
            return result.toArray(new String[0]);
        }

        String lowerPackageName = toLower(packageName);
        if (!lowerPackageName.isEmpty()) {
            for (OemRule rule : OEM_RULES) {
                if (rule.matchesPackage(lowerPackageName)) {
                    result.add(rule.label);
                }
            }
        }

        if (shouldInspectDescription(type, description)) {
            String lowerDescription = toLower(description);
            for (OemRule rule : OEM_RULES) {
                if (rule.matchesDescription(lowerDescription)) {
                    result.add(rule.label);
                }
            }
        }
        return result.isEmpty() ? EmptyArray.STRING : result.toArray(new String[0]);
    }

    @VisibleForTesting
    static boolean shouldInspectDescription(@Nullable String type, @Nullable String description) {
        if (description == null) {
            return false;
        }
        String lowerType = toLower(type);
        if ("oem".equals(lowerType) || "carrier".equals(lowerType)) {
            return true;
        }
        String lowerDescription = toLower(description);
        return lowerDescription.contains("oem")
                || lowerDescription.contains("manufacturer")
                || lowerDescription.contains("vendor")
                || lowerDescription.contains("preinstall")
                || lowerDescription.contains("stock rom")
                || lowerDescription.contains("some rom")
                || lowerDescription.contains("device vendor");
    }

    private static void addExplicit(@NonNull LinkedHashSet<String> result, @Nullable String[] explicitOems) {
        if (explicitOems == null) {
            return;
        }
        for (String oem : explicitOems) {
            String normalized = normalizeExplicit(oem);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
    }

    @NonNull
    private static String normalizeExplicit(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        for (OemRule rule : OEM_RULES) {
            if (rule.label.equalsIgnoreCase(normalized)) {
                return rule.label;
            }
        }
        return normalized;
    }

    @NonNull
    private static String toLower(@Nullable String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }

    private static final class OemRule {
        @NonNull
        final String label;
        @NonNull
        final String[] packagePrefixes;
        @NonNull
        final String[] descriptionTokens;

        OemRule(@NonNull String label, @NonNull String[] packagePrefixes, @NonNull String[] descriptionTokens) {
            this.label = label;
            this.packagePrefixes = packagePrefixes;
            this.descriptionTokens = descriptionTokens;
        }

        boolean matchesPackage(@NonNull String packageName) {
            for (String packagePrefix : packagePrefixes) {
                if (packageName.startsWith(packagePrefix)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesDescription(@NonNull String description) {
            for (String descriptionToken : descriptionTokens) {
                if (description.contains(descriptionToken)) {
                    return true;
                }
            }
            return false;
        }
    }
}
