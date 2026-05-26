// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmBatchRenameUtilsTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-fm-rename");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void createPlan_reservesGeneratedNamesAndReportsConflicts() throws Exception {
        root.createNewFile("photo.jpg", null);
        Path first = root.createNewFile("a.tmp", null);
        Path second = root.createNewFile("b.tmp", null);

        FmBatchRenameUtils.Plan plan = FmBatchRenameUtils.createPlan(
                Arrays.asList(first, second), "photo", "jpg");

        assertTrue(plan.canExecute());
        assertEquals(2, plan.entries.size());
        assertEquals(2, plan.resolvedConflictCount);
        assertEquals("photo (1).jpg", plan.entries.get(0).targetName);
        assertEquals("photo (2).jpg", plan.entries.get(1).targetName);
    }

    @Test
    public void createPlan_rejectsInvalidTargetNames() throws Exception {
        Path file = root.createNewFile("a.tmp", null);

        FmBatchRenameUtils.Plan plan = FmBatchRenameUtils.createPlan(
                Collections.singletonList(file), "folder/name", "txt");

        assertFalse(plan.canExecute());
        assertEquals(1, plan.issues.size());
        assertEquals(FmBatchRenameUtils.IssueType.INVALID_TARGET_NAME, plan.issues.get(0).type);
    }

    @Test
    public void execute_renamesAllAndKeepsOriginalNamesInResults() throws Exception {
        Path first = root.createNewFile("a.tmp", null);
        Path second = root.createNewFile("b.tmp", null);
        FmBatchRenameUtils.Plan plan = FmBatchRenameUtils.createPlan(
                Arrays.asList(first, second), "renamed", "log");

        FmBatchRenameUtils.BatchResult result = FmBatchRenameUtils.execute(plan, null);

        assertFalse(result.interrupted);
        assertEquals(2, result.getSuccessCount());
        assertEquals("a.tmp", result.results.get(0).entry.sourceName);
        assertEquals("renamed.log", result.results.get(0).entry.targetName);
        assertEquals("b.tmp", result.results.get(1).entry.sourceName);
        assertEquals("renamed (2).log", result.results.get(1).entry.targetName);
        assertTrue(root.hasFile("renamed.log"));
        assertTrue(root.hasFile("renamed (2).log"));
        assertFalse(root.hasFile("a.tmp"));
        assertFalse(root.hasFile("b.tmp"));
    }
}
