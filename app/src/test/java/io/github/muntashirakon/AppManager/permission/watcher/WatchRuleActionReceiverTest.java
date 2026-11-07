// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class WatchRuleActionReceiverTest {
    private static final String PKG = "com.example.app";
    private static final String CAMERA = "android.permission.CAMERA";
    private static final String LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    private static final String TAG = "permission_change:" + PKG;

    private Application mApp;
    private PermissionWatchDb mDb;
    private RecordingWorkSink mSink;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mDb = WatchTestDb.install(mApp);
        mSink = RecordingWorkSink.install();
    }

    @After
    public void tearDown() {
        RecordingWorkSink.uninstall();
        WatchTestDb.uninstall(mDb);
    }

    @NonNull
    private static Intent intent(String packageName, int userId, String[] permissions, int state) {
        Intent intent = new Intent(WatchRuleActionReceiver.ACTION_SAVE_RULES)
                .putExtra(WatchRuleActionReceiver.EXTRA_USER, userId)
                .putExtra(WatchRuleActionReceiver.EXTRA_STATE, state);
        if (packageName != null) {
            intent.putExtra(WatchRuleActionReceiver.EXTRA_PACKAGE, packageName);
        }
        if (permissions != null) {
            intent.putExtra(WatchRuleActionReceiver.EXTRA_PERMISSIONS, permissions);
        }
        return intent;
    }

    @NonNull
    private static Intent savedIntent(@NonNull PendingIntent pendingIntent) {
        return shadowOf(pendingIntent).getSavedIntent();
    }

    @Test
    public void eachPermissionTheAlertNamedGetsARule() {
        List<WatchRule> rules = WatchRuleActionReceiver.rulesFrom(intent(PKG, 10,
                new String[]{CAMERA, "", null, LOCATION}, WatchRule.STATE_DENY));

        assertEquals(2, rules.size());
        for (WatchRule rule : rules) {
            assertEquals(PKG, rule.packageName);
            assertEquals(10, rule.userId);
            assertEquals(WatchRule.TYPE_PERMISSION, rule.type);
            assertEquals(WatchRule.STATE_DENY, rule.state);
        }
        assertEquals(CAMERA, rules.get(0).name);
        assertEquals(LOCATION, rules.get(1).name);
    }

    @Test
    public void aMalformedIntentMakesNoRules() {
        String[] perms = {CAMERA};
        assertTrue(WatchRuleActionReceiver.rulesFrom(intent(null, 0, perms, WatchRule.STATE_DENY)).isEmpty());
        assertTrue(WatchRuleActionReceiver.rulesFrom(intent("", 0, perms, WatchRule.STATE_DENY)).isEmpty());
        assertTrue(WatchRuleActionReceiver.rulesFrom(intent(PKG, 0, null, WatchRule.STATE_DENY)).isEmpty());
        assertTrue(WatchRuleActionReceiver.rulesFrom(intent(PKG, 0, perms, 7)).isEmpty());
        assertTrue(WatchRuleActionReceiver.rulesFrom(new Intent(WatchRuleActionReceiver.ACTION_SAVE_RULES)
                .putExtra(WatchRuleActionReceiver.EXTRA_PACKAGE, PKG)
                .putExtra(WatchRuleActionReceiver.EXTRA_PERMISSIONS, perms)).isEmpty());
    }

    @Test
    public void eachButtonKeepsItsOwnRule() {
        PendingIntent deny = WatchRuleActionReceiver.buildPendingIntent(mApp, PKG, 10, Arrays.asList(CAMERA, LOCATION),
                WatchRule.STATE_DENY, TAG);
        PendingIntent allow = WatchRuleActionReceiver.buildPendingIntent(mApp, PKG, 10, Arrays.asList(CAMERA, LOCATION),
                WatchRule.STATE_ALLOW, TAG);

        Intent denyIntent = savedIntent(deny);
        assertEquals(WatchRuleActionReceiver.class.getName(), denyIntent.getComponent().getClassName());
        assertEquals(WatchRuleActionReceiver.ACTION_SAVE_RULES, denyIntent.getAction());
        assertEquals(PKG, denyIntent.getStringExtra(WatchRuleActionReceiver.EXTRA_PACKAGE));
        assertEquals(10, denyIntent.getIntExtra(WatchRuleActionReceiver.EXTRA_USER, -1));
        assertArrayEquals(new String[]{CAMERA, LOCATION},
                denyIntent.getStringArrayExtra(WatchRuleActionReceiver.EXTRA_PERMISSIONS));
        assertEquals(TAG, denyIntent.getStringExtra(WatchRuleActionReceiver.EXTRA_NOTIFICATION_TAG));
        // The second button mustn't overwrite the first one's state.
        assertEquals(WatchRule.STATE_DENY, denyIntent.getIntExtra(WatchRuleActionReceiver.EXTRA_STATE, -1));
        assertEquals(WatchRule.STATE_ALLOW, savedIntent(allow).getIntExtra(WatchRuleActionReceiver.EXTRA_STATE, -1));
    }

    @Test
    public void tappingAButtonSavesTheRulesDismissesTheAlertAndChecksTheApp() {
        NotificationManager manager = (NotificationManager) mApp.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(TAG, 1, new NotificationCompat.Builder(mApp, "test")
                .setSmallIcon(R.drawable.ic_security).build());
        PendingIntent deny = WatchRuleActionReceiver.buildPendingIntent(mApp, PKG, 10, Arrays.asList(CAMERA, LOCATION),
                WatchRule.STATE_DENY, TAG);

        assertTrue(WatchRuleActionReceiver.handle(mApp, savedIntent(deny)));

        WatchRuleDao dao = mDb.ruleDao();
        assertEquals(2, dao.count());
        assertEquals(WatchRule.STATE_DENY, dao.find(PKG, 10, WatchRule.TYPE_PERMISSION, CAMERA).state);
        assertEquals(WatchRule.STATE_DENY, dao.find(PKG, 10, WatchRule.TYPE_PERMISSION, LOCATION).state);
        assertTrue(shadowOf(manager).getAllNotifications().isEmpty());
        assertEquals(Collections.singletonList(PermissionWatchScheduler.uniqueCheckName(PKG, 10)),
                mSink.enqueuedNames);
    }

    @Test
    public void aMalformedTapSavesAndSchedulesNothing() {
        assertFalse(WatchRuleActionReceiver.handle(mApp, intent(PKG, 0, new String[0], WatchRule.STATE_DENY)));

        assertEquals(0, mDb.ruleDao().count());
        assertTrue(mSink.enqueuedNames.isEmpty());
    }
}
