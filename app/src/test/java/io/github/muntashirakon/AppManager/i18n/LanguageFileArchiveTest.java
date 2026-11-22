// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.settings.LanguagePacksPreferences;

/**
 * #31: forums that host translations want them as .zip attachments. A zip from a forum is
 * untrusted, so these feed the import hostile archives too.
 */
@RunWith(RobolectricTestRunner.class)
public class LanguageFileArchiveTest {
    private static final String CANCEL = "\"cancel\": {\"original\": \"Cancel\", \"translation\": \"Abbrechen\"}";
    private static final String SHOWING = "\"main_status_showing_apps\": {\"translation\":"
            + " {\"one\": \"%1$d/%2$d App\", \"other\": \"%1$d/%2$d Apps\"}}";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        deleteAll(new File(mContext.getFilesDir(), "language_packs"));
        deleteAll(new File(mContext.getNoBackupFilesDir(), "language_packs"));
    }

    @Test
    public void anExportedZipHoldsTheJsonAndImportsBack() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        manager.importPack(pack("de", "Deutsch", CANCEL, SHOWING));
        byte[] json = manager.exportBytes("de", false);
        byte[] zip = manager.exportBytes("de", true);
        assertTrue(LanguageFileArchive.isZip(zip));
        assertFalse(LanguageFileArchive.isZip(json));

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry = zis.getNextEntry();
            assertEquals(LanguagePackManager.jsonFileName("de"), entry.getName());
            assertArrayEquals(json, readAll(zis));
            assertNull(zis.getNextEntry());
        }
        assertEquals("AppManagerNG-de.json", LanguagePackManager.jsonFileName("de"));
        assertEquals("AppManagerNG-de.json.zip", LanguagePackManager.zipFileName("de"));

        manager.remove("de", "Deutsch");
        LanguagePackManager.ImportResult result = manager.importPack(zip);
        assertEquals("de", result.code);
        assertEquals("AppManagerNG-de.json", result.archiveEntry);
        assertEquals(1, result.archiveJsonFiles);
        assertEquals(2, manager.list().get(0).translated);
    }

    @Test
    public void entriesThatClimbOutOfTheZipAreSkipped() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        byte[] evil = zip(new String[]{"../evil.json", "/abs.json", "C:/drive.json", "a/../../up.json"},
                pack("de", "Deutsch", CANCEL), pack("de", "Deutsch", CANCEL), pack("de", "Deutsch", CANCEL),
                pack("de", "Deutsch", CANCEL));
        LanguagePackException none = assertThrows(LanguagePackException.class, () -> manager.importPack(evil));
        assertEquals(LanguagePackException.Reason.NO_FILE_IN_ZIP, none.reason);

        byte[] mixed = zip(new String[]{"../evil.json", "translations/", "translations/ru.json"},
                pack("fr", "Français", CANCEL), new byte[0], pack("ru", "Русский", CANCEL));
        LanguagePackManager.ImportResult result = manager.importPack(mixed);
        assertEquals("ru", result.code);
        assertEquals("ru.json", result.archiveEntry);
        assertEquals(1, manager.list().size());
    }

    @Test
    public void anEntryThatInflatesPastTheCapIsStopped() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        // Claims 1000 bytes, inflates to 6 MB
        byte[] bomb = bomb("big.json", 6 * 1024 * 1024, 1000);
        assertTrue(bomb.length < 64 * 1024);
        LanguagePackException tooBig = assertThrows(LanguagePackException.class, () -> manager.importPack(bomb));
        assertEquals(LanguagePackException.Reason.TOO_LARGE, tooBig.reason);
        assertTrue(manager.list().isEmpty());
    }

    @Test
    public void tooManyEntriesAreRefused() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        String[] names = new String[LanguageFileArchive.MAX_ENTRIES + 1];
        byte[][] data = new byte[names.length][];
        for (int i = 0; i < names.length; ++i) {
            names[i] = "note" + i + ".txt";
            data[i] = new byte[]{'x'};
        }
        LanguagePackException e = assertThrows(LanguagePackException.class, () -> manager.importPack(zip(names, data)));
        assertEquals(LanguagePackException.Reason.TOO_LARGE, e.reason);
    }

    @Test
    public void aZipWithoutALanguageFileSaysSo() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        byte[] text = zip(new String[]{"readme.txt", "values-ru/strings.xml"},
                "hello".getBytes(StandardCharsets.UTF_8), "<resources/>".getBytes(StandardCharsets.UTF_8));
        LanguagePackException e = assertThrows(LanguagePackException.class, () -> manager.importPack(text));
        assertEquals(LanguagePackException.Reason.NO_FILE_IN_ZIP, e.reason);

        // An archive inside the archive is never opened
        byte[] nested = zip(new String[]{"inner.zip"}, zip(new String[]{"de.json"}, pack("de", "Deutsch", CANCEL)));
        e = assertThrows(LanguagePackException.class, () -> manager.importPack(nested));
        assertEquals(LanguagePackException.Reason.NO_FILE_IN_ZIP, e.reason);
        byte[] disguised = zip(new String[]{"de.json"}, zip(new String[]{"de.json"}, pack("de", "Deutsch", CANCEL)));
        e = assertThrows(LanguagePackException.class, () -> manager.importPack(disguised));
        assertEquals(LanguagePackException.Reason.NOT_A_PACK, e.reason);

        // A .json that isn't a language file reports why
        byte[] notAPack = zip(new String[]{"package.json"}, "{\"name\": \"x\"}".getBytes(StandardCharsets.UTF_8));
        e = assertThrows(LanguagePackException.class, () -> manager.importPack(notAPack));
        assertEquals(LanguagePackException.Reason.NOT_A_PACK, e.reason);

        byte[] damaged = Arrays.copyOf(zip(new String[]{"de.json"}, pack("de", "Deutsch", CANCEL)), 40);
        e = assertThrows(LanguagePackException.class, () -> manager.importPack(damaged));
        assertEquals(LanguagePackException.Reason.NOT_A_PACK, e.reason);
        assertTrue(manager.list().isEmpty());
    }

    @Test
    public void withTwoLanguageFilesTheFirstValidOneIsUsedAndNamed() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        byte[] two = zip(new String[]{"a/notes.json", "pack/de.json", "pack/fr.json"},
                "{\"todo\": 1}".getBytes(StandardCharsets.UTF_8), pack("de", "Deutsch", CANCEL),
                pack("fr", "Français", CANCEL));
        LanguagePackManager.ImportResult result = manager.importPack(two);
        assertEquals("de", result.code);
        assertEquals("de.json", result.archiveEntry);
        assertEquals(3, result.archiveJsonFiles);
        List<LanguagePackManager.Installed> installed = manager.list();
        assertEquals(1, installed.size());
        assertEquals("de", installed.get(0).code);
    }

    @Test
    public void aPlainJsonImportIsNotAnArchive() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        LanguagePackManager.ImportResult result = manager.importPack(pack("de", "Deutsch", CANCEL));
        assertNull(result.archiveEntry);
        assertEquals(0, result.archiveJsonFiles);
    }

    @Test
    public void theImportPickerOffersJsonAndZip() {
        Intent intent = new LanguagePacksPreferences.ImportContract().createIntent(mContext, "*/*");
        List<String> types = Arrays.asList(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
        assertTrue(types.contains("application/json"));
        assertTrue(types.contains("application/zip"));
        // Chat apps hand files over as these
        assertTrue(types.contains("application/octet-stream"));
        assertEquals("*/*", intent.getType());
    }

    static byte[] pack(String code, String name, String... entries) {
        return ("{\"schema_version\": 1, \"language_code\": \"" + code + "\", \"language_name\": \"" + name + "\","
                + " \"translations\": {" + String.join(",", entries) + "}}").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] zip(String[] names, byte[]... data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; ++i) {
                zos.putNextEntry(new ZipEntry(names[i]));
                if (!names[i].endsWith("/")) {
                    zos.write(data[i]);
                }
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** A zip whose one deflated entry says it's {@code declared} bytes but inflates to {@code actual}. */
    private static byte[] bomb(String name, int actual, int declared) {
        byte[] content = new byte[actual];
        Arrays.fill(content, (byte) ' ');
        content[0] = '{';
        CRC32 crc = new CRC32();
        crc.update(content);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(content);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            compressed.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        byte[] data = compressed.toByteArray();
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(30 + nameBytes.length + data.length + 46 + nameBytes.length + 22)
                .order(ByteOrder.LITTLE_ENDIAN);
        // Local file header
        bb.putInt(0x04034b50).putShort((short) 20).putShort((short) 0).putShort((short) 8)
                .putShort((short) 0).putShort((short) 0).putInt((int) crc.getValue())
                .putInt(data.length).putInt(declared).putShort((short) nameBytes.length).putShort((short) 0)
                .put(nameBytes).put(data);
        int centralStart = bb.position();
        // Central directory
        bb.putInt(0x02014b50).putShort((short) 20).putShort((short) 20).putShort((short) 0).putShort((short) 8)
                .putShort((short) 0).putShort((short) 0).putInt((int) crc.getValue())
                .putInt(data.length).putInt(declared).putShort((short) nameBytes.length).putShort((short) 0)
                .putShort((short) 0).putShort((short) 0).putShort((short) 0).putInt(0).putInt(0)
                .put(nameBytes);
        int centralSize = bb.position() - centralStart;
        bb.putInt(0x06054b50).putShort((short) 0).putShort((short) 0).putShort((short) 1).putShort((short) 1)
                .putInt(centralSize).putInt(centralStart).putShort((short) 0);
        return Arrays.copyOf(bb.array(), bb.position());
    }

    private static byte[] readAll(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static void deleteAll(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}
