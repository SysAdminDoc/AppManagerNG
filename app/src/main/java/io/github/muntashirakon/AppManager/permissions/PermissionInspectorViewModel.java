// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandleHidden;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Builds the inverted "permission -> apps" index by walking every installed
 * package once and tallying how many apps request, and how many have been
 * granted, each curated dangerous-permission group. Special app access
 * (overlay, battery, usage access and the rest) follows in a second pass,
 * since reading it costs a system call per app.
 */
public class PermissionInspectorViewModel extends AndroidViewModel {
    private static final String TAG = PermissionInspectorViewModel.class.getSimpleName();

    public static final class Row {
        /** Set for a runtime permission group. */
        @Nullable
        public final PermissionGroupCatalog.Group group;
        /** Set for a special app access entry. */
        @Nullable
        public final SpecialAccessCatalog.Entry special;
        @StringRes
        public final int labelRes;
        @StringRes
        public final int summaryRes;
        @StringRes
        public final int sectionRes;
        @DrawableRes
        public final int iconRes;
        public final int requestedCount;
        public final int grantedCount;
        /** False when App Manager couldn't read the state for some of these apps. */
        public final boolean stateKnown;

        Row(@NonNull PermissionGroupCatalog.Group group, int requestedCount, int grantedCount) {
            this.group = group;
            this.special = null;
            this.labelRes = group.labelRes;
            this.summaryRes = group.summaryRes;
            this.sectionRes = R.string.perm_section_runtime;
            this.iconRes = group.iconRes;
            this.requestedCount = requestedCount;
            this.grantedCount = grantedCount;
            this.stateKnown = true;
        }

        Row(@NonNull SpecialAccessCatalog.Entry special, int requestedCount, int grantedCount, boolean stateKnown) {
            this.group = null;
            this.special = special;
            this.labelRes = special.labelRes;
            this.summaryRes = special.summaryRes;
            this.sectionRes = special.sectionRes;
            this.iconRes = special.iconRes;
            this.requestedCount = requestedCount;
            this.grantedCount = grantedCount;
            this.stateKnown = stateKnown;
        }
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<List<Row>> mRows = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mLoading = new MutableLiveData<>(false);

    public PermissionInspectorViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<Row>> getRows() { return mRows; }
    public MutableLiveData<Boolean> getLoading() { return mLoading; }

    public void load() {
        mLoading.postValue(true);
        mExecutor.submit(this::loadInternal);
    }

    @WorkerThread
    private void loadInternal() {
        try {
            int userId = UserHandleHidden.myUserId();
            List<Row> rows = runtimeRows(installedPackages(PackageManager.GET_PERMISSIONS, userId));
            // Show the permission groups right away, special access follows
            mRows.postValue(rows);
            List<SpecialAccessCatalog.Entry> entries = SpecialAccessCatalog.available();
            if (entries.isEmpty()) return;
            List<Row> all = new ArrayList<>(rows);
            all.addAll(specialRows(entries, installedPackages(SpecialAccessCatalog.packageFlags(entries), userId)));
            mRows.postValue(all);
        } finally {
            mLoading.postValue(false);
        }
    }

    @NonNull
    private static List<PackageInfo> installedPackages(int flags, int userId) {
        try {
            List<PackageInfo> packages = PackageManagerCompat.getInstalledPackages(flags, userId);
            return packages != null ? packages : Collections.emptyList();
        } catch (Exception e) {
            Log.w(TAG, "Could not list installed packages.", e);
            return Collections.emptyList();
        }
    }

    @NonNull
    private static List<Row> runtimeRows(@NonNull List<PackageInfo> packages) {
        List<PermissionGroupCatalog.Group> groups = PermissionGroupCatalog.all();
        Map<String, int[]> tally = new HashMap<>();
        for (PermissionGroupCatalog.Group g : groups) {
            tally.put(g.id, new int[] { 0, 0 });
        }
        for (PackageInfo pi : packages) {
            if (pi == null || pi.requestedPermissions == null) continue;
            for (PermissionGroupCatalog.Group g : groups) {
                boolean requested = false;
                boolean granted = false;
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    String name = pi.requestedPermissions[i];
                    if (!g.permissions.contains(name)) continue;
                    requested = true;
                    if (pi.requestedPermissionsFlags != null
                            && i < pi.requestedPermissionsFlags.length
                            && (pi.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        granted = true;
                    }
                }
                int[] t = tally.get(g.id);
                if (t == null) continue;
                if (requested) t[0]++;
                if (granted) t[1]++;
            }
        }
        List<Row> rows = new ArrayList<>();
        for (PermissionGroupCatalog.Group g : groups) {
            int[] t = tally.get(g.id);
            if (t == null) continue;
            rows.add(new Row(g, t[0], t[1]));
        }
        return rows;
    }

    @WorkerThread
    @NonNull
    private List<Row> specialRows(@NonNull List<SpecialAccessCatalog.Entry> entries,
                                  @NonNull List<PackageInfo> packages) {
        SpecialAccessSnapshot snapshot = SpecialAccessSnapshot.take(getApplication(), new AppOpsManagerCompat());
        List<Row> rows = new ArrayList<>(entries.size());
        for (SpecialAccessCatalog.Entry entry : entries) {
            int applicable = 0;
            int granted = 0;
            boolean known = true;
            for (PackageInfo pi : packages) {
                if (pi == null || !entry.appliesTo(pi, snapshot)) continue;
                applicable++;
                int status = entry.status(pi, snapshot);
                if (status == SpecialAccess.GRANTED) granted++;
                else if (status == SpecialAccess.UNKNOWN) known = false;
            }
            rows.add(new Row(entry, applicable, granted, known));
        }
        return rows;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
    }
}
