// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class TopAppOverlayServiceTest {
    private static final String APP = "com.example.app";
    private static final String AD = "com.example.ad";

    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private ServiceController<TopAppOverlayService> mController;
    @Rule
    public final TemporaryFolder mFolder = new TemporaryFolder();
    private TopAppHistory mHistory;

    @Before
    public void setUp() {
        ShadowSettings.setCanDrawOverlays(true);
        mHistory = new TopAppHistory(new File(mFolder.getRoot(), TopAppHistory.FILE_NAME), Runnable::run);
        TopAppHistory.setInstanceForTesting(mHistory);
        mController = Robolectric.buildService(TopAppOverlayService.class);
    }

    @After
    public void tearDown() {
        mController.destroy();
        TopAppOverlayService.setRunningForTesting(false);
        mMultiplexer.setTopAppListener(null);
        mMultiplexer.setServiceConnected(false);
        ShadowSettings.setCanDrawOverlays(false);
        Prefs.Privacy.setTopAppHistoryEnabled(false);
        TopAppHistory.setInstanceForTesting(null);
    }

    @NonNull
    private List<TopAppTransition> savedHistory() {
        AtomicReference<List<TopAppTransition>> read = new AtomicReference<>();
        mHistory.read(read::set);
        return read.get();
    }

    private static void walk(@NonNull TopAppOverlayService service, @NonNull TopAppTracker.Window... windows) {
        service.onWindowsChanged(Arrays.asList(windows));
        ShadowLooper.idleMainLooper();
    }

    private TopAppOverlayService startService() {
        return mController.create().startCommand(0, 1).get();
    }

    @Test
    public void startingGoesForegroundWithAStopAction() {
        TopAppOverlayService service = startService();

        assertTrue(TopAppOverlayService.isRunning());
        Notification notification = shadowOf(service).getLastForegroundNotification();
        assertNotNull(notification);
        assertEquals(TopAppOverlayService.CHANNEL_ID, notification.getChannelId());
        assertEquals(service.getString(R.string.top_app_overlay_stop), notification.actions[0].title);
        assertSame(service, mMultiplexer.getTopAppListener());
        assertFalse(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void theStopActionStopsTheService() {
        TopAppOverlayService service = startService();

        service.onStartCommand(new Intent(service, TopAppOverlayService.class)
                .setAction(TopAppOverlayService.ACTION_STOP), 0, 2);

        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void theStopActionIsAPendingIntentForThisService() {
        TopAppOverlayService service = startService();

        Notification notification = shadowOf(service).getLastForegroundNotification();
        Intent saved = shadowOf(notification.actions[0].actionIntent).getSavedIntent();

        assertEquals(TopAppOverlayService.ACTION_STOP, saved.getAction());
        assertEquals(new ComponentName(service, TopAppOverlayService.class), saved.getComponent());
    }

    @Test
    public void aSecondStartKeepsTheSameBubble() {
        TopAppOverlayService service = startService();

        service.onStartCommand(new Intent(service, TopAppOverlayService.class), 0, 2);

        assertFalse(shadowOf(service).isStoppedBySelf());
        assertTrue(TopAppOverlayService.isRunning());
    }

    @Test
    public void destroyingClearsTheRunningStateAndTheSource() {
        startService();

        mController.destroy();

        assertFalse(TopAppOverlayService.isRunning());
        assertNull(mMultiplexer.getTopAppListener());
    }

    @Test
    public void aFailedForegroundStartTellsTheUserAndStops() {
        TopAppOverlayService service = mController.get();
        shadowOf(service).setThrowInStartForeground(new IllegalStateException("refused"));

        mController.create().startCommand(0, 1);

        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(service.getString(R.string.top_app_overlay_failed), ShadowToast.getTextOfLatestToast());
        assertNull(mMultiplexer.getTopAppListener());
    }

    @Test
    public void losingTheOverlayPermissionStopsTheBubbleAndSaysWhy() {
        TopAppOverlayService service = startService();
        ShadowLooper.idleMainLooper();
        assertFalse(shadowOf(service).isStoppedBySelf());

        ShadowSettings.setCanDrawOverlays(false);
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);

        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(service.getString(R.string.top_app_overlay_needs_overlay), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void accessibilityEventsNameTheTopApp() {
        TopAppOverlayService service = startService();

        service.onWindowStateChanged(AD);

        assertEquals(AD, service.getTracker().getCurrent());
    }

    @Test
    public void windowWalksFromTheWorkerAreAppliedOnTheMainThread() {
        TopAppOverlayService service = startService();
        service.onWindowsChanged(Collections.singletonList(new TopAppTracker.Window(APP, true)));
        ShadowLooper.idleMainLooper();
        assertEquals(APP, service.getTracker().getCurrent());

        service.onWindowsChanged(Arrays.asList(new TopAppTracker.Window(AD, false),
                new TopAppTracker.Window(APP, true)));
        ShadowLooper.idleMainLooper();

        assertEquals(AD, service.getTracker().getCurrent());
    }

    @Test
    public void historyIsOnlySavedWhileRecordingIsOn() {
        TopAppOverlayService service = startService();

        walk(service, new TopAppTracker.Window(APP, true));

        assertTrue(savedHistory().isEmpty());
    }

    @Test
    public void historyNamesTheOverlayAndTheAppUnderIt() {
        Prefs.Privacy.setTopAppHistoryEnabled(true);
        TopAppOverlayService service = startService();

        walk(service, new TopAppTracker.Window(APP, true));
        walk(service, new TopAppTracker.Window(AD, false), new TopAppTracker.Window(APP, true));
        walk(service, new TopAppTracker.Window(APP, true));

        List<TopAppTransition> saved = savedHistory();
        assertEquals(3, saved.size());
        assertEquals(TopAppTransition.Kind.OVERLAY_GONE, saved.get(0).kind);
        assertEquals(AD, saved.get(0).packageName);
        assertEquals(APP, saved.get(0).other);
        assertEquals(TopAppTransition.Kind.OVERLAY_SHOWN, saved.get(1).kind);
        assertEquals(AD, saved.get(1).packageName);
        assertEquals(APP, saved.get(1).other);
        assertEquals(TopAppTransition.Kind.APP, saved.get(2).kind);
        assertEquals(APP, saved.get(2).packageName);
        assertNull(saved.get(2).other);
    }

    @Test
    public void turningRecordingOnLaterDoesNotReplayTheAppAlreadyUp() {
        TopAppOverlayService service = startService();
        walk(service, new TopAppTracker.Window(APP, true));

        Prefs.Privacy.setTopAppHistoryEnabled(true);
        walk(service, new TopAppTracker.Window(AD, false), new TopAppTracker.Window(APP, true));

        List<TopAppTransition> saved = savedHistory();
        assertEquals(1, saved.size());
        assertEquals(TopAppTransition.Kind.OVERLAY_SHOWN, saved.get(0).kind);
    }

    @Test
    public void aWalkLandingAfterDestroyIsDropped() {
        TopAppOverlayService service = startService();
        service.onWindowsChanged(Collections.singletonList(new TopAppTracker.Window(APP, true)));

        mController.destroy();
        ShadowLooper.idleMainLooper();

        assertNull(service.getTracker().getCurrent());
    }

    @Test
    public void usagePollsKeepEveryResumedAppSoAFlashedAdStaysInReach() {
        TopAppOverlayService service = startService();

        service.onUsagePolled(true, Arrays.asList(APP, AD, "com.example.after"));

        assertEquals("com.example.after", service.getTracker().getCurrent());
        assertEquals(Arrays.asList("com.example.after", AD, APP), service.getTracker().getRecents());
    }

    @Test
    public void losingEverySourceTellsTheUserOnce() {
        TopAppOverlayService service = startService();
        int before = ShadowToast.shownToastCount();

        service.onUsagePolled(false, Collections.emptyList());
        service.onUsagePolled(false, Collections.emptyList());

        assertEquals(before + 1, ShadowToast.shownToastCount());
        assertEquals(service.getString(R.string.top_app_overlay_no_source), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void theLauncherAndKeyboardAreNotWhatIsBeingHunted() {
        Context context = ApplicationProvider.getApplicationContext();
        ResolveInfo home = new ResolveInfo();
        home.activityInfo = new ActivityInfo();
        home.activityInfo.packageName = "com.example.launcher";
        home.activityInfo.name = "Home";
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), home);
        Settings.Secure.putString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD,
                "com.example.keyboard/.Ime");

        Set<String> ignored = TopAppOverlayService.resolveIgnoredPackages(context);

        assertTrue(ignored.contains(context.getPackageName()));
        assertTrue(ignored.contains("com.android.systemui"));
        assertTrue(ignored.contains("com.example.launcher"));
        assertTrue(ignored.contains("com.example.keyboard"));
        assertFalse(ignored.contains(AD));
    }

    @Test
    public void startingIsRoutedThroughAForegroundServiceStart() {
        Application app = ApplicationProvider.getApplicationContext();

        TopAppOverlayService.start(app);

        Intent started = shadowOf(app).getNextStartedService();
        assertEquals(new ComponentName(app, TopAppOverlayService.class), started.getComponent());
    }

    @Test
    public void theBubbleIsASpecialUseServiceThatIsNotExported() throws IOException {
        String manifest = read("app/src/main/AndroidManifest.xml");
        int service = manifest.indexOf("android:name=\".accessibility.topapp.TopAppOverlayService\"");
        int end = manifest.indexOf("</service>", service);
        assertTrue(service != -1 && end > service);
        String entry = manifest.substring(service, end);

        assertTrue(entry.contains("android:foregroundServiceType=\"specialUse\""));
        assertTrue(entry.contains("android:exported=\"false\""));
        assertTrue(entry.contains("android:name=\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\""));
    }

    private static String read(String path) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(Files.readAllBytes(cursor.resolve(path)), StandardCharsets.UTF_8);
    }
}
