// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.filters.FilterablePermissionInfo;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class PermissionsOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
        put("granted", TYPE_NONE);
        put("denied", TYPE_NONE);
        put("custom", TYPE_NONE);
        put("fixed", TYPE_NONE);
        put("with_flags", TYPE_INT_FLAGS);
        put("without_flags", TYPE_INT_FLAGS);
    }};

    private final Map<Integer, CharSequence> mPermissionFlags = new LinkedHashMap<Integer, CharSequence>() {{
        for (int i = 0; i < 18; ++i) {
            int flag = 1 << i;
            if ((PermissionCompat.MASK_PERMISSION_FLAGS_ALL & flag) != 0) {
                put(flag, PermissionCompat.permissionFlagToString(flag));
            }
        }
    }};

    public PermissionsOption() {
        super("permissions");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if (key.equals("with_flags") || key.equals("without_flags")) {
            return mPermissionFlags;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        List<String> permissions = result.getMatchedPermissions() != null
                ? result.getMatchedPermissions()
                : info.getAllPermissions();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true).setMatchedPermissions(permissions);
            case "eq": {
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.equals(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "contains": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.contains(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "starts_with": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.startsWith(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "ends_with": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission.endsWith(value)) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "regex": {
                Objects.requireNonNull(value);
                List<String> filteredPermissions = new ArrayList<>();
                for (String permission : permissions) {
                    if (regexValue.matcher(permission).matches()) {
                        filteredPermissions.add(permission);
                    }
                }
                return result.setMatched(!filteredPermissions.isEmpty())
                        .setMatchedPermissions(filteredPermissions);
            }
            case "granted":
            case "denied":
            case "custom":
            case "fixed":
            case "with_flags":
            case "without_flags":
                return testPermissionDetails(info, result);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    private TestResult testPermissionDetails(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        List<FilterablePermissionInfo> permissions = getPermissionDetails(info, result);
        List<String> filteredPermissions = new ArrayList<>();
        for (FilterablePermissionInfo permission : permissions) {
            if (matchesPermissionDetails(permission, key, intValue)) {
                filteredPermissions.add(permission.name);
            }
        }
        return result.setMatched(!filteredPermissions.isEmpty())
                .setMatchedPermissions(filteredPermissions);
    }

    @NonNull
    private static List<FilterablePermissionInfo> getPermissionDetails(@NonNull IFilterableAppInfo info,
                                                                      @NonNull TestResult result) {
        List<FilterablePermissionInfo> permissionDetails = info.getAllPermissionDetails();
        List<String> matchedPermissions = result.getMatchedPermissions();
        if (matchedPermissions == null) {
            return permissionDetails;
        }
        Set<String> matchedNames = new HashSet<>(matchedPermissions);
        List<FilterablePermissionInfo> filteredPermissionDetails = new ArrayList<>();
        for (FilterablePermissionInfo permission : permissionDetails) {
            if (matchedNames.contains(permission.name)) {
                filteredPermissionDetails.add(permission);
            }
        }
        return filteredPermissionDetails;
    }

    @VisibleForTesting
    static boolean matchesPermissionDetails(@NonNull FilterablePermissionInfo permission,
                                            @NonNull String key,
                                            int flags) {
        switch (key) {
            case "granted":
                return permission.granted;
            case "denied":
                return !permission.granted;
            case "custom":
                return permission.isCustom();
            case "fixed":
                return permission.isFixed();
            case "with_flags":
                return permission.hasAllPermissionFlags(flags);
            case "without_flags":
                return !permission.hasAllPermissionFlags(flags);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Permissions");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = '").append(value).append("'");
            case "contains":
                return sb.append(" contains '").append(value).append("'");
            case "starts_with":
                return sb.append(" starts with '").append(value).append("'");
            case "ends_with":
                return sb.append(" ends with '").append(value).append("'");
            case "regex":
                return sb.append(" matches '").append(value).append("'");
            case "granted":
                return sb.append(" granted");
            case "denied":
                return sb.append(" denied");
            case "custom":
                return sb.append(" custom");
            case "fixed":
                return sb.append(" fixed");
            case "with_flags":
                return sb.append(" with flags ").append(flagsToString("with_flags", intValue));
            case "without_flags":
                return sb.append(" without flags ").append(flagsToString("without_flags", intValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
