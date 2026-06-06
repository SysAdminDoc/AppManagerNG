// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class AndroidBackupHeaderTest {
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
