// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.apk.installer.FileNameChecksum;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Saved APKs can be named with their native code ABIs and the start of their SHA-256 (#44). The
 * checksum is of the very bytes saved under that name, so the installer can check it later.
 */
@RunWith(RobolectricTestRunner.class)
public class SavedApkNameTest {
    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void theAbiTokenNamesTheNativeCodeOrSaysUniversal() {
        assertEquals("universal", ApkUtils.abiToken(Collections.<String>emptyList()));
        assertEquals("arm64-v8a", ApkUtils.abiToken(Collections.singletonList("arm64-v8a")));
        assertEquals("arm64-v8a+armeabi-v7a", ApkUtils.abiToken(Arrays.asList("armeabi-v7a", "arm64-v8a")));
        assertEquals("x86_64+x86", ApkUtils.abiToken(Arrays.asList("x86", "x86_64")));
        assertEquals("arm64-v8a+armeabi-v7a+armeabi",
                ApkUtils.abiToken(Arrays.asList("armeabi", "armeabi-v7a", "arm64-v8a")));
        assertEquals("arm64-v8a+riscv64", ApkUtils.abiToken(Arrays.asList("riscv64", "arm64-v8a")));
        // Every common ABI is as good as none: it runs anywhere
        assertEquals("universal", ApkUtils.abiToken(Arrays.asList("x86", "arm64-v8a", "x86_64", "armeabi-v7a")));
        assertEquals("universal",
                ApkUtils.abiToken(Arrays.asList("x86", "arm64-v8a", "x86_64", "armeabi-v7a", "armeabi")));
    }

    @Test
    public void theAbisComeFromTheBaseApkAndItsAbiSplits() throws IOException {
        File base = zip("base.apk", "AndroidManifest.xml", "classes.dex", "lib/arm64-v8a/libfoo.so");
        File abiSplit = zip("split_config.x86_64.apk", "AndroidManifest.xml", "lib/x86_64/libfoo.so");
        File densitySplit = zip("split_config.xxhdpi.apk", "AndroidManifest.xml", "res/drawable-xxhdpi/a.png");

        assertEquals("arm64-v8a+x86_64",
                ApkUtils.abiToken(ApkUtils.nativeAbisOf(Arrays.asList(base, abiSplit, densitySplit))));
        assertEquals("universal",
                ApkUtils.abiToken(ApkUtils.nativeAbisOf(Collections.singletonList(zip("plain.apk", "classes.dex")))));
    }

    @Test
    public void theNewTokensFillInAndOldFormatsStayTheSame() {
        assertEquals("Example_1.0_arm64-v8a_0f3a9c7b",
                ApkUtils.formatApkFilename("%label%_%version%_%abi%_%sha256_short%", "Example", "com.example",
                        "1.0", 1, 35, 23, "2026-11-21", "arm64-v8a", "0f3a9c7b"));
        assertEquals("Example_1.0",
                ApkUtils.formatApkFilename("%label%_%version%", "Example", "com.example", "1.0", 1, 35, 23,
                        "2026-11-21", "arm64-v8a", "0f3a9c7b"));
        assertEquals(ApkUtils.formatApkFilename("%label%_%version%", "Example", "com.example", "1.0", 1, 35, 23,
                        "2026-11-21"),
                ApkUtils.formatApkFilename("%label%_%version%", "Example", "com.example", "1.0", 1, 35, 23,
                        "2026-11-21", "arm64-v8a", "0f3a9c7b"));
    }

    @Test
    public void theShortChecksumIsOfTheBytesSavedUnderThatName() throws IOException {
        Path dir = Paths.get(mTemp.newFolder("apks"));
        byte[] first = "first build".getBytes(StandardCharsets.UTF_8);
        String sha = DigestUtils.getHexDigest(DigestUtils.SHA_256, first);
        // An older file already under the final name is replaced
        try (OutputStream out = dir.createNewFile("Example_" + sha.substring(0, 8) + ".apk", null)
                .openOutputStream()) {
            out.write("stale".getBytes(StandardCharsets.UTF_8));
        }

        Path saved = save(dir, "%label%_%sha256_short%", first);

        assertEquals("Example_" + sha.substring(0, 8) + ".apk", saved.getName());
        assertArrayEquals(first, read(saved));
        assertEquals("Nothing else is left in the folder", 1, dir.listFiles().length);
        try (InputStream in = saved.openInputStream()) {
            assertEquals(FileNameChecksum.Verdict.MATCHES, FileNameChecksum.check(saved.getName(), in));
        }

        Path second = save(dir, "%label%_%sha256_short%", "second build".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(saved.getName(), second.getName());
        assertEquals(2, dir.listFiles().length);
    }

    @Test
    public void withoutTheChecksumTokenTheFileIsWrittenUnderItsName() throws IOException {
        Path dir = Paths.get(mTemp.newFolder("apks"));
        List<String> writtenTo = new ArrayList<>();
        byte[] bytes = "plain".getBytes(StandardCharsets.UTF_8);

        Path saved = ApkUtils.saveNamed(dir, "%label%", sha -> "Example", ApkUtils.EXT_APK, out -> {
            writtenTo.add(out.getName());
            try (OutputStream os = out.openOutputStream()) {
                os.write(bytes);
            }
        });

        assertEquals("Example.apk", saved.getName());
        assertEquals(Collections.singletonList("Example.apk"), writtenTo);
        assertArrayEquals(bytes, read(saved));
    }

    @NonNull
    private static Path save(@NonNull Path dir, @NonNull String format, @NonNull byte[] bytes) throws IOException {
        return ApkUtils.saveNamed(dir, format, sha -> ApkUtils.formatApkFilename(format, "Example", "com.example",
                "1.0", 1, 35, 23, "2026-11-21", "universal", sha), ApkUtils.EXT_APK, out -> {
            try (OutputStream os = out.openOutputStream()) {
                os.write(bytes);
            }
        });
    }

    @NonNull
    private static byte[] read(@NonNull Path path) throws IOException {
        try (InputStream in = path.openInputStream()) {
            return IoUtils.readFully(in, -1, true);
        }
    }

    @NonNull
    private File zip(@NonNull String name, @NonNull String... entries) throws IOException {
        File file = new File(mTemp.newFolder(), name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(1);
                zip.closeEntry();
            }
        }
        return file;
    }
}
