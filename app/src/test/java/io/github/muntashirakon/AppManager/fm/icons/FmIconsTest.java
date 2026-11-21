// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;

/**
 * Files without a thumbnail get an icon for their kind of file (#36).
 */
@RunWith(RobolectricTestRunner.class)
public class FmIconsTest {
    private static final String UNKNOWN = "application/octet-stream";

    @Test
    public void archivesGetTheArchiveIcon() {
        assertIcon(R.drawable.ic_archive, "application/zip", "zip");
        assertIcon(R.drawable.ic_archive, "application/x-7z-compressed", "7z");
        assertIcon(R.drawable.ic_archive, "application/x-rar-compressed", "rar");
        assertIcon(R.drawable.ic_archive, "application/x-tar", "tar");
        assertIcon(R.drawable.ic_archive, "application/gzip", "gz");
    }

    @Test
    public void documentsGetDocumentIconsThatAreNotTheGenericFile() {
        assertIcon(R.drawable.ic_pdf_file, "application/pdf", "pdf");
        assertIcon(R.drawable.ic_description, "application/msword", "doc");
        assertIcon(R.drawable.ic_description,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        assertIcon(R.drawable.ic_description, "application/vnd.oasis.opendocument.text", "odt");
        assertIcon(R.drawable.ic_file_document, "text/plain", "txt");
        assertIcon(R.drawable.ic_file_document, "text/markdown", "md");
        int word = FmIcons.getDrawable("application/msword", "doc");
        assertFalse("a Word file looked like an unknown file", FmIcons.isGeneric(word));
    }

    @Test
    public void mediaGetsItsOwnIcons() {
        assertIcon(R.drawable.ic_audio_file, "audio/mpeg", "mp3");
        assertIcon(R.drawable.ic_audio_file, "audio/ogg", "ogg");
        assertIcon(R.drawable.ic_video_file, "video/mp4", "mp4");
        assertIcon(R.drawable.ic_video_file, "video/x-matroska", "mkv");
        assertIcon(R.drawable.ic_image, "image/png", "png");
        assertIcon(R.drawable.ic_image, "image/jpeg", "jpg");
    }

    @Test
    public void androidPackagesGetTheApkIcon() {
        assertIcon(R.drawable.ic_android, "application/vnd.android.package-archive", "apk");
        assertIcon(R.drawable.ic_android, "application/x-apks", "apks");
        assertIcon(R.drawable.ic_android, "application/vnd.apkm", "apkm");
        assertIcon(R.drawable.ic_android, "application/xapk-package-archive", "xapk");
    }

    @Test
    public void codeGetsTheCodeIcon() {
        assertIcon(R.drawable.ic_code, "application/json", "json");
        assertIcon(R.drawable.ic_code, "text/x-java", "java");
        assertIcon(R.drawable.ic_code, "application/x-sh", "sh");
        assertIcon(R.drawable.ic_code, "text/xml", "xml");
    }

    @Test
    public void theExtensionDecidesWhenTheContentSaysNothing() {
        // Magic bytes found nothing (or detection was unavailable): the name still tells the kind
        assertEquals(R.drawable.ic_archive, FmIcons.getDrawable(UNKNOWN, "7z"));
        assertEquals(R.drawable.ic_archive, FmIcons.getDrawable(UNKNOWN, "ZST"));
        assertEquals(R.drawable.ic_android, FmIcons.getDrawable(UNKNOWN, "xapk"));
        assertEquals(R.drawable.ic_android, FmIcons.getDrawable(null, "APKS"));
        assertEquals(R.drawable.ic_description, FmIcons.getDrawable(UNKNOWN, "docx"));
        assertEquals(R.drawable.ic_file_document, FmIcons.getDrawable(UNKNOWN, "md"));
        assertEquals(R.drawable.ic_code, FmIcons.getDrawable(UNKNOWN, "kt"));
        assertEquals(R.drawable.ic_audio_file, FmIcons.getDrawable(null, "opus"));
        assertEquals(R.drawable.ic_video_file, FmIcons.getDrawable(UNKNOWN, "webm"));
        assertEquals(R.drawable.ic_image, FmIcons.getDrawable(UNKNOWN, "heic"));
    }

    @Test
    public void theContentWinsOverAMisleadingExtension() {
        // A PNG renamed to .zip is still a picture
        assertEquals(R.drawable.ic_image, FmIcons.getDrawable("image/png", "zip"));
    }

    @Test
    public void unknownFilesGetTheGenericIcon() {
        assertTrue(FmIcons.isGeneric(FmIcons.getDrawable(UNKNOWN, null)));
        assertTrue(FmIcons.isGeneric(FmIcons.getDrawable(UNKNOWN, "qwertyuiop")));
        assertTrue(FmIcons.isGeneric(FmIcons.getDrawable(null, null)));
        assertTrue(FmIcons.isGeneric(FmIcons.getDrawable(null, "")));
    }

    @Test
    public void everyCategoryHasItsOwnIcon() {
        int[] icons = {
                FmIcons.getDrawable("application/zip", null),
                FmIcons.getDrawable("application/msword", null),
                FmIcons.getDrawable("application/pdf", null),
                FmIcons.getDrawable("text/plain", null),
                FmIcons.getDrawable("audio/mpeg", null),
                FmIcons.getDrawable("video/mp4", null),
                FmIcons.getDrawable("image/png", null),
                FmIcons.getDrawable("application/vnd.android.package-archive", null),
                FmIcons.getDrawable("application/json", null),
                FmIcons.getDrawable(UNKNOWN, null),
        };
        Set<Integer> distinct = new HashSet<>();
        for (int icon : icons) {
            distinct.add(icon);
        }
        assertEquals(icons.length, distinct.size());
        assertNotEquals(R.drawable.ic_file, FmIcons.getDrawable("application/msword", null));
    }

    private static void assertIcon(int expected, String mimeType, String extension) {
        assertEquals(mimeType, expected, FmIcons.getDrawable(mimeType, extension));
        assertEquals(mimeType + " by extension only", expected, FmIcons.getDrawable(UNKNOWN, extension));
    }
}
