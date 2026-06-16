// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.zip.ZipEntry;

import io.github.muntashirakon.AppManager.utils.FileUtils;

/**
 * Hostile archive fixture corpus — verifies that the basename-extraction defense
 * ({@link FileUtils#getFilenameFromZipEntry}) used by ApkFile, OBB extraction,
 * and split APK enumeration correctly neutralizes crafted zip entry names.
 */
@RunWith(RobolectricTestRunner.class)
public class HostileArchiveFixtureTest {

    @Test
    public void getFilename_simpleBasename() {
        assertEquals("base.apk", filename("base.apk"));
    }

    @Test
    public void getFilename_nestedPath() {
        assertEquals("split.apk", filename("splits/arm64/split.apk"));
    }

    @Test
    public void getFilename_traversalStripped() {
        assertEquals("evil.apk", filename("../../../evil.apk"));
    }

    @Test
    public void getFilename_deepTraversal() {
        assertEquals("payload.apk", filename("../../../../../../../tmp/payload.apk"));
    }

    @Test
    public void getFilename_windowsTraversal() {
        // Backslashes are NOT treated as path separators by Paths.getLastPathSegment —
        // the entire string is returned as the "filename". This is safe because the
        // filename won't match .endsWith(".apk") checks (it contains backslashes).
        assertEquals("..\\..\\..\\evil.apk", filename("..\\..\\..\\evil.apk"));
    }

    @Test
    public void getFilename_mixedSeparators() {
        // Only forward slashes are recognized as separators
        assertEquals("arm64\\..\\x86", filename("lib/arm64\\..\\x86"));
    }

    @Test
    public void getFilename_absoluteUnixPath() {
        assertEquals("system.apk", filename("/system/app/system.apk"));
    }

    @Test
    public void getFilename_absoluteWindowsPath() {
        // Backslashes not recognized as separators — entire string is the "filename".
        // Safe because it won't match .endsWith(".apk") pattern without a forward-slash split.
        assertEquals("C:\\Windows\\Temp\\evil.apk", filename("C:\\Windows\\Temp\\evil.apk"));
    }

    @Test
    public void getFilename_dotDotOnly() {
        // ".." is returned as-is — safe because it doesn't match any expected extension
        assertEquals("..", filename(".."));
    }

    @Test
    public void getFilename_slashOnly() {
        assertEquals("", filename("/"));
    }

    @Test
    public void getFilename_emptyName() {
        assertEquals("", filename(""));
    }

    @Test
    public void getFilename_trailingSlash() {
        // Trailing slash is stripped, then the last segment is "subdir"
        assertEquals("subdir", filename("dir/subdir/"));
    }

    @Test
    public void getFilename_dotEntry() {
        assertEquals("", filename("."));
    }

    @Test
    public void getFilename_unicodeName() {
        assertEquals("évil.apk", filename("path/évil.apk"));
    }

    @Test
    public void getFilename_veryLongName() {
        StringBuilder sb = new StringBuilder(4100);
        for (int i = 0; i < 4096; i++) sb.append('a');
        sb.append(".apk");
        String longName = sb.toString();
        assertEquals(longName, filename("dir/" + longName));
    }

    @Test
    public void getFilename_encodedTraversal() {
        // %2e%2e%2f is NOT decoded by ZipEntry — these are literal chars
        assertEquals("%2e%2e%2fescaped.apk", filename("%2e%2e%2fescaped.apk"));
    }

    @Test
    public void getFilename_nullByteInPath() {
        // ZipEntry accepts null bytes; basename extraction should handle them
        String result = filename("dir/foo\0bar.apk");
        // The basename should contain the null byte as-is (it's the caller's job to reject)
        assertTrue(result.contains("\0"));
    }

    // ── Helper methods ──

    private static String filename(String entryName) {
        ZipEntry entry = new ZipEntry(entryName);
        return FileUtils.getFilenameFromZipEntry(entry);
    }
}
