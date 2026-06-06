// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_BACKUP;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_CLEAR_CACHE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_CLEAR_DATA;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_DISABLE_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_ENABLE_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_FORCE_STOP;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_FREEZE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_INSTALL_FROM_URI;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RESTORE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RUN_PROFILE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_SCAN_TRACKERS;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_UNFREEZE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_UNINSTALL;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_BACKUP_FLAGS;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_BACKUP_NAME;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_DRY_RUN;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PACKAGES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PACKAGE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_ID;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_OVERRIDES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_STATE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_URI;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_USER;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_USERS;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierReceiver;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.self.SelfUriManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

final class AutomationRequest {
    private static final String LEGACY_APPMANAGER_SCHEME = "appmanager";
    private static final int MAX_PROFILE_OVERRIDES_LENGTH = 64 * 1024;

    @NonNull
    final String action;
    @NonNull
    final ArrayList<String> packages;
    @NonNull
    final ArrayList<Integer> users;
    @Nullable
    final String component;
    @Nullable
    final String profileId;
    @Nullable
    final String profileState;
    @Nullable
    final JSONObject profileOverrides;
    @Nullable
    final String backupName;
    final int backupFlags;
    final boolean hasBackupFlags;
    @Nullable
    final String uri;
    final boolean dryRun;

    private AutomationRequest(@NonNull String action,
                              @Nullable ArrayList<String> packages,
                              @Nullable ArrayList<Integer> users,
                              @Nullable String component,
                              @Nullable String profileId,
                              @Nullable String profileState,
                              @Nullable JSONObject profileOverrides,
                              @Nullable String backupName,
                              int backupFlags,
                              boolean hasBackupFlags,
                              @Nullable String uri,
                              boolean dryRun) {
        this.action = action;
        this.packages = packages != null ? packages : new ArrayList<>();
        this.users = users != null ? users : new ArrayList<>();
        this.component = trimToNull(component);
        this.profileId = trimToNull(profileId);
        this.profileState = trimToNull(profileState);
        this.profileOverrides = profileOverrides;
        this.backupName = trimToNull(backupName);
        this.backupFlags = backupFlags;
        this.hasBackupFlags = hasBackupFlags;
        this.uri = trimToNull(uri);
        this.dryRun = dryRun;
        validate();
    }

