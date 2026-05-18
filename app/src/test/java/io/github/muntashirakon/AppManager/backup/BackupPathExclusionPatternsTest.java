// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackupPathExclusionPatternsTest {
    @Test
    public void parseNormalizesAndDropsEmptyLines() {
        assertArrayEquals(new String[]{"**/cache/**", "databases/*.db-journal"},
                BackupPathExclusionPatterns.parse("  **\\cache\\**  \n\n# comment\n/databases/*.db-journal"));
    }

    @Test
    public void defaultPatternsExcludeThumbnailFolders() {
        assertTrue(BackupPathExclusionPatterns.isExcluded("Pictures/.thumbnails/thumb.jpg", true));
        assertTrue(BackupPathExclusionPatterns.isExcluded(".thumbnails/thumb.jpg", true));
        assertFalse(BackupPathExclusionPatterns.isExcluded("Pictures/photo.jpg", true));
    }

    @Test
    public void cacheDefaultsRespectBackupCacheFlag() {
        assertTrue(BackupPathExclusionPatterns.isExcluded("cache/state.bin", false));
        assertTrue(BackupPathExclusionPatterns.isExcluded("nested/cache/state.bin", false));
        assertFalse(BackupPathExclusionPatterns.isExcluded("nested/cache/state.bin", true));
    }

    @Test
    public void customGlobsMatchSingleDirectorySegment() {
        String[] globs = {"databases/*.db-journal"};

        assertTrue(BackupPathExclusionPatterns.isExcluded("databases/main.db-journal", true, globs));
        assertFalse(BackupPathExclusionPatterns.isExcluded("databases/nested/main.db-journal", true, globs));
    }

    @Test
    public void customDoubleStarMatchesAnyDirectoryDepth() {
        String[] globs = {"**/cache/**"};

        assertTrue(BackupPathExclusionPatterns.isExcluded("cache/files/a.bin", true, globs));
        assertTrue(BackupPathExclusionPatterns.isExcluded("Android/data/pkg/cache/files/a.bin", true, globs));
        assertFalse(BackupPathExclusionPatterns.isExcluded("Android/data/pkg/files/a.bin", true, globs));
    }
}
