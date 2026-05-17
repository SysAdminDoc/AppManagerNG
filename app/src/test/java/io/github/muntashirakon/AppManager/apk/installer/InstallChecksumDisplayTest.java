// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InstallChecksumDisplayTest {
    @Test
    public void toHexUsesLowercaseTwoDigitBytes() {
        assertEquals("000f10ff", PackageInstallerCompat.toHex(new byte[]{0x00, 0x0f, 0x10, (byte) 0xff}));
    }

    @Test
    public void formatSha256ForDisplayGroupsEightCharacterChunks() {
        String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        assertEquals("01234567 89abcdef 01234567 89abcdef 01234567 89abcdef 01234567 89abcdef",
                PackageInstallerActivity.formatSha256ForDisplay(sha256));
    }
}
