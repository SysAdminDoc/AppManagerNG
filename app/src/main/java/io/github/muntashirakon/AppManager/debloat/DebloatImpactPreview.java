// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.app.role.RoleManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NF-16 — "What changes if I remove this?" Debloater preview.
 *
 * <p>Aggregates default-app role losses across the currently-selected debloat
 * targets. {@link RoleManager} (API 29+) reports the package currently holding
 * each system role (SMS, dialer, browser, home, ...); when one of those
 * packages is about to be removed, the user benefits from an inline warning
 * that the role will become un-bound and Android will prompt for a
 * replacement on first use.</p>
 *
 * <p>On API &lt; 29 the helper returns an empty result instead of throwing;
 * the rest of the debloater confirmation dialog stays unchanged.</p>
 */
public final class DebloatImpactPreview {
    /**
     * The roles AppManagerNG checks for impact. Listed in user-priority order:
     * losing the SMS / dialer / home / browser handler is far more disruptive
     * than losing a more obscure role like Wallet or Call screening.
     */
    @VisibleForTesting
    static final String[] CHECKED_ROLES = new String[] {
            "android.app.role.SMS",
            "android.app.role.DIALER",
            "android.app.role.HOME",
            "android.app.role.BROWSER",
            "android.app.role.ASSISTANT",
            "android.app.role.CALL_REDIRECTION",
            "android.app.role.CALL_SCREENING",
            "android.app.role.EMERGENCY",
            "android.app.role.WALLET",
    };

    /** Pure result struct returned by {@link #compute}. */
    public static final class Result {
        /** Map from role string to the (selected, role-holding) package, sorted by role. */
        @NonNull public final Map<String, String> roleLosses;

        @VisibleForTesting
        public Result(@NonNull Map<String, String> roleLosses) {
            this.roleLosses = Collections.unmodifiableMap(new LinkedHashMap<>(roleLosses));
        }

        public boolean hasAny() {
            return !roleLosses.isEmpty();
        }
    }

    private DebloatImpactPreview() {
    }

    /**
     * Walk {@link #CHECKED_ROLES}; for each role whose current holder is in
     * {@code selectedPackages}, record (role -> package). Worker-thread to
     * keep the {@code RoleManager} call off the UI.
     */
    @WorkerThread
    @NonNull
    public static Result compute(@NonNull Context context, @NonNull List<String> selectedPackages) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || selectedPackages.isEmpty()) {
            return new Result(Collections.emptyMap());
        }
        RoleManager rm;
        try {
            rm = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
        } catch (Throwable t) {
            return new Result(Collections.emptyMap());
        }
        if (rm == null) return new Result(Collections.emptyMap());
        java.util.HashSet<String> selectedSet = new java.util.HashSet<>(selectedPackages);
        LinkedHashMap<String, String> losses = new LinkedHashMap<>();
        for (String role : CHECKED_ROLES) {
            List<String> holders;
            try {
                if (!rm.isRoleAvailable(role)) continue;
                holders = getRoleHolders(rm, role);
            } catch (Throwable t) {
                continue;
            }
            if (holders == null || holders.isEmpty()) continue;
            for (String holder : holders) {
                if (holder != null && selectedSet.contains(holder)) {
                    losses.put(role, holder);
                    break;
                }
            }
        }
        return new Result(losses);
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private static List<String> getRoleHolders(@NonNull RoleManager roleManager, @NonNull String role)
            throws ReflectiveOperationException {
        Object holders = RoleManager.class.getMethod("getRoleHolders", String.class).invoke(roleManager, role);
        if (holders instanceof List) {
            return (List<String>) holders;
        }
        return Collections.emptyList();
    }

    /**
     * Render the {@link Result} as a multi-line bullet list suitable for
     * pasting into a {@link com.google.android.material.dialog.MaterialAlertDialogBuilder#setMessage(CharSequence) MaterialAlertDialogBuilder.setMessage}
     * body. Returns an empty string when {@code result.hasAny()} is false.
     */
    @AnyThread
    @NonNull
    public static String render(@NonNull Result result) {
        if (!result.hasAny()) return "";
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : result.roleLosses.entrySet()) {
            lines.add("  • " + shortRoleLabel(entry.getKey()) + " — " + entry.getValue());
        }
        return String.join("\n", lines);
    }

    @NonNull
    @VisibleForTesting
    static String shortRoleLabel(@NonNull String roleString) {
        int dot = roleString.lastIndexOf('.');
        if (dot < 0 || dot == roleString.length() - 1) {
            return roleString;
        }
        String tail = roleString.substring(dot + 1);
        // Convert "CALL_REDIRECTION" -> "Call redirection".
        String[] parts = tail.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; ++i) {
            if (parts[i].isEmpty()) continue;
            if (i == 0) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            } else {
                sb.append(' ').append(parts[i]);
            }
        }
        return sb.toString();
    }

    /** Defensive copy of the role list for tests; never returns the live array. */
    @VisibleForTesting
    @NonNull
    static String[] checkedRolesCopy() {
        return Arrays.copyOf(CHECKED_ROLES, CHECKED_ROLES.length);
    }

    @VisibleForTesting
    @NonNull
    static Result resultFromMapForTest(@Nullable Map<String, String> roleLosses) {
        if (roleLosses == null || roleLosses.isEmpty()) {
            return new Result(Collections.emptyMap());
        }
        return new Result(new LinkedHashMap<>(roleLosses));
    }
}
