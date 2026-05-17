// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FinderRelevanceScorerTest {
    @Test
    public void levenshteinDistanceCountsEdits() {
        assertEquals(3, FinderRelevanceScorer.levenshteinDistance("kitten", "sitting"));
    }

    @Test
    public void scoreTextPrefersSimplePackageNameMatch() {
        assertTrue(FinderRelevanceScorer.scoreText("com.android.camera", "camera")
                < FinderRelevanceScorer.scoreText("com.example.cameratools", "camera"));
    }

    @Test
    public void scoreTextPrefersExactTokenOverLongerToken() {
        assertTrue(FinderRelevanceScorer.scoreText("com.example.camera", "camera")
                < FinderRelevanceScorer.scoreText("com.example.camerahelper", "camera"));
    }

    @Test
    public void scoreTextIsCaseInsensitive() {
        assertEquals(FinderRelevanceScorer.scoreText("com.example.Camera", "camera"),
                FinderRelevanceScorer.scoreText("COM.EXAMPLE.CAMERA", "CAMERA"));
    }
}
