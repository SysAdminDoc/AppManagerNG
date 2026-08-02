// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;
import io.github.muntashirakon.AppManager.filters.FilteringUtils;
import io.github.muntashirakon.AppManager.profiles.ProfileLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;


public class AppsProfile extends AppsBaseProfile {
    @NonNull
    public String[] packages;  // packages (a list of packages)

    protected AppsProfile(@NonNull String profileId, @NonNull String profileName) {
        super(profileId, profileName, PROFILE_TYPE_APPS);
        packages = EmptyArray.STRING;
    }

    protected AppsProfile(@NonNull String profileId, @NonNull String profileName, @NonNull AppsProfile profile) {
        super(profileId, profileName, profile);
        packages = profile.packages.clone();
    }

    @Override
    public ProfileApplierResult apply(@NonNull String state, @Nullable ProfileLogger logger, @Nullable ProgressHandler progressHandler) {
        return apply(state, logger, progressHandler, null);
    }

    @NonNull
    public ProfileApplierResult apply(@NonNull String state, @Nullable ProfileLogger logger,
                                      @Nullable ProgressHandler progressHandler,
                                      @Nullable FilterItem routineFilter) {
        if (packages.length == 0) return ProfileApplierResult.EMPTY_RESULT;
        int[] users = this.users == null ? Users.getUsersIds() : this.users;
        if (routineFilter == null) {
            int size = packages.length * users.length;
            List<String> packageList = new ArrayList<>(size);
            List<Integer> assocUsers = new ArrayList<>(size);
            for (String packageName : packages) {
                for (int user : users) {
                    packageList.add(packageName);
                    assocUsers.add(user);
                }
            }
            return apply(packageList, assocUsers, state, logger, progressHandler);
        }

        Set<String> packageSet = new HashSet<>();
        for (String packageName : packages) {
            packageSet.add(packageName);
        }
        List<FilterableAppInfo> filterableAppInfoList = FilteringUtils.loadFilterableAppInfo(users, false,
                routineFilter.hasFilterOptionType("domain_links"));
        List<String> packageList = new ArrayList<>();
        List<Integer> assocUsers = new ArrayList<>();
        for (FilterableAppInfo info : filterableAppInfoList) {
            if (packageSet.contains(info.getPackageName()) && routineFilter.matches(info)) {
                packageList.add(info.getPackageName());
                assocUsers.add(info.getUserId());
            }
        }
        if (logger != null) {
            logger.println("====> Routine filter matched packages: " + packageList.size());
        }
        if (packageList.isEmpty()) {
            return ProfileApplierResult.EMPTY_RESULT;
        }
        return apply(packageList, assocUsers, state, logger, progressHandler);
    }

    public void appendPackages(@NonNull String[] packageList) {
        List<String> uniquePackages = new ArrayList<>();
        for (String newPackage : packageList) {
            if (!ArrayUtils.contains(packages, newPackage)) {
                uniquePackages.add(newPackage);
            }
        }
        packages = ArrayUtils.concatElements(String.class, packages, uniquePackages.toArray(new String[0]));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return super.serializeToJson()
                .put("packages", JSONUtils.getJSONArray(packages));
    }

    protected AppsProfile(@NonNull JSONObject profileObj) throws JSONException {
        super(profileObj);
        packages = JSONUtils.getArray(String.class, profileObj.getJSONArray("packages"));
    }

    public static final JsonDeserializer.Creator<AppsProfile> DESERIALIZER = AppsProfile::new;
}
