// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ProfileTriggerFilterTest {
    @Test
    public void jsonRoundTripPreservesPackagePattern() throws JSONException {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-1", ProfileTrigger.TYPE_ON_APP_INSTALL)
                .id("t1")
                .packagePattern("com.vendor.*")
                .build();
        ProfileTrigger restored = ProfileTrigger.fromJson(trigger.toJson());
        assertEquals("com.vendor.*", restored.packagePattern);
        assertEquals(ProfileTrigger.TYPE_ON_APP_INSTALL, restored.type);
    }

    @Test
    public void packagePatternClearedForNonPackageTypes() throws JSONException {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-1", ProfileTrigger.TYPE_TIME_OF_DAY)
                .id("t2")
                .timeOfDay(8, 30)
                .packagePattern("com.vendor.*")
                .build();
        assertEquals("", trigger.packagePattern);
        // And the cleared value survives a round-trip (key is omitted when empty).
        assertEquals("", ProfileTrigger.fromJson(trigger.toJson()).packagePattern);
    }

    @Test
    public void emptyPatternMatchesEverythingIncludingUnknownPackage() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("p", ProfileTrigger.TYPE_ON_APP_UPDATE)
                .id("t").build();
        assertTrue(trigger.matchesPackage("com.anything"));
        assertTrue(trigger.matchesPackage(null));
    }

    @Test
    public void globPatternMatchesExpectedPackages() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("p", ProfileTrigger.TYPE_ON_APP_INSTALL)
                .id("t").packagePattern("com.vendor.*").build();
        assertTrue(trigger.matchesPackage("com.vendor.app"));
        assertTrue(trigger.matchesPackage("com.vendor.sub.app"));
        assertFalse(trigger.matchesPackage("com.other.app"));
        assertFalse(trigger.matchesPackage("org.com.vendor.app"));
        // A non-empty pattern never matches an unknown package.
        assertFalse(trigger.matchesPackage(null));
    }

    @Test
    public void exactPatternWithoutWildcardMatchesOnlyThatPackage() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("p", ProfileTrigger.TYPE_ON_APP_UNINSTALL)
                .id("t").packagePattern("com.vendor.app").build();
        assertTrue(trigger.matchesPackage("com.vendor.app"));
        assertFalse(trigger.matchesPackage("com.vendor.app2"));
    }

    @Test
    public void schedulerGatesPackageEventTriggersByChangedPackage() {
        Context context = ApplicationProvider.getApplicationContext();
        ProfileTriggerStore store = new ProfileTriggerStore(context);
        ProfileTrigger vendorOnly = new ProfileTrigger.Builder("p-vendor", ProfileTrigger.TYPE_ON_APP_INSTALL)
                .id("vendor").packagePattern("com.vendor.*").build();
        ProfileTrigger anyPackage = new ProfileTrigger.Builder("p-any", ProfileTrigger.TYPE_ON_APP_INSTALL)
                .id("any").build();
        store.put(vendorOnly);
        store.put(anyPackage);

        List<ProfileTrigger> matchVendor = RoutineScheduler.matchingPackageEventTriggers(
                store, ProfileTrigger.TYPE_ON_APP_INSTALL, "com.vendor.app");
        assertEquals(2, matchVendor.size());

        List<ProfileTrigger> matchOther = RoutineScheduler.matchingPackageEventTriggers(
                store, ProfileTrigger.TYPE_ON_APP_INSTALL, "com.other.app");
        assertEquals(1, matchOther.size());
        assertEquals("any", matchOther.get(0).id);

        // Unknown package: only the unfiltered trigger runs.
        List<ProfileTrigger> matchUnknown = RoutineScheduler.matchingPackageEventTriggers(
                store, ProfileTrigger.TYPE_ON_APP_INSTALL, null);
        assertEquals(1, matchUnknown.size());
        assertEquals("any", matchUnknown.get(0).id);
    }
}
