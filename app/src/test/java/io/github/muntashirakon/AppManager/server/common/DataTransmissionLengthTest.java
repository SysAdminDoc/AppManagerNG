// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Guards the pre-auth length-prefix validation in {@link DataTransmission#readMessage()}.
 * A malformed length must be rejected before allocation instead of triggering an
 * OutOfMemoryError (huge positive) or NegativeArraySizeException (negative).
 */
public class DataTransmissionLengthTest {
    private static byte[] framed(int declaredLength, byte[] payload) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(buffer);
        dos.writeInt(declaredLength);
        if (payload != null) {
            dos.write(payload);
        }
        dos.flush();
        return buffer.toByteArray();
    }

    private static DataTransmission transmissionReading(byte[] input) {
        return new DataTransmission(new ByteArrayOutputStream(),
                new ByteArrayInputStream(input), false);
    }

    @Test
    public void validMessageRoundTrips() throws IOException {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        DataTransmission dt = transmissionReading(framed(payload.length, payload));
        // sendAndReceiveMessage writes the outgoing message then reads the framed reply.
        assertArrayEquals(payload, dt.sendAndReceiveMessage(new byte[0]));
    }

    @Test
    public void oversizedLengthIsRejectedBeforeAllocation() throws IOException {
        DataTransmission dt = transmissionReading(framed(Integer.MAX_VALUE, null));
        assertThrows(IOException.class, () -> dt.sendAndReceiveMessage(new byte[0]));
    }

    @Test
    public void negativeLengthIsRejected() throws IOException {
        DataTransmission dt = transmissionReading(framed(-1, null));
        assertThrows(IOException.class, () -> dt.sendAndReceiveMessage(new byte[0]));
    }
}
