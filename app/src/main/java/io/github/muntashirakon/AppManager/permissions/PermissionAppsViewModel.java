// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.UserHandleHidden;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;

/**
 * Per-permission-group "apps that request this" list. Toggle and bulk-revoke
 * actions delegate to {@link PermissionToggleHelper}.
 */
public class PermissionAppsViewModel extends AndroidViewModel {
    public static final class AppRow {
        public final String packageName;
        public final CharSequence label;
        @Nullable public final Drawable icon;
        public final boolean isSystem;
        /** Any permission in the group is currently granted. */
        public boolean anyGranted;
        /** Any permission in the group is modifiable (revoke/grant possible). */
        public boolean anyModifiable;

        AppRow(String packageName, CharSequence label, @Nullable Drawable icon, boolean isSystem,
               boolean anyGranted, boolean anyModifiable) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.isSystem = isSystem;
            this.anyGranted = anyGranted;
            this.anyModifiable = anyModifiable;
        }
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AppOpsManagerCompat mAppOpsManager = new AppOpsManagerCompat();
    private final MutableLiveData<List<AppRow>> mRows = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> mToast = new MutableLiveData<>();

    private PermissionGroupCatalog.Group mGroup;

    public PermissionAppsViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<AppRow>> getRows() { return mRows; }
    public MutableLiveData<Boolean> getLoading() { return mLoading; }
    public MutableLiveData<String> getToast() { return mToast; }

    public PermissionGroupCatalog.Group getGroup() { return mGroup; }

    public void setGroup(@NonNull PermissionGroupCatalog.Group group) {
        mGroup = group;
    }

    public void load() {
        mLoading.postValue(true);
        mExecutor.submit(this::loadInternal);
    }

    @WorkerThread
    private void loadInternal() {
        try {
            if (mGroup == null) {
                mRows.postValue(Collections.emptyList());
                return;
            }
            int userId = UserHandleHidden.myUserId();
            PackageManager pm = getApplication().getPackageManager();
            List<PackageInfo> packages;
            try {
                packages = PackageManagerCompat.getInstalledPackages(
                        PackageManager.GET_PERMISSIONS, userId);
            } catch (Throwable th) {
                packages = Collections.emptyList();
            }
            if (packages == null) packages = Collections.emptyList();
            List<AppRow> rows = new ArrayList<>();
            for (PackageInfo pi : packages) {
                if (pi == null || pi.requestedPermissions == null) continue;
                boolean requested = false;
                boolean granted = false;
                String firstMatch = null;
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    String name = pi.requestedPermissions[i];
                    if (!mGroup.permissions.contains(name)) continue;
                    requested = true;
                    if (firstMatch == null) firstMatch = name;
                    if (pi.requestedPermissionsFlags != null
                            && i < pi.requestedPermissionsFlags.length
                            && (pi.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        granted = true;
                    }
                }
                if (!requested) continue;
                ApplicationInfo ai = pi.applicationInfo;
                CharSequence label = ai != null ? ai.loadLabel(pm) : pi.packageName;
                Drawable icon = null;
                try {
                    icon = ai != null ? ai.loadIcon(pm) : null;
                } catch (Throwable ignore) {}
                boolean isSystem = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean modifiable = false;
                if (firstMatch != null) {
                    PermissionToggleHelper.State s = PermissionToggleHelper.load(
                            pi.packageName, userId, firstMatch, mAppOpsManager);
                    modifiable = s != null && s.modifiable;
                }
                rows.add(new AppRow(pi.packageName, label, icon, isSystem, granted, modifiable));
            }
            Collections.sort(rows, new Comparator<AppRow>() {
                @Override
                public int compare(AppRow a, AppRow b) {
                    if (a.anyGranted != b.anyGranted) return a.anyGranted ? -1 : 1;
                    if (a.isSystem != b.isSystem) return a.isSystem ? 1 : -1;
                    return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
                }
            });
            mRows.postValue(rows);
        } finally {
            mLoading.postValue(false);
        }
    }

