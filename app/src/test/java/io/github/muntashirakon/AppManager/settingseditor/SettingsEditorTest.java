// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Issue 48: every change goes on the trail with what it replaced, and putting one back undoes exactly that. */
@RunWith(RobolectricTestRunner.class)
public class SettingsEditorTest {
    @Rule
    public final TemporaryFolder mFolder = new TemporaryFolder();

    private FakeBackend mBackend;
    private SettingsAuditLog mLog;
    private SettingsEditor mEditor;
    private long mNow = 1_000L;

    @Before
    public void setUp() {
        mBackend = new FakeBackend();
        mLog = new SettingsAuditLog(new File(mFolder.getRoot(), SettingsAuditLog.FILE_NAME));
        mEditor = new SettingsEditor(mBackend, mLog, () -> mNow++);
    }

    @Test
    public void aChangeRecordsTheOldAndNewValue() throws IOException {
        mBackend.values.put("secure/adb_enabled", "1");

        SettingsChange change = mEditor.set(SettingsTable.SECURE, "adb_enabled", "0");

        assertEquals("0", mBackend.values.get("secure/adb_enabled"));
        assertEquals("1", change.oldValue);
        assertEquals("0", change.newValue);
        assertEquals(Collections.singletonList(change), mLog.readNewestFirst());
    }

    @Test
    public void revertingAKeyThatWasNotSetDeletesIt() throws IOException {
        SettingsChange added = mEditor.set(SettingsTable.GLOBAL, "new_key", "on");
        assertNull("not set before", added.oldValue);

        SettingsChange revert = mEditor.revert(added);

        assertFalse(mBackend.values.containsKey("global/new_key"));
        assertTrue(revert.isDeletion());
        assertEquals("on", revert.oldValue);
        assertEquals("delete global/new_key", mBackend.calls.get(mBackend.calls.size() - 1));
    }

    @Test
    public void revertingADeletionPutsTheValueBack() throws IOException {
        mBackend.values.put("system/screen_off_timeout", "30000");
        SettingsChange deleted = mEditor.set(SettingsTable.SYSTEM, "screen_off_timeout", null);
        assertTrue(deleted.isDeletion());
        assertFalse(mBackend.values.containsKey("system/screen_off_timeout"));

        SettingsChange revert = mEditor.revert(deleted);

        assertEquals("30000", mBackend.values.get("system/screen_off_timeout"));
        assertNull("the key was gone before the revert", revert.oldValue);
        assertEquals("30000", revert.newValue);
    }

    @Test
    public void aRevertStartsFromTheCurrentValueAndIsOnTheTrailToo() throws IOException {
        mBackend.values.put("secure/lock_screen_owner_info", "Mine");
        SettingsChange first = mEditor.set(SettingsTable.SECURE, "lock_screen_owner_info", "Yours");
        // Something else changed it again since.
        mBackend.values.put("secure/lock_screen_owner_info", "Theirs");

        SettingsChange revert = mEditor.revert(first);

        assertEquals("Mine", mBackend.values.get("secure/lock_screen_owner_info"));
        assertEquals("Theirs", revert.oldValue);
        assertEquals(Arrays.asList(revert, first), mLog.readNewestFirst());
    }

    @Test
    public void settingTheSameValueWritesAndRecordsNothing() throws IOException {
        mBackend.values.put("global/wifi_on", "1");

        SettingsChange change = mEditor.set(SettingsTable.GLOBAL, "wifi_on", "1");

        assertTrue(change.isNoOp());
        assertEquals(Collections.singletonList("get global/wifi_on"), mBackend.calls);
        assertTrue(mLog.readNewestFirst().isEmpty());
        // Deleting a key that isn't there is nothing too.
        assertTrue(mEditor.set(SettingsTable.GLOBAL, "missing", null).isNoOp());
    }

    @Test
    public void aRefusedChangeIsNotRecorded() {
        mBackend.refuseWrites = true;

        try {
            mEditor.set(SettingsTable.SECURE, "adb_enabled", "0");
        } catch (IOException expected) {
            assertEquals("refused", expected.getMessage());
        }

        assertTrue(mLog.readNewestFirst().isEmpty());
    }

    @Test
    public void theTrailSurvivesOddValuesAndDamagedLines() throws IOException {
        String odd = "line one\nline \"two\" with 'quotes' and null";
        mEditor.set(SettingsTable.SECURE, "odd", odd);
        mEditor.set(SettingsTable.SECURE, "literal_null", "null");
        try (FileOutputStream out = new FileOutputStream(new File(mFolder.getRoot(), SettingsAuditLog.FILE_NAME),
                true)) {
            out.write("{not json\n".getBytes(StandardCharsets.UTF_8));
        }

        List<SettingsChange> changes = mLog.readNewestFirst();

        assertEquals(2, changes.size());
        assertEquals("null", changes.get(0).newValue);
        assertEquals(odd, changes.get(1).newValue);
        assertNull(changes.get(1).oldValue);
    }

    @Test
    public void theTrailKeepsTheNewestChanges() throws IOException {
        for (int i = 0; i < SettingsAuditLog.MAX_ENTRIES + 5; ++i) {
            mEditor.set(SettingsTable.GLOBAL, "counter", String.valueOf(i));
        }

        List<SettingsChange> changes = mLog.readNewestFirst();

        assertEquals(SettingsAuditLog.MAX_ENTRIES, changes.size());
        assertEquals(String.valueOf(SettingsAuditLog.MAX_ENTRIES + 4), changes.get(0).newValue);
        assertEquals("5", changes.get(changes.size() - 1).newValue);
    }

    /** Keys as "table/key". */
    private static final class FakeBackend implements SettingsBackend {
        final Map<String, String> values = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean refuseWrites;

        @Override
        public boolean isPrivileged() {
            return true;
        }

        @Override
        public boolean canWrite(@NonNull SettingsTable table) {
            return true;
        }

        @NonNull
        @Override
        public List<SettingEntry> list(@NonNull SettingsTable table) {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public String get(@NonNull SettingsTable table, @NonNull String key) {
            calls.add("get " + table.shellName + "/" + key);
            return values.get(table.shellName + "/" + key);
        }

        @Override
        public void put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value) throws IOException {
            if (refuseWrites) throw new IOException("refused");
            calls.add("put " + table.shellName + "/" + key);
            values.put(table.shellName + "/" + key, value);
        }

        @Override
        public void delete(@NonNull SettingsTable table, @NonNull String key) throws IOException {
            if (refuseWrites) throw new IOException("refused");
            calls.add("delete " + table.shellName + "/" + key);
            values.remove(table.shellName + "/" + key);
        }
    }
}
