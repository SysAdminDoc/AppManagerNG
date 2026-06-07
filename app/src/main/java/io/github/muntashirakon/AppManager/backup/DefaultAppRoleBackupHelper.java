// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.settings.Ops;

public final class DefaultAppRoleBackupHelper {
    public static final String ROLE_BROWSER = "android.app.role.BROWSER";
    public static final String ROLE_DIALER = "android.app.role.DIALER";
    public static final String ROLE_HOME = "android.app.role.HOME";
    public static final String ROLE_SMS = "android.app.role.SMS";

    private static final String TAG = DefaultAppRoleBackupHelper.class.getSimpleName();
    private static final String PACKAGE_PREFIX = "package:";
    private static final String[] SUPPORTED_ROLES = new String[]{
            ROLE_DIALER,
            ROLE_SMS,
            ROLE_HOME,
            ROLE_BROWSER,
    };

    private DefaultAppRoleBackupHelper() {
    }

    @NonNull
    public static String[] getHeldDefaultRoles(@NonNull String packageName, @UserIdInt int userId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return new String[0];
        }
        if (!canManageRoleHolders()) {
            return new String[0];
        }
        List<String> roles = new ArrayList<>(SUPPORTED_ROLES.length);
        for (String roleName : SUPPORTED_ROLES) {
            List<String> holders = getRoleHolders(userId, roleName);
            if (holders != null && holders.contains(packageName)) {
                roles.add(roleName);
            }
        }
        return roles.toArray(new String[0]);
    }

    @NonNull
    public static List<RoleRebindRequest> restoreHeldDefaultRoles(@NonNull String packageName,
                                                                  @UserIdInt int userId,
                                                                  @Nullable String[] roleNames) {
        List<RoleRebindRequest> pendingRequests = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return pendingRequests;
        }
        for (String roleName : sanitizeRoleNames(roleNames)) {
            if (!canManageRoleHolders()) {
                pendingRequests.add(new RoleRebindRequest(packageName, userId, roleName));
                continue;
            }
            Boolean alreadyHeld = isRoleHeld(packageName, userId, roleName);
            if (Boolean.TRUE.equals(alreadyHeld)) {
                continue;
            }
            Runner.Result result = Runner.runCommand(new String[]{
                    "cmd", "role", "add-role-holder", "--user", String.valueOf(userId), roleName, packageName
            });
            if (!result.isSuccessful()) {
                pendingRequests.add(new RoleRebindRequest(packageName, userId, roleName));
                continue;
            }
            Boolean heldAfterRestore = isRoleHeld(packageName, userId, roleName);
            if (Boolean.FALSE.equals(heldAfterRestore)) {
                pendingRequests.add(new RoleRebindRequest(packageName, userId, roleName));
            }
        }
        return pendingRequests;
    }

    @NonNull
    public static String[] sanitizeRoleNames(@Nullable String[] roleNames) {
        if (roleNames == null || roleNames.length == 0) {
            return new String[0];
        }
        Set<String> sanitized = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            if (roleName == null) {
                continue;
            }
            String trimmedRoleName = roleName.trim();
            if (!isEmpty(trimmedRoleName) && isSupportedRole(trimmedRoleName)) {
                sanitized.add(trimmedRoleName);
            }
        }
        return sanitized.toArray(new String[0]);
    }

    public static boolean isSupportedRole(@NonNull String roleName) {
        for (String supportedRole : SUPPORTED_ROLES) {
            if (supportedRole.equals(roleName)) {
                return true;
            }
        }
        return false;
    }

    @StringRes
    public static int getRoleLabelRes(@NonNull String roleName) {
        switch (roleName) {
            case ROLE_DIALER:
                return R.string.default_app_role_phone;
            case ROLE_SMS:
                return R.string.default_app_role_sms;
            case ROLE_HOME:
                return R.string.default_app_role_home;
            case ROLE_BROWSER:
                return R.string.default_app_role_browser;
            default:
                return R.string.state_unknown;
        }
    }

    @NonNull
    public static CharSequence getRoleLabel(@NonNull Context context, @NonNull String roleName) {
        return context.getText(getRoleLabelRes(roleName));
    }

    private static boolean canManageRoleHolders() {
        return Ops.isDirectRoot() || LocalServices.alive();
    }

    @SuppressLint("NewApi")
    @Nullable
    private static Boolean isRoleHeld(@NonNull String packageName, @UserIdInt int userId,
                                      @NonNull String roleName) {
        List<String> holders = getRoleHolders(userId, roleName);
        return holders == null ? null : holders.contains(packageName);
    }

    @SuppressLint("NewApi")
    @Nullable
    private static List<String> getRoleHolders(@UserIdInt int userId, @NonNull String roleName) {
        Runner.Result result = Runner.runCommand(new String[]{
                "cmd", "role", "get-role-holders", "--user", String.valueOf(userId), roleName
        });
        if (!result.isSuccessful()) {
            Log.w(TAG, "Could not read role holders for %s (user %d).", roleName, userId);
            return null;
        }
        List<String> holders = new ArrayList<>();
        for (String line : result.getOutputAsList()) {
            appendRoleHoldersFromLine(holders, line);
        }
        return holders;
    }

    @VisibleForTesting
    static void appendRoleHoldersFromLine(@NonNull List<String> holders, @Nullable String line) {
        if (isEmpty(line)) {
            return;
        }
        String value = line.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
            String[] splitValues = value.split(",");
            for (String splitValue : splitValues) {
                appendRoleHoldersFromLine(holders, splitValue);
            }
            return;
        }
        if (value.startsWith(PACKAGE_PREFIX)) {
            value = value.substring(PACKAGE_PREFIX.length()).trim();
        }
        if (!isEmpty(value)) {
            holders.add(value);
        }
    }

    private static boolean isEmpty(@Nullable CharSequence value) {
        return value == null || value.length() == 0;
    }

    public static final class RoleRebindRequest implements Parcelable {
        @NonNull
        private final String mPackageName;
        @UserIdInt
        private final int mUserId;
        @NonNull
        private final String mRoleName;

        public RoleRebindRequest(@NonNull String packageName, @UserIdInt int userId,
                                 @NonNull String roleName) {
            mPackageName = packageName;
            mUserId = userId;
            mRoleName = roleName;
        }

        protected RoleRebindRequest(@NonNull Parcel in) {
            mPackageName = Objects.requireNonNull(in.readString());
            mUserId = in.readInt();
            mRoleName = Objects.requireNonNull(in.readString());
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @UserIdInt
        public int getUserId() {
            return mUserId;
        }

        @NonNull
        public String getRoleName() {
            return mRoleName;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(mPackageName);
            dest.writeInt(mUserId);
            dest.writeString(mRoleName);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<RoleRebindRequest> CREATOR = new Creator<RoleRebindRequest>() {
            @Override
            public RoleRebindRequest createFromParcel(Parcel in) {
                return new RoleRebindRequest(in);
            }

            @Override
            public RoleRebindRequest[] newArray(int size) {
                return new RoleRebindRequest[size];
            }
        };
    }
}
