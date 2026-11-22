// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.Context;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.pm.PermissionInfoCompat;
import androidx.core.os.ConfigurationCompat;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;

/**
 * A permission's protection level and flags, and its grant state, as the short words under a permission row, joined
 * by "|" like "dangerous|instant|revoked". Each word is a string resource, so a translated app shows translated
 * words. English keeps the platform's own lowercase names.
 */
public final class PermissionProtectionText {
    private static final char SEPARATOR = '|';

    private PermissionProtectionText() {
    }

    @NonNull
    public static String of(@NonNull Context context, @NonNull PermissionInfo info) {
        int base = PermissionInfoCompat.getProtection(info);
        int flags = PermissionInfoCompat.getProtectionFlags(info);
        StringBuilder sb = new StringBuilder();
        switch (base) {
            case PermissionInfo.PROTECTION_DANGEROUS:
                sb.append(context.getString(R.string.perm_protection_dangerous));
                break;
            case PermissionInfo.PROTECTION_NORMAL:
                sb.append(context.getString(R.string.perm_protection_normal));
                break;
            case PermissionInfo.PROTECTION_SIGNATURE:
                sb.append(reused(context, R.string.app_signing_signature));
                break;
            case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
            case PermissionInfo.PROTECTION_SIGNATURE | PermissionInfo.PROTECTION_FLAG_PRIVILEGED:
                sb.append(context.getString(R.string.perm_protection_signature_or_privileged));
                break;
            default:
                sb.append("????");
                break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_PRIVILEGED,
                    R.string.receiver_broadcast_route_privileged, true);
            appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_PRE23, R.string.perm_protection_pre23, false);
            appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_INSTALLER, R.string.installer, true);
            appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_VERIFIER, R.string.perm_protection_verifier,
                    false);
            appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_PREINSTALLED,
                    R.string.perm_protection_preinstalled, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_SETUP, R.string.perm_protection_setup,
                        false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY,
                        R.string.perm_protection_runtime, false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_INSTANT, R.string.perm_protection_instant,
                        false);
            }
        } else {
            appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_SYSTEM, R.string.system, true);
        }
        appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_DEVELOPMENT, R.string.perm_protection_development,
                false);
        appendIf(context, sb, flags, PermissionInfo.PROTECTION_FLAG_APPOP, R.string.perm_protection_appop, false);
        return sb.toString();
    }

    /**
     * The protection words followed by whether the app has the permission.
     */
    @NonNull
    public static String of(@NonNull Context context, @NonNull PermissionInfo info, boolean granted) {
        return of(context, info) + SEPARATOR + (granted ? reused(context, R.string.perm_app_status_granted)
                : context.getString(R.string.perm_status_revoked));
    }

    private static void appendIf(@NonNull Context context, @NonNull StringBuilder sb, int flags, int flag,
                                 @StringRes int word, boolean reused) {
        if ((flags & flag) != 0) {
            sb.append(SEPARATOR).append(reused ? reused(context, word) : context.getString(word));
        }
    }

    /**
     * A word shared with another screen, where it starts with a capital ("Granted", "Installer"). The line shows its
     * words in lowercase.
     */
    @NonNull
    private static String reused(@NonNull Context context, @StringRes int word) {
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        return context.getString(word).toLowerCase(locale != null ? locale : Locale.ROOT);
    }
}
