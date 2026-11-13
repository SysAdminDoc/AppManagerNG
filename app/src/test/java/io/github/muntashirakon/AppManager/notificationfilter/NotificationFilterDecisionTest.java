// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterDecision.Facts;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterDecision.Result;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Field;

public class NotificationFilterDecisionTest {
    private static final String OWN = "io.github.sysadmindoc.AppManagerNG";
    private static final String CHAT = "com.example.chat";
    private static final NotificationFields SALE = new NotificationFields("Shop", "Big sale today", "Shop");
    private static final NotificationFields MOM = new NotificationFields("Mom", "Dinner at six", "Mom");

    @NonNull
    private static Facts facts(@NonNull String packageName, @NonNull NotificationFields fields) {
        return new Facts(packageName, 0, true, null, false, false, fields);
    }

    @NonNull
    private static NotificationFilterRule rule(@NonNull String name, @NonNull String pattern, @NonNull Action action) {
        return new NotificationFilterRule(name, name, null, Field.ANY, pattern, false, action);
    }

    @NonNull
    private static Result decide(@NonNull Facts facts, @NonNull List<NotificationFilterRule> rules,
                                 @Nullable Action quietAction) {
        return NotificationFilterDecision.decide(facts, OWN, rules, quietAction);
    }

    @Test
    public void ownNotificationsAreNeverTouched() {
        Result result = decide(facts(OWN, SALE), Collections.singletonList(rule("Block", "", Action.BLOCK)),
                Action.SILENT);

        assertTrue(result.exempt);
        assertNull(result.action);
    }

    @Test
    public void ongoingForegroundAndUnclearableNotificationsAreExempt() {
        List<NotificationFilterRule> blockAll = Collections.singletonList(rule("Block", "", Action.BLOCK));
        int[] untouchable = {Notification.FLAG_ONGOING_EVENT, Notification.FLAG_FOREGROUND_SERVICE,
                Notification.FLAG_NO_CLEAR, Notification.FLAG_GROUP_SUMMARY};
        for (int flag : untouchable) {
            Facts facts = new Facts(CHAT, flag, true, null, false, false, SALE);
            assertTrue("flag " + flag, decide(facts, blockAll, Action.SILENT).exempt);
        }
        Facts unclearable = new Facts(CHAT, 0, false, null, false, false, SALE);
        assertTrue(decide(unclearable, blockAll, Action.SILENT).exempt);
    }

    @Test
    public void callsAlarmsFullScreenAndMediaAreExempt() {
        List<NotificationFilterRule> blockAll = Collections.singletonList(rule("Block", "", Action.BLOCK));

        assertTrue(decide(new Facts(CHAT, 0, true, Notification.CATEGORY_CALL, false, false, SALE), blockAll,
                null).exempt);
        assertTrue(decide(new Facts(CHAT, 0, true, Notification.CATEGORY_ALARM, false, false, SALE), blockAll,
                null).exempt);
        assertTrue(decide(new Facts(CHAT, 0, true, null, true, false, SALE), blockAll, null).exempt);
        assertTrue(decide(new Facts(CHAT, 0, true, null, false, true, SALE), blockAll, null).exempt);
        assertFalse(decide(new Facts(CHAT, 0, true, Notification.CATEGORY_MESSAGE, false, false, SALE), blockAll,
                null).exempt);
    }

    @Test
    public void firstMatchingRuleWins() {
        List<NotificationFilterRule> rules = Arrays.asList(rule("Quiet sales", "sale", Action.SILENT),
                rule("Block sales", "sale", Action.BLOCK));

        Result result = decide(facts(CHAT, SALE), rules, null);

        assertEquals(Action.SILENT, result.action);
        assertEquals("Quiet sales", result.ruleName);
        assertFalse(result.byQuietMode);
    }

    @Test
    public void laterRuleAppliesWhenTheFirstDoesNotMatch() {
        List<NotificationFilterRule> rules = Arrays.asList(rule("Mom", "mom", Action.ALLOW),
                rule("Block sales", "sale", Action.BLOCK));

        Result result = decide(facts(CHAT, SALE), rules, null);

        assertEquals(Action.BLOCK, result.action);
        assertEquals("Block sales", result.ruleName);
    }

    @Test
    public void allowRuleBeatsQuietMode() {
        List<NotificationFilterRule> rules = Collections.singletonList(rule("Mom", "mom", Action.ALLOW));

        Result result = decide(facts(CHAT, MOM), rules, Action.VIBRATE);

        assertEquals(Action.ALLOW, result.action);
        assertEquals("Mom", result.ruleName);
        assertFalse(result.byQuietMode);
    }

    @Test
    public void quietModeAppliesItsActionToWhatNoRuleMatches() {
        List<NotificationFilterRule> rules = Collections.singletonList(rule("Mom", "mom", Action.ALLOW));

        Result vibrate = decide(facts(CHAT, SALE), rules, Action.VIBRATE);
        Result silent = decide(facts(CHAT, SALE), rules, Action.SILENT);

        assertEquals(Action.VIBRATE, vibrate.action);
        assertTrue(vibrate.byQuietMode);
        assertNull(vibrate.ruleName);
        assertEquals(Action.SILENT, silent.action);
    }

    @Test
    public void quietModeLeavesSystemWarningsButRulesStillApply() {
        Result quiet = decide(facts("android", SALE), Collections.emptyList(), Action.SILENT);
        Result systemUi = decide(facts("com.android.systemui", SALE), Collections.emptyList(), Action.SILENT);
        Result ruled = decide(facts("android", SALE),
                Collections.singletonList(rule("Block", "sale", Action.BLOCK)), Action.SILENT);

        assertNull(quiet.action);
        assertFalse(quiet.exempt);
        assertNull(systemUi.action);
        assertEquals(Action.BLOCK, ruled.action);
    }

    @Test
    public void nothingHappensWithoutAMatchOrQuietMode() {
        Result result = decide(facts(CHAT, SALE), Collections.singletonList(rule("Mom", "mom", Action.BLOCK)), null);

        assertNull(result.action);
        assertFalse(result.exempt);
        assertFalse(result.byQuietMode);
    }
}
