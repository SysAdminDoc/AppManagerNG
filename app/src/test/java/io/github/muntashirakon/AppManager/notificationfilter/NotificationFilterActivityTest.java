// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Field;
import io.github.muntashirakon.AppManager.settings.Ops;

/** The filter screen and the rule editor, opened for real. */
@RunWith(RobolectricTestRunner.class)
public class NotificationFilterActivityTest {
    private Application mApp;
    private boolean mAuthenticatedBefore;
    private NotificationFilterSettings mSettings;
    private ActivityController<?> mController;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        NotificationFilterSettings.resetForTesting();
        mSettings = NotificationFilterSettings.getInstance(mApp);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        NotificationFilterSettings.resetForTesting();
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
    }

    @NonNull
    private NotificationFilterActivity openFilter() {
        ActivityController<NotificationFilterActivity> controller =
                Robolectric.buildActivity(NotificationFilterActivity.class).setup();
        mController = controller;
        ShadowLooper.idleMainLooper();
        return controller.get();
    }

    @NonNull
    private NotificationRuleEditorActivity openEditor(String ruleId) {
        Intent intent = NotificationRuleEditorActivity.newIntent(mApp, ruleId);
        ActivityController<NotificationRuleEditorActivity> controller =
                Robolectric.buildActivity(NotificationRuleEditorActivity.class, intent).setup();
        mController = controller;
        ShadowLooper.idleMainLooper();
        return controller.get();
    }

    @NonNull
    private static NotificationFilterRule rule(@NonNull String id, @NonNull String pattern) {
        return new NotificationFilterRule(id, "Rule " + id, null, Field.ANY, pattern, false, Action.SILENT);
    }

    @NonNull
    private List<String> ruleIds() {
        List<NotificationFilterRule> rules = mSettings.getRules();
        String[] ids = new String[rules.size()];
        for (int i = 0; i < ids.length; ++i) {
            ids[i] = rules.get(i).id;
        }
        return Arrays.asList(ids);
    }

    @Test
    public void rulesAreListedInOrderAndCanBeMovedAndDeleted() {
        mSettings.setRules(Arrays.asList(rule("a", "alpha"), rule("b", "beta"), rule("c", "(broken")));
        NotificationFilterActivity activity = openFilter();
        ViewGroup list = activity.getRulesList();

        assertEquals(3, list.getChildCount());
        assertEquals("Rule a", ((TextView) list.getChildAt(0).findViewById(R.id.rule_name)).getText().toString());
        assertFalse(list.getChildAt(0).findViewById(R.id.rule_up).isEnabled());
        assertFalse(list.getChildAt(2).findViewById(R.id.rule_down).isEnabled());

        list.getChildAt(0).findViewById(R.id.rule_down).performClick();
        assertEquals(Arrays.asList("b", "a", "c"), ruleIds());

        list = activity.getRulesList();
        list.getChildAt(1).findViewById(R.id.rule_delete).performClick();
        assertEquals(Arrays.asList("b", "c"), ruleIds());
        assertEquals(2, activity.getRulesList().getChildCount());
    }

    @Test
    public void emptyListSaysSo() {
        NotificationFilterActivity activity = openFilter();

        assertEquals(0, activity.getRulesList().getChildCount());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.rules_empty).getVisibility());
    }

    @Test
    public void quietSwitchTurnsQuietModeOn() {
        NotificationFilterActivity activity = openFilter();
        MaterialSwitch quiet = activity.findViewById(R.id.quiet_switch);
        assertFalse(quiet.isChecked());

        quiet.performClick();

        assertTrue(mSettings.isQuietModeActive(System.currentTimeMillis()));
    }

    @Test
    public void addOpensTheEditor() {
        NotificationFilterActivity activity = openFilter();

        activity.findViewById(R.id.rule_add).performClick();

        Intent next = shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(NotificationRuleEditorActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void editorSavesANewRuleForAnyApp() {
        NotificationRuleEditorActivity editor = openEditor(null);
        ((EditText) editor.findViewById(R.id.rule_pattern)).setText("sale");

        editor.findViewById(R.id.rule_save).performClick();

        List<NotificationFilterRule> rules = mSettings.getRules();
        assertEquals(1, rules.size());
        assertEquals("sale", rules.get(0).pattern);
        assertNull(rules.get(0).packageName);
        assertEquals(Action.SILENT, rules.get(0).action);
        assertFalse(rules.get(0).name.isEmpty());
        assertTrue(editor.isFinishing());
    }

    @Test
    public void editorRefusesABrokenRegex() {
        NotificationRuleEditorActivity editor = openEditor(null);
        ((EditText) editor.findViewById(R.id.rule_pattern)).setText("(sale");
        ((MaterialSwitch) editor.findViewById(R.id.rule_regex)).setChecked(true);

        editor.findViewById(R.id.rule_save).performClick();

        assertNotNull(((TextInputLayout) editor.findViewById(R.id.rule_pattern_layout)).getError());
        assertTrue(mSettings.getRules().isEmpty());
        assertFalse(editor.isFinishing());
    }

    @Test
    public void editorRefusesAnAppThatWasTypedButNotPicked() {
        NotificationRuleEditorActivity editor = openEditor(null);
        ((EditText) editor.findViewById(R.id.rule_app)).setText("Some app");

        editor.findViewById(R.id.rule_save).performClick();

        assertNotNull(((TextInputLayout) editor.findViewById(R.id.rule_app_layout)).getError());
        assertTrue(mSettings.getRules().isEmpty());
    }

    @Test
    public void typingInTheAppFieldReplacesAnyApp() {
        NotificationRuleEditorActivity editor = openEditor(null);
        EditText app = editor.findViewById(R.id.rule_app);
        assertEquals(editor.getString(R.string.notification_filter_rule_any_app), app.getText().toString());

        app.requestFocus();
        // What a keyboard does with typed text: replace the selection, or insert at the cursor.
        app.getText().replace(app.getSelectionStart(), app.getSelectionEnd(), "chr");

        assertEquals("chr", app.getText().toString());
    }

    @Test
    public void editorKeepsTheIdOfTheRuleItEdits() {
        mSettings.setRules(Arrays.asList(rule("a", "alpha"), rule("b", "beta")));
        NotificationRuleEditorActivity editor = openEditor("b");
        EditText pattern = editor.findViewById(R.id.rule_pattern);
        assertEquals("beta", pattern.getText().toString());

        pattern.setText("gamma");
        editor.findViewById(R.id.rule_save).performClick();

        assertEquals(Arrays.asList("a", "b"), ruleIds());
        assertEquals("gamma", mSettings.findRule("b").pattern);
        assertEquals("Rule b", mSettings.findRule("b").name);
    }

    @Test
    public void editorForARuleThatIsGoneCloses() {
        NotificationRuleEditorActivity editor = openEditor("missing");

        assertTrue(editor.isFinishing());
    }
}
