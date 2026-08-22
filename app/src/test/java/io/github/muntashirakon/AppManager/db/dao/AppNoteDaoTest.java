// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.AppNote;

@RunWith(RobolectricTestRunner.class)
public class AppNoteDaoTest {
    private AppsDb mDb;
    private AppNoteDao mDao;

    @Before
    public void setUp() {
        mDb = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppsDb.class)
                .allowMainThreadQueries()
                .build();
        mDao = mDb.appNoteDao();
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    @Test
    public void insertReplacesByPackage_andReturnsSortedRows() {
        insert("com.z", "Zed", 1);
        insert("com.a", "Alpha", 2);
        insert("com.z", "Updated", 3);

        List<AppNote> notes = mDao.getAll();

        assertEquals(2, notes.size());
        assertEquals("com.a", notes.get(0).packageName);
        assertEquals("com.z", notes.get(1).packageName);
        assertEquals("Updated", notes.get(1).note);
        assertEquals(3, notes.get(1).updatedAt);
    }

    @Test
    public void deleteRemovesOnlyTheRequestedPackage() {
        insert("com.keep", "Keep", 1);
        insert("com.remove", "Remove", 2);

        mDao.delete("com.remove");

        assertNull(mDao.get("com.remove"));
        assertEquals("Keep", mDao.get("com.keep").note);
    }

    private void insert(String packageName, String noteText, long updatedAt) {
        AppNote note = new AppNote();
        note.packageName = packageName;
        note.note = noteText;
        note.updatedAt = updatedAt;
        mDao.insert(note);
    }
}
