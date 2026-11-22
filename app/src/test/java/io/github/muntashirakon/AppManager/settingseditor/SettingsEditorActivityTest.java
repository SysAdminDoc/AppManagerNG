// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.DialogInterface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

/** Issue 48: the Labs settings editor lists, edits, warns about risky keys and puts changes back. */
@RunWith(RobolectricTestRunner.class)
public class SettingsEditorActivityTest {
    @Rule
    public final TemporaryFolder mFolder = new TemporaryFolder();

    private Application mApp;
    private FakeBackend mBackend;
    private File mAuditFile;
    private ActivityController<SettingsEditorActivity> mController;
    private boolean mAuthenticatedBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        mBackend = new FakeBackend();
        mBackend.values.put("system/screen_brightness", "128");
        mBackend.values.put("secure/adb_enabled", "1");
        mBackend.values.put("secure/lock_screen_owner_info", "Mine");
        mAuditFile = new File(mFolder.getRoot(), SettingsAuditLog.FILE_NAME);
        SettingsEditorViewModel.setForTesting(mBackend, mAuditFile);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        SettingsEditorViewModel.setForTesting(null, null);
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
    }

    @Test
    public void listsTheTableAndMarksRiskyKeys() {
        SettingsEditorActivity activity = open();
        waitForRows(activity, 1);
        assertEquals("screen_brightness", text(row(activity, 0), R.id.key));
        assertEquals(View.GONE, row(activity, 0).findViewById(R.id.risky_chip).getVisibility());

        selectTab(activity, 1);
        waitForRows(activity, 2);

        assertEquals("adb_enabled", text(row(activity, 0), R.id.key));
        assertEquals(View.VISIBLE, row(activity, 0).findViewById(R.id.risky_chip).getVisibility());
        assertEquals(View.VISIBLE, row(activity, 1).findViewById(R.id.risky_chip).getVisibility());
        // Root, ADB or Shizuku: nothing to explain, and keys can be added.
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.mode_note).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.floatingActionButton).getVisibility());
    }

    @Test
    public void editingSavesRecordsAndRevertsFromChanges() {
        SettingsEditorActivity activity = open();
        selectTab(activity, 1);
        waitForRows(activity, 2);

        click(row(activity, 0));
        AlertDialog dialog = activity.getOpenDialog();
        assertNotNull(dialog);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                dialog.getWindow().getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
        // The note says what turning ADB off does.
        TextView note = dialog.findViewById(R.id.risk_note);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertEquals(mApp.getString(R.string.settings_editor_risk_adb), note.getText().toString());
        ((TextView) dialog.findViewById(R.id.value)).setText("0");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        waitFor(() -> "0".equals(mBackend.values.get("secure/adb_enabled")));

        selectTab(activity, 3);
        waitForRows(activity, 1);
        assertEquals(mApp.getString(R.string.settings_editor_change_values, "1", "0"),
                text(row(activity, 0), R.id.summary));
        click(row(activity, 0).findViewById(R.id.revert));

        waitFor(() -> "1".equals(mBackend.values.get("secure/adb_enabled")));
        waitForRows(activity, 2);
        assertEquals(mApp.getString(R.string.settings_editor_change_values, "0", "1"),
                text(row(activity, 0), R.id.summary));
    }

    @Test
    public void deletingFromTheDialogIsImmediateAndAddingChecksTheKey() {
        SettingsEditorActivity activity = open();
        waitForRows(activity, 1);

        click(row(activity, 0));
        click(activity.getOpenDialog().getButton(DialogInterface.BUTTON_NEUTRAL));
        waitFor(() -> !mBackend.values.containsKey("system/screen_brightness"));

        click(activity.findViewById(R.id.floatingActionButton));
        AlertDialog dialog = activity.getOpenDialog();
        ((TextView) dialog.findViewById(R.id.key)).setText("bad key; reboot");
        ((TextView) dialog.findViewById(R.id.value)).setText("1");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertTrue("a bad key keeps the dialog open", dialog.isShowing());
        assertNotNull(((TextInputLayout) dialog.findViewById(R.id.key_layout)).getError());

        ((TextView) dialog.findViewById(R.id.key)).setText("screen_off_timeout");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        waitFor(() -> "1".equals(mBackend.values.get("system/screen_off_timeout")));
        assertFalse(dialog.isShowing());
    }

    @Test
    public void withoutPrivilegesTheTableIsReadOnlyAndSaysWhy() {
        mBackend.privileged = false;
        mBackend.writable = false;
        SettingsEditorActivity activity = open();
        waitForRows(activity, 1);

        TextView note = activity.findViewById(R.id.mode_note);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertEquals(mApp.getString(R.string.settings_editor_note_read_only_system), note.getText().toString());
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.floatingActionButton).getVisibility());
        selectTab(activity, 2);
        waitFor(() -> mApp.getString(R.string.settings_editor_note_read_only)
                .equals(((TextView) activity.findViewById(R.id.mode_note)).getText().toString()));

        selectTab(activity, 0);
        waitForRows(activity, 1);
        click(row(activity, 0));
        AlertDialog dialog = activity.getOpenDialog();
        // Only copying and closing.
        assertFalse(dialog.findViewById(R.id.value).isEnabled());
        assertEquals(mApp.getString(R.string.copy), dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText());
        assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getVisibility());
    }

    @NonNull
    private SettingsEditorActivity open() {
        mController = Robolectric.buildActivity(SettingsEditorActivity.class).setup();
        ShadowLooper.idleMainLooper();
        return mController.get();
    }

    private static void selectTab(@NonNull SettingsEditorActivity activity, int position) {
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        TabLayout.Tab tab = tabs.getTabAt(position);
        assertNotNull(tab);
        tab.select();
        ShadowLooper.idleMainLooper();
    }

    @NonNull
    private static View row(@NonNull SettingsEditorActivity activity, int position) {
        RecyclerView list = activity.findViewById(android.R.id.list);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 2400);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position, holder);
        return holder.itemView;
    }

    /** Dialogs run their show listener (which wires Save) from the main looper, so let it run. */
    private static void click(@NonNull View view) {
        view.performClick();
        ShadowLooper.idleMainLooper();
    }

    /** Lets the add button's show and hide animations finish. */
    private static void settle() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
    }

    @NonNull
    private static String text(@NonNull View row, int id) {
        return ((TextView) row.findViewById(id)).getText().toString();
    }

    private static void waitForRows(@NonNull SettingsEditorActivity activity, int rows) {
        RecyclerView list = activity.findViewById(android.R.id.list);
        waitFor(() -> list.getAdapter() != null && list.getAdapter().getItemCount() == rows);
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
        ShadowLooper.idleMainLooper();
    }

    /** Keys as "table/key". */
    private static final class FakeBackend implements SettingsBackend {
        final Map<String, String> values = new ConcurrentHashMap<>();
        volatile boolean privileged = true;
        volatile boolean writable = true;

        @Override
        public boolean isPrivileged() {
            return privileged;
        }

        @Override
        public boolean canWrite(@NonNull SettingsTable table) {
            return writable;
        }

        @NonNull
        @Override
        public List<SettingEntry> list(@NonNull SettingsTable table) {
            Map<String, String> rows = new TreeMap<>();
            String prefix = table.shellName + "/";
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    rows.put(entry.getKey().substring(prefix.length()), entry.getValue());
                }
            }
            List<SettingEntry> entries = new ArrayList<>();
            for (Map.Entry<String, String> row : rows.entrySet()) {
                entries.add(new SettingEntry(row.getKey(), row.getValue()));
            }
            return entries;
        }

        @Nullable
        @Override
        public String get(@NonNull SettingsTable table, @NonNull String key) {
            return values.get(table.shellName + "/" + key);
        }

        @Override
        public void put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value) {
            values.put(table.shellName + "/" + key, value);
        }

        @Override
        public void delete(@NonNull SettingsTable table, @NonNull String key) {
            values.remove(table.shellName + "/" + key);
        }
    }
}
