// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmDuplicateApkSelectionUtilsTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void canOfferDuplicateScan_requiresAtLeastTwoReadableLocalApks() throws IOException {
        Path apk = writePath("one.apk", 16);
        Path apks = writePath("two.apks", 16);
        Path apkm = writePath("three.apkm", 16);
        Path xapk = writePath("four.xapk", 16);

        assertTrue(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Arrays.asList(apk, apks)));
        assertTrue(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Arrays.asList(apk, apkm, xapk)));
        assertFalse(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Collections.singletonList(apk)));
        assertFalse(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Collections.emptyList()));
    }

    @Test
    public void canOfferDuplicateScan_rejectsNonPackageFilesDirectoriesAndEmptyFiles() throws IOException {
        Path apk = writePath("one.apk", 16);
        Path text = writePath("notes.txt", 16);
        Path empty = writePath("empty.apk", 0);
        Path directory = Paths.get(tmp.newFolder("folder.apk"));

        assertFalse(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Arrays.asList(apk, text)));
        assertFalse(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Arrays.asList(apk, empty)));
        assertFalse(FmDuplicateApkSelectionUtils.canOfferDuplicateScan(Arrays.asList(apk, directory)));
    }

    @Test
    public void toLocalFilesReturnsConcreteFileList() throws IOException {
        Path one = writePath("one.apk", 16);
        Path two = writePath("two.apk", 32);

        List<File> files = FmDuplicateApkSelectionUtils.toLocalFiles(Arrays.asList(one, two));

        assertEquals(2, files.size());
        assertEquals("one.apk", files.get(0).getName());
        assertEquals("two.apk", files.get(1).getName());
    }

    private Path writePath(String name, int bytes) throws IOException {
        File file = new File(tmp.getRoot(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (bytes > 0) {
                out.write(new byte[bytes]);
            }
        }
        return Paths.get(file);
    }
}
