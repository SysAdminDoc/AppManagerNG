// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import static org.junit.Assert.assertEquals;

import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;

@RunWith(RobolectricTestRunner.class)
public class OpHistoryDaoTest {
    private AppsDb mDb;
    private OpHistoryDao mDao;

    @Before
    public void setUp() {
        mDb = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppsDb.class)
                .allowMainThreadQueries()
                .build();
        mDao = mDb.opHistoryDao();
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    @Test
    public void deleteByStatusOtherThanRemovesStatusesDisplayedAsFailure() {
        insert(1L, OpHistoryManager.STATUS_SUCCESS);
        insert(2L, OpHistoryManager.STATUS_FAILURE);
        insert(3L, "future_status");

        assertEquals(2, mDao.deleteByStatusOtherThan(OpHistoryManager.STATUS_SUCCESS));

        List<OpHistory> rows = mDao.getAll();
        assertEquals(1, rows.size());
        assertEquals(OpHistoryManager.STATUS_SUCCESS, rows.get(0).status);
    }

    @Test
    public void deleteByStatusKeepsExactSuccessCleanupNarrow() {
        insert(1L, OpHistoryManager.STATUS_SUCCESS);
        insert(2L, OpHistoryManager.STATUS_FAILURE);
        insert(3L, "future_status");

        assertEquals(1, mDao.deleteByStatus(OpHistoryManager.STATUS_SUCCESS));

        List<OpHistory> rows = mDao.getAll();
        assertEquals(2, rows.size());
    }

    private void insert(long id, String status) {
        OpHistory row = new OpHistory();
        row.id = id;
        row.type = OpHistoryManager.HISTORY_TYPE_BATCH_OPS;
        row.execTime = 1_700_000_000_000L + id;
        row.status = status;
        row.serializedData = "{}";
        row.serializedExtra = null;
        mDao.insert(row);
    }
}
