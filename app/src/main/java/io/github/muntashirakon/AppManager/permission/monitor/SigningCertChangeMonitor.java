// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.pm.PackageInfoCompat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Engine for the App Signing Key Change Alert (T9). Parallel to
 * {@link PermissionChangeMonitor} — same broadcast trigger, same goAsync
 * pattern in the receiver, separate snapshot store so a corrupted store in
 * one feature can't disable the other.
 *
 * <p>Behaviour on each replace:
 * <ol>
 *   <li>Read cached SHA-256 set for the package (if any) from
 *       {@link SigningCertSnapshotStore}.</li>
 *   <li>Compute the current set by hashing every signer in
 *       {@code PackageInfo.signingInfo}.</li>
 *   <li>Diff via {@link SigningCertChangeDiff}.</li>
 *   <li>If {@link SigningCertChangeDiff.Result#kind} is
 *       {@link SigningCertChangeDiff.Kind#REPLACED} (strict cert change),
 *       post a high-priority notification with a strong tone.
 *       {@link SigningCertChangeDiff.Kind#ROTATED_ADDITIVE} (rotation) gets a
 *       quieter notification so users see the signal without alarm.</li>
 *   <li>Persist the new snapshot.</li>
 * </ol>
 *
 * <p>Self-update is silently skipped — NG's own signing rotation is announced
 * through release notes, not the alert system.
 */
public final class SigningCertChangeMonitor {
    public static final String TAG = "SigningCertChangeMonitor";

    private SigningCertChangeMonitor() {
    }

    @WorkerThread
    @Nullable
    public static SigningCertChangeDiff.Result onPackageReplaced(@NonNull Context appContext,
                                                                 @NonNull String packageName) {
        if (appContext.getPackageName().equals(packageName)) return null;
        SigningCertSnapshotStore store = new SigningCertSnapshotStore(appContext);
        SigningCertSnapshot before = store.get(packageName);
        SigningCertSnapshot after;
        try {
            after = computeCurrentSnapshot(appContext, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            store.remove(packageName);
            return null;
        }
        if (before == null) {
            store.put(packageName, after);
            return null;
        }
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute(packageName, before, after);
        store.put(packageName, after);
        if (diff.isInteresting()) {
            String label = resolveLabel(appContext, packageName);
            boolean replaced = diff.kind == SigningCertChangeDiff.Kind.REPLACED;
            String title = appContext.getString(replaced
                    ? R.string.signing_cert_change_replaced_title
                    : R.string.signing_cert_change_rotated_title, label);
            String body = appContext.getString(replaced
                            ? R.string.signing_cert_change_replaced_body
                            : R.string.signing_cert_change_rotated_body,
                    shortJoin(diff.added),
                    shortJoin(diff.removed));
            new AppChangeFeedStore(appContext).append(AppChangeFeedEntry.now("signing_cert", packageName, title, body));
            try {
                postNotification(appContext, packageName, title, body);
            } catch (Throwable t) {
                Log.w(TAG, "Could not post signing-cert change notification for " + packageName, t);
            }
        }
        return diff;
    }

    @WorkerThread
    public static int primeSnapshotsForAllPackages(@NonNull Context appContext) {
        SigningCertSnapshotStore store = new SigningCertSnapshotStore(appContext);
        int seen = 0;
        int signingFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        try {
            PackageManager pm = appContext.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(signingFlag)) {
                if (pi == null || pi.packageName == null) continue;
                if (appContext.getPackageName().equals(pi.packageName)) continue;
                store.put(pi.packageName, computeSnapshotFromPackageInfo(pi));
                ++seen;
            }
        } catch (Throwable t) {
            Log.w(TAG, "primeSnapshotsForAllPackages failed", t);
        }
        return seen;
    }

    @WorkerThread
    @NonNull
    private static SigningCertSnapshot computeCurrentSnapshot(@NonNull Context appContext,
                                                              @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = appContext.getPackageManager();
        int signingFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo pi = pm.getPackageInfo(packageName, signingFlag);
        return computeSnapshotFromPackageInfo(pi);
    }

    @NonNull
    private static SigningCertSnapshot computeSnapshotFromPackageInfo(@NonNull PackageInfo pi) {
        long versionCode = PackageInfoCompat.getLongVersionCode(pi);
        Set<String> shas = new HashSet<>();
        Signature[] sigs = extractSigners(pi);
        if (sigs != null) {
            for (Signature sig : sigs) {
                String hex = sha256(sig.toByteArray());
                if (hex != null) shas.add(hex);
            }
        }
        return new SigningCertSnapshot(versionCode, shas);
    }

    @Nullable
    private static Signature[] extractSigners(@NonNull PackageInfo pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pi.signingInfo != null) {
            SigningInfo info = pi.signingInfo;
            // hasMultipleSigners => getApkContentsSigners; else getSigningCertificateHistory
            // gives V3 rotation history with the current signer at index 0.
            return info.hasMultipleSigners() ? info.getApkContentsSigners()
                    : info.getSigningCertificateHistory();
        }
        //noinspection deprecation
        return pi.signatures;
    }

    @VisibleForTesting
    @Nullable
    static String sha256(@NonNull byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 3);
            for (int i = 0; i < d.length; ++i) {
                if (i > 0) sb.append(':');
                int v = d[i] & 0xff;
                sb.append(Character.forDigit(v >>> 4, 16));
                sb.append(Character.forDigit(v & 0x0f, 16));
            }
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    @WorkerThread
    private static void postNotification(@NonNull Context appContext,
                                         @NonNull String packageName,
                                         @NonNull String title,
                                         @NonNull String body) {
        Intent contentIntent = AppDetailsActivity.getIntent(appContext, packageName, 0, true);
        // Hash to a stable but per-package notification id so a follow-up alert
        // for the same package replaces the previous instead of stacking.
        int notifId = ("signing_cert_" + packageName).hashCode();
        PendingIntent pi = PendingIntentCompat.getActivity(appContext, notifId,
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(appContext)
                .setSmallIcon(R.drawable.ic_shield_key)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi);
        NotificationUtils.displayHighPriorityNotification(appContext, builder.build());
    }

    @NonNull
    private static String resolveLabel(@NonNull Context appContext, @NonNull String packageName) {
        try {
            ApplicationInfo info = appContext.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = info.loadLabel(appContext.getPackageManager());
            return label != null && label.length() > 0 ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @NonNull
    private static String shortJoin(@NonNull Set<String> shas) {
        if (shas.isEmpty()) return "—";
        // Trim each digest to the first 16 hex chars + ellipsis so the body
        // stays scannable even with multiple signers.
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = shas.iterator();
        int i = 0;
        while (it.hasNext() && i < 3) {
            String s = it.next();
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.length() > 19 ? s.substring(0, 19) + "…" : s);
            ++i;
        }
        int rest = shas.size() - i;
        if (rest > 0) sb.append(" +").append(rest);
        return sb.toString();
    }

    @VisibleForTesting
    @NonNull
    static Set<String> emptySet() {
        return Collections.emptySet();
    }
}
