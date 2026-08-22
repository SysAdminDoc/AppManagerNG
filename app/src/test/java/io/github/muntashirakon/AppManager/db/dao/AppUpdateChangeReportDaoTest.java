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
import io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport;

@RunWith(RobolectricTestRunner.class)
public class AppUpdateChangeReportDaoTest {
    private AppsDb mDb;
    private AppUpdateChangeReportDao mDao;

    @Before
    public void setUp() {
        mDb = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppsDb.class)
                .allowMainThreadQueries()
                .build();
        mDao = mDb.appUpdateChangeReportDao();
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    @Test
    public void recentReportsAreOrderedNewestFirstAndLimited() {
        insert("com.old", 100L);
        insert("com.new", 200L);
        insert("com.latest", 300L);

        List<AppUpdateChangeReport> reports = mDao.getRecent(2);

        assertEquals(2, reports.size());
        assertEquals("com.latest", reports.get(0).packageName);
        assertEquals("com.new", reports.get(1).packageName);
    }

    @Test
    public void deleteOlderThanKeepsTheCutoff() {
        insert("com.old", 100L);
        insert("com.keep", 200L);

        assertEquals(1, mDao.deleteOlderThan(200L));
        assertEquals(1, mDao.getRecent(10).size());
        assertEquals("com.keep", mDao.getRecent(10).get(0).packageName);
    }

    private void insert(String packageName, long timestamp) {
        AppUpdateChangeReport report = new AppUpdateChangeReport();
        report.packageName = packageName;
        report.timestampMillis = timestamp;
        report.beforeVersionCode = 1;
        report.afterVersionCode = 2;
        report.addedPermissions = "[\"android.permission.CAMERA\"]";
        mDao.insert(report);
    }
}
