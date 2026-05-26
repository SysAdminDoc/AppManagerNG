// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static org.junit.Assert.assertEquals;

import android.app.Activity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class BarChartViewTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void defaultAxisKeepsZeroMinimum() {
        BarChartView chart = newChart();

        chart.setData(Arrays.asList(4f, 5f), Arrays.asList("A", "B"));

        assertEquals(0f, chart.getAxisMinValue(), DELTA);
        assertEquals(5f, chart.getAxisMaxValue(), DELTA);
        assertEquals(0f, chart.getNormalizedValueForTesting(0f), DELTA);
        assertEquals(1f, chart.getNormalizedValueForTesting(5f), DELTA);
    }

    @Test
    public void manualAxisMinimumOffsetsNormalizedValues() {
        BarChartView chart = newChart();

        chart.setData(Arrays.asList(4f, 5f, 6f), Arrays.asList("A", "B", "C"));
        chart.setManualYAxisRange(4f, 6f);

        assertEquals(4f, chart.getAxisMinValue(), DELTA);
        assertEquals(6f, chart.getAxisMaxValue(), DELTA);
        assertEquals(0f, chart.getNormalizedValueForTesting(4f), DELTA);
        assertEquals(0.5f, chart.getNormalizedValueForTesting(5f), DELTA);
        assertEquals(1f, chart.getNormalizedValueForTesting(6f), DELTA);
    }

    @Test
    public void normalizedValuesClampOutsideManualRange() {
        BarChartView chart = newChart();

        chart.setManualYAxisRange(4f, 6f);
        chart.setData(Arrays.asList(3f, 7f), Arrays.asList("A", "B"));

        assertEquals(0f, chart.getNormalizedValueForTesting(3f), DELTA);
        assertEquals(1f, chart.getNormalizedValueForTesting(7f), DELTA);
    }

    @Test
    public void manualAxisPadsInvalidRange() {
        BarChartView chart = newChart();

        chart.setManualYAxisRange(5f, 5f);
        chart.setData(Arrays.asList(5f), Arrays.asList("A"));

        assertEquals(5f, chart.getAxisMinValue(), DELTA);
        assertEquals(6f, chart.getAxisMaxValue(), DELTA);
        assertEquals(0f, chart.getNormalizedValueForTesting(5f), DELTA);
    }

    private static BarChartView newChart() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme_V2);
        return new BarChartView(activity);
    }
}
