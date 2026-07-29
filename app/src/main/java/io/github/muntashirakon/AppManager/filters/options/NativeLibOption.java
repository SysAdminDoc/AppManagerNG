// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.scanner.NativeLibReadiness;
import io.github.muntashirakon.AppManager.utils.LangUtils;

/**
 * Sweeps the device for native code that will break or bloat on current Android: libraries that
 * are not 16 KB page-aligned, packages that ship only 32-bit code, and libraries stored
 * compressed.
 *
 * <p>A package whose libraries could not be read never matches "ready" — an unreadable package is
 * not a clean one — and is reachable through its own key so it can be found deliberately.
 */
public class NativeLibOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("has_native_libs", TYPE_NONE);
        put("no_native_libs", TYPE_NONE);
        put("not_16kb_ready", TYPE_NONE);
        put("16kb_ready", TYPE_NONE);
        put("alignment_unknown", TYPE_NONE);
        put("32bit_only", TYPE_NONE);
        put("compressed", TYPE_NONE);
    }};

    public NativeLibOption() {
        super("native_lib");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        NativeLibReadiness readiness = info.getNativeLibReadiness();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "has_native_libs":
                return result.setMatched(readiness.hasNativeLibraries);
            case "no_native_libs":
                return result.setMatched(!readiness.isUnknown() && !readiness.hasNativeLibraries);
            case "not_16kb_ready":
                return result.setMatched(readiness.needsSixteenKbAttention());
            case "16kb_ready":
                // Only a package that was actually examined can be called ready.
                return result.setMatched(!readiness.isUnknown() && readiness.sixteenKbReady);
            case "alignment_unknown":
                return result.setMatched(readiness.alignmentUnknown);
            case "32bit_only":
                return result.setMatched(readiness.thirtyTwoBitOnly);
            case "compressed":
                return result.setMatched(readiness.compressed);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Native libraries");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "has_native_libs":
                return sb.append(LangUtils.getSeparatorString()).append("present");
            case "no_native_libs":
                return sb.append(LangUtils.getSeparatorString()).append("none");
            case "not_16kb_ready":
                return sb.append(LangUtils.getSeparatorString()).append("not 16 KB-ready");
            case "16kb_ready":
                return sb.append(LangUtils.getSeparatorString()).append("16 KB-ready");
            case "alignment_unknown":
                return sb.append(LangUtils.getSeparatorString()).append("alignment unknown");
            case "32bit_only":
                return sb.append(LangUtils.getSeparatorString()).append("32-bit only");
            case "compressed":
                return sb.append(LangUtils.getSeparatorString()).append("compressed");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
