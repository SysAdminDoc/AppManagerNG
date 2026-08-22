// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.compat.BatchCommitCoalescer;

/**
 * Runs a component operation once for each distinct package/user pair.
 *
 * <p>The package writer owns one rules transaction and must commit all of the
 * supplied components before returning. Keeping the grouping here makes the
 * batch contract explicit and gives benchmarks and host tests a small seam
 * that does not need a privileged device.</p>
 */
public final class ComponentBlockingBatch {
    @FunctionalInterface
    public interface ComponentSource {
        @NonNull
        Map<String, RuleType> resolve(@NonNull UserPackagePair pair) throws Exception;
    }

    @FunctionalInterface
    public interface PackageWriter {
        void write(@NonNull UserPackagePair pair,
                   @NonNull Map<String, RuleType> components) throws Exception;
    }

    public static final class Failure {
        @NonNull
        private final UserPackagePair pair;
        @NonNull
        private final Exception error;

        private Failure(@NonNull UserPackagePair pair, @NonNull Exception error) {
            this.pair = pair;
            this.error = error;
        }

        @NonNull
        public UserPackagePair getPair() {
            return pair;
        }

        @NonNull
        public Exception getError() {
            return error;
        }
    }

    public static final class Result {
        private final int packageCount;
        private final int componentCount;
        private final int commitCount;
        @NonNull
        private final List<Failure> failures;

        private Result(int packageCount, int componentCount, int commitCount,
                       @NonNull List<Failure> failures) {
            this.packageCount = packageCount;
            this.componentCount = componentCount;
            this.commitCount = commitCount;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public int getPackageCount() {
            return packageCount;
        }

        public int getComponentCount() {
            return componentCount;
        }

        /** Number of package-writer invocations, one for each committed transaction attempt. */
        public int getCommitCount() {
            return commitCount;
        }

        @NonNull
        public List<Failure> getFailures() {
            return failures;
        }

        public boolean isSuccessful() {
            return failures.isEmpty();
        }
    }

    private ComponentBlockingBatch() {
    }

    @WorkerThread
    @NonNull
    public static Result execute(@NonNull Collection<UserPackagePair> pairs,
                                 @NonNull ComponentSource source,
                                 @NonNull PackageWriter writer) {
        BatchCommitCoalescer.Result<UserPackagePair> result =
                BatchCommitCoalescer.executeValues(pairs, source::resolve, writer::write,
                        Map::size);
        List<Failure> failures = new ArrayList<>(result.getFailures().size());
        for (BatchCommitCoalescer.Failure<UserPackagePair> failure : result.getFailures()) {
            failures.add(new Failure(failure.getKey(), failure.getError()));
        }
        return new Result(result.getKeyCount(), result.getValueCount(), result.getCommitCount(),
                failures);
    }
}
