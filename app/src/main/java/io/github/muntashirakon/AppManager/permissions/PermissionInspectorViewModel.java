// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;

/**
 * Builds the inverted "permission -> apps" index by walking every installed
 * package once and tallying how many apps request, and how many have been
 * granted, each curated dangerous-permission group.
 */
public class PermissionInspectorViewModel extends AndroidViewModel {
    public static final class Row {
        @NonNull public final PermissionGroupCatalog.Group group;
        public final int requestedCount;
        public final int grantedCount;

        Row(@NonNull PermissionGroupCatalog.Group group, int requestedCount, int grantedCount) {
            this.group = group;
            this.requestedCount = requestedCount;
            this.grantedCount = grantedCount;
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

    private void loadInternal() {
        try {
            int userId = UserHandleHidden.myUserId();
            List<PackageInfo> packages;
            try {
                packages = PackageManagerCompat.getInstalledPackages(
                        PackageManager.GET_PERMISSIONS, userId);
            } catch (Throwable th) {
                packages = Collections.emptyList();
            }
            if (packages == null) packages = Collections.emptyList();
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
            mRows.postValue(rows);
        } finally {
            mLoading.postValue(false);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
    }
}
