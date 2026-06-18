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
import io.github.muntashirakon.AppManager.rules.struct.PermissionReferenceRule;
import io.github.muntashirakon.AppManager.safety.CriticalPackageGuard;

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
        @NonNull
        public final List<String> permissionNames;
        public boolean hasReference;
        public boolean referenceDrifted;
        public int referenceCount;

        AppRow(String packageName, CharSequence label, @Nullable Drawable icon, boolean isSystem,
               boolean anyGranted, boolean anyModifiable) {
            this(packageName, label, icon, isSystem, anyGranted, anyModifiable,
                    Collections.emptyList(), false, false, 0);
        }

        AppRow(String packageName, CharSequence label, @Nullable Drawable icon, boolean isSystem,
               boolean anyGranted, boolean anyModifiable, @NonNull List<String> permissionNames,
               boolean hasReference, boolean referenceDrifted, int referenceCount) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.isSystem = isSystem;
            this.anyGranted = anyGranted;
            this.anyModifiable = anyModifiable;
            this.permissionNames = new ArrayList<>(permissionNames);
            this.hasReference = hasReference;
            this.referenceDrifted = referenceDrifted;
            this.referenceCount = referenceCount;
        }
    }

    /** EI-04 — chip-row filter state. */
    public enum Filter { ALL, USER_APPS, GRANTED }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AppOpsManagerCompat mAppOpsManager = new AppOpsManagerCompat();
    private final MutableLiveData<List<AppRow>> mRows = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> mToast = new MutableLiveData<>();
    /** Emits the skipped-package count after revokeForAll() so the UI can surface an explanation. */
    private final MutableLiveData<Integer> mLastSkippedCount = new MutableLiveData<>();

    private PermissionGroupCatalog.Group mGroup;
    /** The full unfiltered set; {@link #mRows} stays the post-filter view. */
    @NonNull
    private List<AppRow> mUnfiltered = Collections.emptyList();
    @NonNull
    private Filter mFilter = Filter.ALL;

    public PermissionAppsViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<AppRow>> getRows() { return mRows; }
    public MutableLiveData<Boolean> getLoading() { return mLoading; }
    public MutableLiveData<String> getToast() { return mToast; }
    public MutableLiveData<Integer> getLastSkippedCount() { return mLastSkippedCount; }

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
            } catch (Exception th) {
                packages = Collections.emptyList();
            }
            if (packages == null) packages = Collections.emptyList();
            List<AppRow> rows = new ArrayList<>();
            for (PackageInfo pi : packages) {
                if (pi == null || pi.requestedPermissions == null) continue;
                boolean requested = false;
                boolean granted = false;
                boolean modifiable = false;
                boolean hasReference = false;
                boolean referenceDrifted = false;
                int referenceCount = 0;
                List<String> permissionNames = new ArrayList<>();
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    String name = pi.requestedPermissions[i];
                    if (!mGroup.permissions.contains(name)) continue;
                    requested = true;
                    permissionNames.add(name);
                    boolean rawGranted = pi.requestedPermissionsFlags != null
                            && i < pi.requestedPermissionsFlags.length
                            && (pi.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    PermissionToggleHelper.State s = PermissionToggleHelper.load(
                            pi.packageName, userId, name, mAppOpsManager);
                    boolean effectiveGranted = s != null ? s.effectiveGranted : rawGranted;
                    if (effectiveGranted) {
                        granted = true;
                    }
                    modifiable |= s != null && s.modifiable;
                    PermissionReferenceRule reference = PermissionToggleHelper.loadReference(
                            pi.packageName, userId, name);
                    if (reference != null) {
                        hasReference = true;
                        referenceCount++;
                        referenceDrifted |= reference.isGranted() != effectiveGranted;
                    }
                }
                if (!requested) continue;
                ApplicationInfo ai = pi.applicationInfo;
                CharSequence label = ai != null ? ai.loadLabel(pm) : pi.packageName;
                Drawable icon = null;
                try {
                    icon = ai != null ? ai.loadIcon(pm) : null;
                } catch (Exception ignore) {}
                boolean isSystem = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                rows.add(new AppRow(pi.packageName, label, icon, isSystem, granted, modifiable,
                        permissionNames, hasReference, referenceDrifted, referenceCount));
            }
            Collections.sort(rows, new Comparator<AppRow>() {
                @Override
                public int compare(AppRow a, AppRow b) {
                    if (a.anyGranted != b.anyGranted) return a.anyGranted ? -1 : 1;
                    if (a.isSystem != b.isSystem) return a.isSystem ? 1 : -1;
                    return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
                }
            });
            mUnfiltered = rows;
            mRows.postValue(applyFilter(rows, mFilter));
        } finally {
            mLoading.postValue(false);
        }
    }

    /** EI-04 — change the active chip-row filter and re-emit. */
    public void setFilter(@NonNull Filter filter) {
        if (mFilter == filter) return;
        mFilter = filter;
        mRows.postValue(applyFilter(mUnfiltered, mFilter));
    }

    @NonNull
    public Filter getFilter() {
        return mFilter;
    }

    @NonNull
    static List<AppRow> applyFilter(@NonNull List<AppRow> source, @NonNull Filter filter) {
        if (filter == Filter.ALL || source.isEmpty()) {
            return new ArrayList<>(source);
        }
        ArrayList<AppRow> out = new ArrayList<>(source.size());
        for (AppRow row : source) {
            switch (filter) {
                case USER_APPS:
                    if (!row.isSystem) out.add(row);
                    break;
                case GRANTED:
                    if (row.anyGranted) out.add(row);
                    break;
                case ALL:
                default:
                    out.add(row);
                    break;
            }
        }
        return out;
    }

    public void togglePermission(@NonNull AppRow row) {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            int successCount = 0;
            int failedCount = 0;
            try {
                int userId = UserHandleHidden.myUserId();
                boolean targetGrant = !row.anyGranted;
                for (String permName : mGroup.permissions) {
                    PermissionToggleHelper.State s = PermissionToggleHelper.load(
                            row.packageName, userId, permName, mAppOpsManager);
                    if (s == null || !s.modifiable) continue;
                    if (s.effectiveGranted == targetGrant) {
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
            } finally {
                // Reload to refresh other rows / counts and clear the busy state.
                loadInternal();
            }
            if (failedCount > 0) {
                mToast.postValue(getApplication().getString(
                        io.github.muntashirakon.AppManager.R.string.failed_to_revoke_permission));
            }
        });
    }

    public void pinReference(@NonNull AppRow row) {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            int pinned = 0;
            int failed = 0;
            try {
                int userId = UserHandleHidden.myUserId();
                for (String permName : row.permissionNames) {
                    PermissionToggleHelper.State s = PermissionToggleHelper.load(
                            row.packageName, userId, permName, mAppOpsManager);
                    if (s == null) {
                        failed++;
                        continue;
                    }
                    if (PermissionToggleHelper.pinReference(row.packageName, userId, permName,
                            s.effectiveGranted)) {
                        pinned++;
                    } else failed++;
                }
                mToast.postValue(getApplication().getString(failed == 0 && pinned > 0
                        ? io.github.muntashirakon.AppManager.R.string.permission_reference_pinned
                        : io.github.muntashirakon.AppManager.R.string.permission_reference_pin_failed));
            } finally {
                loadInternal();
            }
        });
    }

    public void restoreReference(@NonNull AppRow row) {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            int restored = 0;
            int failed = 0;
            try {
                int userId = UserHandleHidden.myUserId();
                for (String permName : row.permissionNames) {
                    PermissionReferenceRule reference = PermissionToggleHelper.loadReference(
                            row.packageName, userId, permName);
                    if (reference == null) continue;
                    PermissionToggleHelper.State s = PermissionToggleHelper.load(
                            row.packageName, userId, permName, mAppOpsManager);
                    if (s == null) {
                        failed++;
                        continue;
                    }
                    if (s.effectiveGranted == reference.isGranted()) {
                        restored++;
                        continue;
                    }
                    if (!s.modifiable) {
                        failed++;
                        continue;
                    }
                    if (PermissionToggleHelper.setGranted(row.packageName, userId, permName,
                            reference.isGranted(), mAppOpsManager)) {
                        restored++;
                    } else failed++;
                }
                mToast.postValue(getApplication().getString(failed == 0 && restored > 0
                        ? io.github.muntashirakon.AppManager.R.string.permission_reference_restored
                        : io.github.muntashirakon.AppManager.R.string.permission_reference_restore_failed));
            } finally {
                loadInternal();
            }
        });
    }

    private static boolean isCriticalPackage(@NonNull String pkg) {
        return CriticalPackageGuard.isCriticalPackage(pkg);
    }

    public void revokeForAll() {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            int affected = 0;
            int skipped = 0;
            int failed = 0;
            try {
                int userId = UserHandleHidden.myUserId();
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
                        if (s == null || !s.modifiable || !s.effectiveGranted) continue;
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
                mLastSkippedCount.postValue(skipped);
            } finally {
                loadInternal();
            }
        });
    }

    public void grantForAll() {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            int affected = 0;
            int failed = 0;
            try {
                int userId = UserHandleHidden.myUserId();
                List<AppRow> current = mRows.getValue();
                if (current == null) return;
                for (AppRow row : current) {
                    if (!row.anyModifiable) continue;
                    boolean anySuccess = false;
                    for (String permName : mGroup.permissions) {
                        PermissionToggleHelper.State s = PermissionToggleHelper.load(
                                row.packageName, userId, permName, mAppOpsManager);
                        if (s == null || !s.modifiable || s.effectiveGranted) continue;
                        if (PermissionToggleHelper.grant(row.packageName, userId, permName, mAppOpsManager)) {
                            anySuccess = true;
                        } else {
                            failed++;
                        }
                    }
                    if (anySuccess) affected++;
                }
                mToast.postValue(getApplication().getResources().getQuantityString(
                        io.github.muntashirakon.AppManager.R.plurals.perm_inspector_bulk_granted,
                        affected, affected));
            } finally {
                loadInternal();
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
    }
}
