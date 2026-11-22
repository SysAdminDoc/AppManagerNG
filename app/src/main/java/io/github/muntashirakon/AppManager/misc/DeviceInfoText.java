// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import io.github.muntashirakon.AppManager.R;

/**
 * The values About the device shows, worded through string resources so they follow the app's language (#31).
 * Raw identifiers the system reports, like {@code enforcing} or an ABI, stay as they are.
 */
public final class DeviceInfoText {
    private DeviceInfoText() {
    }

    @NonNull
    public static String yesNo(@NonNull Context context, boolean value) {
        return context.getString(value ? R.string.yes : R.string.no);
    }

    /** {@code ro.boot.verifiedbootstate}: green, yellow, orange or red, with what each color means. */
    @NonNull
    public static String verifiedBootState(@NonNull Context context, @NonNull String color) {
        switch (color) {
            case "green":
                return context.getString(R.string.verified_boot_state_green);
            case "yellow":
                return context.getString(R.string.verified_boot_state_yellow);
            case "orange":
                return context.getString(R.string.verified_boot_state_orange);
            case "red":
                return context.getString(R.string.verified_boot_state_red);
            default:
                return context.getString(R.string.verified_boot_state_other, color);
        }
    }

    /** {@code ro.boot.vbmeta.device_state} as a yes or no answer, or the raw value if it's neither. */
    @NonNull
    public static String bootloaderLocked(@NonNull Context context, @NonNull String deviceState) {
        switch (deviceState) {
            case "locked":
                return yesNo(context, true);
            case "unlocked":
                return yesNo(context, false);
            default:
                return deviceState;
        }
    }

    @NonNull
    public static String batteryHealth(@NonNull Context context, int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return context.getString(R.string.battery_health_good);
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return context.getString(R.string.battery_health_overheat);
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return context.getString(R.string.battery_health_dead);
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return context.getString(R.string.battery_health_over_voltage);
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return context.getString(R.string.battery_health_failure);
            case BatteryManager.BATTERY_HEALTH_COLD:
                return context.getString(R.string.battery_health_cold);
            default:
                return context.getString(R.string.state_unknown);
        }
    }

    /** The health, with the charge cycle count when the system reports one. */
    @NonNull
    public static String batteryHealthWithCycles(@NonNull Context context, int health, int cycleCount) {
        String label = batteryHealth(context, health);
        if (cycleCount <= 0) {
            return label;
        }
        return context.getResources().getQuantityString(R.plurals.battery_health_with_cycles, cycleCount, label,
                cycleCount);
    }

    /**
     * The design capacity from the power profile and the one estimated from the charge counter, either of which can
     * be missing (0 or less).
     */
    @Nullable
    public static String batteryCapacity(@NonNull Context context, double designMAh, double estimatedMAh) {
        Locale locale = locale(context);
        if (designMAh > 0) {
            if (estimatedMAh > 0) {
                return context.getString(R.string.battery_capacity_mah_with_estimate, decimal(designMAh, locale),
                        decimal(estimatedMAh, locale));
            }
            return context.getString(R.string.battery_capacity_mah, decimal(designMAh, locale));
        }
        if (estimatedMAh > 0) {
            return context.getString(R.string.battery_capacity_mah_estimated, decimal(estimatedMAh, locale));
        }
        return null;
    }

    @NonNull
    public static String sizePx(@NonNull Context context, int widthPx, int heightPx) {
        return context.getString(R.string.about_device_size_px, widthPx, heightPx);
    }

    @NonNull
    public static String density(@NonNull Context context, @NonNull String densityName, int dpi) {
        return context.getString(R.string.about_device_density_dpi, densityName, dpi);
    }

    @NonNull
    public static String refreshRate(@NonNull Context context, float hz) {
        return context.getString(R.string.about_device_refresh_rate_hz, decimal(hz, locale(context)));
    }

    /**
     * The current rate, with every rate the screen supports when there's more than that one (#54).
     *
     * @param supported The rates of the display's modes, or {@code null} on Android 5, which can't list them
     */
    @NonNull
    public static String refreshRate(@NonNull Context context, float currentHz, @Nullable float[] supported) {
        Locale locale = locale(context);
        String current = decimal(currentHz, locale);
        List<String> rates = supportedRefreshRates(supported, locale);
        if (rates.isEmpty() || (rates.size() == 1 && rates.get(0).equals(current))) {
            return context.getString(R.string.about_device_refresh_rate_hz, current);
        }
        return context.getString(R.string.about_device_refresh_rate_supported, current,
                TextUtils.join(listSeparator(locale), rates));
    }

    /**
     * Each rate once, lowest first, to a tenth of a hertz. Displays list a rate once per resolution, and the same rate
     * can come back as 59.999996 and 60.000004.
     */
    @NonNull
    static List<String> supportedRefreshRates(@Nullable float[] rates, @NonNull Locale locale) {
        if (rates == null) {
            return Collections.emptyList();
        }
        TreeSet<Long> tenths = new TreeSet<>();
        for (float rate : rates) {
            if (rate > 0 && !Float.isInfinite(rate)) {
                tenths.add(Math.round(rate * 10d));
            }
        }
        List<String> labels = new ArrayList<>(tenths.size());
        for (long tenth : tenths) {
            labels.add(decimal(tenth / 10d, locale));
        }
        return labels;
    }

    /** A comma, or a semicolon where the comma is the decimal mark (59,9; 60). */
    @NonNull
    static String listSeparator(@NonNull Locale locale) {
        return DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',' ? "; " : ", ";
    }

    /** RAM as the system reports it: the total the kernel can use and what's available to apps now. */
    @NonNull
    public static String ramAmount(@NonNull Context context, long totalBytes, long availableBytes) {
        return context.getString(R.string.about_device_amount_available, size(context, totalBytes),
                size(context, availableBytes));
    }

    /** Swap, which on Android is usually ZRAM, from /proc/meminfo's SwapTotal and SwapFree. */
    @NonNull
    public static String swapAmount(@NonNull Context context, long totalBytes, long freeBytes) {
        if (totalBytes <= 0) {
            return context.getString(R.string.none);
        }
        return context.getString(R.string.about_device_amount_used, size(context, totalBytes),
                size(context, Math.max(0, totalBytes - freeBytes)));
    }

    /** A storage volume's size and the space apps can still use. */
    @NonNull
    public static String storageAmount(@NonNull Context context, long totalBytes, long freeBytes) {
        return context.getString(R.string.about_device_amount_free, size(context, totalBytes),
                size(context, freeBytes));
    }

    @NonNull
    private static String size(@NonNull Context context, long bytes) {
        return Formatter.formatFileSize(context, bytes);
    }

    /** A number with at most one decimal and no grouping, like 4500, 59.9 or 4370,5. */
    @NonNull
    static String decimal(double value, @NonNull Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    @NonNull
    static Locale locale(@NonNull Context context) {
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        return locale != null ? locale : Locale.getDefault();
    }
}
