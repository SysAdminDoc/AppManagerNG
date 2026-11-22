// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issues 39 and 22: App Info shows the base APK's SHA-256 next to the version, short, and copies the whole hash.
 * Hashing a big APK takes a while, so a file that hasn't changed is hashed once.
 */
@RunWith(RobolectricTestRunner.class)
public class ApkHashCacheTest {
    private static final String SHA_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void theDefaultHasherGivesTheFullSha256() throws IOException {
        File apk = write(mTemp.newFile("base.apk"), "abc");

        assertEquals(SHA_OF_ABC, ApkHashCache.getInstance().sha256(apk));
    }

    @Test
    public void anUnchangedFileIsHashedOnce() throws IOException {
        AtomicInteger runs = new AtomicInteger();
        ApkHashCache cache = new ApkHashCache(file -> {
            runs.incrementAndGet();
            return SHA_OF_ABC;
        });
        File apk = write(mTemp.newFile("base.apk"), "abc");

        cache.sha256(apk);
        cache.sha256(apk);

        assertEquals(1, runs.get());
    }

    @Test
    public void aChangedFileIsHashedAgain() throws IOException {
        AtomicInteger runs = new AtomicInteger();
        ApkHashCache cache = new ApkHashCache(file -> "hash" + runs.incrementAndGet());
        File apk = write(mTemp.newFile("base.apk"), "abc");
        assertEquals("hash1", cache.sha256(apk));

        write(apk, "abcdef");

        assertEquals("hash2", cache.sha256(apk));
    }

    @Test
    public void aMissingFileHasNoHash() {
        assertNull(ApkHashCache.getInstance().sha256(new File(mTemp.getRoot(), "gone.apk")));
    }

    @Test
    public void theShortFormIsTheFirstEightHexDigits() {
        assertEquals("ba7816bf", ApkHashCache.shortForm(SHA_OF_ABC));
        assertEquals("ba7816bf", ApkHashCache.shortForm(SHA_OF_ABC.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    public void theVersionLineEndsWithTheShortHash() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals("0.8.0 (34) " + (char) 0x00B7 + " SHA ba7816bf",
                ApkHashCache.versionWithShortSha(context, "0.8.0 (34)", SHA_OF_ABC).toString());
        assertEquals("0.8.0 (34)", ApkHashCache.versionWithShortSha(context, "0.8.0 (34)", null).toString());
    }

    private static File write(File file, String text) throws IOException {
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
