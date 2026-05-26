// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MotionUtilsTest {
    private static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";
    private static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";
    private static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";

    private ContentResolver mResolver;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mResolver = mContext.getContentResolver();
        resetScales();
    }

    @After
    public void tearDown() {
        resetScales();
    }

    @Test
    public void shouldReduceMotionWhenAnySystemAnimationScaleIsDisabled() {
        Settings.Global.putString(mResolver, WINDOW_ANIMATION_SCALE, "1");
        Settings.Global.putString(mResolver, TRANSITION_ANIMATION_SCALE, "0");
        Settings.Global.putString(mResolver, ANIMATOR_DURATION_SCALE, "1");

        assertTrue(MotionUtils.shouldReduceMotion(mContext));
    }

    @Test
    public void shouldNotReduceMotionWhenAllScalesAreEnabled() {
        Settings.Global.putString(mResolver, WINDOW_ANIMATION_SCALE, "1");
        Settings.Global.putString(mResolver, TRANSITION_ANIMATION_SCALE, "0.5");
        Settings.Global.putString(mResolver, ANIMATOR_DURATION_SCALE, "1");

        assertFalse(MotionUtils.shouldReduceMotion(mContext));
    }

    @Test
    public void shouldIgnoreMissingOrMalformedAnimationScales() {
        Settings.Global.putString(mResolver, WINDOW_ANIMATION_SCALE, null);
        Settings.Global.putString(mResolver, TRANSITION_ANIMATION_SCALE, "not-a-number");
        Settings.Global.putString(mResolver, ANIMATOR_DURATION_SCALE, "1");

        assertFalse(MotionUtils.shouldReduceMotion(mContext));
    }

    private void resetScales() {
        Settings.Global.putString(mResolver, WINDOW_ANIMATION_SCALE, null);
        Settings.Global.putString(mResolver, TRANSITION_ANIMATION_SCALE, null);
        Settings.Global.putString(mResolver, ANIMATOR_DURATION_SCALE, null);
    }
}
