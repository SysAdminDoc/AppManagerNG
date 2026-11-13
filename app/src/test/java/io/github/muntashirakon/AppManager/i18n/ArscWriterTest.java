// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArscWriterTest {
    private static final String PACKAGE = "io.github.muntashirakon.AppManager";
    private static final int NO_ENTRY = 0xFFFFFFFF;

    @Test
    public void writesATableTheWayAapt2Does() {
        Map<String, StyledText> forms = new LinkedHashMap<>();
        forms.put("one", StyledText.plain("%d App"));
        forms.put("other", StyledText.plain("%d Apps"));
        StyledText styled = new StyledText("Sei vorsichtig hier",
                Collections.singletonList(new StyledText.Span(StyledText.BOLD, 0, 14)));
        byte[] table = ArscWriter.write(PACKAGE, 0x7f, FakeSourceStrings.typeNames(), "de", Arrays.asList(
                ArscWriter.Entry.string(2, 0, "cancel", StyledText.plain("Abbrechen")),
                ArscWriter.Entry.string(2, 3, "warning", styled),
                ArscWriter.Entry.plural(1, 0, "apps", forms)));
        ByteBuffer b = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x0002, b.getShort(0));
        assertEquals(12, b.getShort(2));
        assertEquals(table.length, b.getInt(4));
        assertEquals(1, b.getInt(8));

        // Value pool: styled strings first, then plain values in entry order (plural forms sorted by
        // their bag key, so other comes before one), then the span names.
        Pool values = Pool.read(b, 12);
        assertEquals(Arrays.asList("Sei vorsichtig hier", "Abbrechen", "%d Apps", "%d App", "b"), values.strings);
        assertEquals(1, values.styles.size());
        assertArrayEquals(new int[]{4, 0, 13}, values.styles.get(0));
        assertTrue(values.endsWithSpanEnd);

        int pkg = 12 + values.size;
        assertEquals(0x0200, b.getShort(pkg));
        assertEquals(288, b.getShort(pkg + 2));
        assertEquals(table.length - pkg, b.getInt(pkg + 4));
        assertEquals(0x7f, b.getInt(pkg + 8));
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 128 && b.getChar(pkg + 12 + 2 * i) != 0; ++i) {
            name.append(b.getChar(pkg + 12 + 2 * i));
        }
        assertEquals(PACKAGE, name.toString());
        assertEquals(288, b.getInt(pkg + 268));
        assertEquals(2, b.getInt(pkg + 272));
        Pool types = Pool.read(b, pkg + 288);
        assertEquals(FakeSourceStrings.typeNames(), types.strings);
        assertEquals(288 + types.size, b.getInt(pkg + 276));
        assertEquals(3, b.getInt(pkg + 280));
        Pool keys = Pool.read(b, pkg + b.getInt(pkg + 276));
        assertEquals(Arrays.asList("cancel", "warning", "apps"), keys.strings);

        // plurals: one entry, a bag of two forms
        int at = pkg + 288 + types.size + keys.size;
        at = checkSpec(b, at, 1, new int[]{4});
        int type1 = at;
        int entries1 = checkTypeHeader(b, at, 1, 1);
        assertEquals(0, b.getInt(type1 + 84));
        int e = type1 + entries1;
        assertEquals(16, b.getShort(e));
        assertEquals(1, b.getShort(e + 2));
        assertEquals(2, b.getInt(e + 4));
        assertEquals(0, b.getInt(e + 8));
        assertEquals(2, b.getInt(e + 12));
        assertEquals(0x01000004, b.getInt(e + 16));
        checkStringValue(b, e + 20, 2);
        assertEquals(0x01000006, b.getInt(e + 28));
        checkStringValue(b, e + 32, 3);
        at = type1 + b.getInt(type1 + 4);

        // strings: entries 0 and 3, nothing at 1 and 2
        at = checkSpec(b, at, 2, new int[]{4, 0, 0, 4});
        int type2 = at;
        int entries2 = checkTypeHeader(b, at, 2, 4);
        assertEquals(0, b.getInt(type2 + 84));
        assertEquals(NO_ENTRY, b.getInt(type2 + 88));
        assertEquals(NO_ENTRY, b.getInt(type2 + 92));
        assertEquals(16, b.getInt(type2 + 96));
        int first = type2 + entries2;
        assertEquals(8, b.getShort(first));
        assertEquals(0, b.getShort(first + 2));
        assertEquals(0, b.getInt(first + 4));
        checkStringValue(b, first + 8, 1);
        assertEquals(1, b.getInt(first + 20));
        checkStringValue(b, first + 24, 0);
        assertEquals(table.length, type2 + b.getInt(type2 + 4));
    }

    @Test
    public void localeConfigPacksLanguageScriptAndRegion() {
        byte[] de = ArscWriter.localeConfig("de");
        assertEquals(64, de[0]);
        assertEquals('d', de[8]);
        assertEquals('e', de[9]);
        assertEquals(0, de[10]);

        byte[] ptBr = ArscWriter.localeConfig("pt-BR");
        assertEquals("ptBR", new String(new char[]{(char) ptBr[8], (char) ptBr[9], (char) ptBr[10], (char) ptBr[11]}));

        byte[] srLatn = ArscWriter.localeConfig("sr-Latn");
        assertEquals("Latn", new String(Arrays.copyOfRange(srLatn, 36, 40), java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(0, srLatn[10]);

        byte[] fil = ArscWriter.localeConfig("fil");
        assertEquals((byte) 0xAD, fil[8]);
        assertEquals((byte) 0x05, fil[9]);
        assertEquals("fil", unpack(fil[8], fil[9], 'a'));

        byte[] latam = ArscWriter.localeConfig("es-419");
        assertEquals("419", unpack(latam[10], latam[11], '0'));

        assertThrows(IllegalArgumentException.class, () -> ArscWriter.localeConfig("pt_BR"));
        assertThrows(IllegalArgumentException.class, () -> ArscWriter.localeConfig("DE"));
    }

    @Test
    public void longStringsGetTheTwoWordLength() {
        char[] chars = new char[0x12345];
        Arrays.fill(chars, 'x');
        String longOne = new String(chars);
        byte[] pool = ArscWriter.stringPool(Arrays.asList("short", longOne), new ArrayList<>());
        ByteBuffer b = ByteBuffer.wrap(pool).order(ByteOrder.LITTLE_ENDIAN);
        int stringsStart = b.getInt(20);
        int second = stringsStart + b.getInt(28 + 4);
        assertEquals((short) 0x8001, b.getShort(second));
        assertEquals((short) 0x2345, b.getShort(second + 2));
        assertEquals(Arrays.asList("short", longOne), Pool.read(b, 0).strings);
        assertEquals(0, pool.length % 4);
    }

    @Test
    public void rejectsWhatATableCantHold() {
        Map<String, StyledText> noOther = Collections.singletonMap("one", StyledText.plain("x"));
        assertThrows(IllegalArgumentException.class, () -> ArscWriter.Entry.plural(1, 0, "k", noOther));
        Map<String, StyledText> unknown = new LinkedHashMap<>();
        unknown.put("other", StyledText.plain("x"));
        unknown.put("several", StyledText.plain("y"));
        assertThrows(IllegalArgumentException.class, () -> ArscWriter.Entry.plural(1, 0, "k", unknown));
        assertThrows(IllegalArgumentException.class, () -> ArscWriter.Entry.string(0, 0, "k", StyledText.plain("x")));
        assertThrows(IllegalArgumentException.class, () -> ArscWriter.Entry.string(1, 0x10000, "k", StyledText.plain("x")));
        assertThrows(IllegalArgumentException.class,
                () -> ArscWriter.write(PACKAGE, 0x7f, FakeSourceStrings.typeNames(), "de", Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> ArscWriter.write(PACKAGE, 0x7f,
                Collections.singletonList("plurals"), "de",
                Collections.singletonList(ArscWriter.Entry.string(2, 0, "k", StyledText.plain("x")))));
    }

    @Test
    public void compilerKeepsOnlyUsableEntriesUnderTheAppsIds() throws Exception {
        Map<String, LanguagePack.Entry> entries = new LinkedHashMap<>();
        entries.put("cancel", LanguagePackValidatorTest.entry("Cancel", "Abbrechen"));
        entries.put("apps_count", LanguagePackValidatorTest.entry(null, "%1$d kaputt"));
        entries.put("gone_key", LanguagePackValidatorTest.entry(null, "Weg"));
        entries.put("help_link", LanguagePackValidatorTest.entry(null,
                "Lies <a href=\"" + FakeSourceStrings.LINK + "\">die Hilfe</a>"));
        LanguagePack pack = new LanguagePack("de-AT", "Deutsch (AT)", 1, Collections.emptyList(), entries, 0);
        byte[] table = LanguagePackCompiler.compile(PACKAGE, FakeSourceStrings.sample(), pack, (p, t) -> "t" + t);
        ByteBuffer b = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);
        Pool values = Pool.read(b, 12);
        assertEquals(Arrays.asList("Lies die Hilfe", "Abbrechen", StyledText.LINK_PREFIX + FakeSourceStrings.LINK),
                values.strings);
        int pkg = 12 + values.size;
        Pool types = Pool.read(b, pkg + 288);
        // Only strings are in the table, so the plurals type gets the stand-in name
        assertEquals(Arrays.asList("t1", "string"), types.strings);
        Pool keys = Pool.read(b, pkg + b.getInt(pkg + 276));
        assertEquals(Arrays.asList("cancel", "help_link"), keys.strings);
        int type = pkg + 288 + types.size + keys.size;
        type += b.getInt(type + 4);
        assertEquals('d', b.get(type + 20 + 8));
        assertEquals('A', b.get(type + 20 + 10));
    }

    @Test
    public void compilerRefusesAPackWithNothingUsable() {
        Map<String, LanguagePack.Entry> entries = Collections.singletonMap("gone_key",
                LanguagePackValidatorTest.entry(null, "Weg"));
        LanguagePack pack = new LanguagePack("de", "Deutsch", 1, Collections.emptyList(), entries, 0);
        LanguagePackException e = assertThrows(LanguagePackException.class,
                () -> LanguagePackCompiler.compile(PACKAGE, FakeSourceStrings.sample(), pack, (p, t) -> "t" + t));
        assertEquals(LanguagePackException.Reason.NOTHING_USABLE, e.reason);
    }

    private static int checkSpec(ByteBuffer b, int at, int typeId, int[] flags) {
        assertEquals(0x0202, b.getShort(at));
        assertEquals(16, b.getShort(at + 2));
        assertEquals(16 + 4 * flags.length, b.getInt(at + 4));
        assertEquals(typeId, b.get(at + 8));
        assertEquals(1, b.getShort(at + 10));
        assertEquals(flags.length, b.getInt(at + 12));
        for (int i = 0; i < flags.length; ++i) {
            assertEquals(flags[i], b.getInt(at + 16 + 4 * i));
        }
        return at + 16 + 4 * flags.length;
    }

    /** Returns where the entries start, relative to the type chunk. */
    private static int checkTypeHeader(ByteBuffer b, int at, int typeId, int entryCount) {
        assertEquals(0x0201, b.getShort(at));
        assertEquals(84, b.getShort(at + 2));
        assertEquals(typeId, b.get(at + 8));
        assertEquals(entryCount, b.getInt(at + 12));
        int entriesStart = b.getInt(at + 16);
        assertEquals(84 + 4 * entryCount, entriesStart);
        assertEquals(64, b.getInt(at + 20));
        assertEquals('d', b.get(at + 28));
        assertEquals('e', b.get(at + 29));
        return entriesStart;
    }

    private static void checkStringValue(ByteBuffer b, int at, int poolIndex) {
        assertEquals(8, b.getShort(at));
        assertEquals(0, b.get(at + 2));
        assertEquals(0x03, b.get(at + 3));
        assertEquals(poolIndex, b.getInt(at + 4));
    }

    /** {@code unpackLanguageOrRegion} from the platform's ResourceTypes.cpp. */
    private static String unpack(byte in0, byte in1, char base) {
        int first = in1 & 0x1f;
        int second = ((in1 & 0xe0) >> 5) + ((in0 & 0x03) << 3);
        int third = (in0 & 0x7c) >> 2;
        return new String(new char[]{(char) (first + base), (char) (second + base), (char) (third + base)});
    }

    /** A UTF-16 string pool read back the way the platform reads it. */
    private static final class Pool {
        final List<String> strings = new ArrayList<>();
        final List<int[]> styles = new ArrayList<>();
        int size;
        boolean endsWithSpanEnd;

        static Pool read(ByteBuffer b, int at) {
            Pool p = new Pool();
            assertEquals(0x0001, b.getShort(at));
            assertEquals(28, b.getShort(at + 2));
            p.size = b.getInt(at + 4);
            assertEquals(0, p.size % 4);
            int stringCount = b.getInt(at + 8);
            int styleCount = b.getInt(at + 12);
            assertEquals(0, b.getInt(at + 16));
            int stringsStart = b.getInt(at + 20);
            int stylesStart = b.getInt(at + 24);
            for (int i = 0; i < stringCount; ++i) {
                int s = at + stringsStart + b.getInt(at + 28 + 4 * i);
                int len = b.getChar(s);
                s += 2;
                if ((len & 0x8000) != 0) {
                    len = ((len & 0x7FFF) << 16) | b.getChar(s);
                    s += 2;
                }
                char[] chars = new char[len];
                for (int c = 0; c < len; ++c) {
                    chars[c] = b.getChar(s + 2 * c);
                }
                assertEquals(0, b.getChar(s + 2 * len));
                p.strings.add(new String(chars));
            }
            for (int i = 0; i < styleCount; ++i) {
                int s = at + stylesStart + b.getInt(at + 28 + 4 * stringCount + 4 * i);
                List<Integer> spans = new ArrayList<>();
                while (b.getInt(s) != 0xFFFFFFFF) {
                    spans.add(b.getInt(s));
                    s += 4;
                }
                int[] out = new int[spans.size()];
                for (int j = 0; j < out.length; ++j) out[j] = spans.get(j);
                p.styles.add(out);
                if (i == styleCount - 1) {
                    p.endsWithSpanEnd = b.getInt(s + 4) == 0xFFFFFFFF && b.getInt(s + 8) == 0xFFFFFFFF
                            && s + 12 == at + p.size;
                }
            }
            return p;
        }
    }
}
