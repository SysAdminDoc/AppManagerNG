// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.NotificationUtils;

@RunWith(RobolectricTestRunner.class)
public class AMExceptionHandlerTest {
    @Test
    public void formatCrashReportForShareScrubsPrivateTextAndSanitizesLines() {
        String report = "=cmd\tpayload\n"
                + "content://com.example.secret/private.apk /storage/emulated/0/private.apk "
                + "userId=10345 person@example.com";

        String shared = AMExceptionHandler.formatCrashReportForShare(report);

        assertTrue(shared.startsWith("'=cmd payload\n"));
        assertTrue(shared.contains("userId=<redacted>"));
        assertTrue(shared.contains("<email>"));
        assertFalse(shared.contains("\t"));
        assertFalse(shared.contains("com.example.secret"));
        assertFalse(shared.contains("private.apk"));
        assertFalse(shared.contains("10345"));
        assertFalse(shared.contains("person@example.com"));
    }

    @Test
    public void buildCrashShareIntentAttachesScrubbedReportAndCrashUriGrant() {
        Uri crashUri = Uri.parse("content://io.github.sysadmindoc.AppManagerNG.filecache/crashes/report.json");

        Intent intent = AMExceptionHandler.buildCrashShareIntent(
                "=cmd\tpayload\n/storage/emulated/0/private.apk", crashUri, 123L);

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals("AppManager NG: Crash Report", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT).startsWith("'=cmd payload"));
        assertFalse(intent.getStringExtra(Intent.EXTRA_TEXT).contains("private.apk"));
        assertEquals(crashUri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(crashUri, intent.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void buildCrashShareIntentWithoutCrashUriHasNoStreamGrant() {
        Intent intent = AMExceptionHandler.buildCrashShareIntent("plain", null, 123L);

        assertNull(intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertFalse((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNull(intent.getClipData());
    }

    @Test
    public void aThrowingDeviceInfoStillPostsTheStackTraceAndHandsOnOnce() {
        Recorder handler = new Recorder();
        handler.deviceInfoError = new IllegalStateException("no package manager");

        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        assertEquals(Arrays.asList("sink", "notify", "pause:250", "default:boom"), handler.events);
        assertTrue(handler.report.startsWith("java.lang.RuntimeException: boom\n"));
        assertTrue(handler.report.contains("Device Info:\nUnavailable (java.lang.IllegalStateException)"));
    }

    @Test
    public void aThrowingCrashSinkStillPostsTheNotificationAndHandsOnOnce() {
        Recorder handler = new Recorder();
        handler.sinkError = new IllegalStateException("disk full");

        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        assertEquals(Arrays.asList("sink", "notify", "pause:250", "default:boom"), handler.events);
    }

    @Test
    public void aThrowingNotificationManagerStillHandsOnOnceWithoutWaiting() {
        Recorder handler = new Recorder();
        handler.notifyError = new SecurityException("notification manager died");

        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        assertEquals(Arrays.asList("sink", "notify", "default:boom"), handler.events);
    }

    @Test
    public void theNotificationGoesOnTheCrashChannelBeforeTheDefaultHandlerRuns() {
        Context context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Recorder handler = new Recorder();
        handler.postForReal = true;

        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        List<Notification> posted = Shadows.shadowOf((NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE)).getAllNotifications();
        assertEquals(1, posted.size());
        assertEquals(NotificationUtils.CRASH_CHANNEL_ID, posted.get(0).getChannelId());
        assertEquals(Arrays.asList("sink", "notify", "pause:250", "default:boom"), handler.events);
        assertTrue(AMExceptionHandler.NOTIFICATION_GRACE_MILLIS <= 250);
    }

    @Test
    public void nothingPostedMeansNoWait() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Recorder handler = new Recorder();
        handler.postForReal = true;

        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        assertEquals(Arrays.asList("sink", "notify", "default:boom"), handler.events);
    }

    @Test
    public void noDefaultHandlerIsNotACrashOfItsOwn() {
        List<String> events = new ArrayList<>();
        AMExceptionHandler handler = new AMExceptionHandler(RuntimeEnvironment.getApplication(), null) {
            @Override
            boolean displayNotification(@NonNull Notification notification) {
                events.add("notify");
                return false;
            }
        };
        // Would throw a NullPointerException before the fix
        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        assertEquals(Collections.singletonList("notify"), events);
    }

    /**
     * Records each step of the crash path. The default handler is the last thing that may run,
     * and it must run once whatever fails before it.
     */
    private static final class Recorder extends AMExceptionHandler {
        final List<String> events;
        @Nullable
        RuntimeException deviceInfoError;
        @Nullable
        RuntimeException sinkError;
        @Nullable
        RuntimeException notifyError;
        boolean postForReal;
        String report;

        Recorder() {
            this(new ArrayList<>());
        }

        private Recorder(List<String> events) {
            super(RuntimeEnvironment.getApplication(), (t, e) -> events.add("default:" + e.getMessage()));
            this.events = events;
        }

        @NonNull
        @Override
        String describeDevice() {
            if (deviceInfoError != null) throw deviceInfoError;
            return "test device\n";
        }

        @Nullable
        @Override
        Uri writeCrashSink(@NonNull Thread t, @NonNull Throwable e, @NonNull String report) {
            events.add("sink");
            this.report = report;
            if (sinkError != null) throw sinkError;
            return null;
        }

        @Override
        boolean displayNotification(@NonNull Notification notification) {
            events.add("notify");
            if (notifyError != null) throw notifyError;
            return !postForReal || super.displayNotification(notification);
        }

        @Override
        void pause(long millis) {
            events.add("pause:" + millis);
        }
    }
}
