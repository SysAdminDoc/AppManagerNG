// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w360dp-h640dp-port-mdpi")
public class TopAppBubbleTest {
    private static final String APP = "com.example.app";
    private static final String AD = "com.example.ad";

    private final List<String> mOpened = new ArrayList<>();
    private TopAppBubble mBubble;

    @Before
    public void setUp() {
        mBubble = new TopAppBubble(ApplicationProvider.getApplicationContext(), mOpened::add);
        assertTrue(mBubble.show());
        ShadowLooper.idleMainLooper();
    }

    @After
    public void tearDown() {
        mBubble.dismiss();
    }

    private static void touch(@NonNull View view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private void tap() {
        tap(mBubble.getPill());
    }

    private static void tap(@NonNull View row) {
        touch(row, MotionEvent.ACTION_DOWN, 10, 10);
        touch(row, MotionEvent.ACTION_UP, 10, 10);
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void anOverlayGetsItsOwnRowAboveTheAppUnderneath() {
        mBubble.setLayers(APP, AD, true, Arrays.asList(AD, APP));

        assertEquals(View.VISIBLE, mBubble.getOverlayRow().getVisibility());
        assertEquals(1f, mBubble.getOverlayRow().getAlpha(), 0f);
        assertEquals(View.VISIBLE, mBubble.getUnderlyingLayer().getVisibility());
        assertEquals(View.VISIBLE, mBubble.getPill().getVisibility());
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(context.getString(R.string.top_app_overlay_layer_overlay), mBubble.getOverlayLayer().getText());
        // Each row opens its own app.
        tap(mBubble.getOverlayRow());
        tap(mBubble.getPill());
        assertEquals(Arrays.asList(AD, APP), mOpened);
    }

    @Test
    public void aGoneOverlayFadesAndSaysSo() {
        mBubble.setLayers(APP, AD, false, Arrays.asList(AD, APP));

        assertEquals(View.VISIBLE, mBubble.getOverlayRow().getVisibility());
        assertEquals(TopAppBubble.GONE_OVERLAY_ALPHA, mBubble.getOverlayRow().getAlpha(), 0f);
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(context.getString(R.string.top_app_overlay_layer_overlay_gone),
                mBubble.getOverlayLayer().getText());
    }

    @Test
    public void withoutAnOverlayOnlyTheAppRowShows() {
        mBubble.setLayers(APP, AD, true, Arrays.asList(AD, APP));

        mBubble.setLayers(APP, null, false, Collections.singletonList(APP));

        assertEquals(View.GONE, mBubble.getOverlayRow().getVisibility());
        assertEquals(View.GONE, mBubble.getUnderlyingLayer().getVisibility());
        assertEquals(View.VISIBLE, mBubble.getPill().getVisibility());
    }

    @Test
    public void overTheHomeScreenOnlyTheOverlayRowShows() {
        mBubble.setLayers(null, AD, true, Collections.singletonList(AD));

        assertEquals(View.VISIBLE, mBubble.getOverlayRow().getVisibility());
        assertEquals(View.GONE, mBubble.getPill().getVisibility());
    }

    @Test
    public void aDragOnTheOverlayRowMovesTheBubbleWithoutOpeningAnything() {
        mBubble.setLayers(APP, AD, true, Arrays.asList(AD, APP));
        ShadowLooper.idleMainLooper();
        WindowManager.LayoutParams params = mBubble.getLayoutParams();
        int startY = params.y;
        View row = mBubble.getOverlayRow();

        touch(row, MotionEvent.ACTION_DOWN, 10, 10);
        touch(row, MotionEvent.ACTION_MOVE, 10, 90);
        touch(row, MotionEvent.ACTION_UP, 10, 90);
        ShadowLooper.idleMainLooper();

        assertEquals(startY + 80, params.y);
        assertTrue(mOpened.isEmpty());
    }

    @Test
    public void aLongPressOnTheOverlayRowShowsTheRecentApps() {
        mBubble.setLayers(APP, AD, true, Arrays.asList(AD, APP));

        mBubble.getOverlayRow().performLongClick();

        assertEquals(View.VISIBLE, mBubble.getRecentsContainer().getVisibility());
    }

    @Test
    public void aTapOpensTheCurrentApp() {
        mBubble.setTopApp(AD, Collections.singletonList(AD));

        tap();

        assertEquals(Collections.singletonList(AD), mOpened);
    }

    @Test
    public void aTapBeforeAnyAppOpensNothing() {
        tap();

        assertTrue(mOpened.isEmpty());
    }

    @Test
    public void aDragMovesTheBubbleWithoutOpeningAnything() {
        mBubble.setTopApp(AD, Collections.singletonList(AD));
        WindowManager.LayoutParams params = mBubble.getLayoutParams();
        int startX = params.x;
        int startY = params.y;
        View pill = mBubble.getPill();

        touch(pill, MotionEvent.ACTION_DOWN, 10, 10);
        touch(pill, MotionEvent.ACTION_MOVE, 60, 90);
        touch(pill, MotionEvent.ACTION_UP, 60, 90);
        ShadowLooper.idleMainLooper();

        assertEquals(startX + 50, params.x);
        assertEquals(startY + 80, params.y);
        assertTrue(mOpened.isEmpty());
    }

    @Test
    public void aMoveInsideTheSlopIsStillATap() {
        mBubble.setTopApp(AD, Collections.singletonList(AD));
        WindowManager.LayoutParams params = mBubble.getLayoutParams();
        int startY = params.y;
        View pill = mBubble.getPill();

        touch(pill, MotionEvent.ACTION_DOWN, 10, 10);
        touch(pill, MotionEvent.ACTION_MOVE, 11, 11);
        touch(pill, MotionEvent.ACTION_UP, 11, 11);
        ShadowLooper.idleMainLooper();

        assertEquals(startY, params.y);
        assertEquals(Collections.singletonList(AD), mOpened);
    }

    @Test
    public void aDragPastTheEdgeStopsOnScreen() {
        WindowManager.LayoutParams params = mBubble.getLayoutParams();
        View pill = mBubble.getPill();

        touch(pill, MotionEvent.ACTION_DOWN, 10, 10);
        touch(pill, MotionEvent.ACTION_MOVE, -5000, -5000);
        touch(pill, MotionEvent.ACTION_UP, -5000, -5000);

        int margin = TopAppOverlayPolicy.edgeMarginPx(1f);
        assertEquals(margin, params.x);
        assertEquals(margin, params.y);
    }

    @Test
    public void aLongPressShowsTheRecentAppsAndARowOpensOne() {
        mBubble.setTopApp(AD, Arrays.asList(AD, APP));

        mBubble.getPill().performLongClick();

        assertEquals(View.VISIBLE, mBubble.getRecentsContainer().getVisibility());
        assertEquals(2, mBubble.getRecentsContainer().getChildCount());
        assertTrue(mBubble.getLayoutParams().alpha > TopAppOverlayPolicy.COLLAPSED_ALPHA);

        mBubble.getRecentsContainer().getChildAt(1).performClick();

        assertEquals(Collections.singletonList(APP), mOpened);
        assertEquals(View.GONE, mBubble.getRecentsContainer().getVisibility());
        assertEquals(TopAppOverlayPolicy.COLLAPSED_ALPHA, mBubble.getLayoutParams().alpha, 0f);
    }

    @Test
    public void aSecondLongPressCollapsesTheRecentApps() {
        mBubble.setTopApp(AD, Collections.singletonList(AD));

        mBubble.getPill().performLongClick();
        mBubble.getPill().performLongClick();

        assertEquals(View.GONE, mBubble.getRecentsContainer().getVisibility());
    }

    @Test
    public void openRecentsFollowNewApps() {
        mBubble.setTopApp(AD, Collections.singletonList(AD));
        mBubble.getPill().performLongClick();

        mBubble.setTopApp(APP, Arrays.asList(APP, AD));

        assertEquals(2, mBubble.getRecentsContainer().getChildCount());
    }

    @Test
    public void theWindowOnlyCoversTheBubble() {
        WindowManager.LayoutParams params = mBubble.getLayoutParams();

        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height);
        // The window manager adds its own flags, such as hardware acceleration, to these.
        assertEquals(TopAppOverlayPolicy.windowFlags(), params.flags & TopAppOverlayPolicy.windowFlags());
        assertTrue(params.alpha < 0.8f);
    }

    @Test
    public void aRotationKeepsTheRelativeSpot() {
        WindowManager.LayoutParams params = mBubble.getLayoutParams();
        View pill = mBubble.getPill();
        touch(pill, MotionEvent.ACTION_DOWN, 0, 0);
        touch(pill, MotionEvent.ACTION_MOVE, 5000, 5000);
        touch(pill, MotionEvent.ACTION_UP, 5000, 5000);
        int bottomRightX = params.x;
        int bottomRightY = params.y;

        RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-mdpi");
        mBubble.onConfigurationChanged();

        assertTrue("the bubble should move right along the longer side", params.x > bottomRightX);
        assertTrue("the bubble should move up along the shorter side", params.y < bottomRightY);
        assertEquals(TopAppOverlayPolicy.clamp(5000, mBubble.getPill().getRootView().getWidth(), 640,
                TopAppOverlayPolicy.edgeMarginPx(1f)), params.x);
        assertEquals(TopAppOverlayPolicy.clamp(5000, mBubble.getPill().getRootView().getHeight(), 360,
                TopAppOverlayPolicy.edgeMarginPx(1f)), params.y);
    }

    @Test
    public void aConfigurationChangeWithTheSameDisplayLeavesTheBubbleAlone() {
        WindowManager.LayoutParams params = mBubble.getLayoutParams();
        int startX = params.x;
        int startY = params.y;

        mBubble.onConfigurationChanged();

        assertEquals(startX, params.x);
        assertEquals(startY, params.y);
    }
}
