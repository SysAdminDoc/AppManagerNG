// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.ComponentName;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Gate for the confirmation {@link Intent} the platform hands back through
 * {@link Intent#EXTRA_INTENT} on {@code STATUS_PENDING_USER_ACTION}.
 *
 * <p>That intent arrives inside a broadcast delivered through a <em>mutable</em>
 * {@code PendingIntent} — the platform installer has to be able to fill it in. Anything that can
 * make us deliver such a broadcast can therefore also choose the payload, and we launch it. An
 * unvalidated forward is a textbook intent-redirection primitive: the payload runs with our
 * identity and can carry our URI grants along with it.
 *
 * <p>The policy is deliberately narrow: the payload must name where it is going, must not point
 * back into this app, and never keeps the caller's flags — least of all URI-permission grants.
 */
public final class InstallerConfirmIntentGuard {
    private static final int URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    private InstallerConfirmIntentGuard() {
    }

    /**
     * @param confirmIntent the intent supplied in {@link Intent#EXTRA_INTENT}.
     * @param selfPackage   this application's package name.
     * @return a sanitized copy safe to launch, or {@code null} when it must not be forwarded.
     */
    @Nullable
    public static Intent sanitize(@Nullable Intent confirmIntent, @NonNull String selfPackage) {
        if (confirmIntent == null) {
            return null;
        }
        ComponentName component = confirmIntent.getComponent();
        String targetPackage = component != null ? component.getPackageName() : confirmIntent.getPackage();
        if (targetPackage == null || targetPackage.isEmpty()) {
            // A fully implicit payload could resolve anywhere, including to us.
            return null;
        }
        if (selfPackage.equals(targetPackage)) {
            // Redirecting back into our own components is the confused-deputy case itself.
            return null;
        }
        Intent sanitized = new Intent(confirmIntent);
        // Drop every caller-chosen flag, then re-add only what launching from a receiver needs.
        // In particular this strips URI grants, which would otherwise be passed on with our
        // permission to read them.
        sanitized.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return sanitized;
    }

    /**
     * @return whether {@code confirmIntent} carries URI-permission grants, i.e. whether
     * {@link #sanitize} had to strip any.
     */
    public static boolean carriesUriGrants(@Nullable Intent confirmIntent) {
        return confirmIntent != null && (confirmIntent.getFlags() & URI_GRANT_FLAGS) != 0;
    }
}
