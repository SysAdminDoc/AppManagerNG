// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;

public class AndroidBackupHeaderTest {
    @Test
    public void parseHeaderIntAcceptsTrimmedValues() throws Exception {
        assertEquals(5, AndroidBackupHeader.parseHeaderInt(" 5 ", "backup file version"));
    }

    @Test
    public void parseHeaderIntRejectsMalformedValuesAsIoException() {
        assertThrows(IOException.class, () ->
                AndroidBackupHeader.parseHeaderInt("not-a-number", "backup file version"));
        assertThrows(IOException.class, () ->
                AndroidBackupHeader.parseHeaderInt("999999999999999999999", "PBKDF2 rounds"));
    }

    @Test
    public void parseCompressionFlagAcceptsOnlyDefinedValues() throws Exception {
        assertEquals(false, AndroidBackupHeader.parseCompressionFlag(" 0 "));
        assertEquals(true, AndroidBackupHeader.parseCompressionFlag("1"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.parseCompressionFlag("2"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.parseCompressionFlag("-1"));
    }

    @Test
    public void readKeyBlobSegmentRejectsMalformedLengths() throws Exception {
        int[] offset = {0};
        assertArrayEquals(new byte[]{1, 2}, AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{2, 1, 2, 1, 3}, offset, "encryption IV"));
        assertEquals(3, offset[0]);

        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[0], new int[]{0}, "encryption IV"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{0}, new int[]{0}, "encryption IV"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{3, 1, 2}, new int[]{0}, "encryption IV"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{1, 2}, new int[]{2}, "encryption key"));
    }

    @Test
    public void hexToByteArray_roundTripsHeaderBytes() {
        byte[] data = {0x00, 0x0f, (byte) 0xa0, (byte) 0xff};

        assertArrayEquals(data, AndroidBackupHeader.hexToByteArray(AndroidBackupHeader.byteArrayToHex(data)));
    }

    @Test
    public void hexToByteArray_rejectsOddLengthInput() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackupHeader.hexToByteArray("abc"));
    }

    @Test
    public void hexToByteArray_rejectsNonHexInput() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackupHeader.hexToByteArray("00zz"));
    }

    @Test
    public void hexToByteArray_rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackupHeader.hexToByteArray(null));
    }
}
