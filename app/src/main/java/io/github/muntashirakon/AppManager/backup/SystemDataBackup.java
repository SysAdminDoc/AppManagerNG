// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.annotation.UserIdInt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class SystemDataBackup {
    static final String ANDROID_PACKAGE_NAME = "android";
    private static final int APP_SCOPED_CONTENT_FLAGS = BackupFlags.BACKUP_APK_FILES
            | BackupFlags.BACKUP_INT_DATA
            | BackupFlags.BACKUP_EXT_DATA
            | BackupFlags.BACKUP_ADB_DATA
            | BackupFlags.BACKUP_EXT_OBB_MEDIA
            | BackupFlags.BACKUP_CACHE
            | BackupFlags.BACKUP_EXTRAS
            | BackupFlags.BACKUP_RULES;

    @VisibleForTesting
    static final String TOKEN_WIFI_MISC = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "wifi_misc";
    @VisibleForTesting
    static final String TOKEN_WIFI_APEX = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "wifi_apex";
    @VisibleForTesting
    static final String TOKEN_BLUETOOTH_MISC = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "bluetooth_misc";
    @VisibleForTesting
    static final String TOKEN_BLUETOOTH_BLUEDROID = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "bluetooth_bluedroid";
    @VisibleForTesting
    static final String TOKEN_BLUETOOTH_APEX = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "bluetooth_apex";
    @VisibleForTesting
    static final String TOKEN_ACCOUNTS_CE = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "accounts_ce";
    @VisibleForTesting
    static final String TOKEN_ACCOUNTS_DE = BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX + "accounts_de";

    private static final String[] ACCOUNT_CE_FILTERS = new String[]{"accounts_ce\\.db.*"};
    private static final String[] ACCOUNT_DE_FILTERS = new String[]{"accounts_de\\.db.*"};

    private static final Spec[] SPECS = new Spec[]{
            new Spec(TOKEN_WIFI_MISC, "/data/misc/wifi", null, null),
            new Spec(TOKEN_WIFI_APEX, "/data/misc/apexdata/com.android.wifi", null, null),
            new Spec(TOKEN_BLUETOOTH_MISC, "/data/misc/bluetooth", null, null),
            new Spec(TOKEN_BLUETOOTH_BLUEDROID, "/data/misc/bluedroid", null, null),
            new Spec(TOKEN_BLUETOOTH_APEX, "/data/misc/apexdata/com.android.btservices", null, null),
            new Spec(TOKEN_ACCOUNTS_CE, "/data/system_ce/%d", ACCOUNT_CE_FILTERS, null),
            new Spec(TOKEN_ACCOUNTS_DE, "/data/system_de/%d", ACCOUNT_DE_FILTERS, null),
    };

    private SystemDataBackup() {
    }

    static boolean isSystemDataPackage(@NonNull String packageName) {
        return ANDROID_PACKAGE_NAME.equals(packageName);
    }

    static boolean canBackUpSystemData() {
        return SelfPermissions.isSystemOrRoot();
    }

    static void retainOnlySystemData(@NonNull BackupFlags flags) {
        flags.removeFlag(APP_SCOPED_CONTENT_FLAGS);
        flags.addFlag(BackupFlags.BACKUP_SYSTEM_DATA);
    }

    static boolean isSystemDataToken(@NonNull String dataDir) {
        return dataDir.startsWith(BackupManager.DATA_BACKUP_SPECIAL_SYSTEM_PREFIX);
    }

    static boolean isKnownSystemDataToken(@NonNull String dataDir) {
        for (Spec spec : SPECS) {
            if (spec.token.equals(dataDir)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasSystemDataToken(@Nullable String[] dataDirs) {
        if (dataDirs == null) {
            return false;
        }
        for (String dataDir : dataDirs) {
            if (dataDir != null && isSystemDataToken(dataDir)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    static String[] getAvailableTokens(@UserIdInt int userId) {
        List<String> tokens = new ArrayList<>(SPECS.length);
        for (Spec spec : SPECS) {
            Path source = Paths.get(spec.getPath(userId));
            if (source.exists()) {
                tokens.add(spec.token);
            }
        }
        return tokens.toArray(new String[0]);
    }

    @NonNull
    static Source getSource(@NonNull String token, @UserIdInt int userId) throws BackupException {
        return new Source(Paths.get(getSourcePath(token, userId)),
                getFilters(token), getExclusions(token));
    }

    @VisibleForTesting
    @NonNull
    static String getSourcePath(@NonNull String token, @UserIdInt int userId) throws BackupException {
        for (Spec spec : SPECS) {
            if (spec.token.equals(token)) {
                return spec.getPath(userId);
            }
        }
        throw new BackupException("Unknown system data token " + token);
    }

    @Nullable
    private static String[] getFilters(@NonNull String token) throws BackupException {
        return getSpec(token).filters;
    }

    @Nullable
    private static String[] getExclusions(@NonNull String token) throws BackupException {
        return getSpec(token).exclusions;
    }

    @NonNull
    private static Spec getSpec(@NonNull String token) throws BackupException {
        for (Spec spec : SPECS) {
            if (spec.token.equals(token)) {
                return spec;
            }
        }
        throw new BackupException("Unknown system data token " + token);
    }

    static final class Source {
        @NonNull
        final Path source;
        @Nullable
        final String[] filters;
        @Nullable
        final String[] exclusions;

        private Source(@NonNull Path source, @Nullable String[] filters, @Nullable String[] exclusions) {
            this.source = source;
            this.filters = filters;
            this.exclusions = exclusions;
        }
    }

    private static final class Spec {
        @NonNull
        final String token;
        @NonNull
        final String path;
        @Nullable
        final String[] filters;
        @Nullable
        final String[] exclusions;

        private Spec(@NonNull String token, @NonNull String path, @Nullable String[] filters,
                     @Nullable String[] exclusions) {
            this.token = token;
            this.path = path;
            this.filters = filters;
            this.exclusions = exclusions;
        }

        @NonNull
        String getPath(@UserIdInt int userId) {
            if (path.contains("%d")) {
                return String.format(Locale.ROOT, path, userId);
            }
            return path;
        }
    }
}
