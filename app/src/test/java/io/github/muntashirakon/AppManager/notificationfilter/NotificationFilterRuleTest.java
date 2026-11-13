// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Field;

// Robolectric only for org.json, which android.jar stubs out.
@RunWith(RobolectricTestRunner.class)
public class NotificationFilterRuleTest {
    private static final String CHAT = "com.example.chat";
    private static final String SHOP = "com.example.shop";

    @NonNull
    private static NotificationFilterRule rule(@Nullable String packageName, @NonNull Field field,
                                               @NonNull String pattern, boolean regex) {
        return new NotificationFilterRule("id", "Rule", packageName, field, pattern, regex, Action.SILENT);
    }

    @NonNull
    private static NotificationFields fields(@NonNull String title, @NonNull String text, @NonNull String sender) {
        return new NotificationFields(title, text, sender);
    }

    @Test
    public void plainPatternIsACaseInsensitiveContains() {
        NotificationFilterRule rule = rule(null, Field.ANY, "flash sale", false);

        assertTrue(rule.matches(SHOP, fields("Today only", "FLASH SALE on shoes", "")));
        assertFalse(rule.matches(SHOP, fields("Today only", "A sale, in a flash", "")));
    }

    @Test
    public void regexIsCaseInsensitiveAndUsesTheWholePattern() {
        NotificationFilterRule rule = rule(null, Field.TEXT, "^order #\\d+ shipped$", true);

        assertTrue(rule.isValid());
        assertTrue(rule.matches(SHOP, fields("Shop", "Order #1234 shipped", "")));
        assertFalse(rule.matches(SHOP, fields("Shop", "Order #1234 shipped today", "")));
        assertFalse(rule.matches(SHOP, fields("Shop", "Order #abc shipped", "")));
    }

    @Test
    public void plainPatternTreatsRegexCharactersAsText() {
        NotificationFilterRule rule = rule(null, Field.ANY, "50% off (today)", false);

        assertTrue(rule.matches(SHOP, fields("", "Get 50% off (today) only", "")));
        assertFalse(rule.matches(SHOP, fields("", "Get 50 off today", "")));
    }

    @Test
    public void fieldDecidesWhereThePatternLooks() {
        NotificationFields groupMessage = fields("Family group", "Mom: dinner at six", "Mom");

        assertFalse(rule(null, Field.TITLE, "mom", false).matches(CHAT, groupMessage));
        assertTrue(rule(null, Field.TEXT, "mom", false).matches(CHAT, groupMessage));
        assertTrue(rule(null, Field.SENDER, "mom", false).matches(CHAT, groupMessage));
        assertTrue(rule(null, Field.ANY, "family", false).matches(CHAT, groupMessage));
        assertFalse(rule(null, Field.SENDER, "family", false).matches(CHAT, groupMessage));
    }

    @Test
    public void ruleForOneAppIgnoresEveryOtherApp() {
        NotificationFilterRule rule = rule(CHAT, Field.ANY, "hello", false);

        assertTrue(rule.matches(CHAT, fields("hello", "", "")));
        assertFalse(rule.matches(SHOP, fields("hello", "", "")));
        // An empty package name is saved as "any app".
        assertNull(rule("", Field.ANY, "hello", false).packageName);
    }

    @Test
    public void emptyPatternMatchesEverythingFromTheApp() {
        NotificationFilterRule rule = rule(SHOP, Field.ANY, "", false);

        assertTrue(rule.isValid());
        assertTrue(rule.matches(SHOP, fields("", "", "")));
        assertTrue(rule.matches(SHOP, fields("Anything", "at all", "")));
        assertFalse(rule.matches(CHAT, fields("Anything", "at all", "")));
    }

    @Test
    public void brokenRegexIsKeptButNeverMatches() {
        NotificationFilterRule unclosed = rule(null, Field.ANY, "(unclosed", true);
        NotificationFilterRule stalls = rule(null, Field.ANY, "(a+)+", true);

        assertFalse(unclosed.isValid());
        assertFalse(unclosed.matches(SHOP, fields("(unclosed", "(unclosed", "(unclosed")));
        assertFalse(stalls.isValid());
        assertFalse(stalls.matches(SHOP, fields("aaaa", "aaaa", "aaaa")));
        // The same text as a plain pattern is fine.
        assertTrue(rule(null, Field.ANY, "(unclosed", false).matches(SHOP, fields("(unclosed", "", "")));
    }

    @Test
    public void jsonRoundTripKeepsEverything() throws Exception {
        NotificationFilterRule oneApp = new NotificationFilterRule("a1", "Muted promos", SHOP, Field.TITLE,
                "^promo", true, Action.BLOCK);
        NotificationFilterRule anyApp = new NotificationFilterRule("a2", "Quiet Bob", null, Field.SENDER, "bob",
                false, Action.VIBRATE);

        NotificationFilterRule oneAppBack = NotificationFilterRule.fromJson(new JSONObject(oneApp.toJson().toString()));
        NotificationFilterRule anyAppBack = NotificationFilterRule.fromJson(new JSONObject(anyApp.toJson().toString()));

        assertEquals(oneApp, oneAppBack);
        assertNotNull(anyAppBack);
        assertEquals(anyApp, anyAppBack);
        assertNull(anyAppBack.packageName);
        assertTrue(oneAppBack.matches(SHOP, fields("Promo inside", "", "")));
    }

    @Test
    public void unreadableJsonGivesNoRule() throws Exception {
        JSONObject json = new NotificationFilterRule("a1", "Rule", null, Field.ANY, "x", false, Action.SILENT)
                .toJson();
        json.put("action", "EXPLODE");
        assertNull(NotificationFilterRule.fromJson(json));

        JSONObject missing = new JSONObject().put("id", "a1").put("name", "Rule");
        assertNull(NotificationFilterRule.fromJson(missing));
    }

    @Test
    public void createGivesEachRuleItsOwnId() {
        NotificationFilterRule first = NotificationFilterRule.create("A", null, Field.ANY, "x", false, Action.SILENT);
        NotificationFilterRule second = NotificationFilterRule.create("A", null, Field.ANY, "x", false,
                Action.SILENT);

        assertFalse(first.id.equals(second.id));
    }
}
