// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNotificationManager;

/**
 * The confirmation notification is posted with {@code setAutoCancel(false)}, so nothing retires it
 * on its own. Fork issue #14 reported it stuck at full progress with no system prompt behind it:
 * the payload had been refused and the only two branches that cancelled the notification were the
 * ones that never ran.
 */
@RunWith(RobolectricTestRunner.class)
public class PackageInstallerConfirmNotificationTest {
    private static final String INSTALLER = "com.android.packageinstaller";

    private Context mContext;
    private ShadowNotificationManager mNotifications;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        // NotificationUtils drops the notify() call outright without this, so without the grant
        // every assertion below would pass against a manager that was never asked to post.
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mNotifications = Shadows.shadowOf(
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE));
    }

    @NonNull
    private static Intent pendingUserAction(@Nullable Intent payload, int sessionId) {
        Intent intent = new Intent(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        if (payload != null) {
            intent.putExtra(Intent.EXTRA_INTENT, payload);
        }
        return intent;
    }

    @NonNull
    private static Intent status(int status, int sessionId) {
        return new Intent(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER)
                .putExtra(PackageInstaller.EXTRA_STATUS, status)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
    }

    /** The AOSP install confirmation: implicit action, explicitly packaged. */
    @NonNull
    private Intent installConfirmationResolvingToSystem() {
        Intent payload = new Intent("android.content.pm.action.CONFIRM_INSTALL")
                .setPackage(INSTALLER);
        registerSystemActivity(payload);
        return payload;
    }

    private void registerSystemActivity(@NonNull Intent query) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = INSTALLER;
        applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = INSTALLER;
        activityInfo.name = INSTALLER + ".InstallStart";
        activityInfo.applicationInfo = applicationInfo;
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        PackageManager packageManager = mContext.getPackageManager();
        Shadows.shadowOf(packageManager).addResolveInfoForIntent(query, resolveInfo);
    }

    @NonNull
    private PackageInstallerBroadcastReceiver receiverWithPostedConfirmation() {
        PackageInstallerBroadcastReceiver receiver = new PackageInstallerBroadcastReceiver();
        receiver.setPackageName("com.example.app");
        receiver.setAppLabel("Example");
        receiver.onReceive(mContext, pendingUserAction(installConfirmationResolvingToSystem(), 7));
        assertEquals("a confirmation notification should have been posted",
                1, mNotifications.size());
        return receiver;
    }

    @Test
    public void successRetiresTheConfirmationNotification() {
        PackageInstallerBroadcastReceiver receiver = receiverWithPostedConfirmation();

        receiver.onReceive(mContext, status(PackageInstaller.STATUS_SUCCESS, 7));

        assertEquals(0, mNotifications.size());
    }

    @Test
    public void failureRetiresTheConfirmationNotification() {
        PackageInstallerBroadcastReceiver receiver = receiverWithPostedConfirmation();

        receiver.onReceive(mContext, status(PackageInstaller.STATUS_FAILURE_CONFLICT, 7));

        assertEquals(0, mNotifications.size());
    }

    /**
     * The path that ends an abandoned session: an interaction or result timeout in
     * {@link PackageInstallerCompat} reaches no receiver branch at all, so it retires the
     * notification through this call instead.
     */
    @Test
    public void anAbandonedSessionRetiresTheConfirmationNotification() {
        PackageInstallerBroadcastReceiver receiver = receiverWithPostedConfirmation();

        receiver.clearConfirmNotification(mContext);

        assertEquals(0, mNotifications.size());
    }

    /**
     * Every terminal path converges on the same call, so it runs more than once for one session:
     * a receiver branch retires the notification and the {@code finally} in
     * {@link PackageInstallerCompat} calls again straight after. Only the first may act, or the
     * receiver is holding an id it no longer owns.
     */
    @Test
    public void theConfirmationIsRetiredByExactlyOneCall() {
        PackageInstallerBroadcastReceiver receiver = receiverWithPostedConfirmation();

        assertTrue("the first retirement should be the one that acts",
                receiver.clearConfirmNotification(mContext));
        assertFalse("a second retirement must be a no-op",
                receiver.clearConfirmNotification(mContext));
        assertFalse(receiver.clearConfirmNotification(mContext));
        assertEquals(0, mNotifications.size());
    }

    @Test
    public void aReceiverBranchRetiresItSoTheSessionTeardownHasNothingLeftToDo() {
        PackageInstallerBroadcastReceiver receiver = receiverWithPostedConfirmation();

        receiver.onReceive(mContext, status(PackageInstaller.STATUS_SUCCESS, 7));

        assertEquals(0, mNotifications.size());
        assertFalse("the success branch already retired it",
                receiver.clearConfirmNotification(mContext));
    }

    @Test
    public void retiringWithoutAPostedConfirmationIsANoOp() {
        PackageInstallerBroadcastReceiver receiver = new PackageInstallerBroadcastReceiver();

        receiver.clearConfirmNotification(mContext);

        assertEquals(0, mNotifications.size());
    }

    /**
     * A refused payload has no prompt behind it, so posting a notification whose only action can
     * fail would strand the user. The session has to end instead.
     */
    @Test
    public void aRefusedConfirmationPostsNoNotificationAndEndsTheSession() {
        PackageInstallerBroadcastReceiver receiver = new PackageInstallerBroadcastReceiver();
        receiver.setPackageName("com.example.app");
        receiver.setAppLabel("Example");
        Intent hostile = new Intent(Intent.ACTION_VIEW)
                .setComponent(new ComponentName(mContext.getPackageName(), "Anything"));

        receiver.onReceive(mContext, pendingUserAction(hostile, 7));

        assertEquals(0, mNotifications.size());
    }

    /**
     * The guard used to reject this outright, which is what took the system prompt away. It has
     * to survive the receiver and reach the activity bound to the resolved component.
     */
    @Test
    public void theImplicitUninstallConfirmationSurvivesTheReceiver() {
        Intent payload = new Intent(Intent.ACTION_UNINSTALL_PACKAGE)
                .setData(Uri.fromParts("package", "com.example.app", null));
        registerSystemActivity(payload);
        PackageInstallerBroadcastReceiver receiver = new PackageInstallerBroadcastReceiver();
        receiver.setPackageName("com.example.app");
        receiver.setAppLabel("Example");

        receiver.onReceive(mContext, pendingUserAction(payload, -1));

        assertTrue("the uninstall confirmation should have been forwarded, not refused",
                mNotifications.size() > 0);
    }
}
