// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.format.Formatter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Issue 54: About the device shows memory, swap, storage and every refresh rate the screen supports. */
@RunWith(RobolectricTestRunner.class)
public class DeviceInfoTextTest {
    private static final long GB = 1_000_000_000L;

    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void ramReadsTotalThenAvailable() {
        assertEquals(size(8 * GB) + " total, " + size(3 * GB) + " available",
                DeviceInfoText.ramAmount(mContext, 8 * GB, 3 * GB));
    }

    @Test
    public void swapReadsTotalThenUsed() {
        assertEquals(size(4 * GB) + " total, " + size(3 * GB) + " used",
                DeviceInfoText.swapAmount(mContext, 4 * GB, GB));
        // More free than total is a misread, not negative use.
        assertEquals(size(4 * GB) + " total, " + size(0) + " used",
                DeviceInfoText.swapAmount(mContext, 4 * GB, 5 * GB));
        assertEquals("None", DeviceInfoText.swapAmount(mContext, 0, 0));
    }

    @Test
    public void storageReadsTotalThenFree() {
        assertEquals(size(256 * GB) + " total, " + size(120 * GB) + " free",
                DeviceInfoText.storageAmount(mContext, 256 * GB, 120 * GB));
    }

    @Test
    public void supportedRatesAreListedOnceLowestFirst() {
        // Modes repeat a rate at other resolutions, and the same rate can come back a hair off.
        assertEquals(Arrays.asList("60", "90", "120"), DeviceInfoText.supportedRefreshRates(
                new float[]{120f, 60.000004f, 90f, 59.999996f, 120.00001f, 0f, Float.NaN}, Locale.US));
        assertEquals(Arrays.asList("59.9", "60"),
                DeviceInfoText.supportedRefreshRates(new float[]{60f, 59.94f}, Locale.US));
        assertEquals(Collections.emptyList(), DeviceInfoText.supportedRefreshRates(null, Locale.US));
    }

    @Test
    public void refreshRateNamesEverySupportedRate() {
        assertEquals("120 Hz (supported: 60, 90, 120 Hz)",
                DeviceInfoText.refreshRate(mContext, 120f, new float[]{60f, 90f, 120f, 90f}));
        // One rate needs no list, and Android 5 can't list them at all.
        assertEquals("60 Hz", DeviceInfoText.refreshRate(mContext, 60f, new float[]{60.000004f}));
        assertEquals("60 Hz", DeviceInfoText.refreshRate(mContext, 60f, null));
    }

    @Test
    public void ratesWithADecimalCommaAreSeparatedBySemicolons() {
        Locale russian = new Locale("ru");

        assertEquals(Arrays.asList("59,9", "60"),
                DeviceInfoText.supportedRefreshRates(new float[]{60f, 59.94f}, russian));
        assertEquals("; ", DeviceInfoText.listSeparator(russian));
        assertEquals(", ", DeviceInfoText.listSeparator(Locale.US));
        assertTrue(DeviceInfoText.listSeparator(Locale.GERMANY).startsWith(";"));
    }

    private String size(long bytes) {
        return Formatter.formatFileSize(mContext, bytes);
    }
}
