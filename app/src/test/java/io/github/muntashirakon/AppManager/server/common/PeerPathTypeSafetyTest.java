// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Bytes on the privileged channel must never choose which class the other side instantiates.
 * The reply payload is read through a concrete creator, a remote failure travels as text rather
 * than through Java serialization, and parameter-type names resolve only against an allowlist.
 */
@RunWith(RobolectricTestRunner.class)
public class PeerPathTypeSafetyTest {
    @Test
    public void allowlistedTypeNamesResolve() {
        assertSame(String.class, ClassUtils.string2Class("java.lang.String"));
        assertSame(int.class, ClassUtils.string2Class("int"));
        assertSame(Bundle.class, ClassUtils.string2Class("android.os.Bundle"));
        assertSame(Intent.class, ClassUtils.string2Class("android.content.Intent"));
        assertSame(byte[].class, ClassUtils.string2Class("[B"));
    }

    @Test
    public void unexpectedTypeNamesAreRefusedNotLoaded() {
        // These all resolve fine through Class.forName, which is exactly the point: the wire
        // must not be able to pick them.
        assertNull(ClassUtils.string2Class("java.lang.Runtime"));
        assertNull(ClassUtils.string2Class("java.io.File"));
        assertNull(ClassUtils.string2Class("java.util.HashMap"));
        assertNull(ClassUtils.string2Class("io.github.muntashirakon.AppManager.server.common.Shell"));
        assertNull(ClassUtils.string2Class("does.not.Exist"));
        assertNull(ClassUtils.string2Class((String) null));
    }

    @Test
    public void allowlistIsNotPoisonedByRepeatedLookups() {
        // The resolver used to cache whatever Class.forName returned, so a name only had to get
        // through once.
        assertNull(ClassUtils.string2Class("java.lang.Runtime"));
        assertNull(ClassUtils.string2Class("java.lang.Runtime"));
        assertSame(String.class, ClassUtils.string2Class("java.lang.String"));
    }

    @Test
    public void shellResultSurvivesTheReplyRoundTrip() {
        CallerResult sent = new CallerResult();
        sent.setReply(marshallShellResult("hello from the shell", 0));

        CallerResult received = roundTrip(sent);
        Shell.Result result = received.getShellResult();
        assertNotNull(result);
        assertEquals("hello from the shell", result.getMessage());
        assertEquals(0, result.getStatusCode());
        assertNull(received.getThrowable());
    }

    @Test
    public void remoteFailureTravelsAsTextNotAsASerializedType() {
        CallerResult sent = new CallerResult();
        sent.setThrowable(new IllegalStateException("shell died"));

        CallerResult received = roundTrip(sent);
        Throwable throwable = received.getThrowable();
        assertNotNull(throwable);
        // Rebuilt locally as our own type -- the remote class name is data, not a type to load.
        assertSame(CallerResult.RemoteFailure.class, throwable.getClass());
        assertTrue(throwable.getMessage(), throwable.getMessage().contains("shell died"));
        assertTrue(throwable.getMessage(), throwable.getMessage().contains("IllegalStateException"));
        assertTrue(((CallerResult.RemoteFailure) throwable).getRemoteStackTrace().contains("at "));
        assertNull(received.getShellResult());
    }

    @Test
    public void aFailureWithoutAMessageStillTravels() {
        CallerResult sent = new CallerResult();
        sent.setThrowable(new NullPointerException());

        Throwable throwable = roundTrip(sent).getThrowable();
        assertNotNull(throwable);
        assertTrue(throwable.getMessage(), throwable.getMessage().contains("NullPointerException"));
    }

    @Test
    public void anEmptyReplyIsNotAFailure() {
        CallerResult received = roundTrip(new CallerResult());
        assertNull(received.getThrowable());
        assertNull(received.getShellResult());
    }

    @NonNull
    private static CallerResult roundTrip(@NonNull CallerResult result) {
        return ParcelableUtil.unmarshall(ParcelableUtil.marshall(result), CallerResult.CREATOR);
    }

    /** Mirrors what the privileged server writes for a shell reply. */
    private static byte[] marshallShellResult(String message, int statusCode) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeString(message);
            parcel.writeInt(statusCode);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }
}
