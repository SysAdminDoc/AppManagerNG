// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Malformed-input contract for the ABX parser: attacker-influenceable bytes (backup metadata,
 * copies of {@code /data/system/*.xml}, user-opened files) must never produce an unchecked
 * throwable, because callers only contain {@link IOException} and {@link XmlPullParserException}.
 */
@RunWith(RobolectricTestRunner.class)
public class BinaryXmlMalformedTest {
    /** A START_TAG whose interned name reference was never defined. */
    @Test
    public void danglingTagNameReference() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeMagic(os);
        os.write(XmlPullParser.START_TAG);
        os.write(0x00);
        os.write(0x05); // reference 5, nothing interned yet
        assertParseFailsCleanly(os.toByteArray());
    }

    /** An ATTRIBUTE whose interned value reference was never defined. */
    @Test
    public void danglingAttributeValueReference() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeMagic(os);
        os.write(XmlPullParser.START_TAG);
        writeNewInternedString(os, "tag");
        os.write(BinaryXmlSerializer.ATTRIBUTE | BinaryXmlSerializer.TYPE_STRING_INTERNED);
        writeNewInternedString(os, "name");
        os.write(0x00);
        os.write(0x40); // reference 64, beyond anything defined
        assertParseFailsCleanly(os.toByteArray());
    }

    /** Truncated stream in the middle of an interned string payload. */
    @Test
    public void truncatedInternedString() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeMagic(os);
        os.write(XmlPullParser.START_TAG);
        os.write(0xff);
        os.write(0xff);
        os.write(0x00);
        os.write(0x20); // claims 32 bytes
        os.write('a');  // ...delivers one
        // A stream that ends mid-token is reported as EOF, which the parser reports as
        // END_DOCUMENT (upstream behaviour). What must not happen is an unchecked throwable.
        assertNoUncheckedFailure(os.toByteArray());
    }

    /** Numeric entities are stream-controlled digits; a parse failure must stay checked. */
    @Test
    public void malformedNumericEntity() {
        assertEntityRejected("#");
        assertEntityRejected("#x41");
        assertEntityRejected("#zzz");
        assertEntityRejected("#99999999999999999999");
        assertEntityRejected("#-5");
    }

    /** A numeric entity above the Unicode range used to be silently truncated to a char. */
    @Test
    public void numericEntityOutOfRange() {
        assertEntityRejected("#1114112"); // Character.MAX_CODE_POINT + 1
    }

    @Test
    public void validNumericEntitiesResolve() throws XmlPullParserException {
        assertEquals("A", BinaryXmlPullParser.resolveEntity("#65"));
        assertEquals("<", BinaryXmlPullParser.resolveEntity("lt"));
        // Supplementary plane: two chars, not a truncated one.
        assertEquals(2, BinaryXmlPullParser.resolveEntity("#128512").length());
    }

    private static void assertEntityRejected(String entity) {
        try {
            BinaryXmlPullParser.resolveEntity(entity);
            fail("Expected XmlPullParserException for entity " + entity);
        } catch (XmlPullParserException expected) {
            // Checked, as the caller contract requires.
        } catch (Throwable t) {
            fail("Entity " + entity + " surfaced as an unchecked " + t.getClass().getName());
        }
    }

    /**
     * Drives the parser to exhaustion and asserts the only failures are the two checked kinds.
     */
    static void assertParseFailsCleanly(byte[] blob) {
        if (drainQuietly(blob) == null) {
            fail("Expected a checked parse failure, but the malformed stream parsed cleanly");
        }
    }

    /** Weaker contract: the stream may parse to END_DOCUMENT, but must not throw unchecked. */
    static void assertNoUncheckedFailure(byte[] blob) {
        drainQuietly(blob);
    }

    /**
     * Drives the parser to exhaustion, returning the checked failure it raised, or {@code null}
     * if it reached END_DOCUMENT. Any unchecked throwable fails the test outright.
     */
    private static Exception drainQuietly(byte[] blob) {
        try {
            TypedXmlPullParser parser = Xml.newBinaryPullParser();
            parser.setInput(new ByteArrayInputStream(blob), StandardCharsets.UTF_8.name());
            int event;
            int guard = 0;
            do {
                event = parser.nextToken();
            } while (event != XmlPullParser.END_DOCUMENT && ++guard < 10_000);
            return null;
        } catch (IOException | XmlPullParserException expected) {
            return expected;
        } catch (Throwable t) {
            fail("Malformed input surfaced as an unchecked " + t.getClass().getName() + ": " + t);
            throw new AssertionError(t);
        }
    }

    static void writeMagic(@NonNull ByteArrayOutputStream os) {
        os.write(BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0, 0,
                BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0.length);
    }

    static void writeNewInternedString(@NonNull ByteArrayOutputStream os, @NonNull String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        os.write(0xff);
        os.write(0xff);
        os.write((bytes.length >> 8) & 0xff);
        os.write(bytes.length & 0xff);
        os.write(bytes, 0, bytes.length);
    }
}
