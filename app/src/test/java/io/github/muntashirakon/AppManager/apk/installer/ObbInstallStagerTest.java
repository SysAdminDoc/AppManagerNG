// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class ObbInstallStagerTest {
    private static final String OBB_NAME = "main.1.com.example.obb";
    private static final String PATCH_NAME = "patch.1.com.example.obb";

    private Path mTestDir;
    private Path mStagingDir;

    @Before
    public void setUp() throws IOException {
        mTestDir = Paths.get(RoboUtils.getTestBaseDir())
                .createNewDirectory("obb-stager-test-" + UUID.randomUUID());
        mStagingDir = mTestDir.createNewDirectory("staging");
    }

    @After
    public void tearDown() {
        deleteRecursively(mTestDir);
    }

    @Test
    public void stageValidatesTheExtractedGeneration() throws IOException {
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            stager.stage(dir -> {
                write(dir, OBB_NAME, "new-main");
                write(dir, PATCH_NAME, "new-patch");
            });
            assertTrue(stager.isStaged());
            assertEquals(Arrays.asList(OBB_NAME, PATCH_NAME), stager.getStagedNames());
        }
    }

    @Test
    public void stageRejectsAnExtractionThatProducedNothing() throws IOException {
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            assertThrows(IOException.class, () -> stager.stage(dir -> {
            }));
            assertFalse(stager.isStaged());
        }
    }

    @Test
    public void stageRejectsAnEmptyObbFile() throws IOException {
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            assertThrows(IOException.class, () -> stager.stage(dir -> write(dir, OBB_NAME, "")));
            assertFalse(stager.isStaged());
        }
    }

    @Test
    public void failedStagingLeavesThePreviousGenerationIntact() throws IOException {
        Path obbDir = newObbDir("user-0");
        write(obbDir, OBB_NAME, "old-main");
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            assertThrows(IOException.class, () -> stager.stage(dir -> {
                write(dir, OBB_NAME, "partial");
                throw new IOException("extraction blew up");
            }));
        }
        assertEquals("old-main", read(obbDir, OBB_NAME));
        assertArrayEquals(new String[]{OBB_NAME}, obbDir.listFileNames());
    }

    @Test
    public void aStagedGenerationIsNotPublishedUntilActivation() throws IOException {
        Path obbDir = newObbDir("user-0");
        write(obbDir, OBB_NAME, "old-main");
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            stager.stage(dir -> write(dir, OBB_NAME, "new-main"));
            // Install failed or was cancelled: activate() is never called.
            assertEquals("old-main", read(obbDir, OBB_NAME));
        }
        assertEquals("old-main", read(obbDir, OBB_NAME));
    }

    @Test
    public void activationReplacesTheGenerationForEveryUser() throws IOException {
        Path userZero = newObbDir("user-0");
        Path userTen = newObbDir("user-10");
        write(userZero, OBB_NAME, "old-main");
        write(userZero, "stale.obb", "stale");
        write(userTen, OBB_NAME, "old-main-10");
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            stager.stage(dir -> {
                write(dir, OBB_NAME, "new-main");
                write(dir, PATCH_NAME, "new-patch");
            });
            stager.activate(userZero);
            stager.activate(userTen);
        }
        for (Path obbDir : new Path[]{userZero, userTen}) {
            assertEquals("new-main", read(obbDir, OBB_NAME));
            assertEquals("new-patch", read(obbDir, PATCH_NAME));
            String[] names = obbDir.listFileNames();
            Arrays.sort(names);
            assertArrayEquals(new String[]{OBB_NAME, PATCH_NAME}, names);
        }
    }

    @Test
    public void activationFailureRestoresThePreviousGeneration() throws IOException {
        Path obbDir = newObbDir("user-0");
        write(obbDir, OBB_NAME, "old-main");
        write(obbDir, PATCH_NAME, "old-patch");
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            stager.stage(dir -> {
                write(dir, OBB_NAME, "new-main");
                write(dir, PATCH_NAME, "new-patch");
            });
            // Simulate a failure part-way through the copy: the second staged file disappears.
            mStagingDir.findFile(PATCH_NAME).delete();
            assertThrows(IOException.class, () -> stager.activate(obbDir));
        }
        assertEquals("old-main", read(obbDir, OBB_NAME));
        assertEquals("old-patch", read(obbDir, PATCH_NAME));
        String[] names = obbDir.listFileNames();
        Arrays.sort(names);
        assertArrayEquals(new String[]{OBB_NAME, PATCH_NAME}, names);
    }

    @Test
    public void activationFailureForOneUserDoesNotAffectAnother() throws IOException {
        Path userZero = newObbDir("user-0");
        Path userTen = newObbDir("user-10");
        write(userZero, OBB_NAME, "old-main");
        write(userTen, OBB_NAME, "old-main-10");
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            stager.stage(dir -> write(dir, OBB_NAME, "new-main"));
            stager.activate(userZero);
            mStagingDir.findFile(OBB_NAME).delete();
            assertThrows(IOException.class, () -> stager.activate(userTen));
        }
        assertEquals("new-main", read(userZero, OBB_NAME));
        assertEquals("old-main-10", read(userTen, OBB_NAME));
    }

    @Test
    public void anInterruptedActivationIsRolledBackOnTheNextAttempt() throws IOException {
        Path obbDir = newObbDir("user-0");
        // A crash between the rename and the copy leaves a retained file plus a half-written one.
        write(obbDir, OBB_NAME + ObbInstallStager.BACKUP_SUFFIX, "old-main");
        write(obbDir, OBB_NAME, "half-written");
        try (ObbInstallStager stager = new ObbInstallStager(mStagingDir)) {
            stager.stage(dir -> write(dir, OBB_NAME, "new-main"));
            stager.activate(obbDir);
        }
        assertEquals("new-main", read(obbDir, OBB_NAME));
        assertArrayEquals(new String[]{OBB_NAME}, obbDir.listFileNames());
    }

    @Test
    public void closeRemovesTheStagedFiles() throws IOException {
        ObbInstallStager stager = new ObbInstallStager(mStagingDir);
        stager.stage(dir -> write(dir, OBB_NAME, "new-main"));
        stager.close();
        assertFalse(mStagingDir.exists());
        assertThrows(IOException.class, () -> stager.activate(newObbDir("user-0")));
    }

    private Path newObbDir(String name) throws IOException {
        return mTestDir.createNewDirectory(name);
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Path file = dir.findOrCreateFile(name, null);
        try (OutputStream os = file.openOutputStream()) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(Path dir, String name) throws IOException {
        return new String(dir.findFile(name).getContentAsBinary(), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path path) {
        if (path.isDirectory()) {
            for (Path child : path.listFiles()) {
                deleteRecursively(child);
            }
        }
        path.delete();
    }
}
