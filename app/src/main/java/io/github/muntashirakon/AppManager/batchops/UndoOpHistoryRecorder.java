// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the JVM-clean {@link UndoableActionQueue} to a future
 * {@code OpHistory} row write (T21-F follow-up).
 *
 * <p>{@link UndoableActionQueue.Entry} carries the label, the deferred
 * commit, and the deadline - everything an audit row needs except the
 * status (cancelled / committed) and a structured timestamp. This recorder
 * is the pure-function shape that builds an op-history row from a queue
 * entry plus an outcome, so the Android-side {@code OpHistoryManager}
 * caller has only one line to wire ("record(entry, OUTCOME_COMMITTED,
 * nowMillis)").
 *
 * <p>The recorder does not touch Room - that's the Android side's job. It
 * just produces the {@link OpHistoryEntry} value object and exposes
 * convenience converters for cancel / commit batches.
 */
public final class UndoOpHistoryRecorder {

    public enum Outcome {
        /** User pressed Undo before the deadline; the commit never ran. */
        CANCELLED,
        /** Deadline elapsed, the deferred commit was run by the caller. */
        COMMITTED,
        /** The surface (Activity / Service) shut down while the entry was pending. */
        FLUSHED_ON_SHUTDOWN
    }

    /** Stable row-shape value object. Mirrors {@code db.entity.OpHistory} fields. */
    public static final class OpHistoryEntry {
        /** Stable identifier for the T21-F "undoable destructive op" family. */
        public static final String TYPE = "destructive_op_v1";

        @NonNull
        public final String type;
        @NonNull
        public final String label;
        @NonNull
        public final Outcome outcome;
        public final long expiresAtMillis;
        public final long recordedAtMillis;
        @Nullable
        public final String extraJson;

        OpHistoryEntry(@NonNull String type, @NonNull String label,
                       @NonNull Outcome outcome, long expiresAtMillis,
                       long recordedAtMillis, @Nullable String extraJson) {
            this.type = type;
            this.label = label;
            this.outcome = outcome;
            this.expiresAtMillis = expiresAtMillis;
            this.recordedAtMillis = recordedAtMillis;
            this.extraJson = extraJson;
        }

        /**
         * Status string for {@code OpHistory.status}. Format is stable
         * across versions so the history UI's filter chip can match against
         * the literal text.
         */
        @NonNull
        public String statusLabel() {
            switch (outcome) {
                case CANCELLED:
                    return "cancelled";
                case COMMITTED:
                    return "committed";
                case FLUSHED_ON_SHUTDOWN:
                    return "flushed";
                default:
                    return "unknown";
            }
        }
    }

    private UndoOpHistoryRecorder() {
    }

    /** Build a single op-history row from one queue entry + outcome. */
    @NonNull
    public static OpHistoryEntry record(@NonNull UndoableActionQueue.Entry entry,
                                        @NonNull Outcome outcome,
                                        long recordedAtMillis,
                                        @Nullable String extraJson) {
        return new OpHistoryEntry(OpHistoryEntry.TYPE,
                entry.label, outcome,
                entry.expiresAtMillis, recordedAtMillis,
                extraJson);
    }

    /**
     * Convenience batch converter for a {@code pollExpired} drain - every
     * returned entry maps to a {@link Outcome#COMMITTED} row stamped at
     * {@code recordedAtMillis}.
     */
    @NonNull
    public static List<OpHistoryEntry> recordCommittedBatch(
            @NonNull List<UndoableActionQueue.Entry> drainedEntries,
            long recordedAtMillis) {
        List<OpHistoryEntry> rows = new ArrayList<>(drainedEntries.size());
        for (UndoableActionQueue.Entry e : drainedEntries) {
            if (e != null) {
                rows.add(record(e, Outcome.COMMITTED, recordedAtMillis, null));
            }
        }
        return rows;
    }

    /**
     * Convenience batch converter for a {@code drainAll} on shutdown -
     * every returned entry maps to a {@link Outcome#FLUSHED_ON_SHUTDOWN}
     * row.
     */
    @NonNull
    public static List<OpHistoryEntry> recordShutdownFlush(
            @NonNull List<UndoableActionQueue.Entry> drainedEntries,
            long recordedAtMillis) {
        List<OpHistoryEntry> rows = new ArrayList<>(drainedEntries.size());
        for (UndoableActionQueue.Entry e : drainedEntries) {
            if (e != null) {
                rows.add(record(e, Outcome.FLUSHED_ON_SHUTDOWN, recordedAtMillis, null));
            }
        }
        return rows;
    }
}
