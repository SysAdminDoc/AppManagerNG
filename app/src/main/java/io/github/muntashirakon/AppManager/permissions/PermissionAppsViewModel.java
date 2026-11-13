// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.Application;
import android.content.Intent;
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

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.rules.struct.PermissionReferenceRule;
import io.github.muntashirakon.AppManager.safety.CriticalPackageGuard;

/**
 * Per-permission-group "apps that request this" list. Toggle and bulk-revoke
 * actions delegate to {@link PermissionToggleHelper}. For a special app access
 * entry the list holds the apps it applies to, and the entry's own probe reads
 * and changes the state.
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
        /** False when App Manager couldn't read whether the app has the access. */
        public boolean stateKnown = true;
        /** Settings pages for special app access, best first. */
        @Nullable
        public List<Intent> settingsIntents;
        @Nullable
        PackageInfo packageInfo;

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
    @Nullable
    private SpecialAccessCatalog.Entry mSpecial;
    /** The state the last special access load read; only touched on {@link #mExecutor}. */
    @Nullable
    private SpecialAccessSnapshot mSnapshot;
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

    @Nullable
    public SpecialAccessCatalog.Entry getSpecial() { return mSpecial; }

    public void setSpecial(@NonNull SpecialAccessCatalog.Entry special) {
        mSpecial = special;
    }

    public void load() {
        mLoading.postValue(true);
        mExecutor.submit(this::loadInternal);
    }

    @WorkerThread
    private void loadInternal() {
        try {
            if (mSpecial != null) {
                publish(loadSpecialRows(mSpecial));
                return;
            }
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
            publish(rows);
        } finally {
            mLoading.postValue(false);
        }
    }

    private void publish(@NonNull List<AppRow> rows) {
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
    }

    @WorkerThread
    @NonNull
    private List<AppRow> loadSpecialRows(@NonNull SpecialAccessCatalog.Entry entry) {
        int userId = UserHandleHidden.myUserId();
        PackageManager pm = getApplication().getPackageManager();
        List<PackageInfo> packages;
        try {
            packages = PackageManagerCompat.getInstalledPackages(
                    SpecialAccessCatalog.packageFlags(Collections.singletonList(entry)), userId);
        } catch (Exception th) {
            packages = Collections.emptyList();
        }
        if (packages == null) packages = Collections.emptyList();
        SpecialAccessSnapshot snapshot = SpecialAccessSnapshot.take(getApplication(), mAppOpsManager);
        mSnapshot = snapshot;
        List<AppRow> rows = new ArrayList<>();
        for (PackageInfo pi : packages) {
            if (pi == null || !entry.appliesTo(pi, snapshot)) continue;
            ApplicationInfo ai = pi.applicationInfo;
            int status = entry.status(pi, snapshot);
            Drawable icon = null;
            try {
                icon = ai.loadIcon(pm);
            } catch (Exception ignore) {}
            AppRow row = new AppRow(pi.packageName, ai.loadLabel(pm), icon,
                    (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0, status == SpecialAccess.GRANTED,
                    entry.canChange(pi, snapshot));
            row.stateKnown = status != SpecialAccess.UNKNOWN;
            row.settingsIntents = entry.settings(pi, snapshot);
            row.packageInfo = pi;
            rows.add(row);
        }
        return rows;
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
        if (mSpecial != null) {
            toggleSpecial(mSpecial, row);
            return;
        }
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

    private void toggleSpecial(@NonNull SpecialAccessCatalog.Entry entry, @NonNull AppRow row) {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            boolean changed = false;
            try {
                PackageInfo pi = row.packageInfo;
                if (pi == null) return;
                SpecialAccessSnapshot snapshot = mSnapshot != null ? mSnapshot
                        : SpecialAccessSnapshot.take(getApplication(), mAppOpsManager);
                boolean allow = !row.anyGranted;
                // Android can refuse quietly, so read the state back before calling it done
                changed = entry.set(pi, allow, snapshot)
                        && entry.status(pi, snapshot) == (allow ? SpecialAccess.GRANTED : SpecialAccess.DENIED);
            } finally {
                loadInternal();
                if (!changed) mToast.postValue(getApplication().getString(R.string.special_access_change_failed));
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

    /** Runs whatever the row's reference flag stands for right now: pin, unpin or restore. */
    public void referenceAction(@NonNull AppRow row) {
        switch (ReferenceAction.next(row.hasReference, row.referenceDrifted, row.anyModifiable)) {
            case ReferenceAction.RESTORE:
                restoreReference(row);
                break;
            case ReferenceAction.UNPIN:
                unpinReference(row);
                break;
            case ReferenceAction.PIN:
            default:
                pinReference(row);
                break;
        }
    }

    public void unpinReference(@NonNull AppRow row) {
        mLoading.postValue(true);
        mExecutor.submit(() -> {
            int unpinned = 0;
            int failed = 0;
            try {
                int userId = UserHandleHidden.myUserId();
                for (String permName : row.permissionNames) {
                    if (PermissionToggleHelper.loadReference(row.packageName, userId, permName) == null) continue;
                    if (PermissionToggleHelper.unpinReference(row.packageName, userId, permName)) {
                        unpinned++;
                    } else failed++;
                }
                mToast.postValue(getApplication().getString(failed == 0 && unpinned > 0
                        ? ReferenceAction.doneMessage(ReferenceAction.UNPIN)
                        : ReferenceAction.failedMessage(ReferenceAction.UNPIN)));
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
        if (mGroup == null) return;
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
        if (mGroup == null) return;
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
