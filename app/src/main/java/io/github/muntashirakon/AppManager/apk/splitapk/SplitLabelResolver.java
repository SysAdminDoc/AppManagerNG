// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.util.DisplayMetrics;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.misc.VMRuntime;

public final class SplitLabelResolver {
    private static final Map<String, String> ABI_LABELS = new HashMap<>();
    private static final Map<String, String> DENSITY_LABELS = new HashMap<>();

    static {
        ABI_LABELS.put(VMRuntime.ABI_ARMEABI_V7A, "ARM 32-bit");
        ABI_LABELS.put(VMRuntime.ABI_ARM64_V8A, "ARM 64-bit");
        ABI_LABELS.put(VMRuntime.ABI_X86, "x86 32-bit");
        ABI_LABELS.put(VMRuntime.ABI_X86_64, "x86 64-bit");

        DENSITY_LABELS.put("ldpi", "Low density");
        DENSITY_LABELS.put("mdpi", "Medium density");
        DENSITY_LABELS.put("tvdpi", "TV density");
        DENSITY_LABELS.put("hdpi", "High density");
        DENSITY_LABELS.put("xhdpi", "Extra-high density");
        DENSITY_LABELS.put("xxhdpi", "Extra-extra-high density");
        DENSITY_LABELS.put("xxxhdpi", "Extra-extra-extra-high density");
    }

    @NonNull
    public static String getAbiLabel(@NonNull String abi) {
        String label = ABI_LABELS.get(abi);
        return label != null ? label : abi;
    }

    @NonNull
    public static String getDensityLabel(@NonNull String densitySuffix) {
        String label = DENSITY_LABELS.get(densitySuffix);
        return label != null ? label : densitySuffix;
    }

    private SplitLabelResolver() {
    }
}
