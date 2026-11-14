// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Size limits of the isolated thumbnail decoder. These replace FmIconsTest, whose PDF size bound
 * moved into the decoder and now caps every kind of thumbnail at {@link ThumbnailDecoder#MAX_SIDE}.
 */
public class ThumbnailDecoderSizeTest {
    private static final int MAX = ThumbnailDecoder.MAX_SIDE;

    @Test
    public void smallImagesAreNeverScaledUp() {
        assertArrayEquals(new int[]{100, 50}, ThumbnailDecoder.fitWithin(100, 50, MAX));
        assertArrayEquals(new int[]{MAX, MAX}, ThumbnailDecoder.fitWithin(MAX, MAX, MAX));
    }

    @Test
    public void largePagesFitAndKeepTheirShape() {
        assertArrayEquals(new int[]{256, 128}, ThumbnailDecoder.fitWithin(6000, 3000, MAX));
        assertArrayEquals(new int[]{128, 256}, ThumbnailDecoder.fitWithin(3000, 6000, MAX));
        assertArrayEquals(new int[]{256, 256}, ThumbnailDecoder.fitWithin(3000, 3000, MAX));
        assertArrayEquals(new int[]{171, 256}, ThumbnailDecoder.fitWithin(400, 600, MAX));
    }

    @Test
    public void sliversNeverShrinkToNothing() {
        assertArrayEquals(new int[]{256, 1}, ThumbnailDecoder.fitWithin(100_000, 1, MAX));
        assertArrayEquals(new int[]{1, 256}, ThumbnailDecoder.fitWithin(1, 100_000, MAX));
        assertArrayEquals(new int[]{256, 1}, ThumbnailDecoder.fitWithin(Integer.MAX_VALUE, 1, MAX));
    }

    @Test
    public void sampleSizeDecodesNoLargerThanNeeded() {
        assertEquals(1, ThumbnailDecoder.sampleSizeFor(256, 256, MAX));
        assertEquals(1, ThumbnailDecoder.sampleSizeFor(511, 100, MAX));
        assertEquals(2, ThumbnailDecoder.sampleSizeFor(512, 512, MAX));
        assertEquals(8, ThumbnailDecoder.sampleSizeFor(4000, 3000, MAX));
    }

    @Test
    public void sampleSizeKeepsTheDecodedPixelBudget() {
        // A large maxSide alone wouldn't subsample, so only the pixel budget applies
        assertEquals(4, ThumbnailDecoder.sampleSizeFor(8192, 8192, 8192));
        int sampleSize = ThumbnailDecoder.sampleSizeFor(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals("a power of two", 0, sampleSize & (sampleSize - 1));
        long width = Integer.MAX_VALUE / sampleSize;
        assertTrue(width * width <= ThumbnailDecoder.MAX_DECODE_PIXELS);
    }

    @Test
    public void sampleSizeEndsForAbsurdDimensions() {
        int sampleSize = ThumbnailDecoder.sampleSizeFor(Integer.MAX_VALUE, Integer.MAX_VALUE, 1);
        assertTrue(sampleSize > 0 && sampleSize <= (1 << 30));
    }
}