    public void togglePermission(@NonNull AppRow row) {
        mExecutor.submit(() -> {
            int userId = UserHandleHidden.myUserId();
            int successCount = 0;
            int failedCount = 0;
            boolean targetGrant = !row.anyGranted;
            for (String permName : mGroup.permissions) {
                PermissionToggleHelper.State s = PermissionToggleHelper.load(
                        row.packageName, userId, permName, mAppOpsManager);
                if (s == null || !s.modifiable) continue;
                if (s.granted == targetGrant) {
                    successCount++;
                    continue;
                }
                Boolean newState = PermissionToggleHelper.toggle(
                        row.packageName, userId, permName, mAppOpsManager);
                if (newState != null) successCount++;
                else failedCount++;
            }
            if (successCount > 0) {
                row.anyGranted = targetGrant;
            }
            // Reload to refresh other rows / counts
            loadInternal();
            if (failedCount > 0) {
                mToast.postValue(getApplication().getString(
                        io.github.muntashirakon.AppManager.R.string.failed_to_revoke_permission));
            }
        });
    }

    /**
     * Critical system packages that the OS or vendor depends on for core
     * services (location provider, telephony, push, settings UI). Bulk-revoke
     * skips these to avoid system_server crashes / reboot loops. Per-app
     * toggles are still unrestricted — this only affects the master action.
     */
    private static final java.util.Set<String> CRITICAL_PACKAGES;
    static {
        java.util.HashSet<String> s = new java.util.HashSet<>();
        s.add("android");
        s.add("com.android.systemui");
        s.add("com.android.settings");
        s.add("com.android.phone");
        s.add("com.android.providers.telephony");
        s.add("com.android.providers.contacts");
        s.add("com.android.providers.calendar");
        s.add("com.android.providers.media");
        s.add("com.android.providers.media.module");
        s.add("com.android.bluetooth");
        s.add("com.android.nfc");
        s.add("com.android.location.fused");
        s.add("com.android.shell");
        s.add("com.google.android.gms");
        s.add("com.google.android.gsf");
        s.add("com.google.android.ext.services");
        s.add("com.google.android.permissioncontroller");
        s.add("com.google.android.packageinstaller");
        s.add("com.android.permissioncontroller");
        s.add("com.samsung.android.location");
        s.add("com.samsung.android.providers.context");
        s.add("com.samsung.android.bluetooth");
        s.add("com.sec.location.nsflp2");
        s.add("com.sec.android.app.camera");
        s.add("com.sec.phone");
        s.add("com.sec.imsservice");
        s.add("com.sec.epdg");
        s.add("io.github.muntashirakon.AppManager");
        CRITICAL_PACKAGES = java.util.Collections.unmodifiableSet(s);
    }

    private static boolean isCriticalPackage(@NonNull String pkg) {
        if (CRITICAL_PACKAGES.contains(pkg)) return true;
        // Match common system-service prefixes we don't enumerate
        return pkg.startsWith("com.android.server.")
                || pkg.startsWith("com.google.android.gms.")
                || pkg.equals("com.google.android.apps.maps"); // map provider; revoking BG loc here has rebooted devices
    }

    public void revokeForAll() {
        mExecutor.submit(() -> {
            int userId = UserHandleHidden.myUserId();
            int affected = 0;
            int skipped = 0;
            int failed = 0;
            List<AppRow> current = mRows.getValue();
            if (current == null) return;
            for (AppRow row : current) {
                if (!row.anyGranted || !row.anyModifiable) continue;
                if (isCriticalPackage(row.packageName)) {
                    skipped++;
                    continue;
                }
                boolean anySuccess = false;
                for (String permName : mGroup.permissions) {
                    PermissionToggleHelper.State s = PermissionToggleHelper.load(
                            row.packageName, userId, permName, mAppOpsManager);
                    if (s == null || !s.modifiable || !s.granted) continue;
                    if (PermissionToggleHelper.revoke(row.packageName, userId, permName, mAppOpsManager)) {
                        anySuccess = true;
                    } else {
                        failed++;
                    }
                }
                if (anySuccess) affected++;
            }
            String msg = getApplication().getResources().getQuantityString(
                    io.github.muntashirakon.AppManager.R.plurals.perm_inspector_bulk_revoked,
                    affected, affected);
            if (skipped > 0) {
                msg = msg + " " + getApplication().getResources().getQuantityString(
                        io.github.muntashirakon.AppManager.R.plurals.perm_inspector_bulk_skipped,
                        skipped, skipped);
            }
            mToast.postValue(msg);
            loadInternal();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
    }
}
