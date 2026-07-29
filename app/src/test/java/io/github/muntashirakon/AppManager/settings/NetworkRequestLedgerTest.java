// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * The transparency ledger promises evidence of optional egress. It has to actually record it —
 * and record nothing more than the outcome and the time.
 */
@RunWith(RobolectricTestRunner.class)
public class NetworkRequestLedgerTest {
    private static final long T1 = 1_780_000_000_000L;
    private static final long T2 = T1 + 60_000L;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        NetworkRequestLedger.clear(mContext);
    }

    @Test
    public void nothingIsRecordedUntilARequestHappens() {
        assertEquals(0L, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL));
        assertEquals(0L, NetworkRequestLedger.getLastFailure(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL));
        assertEquals(0L, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_PITHUS));
        assertEquals(0L, NetworkRequestLedger.getLastFailure(mContext, NetworkRequestLedger.CLIENT_PITHUS));
    }

    @Test
    public void successAndFailureAreRecordedIndependently() {
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL, true, T1);
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL, false, T2);
        assertEquals(T1, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL));
        assertEquals(T2, NetworkRequestLedger.getLastFailure(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL));
    }

    @Test
    public void clientsDoNotShareTheirRecords() {
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_PITHUS, true, T1);
        assertEquals(T1, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_PITHUS));
        assertEquals(0L, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL));
    }

    @Test
    public void aLaterOutcomeReplacesTheEarlierOne() {
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_PITHUS, true, T1);
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_PITHUS, true, T2);
        assertEquals(T2, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_PITHUS));
    }

    @Test
    public void clearForgetsEverything() {
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_PITHUS, true, T1);
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL, false, T2);
        NetworkRequestLedger.clear(mContext);
        assertEquals(0L, NetworkRequestLedger.getLastSuccess(mContext, NetworkRequestLedger.CLIENT_PITHUS));
        assertEquals(0L, NetworkRequestLedger.getLastFailure(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL));
    }

    @Test
    public void theLedgerDisplaysNeverBeforeAnyRequestAndTimestampsAfterwards() {
        String before = NetworkTransparencyLedger.formatForDisplay(mContext,
                NetworkTransparencyLedger.buildEntries(mContext));
        assertTrue(before.contains("Last success: never"));
        assertTrue(before.contains("Last failure: never"));

        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL, true, T1);
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_PITHUS, false, T2);
        List<NetworkTransparencyLedger.Entry> entries = NetworkTransparencyLedger.buildEntries(mContext);
        String after = NetworkTransparencyLedger.formatForDisplay(mContext, entries);
        assertFalse("a recorded success must not still read as never",
                after.indexOf("Last success: never") == after.lastIndexOf("Last success: never")
                        && !after.contains("Last success: 20"));
        assertTrue(after.contains("Last failure: 20"));

        for (NetworkTransparencyLedger.Entry entry : entries) {
            if ("VirusTotal".equals(entry.name)) {
                assertEquals(T1, entry.lastSuccessMillis);
                assertEquals(0L, entry.lastFailureMillis);
            } else if ("Pithus".equals(entry.name)) {
                assertEquals(0L, entry.lastSuccessMillis);
                assertEquals(T2, entry.lastFailureMillis);
            }
        }
    }

    @Test
    public void theLedgerRecordsNoIdentifyingDetail() {
        NetworkRequestLedger.record(mContext, NetworkRequestLedger.CLIENT_VIRUS_TOTAL, true, T1);
        String display = NetworkTransparencyLedger.formatForDisplay(mContext,
                NetworkTransparencyLedger.buildEntries(mContext));
        // Only the fixed destination class and the outcome may appear — never a hash, a package
        // name, a full URL, or a response body.
        assertFalse(display.contains("http://"));
        assertFalse(display.contains("https://"));
        assertFalse(display.contains(mContext.getPackageName()));
        assertFalse(display.contains("x-apikey"));
    }
}
