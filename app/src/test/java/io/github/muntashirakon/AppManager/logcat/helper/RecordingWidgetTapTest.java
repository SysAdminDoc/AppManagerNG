// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import io.github.muntashirakon.AppManager.logcat.RecordLogDialogActivity;
import io.github.muntashirakon.AppManager.logcat.RecordingWidgetActionReceiver;
import io.github.muntashirakon.AppManager.logcat.RecordingWidgetProvider;

/**
 * The recording widget's provider is exported, as every widget provider is for the launcher's
 * broadcasts, and it also started and stopped recording on a custom action. Any installed app could
 * send that action and stop a recording. Taps now go to a receiver that isn't exported.
 */
@RunWith(RobolectricTestRunner.class)
public class RecordingWidgetTapTest {
    private final Application mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void theWidgetTapGoesToAReceiverOtherAppsCantReach() throws Exception {
        PendingIntent tap = WidgetHelper.getPendingIntent(mContext, 7);
        Intent intent = shadowOf(tap).getSavedIntent();

        assertTrue(shadowOf(tap).isBroadcast());
        assertEquals(new ComponentName(mContext, RecordingWidgetActionReceiver.class), intent.getComponent());
        assertEquals(RecordingWidgetActionReceiver.ACTION_RECORD_OR_STOP, intent.getAction());
        // One PendingIntent per widget
        assertEquals(7, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
        assertNotNull(intent.getData());

        ActivityInfo receiver = mContext.getPackageManager().getReceiverInfo(intent.getComponent(), 0);
        assertFalse(receiver.exported);
    }

    @Test
    public void theExportedProviderNoLongerAnswersTheAction() {
        Intent action = new Intent(RecordingWidgetActionReceiver.ACTION_RECORD_OR_STOP)
                .setData(Uri.parse(RecordingWidgetProvider.URI_SCHEME + "://widget/id/#7"))
                .setPackage(mContext.getPackageName());
        List<ResolveInfo> receivers = mContext.getPackageManager()
                .queryBroadcastReceivers(action, PackageManager.MATCH_ALL);
        for (ResolveInfo info : receivers) {
            // Not the provider, and nothing else another app could send it to either
            assertFalse(info.activityInfo.name + " answers the action and is exported", info.activityInfo.exported);
        }

        // Sent to it by name anyway, as another app could, it does nothing
        new RecordingWidgetProvider().onReceive(mContext, new Intent(action)
                .setComponent(new ComponentName(mContext, RecordingWidgetProvider.class)));
        assertNull(shadowOf(mContext).getNextStartedActivity());
    }

    @Test
    public void aTapWithNothingRecordingOpensTheRecordDialog() {
        new RecordingWidgetActionReceiver().onReceive(mContext,
                new Intent(RecordingWidgetActionReceiver.ACTION_RECORD_OR_STOP));

        Intent started = shadowOf(mContext).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RecordLogDialogActivity.class.getName(), started.getComponent().getClassName());
    }
}
