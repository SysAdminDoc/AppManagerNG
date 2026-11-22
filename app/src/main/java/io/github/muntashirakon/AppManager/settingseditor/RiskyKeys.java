// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/** Settings that can lock someone out, cut the ADB link or leave the system out of step, each with a short note. */
public final class RiskyKeys {
    private RiskyKeys() {
    }

    public static boolean isRisky(@NonNull String key) {
        return getNote(key) != 0;
    }

    /** What could go wrong, or 0 for an ordinary key. */
    @StringRes
    public static int getNote(@NonNull String key) {
        switch (key) {
            case "device_provisioned":
            case "user_setup_complete":
                return R.string.settings_editor_risk_setup;
            case "adb_enabled":
            case "development_settings_enabled":
                return R.string.settings_editor_risk_adb;
            case "airplane_mode_on":
            case "bluetooth_on":
            case "wifi_on":
                return R.string.settings_editor_risk_radio;
            case "package_verifier_enable":
                return R.string.settings_editor_risk_verifier;
        }
        if (key.startsWith("hidden_api_policy")) {
            return R.string.settings_editor_risk_hidden_api;
        }
        if (key.startsWith("lock_screen")) {
            return R.string.settings_editor_risk_lock_screen;
        }
        return 0;
    }
}
