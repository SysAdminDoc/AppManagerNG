// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;

import io.github.muntashirakon.AppManager.servermanager.AdbFailure.Code;
import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

/**
 * Every ADB-mode failure used to end in the same general message (issue #20). Each exception shape
 * the connect and start paths throw maps to one code with one next step.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbFailureTest {
    @After
    public void tearDown() {
        AdbFailure.clear();
    }

    @Test
    public void eachFailureShapeGetsItsCode() {
        assertCode(Code.NO_WIFI, new AdbFailure.NoWifiException());
        assertCode(Code.PAIRING_REQUIRED, new AdbPairingRequiredException("Pairing required"));
        // What the S22 answered before it was paired (2026-09-30)
        assertCode(Code.PAIRING_REQUIRED, new IOException(new SSLProtocolException("Read error: ssl=0xb4: Failure "
                + "in SSL library, usually a protocol error\nerror:10000416:SSL routines:OPENSSL_internal:"
                + "SSLV3_ALERT_CERTIFICATE_UNKNOWN")));
        assertCode(Code.CERTIFICATE_REJECTED, new LocalServerManager.AdbUnreachableException(
                new SSLHandshakeException("Certificate rejected")));
        assertCode(Code.CERTIFICATE_REJECTED, new IOException(new CertificateException("bad")));
        assertCode(Code.CERTIFICATE_REJECTED, new AdbAuthenticationFailedException());
        assertCode(Code.CONNECTION_REFUSED, new LocalServerManager.AdbUnreachableException(
                new ConnectException("failed to connect to /192.168.1.20 (port 37123): Connection refused")));
        assertCode(Code.CONNECTION_REFUSED, new LocalServerManager.AdbUnreachableException(null));
        assertCode(Code.STREAM_CLOSED_AFTER_CONNECT, serverStart(new LocalServerManager.ShellClosedEarlyException(null)));
        assertCode(Code.LAUNCH_DENIED, serverStart(new LocalServerManager.LaunchRefusedException(
                "Could not prepare the staging folder: chmod: Operation not permitted")));
        assertCode(Code.LAUNCH_TIMEOUT, serverStart(new TimeoutException("The server launcher didn't answer.")));
        assertCode(Code.LAUNCH_TIMEOUT, new ServerConnectionFailure(ServerConnectionFailure.Reason.UNRESPONSIVE,
                "silent", null));
    }

    @Test
    public void wirelessDebuggingBeingOffExplainsAnyFailure() {
        AdbFailure failure = AdbFailure.classify(new LocalServerManager.AdbUnreachableException(
                new ConnectException("Connection refused")), true);
        assertEquals(Code.WIRELESS_DEBUGGING_OFF, failure.code);
        // No Wi-Fi at all is the more useful thing to say
        assertEquals(Code.NO_WIFI, AdbFailure.classify(new AdbFailure.NoWifiException(), true).code);
    }

    @Test
    public void anUnknownFailureKeepsItsClassAndNothingElse() {
        AdbFailure failure = AdbFailure.classify(new IOException("adb 192.168.1.20:37123 token=0123456789abcdef",
                new EOFException("192.168.1.20")), false);

        assertEquals(Code.UNKNOWN, failure.code);
        assertEquals("java.io.EOFException", failure.exceptionClass);
        assertEquals("UNKNOWN (java.io.EOFException)", failure.describe());
        assertFalse(failure.describe().contains("192.168"));
        assertFalse(failure.describe().contains("0123456789abcdef"));
        // A known code has no need for the class
        assertNull(AdbFailure.classify(new TimeoutException(), false).exceptionClass);
    }

    @Test
    public void theLastFailureOutlivesARestartUntilAConnectWorks() {
        assertNull(AdbFailure.getLast());
        AdbFailure.record(AdbFailure.classify(new IllegalStateException("x"), false));
        AdbFailure last = AdbFailure.getLast();
        assertEquals(Code.UNKNOWN, last.code);
        assertEquals("java.lang.IllegalStateException", last.exceptionClass);

        AdbFailure.record(AdbFailure.classify(new TimeoutException(), false));
        assertEquals(Code.LAUNCH_TIMEOUT, AdbFailure.getLast().code);
        assertNull(AdbFailure.getLast().exceptionClass);

        AdbFailure.clear();
        assertNull(AdbFailure.getLast());
        // One written by a later version with a code this one doesn't have
        assertNull(AdbFailure.deserialize("SOMETHING_NEW"));
    }

    @Test
    public void everyCodeSaysWhatHappenedAndWhatToTry() {
        Context context = ApplicationProvider.getApplicationContext();
        for (Code code : Code.values()) {
            String message = context.getString(code.message);
            String next = context.getString(code.nextStep);
            assertTrue(code.name(), message.endsWith("."));
            assertTrue(code.name(), next.endsWith("."));
            assertFalse(code.name(), message.equals(next));
            assertTrue(new AdbFailure(code, null).explain(context).toString().startsWith(message + " "));
        }
    }

    private static ServerConnectionFailure serverStart(Throwable cause) {
        return new ServerConnectionFailure(ServerConnectionFailure.Reason.SERVER_START, "Could not start server", cause);
    }

    private static void assertCode(Code expected, Throwable failure) {
        assertEquals(failure.toString(), expected, AdbFailure.classify(failure, false).code);
    }
}
