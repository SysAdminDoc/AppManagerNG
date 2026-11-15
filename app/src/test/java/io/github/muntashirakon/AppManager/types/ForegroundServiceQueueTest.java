// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A service stopped with starts still queued hands each of them back once, so whatever the caller
 * passed along with one (an open package file, a cached copy) can be let go of.
 */
@RunWith(RobolectricTestRunner.class)
public class ForegroundServiceQueueTest {
    public static class QueueService extends ForegroundService {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch finishFirst = new CountDownLatch(1);
        final List<String> handled = Collections.synchronizedList(new ArrayList<>());
        final List<String> dropped = new ArrayList<>();
        @Nullable
        Boolean queueDropped;
        @Nullable
        volatile Thread worker;

        public QueueService() {
            super("QueueService");
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            worker = Thread.currentThread();
            String action = intent != null ? intent.getAction() : null;
            handled.add(action);
            if ("first".equals(action)) {
                firstStarted.countDown();
                try {
                    finishFirst.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        protected void onDropped(@Nullable Intent intent) {
            dropped.add(intent != null ? intent.getAction() : null);
        }

        @Override
        protected void onStoppedUnfinished(boolean queueDropped) {
            this.queueDropped = queueDropped;
        }
    }

    public static class ThrowingDropService extends QueueService {
        @Override
        protected void onDropped(@Nullable Intent intent) {
            super.onDropped(intent);
            if (intent != null && "second".equals(intent.getAction())) {
                throw new IllegalStateException("Release failed");
            }
        }
    }

    @Test
    public void aDroppedStartThatFailsToReleaseStillLetsTheOthersGoAndTheWorkerStop() throws Exception {
        ServiceController<ThrowingDropService> controller = Robolectric.buildService(ThrowingDropService.class).create();
        ThrowingDropService service = controller.get();
        service.onStartCommand(new Intent("first"), 0, 1);
        assertTrue(service.firstStarted.await(30, TimeUnit.SECONDS));
        service.onStartCommand(new Intent("second"), 0, 2);
        service.onStartCommand(new Intent("third"), 0, 3);

        controller.destroy();
        service.finishFirst.countDown();
        Thread worker = service.worker;
        assertNotNull(worker);
        worker.join(30_000);

        assertFalse(worker.isAlive());
        assertEquals(Arrays.asList("second", "third"), service.dropped);
        assertEquals(Boolean.TRUE, service.queueDropped);
        assertEquals(Collections.singletonList("first"), service.handled);
    }

    @Test
    public void startsStillQueuedWhenTheServiceStopsAreHandedBackAndNeverRun() throws Exception {
        ServiceController<QueueService> controller = Robolectric.buildService(QueueService.class).create();
        QueueService service = controller.get();
        service.onStartCommand(new Intent("first"), 0, 1);
        assertTrue(service.firstStarted.await(30, TimeUnit.SECONDS));
        service.onStartCommand(new Intent("second"), 0, 2);
        service.onStartCommand(new Intent("third"), 0, 3);

        controller.destroy();
        service.finishFirst.countDown();
        Thread worker = service.worker;
        assertNotNull(worker);
        worker.join(30_000);

        assertFalse(worker.isAlive());
        assertEquals(Arrays.asList("second", "third"), service.dropped);
        assertEquals(Boolean.TRUE, service.queueDropped);
        assertEquals(Collections.singletonList("first"), service.handled);
    }

    @Test
    public void aStopWithOnlyTheRunningStartLeftDropsNothing() throws Exception {
        ServiceController<QueueService> controller = Robolectric.buildService(QueueService.class).create();
        QueueService service = controller.get();
        service.onStartCommand(new Intent("first"), 0, 1);
        assertTrue(service.firstStarted.await(30, TimeUnit.SECONDS));

        controller.destroy();
        service.finishFirst.countDown();
        Thread worker = service.worker;
        assertNotNull(worker);
        worker.join(30_000);

        assertEquals(Collections.emptyList(), service.dropped);
        assertEquals(Boolean.FALSE, service.queueDropped);
        assertEquals(Collections.singletonList("first"), service.handled);
    }
}
