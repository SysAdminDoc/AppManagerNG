// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CommandHistoryTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CommandHistory mHistory;

    @Before
    public void setUp() {
        mHistory = new CommandHistory(RuntimeEnvironment.getApplication());
    }

    @Test
    public void emptyHistoryReturnsNull() {
        assertNull(mHistory.navigateUp());
        assertNull(mHistory.navigateDown());
    }

    @Test
    public void addAndNavigateUp() {
        mHistory.add("ls");
        mHistory.add("pwd");
        mHistory.add("whoami");

        assertEquals("whoami", mHistory.navigateUp());
        assertEquals("pwd", mHistory.navigateUp());
        assertEquals("ls", mHistory.navigateUp());
        assertNull(mHistory.navigateUp());
    }

    @Test
    public void navigateDownReturnsEmptyAtEnd() {
        mHistory.add("ls");
        mHistory.add("pwd");

        assertEquals("pwd", mHistory.navigateUp());
        assertEquals("ls", mHistory.navigateUp());
        assertEquals("pwd", mHistory.navigateDown());
        assertEquals("", mHistory.navigateDown());
    }

    @Test
    public void duplicateAdjacentCommandsNotStored() {
        mHistory.add("ls");
        mHistory.add("ls");
        mHistory.add("ls");

        assertEquals(1, mHistory.size());
        assertEquals("ls", mHistory.navigateUp());
        assertNull(mHistory.navigateUp());
    }

    @Test
    public void blankCommandsIgnored() {
        mHistory.add("");
        mHistory.add("  ");
        mHistory.add("\t");

        assertEquals(0, mHistory.size());
    }

    @Test
    public void persistenceAcrossInstances() {
        mHistory.add("echo hello");
        mHistory.add("exit");

        CommandHistory reloaded = new CommandHistory(RuntimeEnvironment.getApplication());
        assertEquals(2, reloaded.size());
        assertEquals("exit", reloaded.navigateUp());
        assertEquals("echo hello", reloaded.navigateUp());
    }

    @Test
    public void resetPositionAfterAdd() {
        mHistory.add("ls");
        mHistory.add("pwd");

        assertEquals("pwd", mHistory.navigateUp());
        mHistory.add("whoami");
        assertEquals("whoami", mHistory.navigateUp());
        assertEquals("pwd", mHistory.navigateUp());
    }
}
