// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.safety;

import android.os.Process;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;

/**
 * Fail-closed boundary for AppOps mutations that Android applies to a whole UID.
 */
public final class AppOpsUidGuard {
    private AppOpsUidGuard() {}

    public enum MutationSource {
        DIRECT,
        RESET,
        IGNORE_DANGEROUS,
        BATCH,
        RULE_IMPORT,
        RESTORE
    }

    public interface PackageResolver {
        @Nullable
        String[] getPackagesForUid(int uid) throws Exception;
    }

    public static final class Impact {
        private final int mUid;
        @NonNull
        private final String mRequestedPackage;
        @NonNull
        private final List<String> mAffectedPackages;
        @NonNull
        private final List<Integer> mOperations;
        @NonNull
        private final MutationSource mSource;
        private final boolean mSystemUid;

        private Impact(int uid, @NonNull String requestedPackage,
                       @NonNull List<String> affectedPackages,
                       @NonNull List<Integer> operations,
                       @NonNull MutationSource source, boolean systemUid) {
            mUid = uid;
            mRequestedPackage = requestedPackage;
            mAffectedPackages = Collections.unmodifiableList(affectedPackages);
            mOperations = Collections.unmodifiableList(operations);
            mSource = source;
            mSystemUid = systemUid;
        }

        public int getUid() {
            return mUid;
        }

        @NonNull
        public String getRequestedPackage() {
            return mRequestedPackage;
        }

        @NonNull
        public List<String> getAffectedPackages() {
            return mAffectedPackages;
        }

        @NonNull
        public List<Integer> getOperations() {
            return mOperations;
        }

        @NonNull
        public MutationSource getSource() {
            return mSource;
        }

        public boolean isSystemUid() {
            return mSystemUid;
        }

        public boolean requiresReview() {
            return mSystemUid || mAffectedPackages.size() > 1;
        }
    }

    public static final class ReviewedPlan {
        @NonNull
        private final Impact mImpact;

        private ReviewedPlan(@NonNull Impact impact) {
            mImpact = impact;
        }
    }

    public static final class UnsafeUidMutationException extends SecurityException {
        @NonNull
        private final Impact mImpact;

        private UnsafeUidMutationException(@NonNull String message, @NonNull Impact impact) {
            super(message);
            mImpact = impact;
        }

        @NonNull
        public Impact getImpact() {
            return mImpact;
        }
    }

    @WorkerThread
    @NonNull
    public static Impact inspect(int uid, @NonNull String requestedPackage,
                                 @NonNull int[] operations,
                                 @NonNull MutationSource source) {
        return inspect(uid, requestedPackage, operations, source,
                resolvedUid -> PackageManagerCompat.getPackageManager().getPackagesForUid(resolvedUid));
    }

    @VisibleForTesting
    @NonNull
    static Impact inspect(int uid, @NonNull String requestedPackage,
                          @NonNull int[] operations, @NonNull MutationSource source,
                          @NonNull PackageResolver resolver) {
        List<Integer> normalizedOperations = normalizeOperations(operations);
        boolean systemUid = uid >= 0
                && UserHandleHidden.getAppId(uid) < Process.FIRST_APPLICATION_UID;
        List<String> packages;
        try {
            packages = normalizePackages(resolver.getPackagesForUid(uid));
        } catch (Exception e) {
            Impact impact = unresolvedImpact(uid, requestedPackage, normalizedOperations, source, systemUid);
            throw unsafe("Could not resolve every package sharing UID " + uid + '.', impact);
        }
        if (uid < 0 || packages.isEmpty() || !packages.contains(requestedPackage)) {
            Impact impact = unresolvedImpact(uid, requestedPackage, normalizedOperations, source, systemUid);
            throw unsafe("Package lookup did not confirm the requested UID owner.", impact);
        }
        return new Impact(uid, requestedPackage, packages, normalizedOperations, source, systemUid);
    }

