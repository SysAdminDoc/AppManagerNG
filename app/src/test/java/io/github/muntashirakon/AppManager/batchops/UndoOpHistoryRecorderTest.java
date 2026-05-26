// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UndoOpHistoryRecorderTest {

    private static UndoableActionQueue.Entry entry(int handle, String label, long expiresAt) {
        return new UndoableActionQueue.Entry(handle, label, () -> {}, expiresAt);
    }

    @Test
    public void recordCommittedCarriesEveryField() {
        UndoableActionQueue.Entry e = entry(1, "Uninstall com.example", 5_000L);
        UndoOpHistoryRecorder.OpHistoryEntry row = UndoOpHistoryRecorder.record(
                e, UndoOpHistoryRecorder.Outcome.COMMITTED, 6_000L, null);
        assertEquals(UndoOpHistoryRecorder.OpHistoryEntry.TYPE, row.type);
        assertEquals("Uninstall com.example", row.label);
        assertEquals(UndoOpHistoryRecorder.Outcome.COMMITTED, row.outcome);
        assertEquals(5_000L, row.expiresAtMillis);
        assertEquals(6_000L, row.recordedAtMillis);
        assertNull(row.extraJson);
        assertEquals("committed", row.statusLabel());
    }

    @Test
    public void recordCancelledFlagsTheUndoOutcome() {
        UndoableActionQueue.Entry e = entry(2, "Freeze com.example", 3_500L);
        UndoOpHistoryRecorder.OpHistoryEntry row = UndoOpHistoryRecorder.record(
                e, UndoOpHistoryRecorder.Outcome.CANCELLED, 3_400L, "{\"by\":\"user\"}");
        assertEquals(UndoOpHistoryRecorder.Outcome.CANCELLED, row.outcome);
        assertEquals("cancelled", row.statusLabel());
        assertEquals("{\"by\":\"user\"}", row.extraJson);
    }

    @Test
    public void recordFlushedOnShutdownFlagsShutdownOutcome() {
        UndoableActionQueue.Entry e = entry(3, "Clear data", 10_000L);
        UndoOpHistoryRecorder.OpHistoryEntry row = UndoOpHistoryRecorder.record(
                e, UndoOpHistoryRecorder.Outcome.FLUSHED_ON_SHUTDOWN, 9_000L, null);
        assertEquals("flushed", row.statusLabel());
    }

    @Test
    public void recordCommittedBatchMapsEveryEntryAndSkipsNulls() {
        List<UndoableActionQueue.Entry> drained = new ArrayList<>();
        drained.add(entry(1, "a", 1_000L));
        drained.add(null);
        drained.add(entry(2, "b", 2_000L));
        List<UndoOpHistoryRecorder.OpHistoryEntry> rows =
                UndoOpHistoryRecorder.recordCommittedBatch(drained, 500L);
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).label);
        assertEquals("b", rows.get(1).label);
        for (UndoOpHistoryRecorder.OpHistoryEntry r : rows) {
            assertEquals(UndoOpHistoryRecorder.Outcome.COMMITTED, r.outcome);
            assertEquals(500L, r.recordedAtMillis);
        }
    }

    @Test
    public void recordShutdownFlushMapsEntriesWithFlushOutcome() {
        List<UndoableActionQueue.Entry> drained = Arrays.asList(
                entry(1, "uninstall com.a", 1_000L),
                entry(2, "uninstall com.b", 2_000L));
        List<UndoOpHistoryRecorder.OpHistoryEntry> rows =
                UndoOpHistoryRecorder.recordShutdownFlush(drained, 0L);
        assertEquals(2, rows.size());
        for (UndoOpHistoryRecorder.OpHistoryEntry r : rows) {
            assertEquals(UndoOpHistoryRecorder.Outcome.FLUSHED_ON_SHUTDOWN, r.outcome);
            assertEquals("flushed", r.statusLabel());
        }
    }

    @Test
    public void batchHelpersAcceptEmptyInputs() {
        assertTrue(UndoOpHistoryRecorder
                .recordCommittedBatch(new ArrayList<>(), 0L).isEmpty());
        assertTrue(UndoOpHistoryRecorder
                .recordShutdownFlush(new ArrayList<>(), 0L).isEmpty());
    }

    @Test
    public void statusLabelIsStableAcrossOutcomes() {
        // Pin literals so the history UI's filter-chip code can match
        // against constants without ambiguity.
        UndoableActionQueue.Entry e = entry(1, "x", 1L);
        assertEquals("committed", UndoOpHistoryRecorder.record(e,
                UndoOpHistoryRecorder.Outcome.COMMITTED, 0L, null).statusLabel());
        assertEquals("cancelled", UndoOpHistoryRecorder.record(e,
                UndoOpHistoryRecorder.Outcome.CANCELLED, 0L, null).statusLabel());
        assertEquals("flushed", UndoOpHistoryRecorder.record(e,
                UndoOpHistoryRecorder.Outcome.FLUSHED_ON_SHUTDOWN, 0L, null).statusLabel());
    }

    @Test
    public void typeConstantMatchesEntryDefault() {
        UndoableActionQueue.Entry e = entry(1, "x", 1L);
        UndoOpHistoryRecorder.OpHistoryEntry row = UndoOpHistoryRecorder.record(e,
                UndoOpHistoryRecorder.Outcome.COMMITTED, 0L, null);
        assertEquals("destructive_op_v1", row.type);
        assertEquals(UndoOpHistoryRecorder.OpHistoryEntry.TYPE, row.type);
    }
}
