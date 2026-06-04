// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.core.util.Pair;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class ApkDuplicateOperationsTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void isPlainApkChecksExtensionCaseInsensitively() {
        assertTrue(ApkDuplicateOperations.isPlainApk(new File("base.apk")));
        assertTrue(ApkDuplicateOperations.isPlainApk(new File("BASE.APK")));
        assertFalse(ApkDuplicateOperations.isPlainApk(new File("bundle.apks")));
        assertFalse(ApkDuplicateOperations.isPlainApk(new File("archive.xapk")));
    }

    @Test
    public void deleteCandidatesReturnsCountAndReclaimedBytes() throws IOException {
        File first = writeFile("one.apk", 16);
        File second = writeFile("two.apk", 32);
        ApkDuplicateSelector.Candidate firstCandidate = new ApkDuplicateSelector.Candidate(
                first, "example.pkg", 1, null, first.length());
        ApkDuplicateSelector.Candidate secondCandidate = new ApkDuplicateSelector.Candidate(
                second, "example.pkg", 1, null, second.length());

        Pair<Integer, Long> result = ApkDuplicateOperations.deleteCandidates(
                Arrays.asList(firstCandidate, secondCandidate));

        assertEquals(Integer.valueOf(2), result.first);
        assertEquals(Long.valueOf(48L), result.second);
        assertFalse(first.exists());
        assertFalse(second.exists());
    }

    private File writeFile(String name, int bytes) throws IOException {
        File file = new File(tmp.getRoot(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        }
        return file;
    }
}
