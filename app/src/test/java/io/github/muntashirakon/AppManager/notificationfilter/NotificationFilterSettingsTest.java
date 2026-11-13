// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Field;

@RunWith(RobolectricTestRunner.class)
public class NotificationFilterSettingsTest {
    private SharedPreferences mPrefs;
    private NotificationFilterSettings mSettings;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mPrefs = context.getSharedPreferences(NotificationFilterSettings.FILE_NAME, Context.MODE_PRIVATE);
        mSettings = new NotificationFilterSettings(mPrefs);
    }

    @After
    public void tearDown() {
        NotificationFilterSettings.resetForTesting();
    }

    private static NotificationFilterRule rule(String id, String pattern, Action action) {
        return new NotificationFilterRule(id, "Rule " + id, null, Field.ANY, pattern, false, action);
    }

    @Test
    public void rulesKeepTheirOrderAcrossInstances() {
        List<NotificationFilterRule> rules = Arrays.asList(rule("b", "beta", Action.BLOCK),
                rule("a", "alpha", Action.SILENT), rule("c", "", Action.ALLOW));
        mSettings.setRules(rules);

        assertEquals(rules, new NotificationFilterSettings(mPrefs).getRules());
    }

    @Test
    public void rulesAreParsedOncePerChange() {
        mSettings.setRules(Arrays.asList(rule("a", "alpha", Action.SILENT)));

        List<NotificationFilterRule> first = mSettings.getRules();
        assertSame(first, mSettings.getRules());

        mSettings.saveRule(rule("b", "beta", Action.BLOCK));
        assertEquals(2, mSettings.getRules().size());
    }

    @Test
    public void saveRuleReplacesTheSameIdInPlaceAndAddsNewOnesLast() {
        mSettings.setRules(Arrays.asList(rule("a", "alpha", Action.SILENT), rule("b", "beta", Action.SILENT)));

        mSettings.saveRule(rule("a", "alpha", Action.BLOCK));
        mSettings.saveRule(rule("c", "gamma", Action.VIBRATE));

        List<NotificationFilterRule> rules = mSettings.getRules();
        assertEquals(3, rules.size());
        assertEquals("a", rules.get(0).id);
        assertEquals(Action.BLOCK, rules.get(0).action);
        assertEquals("c", rules.get(2).id);
        assertEquals(Action.VIBRATE, mSettings.findRule("c").action);
        assertNull(mSettings.findRule("missing"));
    }

    @Test
    public void unreadableSavedRulesAreSkipped() {
        mPrefs.edit().putString("rules", "[{\"id\":\"x\"}, 7, " + "{\"id\":\"ok\",\"name\":\"Ok\",\"field\":\"ANY\","
                + "\"pattern\":\"p\",\"regex\":false,\"action\":\"SILENT\"}]").commit();

        List<NotificationFilterRule> rules = mSettings.getRules();
        assertEquals(1, rules.size());
        assertEquals("ok", rules.get(0).id);

        mPrefs.edit().putString("rules", "not json").commit();
        assertTrue(mSettings.getRules().isEmpty());
    }

    @Test
    public void quietModeDefaultsToTenAtNightUntilSevenWithTheScheduleOff() {
        QuietMode quiet = mSettings.getQuietMode();

        assertFalse(quiet.manual);
        assertFalse(quiet.scheduled);
        assertEquals(22 * 60, quiet.startMinute);
        assertEquals(7 * 60, quiet.endMinute);
        assertFalse(mSettings.isQuietModeActive(System.currentTimeMillis()));
    }

    @Test
    public void quietModeIsSavedAndToggled() {
        long now = System.currentTimeMillis();
        mSettings.setQuietMode(new QuietMode(false, true, 60, 120, 5L));

        QuietMode back = new NotificationFilterSettings(mPrefs).getQuietMode();
        assertTrue(back.scheduled);
        assertEquals(60, back.startMinute);
        assertEquals(120, back.endMinute);
        assertEquals(5L, back.scheduleSkippedUntilMillis);

        mSettings.setQuietMode(new QuietMode(false, false, 60, 120, 0));
        assertTrue(mSettings.toggleQuietMode(now));
        assertTrue(mSettings.isQuietModeActive(now));
        assertFalse(mSettings.toggleQuietMode(now));
        assertFalse(mSettings.isQuietModeActive(now));
    }

    @Test
    public void quietActionIsSilentOrVibrateOnly() {
        assertEquals(Action.SILENT, mSettings.getQuietAction());

        mSettings.setQuietAction(Action.VIBRATE);
        assertEquals(Action.VIBRATE, mSettings.getQuietAction());

        assertThrows(IllegalArgumentException.class, () -> mSettings.setQuietAction(Action.BLOCK));
        assertThrows(IllegalArgumentException.class, () -> mSettings.setQuietAction(Action.ALLOW));
        mPrefs.edit().putString("quiet_action", "BLOCK").commit();
        assertEquals(Action.SILENT, mSettings.getQuietAction());
    }

    @Test
    public void retentionIsOneOfTheChoices() {
        assertEquals(7, mSettings.getRetentionDays());
        assertEquals(TimeUnit.DAYS.toMillis(7), mSettings.getRetentionMillis());

        mSettings.setRetentionDays(30);
        assertEquals(30, mSettings.getRetentionDays());
        mSettings.setRetentionDays(1);
        assertEquals(TimeUnit.DAYS.toMillis(1), mSettings.getRetentionMillis());

        mSettings.setRetentionDays(3);
        assertEquals(NotificationFilterSettings.DEFAULT_RETENTION_DAYS, mSettings.getRetentionDays());
    }

    @Test
    public void recordAllIsOffUntilTurnedOn() {
        assertFalse(mSettings.isRecordAll());

        mSettings.setRecordAll(true);
        assertTrue(new NotificationFilterSettings(mPrefs).isRecordAll());
    }
}
