// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ProfileTriggerStoreTest {

    private ProfileTriggerStore mStore;

    @Before
    public void setUp() {
        mStore = new ProfileTriggerStore(ApplicationProvider.getApplicationContext());
        // Clean slate every test; Robolectric scopes prefs to the test process but
        // be explicit.
        for (ProfileTrigger trigger : mStore.all()) {
            mStore.remove(trigger.id);
        }
    }

    private static ProfileTrigger buildTime(String profileId, int hour, int minute) {
        return new ProfileTrigger.Builder(profileId, ProfileTrigger.TYPE_TIME_OF_DAY)
                .timeOfDay(hour, minute)
                .build();
    }

    private static ProfileTrigger buildEvent(String profileId, @ProfileTrigger.Type int type) {
        return new ProfileTrigger.Builder(profileId, type).build();
    }

    @Test
    public void emptyStoreReturnsEmptyList() {
        assertTrue(mStore.all().isEmpty());
        assertFalse(mStore.hasAnyEnabled());
        assertNull(mStore.find("nope"));
    }

    @Test
    public void putRoundTripsThroughASecondInstance() {
        ProfileTrigger trigger = buildTime("profile-a", 7, 30);
        mStore.put(trigger);
        ProfileTriggerStore other = new ProfileTriggerStore(ApplicationProvider.getApplicationContext());
        ProfileTrigger reread = other.find(trigger.id);
        assertNotNull(reread);
        assertEquals("profile-a", reread.profileId);
        assertEquals(ProfileTrigger.TYPE_TIME_OF_DAY, reread.type);
        assertEquals(7, reread.hourOfDay);
        assertEquals(30, reread.minuteOfHour);
        assertTrue(reread.enabled);
    }

    @Test
    public void forProfileFiltersByProfileId() {
        mStore.put(buildTime("profile-a", 7, 30));
        mStore.put(buildEvent("profile-a", ProfileTrigger.TYPE_ON_CHARGING));
        mStore.put(buildEvent("profile-b", ProfileTrigger.TYPE_ON_BOOT));
        List<ProfileTrigger> a = mStore.forProfile("profile-a");
        List<ProfileTrigger> b = mStore.forProfile("profile-b");
        assertEquals(2, a.size());
        assertEquals(1, b.size());
        for (ProfileTrigger trigger : a) assertEquals("profile-a", trigger.profileId);
    }

    @Test
    public void toggleEnabledFlipsAndPersists() {
        ProfileTrigger trigger = buildEvent("profile-a", ProfileTrigger.TYPE_ON_NETWORK_WIFI);
        mStore.put(trigger);
        assertTrue(trigger.enabled);

        Boolean after = mStore.toggleEnabled(trigger.id);
        assertNotNull(after);
        assertFalse(after);
        ProfileTrigger reread = mStore.find(trigger.id);
        assertNotNull(reread);
        assertFalse(reread.enabled);

        Boolean afterAgain = mStore.toggleEnabled(trigger.id);
        assertNotNull(afterAgain);
        assertTrue(afterAgain);
    }

    @Test
    public void toggleEnabledOnMissingReturnsNull() {
        assertNull(mStore.toggleEnabled("does-not-exist"));
    }

    @Test
    public void removeRemovesByIdAndIsIdempotent() {
        ProfileTrigger trigger = buildEvent("profile-a", ProfileTrigger.TYPE_ON_CHARGING);
        mStore.put(trigger);
        assertTrue(mStore.remove(trigger.id));
        assertFalse(mStore.remove(trigger.id));
        assertNull(mStore.find(trigger.id));
    }

    @Test
    public void removeForProfileSweepsEverythingAttached() {
        mStore.put(buildTime("profile-a", 7, 30));
        mStore.put(buildEvent("profile-a", ProfileTrigger.TYPE_ON_CHARGING));
        mStore.put(buildEvent("profile-b", ProfileTrigger.TYPE_ON_BOOT));
        int removed = mStore.removeForProfile("profile-a");
        assertEquals(2, removed);
        assertTrue(mStore.forProfile("profile-a").isEmpty());
        assertEquals(1, mStore.forProfile("profile-b").size());
    }

    @Test
    public void hasAnyEnabledReflectsCurrentState() {
        ProfileTrigger trigger = buildEvent("profile-a", ProfileTrigger.TYPE_ON_NETWORK_ANY)
                .withEnabled(false);
        mStore.put(trigger);
        assertFalse(mStore.hasAnyEnabled());
        Boolean now = mStore.toggleEnabled(trigger.id);
        assertNotNull(now);
        assertTrue(now);
        assertTrue(mStore.hasAnyEnabled());
    }

    @Test
    public void builderRejectsInvalidTimeOfDay() {
        try {
            new ProfileTrigger.Builder("p", ProfileTrigger.TYPE_TIME_OF_DAY)
                    .timeOfDay(24, 0);
            fail("expected IllegalArgumentException for hour=24");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new ProfileTrigger.Builder("p", ProfileTrigger.TYPE_TIME_OF_DAY)
                    .timeOfDay(10, 60);
            fail("expected IllegalArgumentException for minute=60");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void builderRejectsEmptyProfileId() {
        try {
            new ProfileTrigger.Builder("", ProfileTrigger.TYPE_ON_CHARGING).build();
            fail("expected IllegalArgumentException for empty profileId");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void typeStringRoundTripsExactly() {
        for (int type : new int[] {
                ProfileTrigger.TYPE_TIME_OF_DAY,
                ProfileTrigger.TYPE_ON_CHARGING,
                ProfileTrigger.TYPE_ON_NETWORK_WIFI,
                ProfileTrigger.TYPE_ON_NETWORK_ANY,
                ProfileTrigger.TYPE_ON_BOOT,
        }) {
            String label = ProfileTrigger.typeAsString(type);
            assertEquals(type, ProfileTrigger.parseTypeString(label));
        }
    }
}
