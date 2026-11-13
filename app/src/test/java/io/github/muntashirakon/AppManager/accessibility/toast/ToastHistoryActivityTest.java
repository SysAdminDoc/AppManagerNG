// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.function.BooleanSupplier;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class ToastHistoryActivityTest {
    private static final String ONE = "com.example.one";
    private static final String TWO = "com.example.two";
    /** Recent enough that the history's age limit keeps every toast these tests save. */
    private static final long T0 = System.currentTimeMillis() - 60_000L;

    private Application mApp;
    private ToastHistory mHistory;
    private ActivityController<ToastHistoryActivity> mController;
    private boolean mAuthenticatedBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        mHistory = ToastHistoryTestAccess.useInMemory(mApp);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        Prefs.Privacy.setToastHistoryEnabled(false);
        ToastHistoryTestAccess.reset();
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
        Settings.Secure.putString(mApp.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null);
    }

    @NonNull
    private ToastHistoryActivity open() {
        mController = Robolectric.buildActivity(ToastHistoryActivity.class).setup();
        ShadowLooper.idleMainLooper();
        return mController.get();
    }

    private static void waitFor(@NonNull BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!condition.getAsBoolean()) {
            assertTrue("timed out", System.currentTimeMillis() < deadline);
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private void waitForRows(@NonNull ToastHistoryActivity activity, int rows) {
        waitFor(() -> activity.getVisibleEntries().size() == rows);
    }

    /** The toast comes after the clipboard is set, so what's there is from this test and not an earlier one. */
    @NonNull
    private String waitForCopy() {
        waitFor(() -> mApp.getString(R.string.copied_to_clipboard).equals(ShadowToast.getTextOfLatestToast()));
        ClipboardManager clipboard = (ClipboardManager) mApp.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
    }

    private void turnAccessibilityOn() {
        Settings.Secure.putString(mApp.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                mApp.getPackageName() + "/.accessibility.NoRootAccessibilityService");
    }

    @Test
    public void recordingStartsOff() {
        ToastHistoryActivity activity = open();

        MaterialSwitch toggle = activity.findViewById(R.id.record_switch);

        assertFalse(toggle.isChecked());
        assertEquals(View.GONE, activity.findViewById(R.id.service_off_group).getVisibility());
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertEquals(mApp.getString(R.string.toast_history_empty_off), text(activity, R.id.empty_state_summary));
        assertEquals(mApp.getString(R.string.toast_history_turn_on), text(activity, R.id.empty_state_action));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.empty_state_action).getVisibility());
    }

    @Test
    public void theEmptyScreensButtonTurnsRecordingOn() {
        turnAccessibilityOn();
        ToastHistoryActivity activity = open();

        activity.findViewById(R.id.empty_state_action).performClick();

        assertTrue(Prefs.Privacy.isToastHistoryEnabled());
        assertTrue(((MaterialSwitch) activity.findViewById(R.id.record_switch)).isChecked());
        assertEquals(mApp.getString(R.string.toast_history_empty_on), text(activity, R.id.empty_state_summary));
        assertEquals(View.GONE, activity.findViewById(R.id.empty_state_action).getVisibility());
    }

    @Test
    public void withRecordingOnButTheServiceOffTheEmptyScreenOpensAccessibilitySettings() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        ToastHistoryActivity activity = open();
        assertEquals(mApp.getString(R.string.toast_history_service_off), text(activity, R.id.empty_state_summary));
        assertEquals(mApp.getString(R.string.toast_history_open_accessibility),
                text(activity, R.id.empty_state_action));

        activity.findViewById(R.id.empty_state_action).performClick();

        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(activity).getNextStartedActivity().getAction());
    }

    @Test
    public void turningItOnWithTheServiceOffGoesToTheAccessibilityScreenAndSaysWhy() {
        ToastHistoryActivity activity = open();

        ((MaterialSwitch) activity.findViewById(R.id.record_switch)).setChecked(true);
        ShadowLooper.idleMainLooper();

        assertTrue(Prefs.Privacy.isToastHistoryEnabled());
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, next.getAction());
        assertEquals(mApp.getString(R.string.toast_history_needs_accessibility), ShadowToast.getTextOfLatestToast());
        assertEquals(mApp.getString(R.string.toast_history_service_off), text(activity, R.id.empty_state_summary));
    }

    @Test
    public void turningItOnWithTheServiceOnJustStarts() {
        turnAccessibilityOn();
        ToastHistoryActivity activity = open();

        ((MaterialSwitch) activity.findViewById(R.id.record_switch)).setChecked(true);

        assertTrue(Prefs.Privacy.isToastHistoryEnabled());
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertEquals(View.GONE, activity.findViewById(R.id.service_off_group).getVisibility());
    }

    @Test
    public void comingBackWithTheServiceOnClearsTheWarning() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        mHistory.record(T0 + 1_000L, ONE, "Saved");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 1);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.service_off_group).getVisibility());
        assertNull("reopening the screen shouldn't send the user away again",
                shadowOf(activity).getNextStartedActivity());

        turnAccessibilityOn();
        mController.pause().resume();

        assertEquals(View.GONE, activity.findViewById(R.id.service_off_group).getVisibility());
    }

    @Test
    public void turningItOffStopsRecordingButKeepsTheHistory() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        turnAccessibilityOn();
        mHistory.record(T0 + 1_000L, ONE, "Saved");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 1);

        ((MaterialSwitch) activity.findViewById(R.id.record_switch)).setChecked(false);

        assertFalse(Prefs.Privacy.isToastHistoryEnabled());
        assertEquals(1, mHistory.readAll().size());
    }

    @Test
    public void rowsShowEverythingNewestFirst() {
        mHistory.record(T0 + 1_000L, ONE, "Older");
        mHistory.record(T0 + 2_234L, TWO, "Newer");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 2);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        View first = list.getChildAt(0);

        assertEquals("Label of " + TWO, text(first, R.id.label));
        assertEquals(TWO, text(first, R.id.package_name));
        assertEquals("Newer", text(first, R.id.text));
        assertEquals(ToastEntry.newTimeFormat().format(new java.util.Date(T0 + 2_234L)), text(first, R.id.time));
        assertEquals("Older", text(list.getChildAt(1), R.id.text));
    }

    @Test
    public void aToastWithNoTextSaysSo() {
        mHistory.record(T0 + 1_000L, ONE, "");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 1);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        assertEquals(mApp.getString(R.string.toast_history_no_text), text(list.getChildAt(0), R.id.text));
    }

    @Test
    public void aNewToastShowsUpWhileTheScreenIsOpen() {
        ToastHistoryActivity activity = open();

        mHistory.record(T0 + 1_000L, ONE, "Saved");

        waitForRows(activity, 1);
    }

    @Test
    public void theFilterNarrowsToOneAppAndCanGoBack() {
        mHistory.record(T0 + 1_000L, ONE, "From one");
        mHistory.record(T0 + 2_000L, TWO, "From two");
        mHistory.record(T0 + 3_000L, TWO, "From two again");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 3);
        AutoCompleteTextView filter = activity.findViewById(R.id.filter_app);
        assertEquals(mApp.getString(R.string.toast_history_filter_all, 3), filter.getText().toString());

        // Row 0 is every app, and the rest are sorted by name.
        filter.getOnItemClickListener().onItemClick(null, null, 2, 0);
        waitForRows(activity, 2);
        assertEquals(TWO, activity.getVisibleEntries().get(0).packageName);
        assertEquals(mApp.getString(R.string.toast_history_filter_item, "Label of " + TWO, 2),
                filter.getText().toString());

        filter = activity.findViewById(R.id.filter_app);
        filter.getOnItemClickListener().onItemClick(null, null, 0, 0);
        waitForRows(activity, 3);
    }

    @Test
    public void theToolbarHasARegexCapableSearch() {
        ToastHistoryActivity activity = open();

        View searchView = shadowOf(activity).getOptionsMenu().findItem(R.id.action_search).getActionView();

        assertTrue(searchView instanceof AdvancedSearchView);
    }

    @Test
    public void searchNarrowsTheListAndWorksWithTheAppFilter() {
        mHistory.record(T0 + 1_000L, ONE, "Battery low");
        mHistory.record(T0 + 2_000L, TWO, "Battery saver on");
        mHistory.record(T0 + 3_000L, TWO, "Saved");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 3);

        activity.onQueryTextChange("BATTERY", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        waitForRows(activity, 2);

        AutoCompleteTextView filter = activity.findViewById(R.id.filter_app);
        filter.getOnItemClickListener().onItemClick(null, null, 2, 0);
        waitForRows(activity, 1);
        assertEquals("Battery saver on", activity.getVisibleEntries().get(0).text);

        // The package matches too, and a search that hides every row says so.
        activity.onQueryTextChange("example.one", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        waitForRows(activity, 0);
        assertEquals(mApp.getString(R.string.toast_history_no_matches_title), text(activity, R.id.empty_state_title));
        assertEquals(mApp.getString(R.string.toast_history_no_matches), text(activity, R.id.empty_state_summary));

        filter = activity.findViewById(R.id.filter_app);
        filter.getOnItemClickListener().onItemClick(null, null, 0, 0);
        waitForRows(activity, 1);
        assertEquals(ONE, activity.getVisibleEntries().get(0).packageName);

        activity.onQueryTextChange("", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        waitForRows(activity, 3);
    }

    @Test
    public void aRegexSearchMatchesAndABrokenOneSaysWhyNothingShows() {
        mHistory.record(T0 + 1_000L, ONE, "Photo saved");
        mHistory.record(T0 + 2_000L, TWO, "Photo deleted");
        mHistory.record(T0 + 3_000L, TWO, "Battery saver on");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 3);

        activity.onQueryTextChange("^photo (saved|deleted)$", AdvancedSearchView.SEARCH_TYPE_REGEX);
        waitForRows(activity, 2);

        activity.onQueryTextChange("photo (saved", AdvancedSearchView.SEARCH_TYPE_REGEX);
        waitForRows(activity, 0);
        assertEquals(mApp.getString(R.string.search_regex_unusable), text(activity, R.id.empty_state_summary));

        // The same text as plain text isn't a pattern at all.
        activity.onQueryTextChange("photo (saved", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        waitFor(() -> mApp.getString(R.string.toast_history_no_matches)
                .equals(text(activity, R.id.empty_state_summary)));
        assertTrue(activity.getVisibleEntries().isEmpty());
    }

    @Test
    public void clearingIsImmediateAndOffersUndo() {
        mHistory.record(T0 + 1_000L, ONE, "Older");
        mHistory.record(T0 + 2_000L, TWO, "Newer");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 2);

        shadowOf(activity).clickMenuItem(R.id.action_clear);
        ShadowLooper.idleMainLooper();

        waitForRows(activity, 0);
        assertTrue(mHistory.readAll().isEmpty());
        assertEquals(View.GONE, activity.findViewById(R.id.filter_layout).getVisibility());
        assertEquals(mApp.getString(R.string.toast_history_cleared),
                text(activity, com.google.android.material.R.id.snackbar_text));

        activity.findViewById(com.google.android.material.R.id.snackbar_action).performClick();

        waitForRows(activity, 2);
        assertEquals("Newer", activity.getVisibleEntries().get(0).text);
        assertEquals(2, mHistory.readAll().size());
    }

    @Test
    public void copyAllCopiesWhatIsShown() {
        mHistory.record(T0 + 1_000L, ONE, "From one");
        mHistory.record(T0 + 2_000L, TWO, "From two");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 2);
        AutoCompleteTextView filter = activity.findViewById(R.id.filter_app);
        filter.getOnItemClickListener().onItemClick(null, null, 2, 0);
        waitForRows(activity, 1);

        shadowOf(activity).clickMenuItem(R.id.action_copy_all);

        String copied = waitForCopy();
        assertTrue(copied.contains("From two"));
        assertFalse(copied.contains("From one"));
    }

    @Test
    public void tappingARowCopiesIt() {
        mHistory.record(T0 + 1_000L, ONE, "From one");
        mHistory.record(T0 + 2_000L, TWO, "From two");
        ToastHistoryActivity activity = open();
        waitForRows(activity, 2);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        list.getChildAt(1).performClick();

        String copied = waitForCopy();
        assertTrue(copied.contains("From one"));
        assertFalse(copied.contains("From two"));
    }

    @Test
    public void copyingNothingSaysSo() {
        ToastHistoryActivity activity = open();

        shadowOf(activity).clickMenuItem(R.id.action_copy_all);

        assertEquals(mApp.getString(R.string.toast_history_nothing_to_copy), ShadowToast.getTextOfLatestToast());
    }

    private static void layOut(@NonNull RecyclerView list) {
        ShadowLooper.idleMainLooper();
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
    }

    @NonNull
    private static String text(@NonNull View row, int id) {
        return ((TextView) row.findViewById(id)).getText().toString();
    }

    @NonNull
    private static String text(@NonNull ToastHistoryActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
