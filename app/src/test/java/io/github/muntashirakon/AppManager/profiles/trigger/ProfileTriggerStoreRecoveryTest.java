// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ProfileTriggerStoreRecoveryTest {
    private static final String PREFS_NAME = "profile_triggers";
    private static final String KEY_ALL = "triggers";
    private static final String PROFILE = "profile-1";

    private SharedPreferences mPrefs;
    private ProfileTriggerStore mStore;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
        mStore = new ProfileTriggerStore(context);
    }

    @Test
    public void aHealthyStoreNeedsNoAttention() {
        mStore.put(trigger(7, 0));
        ProfileTriggerStore.Health health = mStore.inspect();
        assertFalse(health.needsAttention());
        assertFalse(health.documentCorrupt);
        assertEquals(0, health.droppedEntries);
    }

    @Test
    public void aCorruptDocumentFallsBackToTheLastGoodOne() {
        mStore.put(trigger(7, 0));
        mStore.put(trigger(8, 30));
        // Only the newest document is damaged; the previous one still parses.
        mPrefs.edit().putString(KEY_ALL, "{not json at all").commit();

        List<ProfileTrigger> recovered = mStore.all();
        assertEquals(1, recovered.size());
        ProfileTriggerStore.Health health = mStore.inspect();
        assertTrue(health.documentCorrupt);
        assertTrue(health.restoredFromLastGood);
        assertTrue(health.needsAttention());
    }

    @Test
    public void aCorruptDocumentIsRetainedVerbatimForExport() {
        mStore.put(trigger(7, 0));
        mPrefs.edit().putString(KEY_ALL, "{not json at all").commit();

        mStore.all();
        assertEquals("{not json at all", mStore.getQuarantinedDocument());
        assertTrue(mStore.inspect().quarantinedAt > 0);
    }

    @Test
    public void aCorruptDocumentDoesNotDestroyAutomationOnTheNextEdit() {
        mStore.put(trigger(7, 0));
        mStore.put(trigger(8, 30));
        mPrefs.edit().putString(KEY_ALL, "@@@").commit();

        // The next edit must build on the salvaged state, not on an empty one.
        mStore.put(trigger(9, 15));
        assertEquals(2, mStore.all().size());
    }

    @Test
    public void aSingleMalformedEntryDoesNotTakeItsSiblingsDown() {
        mStore.put(trigger(7, 0));
        String healthy = mPrefs.getString(KEY_ALL, "[]");
        String damaged = healthy.substring(0, healthy.length() - 1) + ",{\"id\":\"broken\"}]";
        mPrefs.edit().putString(KEY_ALL, damaged).commit();

        List<ProfileTrigger> triggers = mStore.all();
        assertEquals(1, triggers.size());
        ProfileTriggerStore.Health health = mStore.inspect();
        assertFalse(health.documentCorrupt);
        assertEquals(1, health.droppedEntries);
        assertTrue(health.needsAttention());
    }

    @Test
    public void anEmptyDocumentIsNotTreatedAsCorruption() {
        mPrefs.edit().putString(KEY_ALL, "").commit();
        assertTrue(mStore.all().isEmpty());
        assertFalse(mStore.inspect().needsAttention());
        assertNull(mStore.getQuarantinedDocument());
    }

    @Test
    public void anInterruptedWriteThatLeftATruncatedDocumentIsRecoverable() {
        mStore.put(trigger(7, 0));
        mStore.put(trigger(8, 30));
        String healthy = mPrefs.getString(KEY_ALL, "[]");
        // A write that stopped half-way leaves an unterminated array.
        mPrefs.edit().putString(KEY_ALL, healthy.substring(0, healthy.length() / 2)).commit();

        assertEquals(1, mStore.all().size());
        assertTrue(mStore.inspect().restoredFromLastGood);
        assertNotNull(mStore.getQuarantinedDocument());
    }

    @Test
    public void clearingTheQuarantineKeepsTheTriggers() {
        mStore.put(trigger(7, 0));
        mStore.put(trigger(8, 30));
        mPrefs.edit().putString(KEY_ALL, "###").commit();
        mStore.all();
        assertNotNull(mStore.getQuarantinedDocument());

        mStore.clearQuarantine();
        assertNull(mStore.getQuarantinedDocument());
        assertEquals(1, mStore.all().size());
    }

    @Test
    public void resetDiscardsEverythingIncludingTheRecoveryCopies() {
        mStore.put(trigger(7, 0));
        mStore.put(trigger(8, 30));
        mPrefs.edit().putString(KEY_ALL, "###").commit();
        mStore.all();

        mStore.reset();
        assertTrue(mStore.all().isEmpty());
        assertNull(mStore.getQuarantinedDocument());
        assertFalse(mStore.inspect().needsAttention());
    }

    private static ProfileTrigger trigger(int hour, int minute) {
        return new ProfileTrigger.Builder(PROFILE, ProfileTrigger.TYPE_TIME_OF_DAY)
                .timeOfDay(hour, minute)
                .build();
    }
}
