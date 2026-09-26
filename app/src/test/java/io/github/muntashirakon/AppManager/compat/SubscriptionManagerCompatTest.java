// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.DeadObjectException;
import android.os.RemoteException;
import android.telephony.SubscriptionInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.SubscriptionManagerCompat.Availability;
import io.github.muntashirakon.AppManager.ipc.ServiceNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SubscriptionManagerCompatTest {
    @Before
    public void setUp() {
        SubscriptionManagerCompat.resetAvailability();
    }

    @After
    public void tearDown() {
        SubscriptionManagerCompat.resetAvailability();
    }

    @Test
    public void unregisteredSubscriptionServiceReportsNoSubscriberIds() {
        // Robolectric registers neither isub nor iphonesubinfo, which is the Wi-Fi-only and OEM
        // shape from fork issue #18. The real lookup path must come back empty, not throw.
        assertNull(SubscriptionManagerCompat.getActiveSubscriptionInfoList());
        assertTrue(SubscriptionManagerCompat.getActiveSubscriberIds().isEmpty());
        assertNull(SubscriptionManagerCompat.getSubscriberIdForSubscriber(1));

        assertEquals(Availability.NOT_FOUND, SubscriptionManagerCompat.getAvailability(SubscriptionManagerCompat.SERVICE_SUB));
        assertEquals("isub unavailable: service not registered, iphonesubinfo unavailable: service not registered",
                SubscriptionManagerCompat.describeServiceAvailability());
    }

    @Test
    public void supportLineProbesServicesNoLookupHasTouched() {
        // A support bundle from a fresh process must still show the missing service.
        assertEquals("isub unavailable: service not registered, iphonesubinfo unavailable: service not registered",
                SubscriptionManagerCompat.describeServiceAvailability());
    }

    @Test
    public void otherRemoteFailuresAreClassifiedAndDoNotEscape() {
        assertNull(SubscriptionManagerCompat.queryOrUnavailable("isub", () -> {
            throw new IllegalStateException("thrown inside the phone process");
        }));
        assertEquals(Availability.REMOTE_FAILURE, SubscriptionManagerCompat.getAvailability("isub"));

        assertNull(SubscriptionManagerCompat.queryOrUnavailable("isub", () -> {
            throw new NoSuchMethodError("getActiveSubscriptionInfoList");
        }));
        assertEquals(Availability.INCOMPATIBLE, SubscriptionManagerCompat.getAvailability("isub"));
    }

    @Test
    public void missingServiceIsClassifiedAsNotFound() {
        assertNull(SubscriptionManagerCompat.queryOrUnavailable("isub", () -> {
            throw new ServiceNotFoundException("Service couldn't be found: isub");
        }));
        assertEquals(Availability.NOT_FOUND, SubscriptionManagerCompat.getAvailability("isub"));
    }

    @Test
    public void binderDeathIsClassifiedSeparatelyFromOtherRemoteFailures() {
        assertNull(SubscriptionManagerCompat.queryOrUnavailable("isub", () -> {
            throw new DeadObjectException();
        }));
        assertEquals(Availability.BINDER_DEAD, SubscriptionManagerCompat.getAvailability("isub"));

        assertNull(SubscriptionManagerCompat.queryOrUnavailable("isub", () -> {
            throw new RemoteException("transaction failed");
        }));
        assertEquals(Availability.REMOTE_FAILURE, SubscriptionManagerCompat.getAvailability("isub"));
    }

    @Test
    public void rejectedAccessIsClassifiedAndDoesNotEscape() {
        assertNull(SubscriptionManagerCompat.queryOrUnavailable("iphonesubinfo", () -> {
            throw new SecurityException("READ_PRIVILEGED_PHONE_STATE required");
        }));
        assertEquals(Availability.ACCESS_REJECTED, SubscriptionManagerCompat.getAvailability("iphonesubinfo"));
    }

    @Test
    public void workingServiceKeepsItsResultAndRecoversFromEarlierFailure() {
        SubscriptionManagerCompat.queryOrUnavailable("isub", () -> {
            throw new DeadObjectException();
        });
        List<String> result = SubscriptionManagerCompat.queryOrUnavailable("isub", () -> Arrays.asList("a", "b"));
        assertEquals(Arrays.asList("a", "b"), result);
        assertEquals(Availability.AVAILABLE, SubscriptionManagerCompat.getAvailability("isub"));
    }

    @Test
    public void multiSimResponseKeepsEverySubscriberId() {
        Map<Integer, String> ids = new HashMap<>();
        ids.put(1, "310260000000001");
        ids.put(2, "310260000000002");
        List<SubscriptionInfo> subscriptions = Arrays.asList(subscription(1), subscription(2));

        assertEquals(Arrays.asList("310260000000001", "310260000000002"),
                SubscriptionManagerCompat.collectSubscriberIds(subscriptions, ids::get));
    }

    @Test
    public void duplicateSubscriberIdsAreQueriedOnce() {
        Map<Integer, String> ids = new HashMap<>();
        ids.put(1, "310260000000001");
        ids.put(3, "310260000000001");
        List<SubscriptionInfo> subscriptions = Arrays.asList(subscription(1), subscription(3));

        assertEquals(Collections.singletonList("310260000000001"),
                SubscriptionManagerCompat.collectSubscriberIds(subscriptions, ids::get));
    }

    @Test
    public void anUnreadableSubscriberIdFallsBackToOneUnfilteredQuery() {
        Map<Integer, String> ids = new HashMap<>();
        ids.put(1, "310260000000001");
        List<SubscriptionInfo> subscriptions = Arrays.asList(subscription(1), subscription(2));

        // Querying only subscription 1 would lose subscription 2's traffic; an extra unfiltered
        // query beside it would count subscription 1 twice. No IDs means one unfiltered query.
        assertTrue(SubscriptionManagerCompat.collectSubscriberIds(subscriptions, ids::get).isEmpty());
    }

    private static SubscriptionInfo subscription(int id) {
        return SubscriptionInfoBuilder.newBuilder().setId(id).buildSubscriptionInfo();
    }
}
