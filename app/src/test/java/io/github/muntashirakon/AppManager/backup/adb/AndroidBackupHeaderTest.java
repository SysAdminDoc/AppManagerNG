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
