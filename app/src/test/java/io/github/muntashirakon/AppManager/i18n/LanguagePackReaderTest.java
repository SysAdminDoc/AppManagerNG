// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

public class LanguagePackReaderTest {
    @Test
    public void readsAPack() throws Exception {
        LanguagePack pack = LanguagePackReader.read(bytes("{"
                + "\"schema_version\": 1, \"language_code\": \"pt_br\", \"language_name\": \"  Portugues  \","
                + "\"translation_version\": 3, \"translators\": [\"Ana\", \"\", 5, \"Bad\\u202Ename\"],"
                + "\"translations\": {"
                + "  \"cancel\": {\"original\": \"Cancel\", \"translation\": \"Cancelar\"},"
                + "  \"apps\": {\"original\": {\"one\": \"%d app\", \"other\": \"%d apps\"},"
                + "           \"translation\": {\"one\": \"%d app\", \"other\": \"%d apps\", \"many\": \"\"}},"
                + "  \"empty\": {\"translation\": \"\"},"
                + "  \"spaces only\": {\"translation\": \"x\"},"
                + "  \"not_object\": \"x\","
                + "  \"bad_form\": {\"translation\": {\"one\": 1, \"other\": \"x\"}},"
                + "  \"bad_quantity\": {\"translation\": {\"other\": \"x\", \"several\": \"y\"}},"
                + "  \"number\": {\"translation\": 5}"
                + "}}"));
        assertEquals("pt-BR", pack.code);
        assertEquals("Portugues", pack.name);
        assertEquals(3, pack.version);
        assertEquals(Arrays.asList("Ana"), pack.translators);
        // The JVM org.json used by unit tests keeps keys in hash order, Android keeps file order
        assertEquals(new HashSet<>(Arrays.asList("cancel", "apps")), pack.entries.keySet());
        assertEquals(TextValue.ofString("Cancelar"), pack.entries.get("cancel").translation);
        assertEquals(TextValue.ofString("Cancel"), pack.entries.get("cancel").original);
        assertTrue(pack.entries.get("apps").translation.isForms());
        assertEquals(5, pack.malformedEntries);
    }

    @Test
    public void entriesWithoutOriginalAreKept() throws Exception {
        LanguagePack pack = LanguagePackReader.read(pack("\"cancel\": {\"translation\": \"Abbrechen\"}"));
        assertNull(pack.entries.get("cancel").original);
    }

    @Test
    public void byteOrderMarkIsSkipped() throws Exception {
        byte[] body = pack("");
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertEquals("de", LanguagePackReader.read(withBom).code);
    }

    @Test
    public void rejectsWhatIsntAPack() {
        assertReason(LanguagePackException.Reason.NOT_A_PACK, bytes("not json"));
        assertReason(LanguagePackException.Reason.NOT_A_PACK, bytes("[1, 2]"));
        assertReason(LanguagePackException.Reason.NOT_A_PACK, bytes("{\"language_code\": \"de\"}"));
        assertReason(LanguagePackException.Reason.NOT_A_PACK, bytes("{\"schema_version\": 0, \"translations\": {}}"));
        assertReason(LanguagePackException.Reason.NOT_A_PACK, bytes("{\"schema_version\": 1, \"translations\": []}"));
        assertReason(LanguagePackException.Reason.NOT_A_PACK, new byte[]{'{', (byte) 0xC3, '}'});
        StringBuilder deep = new StringBuilder("{\"schema_version\": 1, \"translations\": ");
        for (int i = 0; i < 20; ++i) deep.append("[");
        for (int i = 0; i < 20; ++i) deep.append("]");
        assertReason(LanguagePackException.Reason.NOT_A_PACK, bytes(deep.append("}").toString()));
    }

    @Test
    public void bracketsInsideStringsDontCountAsNesting() {
        assertEquals(1, LanguagePackReader.nestingDepth("{\"a\": \"[[[[[[[[[[{{{{{{{{\\\"]]]\"}"));
    }

    @Test
    public void rejectsNewerSchemas() {
        assertReason(LanguagePackException.Reason.NEWER_SCHEMA,
                bytes("{\"schema_version\": 2, \"language_code\": \"de\", \"language_name\": \"Deutsch\", \"translations\": {}}"));
    }

