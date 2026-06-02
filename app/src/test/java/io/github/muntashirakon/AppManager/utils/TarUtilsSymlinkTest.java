// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Covers the symlink-target containment guard added to {@link TarUtils} so a
 * tampered/shared backup cannot make a privileged restore plant a symlink
 * pointing outside the extraction root (sandbox-escape primitive).
 */
@RunWith(RobolectricTestRunner.class)
public class TarUtilsSymlinkTest {
    private static final String DEST = "/data/data/com.me";

    @Test
    public void rejectsAbsoluteTargetOutsideDestination() {
        assertFalse(TarUtils.isSymlinkTargetContained("/data/data/com.other/databases", "files/db", DEST));
    }

    @Test
    public void allowsAbsoluteTargetInsideDestination() {
        assertTrue(TarUtils.isSymlinkTargetContained(DEST + "/files/real", "files/link", DEST));
    }

    @Test
    public void rejectsRelativeTraversalEscape() {
        assertFalse(TarUtils.isSymlinkTargetContained("../../escape", "files/db", DEST));
    }

    @Test
    public void allowsInTreeRelativeTarget() {
        assertTrue(TarUtils.isSymlinkTargetContained("sibling", "files/db", DEST));
    }

    @Test
    public void rejectsEmptyTarget() {
        assertFalse(TarUtils.isSymlinkTargetContained("", "files/db", DEST));
    }

    @Test
    public void rejectsAbsoluteTargetWhenDestinationUnknown() {
        assertFalse(TarUtils.isSymlinkTargetContained("/data/data/com.me/x", "files/db", null));
    }
}
