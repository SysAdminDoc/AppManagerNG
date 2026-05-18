// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

public final class SplitApkSignatureMismatch {
    private static final String TAG = SplitApkSignatureMismatch.class.getSimpleName();
    private static final String VERSION_UNKNOWN = "unknown";

    @IntDef({
            REASON_BASE_CERT_UNREADABLE,
            REASON_SPLIT_CERT_UNREADABLE,
            REASON_SIGNER_COUNT_DIFFERS,
            REASON_CERT_DIFFERS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Reason {
    }

    public static final int REASON_BASE_CERT_UNREADABLE = 1;
    public static final int REASON_SPLIT_CERT_UNREADABLE = 2;
    public static final int REASON_SIGNER_COUNT_DIFFERS = 3;
    public static final int REASON_CERT_DIFFERS = 4;

    private SplitApkSignatureMismatch() {
    }

    @WorkerThread
    @NonNull
    public static List<Mismatch> find(@NonNull PackageManager packageManager,
                                      @NonNull ApkFile apkFile,
                                      @NonNull Collection<String> selectedSplitIds) {
        Set<String> selectedIds = new HashSet<>(selectedSplitIds);
        List<EntryReport> reports = new ArrayList<>();
        for (ApkFile.Entry entry : apkFile.getEntries()) {
            if (selectedIds.contains(entry.id)) {
                reports.add(loadReport(packageManager, entry));
            }
        }
        return findMismatches(reports);
    }

    @VisibleForTesting
    @NonNull
    static List<Mismatch> findMismatches(@NonNull List<EntryReport> reports) {
        EntryReport base = null;
        for (EntryReport report : reports) {
            if (report.base) {
                base = report;
                break;
            }
        }
        if (base == null) {
            return Collections.emptyList();
        }
        List<Mismatch> mismatches = new ArrayList<>();
        for (EntryReport report : reports) {
            if (report.base) {
                continue;
            }
            @Reason int reason = classify(base.certSha256, report.certSha256);
            if (reason != 0) {
                mismatches.add(new Mismatch(report, reason));
            }
        }
        return mismatches;
    }

    @VisibleForTesting
    static int classify(@NonNull List<String> baseCerts, @NonNull List<String> splitCerts) {
        if (baseCerts.isEmpty()) {
            return REASON_BASE_CERT_UNREADABLE;
        }
        if (splitCerts.isEmpty()) {
            return REASON_SPLIT_CERT_UNREADABLE;
        }
        if (baseCerts.size() != splitCerts.size()) {
            return REASON_SIGNER_COUNT_DIFFERS;
        }
        if (!new HashSet<>(baseCerts).equals(new HashSet<>(splitCerts))) {
            return REASON_CERT_DIFFERS;
        }
        return 0;
    }

    @NonNull
    private static EntryReport loadReport(@NonNull PackageManager packageManager, @NonNull ApkFile.Entry entry) {
        File apkFile = null;
        try {
            apkFile = entry.getFile(false);
        } catch (IOException e) {
            Log.w(TAG, "Could not cache split APK %s for signature check.", e, entry.name);
        }
        String version = apkFile != null ? loadVersion(packageManager, apkFile) : VERSION_UNKNOWN;
        List<String> certSha256 = apkFile != null ? loadCurrentSignerSha256(apkFile) : Collections.emptyList();
        return new EntryReport(entry.id, entry.name, version, certSha256,
                entry.type == ApkFile.APK_BASE, entry.isRequired());
    }

    @NonNull
    private static String loadVersion(@NonNull PackageManager packageManager, @NonNull File apkFile) {
        PackageInfo packageInfo = packageManager.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        if (packageInfo == null) {
            return VERSION_UNKNOWN;
        }
        long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        String versionName = packageInfo.versionName;
        if (versionName == null || versionName.length() == 0) {
            return String.valueOf(versionCode);
        }
        return String.format(Locale.ROOT, "%s (%d)", versionName, versionCode);
    }

    @NonNull
    private static List<String> loadCurrentSignerSha256(@NonNull File apkFile) {
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(apkFile)
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                    .build()
                    .verify();
            SignerInfo signerInfo = new SignerInfo(result);
            X509Certificate[] certificates = signerInfo.getCurrentSignerCerts();
            if (certificates == null || certificates.length == 0) {
                return Collections.emptyList();
            }
            List<String> certSha256 = new ArrayList<>(certificates.length);
            for (X509Certificate certificate : certificates) {
                certSha256.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, certificate.getEncoded()));
            }
            return normalize(certSha256);
        } catch (ApkFormatException | CertificateEncodingException | IOException | NoSuchAlgorithmException e) {
            Log.w(TAG, "Could not read signing certificates from %s.", e, apkFile);
            return Collections.emptyList();
        }
    }

    @NonNull
    private static List<String> normalize(@NonNull Collection<String> certSha256) {
        List<String> normalized = new ArrayList<>(certSha256.size());
        for (String sha256 : certSha256) {
            if (sha256 != null && sha256.length() > 0) {
                normalized.add(sha256.toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    public static final class EntryReport {
        @NonNull
        public final String id;
        @NonNull
        public final String name;
        @NonNull
        public final String version;
        @NonNull
        public final List<String> certSha256;
        public final boolean base;
        public final boolean required;

        public EntryReport(@NonNull String id, @NonNull String name, @Nullable String version,
                           @NonNull Collection<String> certSha256, boolean base, boolean required) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.version = version != null && version.length() > 0 ? version : VERSION_UNKNOWN;
            this.certSha256 = Collections.unmodifiableList(normalize(certSha256));
            this.base = base;
            this.required = required;
        }
    }

    public static final class Mismatch {
        @NonNull
        public final EntryReport entry;
        @Reason
        public final int reason;

        public Mismatch(@NonNull EntryReport entry, @Reason int reason) {
            this.entry = Objects.requireNonNull(entry);
            this.reason = reason;
        }

        public boolean canRemove() {
            return !entry.base && !entry.required;
        }
    }
}
