// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static io.github.muntashirakon.AppManager.backup.BackupUtils.TAR_TYPES;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupPathExclusionPatterns;
import io.github.muntashirakon.AppManager.dex.SmaliDecodeOptions;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupScheduler;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.fm.FmActivity;
import io.github.muntashirakon.AppManager.fm.FmListOptions;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Why this class?
//
// This class is just an abstract over the AppPref to make life a bit easier. In the future, however, it might be
// possible to deliver the changes to the settings using lifecycle where required. For example, in the log viewer page,
// changes to the settings are not immediately reflected unless the settings page is opened from the page itself.
public final class Prefs {
    public static final class AppDetailsPage {
        public static boolean displayDefaultAppOps() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL);
        }

        public static void setDisplayDefaultAppOps(boolean display) {
            AppPref.set(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL, display);
        }

        @AppDetailsFragment.SortOrder
        public static int getAppOpsSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT);
        }

        public static void setAppOpsSortOrder(@AppDetailsFragment.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT, sortOrder);
        }

        @AppDetailsFragment.SortOrder
        public static int getComponentsSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT);
        }

        public static void setComponentsSortOrder(@AppDetailsFragment.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT, sortOrder);
        }

        @AppDetailsFragment.SortOrder
        public static int getPermissionsSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT);
        }

        public static void setPermissionsSortOrder(@AppDetailsFragment.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT, sortOrder);
        }

        @AppDetailsFragment.SortOrder
        public static int getOverlaysSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_OVERLAYS_SORT_ORDER_INT);
        }

        public static void setOverlaysSortOrder(@AppDetailsFragment.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_OVERLAYS_SORT_ORDER_INT, sortOrder);
        }
    }

    public static final class Appearance {
        @NonNull
        public static String getLanguage() {
            return AppPref.getString(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        }

        @NonNull
        public static String getLanguage(@NonNull Context context) {
            // Required when application isn't initialised properly
            AppPref appPref = AppPref.getNewInstance(context);
            return (String) appPref.getValue(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        }

        public static void setLanguage(@NonNull String language) {
            AppPref.set(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, language);
            // Mirror the in-app language preference into AppCompatDelegate so the OS-side per-app
            // locale (Settings → Apps → AppManagerNG → Language on API 33+, SharedPreferences-backed
            // back-port on API 26-32) stays in sync. ROADMAP iter-22 [S269] — Per-App Locale Picker.
            LocaleListCompat localeList = LangUtils.LANG_AUTO.equals(language)
                    ? LocaleListCompat.getEmptyLocaleList()
                    : LocaleListCompat.forLanguageTags(language);
            AppCompatDelegate.setApplicationLocales(localeList);
        }

        public static int getLayoutDirection() {
            return AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT);
        }

        public static void setLayoutDirection(int layoutDirection) {
            AppPref.set(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT, layoutDirection);
        }

        @StyleRes
        public static int getAppTheme() {
            switch (AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT)) {
                case 1: // Full black theme
                    return io.github.muntashirakon.AppManager.R.style.AppTheme_V2_Amoled;
                default: // Normal theme
                    return io.github.muntashirakon.AppManager.R.style.AppTheme_V2;
            }
        }

        @StyleRes
        public static int getTransparentAppTheme() {
            // V2 preview keeps the classic transparent variants; transparent surfaces are
            // intentionally out of v0.4.x foundation scope (see design/plan/3-rollout.md).
            switch (AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT)) {
                case 1: // Full black theme
                    return io.github.muntashirakon.ui.R.style.AppTheme_TransparentBackground_Black;
                default: // Normal theme
                    return io.github.muntashirakon.ui.R.style.AppTheme_TransparentBackground;
            }
        }

        public static boolean isPremiumPreviewEnabled() {
            return true;
        }

        public static void setPremiumPreviewEnabled(boolean enabled) {
            // Preference compatibility for older installs; the refreshed interface is now the default.
            AppPref.set(AppPref.PrefKey.PREF_PREMIUM_PREVIEW_BOOL, true);
        }

        public static boolean isPureBlackTheme() {
            return AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT) == 1;
        }

        public static void setPureBlackTheme(boolean enabled) {
            AppPref.set(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT, enabled ? 1 : 0);
        }

        public static int getNightMode() {
            return AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT);
        }

        public static void setNightMode(int nightMode) {
            AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, nightMode);
        }

        public static boolean useSystemFont() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_USE_SYSTEM_FONT_BOOL);
        }
    }

    public static final class Experience {
        public static boolean isGuidedModeEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_GUIDED_MODE_BOOL);
        }

        public static void setGuidedModeEnabled(boolean enabled) {
            AppPref.set(AppPref.PrefKey.PREF_GUIDED_MODE_BOOL, enabled);
        }
    }

    public static final class BackupRestore {
        public static boolean backupAppsWithKeyStore() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL);
        }

        @NonNull
        @TarUtils.TarType
        public static String getCompressionMethod() {
            String tarType = AppPref.getString(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR);
            // Verify tar type
            if (ArrayUtils.indexOf(TAR_TYPES, tarType) == -1) {
                // Unknown tar type, set default
                tarType = TarUtils.TAR_GZIP;
            }
            return tarType;
        }

        public static void setCompressionMethod(@NonNull @TarUtils.TarType String tarType) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR, tarType);
        }

        @BackupFlags.BackupFlag
        public static int getBackupFlags() {
            return AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT);
        }

        public static void setBackupFlags(@BackupFlags.BackupFlag int flags) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT, flags);
        }

        @NonNull
        public static String[] getBackupExclusionPatterns() {
            return BackupPathExclusionPatterns.parse(
                    AppPref.getString(AppPref.PrefKey.PREF_BACKUP_EXCLUSION_PATTERNS_STR));
        }

        public static void setBackupExclusionPatterns(@Nullable String[] patterns) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_EXCLUSION_PATTERNS_STR,
                    TextUtils.join("\n", BackupPathExclusionPatterns.sanitize(patterns)));
        }

        /**
         * Maximum backups to keep per (packageName, userId, backupName) bucket.
         * {@code 0} means unlimited (no count-based pruning).
         */
        public static int getMaxBackupsPerApp() {
            int v = AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_RETENTION_MAX_COUNT_INT);
            return Math.max(0, v);
        }

        public static void setMaxBackupsPerApp(int value) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_RETENTION_MAX_COUNT_INT, Math.max(0, value));
        }

        /**
         * Maximum age in days for any backup before it's eligible for pruning.
         * {@code 0} means unlimited (no age-based pruning).
         */
        public static int getMaxBackupAgeDays() {
            int v = AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_RETENTION_MAX_AGE_DAYS_INT);
            return Math.max(0, v);
        }

        public static void setMaxBackupAgeDays(int value) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_RETENTION_MAX_AGE_DAYS_INT, Math.max(0, value));
        }

        public static boolean isScheduledAutoBackupEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_ENABLED_BOOL);
        }

        public static void setScheduledAutoBackupEnabled(boolean enabled) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_ENABLED_BOOL, enabled);
        }

        public static int getScheduledBackupHour() {
            return AutoBackupScheduler.sanitizeHour(
                    AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_HOUR_INT));
        }

        public static void setScheduledBackupHour(int hour) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_HOUR_INT,
                    AutoBackupScheduler.sanitizeHour(hour));
        }

        public static int getScheduledBackupMinute() {
            return AutoBackupScheduler.sanitizeMinute(
                    AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_MINUTE_INT));
        }

        public static void setScheduledBackupMinute(int minute) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_MINUTE_INT,
                    AutoBackupScheduler.sanitizeMinute(minute));
        }

        public static boolean isScheduledBackupChargingRequired() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_REQUIRE_CHARGING_BOOL);
        }

        public static void setScheduledBackupChargingRequired(boolean required) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_REQUIRE_CHARGING_BOOL, required);
        }

        public static int getScheduledBackupNetworkType() {
            return AutoBackupScheduler.sanitizeNetworkType(
                    AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_NETWORK_INT));
        }

        public static void setScheduledBackupNetworkType(int networkType) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_NETWORK_INT,
                    AutoBackupScheduler.sanitizeNetworkType(networkType));
        }

        public static int getScheduledBackupMinimumAgeDays() {
            return AutoBackupScheduler.sanitizeMinimumAgeDays(
                    AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_MINIMUM_AGE_DAYS_INT));
        }

        public static void setScheduledBackupMinimumAgeDays(int days) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_MINIMUM_AGE_DAYS_INT,
                    AutoBackupScheduler.sanitizeMinimumAgeDays(days));
        }

        public static long getScheduledBackupLastRun() {
            return AppPref.getLong(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_RUN_LONG);
        }

        public static void setScheduledBackupLastRun(long lastRun) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_RUN_LONG, Math.max(0L, lastRun));
        }

        @NonNull
        public static String getScheduledBackupLastResult() {
            return AppPref.getString(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_RESULT_STR);
        }

        public static void setScheduledBackupLastResult(@NonNull String result) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_RESULT_STR, result);
        }

        @NonNull
        public static String getScheduledBackupLastDiagnostics() {
            return AppPref.getString(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_DIAGNOSTICS_STR);
        }

        public static void setScheduledBackupLastDiagnostics(@NonNull String diagnostics) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_DIAGNOSTICS_STR, diagnostics);
        }

        @NonNull
        public static String getScheduledBackupLastSkippedPackages() {
            return AppPref.getString(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_SKIPPED_STR);
        }

        public static void setScheduledBackupLastSkippedPackages(@NonNull String skippedPackages) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_SCHEDULE_LAST_SKIPPED_STR, skippedPackages);
        }

        public static boolean backupDirectoryExists() {
            Uri uri = Storage.getVolumePath();
            Path path;
            if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                // Append AppManager only if storage permissions are granted
                String newPath = uri.getPath();
                if (SelfPermissions.checkStoragePermission()) {
                    newPath += Paths.PATH_SEPARATOR + "AppManager";
                }
                path = Paths.get(newPath);
            } else path = Paths.get(uri);
            return path.exists();
        }
    }

    public static final class Blocking {
        public static boolean globalBlockingEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL);
        }

        @ComponentRule.ComponentStatus
        public static String getDefaultBlockingMethod() {
            String selectedStatus = AppPref.getString(AppPref.PrefKey.PREF_DEFAULT_BLOCKING_METHOD_STR);
            if (!SelfPermissions.canBlockByIFW()) {
                if (selectedStatus.equals(ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE)
                        || selectedStatus.equals(ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW)) {
                    // Lower the status
                    return ComponentRule.COMPONENT_TO_BE_DISABLED;
                }
            }
            return selectedStatus;
        }

        public static void setDefaultBlockingMethod(@NonNull @ComponentRule.ComponentStatus String blockingMethod) {
            AppPref.set(AppPref.PrefKey.PREF_DEFAULT_BLOCKING_METHOD_STR, blockingMethod);
        }

        @FreezeUtils.FreezeMethod
        public static int getDefaultFreezingMethod() {
            int freezeType = AppPref.getInt(AppPref.PrefKey.PREF_FREEZE_TYPE_INT);
            if (freezeType == FreezeUtils.FREEZE_HIDE) {
                // Requires MANAGE_USERS permission
                if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                    return FreezeUtils.FREEZE_DISABLE;
                }
            } else if (freezeType == FreezeUtils.FREEZE_SUSPEND || freezeType == FreezeUtils.FREEZE_ADV_SUSPEND) {
                // 7+ only. Requires MANAGE_USERS permission until P. Requires SUSPEND_APPS permission after that.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                        || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS)
                        || (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS))) {
                    return FreezeUtils.FREEZE_DISABLE;
                }
            }
            return freezeType;
        }

        public static void setDefaultFreezingMethod(@FreezeUtils.FreezeMethod int freezeType) {
            AppPref.set(AppPref.PrefKey.PREF_FREEZE_TYPE_INT, freezeType);
        }
    }

    public static final class Encryption {
        @NonNull
        @CryptoUtils.Mode
        public static String getEncryptionMode() {
            return AppPref.getString(AppPref.PrefKey.PREF_ENCRYPTION_STR);
        }

        public static void setEncryptionMode(@NonNull @CryptoUtils.Mode String mode) {
            AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, mode);
        }

        @NonNull
        public static String getOpenPgpProvider() {
            return AppPref.getString(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR);
        }

        public static void setOpenPgpProvider(@NonNull String providerPackage) {
            AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR, providerPackage);
        }

        @NonNull
        public static String getOpenPgpKeyIds() {
            return AppPref.getString(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR);
        }

        public static void setOpenPgpKeyIds(@NonNull String keyIds) {
            AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR, keyIds);
        }
    }

    public static final class FileManager {
        public static boolean displayInLauncher() {
            ComponentName componentName = new ComponentName(BuildConfig.APPLICATION_ID, FmActivity.LAUNCHER_ALIAS);
            int state = ContextUtils.getContext().getPackageManager().getComponentEnabledSetting(componentName);
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        }

        public static Uri getHome() {
            return Uri.parse(AppPref.getString(AppPref.PrefKey.PREF_FM_HOME_STR));
        }

        public static void setHome(@NonNull Uri uri) {
            AppPref.set(AppPref.PrefKey.PREF_FM_HOME_STR, uri.toString());
        }

        public static boolean isRememberLastOpenedPath() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_FM_REMEMBER_LAST_PATH_BOOL);
        }

        @Nullable
        public static Pair<FmActivity.Options, Pair<Uri, Integer>> getLastOpenedPath() {
            String jsonString = AppPref.getString(AppPref.PrefKey.PREF_FM_LAST_PATH_STR);
            try {
                JSONObject object = new JSONObject(jsonString);
                if (object.has("path") && object.has("pos")) {
                    boolean vfs = object.has("vfs") && object.getBoolean("vfs");
                    FmActivity.Options options = new FmActivity.Options(Uri.parse(object.getString("path")),
                            vfs, false, false);
                    if (!Paths.getStrict(options.uri).exists()) {
                        // Do not bother if path does not exist
                        return null;
                    }
                    Uri initUri;
                    if (vfs && object.has("init")) {
                        initUri = Uri.parse(object.getString("init"));
                    } else initUri = null;
                    Pair<Uri, Integer> uriPositionPair = new Pair<>(initUri, object.getInt("pos"));
                    return new Pair<>(options, uriPositionPair);
                }
            } catch (JSONException | FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        public static void setLastOpenedPath(@NonNull FmActivity.Options options, @NonNull Uri initUri, int position) {
            try {
                if (options.isVfs()) {
                    // Ignore VFS for now
                    return;
                }
                JSONObject object = new JSONObject();
                object.put("pos", position);
                if (options.isVfs()) {
                    object.put("vfs", true);
                    object.put("path", options.uri.toString());
                    object.put("init", initUri.toString());
                } else {
                    object.put("path", initUri.toString());
                }
                AppPref.set(AppPref.PrefKey.PREF_FM_LAST_PATH_STR, object.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @FmListOptions.Options
        public static int getOptions() {
            return AppPref.getInt(AppPref.PrefKey.PREF_FM_OPTIONS_INT);
        }

        public static void setOptions(@FmListOptions.Options int options) {
            AppPref.set(AppPref.PrefKey.PREF_FM_OPTIONS_INT, options);
        }

        @FmListOptions.SortOrder
        public static int getSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_FM_SORT_ORDER_INT);
        }

        public static void setSortOrder(@FmListOptions.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_FM_SORT_ORDER_INT, sortOrder);
        }

        public static boolean isReverseSort() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_FM_SORT_REVERSE_BOOL);
        }

        public static void setReverseSort(boolean reverseSort) {
            AppPref.set(AppPref.PrefKey.PREF_FM_SORT_REVERSE_BOOL, reverseSort);
        }

        @NonNull
        public static String getSmaliCommentLevel() {
            return SmaliDecodeOptions.normalizeCommentLevel(
                    AppPref.getString(AppPref.PrefKey.PREF_FM_SMALI_COMMENT_LEVEL_STR));
        }

        public static boolean isSmaliRemoveAnnotations() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_FM_SMALI_REMOVE_ANNOTATIONS_BOOL);
        }

        @NonNull
        public static SmaliDecodeOptions getSmaliDecodeOptions() {
            return new SmaliDecodeOptions(getSmaliCommentLevel(), isSmaliRemoveAnnotations());
        }
    }

    public static final class Installer {
        public static boolean installInBackground() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL);
        }

        public static boolean displayChanges() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_CHANGES_BOOL);
        }

        public static boolean blockTrackers() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_BLOCK_TRACKERS_BOOL);
        }

        public static boolean forceDexOpt() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_FORCE_DEX_OPT_BOOL);
        }

        public static boolean canSignApk() {
            if (!AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_SIGN_APK_BOOL)) {
                // Signing not enabled
                return false;
            }
            return Signer.canSign();
        }

        public static int getInstallLocation() {
            return AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
        }

        public static void setInstallLocation(int installLocation) {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, installLocation);
        }

        @NonNull
        public static String getInstallerPackageName() {
            if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
                return BuildConfig.APPLICATION_ID;
            }
            return AppPref.getString(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
        }

        public static void setInstallerPackageName(@NonNull String packageName) {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, packageName);
        }

        public static boolean isSetOriginatingPackage() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_SET_ORIGIN_BOOL);
        }

        public static int getPackageSource() {
            return AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT);
        }

        public static void setPackageSource(int source) {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT, source);
        }

        public static boolean requestUpdateOwnership() {
            // Shell default is false
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_UPDATE_OWNERSHIP_BOOL);
        }

        public static boolean isDisableApkVerification() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISABLE_VERIFICATION_BOOL);
        }
    }

    public static final class LogViewer {
        @LogcatHelper.LogBufferId
        public static int getBuffers() {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT);
        }

        public static void setBuffers(@LogcatHelper.LogBufferId int buffers) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT, buffers);
        }

        public static int getLogLevel() {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT);
        }

        public static void setLogLevel(int logLevel) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT, logLevel);
        }

        public static int getDisplayLimit() {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);
        }

        public static void setDisplayLimit(int displayLimit) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT, displayLimit);
        }

        @NonNull
        public static String getFilterPattern() {
            return AppPref.getString(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR);
        }

        public static void setFilterPattern(@NonNull String filterPattern) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR, filterPattern);
        }

        public static int getLogWritingInterval() {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT);
        }

        public static void setLogWritingInterval(int logWritingInterval) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT, logWritingInterval);
        }

        public static boolean expandByDefault() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL);
        }

        public static boolean omitSensitiveInfo() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL);
        }

        public static boolean showPidTidTimestamp() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL);
        }
    }

    public static final class MainPage {
        @MainListOptions.SortOrder
        public static int getSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
        }

        public static void setSortOrder(@RunningAppsActivity.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, sortOrder);
        }

        public static boolean isReverseSort() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL);
        }

        public static void setReverseSort(boolean reverseSort) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL, reverseSort);
        }

        @MainListOptions.Filter
        public static int getFilters() {
            return AppPref.getInt(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT);
        }

        public static void setFilters(@MainListOptions.Filter int filters) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, filters);
        }

        public static long getInstallDateStartMillis() {
            return AppPref.getLong(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_INSTALL_DATE_START_LONG);
        }

        public static void setInstallDateStartMillis(long startMillis) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_INSTALL_DATE_START_LONG, startMillis);
        }

        public static long getInstallDateEndMillis() {
            return AppPref.getLong(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_INSTALL_DATE_END_LONG);
        }

        public static void setInstallDateEndMillis(long endMillis) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_INSTALL_DATE_END_LONG, endMillis);
        }

        @Nullable
        public static String getFilteredProfileName() {
            String profileName = AppPref.getString(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_PROFILE_STR);
            if (TextUtils.isEmpty(profileName)) {
                return null;
            }
            return profileName;
        }

        public static void setFilteredProfileName(@Nullable String profileName) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_PROFILE_STR, profileName == null ? "" : profileName);
        }
    }

    public static final class Misc {
        @Nullable
        public static int[] getSelectedUsers() {
            String usersStr = AppPref.getString(AppPref.PrefKey.PREF_SELECTED_USERS_STR);
            if (usersStr.isEmpty()) return null;
            // Parse defensively: this value can be set by the snapshot-import
            // feature (a trust boundary) or hand-edited, so a blank or
            // non-numeric token must not crash every caller via an unchecked
            // NumberFormatException (Integer.decode("") threw and propagated out
            // of Users.getUsers()/getUsersIds() and the backup/list paths). Skip
            // bad tokens; return null (= "all users") if nothing valid remains.
            String[] usersSplitStr = usersStr.split(",");
            int[] parsed = new int[usersSplitStr.length];
            int count = 0;
            for (String token : usersSplitStr) {
                String t = token.trim();
                if (t.isEmpty()) continue;
                try {
                    parsed[count++] = Integer.decode(t);
                } catch (NumberFormatException ignore) {
                    // skip malformed token
                }
            }
            if (count == 0) return null;
            return count == parsed.length ? parsed : java.util.Arrays.copyOf(parsed, count);
        }

        public static void setSelectedUsers(@Nullable int[] users) {
            if (users == null) {
                AppPref.set(AppPref.PrefKey.PREF_SELECTED_USERS_STR, "");
                return;
            }
            String[] userString = new String[users.length];
            for (int i = 0; i < users.length; ++i) {
                userString[i] = String.valueOf(users[i]);
            }
            AppPref.set(AppPref.PrefKey.PREF_SELECTED_USERS_STR, TextUtils.join(",", userString));
        }

        public static boolean sendNotificationsToConnectedDevices() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_SEND_NOTIFICATIONS_TO_CONNECTED_DEVICES_BOOL);
        }

        public static void setAdbLocalServerPort(int port) {
            AppPref.set(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT, port);
        }

        public static int getAdbLocalServerPort() {
            return AppPref.getInt(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT);
        }
    }

    public static final class AppActions {
        @Nullable
        public static String getForceStopTileTarget() {
            String target = AppPref.getString(AppPref.PrefKey.PREF_FORCE_STOP_TILE_TARGET_STR);
            if (TextUtils.isEmpty(target)) {
                return null;
            }
            return target;
        }

        public static void setForceStopTileTarget(@Nullable String target) {
            AppPref.set(AppPref.PrefKey.PREF_FORCE_STOP_TILE_TARGET_STR, target == null ? "" : target);
        }
    }

    public static final class RunningApps {
        @RunningAppsActivity.SortOrder
        public static int getSortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT);
        }

        public static void setSortOrder(@RunningAppsActivity.SortOrder int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT, sortOrder);
        }

        @RunningAppsActivity.Filter
        public static int getFilters() {
            return AppPref.getInt(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT);
        }

        public static void setFilters(@RunningAppsActivity.Filter int filters) {
            AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, filters);
        }

        public static boolean enableKillForSystemApps() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL);
        }

        public static void setEnableKillForSystemApps(boolean enable) {
            AppPref.set(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL, enable);
        }
    }

    public static final class Privacy {
        public static boolean isScreenLockEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL);
        }

        /** NF-07: which tracker categories get blocked when "Block trackers" runs. */
        @NonNull
        public static io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity getTrackerBlockingIntensity() {
            return io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity
                    .fromPrefValue(AppPref.getString(AppPref.PrefKey.PREF_TRACKER_BLOCKING_INTENSITY_STR));
        }

        public static void setTrackerBlockingIntensity(@NonNull io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity intensity) {
            AppPref.set(AppPref.PrefKey.PREF_TRACKER_BLOCKING_INTENSITY_STR, intensity.name());
        }

        public static boolean isAutoLockEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_AUTO_LOCK_BOOL);
        }

        public static boolean isPersistentSessionAllowed() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_PERSISTENT_SESSION_BOOL);
        }

        public static boolean isActionAuthGateEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_ACTION_AUTH_GATE_BOOL);
        }

        public static boolean isPermissionChangeMonitorEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_PERMISSION_CHANGE_MONITOR_BOOL);
        }

        public static void setPermissionChangeMonitorEnabled(boolean enabled) {
            AppPref.set(AppPref.PrefKey.PREF_ENABLE_PERMISSION_CHANGE_MONITOR_BOOL, enabled);
        }

        public static boolean isSigningCertChangeMonitorEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SIGNING_CERT_CHANGE_MONITOR_BOOL);
        }

        public static void setSigningCertChangeMonitorEnabled(boolean enabled) {
            AppPref.set(AppPref.PrefKey.PREF_ENABLE_SIGNING_CERT_CHANGE_MONITOR_BOOL, enabled);
        }

        public static boolean isLocalCrashSinkEnabled() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOCAL_CRASH_SINK_ENABLED_BOOL);
        }

        public static void setLocalCrashSinkEnabled(boolean enabled) {
            AppPref.set(AppPref.PrefKey.PREF_LOCAL_CRASH_SINK_ENABLED_BOOL, enabled);
        }

        public static int getOpHistoryRetentionDays() {
            return AppPref.getInt(AppPref.PrefKey.PREF_OP_HISTORY_RETENTION_DAYS_INT);
        }

        public static void setOpHistoryRetentionDays(int retentionDays) {
            AppPref.set(AppPref.PrefKey.PREF_OP_HISTORY_RETENTION_DAYS_INT, retentionDays);
        }

        public static int getOpHistorySortOrder() {
            return AppPref.getInt(AppPref.PrefKey.PREF_OP_HISTORY_SORT_ORDER_INT);
        }

        public static void setOpHistorySortOrder(int sortOrder) {
            AppPref.set(AppPref.PrefKey.PREF_OP_HISTORY_SORT_ORDER_INT, sortOrder);
        }

        public static boolean autoUpdateDebloatDefinitions() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_AUTO_UPDATE_BOOL);
        }

        public static long getLastDebloatDefinitionsCheckTime() {
            return AppPref.getLong(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_LAST_CHECK_LONG);
        }

        public static void setLastDebloatDefinitionsCheckTime(long lastCheckTime) {
            AppPref.set(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_LAST_CHECK_LONG, lastCheckTime);
        }

        @NonNull
        public static String getDebloatDefinitionsVersion() {
            return AppPref.getString(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_VERSION_STR);
        }

        public static void setDebloatDefinitionsVersion(@NonNull String version) {
            AppPref.set(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_VERSION_STR, version);
        }

        @NonNull
        public static String getDebloatDefinitionsSha256() {
            return AppPref.getString(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_SHA256_STR);
        }

        public static void setDebloatDefinitionsSha256(@NonNull String sha256) {
            AppPref.set(AppPref.PrefKey.PREF_DEBLOAT_DEFINITIONS_SHA256_STR, sha256);
        }
    }

    public static final class Profiles {
        @Nullable
        public static String getQuickFreezeProfileId() {
            String profileId = AppPref.getString(AppPref.PrefKey.PREF_QUICK_FREEZE_PROFILE_ID_STR);
            if (TextUtils.isEmpty(profileId)) {
                return null;
            }
            return profileId;
        }

        public static void setQuickFreezeProfileId(@Nullable String profileId) {
            AppPref.set(AppPref.PrefKey.PREF_QUICK_FREEZE_PROFILE_ID_STR, profileId == null ? "" : profileId);
        }
    }

    public static final class Signing {
        @NonNull
        public static SigSchemes getSigSchemes() {
            SigSchemes sigSchemes = new SigSchemes(AppPref.getInt(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT));
            if (sigSchemes.isEmpty()) {
                // Use default if no flag is set
                return new SigSchemes(SigSchemes.DEFAULT_SCHEMES);
            }
            return sigSchemes;
        }

        public static void setSigSchemes(int flags) {
            AppPref.set(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT, flags);
        }

        public static boolean zipAlign() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ZIP_ALIGN_BOOL);
        }
    }

    public static final class Storage {
        @NonNull
        public static Path getAppManagerDirectory() {
            Uri uri = getVolumePath();
            Path path;
            if (Objects.equals(uri.getScheme(), ContentResolver.SCHEME_FILE)) {
                // Append AppManager
                String newPath = uri.getPath() + Paths.PATH_SEPARATOR + "AppManager";
                path = Paths.get(newPath);
            } else path = Paths.get(uri);
            if (!path.exists()) path.mkdirs();
            return path;
        }

        public static Uri getVolumePath() {
            String uriOrBareFile = AppPref.getString(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR);
            if (uriOrBareFile.startsWith("/")) {
                // A good URI starts with file:// or content://, if not, migrate
                Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_FILE).path(uriOrBareFile).build();
                AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, uri.toString());
                return uri;
            }
            return Uri.parse(uriOrBareFile);
        }


        public static void setVolumePath(@NonNull String path) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, path);
        }

        @NonNull
        public static Path getTempPath() {
            // This path is intended for storing temporary data for backup/restore and similar operations
            return Paths.get(FileUtils.getCachePath());
        }
    }

    public static final class VirusTotal {
        @Nullable
        public static String getApiKey() {
            String apiKey = AppPref.getString(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR);
            if (TextUtils.isEmpty(apiKey)) {
                return null;
            }
            return apiKey;
        }


        public static void setApiKey(@Nullable String apiKey) {
            AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR, apiKey);
        }

        public static boolean promptBeforeUpload() {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL);
        }
    }
}
