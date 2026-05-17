// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.IntentUtils;

public final class RestrictedSettingsDiagnostics {
    public static final int STATUS_NOT_APPLICABLE = 0;
    public static final int STATUS_TRUSTED_STORE = 1;
    public static final int STATUS_REVIEW_RECOMMENDED = 2;
    public static final int STATUS_LIKELY_RESTRICTED = 3;
    public static final int STATUS_UNKNOWN_SOURCE = 4;

    private static final Set<String> TRUSTED_STORE_SOURCES = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.huawei.appmarket",
            "com.xiaomi.mipicks",
            "com.heytap.market",
            "com.oppo.market",
            "com.vivo.appstore"));

    private static final Set<String> LIKELY_SIDELOAD_SOURCES = new HashSet<>(Arrays.asList(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.apps.nbu.files",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.sec.android.app.myfiles",
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx"));

    private RestrictedSettingsDiagnostics() {
    }

    @NonNull
    public static Result probe(@NonNull Context context) {
        String installerPackageName = null;
        String initiatingPackageName = null;
        String originatingPackageName = null;
        String error = null;
        try {
            InstallSourceInfoCompat installSourceInfo = PackageManagerCompat.getInstallSourceInfo(
                    context.getPackageName(), android.os.UserHandleHidden.myUserId());
            installerPackageName = installSourceInfo.getInstallingPackageName();
            initiatingPackageName = installSourceInfo.getInitiatingPackageName();
            originatingPackageName = installSourceInfo.getOriginatingPackageName();
        } catch (RemoteException | RuntimeException e) {
            error = e.getClass().getSimpleName()
                    + (isEmpty(e.getMessage()) ? "" : ": " + e.getMessage());
        }
        String sourcePackageName = chooseBestSource(installerPackageName, initiatingPackageName, originatingPackageName);
        return new Result(classify(Build.VERSION.SDK_INT, sourcePackageName),
                sourcePackageName, installerPackageName, initiatingPackageName, originatingPackageName, error);
    }

    @NonNull
    public static Intent buildAppInfoIntent(@NonNull Context context) {
        return IntentUtils.getAppDetailsSettings(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @NonNull
    public static Intent buildAccessibilitySettingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Status
    static int classify(int sdk, @Nullable String sourcePackageName) {
        if (sdk < Build.VERSION_CODES.TIRAMISU) {
            return STATUS_NOT_APPLICABLE;
        }
        if (isEmpty(sourcePackageName)) {
            return STATUS_UNKNOWN_SOURCE;
        }
        if (TRUSTED_STORE_SOURCES.contains(sourcePackageName)) {
            return STATUS_TRUSTED_STORE;
        }
        if (LIKELY_SIDELOAD_SOURCES.contains(sourcePackageName)) {
            return STATUS_LIKELY_RESTRICTED;
        }
        return STATUS_REVIEW_RECOMMENDED;
    }

    static boolean isTrustedStoreSource(@Nullable String sourcePackageName) {
        return sourcePackageName != null && TRUSTED_STORE_SOURCES.contains(sourcePackageName);
    }

    static boolean isLikelySideloadSource(@Nullable String sourcePackageName) {
        return sourcePackageName != null && LIKELY_SIDELOAD_SOURCES.contains(sourcePackageName);
    }

    @Nullable
    private static String chooseBestSource(@Nullable String installerPackageName,
                                           @Nullable String initiatingPackageName,
                                           @Nullable String originatingPackageName) {
        if (!isEmpty(initiatingPackageName)) {
            return initiatingPackageName;
        }
        if (!isEmpty(installerPackageName)) {
            return installerPackageName;
        }
        return isEmpty(originatingPackageName) ? null : originatingPackageName;
    }

    private static boolean isEmpty(@Nullable String value) {
        return value == null || value.isEmpty();
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            STATUS_NOT_APPLICABLE,
            STATUS_TRUSTED_STORE,
            STATUS_REVIEW_RECOMMENDED,
            STATUS_LIKELY_RESTRICTED,
            STATUS_UNKNOWN_SOURCE
    })
    public @interface Status {
    }

    public static final class Result {
        @Status
        public final int status;
        @Nullable
        public final String sourcePackageName;
        @Nullable
        public final String installerPackageName;
        @Nullable
        public final String initiatingPackageName;
        @Nullable
        public final String originatingPackageName;
        @Nullable
        public final String error;

        private Result(@Status int status, @Nullable String sourcePackageName,
                       @Nullable String installerPackageName, @Nullable String initiatingPackageName,
                       @Nullable String originatingPackageName, @Nullable String error) {
            this.status = status;
            this.sourcePackageName = sourcePackageName;
            this.installerPackageName = installerPackageName;
            this.initiatingPackageName = initiatingPackageName;
            this.originatingPackageName = originatingPackageName;
            this.error = error;
        }
    }
}
