// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class ApprovalGateTest {
    private final List<ApprovalGate.Request> mSeen = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @After
    public void tearDown() {
        mExecutor.shutdownNow();
    }

    @Test
    public void aRequestAfterSharingStoppedIsDeniedWithoutAsking() throws Exception {
        ApprovalGate gate = new ApprovalGate(mSeen::add, 3000);
        gate.cancel();

        long start = System.nanoTime();
        ApprovalGate.Decision decision = gate.approve(session());

        assertEquals(ApprovalGate.Decision.DENY, decision);
        assertTrue("Nobody is asked once sharing stopped", mSeen.isEmpty());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
        assertNull(gate.getCurrent());
    }

    @Test
    public void theAnswerOnScreenSettlesTheRequest() throws Exception {
        ApprovalGate gate = new ApprovalGate(mSeen::add, 30_000);
        Future<ApprovalGate.Decision> decision = mExecutor.submit(() -> gate.approve(session()));
        ApprovalGate.Request request = awaitRequest(gate);
        assertEquals("123456", request.sas);
        assertEquals("Pixel", request.peerName);

        request.decide(ApprovalGate.Decision.ALLOW_AND_REMEMBER);
        // A second tap changes nothing
        request.decide(ApprovalGate.Decision.DENY);

        assertEquals(ApprovalGate.Decision.ALLOW_AND_REMEMBER, decision.get(10, TimeUnit.SECONDS));
        assertTrue(request.isSettled());
        assertNull(gate.getCurrent());
        assertNull("The screen hears that the request is gone", mSeen.get(mSeen.size() - 1));
    }

    @Test
    public void stoppingWhileARequestWaitsDeniesIt() throws Exception {
        ApprovalGate gate = new ApprovalGate(mSeen::add, 30_000);
        Future<ApprovalGate.Decision> decision = mExecutor.submit(() -> gate.approve(session()));
        awaitRequest(gate);

        gate.cancel();

        assertEquals(ApprovalGate.Decision.DENY, decision.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void anUnansweredRequestTimesOutAsADenial() throws Exception {
        ApprovalGate gate = new ApprovalGate(mSeen::add, 50);

        assertEquals(ApprovalGate.Decision.DENY, gate.approve(session()));
        assertNull(gate.getCurrent());
    }

    @NonNull
    private ApprovalGate.Request awaitRequest(@NonNull ApprovalGate gate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (gate.getCurrent() == null || mSeen.isEmpty()) {
            assertTrue("No request reached the screen", System.nanoTime() < deadline);
            Thread.sleep(10);
        }
        ApprovalGate.Request request = mSeen.get(0);
        assertNotNull(request);
        return request;
    }

    @NonNull
    private static PairedSession session() {
        SecureChannel channel = new SecureChannel(new FrameIo(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream()), new byte[32], new byte[32]);
        return new PairedSession(channel, "123456", "Pixel", new byte[]{4, 1, 2, 3}, true);
    }
}
