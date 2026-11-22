// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;

import java.text.NumberFormat;
import java.util.Locale;

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
