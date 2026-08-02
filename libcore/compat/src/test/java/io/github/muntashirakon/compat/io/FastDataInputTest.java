// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FastDataInputTest {
    /**
     * A reference that points past everything the stream has defined must fail as a checked
     * {@link IOException}. Callers only contain {@code IOException} /
     * {@code XmlPullParserException}, so an unchecked
     * {@link ArrayIndexOutOfBoundsException} would escape them.
     */
    @Test
    public void undefinedInternedReferenceRaisesIoException() {
        // A reference of 5 with no interned strings defined at all.
        byte[] blob = new byte[]{0x00, 0x05};
        FastDataInput in = FastDataInput.obtainUsing4ByteSequences(new ByteArrayInputStream(blob));
        try {
            in.readInternedUTF();
            fail("Expected IOException for an undefined interned string reference");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Undefined interned string reference"));
        } finally {
            in.release();
        }
    }

    /**
     * The backing table starts at length 32, so a reference just past the table length used to
     * be the case that threw rather than the case that was rejected.
     */
    @Test
    public void referencePastBackingArrayRaisesIoException() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeNewInternedString(os, "first");
        // Reference 40 -- beyond both the single defined string and the initial array length.
        os.write(0x00);
        os.write(40);

        FastDataInput in = FastDataInput.obtainUsing4ByteSequences(new ByteArrayInputStream(os.toByteArray()));
        try {
            assertEquals("first", in.readInternedUTF());
            in.readInternedUTF();
            fail("Expected IOException for a reference past the interned string table");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Undefined interned string reference"));
        } finally {
            in.release();
        }
    }

    /** The valid path still resolves back-references. */
    @Test
    public void definedInternedReferenceResolves() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeNewInternedString(os, "alpha");
        writeNewInternedString(os, "beta");
        os.write(0x00);
        os.write(0x00); // back-reference to "alpha"
        os.write(0x00);
        os.write(0x01); // back-reference to "beta"

        FastDataInput in = FastDataInput.obtainUsing4ByteSequences(new ByteArrayInputStream(os.toByteArray()));
        try {
            assertEquals("alpha", in.readInternedUTF());
            assertEquals("beta", in.readInternedUTF());
            assertEquals("alpha", in.readInternedUTF());
            assertEquals("beta", in.readInternedUTF());
        } finally {
            in.release();
        }
    }

    /**
     * {@link FastDataInput#obtainUsing4ByteSequences} is backed by a process-wide recycler, so a
     * released instance must not carry its interned string table into the next — differently
     * trusted — parse.
     */
    @Test
    public void releaseClearsTheInternedStringTable() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeNewInternedString(os, "secret-from-the-first-parse");
        FastDataInput first = FastDataInput.obtainUsing4ByteSequences(new ByteArrayInputStream(os.toByteArray()));
        assertEquals("secret-from-the-first-parse", first.readInternedUTF());
        first.release();

        // A fresh parse that immediately asks for reference 0 must not be handed the string the
        // previous parse interned.
        FastDataInput second = FastDataInput.obtainUsing4ByteSequences(
                new ByteArrayInputStream(new byte[]{0x00, 0x00}));
        try {
            second.readInternedUTF();
            fail("Interned string table survived release()");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Undefined interned string reference"));
        } finally {
            second.release();
        }
    }

    /** Reads after release fail loudly rather than operating on a recycled buffer. */
    @Test
    public void readAfterReleaseIsCheckedException() {
        FastDataInput in = FastDataInput.obtainUsing4ByteSequences(
                new ByteArrayInputStream(new byte[]{0x01, 0x02, 0x03, 0x04}));
        in.release();
        try {
            in.readInt();
            fail("Expected IOException reading from a released instance");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("released"));
        }
    }

    /**
     * Writes the "this is a new string" marker (0xFFFF) followed by a length-prefixed UTF-8
     * payload, mirroring {@code FastDataOutput#writeInternedUTF}.
     */
    private static void writeNewInternedString(@NonNull ByteArrayOutputStream os, @NonNull String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        os.write(0xff);
        os.write(0xff);
        os.write((bytes.length >> 8) & 0xff);
        os.write(bytes.length & 0xff);
        os.write(bytes, 0, bytes.length);
    }
}
