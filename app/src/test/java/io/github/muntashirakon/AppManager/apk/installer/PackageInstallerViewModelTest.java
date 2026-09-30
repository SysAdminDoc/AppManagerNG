// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The native-code preflight reads the base APK's own {@code lib/<abi>/} entries. Only split APK sets
 * were checked before, through their ABI splits.
 */
public class PackageInstallerViewModelTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    @Test
    public void theBaseApksNativeAbisComeFromItsLibEntries() throws IOException {
        File apk = zip("AndroidManifest.xml", "classes.dex", "lib/x86_64/libgame.so", "lib/x86/libgame.so",
                "lib/x86_64/libaudio.so", "assets/lib/arm64-v8a/not-native.so", "res/raw/lib.txt");

        assertEquals(new TreeSet<>(Arrays.asList("x86", "x86_64")), PackageInstallerViewModel.readNativeAbis(apk));
    }

    @Test
    public void anApkWithoutNativeCodeHasNoAbis() throws IOException {
        assertTrue(PackageInstallerViewModel.readNativeAbis(zip("AndroidManifest.xml", "classes.dex")).isEmpty());
    }

    private File zip(String... names) throws IOException {
        File file = mFolder.newFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                out.write(1);
                out.closeEntry();
            }
        }
        return file;
    }
}
