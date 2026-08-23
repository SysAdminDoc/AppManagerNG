// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Process;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class AppOpsUidGuardTest {
    private static final int OP_CAMERA = 26;
    private static final int OP_RECORD_AUDIO = 27;

    @Test
    public void singleApplicationUidDoesNotNeedAReviewedPlan() {
        AppOpsUidGuard.requireAllowed(Process.FIRST_APPLICATION_UID + 42, "com.example.one",
                new int[]{OP_CAMERA}, AppOpsUidGuard.MutationSource.DIRECT, null,
                uid -> new String[]{"com.example.one"});
    }

    @Test
    public void sharedApplicationUidFailsClosedWithoutPlan() {
        AppOpsUidGuard.UnsafeUidMutationException error = assertThrows(
                AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.requireAllowed(Process.FIRST_APPLICATION_UID + 42,
                        "com.example.one", new int[]{OP_CAMERA},
                        AppOpsUidGuard.MutationSource.IGNORE_DANGEROUS, null,
                        uid -> new String[]{"com.example.two", "com.example.one"}));

        assertEquals(Arrays.asList("com.example.one", "com.example.two"),
                error.getImpact().getAffectedPackages());
        assertEquals(Collections.singletonList(OP_CAMERA), error.getImpact().getOperations());
        assertTrue(error.getImpact().requiresReview());
    }

    @Test
    public void systemUidFailsClosedEvenForOneResolvedPackage() {
        AppOpsUidGuard.UnsafeUidMutationException error = assertThrows(
                AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.requireAllowed(1000, "android",
                        new int[]{OP_CAMERA}, AppOpsUidGuard.MutationSource.RESET, null,
                        uid -> new String[]{"android"}));

        assertTrue(error.getImpact().isSystemUid());
    }

    @Test
    public void reviewedPlanMustNameEveryPackageAndOperation() {
        AppOpsUidGuard.Impact impact = AppOpsUidGuard.inspect(
                Process.FIRST_APPLICATION_UID + 42, "com.example.one",
                new int[]{OP_CAMERA, OP_RECORD_AUDIO}, AppOpsUidGuard.MutationSource.BATCH,
                uid -> new String[]{"com.example.one", "com.example.two"});

        assertThrows(AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.createReviewedPlan(impact,
                        Collections.singletonList("com.example.one"),
                        new int[]{OP_CAMERA, OP_RECORD_AUDIO}, true));
        assertThrows(AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.createReviewedPlan(impact,
                        Arrays.asList("com.example.one", "com.example.two"),
                        new int[]{OP_CAMERA}, true));
        assertThrows(AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.createReviewedPlan(impact,
                        Arrays.asList("com.example.one", "com.example.two"),
                        new int[]{OP_CAMERA, OP_RECORD_AUDIO}, false));
    }

    @Test
    public void completeReviewedPlanAuthorizesTheCurrentUidWideEffect() {
        AppOpsUidGuard.PackageResolver resolver = uid ->
                new String[]{"com.example.two", "com.example.one"};
        AppOpsUidGuard.Impact impact = AppOpsUidGuard.inspect(
                Process.FIRST_APPLICATION_UID + 42, "com.example.one",
                new int[]{OP_RECORD_AUDIO, OP_CAMERA}, AppOpsUidGuard.MutationSource.BATCH,
                resolver);
        AppOpsUidGuard.ReviewedPlan plan = AppOpsUidGuard.createReviewedPlan(impact,
                Arrays.asList("com.example.one", "com.example.two"),
                new int[]{OP_CAMERA, OP_RECORD_AUDIO}, true);

        AppOpsUidGuard.requireAllowed(Process.FIRST_APPLICATION_UID + 42, "com.example.one",
                new int[]{OP_CAMERA}, AppOpsUidGuard.MutationSource.BATCH, plan, resolver);
        assertFalse(impact.isSystemUid());
    }

    @Test
    public void packageLookupFailureAndStalePlanFailClosed() {
        assertThrows(AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.requireAllowed(Process.FIRST_APPLICATION_UID + 42,
                        "com.example.one", new int[]{OP_CAMERA},
                        AppOpsUidGuard.MutationSource.RESTORE, null,
                        uid -> { throw new IllegalStateException("binder unavailable"); }));

        AppOpsUidGuard.PackageResolver original = uid ->
                new String[]{"com.example.one", "com.example.two"};
        AppOpsUidGuard.Impact impact = AppOpsUidGuard.inspect(
                Process.FIRST_APPLICATION_UID + 42, "com.example.one",
                new int[]{OP_CAMERA}, AppOpsUidGuard.MutationSource.RULE_IMPORT, original);
        AppOpsUidGuard.ReviewedPlan plan = AppOpsUidGuard.createReviewedPlan(impact,
                Arrays.asList("com.example.one", "com.example.two"),
                new int[]{OP_CAMERA}, true);

        assertThrows(AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> AppOpsUidGuard.requireAllowed(Process.FIRST_APPLICATION_UID + 42,
                        "com.example.one", new int[]{OP_CAMERA},
                        AppOpsUidGuard.MutationSource.RULE_IMPORT, plan,
                        uid -> new String[]{"com.example.one", "com.example.two", "com.example.three"}));
    }

    @Test
    public void allMutationSourcesRetainTheSameFailClosedBoundary() {
        for (AppOpsUidGuard.MutationSource source : AppOpsUidGuard.MutationSource.values()) {
            assertThrows(source.name(), AppOpsUidGuard.UnsafeUidMutationException.class,
                    () -> AppOpsUidGuard.requireAllowed(Process.FIRST_APPLICATION_UID + 7,
                            "com.example.one", new int[]{OP_CAMERA}, source, null,
                            uid -> new String[]{"com.example.one", "com.example.two"}));
        }
    }

    @Test
    public void resetIgnoreBatchImportAndRestoreUseTheCentralBoundary() throws IOException {
        assertSourceContains("app/src/main/java/io/github/muntashirakon/AppManager/compat/"
                        + "AppOpsManagerCompat.java",
                "AppOpsUidGuard.requireAllowed", "mAppOpsService.setUidMode",
                "MutationSource.RESET");
        assertSourceContains("app/src/main/java/io/github/muntashirakon/AppManager/details/"
                        + "AppDetailsViewModel.java",
                "MutationSource.IGNORE_DANGEROUS");
        assertSourceContains("app/src/main/java/io/github/muntashirakon/AppManager/batchops/"
                        + "BatchOpsManager.java",
                "MutationSource.BATCH", "createReviewedPlan");
        assertSourceContains("app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/"
                        + "ComponentsBlocker.java",
                "MutationSource.RULE_IMPORT");
        assertSourceContains("app/src/main/java/io/github/muntashirakon/AppManager/backup/RestoreOp.java",
                "MutationSource.RESTORE");
    }

    private static void assertSourceContains(String relativePath, String... needles) throws IOException {
        String source = new String(Files.readAllBytes(repoRoot().resolve(relativePath)), StandardCharsets.UTF_8);
        for (String needle : needles) {
            assertTrue(relativePath + " must contain " + needle, source.contains(needle));
        }
    }

    private static Path repoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve("settings.gradle"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new IllegalStateException("Could not find repository root.");
        }
        return cursor;
    }
}
