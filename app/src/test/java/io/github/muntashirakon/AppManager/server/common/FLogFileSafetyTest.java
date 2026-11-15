// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.system.OsConstants;
import android.system.StructStat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The server log sits in /data/local/tmp, which the shell user owns, and root opens it. Only a
 * plain file with one name may be written. The link case itself (the open never follows one)
 * needs a real kernel and is checked on a rooted emulator.
 */
@RunWith(RobolectricTestRunner.class)
public class FLogFileSafetyTest {
    @Test
    public void aPlainFileWithOneNameIsWritten() {
        assertTrue(FLog.isPrivateLogFile(stat(OsConstants.S_IFREG | 0600, 1)));
        assertTrue(FLog.isPrivateLogFile(stat(OsConstants.S_IFREG | 0644, 1)));
    }

    @Test
    public void aHardLinkToAnotherFileIsLeftAlone() {
        // The shell user can link a file it may write, say another app's world-writable file,
        // into the log's place, and root would empty it and hand it to the shell user
        assertFalse(FLog.isPrivateLogFile(stat(OsConstants.S_IFREG | 0600, 2)));
    }

    @Test
    public void anythingButAPlainFileIsLeftAlone() {
        int[] types = {OsConstants.S_IFIFO, OsConstants.S_IFCHR, OsConstants.S_IFBLK, OsConstants.S_IFSOCK,
                OsConstants.S_IFDIR, OsConstants.S_IFLNK};
        for (int type : types) {
            assertFalse(Integer.toOctalString(type), FLog.isPrivateLogFile(stat(type | 0600, 1)));
        }
    }

    private static StructStat stat(int mode, long links) {
        return new StructStat(0, 0, mode, links, 2000, 2000, 0, 0, 0, 0, 0, 4096, 0);
    }
}