    @WorkerThread
    @NonNull
    public static ReviewedPlan createReviewedPlan(int uid, @NonNull String requestedPackage,
                                                   @NonNull int[] operations,
                                                   @NonNull MutationSource source,
                                                   @NonNull Collection<String> reviewedPackages,
                                                   boolean includesUidWideEffect) {
        Impact impact = inspect(uid, requestedPackage, operations, source);
        return createReviewedPlan(impact, reviewedPackages, operations, includesUidWideEffect);
    }

    @VisibleForTesting
    @NonNull
    static ReviewedPlan createReviewedPlan(@NonNull Impact impact,
                                           @NonNull Collection<String> reviewedPackages,
                                           @NonNull int[] reviewedOperations,
                                           boolean includesUidWideEffect) {
        Set<String> packageSet = new LinkedHashSet<>(reviewedPackages);
        Set<Integer> operationSet = new LinkedHashSet<>(normalizeOperations(reviewedOperations));
        if (!includesUidWideEffect
                || !packageSet.containsAll(impact.getAffectedPackages())
                || !operationSet.containsAll(impact.getOperations())) {
            throw unsafe("The reviewed operation plan does not include the complete UID-wide effect.", impact);
        }
        return new ReviewedPlan(impact);
    }

    @WorkerThread
    public static void requireAllowed(int uid, @NonNull String requestedPackage,
                                      @NonNull int[] operations,
                                      @NonNull MutationSource source,
                                      @Nullable ReviewedPlan reviewedPlan) {
        requireAllowed(uid, requestedPackage, operations, source, reviewedPlan,
                resolvedUid -> PackageManagerCompat.getPackageManager().getPackagesForUid(resolvedUid));
    }

    @VisibleForTesting
    static void requireAllowed(int uid, @NonNull String requestedPackage,
                               @NonNull int[] operations, @NonNull MutationSource source,
                               @Nullable ReviewedPlan reviewedPlan,
                               @NonNull PackageResolver resolver) {
        Impact currentImpact = inspect(uid, requestedPackage, operations, source, resolver);
        if (!currentImpact.requiresReview()) {
            return;
        }
        if (reviewedPlan == null || !matches(reviewedPlan.mImpact, currentImpact)) {
            throw unsafe("A shared or system UID requires a complete reviewed operation plan.", currentImpact);
        }
    }

    @Nullable
    public static Impact findImpact(@Nullable Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> visited = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            if (current instanceof UnsafeUidMutationException) {
                return ((UnsafeUidMutationException) current).getImpact();
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean matches(@NonNull Impact reviewed, @NonNull Impact current) {
        return reviewed.getUid() == current.getUid()
                && reviewed.getSource() == current.getSource()
                && reviewed.getAffectedPackages().equals(current.getAffectedPackages())
                && reviewed.getOperations().containsAll(current.getOperations());
    }

    @NonNull
    private static List<String> normalizePackages(@Nullable String[] packages) {
        if (packages == null) {
            return Collections.emptyList();
        }
        Set<String> packageSet = new LinkedHashSet<>();
        for (String packageName : packages) {
            if (packageName != null && !packageName.trim().isEmpty()) {
                packageSet.add(packageName);
            }
        }
        List<String> normalized = new ArrayList<>(packageSet);
        Collections.sort(normalized);
        return normalized;
    }

    @NonNull
    private static List<Integer> normalizeOperations(@NonNull int[] operations) {
        if (operations.length == 0) {
            throw new IllegalArgumentException("At least one AppOps operation is required.");
        }
        Set<Integer> operationSet = new LinkedHashSet<>();
        for (int operation : operations) {
            operationSet.add(operation);
        }
        List<Integer> normalized = new ArrayList<>(operationSet);
        Collections.sort(normalized);
        return normalized;
    }

    @NonNull
    private static Impact unresolvedImpact(int uid, @NonNull String requestedPackage,
                                           @NonNull List<Integer> operations,
                                           @NonNull MutationSource source, boolean systemUid) {
        return new Impact(uid, requestedPackage,
                new ArrayList<>(Arrays.asList(requestedPackage)),
                new ArrayList<>(operations), source, systemUid);
    }

    @NonNull
    private static UnsafeUidMutationException unsafe(@NonNull String message, @NonNull Impact impact) {
        return new UnsafeUidMutationException(message, impact);
    }
}
