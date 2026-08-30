// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PackageChangeReceiverTest {
    @Test
    public void filtersSeparateSystemAndPrivateActions() {
        IntentFilter packageFilter = PackageChangeReceiver.createPackageFilter();
        IntentFilter systemArrayFilter = PackageChangeReceiver.createSystemArrayFilter();
        IntentFilter privateFilter = PackageChangeReceiver.createPrivateFilter();

        assertTrue(packageFilter.hasAction(Intent.ACTION_PACKAGE_ADDED));
        assertTrue(packageFilter.hasAction(Intent.ACTION_PACKAGE_REMOVED));
        assertTrue(packageFilter.hasAction(Intent.ACTION_PACKAGE_CHANGED));
        assertTrue(packageFilter.hasAction(Intent.ACTION_PACKAGE_RESTARTED));
        assertTrue(systemArrayFilter.hasAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE));
        assertTrue(privateFilter.hasAction(PackageChangeReceiver.ACTION_PACKAGE_ALTERED));
        assertTrue(privateFilter.hasAction(PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED));
        assertTrue(privateFilter.hasAction(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
        assertFalse(systemArrayFilter.hasAction(PackageChangeReceiver.ACTION_PACKAGE_ALTERED));
        assertFalse(privateFilter.hasAction(Intent.ACTION_PACKAGE_ADDED));
    }

    @Test
    public void privateActionsRegisterAsNonExported() {
        RecordingContext context = new RecordingContext(ApplicationProvider.getApplicationContext());
        TestReceiver receiver = new TestReceiver(context, null, null);
        try {
            assertEquals(3, context.mRegistrations.size());
            assertEquals(ContextCompat.RECEIVER_EXPORTED, context.mRegistrations.get(0).flags);
            assertEquals(ContextCompat.RECEIVER_EXPORTED, context.mRegistrations.get(1).flags);
            assertEquals(ContextCompat.RECEIVER_NOT_EXPORTED, context.mRegistrations.get(2).flags);
            assertEquals(PackageChangeReceiver.INTERNAL_BROADCAST_PERMISSION,
                    context.mRegistrations.get(2).permission);
            assertTrue(context.mRegistrations.get(2).filter.hasAction(
                    PackageChangeReceiver.ACTION_PACKAGE_ALTERED));
        } finally {
            receiver.close();
        }
        assertTrue(context.mUnregistered);
    }

    @Test
    public void packageArrayValidationRejectsNullOversizedAndMalformedInput() {
        assertNull(PackageChangeReceiver.validatePackages(null));
        assertNull(PackageChangeReceiver.validatePackages(new String[0]));
        assertNull(PackageChangeReceiver.validatePackages(new String[]{"com.example.good", null}));
        assertNull(PackageChangeReceiver.validatePackages(new String[]{"com.example;bad"}));

        String[] oversized = new String[PackageChangeReceiver.MAX_PACKAGE_COUNT + 1];
        for (int i = 0; i < oversized.length; ++i) {
            oversized[i] = "com.example.app" + i;
        }
        assertNull(PackageChangeReceiver.validatePackages(oversized));
    }

    @Test
    public void workerPoolAndQueueAreBounded() {
        ThreadPoolExecutor executor = PackageChangeReceiver.createExecutor();
        try {
            assertEquals(1, executor.getCorePoolSize());
            assertEquals(1, executor.getMaximumPoolSize());
            assertEquals(PackageChangeReceiver.MAX_PENDING_SIGNALS,
                    executor.getQueue().remainingCapacity());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void systemPackageSignalRequiresUid() {
        Intent valid = new Intent(Intent.ACTION_PACKAGE_CHANGED).putExtra(Intent.EXTRA_UID, 10123);
        PackageChangeReceiver.ValidatedSignal signal = PackageChangeReceiver.validateIntent(valid);
        assertNotNull(signal);
        assertEquals(Integer.valueOf(10123), signal.uid);

        assertNull(PackageChangeReceiver.validateIntent(new Intent(Intent.ACTION_PACKAGE_CHANGED)));
    }

    @Test
    public void invalidArraysAreRejectedBeforeWorkerScheduling() throws Exception {
        CountDownLatch callback = new CountDownLatch(1);
        RecordingContext context = new RecordingContext(ApplicationProvider.getApplicationContext());
        TestReceiver receiver = new TestReceiver(context, callback, null);
        try {
            receiver.onReceive(context, new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED));
            receiver.onReceive(context, new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
                    .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                            new String[]{"com.example.good", "bad package"}));
            String[] oversized = new String[PackageChangeReceiver.MAX_PACKAGE_COUNT + 1];
            for (int i = 0; i < oversized.length; ++i) {
                oversized[i] = "com.example.app" + i;
            }
            receiver.onReceive(context, new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
                    .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, oversized));

            assertFalse(callback.await(300L, TimeUnit.MILLISECONDS));
        } finally {
            receiver.close();
        }
    }

    @Test
    public void validArrayIsCopiedBeforeScheduling() {
        String[] packages = {"com.example.one", "android"};
        Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
                .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packages);

        PackageChangeReceiver.ValidatedSignal signal = PackageChangeReceiver.validateIntent(intent);
        assertNotNull(signal);
        packages[0] = "com.example.changed";

        assertArrayEquals(new String[]{"com.example.one", "android"}, signal.packages);
    }

    @Test
    public void batchSignalRejectsForeignFailureAndKeepsSuccessfulPackages() {
        Intent valid = new Intent(BatchOpsService.ACTION_BATCH_OPS_COMPLETED)
                .putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_CLEAR_CACHE)
                .putExtra(BatchOpsService.EXTRA_OP_PKG,
                        new String[]{"com.example.one", "com.example.two"})
                .putStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG,
                        new ArrayList<>(Collections.singletonList("com.example.two")));
        PackageChangeReceiver.ValidatedSignal signal = PackageChangeReceiver.validateIntent(valid);
        assertNotNull(signal);
        assertArrayEquals(new String[]{"com.example.one"}, signal.packages);

        Intent invalid = new Intent(valid).putStringArrayListExtra(
                BatchOpsService.EXTRA_FAILED_PKG,
                new ArrayList<>(Collections.singletonList("com.example.foreign")));
        assertNull(PackageChangeReceiver.validateIntent(invalid));

        Intent allSuccessful = new Intent(valid).putStringArrayListExtra(
                BatchOpsService.EXTRA_FAILED_PKG, new ArrayList<>());
        PackageChangeReceiver.ValidatedSignal allSuccessfulSignal =
                PackageChangeReceiver.validateIntent(allSuccessful);
        assertNotNull(allSuccessfulSignal);
        assertArrayEquals(new String[]{"com.example.one", "com.example.two"},
                allSuccessfulSignal.packages);
    }

    @Test
    public void repeatedSignalsReuseOneWorkerThread() throws Exception {
        int signalCount = 20;
        CountDownLatch latch = new CountDownLatch(signalCount);
        Set<Integer> workerIds = Collections.synchronizedSet(new HashSet<>());
        RecordingContext context = new RecordingContext(ApplicationProvider.getApplicationContext());
        TestReceiver receiver = new TestReceiver(context, latch, workerIds);
        try {
            for (int i = 0; i < signalCount; ++i) {
                receiver.onReceive(context, new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
                        .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                                new String[]{"com.example.app" + i}));
            }
            assertTrue(latch.await(5L, TimeUnit.SECONDS));
            assertEquals(1, workerIds.size());
        } finally {
            receiver.close();
        }
    }

    private static final class TestReceiver extends PackageChangeReceiver {
        @Nullable
        private final CountDownLatch mLatch;
        @Nullable
        private final Set<Integer> mWorkerIds;

        private TestReceiver(@NonNull Context context, @Nullable CountDownLatch latch,
                             @Nullable Set<Integer> workerIds) {
            super(context);
            mLatch = latch;
            mWorkerIds = workerIds;
        }

        @Override
        protected void onPackageChanged(Intent intent, @Nullable Integer uid,
                                        @Nullable String[] packages) {
            if (mWorkerIds != null) {
                mWorkerIds.add(System.identityHashCode(Thread.currentThread()));
            }
            if (mLatch != null) {
                mLatch.countDown();
            }
        }
    }

    private static final class RecordingContext extends ContextWrapper {
        private final List<Registration> mRegistrations = new ArrayList<>();
        private boolean mUnregistered;

        private RecordingContext(@NonNull Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Nullable
        @Override
        public Intent registerReceiver(@Nullable BroadcastReceiver receiver, @NonNull IntentFilter filter,
                                       @Nullable String permission, @Nullable Handler scheduler, int flags) {
            mRegistrations.add(new Registration(filter, permission, flags));
            return null;
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
            mUnregistered = true;
        }
    }

    private static final class Registration {
        @NonNull
        private final IntentFilter filter;
        @Nullable
        private final String permission;
        private final int flags;

        private Registration(@NonNull IntentFilter filter, @Nullable String permission, int flags) {
            this.filter = filter;
            this.permission = permission;
            this.flags = flags;
        }
    }
}
