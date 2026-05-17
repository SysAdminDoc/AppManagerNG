// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DeveloperVerificationCompatTest {
    @Test
    public void getFailureReasonReturnsNotPresentWithoutExtra() {
        assertEquals(DeveloperVerificationCompat.FAILURE_REASON_NOT_PRESENT,
                DeveloperVerificationCompat.getFailureReason(new Intent()));
    }

    @Test
    public void getFailureReasonReadsIntegerExtra() {
        Intent intent = new Intent();
        intent.putExtra(DeveloperVerificationCompat.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON,
                DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED);

        assertEquals(DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED,
                DeveloperVerificationCompat.getFailureReason(intent));
    }

    @Test
    public void getFailureReasonParsesStringExtra() {
        Intent intent = new Intent();
        intent.putExtra(DeveloperVerificationCompat.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, "1");

        assertEquals(DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE,
                DeveloperVerificationCompat.getFailureReason(intent));
    }

    @Test
    public void getFailureReasonRejectsUnknownCode() {
        Intent intent = new Intent();
        intent.putExtra(DeveloperVerificationCompat.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, 99);

        assertEquals(DeveloperVerificationCompat.FAILURE_REASON_NOT_PRESENT,
                DeveloperVerificationCompat.getFailureReason(intent));
    }

    @Test
    public void getFailureReasonNameIsStableForTranscripts() {
        assertEquals("DEVELOPER_BLOCKED", DeveloperVerificationCompat.getFailureReasonName(
                DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED));
        assertEquals("NETWORK_UNAVAILABLE", DeveloperVerificationCompat.getFailureReasonName(
                DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE));
        assertEquals("UNKNOWN", DeveloperVerificationCompat.getFailureReasonName(
                DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN));
    }

    @Test
    public void appendFailureReasonAddsReadableLineOnce() {
        Context context = RuntimeEnvironment.getApplication();
        String first = DeveloperVerificationCompat.appendFailureReason(context,
                "INSTALL_FAILED_ABORTED",
                DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED);
        String second = DeveloperVerificationCompat.appendFailureReason(context, first,
                DeveloperVerificationCompat.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED);

        assertEquals(first, second);
        assertTrue(first.contains("INSTALL_FAILED_ABORTED"));
        assertTrue(first.contains("Developer verification:"));
        assertTrue(first.contains("DEVELOPER_BLOCKED"));
    }
}
