// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class FmIconsTest {
    @Test
    public void boundedPdfThumbnailSizeKeepsSmallPagesUnchanged() {
        assertArrayEquals(new int[]{600, 800}, FmIcons.getBoundedPdfThumbnailSize(600, 800));
    }

    @Test
    public void boundedPdfThumbnailSizeCapsLargeWidePages() {
        assertArrayEquals(new int[]{2048, 1024}, FmIcons.getBoundedPdfThumbnailSize(6000, 3000));
    }

    @Test
    public void boundedPdfThumbnailSizeCapsPixelBudget() {
        int[] size = FmIcons.getBoundedPdfThumbnailSize(3000, 3000);

        assertArrayEquals(new int[]{2048, 2048}, size);
    }
}
