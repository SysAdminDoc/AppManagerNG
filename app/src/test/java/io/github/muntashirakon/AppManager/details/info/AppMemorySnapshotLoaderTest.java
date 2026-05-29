// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AppMemorySnapshotLoaderTest {

    @Test
    public void firstPid_singlePid() {
        assertEquals("1234", AppMemorySnapshotLoader.firstPid("1234"));
    }

    @Test
    public void firstPid_singlePidWithTrailingNewline() {
        assertEquals("1234", AppMemorySnapshotLoader.firstPid("1234\n"));
    }

    @Test
    public void firstPid_multiplePidsTakesFirst() {
        assertEquals("1234", AppMemorySnapshotLoader.firstPid("1234 5678 9012"));
    }

    @Test
    public void firstPid_leadingWhitespace() {
        assertEquals("42", AppMemorySnapshotLoader.firstPid("   42  43\n"));
    }

    @Test
    public void firstPid_nullInput() {
        assertNull(AppMemorySnapshotLoader.firstPid(null));
    }

    @Test
    public void firstPid_emptyInput() {
        assertNull(AppMemorySnapshotLoader.firstPid(""));
        assertNull(AppMemorySnapshotLoader.firstPid("   \n  "));
    }

    @Test
    public void firstPid_nonNumericRejected() {
        // pidof never emits this, but a shell error string must not be treated as a PID.
        assertNull(AppMemorySnapshotLoader.firstPid("no such process"));
        assertNull(AppMemorySnapshotLoader.firstPid("12a3"));
    }

    @Test
    public void provenance_perRegionIsVirtual() {
        assertEquals("via /proc/maps · virtual",
                AppMemorySnapshotLoader.provenanceFor(MemorySnapshotComposer.FieldSource.PROC_MAPS));
    }

    @Test
    public void provenance_procStatus() {
        assertEquals("via /proc/status",
                AppMemorySnapshotLoader.provenanceFor(MemorySnapshotComposer.FieldSource.PROC_STATUS));
    }

    @Test
    public void provenance_meminfoHasNoSuffix() {
        // dumpsys meminfo is the canonical PSS source; no provenance suffix needed.
        assertNull(AppMemorySnapshotLoader.provenanceFor(MemorySnapshotComposer.FieldSource.DUMPSYS_MEMINFO));
        assertNull(AppMemorySnapshotLoader.provenanceFor(MemorySnapshotComposer.FieldSource.UNAVAILABLE));
    }
}
