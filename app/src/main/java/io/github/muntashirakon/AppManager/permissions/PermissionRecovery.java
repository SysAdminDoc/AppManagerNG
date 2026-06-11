// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import android.os.UserHandleHidden;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.safety.CriticalPackageGuard;

/**
 * Recovery utility for the Permission Inspector's bulk-revoke action. If a
 * pre-guard build of AppManagerNG was used to revoke permissions from
 * OS- or vendor-critical packages, the device may exhibit broken voicemail,
 * crashing Phone, missing location, or boot-loop symptoms.
 *
 * <p>This helper re-grants every dangerous permission to a fixed set of
 * critical system packages and clears any persisted {@link ComponentsBlocker}
 * permission rules for those packages so the bad state does not survive
 * reboot or reinstall.</p>
 */
public final class PermissionRecovery {
    private PermissionRecovery() {}

    /** Critical OS / vendor packages that should never be in a revoked state. */
    public static final Set<String> CRITICAL_PACKAGES = CriticalPackageGuard.CRITICAL_PACKAGES;

    public static final class Result {
        public final int packagesProcessed;
        public final int permissionsRestored;
        public final int rulesCleared;

        public Result(int packagesProcessed, int permissionsRestored, int rulesCleared) {
            this.packagesProcessed = packagesProcessed;
            this.permissionsRestored = permissionsRestored;
            this.rulesCleared = rulesCleared;
        }
    }

    /**
     * Grant every dangerous permission across every catalog group to every
     * known-critical package, and wipe any persisted permission rules for
     * those packages.
     */
    @WorkerThread
    @NonNull
    public static Result restoreAll(@NonNull AppOpsManagerCompat appOps) {
        int userId = UserHandleHidden.myUserId();
        int packagesProcessed = 0;
        int permissionsRestored = 0;
        int rulesCleared = 0;

        // Collect every dangerous permission across all catalog groups
        HashSet<String> allPermissions = new HashSet<>();
        for (PermissionGroupCatalog.Group g : PermissionGroupCatalog.all()) {
            allPermissions.addAll(g.permissions);
        }
        // Plus a few extras commonly affected
        allPermissions.addAll(Arrays.asList(
                "android.permission.READ_PHONE_NUMBERS",
                "android.permission.ANSWER_PHONE_CALLS",
                "android.permission.USE_SIP",
                "android.permission.ADD_VOICEMAIL",
                "com.android.voicemail.permission.ADD_VOICEMAIL",
                "android.permission.ACCESS_MEDIA_LOCATION",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        ));

        for (String pkg : CRITICAL_PACKAGES) {
            boolean touchedPackage = false;
            for (String permName : allPermissions) {
                PermissionToggleHelper.State state =
                        PermissionToggleHelper.load(pkg, userId, permName, appOps);
                if (state == null) continue;
                touchedPackage = true;
                if (!state.granted && state.modifiable) {
                    if (PermissionToggleHelper.grant(pkg, userId, permName, appOps)) {
                        permissionsRestored++;
                    }
                }
            }
            // Wipe any persisted permission rules for this package, regardless
            // of grant outcome — leaving stale rules around lets them re-apply
            // after reboot.
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pkg, userId)) {
                List<PermissionRule> existing = cb.getAll(PermissionRule.class);
                if (existing != null && !existing.isEmpty()) {
                    for (PermissionRule rule : existing) {
                        cb.removeEntry(rule);
                        rulesCleared++;
                    }
                    cb.commit();
                }
            } catch (Throwable ignore) {
            }
            if (touchedPackage) packagesProcessed++;
        }
        return new Result(packagesProcessed, permissionsRestored, rulesCleared);
    }
}
