// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;

public final class ProfileVisibilityDiagnostics {
    public static final String USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE";
    public static final String USER_TYPE_PROFILE_MANAGED = "android.os.usertype.profile.MANAGED";
    public static final String USER_TYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE";
    private static final int ANDROID_15 = 35;

    public enum ProfileKind {
        USER,
        PROFILE,
        MANAGED,
        PRIVATE,
        CLONE,
        GUEST,
        RESTRICTED
    }

    public enum HiddenProfileAccess {
        NOT_APPLICABLE,
        AVAILABLE_WHEN_UNLOCKED,
        NOT_VISIBLE_FROM_CURRENT_MODE_STATE
    }

    public static final class ProfileFacts {
        public final int id;
        public final int flags;
        public final int profileGroupId;
        @Nullable
        public final String userType;
        public final ProfileKind kind;
        public final boolean profile;
        public final boolean quietModeEnabled;
        public final boolean enabled;
        public final boolean ephemeral;

        private ProfileFacts(int id, int flags, int profileGroupId, @Nullable String userType) {
            this.id = id;
            this.flags = flags;
            this.profileGroupId = profileGroupId;
            this.userType = userType;
            this.kind = classify(id, flags, profileGroupId, userType);
            this.profile = kind == ProfileKind.PROFILE
                    || kind == ProfileKind.MANAGED
                    || kind == ProfileKind.PRIVATE
                    || kind == ProfileKind.CLONE;
            this.quietModeEnabled = isFlagSet(flags, android.content.pm.UserInfo.FLAG_QUIET_MODE);
            this.enabled = !isFlagSet(flags, android.content.pm.UserInfo.FLAG_DISABLED);
            this.ephemeral = isFlagSet(flags, android.content.pm.UserInfo.FLAG_EPHEMERAL);
        }
    }

    private ProfileVisibilityDiagnostics() {
    }

    @NonNull
    public static ProfileFacts buildFacts(int id, int flags, int profileGroupId, @Nullable String userType) {
        return new ProfileFacts(id, flags, profileGroupId, userType);
    }

    @NonNull
    public static ProfileFacts buildFacts(@NonNull android.content.pm.UserInfo userInfo) {
        String userType = readStringField(userInfo, "userType");
        if (userType == null) {
            userType = readStringMethod(userInfo, "getUserType");
        }
        if (userType == null && readBooleanMethod(userInfo, "isPrivateProfile")) {
            userType = USER_TYPE_PROFILE_PRIVATE;
        } else if (userType == null && readBooleanMethod(userInfo, "isCloneProfile")) {
            userType = USER_TYPE_PROFILE_CLONE;
        }
        return buildFacts(userInfo.id, userInfo.flags, userInfo.profileGroupId, userType);
    }

    @NonNull
    public static CharSequence getUserProfileDetails(@NonNull Context context, @NonNull ProfileFacts facts) {
        List<String> labels = new ArrayList<>(4);
        int kindRes = getKindLabelRes(facts.kind);
        if (kindRes != 0) {
            labels.add(context.getString(kindRes));
        }
        if (facts.quietModeEnabled) {
            labels.add(context.getString(R.string.user_profile_state_quiet));
        }
        if (!facts.enabled) {
            labels.add(context.getString(R.string.user_profile_state_disabled));
        }
        if (facts.ephemeral) {
            labels.add(context.getString(R.string.user_profile_state_ephemeral));
        }
        return String.join(", ", labels);
    }

    @NonNull
    public static CharSequence getSelectedUsersSummary(@NonNull Context context) {
        String base = context.getString(R.string.pref_selected_users_msg);
        HiddenProfileAccess access = getHiddenProfileAccess(Build.VERSION.SDK_INT,
                hasHiddenProfilesPermission(context), isHomeRoleHeld(context));
        if (access == HiddenProfileAccess.NOT_VISIBLE_FROM_CURRENT_MODE_STATE) {
            return base + "\n" + context.getString(R.string.profile_visibility_not_visible_current_mode);
        }
        if (access == HiddenProfileAccess.AVAILABLE_WHEN_UNLOCKED) {
            return base + "\n" + context.getString(R.string.profile_visibility_available_when_unlocked);
        }
        return base;
    }

    @NonNull
    public static HiddenProfileAccess getHiddenProfileAccess(int sdkInt, boolean hasHiddenProfilesPermission,
                                                            boolean holdsHomeRole) {
        if (sdkInt < ANDROID_15) {
            return HiddenProfileAccess.NOT_APPLICABLE;
        }
        if (hasHiddenProfilesPermission && holdsHomeRole) {
            return HiddenProfileAccess.AVAILABLE_WHEN_UNLOCKED;
        }
        return HiddenProfileAccess.NOT_VISIBLE_FROM_CURRENT_MODE_STATE;
    }

    public static boolean hasHiddenProfilesPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < ANDROID_15) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, ManifestCompat.permission.ACCESS_HIDDEN_PROFILES)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isHomeRoleHeld(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        try {
            RoleManager roleManager = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
            return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @NonNull
    private static ProfileKind classify(int id, int flags, int profileGroupId, @Nullable String userType) {
        if (USER_TYPE_PROFILE_PRIVATE.equals(userType)) {
            return ProfileKind.PRIVATE;
        }
        if (USER_TYPE_PROFILE_CLONE.equals(userType)) {
            return ProfileKind.CLONE;
        }
        if (USER_TYPE_PROFILE_MANAGED.equals(userType)
                || isFlagSet(flags, android.content.pm.UserInfo.FLAG_MANAGED_PROFILE)) {
            return ProfileKind.MANAGED;
        }
        if (isFlagSet(flags, android.content.pm.UserInfo.FLAG_GUEST)) {
            return ProfileKind.GUEST;
        }
        if (isFlagSet(flags, android.content.pm.UserInfo.FLAG_RESTRICTED)) {
            return ProfileKind.RESTRICTED;
        }
        if (profileGroupId != android.content.pm.UserInfo.NO_PROFILE_GROUP_ID && profileGroupId != id) {
            return ProfileKind.PROFILE;
        }
        return ProfileKind.USER;
    }

    private static int getKindLabelRes(@NonNull ProfileKind kind) {
        switch (kind) {
            case PRIVATE:
                return R.string.user_profile_kind_private_space;
            case CLONE:
                return R.string.user_profile_kind_clone;
            case MANAGED:
                return R.string.user_profile_kind_work;
            case GUEST:
                return R.string.user_profile_kind_guest;
            case RESTRICTED:
                return R.string.user_profile_kind_restricted;
            case PROFILE:
                return R.string.user_profile_kind_profile;
            case USER:
            default:
                return 0;
        }
    }

    private static boolean isFlagSet(int flags, int flag) {
        return (flags & flag) != 0;
    }

    @Nullable
    private static String readStringField(@NonNull Object target, @NonNull String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object value = field.get(target);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String readStringMethod(@NonNull Object target, @NonNull String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean readBooleanMethod(@NonNull Object target, @NonNull String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
