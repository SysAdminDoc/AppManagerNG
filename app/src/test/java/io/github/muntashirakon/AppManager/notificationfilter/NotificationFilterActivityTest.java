// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListAdapter;
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
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.PinLength;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Priority;
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

    /** The toolbar's own title from the layout used to win, so editing a rule still said "New rule". */
    @Test
    public void editorTitleSaysWhetherItsANewRule() {
        mSettings.setRules(Arrays.asList(rule("a", "alpha")));
        NotificationRuleEditorActivity editor = openEditor("a");
        androidx.appcompat.widget.Toolbar toolbar = editor.findViewById(R.id.toolbar);
        assertEquals(mApp.getString(R.string.notification_filter_rule_edit), String.valueOf(toolbar.getTitle()));
        mController.pause().stop().destroy();
        mController = null;

        NotificationRuleEditorActivity newRule = openEditor(null);
        toolbar = newRule.findViewById(R.id.toolbar);
        assertEquals(mApp.getString(R.string.notification_filter_rule_new), String.valueOf(toolbar.getTitle()));
    }

    private static void pick(@NonNull AutoCompleteTextView view, @NonNull String label) {
        ListAdapter adapter = view.getAdapter();
        for (int i = 0; i < adapter.getCount(); ++i) {
            if (label.equals(String.valueOf(adapter.getItem(i)))) {
                view.setText(label, false);
                view.getOnItemClickListener().onItemClick(null, null, i, i);
                return;
            }
        }
        fail("No " + label + " in the list");
    }

    /** #32: ongoing, the pin length and the priority are saved with the rule. */
    @Test
    public void editorSavesOngoingPinLengthAndPriority() {
        NotificationRuleEditorActivity editor = openEditor(null);
        View pinFor = editor.findViewById(R.id.rule_pin_for_layout);
        View priority = editor.findViewById(R.id.rule_priority_layout);
        TextView ongoingNote = editor.findViewById(R.id.rule_ongoing_note);
        assertEquals("Silent posts a copy", View.VISIBLE, priority.getVisibility());
        assertEquals(View.GONE, pinFor.getVisibility());
        assertEquals(View.GONE, ongoingNote.getVisibility());

        pick(editor.findViewById(R.id.rule_action), editor.getString(R.string.signer_pin_action_pin));
        assertEquals(View.VISIBLE, pinFor.getVisibility());
        pick(editor.findViewById(R.id.rule_pin_for), editor.getString(R.string.notification_filter_pin_one_hour));
        pick(editor.findViewById(R.id.rule_priority), editor.getString(R.string.notification_filter_priority_high));
        ((MaterialSwitch) editor.findViewById(R.id.rule_ongoing)).setChecked(true);
        assertEquals(View.VISIBLE, ongoingNote.getVisibility());
        // Android 8 and later snooze what they can't remove, and say so.
        assertEquals(editor.getString(R.string.notification_filter_ongoing_snooze), ongoingNote.getText().toString());

        editor.findViewById(R.id.rule_save).performClick();

        NotificationFilterRule saved = mSettings.getRules().get(0);
        assertEquals(Action.PIN, saved.action);
        assertTrue(saved.ongoing);
        assertEquals(Priority.HIGH, saved.priority);
        assertEquals(PinLength.ONE_HOUR, saved.pinFor);
    }

    @Test
    public void priorityShowsOnlyForActionsThatPostACopy() {
        NotificationRuleEditorActivity editor = openEditor(null);
        AutoCompleteTextView action = editor.findViewById(R.id.rule_action);
        View priority = editor.findViewById(R.id.rule_priority_layout);
        View pinFor = editor.findViewById(R.id.rule_pin_for_layout);

        pick(action, editor.getString(R.string.notification_filter_action_allow));
        assertEquals(View.GONE, priority.getVisibility());
        pick(action, editor.getString(R.string.block));
        assertEquals(View.GONE, priority.getVisibility());
        pick(action, editor.getString(R.string.notification_filter_action_keep_icon));
        assertEquals(View.VISIBLE, priority.getVisibility());
        assertEquals(View.GONE, pinFor.getVisibility());
        assertEquals(editor.getString(R.string.notification_filter_action_keep_icon_summary),
                ((TextView) editor.findViewById(R.id.rule_action_summary)).getText().toString());
        pick(action, editor.getString(R.string.notification_filter_action_vibrate));
        assertEquals(View.VISIBLE, priority.getVisibility());
    }

    @Test
    public void editorKeepsTheOptionsOfTheRuleItEdits() {
        mSettings.setRules(Arrays.asList(rule("a", "alpha").withOptions(true, Priority.LOW, PinLength.TODAY)));
        NotificationRuleEditorActivity editor = openEditor("a");
        assertTrue(((MaterialSwitch) editor.findViewById(R.id.rule_ongoing)).isChecked());
        assertEquals(editor.getString(R.string.notification_filter_priority_low),
                ((TextView) editor.findViewById(R.id.rule_priority)).getText().toString());

        ((EditText) editor.findViewById(R.id.rule_pattern)).setText("alphabet");
        editor.findViewById(R.id.rule_save).performClick();

        NotificationFilterRule saved = mSettings.findRule("a");
        assertEquals("alphabet", saved.pattern);
        assertTrue(saved.ongoing);
        assertEquals(Priority.LOW, saved.priority);
        assertEquals(PinLength.TODAY, saved.pinFor);
    }

    @Test
    public void editorForARuleThatIsGoneCloses() {
        NotificationRuleEditorActivity editor = openEditor("missing");

        assertTrue(editor.isFinishing());
    }
}
