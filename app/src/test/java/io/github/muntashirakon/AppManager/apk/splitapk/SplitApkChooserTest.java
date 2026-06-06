// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.apk.ApkFile;

public class SplitApkChooserTest {
    @Test
    public void recordSelectedSplit_recordsIdAndTypeForFeature() {
        Set<String> selectedSplits = new HashSet<>();
        HashMap<String, HashSet<Integer>> seenSplits = new HashMap<>();

        HashSet<Integer> types = SplitApkChooser.recordSelectedSplit(selectedSplits, seenSplits,
                "split_config.en.apk", "feature.maps", ApkFile.APK_SPLIT_LOCALE);

        assertTrue(selectedSplits.contains("split_config.en.apk"));
        assertTrue(types.contains(ApkFile.APK_SPLIT_LOCALE));
        assertSame(types, seenSplits.get("feature.maps"));
    }

    @Test
    public void recordSelectedSplit_reusesFeatureTypeBucket() {
        Set<String> selectedSplits = new HashSet<>();
        HashMap<String, HashSet<Integer>> seenSplits = new HashMap<>();
        HashSet<Integer> types = SplitApkChooser.recordSelectedSplit(selectedSplits, seenSplits,
                "split_config.arm64.apk", "feature.maps", ApkFile.APK_SPLIT_ABI);

        HashSet<Integer> updatedTypes = SplitApkChooser.recordSelectedSplit(selectedSplits, seenSplits,
                "split_config.xxhdpi.apk", "feature.maps", ApkFile.APK_SPLIT_DENSITY);

        assertSame(types, updatedTypes);
        assertTrue(selectedSplits.contains("split_config.arm64.apk"));
        assertTrue(selectedSplits.contains("split_config.xxhdpi.apk"));
        assertTrue(updatedTypes.contains(ApkFile.APK_SPLIT_ABI));
        assertTrue(updatedTypes.contains(ApkFile.APK_SPLIT_DENSITY));
    }
}
