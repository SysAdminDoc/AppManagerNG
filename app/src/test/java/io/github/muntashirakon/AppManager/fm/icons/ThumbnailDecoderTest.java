// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * What the isolated process decodes, with real graphics: every kind comes back within
 * {@link ThumbnailDecoder#MAX_SIDE}, keeps its shape, and a file that isn't what it claims gives
 * no thumbnail.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 36)
public class ThumbnailDecoderTest {
    private static final int MAX = ThumbnailDecoder.MAX_SIDE;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void largeImageIsScaledToFit() throws IOException {
        Bitmap bitmap = decode(png(600, 300, Color.BLUE), ThumbnailDecoder.KIND_IMAGE, null);
        assertSize(256, 128, bitmap);
        assertEquals(Color.BLUE, bitmap.getPixel(128, 64));
    }

    @Test
    public void smallImageKeepsItsSize() throws IOException {
        assertSize(40, 20, decode(png(40, 20, Color.GREEN), ThumbnailDecoder.KIND_IMAGE, null));
    }

    @Test
    public void svgIsDrawnToFit() throws IOException {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"512\" height=\"256\">"
                + "<rect x=\"0\" y=\"0\" width=\"512\" height=\"256\" fill=\"#ff0000\"/></svg>";
        Bitmap bitmap = decode(svg.getBytes(StandardCharsets.UTF_8), ThumbnailDecoder.KIND_SVG, null);
        assertSize(256, 128, bitmap);
        assertEquals(Color.RED, bitmap.getPixel(128, 64));
    }

    @Test
    public void epubCoverIsRead() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", utf8("application/epub+zip"));
        entries.put("META-INF/container.xml", utf8("<?xml version=\"1.0\"?>"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "</rootfiles></container>"));
        entries.put("OEBPS/content.opf", utf8("<?xml version=\"1.0\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\">"
                + "<metadata><meta name=\"cover\" content=\"cover-img\"/></metadata>"
                + "<manifest><item id=\"text\" href=\"text.html\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"cover-img\" href=\"images/cover.png\" media-type=\"image/png\"/></manifest>"
                + "</package>"));
        entries.put("OEBPS/images/cover.png", png(400, 600, Color.MAGENTA));
        Bitmap bitmap = decode(zip(entries), ThumbnailDecoder.KIND_EPUB, null);
        assertSize(171, 256, bitmap);
        assertEquals(Color.MAGENTA, bitmap.getPixel(85, 128));
    }

    @Test
    public void j2meIconIsRead() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", utf8("Manifest-Version: 1.0\r\n"
                + "MIDlet-1: Game, /res/icon.png, com.example.Game\r\n\r\n"));
        entries.put("res/icon.png", png(32, 32, Color.YELLOW));
        assertSize(32, 32, decode(zip(entries), ThumbnailDecoder.KIND_J2ME, null));
    }

    @Test
    public void j2meIconMadeOfSlashesIsNoThumbnail() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", utf8("Manifest-Version: 1.0\r\nMIDlet-Icon: ///\r\n\r\n"));
        assertNull(decode(zip(entries), ThumbnailDecoder.KIND_J2ME, null));
    }

    @Test
    public void openDocumentThumbnailIsRead() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", utf8("application/vnd.oasis.opendocument.text"));
        entries.put("Thumbnails/thumbnail.png", png(256, 181, Color.CYAN));
        assertSize(256, 181, decode(zip(entries), ThumbnailDecoder.KIND_OPEN_DOCUMENT, null));
    }

    @Test
    public void apkBundleIconIsReadAndScaled() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("info.json", utf8("{}"));
        entries.put("icon.png", png(512, 512, Color.BLACK));
        assertSize(256, 256, decode(zip(entries), ThumbnailDecoder.KIND_APK_BUNDLE, null));
    }

    @Test
    public void apkBundleWithoutAnIconIsNoThumbnail() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("base.apk", new byte[64]);
        assertNull(decode(zip(entries), ThumbnailDecoder.KIND_APK_BUNDLE, null));
    }

    @Test
    public void archiveEntryThatIsNotAnImageIsNoThumbnail() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("icon.png", utf8("not a png at all"));
        assertNull(decode(zip(entries), ThumbnailDecoder.KIND_APK_BUNDLE, null));
    }

    @Test
    public void fontThumbnailDrawsItsLabelWithinMaxSide() throws IOException {
        // Not a real font, so the default typeface draws the label
        Bitmap bitmap = decode(new byte[256], ThumbnailDecoder.KIND_FONT, "A VERY LONG LABEL");
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() <= MAX && bitmap.getWidth() == bitmap.getHeight());
    }

    @Test
    public void malformedFilesGiveNoThumbnailThroughTheBinder() throws IOException {
        AtomicInteger kills = new AtomicInteger();
        ThumbnailDecoderBinder binder = new ThumbnailDecoderBinder(kills::incrementAndGet);
        try {
            byte[] garbage = new byte[2048];
            for (int i = 0; i < garbage.length; ++i) {
                garbage[i] = (byte) (i * 13 + 5);
            }
            int[] kinds = {ThumbnailDecoder.KIND_IMAGE, ThumbnailDecoder.KIND_SVG, ThumbnailDecoder.KIND_PDF,
                    ThumbnailDecoder.KIND_EPUB, ThumbnailDecoder.KIND_J2ME, ThumbnailDecoder.KIND_OPEN_DOCUMENT,
                    ThumbnailDecoder.KIND_APK_BUNDLE};
            for (int kind : kinds) {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(write(garbage), ParcelFileDescriptor.MODE_READ_ONLY);
                assertNull("kind " + kind, binder.decode(fd, kind, null, MAX, garbage.length, 5_000));
            }
            assertEquals(0, kills.get());
        } finally {
            binder.shutdown();
        }
    }

    @Test
    public void binderRepliesWithTheDecodedPixels() throws IOException {
        AtomicInteger kills = new AtomicInteger();
        ThumbnailDecoderBinder binder = new ThumbnailDecoderBinder(kills::incrementAndGet);
        try {
            byte[] png = png(600, 300, Color.BLUE);
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(write(png), ParcelFileDescriptor.MODE_READ_ONLY);
            int[] pixels = binder.decode(fd, ThumbnailDecoder.KIND_IMAGE, null, MAX, png.length, 5_000);
            Bitmap bitmap = IsolatedThumbnails.toBitmap(pixels, MAX);
            assertSize(256, 128, bitmap);
            assertEquals(Color.BLUE, bitmap.getPixel(10, 10));
            assertEquals(0, kills.get());
        } finally {
            binder.shutdown();
        }
    }

    private Bitmap decode(byte[] bytes, int kind, String label) throws IOException {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(write(bytes), ParcelFileDescriptor.MODE_READ_ONLY)) {
            return ThumbnailDecoder.decode(fd, kind, label, MAX);
        }
    }

    private File write(byte[] bytes) throws IOException {
        File file = mTemp.newFile();
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static void assertSize(int width, int height, Bitmap bitmap) {
        assertNotNull(bitmap);
        assertEquals("width", width, bitmap.getWidth());
        assertEquals("height", height, bitmap.getHeight());
    }

    private static byte[] png(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
