// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.appOp;
import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import android.app.Application;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class WatchRuleDaoTest {
    private static final String ONE = "com.example.one";
    private static final String TWO = "com.example.two";
    private static final String CAMERA = "android.permission.CAMERA";
    private static final String LOCATION = "android.permission.ACCESS_FINE_LOCATION";

    private Application mApp;
    private PermissionWatchDb mDb;
    private WatchRuleDao mDao;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mDb = WatchTestDb.newInMemory(mApp);
        mDao = mDb.ruleDao();
    }

    @After
    public void tearDown() {
        WatchTestDb.uninstall(mDb);
    }

    @NonNull
    private static List<String> keys(@NonNull List<WatchRule> rules) {
        List<String> keys = new ArrayList<>();
        for (WatchRule rule : rules) {
            keys.add(FakeWatchBackend.key(rule) + (rule.type == WatchRule.TYPE_APP_OP ? " (op)" : ""));
        }
        return keys;
    }

    @Test
    public void aSavedRuleComesBack() {
        WatchRule rule = permission(ONE, 0, CAMERA, WatchRule.STATE_DENY);

        mDao.upsert(rule);

        assertEquals(rule, mDao.find(ONE, 0, WatchRule.TYPE_PERMISSION, CAMERA));
        assertEquals(1, mDao.count());
    }

    @Test
    public void savingTheSameTargetAgainReplacesItsState() {
        mDao.upsert(permission(ONE, 0, CAMERA, WatchRule.STATE_DENY));

        WatchRule allow = new WatchRule(ONE, 0, WatchRule.TYPE_PERMISSION, CAMERA, -1, WatchRule.STATE_ALLOW, 2_000L);
        mDao.upsert(allow);

        assertEquals(1, mDao.count());
        assertEquals(allow, mDao.find(ONE, 0, WatchRule.TYPE_PERMISSION, CAMERA));
    }

    @Test
    public void typeAndUserKeepRulesApart() {
        mDao.upsertAll(Arrays.asList(
                permission(ONE, 0, CAMERA, WatchRule.STATE_DENY),
                permission(ONE, 10, CAMERA, WatchRule.STATE_ALLOW),
                appOp(ONE, 0, 26, CAMERA, WatchRule.STATE_IGNORE)));

        assertEquals(3, mDao.count());
        assertEquals(WatchRule.STATE_DENY, mDao.find(ONE, 0, WatchRule.TYPE_PERMISSION, CAMERA).state);
        assertEquals(WatchRule.STATE_ALLOW, mDao.find(ONE, 10, WatchRule.TYPE_PERMISSION, CAMERA).state);
        WatchRule op = mDao.find(ONE, 0, WatchRule.TYPE_APP_OP, CAMERA);
        assertEquals(WatchRule.STATE_IGNORE, op.state);
        assertEquals(26, op.op);
    }

    @Test
    public void oneAppsRulesAreOnlyThatAppAndUser() {
        mDao.upsertAll(Arrays.asList(
                permission(ONE, 0, LOCATION, WatchRule.STATE_DENY),
                permission(TWO, 0, CAMERA, WatchRule.STATE_DENY),
                permission(ONE, 10, CAMERA, WatchRule.STATE_DENY),
                appOp(ONE, 0, 26, "CAMERA", WatchRule.STATE_DENY),
                permission(ONE, 0, CAMERA, WatchRule.STATE_ALLOW)));

        // Permissions before app ops, each sorted by name.
        assertEquals(Arrays.asList(ONE + "/0 " + LOCATION, ONE + "/0 " + CAMERA, ONE + "/0 CAMERA (op)"),
                keys(mDao.getForPackage(ONE, 0)));
        assertTrue(mDao.getForPackage("com.example.none", 0).isEmpty());
    }

    @Test
    public void everyRuleComesBackInAStableOrder() {
        mDao.upsertAll(Arrays.asList(
                permission(TWO, 0, CAMERA, WatchRule.STATE_DENY),
                permission(ONE, 10, CAMERA, WatchRule.STATE_DENY),
                permission(ONE, 0, LOCATION, WatchRule.STATE_DENY),
                permission(ONE, 0, CAMERA, WatchRule.STATE_DENY)));

        assertEquals(Arrays.asList(ONE + "/0 " + LOCATION, ONE + "/0 " + CAMERA, ONE + "/10 " + CAMERA,
                TWO + "/0 " + CAMERA), keys(mDao.getAll()));
    }

    @Test
    public void removingMatchesTheTargetEvenAfterItsStateChanged() {
        mDao.upsert(permission(ONE, 0, CAMERA, WatchRule.STATE_DENY));
        mDao.upsert(permission(ONE, 0, LOCATION, WatchRule.STATE_DENY));

        int removed = mDao.delete(permission(ONE, 0, CAMERA, WatchRule.STATE_ALLOW));

        assertEquals(1, removed);
        assertNull(mDao.find(ONE, 0, WatchRule.TYPE_PERMISSION, CAMERA));
        assertEquals(1, mDao.count());
    }

    @Test
    public void ignoredPermissionsAreOnlyThatAppsIgnoredPermissionRules() {
        PermissionWatchDb.setInstanceForTesting(mDb);
        mDao.upsertAll(Arrays.asList(
                permission(ONE, 0, CAMERA, WatchRule.STATE_IGNORE),
                permission(ONE, 0, LOCATION, WatchRule.STATE_DENY),
                appOp(ONE, 0, 27, "RECORD_AUDIO", WatchRule.STATE_IGNORE),
                permission(ONE, 10, LOCATION, WatchRule.STATE_IGNORE),
                permission(TWO, 0, LOCATION, WatchRule.STATE_IGNORE)));

        assertEquals(new HashSet<>(Collections.singletonList(CAMERA)),
                PermissionWatchDb.ignoredPermissions(mApp, ONE, 0));
    }

    @Test
    public void noDatabaseMeansNoRulesWithoutCreatingOne() {
        PermissionWatchDb.setInstanceForTesting(null);

        assertFalse(PermissionWatchDb.mightHaveRules(mApp));
        assertTrue(PermissionWatchDb.ignoredPermissions(mApp, ONE, 0).isEmpty());
        assertFalse(mApp.getDatabasePath(PermissionWatchDb.NAME).exists());

        PermissionWatchDb.setInstanceForTesting(mDb);
        assertTrue(PermissionWatchDb.mightHaveRules(mApp));
    }
}
