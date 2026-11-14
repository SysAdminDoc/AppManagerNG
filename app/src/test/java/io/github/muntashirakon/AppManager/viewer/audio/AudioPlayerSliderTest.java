// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.slider.Slider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;

/**
 * Material's Slider checks its value against its range when it draws and throws when the value is outside it, which
 * is the "Slider value(-13.0)" crash upstream saw (#2064). Each test ends with a draw so that check runs.
 */
@RunWith(RobolectricTestRunner.class)
public class AudioPlayerSliderTest {
    private Slider mSlider;

    @Before
    public void setUp() {
        mSlider = new Slider(new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2));
    }

    /** Without the clamp, laying out or drawing is where it fails, so the other tests' draws are a real check. */
    @Test
    public void theSliderItselfThrowsOnAValueOutsideItsRange() {
        AudioPlayerDialogFragment.setSliderRange(mSlider, 60_000);
        mSlider.setValue(-13);
        try {
            draw(mSlider);
            throw new AssertionError("The slider drew a value outside its range");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), String.valueOf(e.getMessage()).contains("-13"));
        }
    }

    @Test
    public void timesShowMinutesAndSecondsAndTimeLeftOnlyWhenTheLengthIsKnown() {
        assertEquals("0:00", AudioPlayerDialogFragment.formatTime(0, 60_000, false));
        assertEquals("1:05", AudioPlayerDialogFragment.formatTime(65_000, 60_000, false));
        assertEquals("12:30", AudioPlayerDialogFragment.formatTime(750_000, -1, false));
        assertEquals("-0:55", AudioPlayerDialogFragment.formatTime(5_000, 60_000, true));
        // A stream's length is -1: there's no time left to show, so it counts up instead of "--1:-5".
        assertEquals("1:05", AudioPlayerDialogFragment.formatTime(65_000, -1, true));
        assertEquals("0:00", AudioPlayerDialogFragment.formatTime(-13, 60_000, false));
        assertEquals("-0:00", AudioPlayerDialogFragment.formatTime(60_500, 60_000, true));
    }

    @Test
    public void aPositionBelowZeroStaysAtTheStart() {
        AudioPlayerDialogFragment.setSliderRange(mSlider, 60_000);

        AudioPlayerDialogFragment.setSliderPosition(mSlider, -13);

        assertEquals(0f, mSlider.getValue(), 0f);
        draw(mSlider);
    }

    @Test
    public void aPositionPastTheEndStopsAtTheEnd() {
        AudioPlayerDialogFragment.setSliderRange(mSlider, 60_000);

        AudioPlayerDialogFragment.setSliderPosition(mSlider, 60_500);

        assertEquals(60_000f, mSlider.getValue(), 0f);
        draw(mSlider);
    }

    @Test
    public void anUnknownLengthDisablesTheSlider() {
        AudioPlayerDialogFragment.setSliderRange(mSlider, -1);
        AudioPlayerDialogFragment.setSliderPosition(mSlider, 5_000);

        assertFalse(mSlider.isEnabled());
        assertEquals(0f, mSlider.getValue(), 0f);
        draw(mSlider);

        AudioPlayerDialogFragment.setSliderRange(mSlider, 0);
        assertFalse(mSlider.isEnabled());
        draw(mSlider);
    }

    @Test
    public void aShorterNextTrackStartsTheSliderOver() {
        AudioPlayerDialogFragment.setSliderRange(mSlider, 300_000);
        AudioPlayerDialogFragment.setSliderPosition(mSlider, 250_000);
        draw(mSlider);

        AudioPlayerDialogFragment.setSliderRange(mSlider, 60_000);

        assertTrue(mSlider.isEnabled());
        assertEquals(0f, mSlider.getValue(), 0f);
        assertEquals(60_000f, mSlider.getValueTo(), 0f);
        draw(mSlider);
    }

    @Test
    public void aKnownLengthAfterAnUnknownOneTurnsTheSliderBackOn() {
        AudioPlayerDialogFragment.setSliderRange(mSlider, -1);
        AudioPlayerDialogFragment.setSliderRange(mSlider, 90_000);

        assertTrue(mSlider.isEnabled());
        AudioPlayerDialogFragment.setSliderPosition(mSlider, 45_000);
        assertEquals(45_000f, mSlider.getValue(), 0f);
        draw(mSlider);
    }

    @Test
    public void skipsStayInsideTheTrack() {
        assertEquals(0, AudioPlayerDialogFragment.skipTarget(3_000, -10_000, 60_000));
        assertEquals(50_000, AudioPlayerDialogFragment.skipTarget(60_000, -10_000, 60_000));
        assertEquals(60_000, AudioPlayerDialogFragment.skipTarget(55_000, 10_000, 60_000));
        assertEquals(25_000, AudioPlayerDialogFragment.skipTarget(15_000, 10_000, 60_000));
        // Unknown length: only the start is a limit.
        assertEquals(0, AudioPlayerDialogFragment.skipTarget(3_000, -10_000, -1));
        assertEquals(13_000, AudioPlayerDialogFragment.skipTarget(3_000, 10_000, -1));
        assertEquals(Integer.MAX_VALUE, AudioPlayerDialogFragment.skipTarget(Integer.MAX_VALUE, 10_000, -1));
    }

    private static void draw(@NonNull Slider slider) {
        int width = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        slider.measure(width, height);
        slider.layout(0, 0, slider.getMeasuredWidth(), Math.max(1, slider.getMeasuredHeight()));
        Bitmap bitmap = Bitmap.createBitmap(600, Math.max(1, slider.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        slider.draw(new Canvas(bitmap));
    }
}
