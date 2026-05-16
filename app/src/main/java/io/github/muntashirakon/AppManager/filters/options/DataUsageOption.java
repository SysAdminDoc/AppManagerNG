// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class DataUsageOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_SIZE_BYTES);
        put("le", TYPE_SIZE_BYTES);
        put("ge", TYPE_SIZE_BYTES);
        // Mobile-only predicates — closes the "Filter: Data Usage Split" row.
        put("mobile_le", TYPE_SIZE_BYTES);
        put("mobile_ge", TYPE_SIZE_BYTES);
        // Wi-Fi-only predicates.
        put("wifi_le", TYPE_SIZE_BYTES);
        put("wifi_ge", TYPE_SIZE_BYTES);
    }};

    public DataUsageOption() {
        super("data_usage");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getDataUsage().getTotal() == longValue);
            case "le":
                return result.setMatched(info.getDataUsage().getTotal() <= longValue);
            case "ge":
                return result.setMatched(info.getDataUsage().getTotal() >= longValue);
            case "mobile_le":
                return result.setMatched(totalOf(info.getMobileDataUsage()) <= longValue);
            case "mobile_ge":
                return result.setMatched(totalOf(info.getMobileDataUsage()) >= longValue);
            case "wifi_le":
                return result.setMatched(totalOf(info.getWifiDataUsage()) <= longValue);
            case "wifi_ge":
                return result.setMatched(totalOf(info.getWifiDataUsage()) >= longValue);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    private static long totalOf(@NonNull AppUsageStatsManager.DataUsage usage) {
        return usage.getTotal();
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Data usage");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = ").append(Formatter.formatFileSize(context, longValue));
            case "le":
                return sb.append(" ≤ ").append(Formatter.formatFileSize(context, longValue));
            case "ge":
                return sb.append(" ≥ ").append(Formatter.formatFileSize(context, longValue));
            case "mobile_le":
                return sb.append(" (mobile) ≤ ").append(Formatter.formatFileSize(context, longValue));
            case "mobile_ge":
                return sb.append(" (mobile) ≥ ").append(Formatter.formatFileSize(context, longValue));
            case "wifi_le":
                return sb.append(" (Wi-Fi) ≤ ").append(Formatter.formatFileSize(context, longValue));
            case "wifi_ge":
                return sb.append(" (Wi-Fi) ≥ ").append(Formatter.formatFileSize(context, longValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
