// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Executes a collection of keyed writes once per distinct key.
 *
 * <p>Callers provide the resolver and writer so the grouping contract can be
 * tested and benchmarked without opening a privileged service.</p>
 */
public final class BatchCommitCoalescer {
    @FunctionalInterface
    public interface Source<K, V> {
        @NonNull
        V resolve(@NonNull K key) throws Exception;
    }

    @FunctionalInterface
    public interface Writer<K, V> {
        void write(@NonNull K key, @NonNull V value) throws Exception;
    }

    public static final class Failure<K> {
        @NonNull
        private final K key;
        @NonNull
        private final Exception error;

        private Failure(@NonNull K key, @NonNull Exception error) {
            this.key = key;
            this.error = error;
        }

        @NonNull
        public K getKey() {
            return key;
        }

        @NonNull
        public Exception getError() {
            return error;
        }
    }

    public static final class Result<K> {
        private final int keyCount;
        private final int valueCount;
        private final int commitCount;
        @NonNull
        private final List<Failure<K>> failures;

        private Result(int keyCount, int valueCount, int commitCount,
                       @NonNull List<Failure<K>> failures) {
            this.keyCount = keyCount;
            this.valueCount = valueCount;
            this.commitCount = commitCount;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public int getKeyCount() {
            return keyCount;
        }

        public int getValueCount() {
            return valueCount;
        }

        public int getCommitCount() {
            return commitCount;
        }

        @NonNull
        public List<Failure<K>> getFailures() {
            return failures;
        }

        public boolean isSuccessful() {
            return failures.isEmpty();
        }
    }

    private BatchCommitCoalescer() {
    }

    public static <K, V> Result<K> executeValues(
            @NonNull Collection<K> keys,
            @NonNull Source<K, V> source,
            @NonNull Writer<K, V> writer,
            @NonNull ValueSizer<V> valueSizer) {
        LinkedHashSet<K> uniqueKeys = new LinkedHashSet<>(keys);
        List<Failure<K>> failures = new ArrayList<>();
        int valueCount = 0;
        int commitCount = 0;
        for (K key : uniqueKeys) {
            try {
                V value = source.resolve(key);
                if (value == null) {
                    throw new IllegalStateException("Batch source returned null for " + key);
                }
                valueCount += valueSizer.size(value);
                ++commitCount;
                writer.write(key, value);
            } catch (Exception e) {
                failures.add(new Failure<>(key, e));
            }
        }
        return new Result<>(uniqueKeys.size(), valueCount, commitCount, failures);
    }

    @FunctionalInterface
    public interface ValueSizer<V> {
        int size(@NonNull V value);
    }
}