    @Nullable
    static AutomationRequest fromIntent(@NonNull Intent intent) throws JSONException {
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && data != null) {
            return fromUri(data);
        }
        String action = intent.getAction();
        if (!AutomationIntents.isAutomationAction(action)) {
            return null;
        }
        ArrayList<String> packages = getStringListExtra(intent, EXTRA_PACKAGES);
        String packageName = getStringExtra(intent, EXTRA_PACKAGE);
        if (packageName != null) {
            packages.add(packageName);
        }
        int fallbackUser = getIntExtra(intent, EXTRA_USER, UserHandleHidden.myUserId());
        ArrayList<Integer> users = getUserListExtra(intent, EXTRA_USERS, packages.size(), fallbackUser);
        String profileId = getStringExtra(intent, EXTRA_PROFILE_ID);
        if (ACTION_BACKUP.equals(action) && packages.isEmpty() && profileId != null) {
            action = ACTION_RUN_PROFILE;
        }
        JSONObject profileOverrides = getProfileOverrides(getStringExtra(intent, EXTRA_PROFILE_OVERRIDES));
        if (ACTION_RUN_PROFILE.equals(action)) {
            profileOverrides = ProfileApplierReceiver.mergeRuntimePackageOverride(profileOverrides,
                    getStringExtra(intent, ProfileApplierReceiver.EXTRA_RUNTIME_PACKAGE));
        }
        return new AutomationRequest(action,
                packages,
                users,
                getStringExtra(intent, EXTRA_COMPONENT),
                profileId,
                getStringExtra(intent, EXTRA_PROFILE_STATE),
                profileOverrides,
                getStringExtra(intent, EXTRA_BACKUP_NAME),
                getIntExtra(intent, EXTRA_BACKUP_FLAGS, BackupFlags.BACKUP_NOTHING),
                hasExtra(intent, EXTRA_BACKUP_FLAGS),
                getStringExtra(intent, EXTRA_URI),
                getBooleanExtra(intent, EXTRA_DRY_RUN, false));
    }

    @Nullable
    static AutomationRequest fromUri(@NonNull Uri uri) throws JSONException {
        String scheme = lower(uri.getScheme());
        String host = lower(uri.getHost());
        if (scheme == null || host == null) {
            return null;
        }

        if ((SelfUriManager.AM_SCHEME.equals(scheme) && ("profile".equals(host) || "run-profile".equals(host)))
                || (LEGACY_APPMANAGER_SCHEME.equals(scheme) && "run-profile".equals(host))) {
            return profileRequestFromUri(uri);
        }
        if (SelfUriManager.AM_SCHEME.equals(scheme) && "install".equals(host)) {
            String source = firstNonEmpty(query(uri, "source"), query(uri, "uri"));
            return new AutomationRequest(ACTION_INSTALL_FROM_URI, null, null, null, null, null, null,
                    null, BackupFlags.BACKUP_NOTHING, false, source, getDryRun(uri));
        }
        if (!SelfUriManager.AM_SCHEME.equals(scheme)) {
            return null;
        }

        String action = actionForHost(host);
        if (action == null) {
            return null;
        }
        ArrayList<String> packages = packagesFromUri(uri);
        ArrayList<Integer> users = usersFromUri(uri, packages.size());
        return new AutomationRequest(action,
                packages,
                users,
                firstNonEmpty(query(uri, "component"), query(uri, "cmp")),
                null,
                null,
                null,
                firstNonEmpty(query(uri, "backup_name"), query(uri, "backup-name")),
                getIntQuery(uri, BackupFlags.BACKUP_NOTHING, "backup_flags", "backup-flags"),
                hasAnyQuery(uri, "backup_flags", "backup-flags"),
                null,
                getDryRun(uri));
    }

    private static AutomationRequest profileRequestFromUri(@NonNull Uri uri) throws JSONException {
        List<String> segments = uri.getPathSegments();
        String host = lower(uri.getHost());
        String profileId = null;
        if ("profile".equals(host)) {
            if (segments.size() >= 2 && "run".equals(lower(segments.get(1)))) {
                profileId = segments.get(0);
            }
        } else if (!segments.isEmpty()) {
            profileId = segments.get(0);
        }
        JSONObject overrides = getProfileOverrides(firstNonEmpty(
                query(uri, "profile_overrides"),
                query(uri, "profile-overrides"),
                query(uri, "overrides")));
        ArrayList<String> packages = packageQueriesFromUri(uri);
        if (!packages.isEmpty()) {
            if (overrides == null) {
                overrides = new JSONObject();
            }
            overrides.put("packages", new JSONArray(packages));
        }
        if (hasAnyQuery(uri, "backup_flags", "backup-flags") || hasAnyQuery(uri, "backup_name", "backup-name")) {
            if (overrides == null) {
                overrides = new JSONObject();
            }
            JSONObject backupData = overrides.optJSONObject("backup_data");
            if (backupData == null) {
                backupData = new JSONObject();
            }
            if (hasAnyQuery(uri, "backup_flags", "backup-flags")) {
                backupData.put("flags", getIntQuery(uri, BackupFlags.BACKUP_NOTHING, "backup_flags", "backup-flags"));
            }
            String backupName = firstNonEmpty(query(uri, "backup_name"), query(uri, "backup-name"));
            if (backupName != null) {
                backupData.put("name", backupName);
            }
            overrides.put("backup_data", backupData);
        }
        return new AutomationRequest(ACTION_RUN_PROFILE, null, null, null, profileId,
                firstNonEmpty(query(uri, "state"), query(uri, "profile_state"), query(uri, "profile-state")),
                overrides, null, BackupFlags.BACKUP_NOTHING, false, null, getDryRun(uri));
    }

    @Nullable
    private static String actionForHost(@NonNull String host) {
        switch (host) {
            case "freeze":
                return ACTION_FREEZE;
            case "unfreeze":
                return ACTION_UNFREEZE;
            case "force-stop":
            case "force_stop":
                return ACTION_FORCE_STOP;
            case "clear-cache":
            case "clear_cache":
                return ACTION_CLEAR_CACHE;
            case "clear-data":
            case "clear_data":
                return ACTION_CLEAR_DATA;
            case "uninstall":
                return ACTION_UNINSTALL;
            case "backup":
                return ACTION_BACKUP;
            case "restore":
                return ACTION_RESTORE;
            case "disable-component":
            case "disable_component":
                return ACTION_DISABLE_COMPONENT;
            case "enable-component":
            case "enable_component":
                return ACTION_ENABLE_COMPONENT;
            case "scan-trackers":
            case "scan_trackers":
                return ACTION_SCAN_TRACKERS;
            default:
                return null;
        }
    }

    @NonNull
    private static ArrayList<String> packagesFromUri(@NonNull Uri uri) {
        ArrayList<String> packages = new ArrayList<>();
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty()) {
            packages.add(segments.get(0));
        }
        packages.addAll(packageQueriesFromUri(uri));
        return packages;
    }

    @NonNull
    private static ArrayList<String> packageQueriesFromUri(@NonNull Uri uri) {
        ArrayList<String> packages = new ArrayList<>();
        addSplitValues(packages, query(uri, "package"));
        addSplitValues(packages, query(uri, "pkg"));
        addSplitValues(packages, query(uri, "packages"));
        return packages;
    }

    @NonNull
    private static ArrayList<Integer> usersFromUri(@NonNull Uri uri, int packageCount) {
        ArrayList<Integer> users = new ArrayList<>();
        addSplitInts(users, query(uri, "users"));
        if (users.isEmpty()) {
            int userId = getIntQuery(uri, UserHandleHidden.myUserId(), "user");
            for (int i = 0; i < packageCount; ++i) {
                users.add(userId);
            }
        } else if (users.size() == 1 && packageCount > 1) {
            int userId = users.get(0);
            while (users.size() < packageCount) {
                users.add(userId);
            }
        }
        return users;
    }

    private void validate() {
        if (ACTION_RUN_PROFILE.equals(action)) {
            if (profileId == null) {
                throw new IllegalArgumentException("Missing " + EXTRA_PROFILE_ID);
            }
            if (profileState != null && !BaseProfile.STATE_ON.equals(profileState)
                    && !BaseProfile.STATE_OFF.equals(profileState)) {
                throw new IllegalArgumentException("Invalid " + EXTRA_PROFILE_STATE);
            }
            return;
        }
        if (ACTION_INSTALL_FROM_URI.equals(action)) {
            if (uri == null) {
                throw new IllegalArgumentException("Missing " + EXTRA_URI);
            }
            return;
        }
        Integer batchOp = AutomationIntents.getBatchOpForAction(action);
        if (batchOp != null || ACTION_SCAN_TRACKERS.equals(action)) {
            validatePackages();
            if ((ACTION_DISABLE_COMPONENT.equals(action) || ACTION_ENABLE_COMPONENT.equals(action))) {
                if (packages.size() != 1) {
                    throw new IllegalArgumentException("Component automation expects exactly one package");
                }
                if (component == null) {
                    throw new IllegalArgumentException("Missing " + EXTRA_COMPONENT);
                }
            }
            return;
        }
        throw new IllegalArgumentException("Unsupported automation action: " + action);
    }

    private void validatePackages() {
        packages.removeAll(Collections.singleton(null));
        for (int i = 0; i < packages.size(); ++i) {
            String packageName = packages.get(i).trim();
            if (!PackageUtils.validateName(packageName)) {
                throw new IllegalArgumentException("Invalid package name: " + packageName);
            }
            packages.set(i, packageName);
        }
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("Missing " + EXTRA_PACKAGE);
        }
        if (users.isEmpty()) {
            int userId = UserHandleHidden.myUserId();
            for (int i = 0; i < packages.size(); ++i) {
                users.add(userId);
            }
        } else if (users.size() == 1 && packages.size() > 1) {
            int userId = users.get(0);
            while (users.size() < packages.size()) {
                users.add(userId);
            }
        }
        for (int userId : users) {
            if (userId < 0) {
                throw new IllegalArgumentException("Invalid " + EXTRA_USER + ": " + userId);
            }
        }
        if (users.size() != packages.size()) {
            throw new IllegalArgumentException(EXTRA_USERS + " size must match package count");
        }
    }

    @Nullable
    private static JSONObject getProfileOverrides(@Nullable String value) throws JSONException {
        String json = trimToNull(value);
        if (json == null) {
            return null;
        }
        if (json.length() > MAX_PROFILE_OVERRIDES_LENGTH) {
            throw new IllegalArgumentException(EXTRA_PROFILE_OVERRIDES + " is too large");
        }
        return new JSONObject(json);
    }

    @NonNull
    private static ArrayList<String> getStringListExtra(@NonNull Intent intent, @NonNull String name) {
        ArrayList<String> values = new ArrayList<>();
        Object extra = getExtra(intent, name);
        if (extra instanceof String[]) {
            values.addAll(Arrays.asList((String[]) extra));
        } else if (extra instanceof ArrayList) {
            for (Object item : (ArrayList<?>) extra) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (extra instanceof Iterable) {
            for (Object item : (Iterable<?>) extra) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (extra != null && extra.getClass().isArray()) {
            int length = Array.getLength(extra);
            for (int i = 0; i < length; ++i) {
                Object item = Array.get(extra, i);
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (extra != null) {
            addSplitValues(values, String.valueOf(extra));
        }
        return values;
    }

    @NonNull
    private static ArrayList<Integer> getUserListExtra(@NonNull Intent intent, @NonNull String name,
                                                       int packageCount, int fallbackUser) {
        ArrayList<Integer> users = new ArrayList<>();
        Object extra = getExtra(intent, name);
        if (extra instanceof int[]) {
            for (int user : (int[]) extra) {
                users.add(user);
            }
        } else if (extra instanceof Integer[]) {
            users.addAll(Arrays.asList((Integer[]) extra));
        } else if (extra instanceof ArrayList) {
            for (Object item : (ArrayList<?>) extra) {
                addInt(users, item);
            }
        } else if (extra instanceof Iterable) {
            for (Object item : (Iterable<?>) extra) {
                addInt(users, item);
            }
        } else if (extra != null && extra.getClass().isArray()) {
            int length = Array.getLength(extra);
            for (int i = 0; i < length; ++i) {
                addInt(users, Array.get(extra, i));
            }
        } else if (extra != null) {
            addSplitInts(users, String.valueOf(extra));
        }
        if (users.isEmpty()) {
            for (int i = 0; i < packageCount; ++i) {
                users.add(fallbackUser);
            }
        } else if (users.size() == 1 && packageCount > 1) {
            int userId = users.get(0);
            while (users.size() < packageCount) {
                users.add(userId);
            }
        }
        return users;
    }

    @Nullable
    private static String getStringExtra(@NonNull Intent intent, @NonNull String name) {
        Object value = getExtra(intent, name);
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean getBooleanExtra(@NonNull Intent intent, @NonNull String name, boolean fallback) {
        Object value = getExtra(intent, name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        return parseBoolean(String.valueOf(value), fallback);
    }

    private static int getIntExtra(@NonNull Intent intent, @NonNull String name, int fallback) {
        Object value = getExtra(intent, name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return fallback;
        }
    }

    private static boolean hasExtra(@NonNull Intent intent, @NonNull String name) {
        Bundle extras = intent.getExtras();
        return extras != null && extras.containsKey(name);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Object getExtra(@NonNull Intent intent, @NonNull String name) {
        Bundle extras = intent.getExtras();
        return extras != null ? extras.get(name) : null;
    }

    private static void addSplitValues(@NonNull ArrayList<String> out, @Nullable String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        for (String part : normalized.split("[,\\n]")) {
            String trimmed = trimToNull(part);
            if (trimmed != null) {
                out.add(trimmed);
            }
        }
    }

    private static void addSplitInts(@NonNull ArrayList<Integer> out, @Nullable String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        for (String part : normalized.split("[,\\n]")) {
            addInt(out, part);
        }
    }

    private static void addInt(@NonNull ArrayList<Integer> out, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number) {
            out.add(((Number) value).intValue());
            return;
        }
        try {
            out.add(Integer.parseInt(String.valueOf(value).trim()));
        } catch (NumberFormatException ignore) {
        }
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    @Nullable
    private static String query(@NonNull Uri uri, @NonNull String name) {
        return trimToNull(uri.getQueryParameter(name));
    }

    private static boolean hasAnyQuery(@NonNull Uri uri, @NonNull String... names) {
        for (String name : names) {
            if (uri.getQueryParameter(name) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean getDryRun(@NonNull Uri uri) {
        return getBooleanQuery(uri, false, "dry_run", "dry-run", "dryRun");
    }

    private static boolean getBooleanQuery(@NonNull Uri uri, boolean fallback, @NonNull String... names) {
        for (String name : names) {
            String value = uri.getQueryParameter(name);
            if (value != null) {
                return parseBoolean(value, fallback);
            }
        }
        return fallback;
    }

    private static boolean parseBoolean(@NonNull String value, boolean fallback) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)
                || "on".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)
                || "off".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private static int getIntQuery(@NonNull Uri uri, int fallback, @NonNull String... names) {
        for (String name : names) {
            String value = uri.getQueryParameter(name);
            if (value != null) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException ignore) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    @Nullable
    private static String lower(@Nullable String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : null;
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