    @Test
    public void rejectsBadCodesAndNames() {
        assertReason(LanguagePackException.Reason.BAD_CODE, header("\"german\"", "\"Deutsch\""));
        assertReason(LanguagePackException.Reason.BAD_CODE, header("\"und\"", "\"Deutsch\""));
        assertReason(LanguagePackException.Reason.BAD_CODE, header("\"\"", "\"Deutsch\""));
        assertReason(LanguagePackException.Reason.BAD_CODE, header("7", "\"Deutsch\""));
        assertReason(LanguagePackException.Reason.ENGLISH, header("\"EN\"", "\"English\""));
        assertReason(LanguagePackException.Reason.BAD_NAME, header("\"de\"", "\"  \""));
        assertReason(LanguagePackException.Reason.BAD_NAME, header("\"de\"", "\"Deutsch\\u202E\""));
        assertReason(LanguagePackException.Reason.BAD_NAME, header("\"de\"", "null"));
    }

    @Test
    public void rejectsOversizedFiles() {
        assertReason(LanguagePackException.Reason.TOO_LARGE, new byte[LanguagePackReader.MAX_FILE_BYTES + 1]);
        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= LanguagePackReader.MAX_ENTRIES; ++i) {
            if (i > 0) many.append(',');
            many.append("\"k").append(i).append("\":{\"translation\":\"x\"}");
        }
        assertReason(LanguagePackException.Reason.TOO_LARGE, pack(many.toString()));
    }

    @Test
    public void normalizesCodes() {
        assertEquals("de", LanguagePackReader.normalizeCode("DE"));
        assertEquals("pt-BR", LanguagePackReader.normalizeCode(" pt_br "));
        assertEquals("zh-Hant-TW", LanguagePackReader.normalizeCode("zh_HANT_tw"));
        assertEquals("es-419", LanguagePackReader.normalizeCode("es-419"));
        assertEquals("fil", LanguagePackReader.normalizeCode("fil"));
        assertNull(LanguagePackReader.normalizeCode("x"));
        assertNull(LanguagePackReader.normalizeCode("qaa"));
        assertNull(LanguagePackReader.normalizeCode("de-DE-x"));
        assertNull(LanguagePackReader.normalizeCode("../de"));
    }

    @Test
    public void cleanLabelsRejectControlAndBidiCharacters() {
        assertTrue(LanguagePackReader.isCleanLabel("Deutsch (Schweiz)"));
        assertFalse(LanguagePackReader.isCleanLabel("a\nb"));
        assertFalse(LanguagePackReader.isCleanLabel("a" + (char) 0x2067 + "b"));
        assertFalse(LanguagePackReader.isCleanLabel("a" + (char) 0x85 + "b"));
    }

    @Test
    public void translatorsAreCapped() throws Exception {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < 30; ++i) {
            if (i > 0) names.append(',');
            names.append("\"Name ").append(i).append('"');
        }
        LanguagePack pack = LanguagePackReader.read(bytes("{\"schema_version\": 1, \"language_code\": \"de\","
                + "\"language_name\": \"Deutsch\", \"translators\": [" + names + "], \"translations\": {}}"));
        assertEquals(LanguagePackReader.MAX_TRANSLATORS, pack.translators.size());
    }

    @Test
    public void withCodeKeepsEverythingElse() throws Exception {
        LanguagePack pack = LanguagePackReader.read(pack("\"cancel\": {\"translation\": \"Abbrechen\"}"));
        LanguagePack renamed = pack.withCode("de-AT");
        assertEquals("de-AT", renamed.code);
        assertEquals(pack.name, renamed.name);
        assertEquals(pack.entries.keySet(), renamed.entries.keySet());
    }

    private static void assertReason(LanguagePackException.Reason reason, byte[] data) {
        LanguagePackException e = assertThrows(LanguagePackException.class, () -> LanguagePackReader.read(data));
        assertEquals(e.getMessage(), reason, e.reason);
    }

    private static byte[] header(String code, String name) {
        return bytes("{\"schema_version\": 1, \"language_code\": " + code + ", \"language_name\": " + name
                + ", \"translations\": {}}");
    }

    static byte[] pack(String translations) {
        return bytes("{\"schema_version\": 1, \"language_code\": \"de\", \"language_name\": \"Deutsch\","
                + " \"translations\": {" + translations + "}}");
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
