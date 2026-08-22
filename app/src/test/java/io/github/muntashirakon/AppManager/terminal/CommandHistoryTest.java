// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class CommandHistoryTest {
    private CommandHistory mHistory;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        File historyFile = new File(context.getFilesDir(), "terminal_history");
        assertTrue(!historyFile.exists() || historyFile.delete());
        mHistory = new CommandHistory(context);
        mHistory.awaitPendingOperations();
    }

    @After
    public void tearDown() {
        mHistory.awaitPendingOperations();
        mHistory.shutdown();
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
        mHistory.awaitPendingOperations();

        CommandHistory reloaded = new CommandHistory(RuntimeEnvironment.getApplication());
        reloaded.awaitPendingOperations();
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
