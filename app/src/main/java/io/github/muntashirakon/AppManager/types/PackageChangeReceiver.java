// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static io.github.muntashirakon.AppManager.batchops.BatchOpsService.ACTION_BATCH_OPS_COMPLETED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public abstract class PackageChangeReceiver extends BroadcastReceiver implements AutoCloseable {
    private static final String TAG = PackageChangeReceiver.class.getSimpleName();
    @VisibleForTesting
    static final String INTERNAL_BROADCAST_PERMISSION =
            BuildConfig.APPLICATION_ID + ".permission.INTERNAL_BROADCAST";
    @VisibleForTesting
    static final int MAX_PACKAGE_COUNT = 4096;
    @VisibleForTesting
    static final int MAX_PENDING_SIGNALS = 64;

    /**
     * Specifies that some packages have been altered. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ALTERED";
    /**
     * Specifies that some packages have been added. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_PACKAGE_ADDED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ADDED";
    /**
     * Specifies that some packages have been removed. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_PACKAGE_REMOVED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_REMOVED";

    /**
     * Specifies that some packages have been altered. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_DB_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_ALTERED";
    /**
     * Specifies that some packages have been added. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_DB_PACKAGE_ADDED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_ADDED";
    /**
     * Specifies that some packages have been removed. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_DB_PACKAGE_REMOVED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_REMOVED";

    @NonNull
    private final Context mContext;
    @NonNull
    private final ThreadPoolExecutor mExecutor;
    @NonNull
    private final AtomicBoolean mClosed = new AtomicBoolean();

    public PackageChangeReceiver(@NonNull Context context) {
        Context applicationContext = context.getApplicationContext();
        mContext = applicationContext != null ? applicationContext : context;
        mExecutor = createExecutor();
        ContextCompat.registerReceiver(mContext, this, createPackageFilter(), ContextCompat.RECEIVER_EXPORTED);
        ContextCompat.registerReceiver(mContext, this, createSystemArrayFilter(), ContextCompat.RECEIVER_EXPORTED);
        ContextCompat.registerReceiver(mContext, this, createPrivateFilter(),
                INTERNAL_BROADCAST_PERMISSION, null, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @NonNull
    @VisibleForTesting
    static IntentFilter createPackageFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        filter.addDataScheme("package");
        return filter;
    }

    @NonNull
    @VisibleForTesting
    static IntentFilter createSystemArrayFilter() {
        IntentFilter filter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            filter.addAction(Intent.ACTION_PACKAGES_SUSPENDED);
            filter.addAction(Intent.ACTION_PACKAGES_UNSUSPENDED);
        }
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        return filter;
    }

    @NonNull
    @VisibleForTesting
    static IntentFilter createPrivateFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PACKAGE_ALTERED);
        filter.addAction(ACTION_PACKAGE_ADDED);
        filter.addAction(ACTION_PACKAGE_REMOVED);
        filter.addAction(ACTION_DB_PACKAGE_ALTERED);
        filter.addAction(ACTION_DB_PACKAGE_ADDED);
        filter.addAction(ACTION_DB_PACKAGE_REMOVED);
        filter.addAction(ACTION_BATCH_OPS_COMPLETED);
        return filter;
    }

    @NonNull
    @VisibleForTesting
    static ThreadPoolExecutor createExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_SIGNALS), runnable -> new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    runnable.run();
                }, "PackageChangeReceiver"), (rejected, executor) -> {
                    if (executor.isShutdown()) {
                        return;
                    }
                    executor.getQueue().poll();
                    if (!executor.getQueue().offer(rejected)) {
                        Log.w(TAG, "Package-change queue is full; dropping the newest signal.");
                    } else {
                        Log.w(TAG, "Package-change queue is full; dropping the oldest signal.");
                    }
                });
    }

    @WorkerThread
    protected abstract void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages);

    @Override
    @UiThread
    public final void onReceive(Context context, @NonNull Intent intent) {
        ValidatedSignal signal = validateIntent(intent);
        if (signal == null || mClosed.get()) {
            return;
        }
        mExecutor.execute(() -> onPackageChanged(signal.intent, signal.uid, signal.packages));
    }

    @Override
    public final void close() {
        if (mClosed.compareAndSet(false, true)) {
            ContextUtils.unregisterReceiver(mContext, this);
            mExecutor.shutdownNow();
        }
    }

    @Nullable
    @VisibleForTesting
    static ValidatedSignal validateIntent(@NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return null;
        }
        try {
            switch (action) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        return null;
                    }
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_CHANGED:
                case Intent.ACTION_PACKAGE_RESTARTED: {
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    return uid >= 0 ? new ValidatedSignal(new Intent(action), uid, null) : null;
                }
                case ACTION_PACKAGE_ADDED:
                case ACTION_PACKAGE_ALTERED:
                case ACTION_PACKAGE_REMOVED:
                case ACTION_DB_PACKAGE_ADDED:
                case ACTION_DB_PACKAGE_ALTERED:
                case ACTION_DB_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGES_SUSPENDED:
                case Intent.ACTION_PACKAGES_UNSUSPENDED:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE: {
                    String[] packages = validatePackages(
                            intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST));
                    return packages != null
                            ? new ValidatedSignal(new Intent(action), null, packages)
                            : null;
                }
                case ACTION_BATCH_OPS_COMPLETED:
                    return validateBatchSignal(intent);
                default:
                    return null;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Rejected malformed package-change signal.", e);
            return null;
        }
    }

    @Nullable
    private static ValidatedSignal validateBatchSignal(@NonNull Intent intent) {
        @BatchOpsManager.OpType int op = intent.getIntExtra(
                BatchOpsService.EXTRA_OP, BatchOpsManager.OP_NONE);
        if (op == BatchOpsManager.OP_NONE || op == BatchOpsManager.OP_ADVANCED_FREEZE
                || op == BatchOpsManager.OP_FREEZE || op == BatchOpsManager.OP_UNFREEZE
                || op == BatchOpsManager.OP_UNINSTALL) {
            return null;
        }
        String[] packages = validatePackages(intent.getStringArrayExtra(BatchOpsService.EXTRA_OP_PKG));
        ArrayList<String> failedPackages = intent.getStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG);
        String[] failed = failedPackages != null
                ? validatePackages(failedPackages.toArray(new String[0]), true)
                : null;
        if (packages == null || failed == null) {
            return null;
        }
        Set<String> packageSet = new HashSet<>(Arrays.asList(packages));
        if (!packageSet.containsAll(Arrays.asList(failed))) {
            return null;
        }
        Set<String> failedSet = new HashSet<>(Arrays.asList(failed));
        List<String> successful = new ArrayList<>(packages.length);
        for (String packageName : packages) {
            if (!failedSet.contains(packageName)) {
                successful.add(packageName);
            }
        }
        return successful.isEmpty() ? null : new ValidatedSignal(
                new Intent(ACTION_BATCH_OPS_COMPLETED), null, successful.toArray(new String[0]));
    }

    @Nullable
    @VisibleForTesting
    static String[] validatePackages(@Nullable String[] packages) {
        return validatePackages(packages, false);
    }

    @Nullable
    private static String[] validatePackages(@Nullable String[] packages, boolean allowEmpty) {
        if (packages == null || (!allowEmpty && packages.length == 0)
                || packages.length > MAX_PACKAGE_COUNT) {
            return null;
        }
        String[] copy = packages.clone();
        for (String packageName : copy) {
            if (packageName == null || packageName.length() > 255 || !PackageUtils.validateName(packageName)) {
                return null;
            }
        }
        return copy;
    }

    @VisibleForTesting
    static final class ValidatedSignal {
        @NonNull
        final Intent intent;
        @Nullable
        final Integer uid;
        @Nullable
        final String[] packages;

        private ValidatedSignal(@NonNull Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            this.intent = intent;
            this.uid = uid;
            this.packages = packages;
        }
    }
}
