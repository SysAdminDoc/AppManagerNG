// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a minimal compiled resources table ({@code resources.arsc}) holding one language's
 * strings and plurals, laid out the way aapt2 writes them. Android 11+ loads it through a
 * {@code ResourcesLoader}, and because a loader's entry wins over the app's own entry for the same
 * configuration, the table's strings replace the app's text for that language only. Entries use
 * the app's own resource IDs, so the table has to be rebuilt whenever the app is updated.
 */
public final class ArscWriter {
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_TABLE_TYPE = 0x0002;
    private static final int RES_TABLE_PACKAGE_TYPE = 0x0200;
    private static final int RES_TABLE_TYPE_TYPE = 0x0201;
    private static final int RES_TABLE_TYPE_SPEC_TYPE = 0x0202;

    private static final int PACKAGE_HEADER_SIZE = 288;
    private static final int CONFIG_SIZE = 64;
    private static final int TYPE_HEADER_SIZE = 20 + CONFIG_SIZE;
    private static final int CONFIG_LOCALE = 0x0004;
    private static final int TYPE_STRING = 0x03;
    private static final int FLAG_COMPLEX = 0x0001;
    private static final int NO_ENTRY = 0xFFFFFFFF;
    private static final int SPAN_END = 0xFFFFFFFF;

    // Res_MAKEINTERNAL(4..9): the bag keys of plural quantities.
    private static final Map<String, Integer> QUANTITY_ATTRS = new LinkedHashMap<>();

    static {
        QUANTITY_ATTRS.put("other", 0x01000004);
        QUANTITY_ATTRS.put("zero", 0x01000005);
        QUANTITY_ATTRS.put("one", 0x01000006);
        QUANTITY_ATTRS.put("two", 0x01000007);
        QUANTITY_ATTRS.put("few", 0x01000008);
        QUANTITY_ATTRS.put("many", 0x01000009);
    }

    private static final Pattern LOCALE = Pattern.compile(
            "([a-z]{2,3})(?:-([A-Z][a-z]{3}))?(?:-([A-Z]{2}|[0-9]{3}))?");

    public static final class Entry {
        final int typeId;
        final int entryIndex;
        @NonNull
        final String key;
        @Nullable
        final StyledText value;
        @Nullable
        final SortedMap<Integer, StyledText> bag;

        private Entry(int typeId, int entryIndex, @NonNull String key, @Nullable StyledText value,
                      @Nullable SortedMap<Integer, StyledText> bag) {
            if (typeId < 1 || typeId > 0xFF || entryIndex < 0 || entryIndex > 0xFFFF) {
                throw new IllegalArgumentException("Bad resource ID for " + key);
            }
            this.typeId = typeId;
            this.entryIndex = entryIndex;
            this.key = key;
            this.value = value;
            this.bag = bag;
        }

        @NonNull
        public static Entry string(int typeId, int entryIndex, @NonNull String key, @NonNull StyledText value) {
            return new Entry(typeId, entryIndex, key, value, null);
        }

        @NonNull
        public static Entry plural(int typeId, int entryIndex, @NonNull String key,
                                   @NonNull Map<String, StyledText> forms) {
            SortedMap<Integer, StyledText> bag = new TreeMap<>(Integer::compareUnsigned);
            for (Map.Entry<String, StyledText> form : forms.entrySet()) {
                Integer attr = QUANTITY_ATTRS.get(form.getKey());
                if (attr == null) {
                    throw new IllegalArgumentException("Unknown quantity " + form.getKey());
                }
                bag.put(attr, form.getValue());
            }
            if (!bag.containsKey(QUANTITY_ATTRS.get("other"))) {
                throw new IllegalArgumentException(key + " has no other form");
            }
            return new Entry(typeId, entryIndex, key, null, bag);
        }
    }

    private ArscWriter() {
    }

    /**
     * @param packageName the app's resource package name, as {@code Resources#getResourcePackageName} reports it
     * @param packageId   the app's package ID, normally {@code 0x7f}
     * @param typeNames   type names in ID order, {@code typeNames.get(0)} being type ID 1
     * @param localeCode  a code {@link LanguagePackReader#normalizeCode} produced
     */
    @NonNull
    public static byte[] write(@NonNull String packageName, int packageId, @NonNull List<String> typeNames,
                               @NonNull String localeCode, @NonNull List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No entries");
        }
        byte[] config = localeConfig(localeCode);

        // Global value pool: styled strings first (the pool format requires it), then plain
        // values, then the span names styled strings refer to.
        Map<StyledText, Integer> styled = new LinkedHashMap<>();
        Map<String, Integer> plain = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.value != null) {
                addValue(e.value, styled, plain);
            } else {
                for (StyledText form : e.bag.values()) {
                    addValue(form, styled, plain);
                }
            }
        }
        for (StyledText st : styled.keySet()) {
            for (StyledText.Span span : st.spans) {
                plain.putIfAbsent(span.name, plain.size());
            }
        }
        int styledCount = styled.size();
        List<String> poolStrings = new ArrayList<>(styledCount + plain.size());
        int i = 0;
        for (Map.Entry<StyledText, Integer> s : styled.entrySet()) {
            s.setValue(i++);
            poolStrings.add(s.getKey().text);
        }
        for (Map.Entry<String, Integer> p : plain.entrySet()) {
            p.setValue(i++);
            poolStrings.add(p.getKey());
        }
        List<int[]> styleSpans = new ArrayList<>(styledCount);
        for (StyledText st : styled.keySet()) {
            int[] spans = new int[st.spans.size() * 3];
            int j = 0;
            for (StyledText.Span span : st.spans) {
                spans[j++] = plain.get(span.name);
                spans[j++] = span.start;
                spans[j++] = span.end - 1;
            }
            styleSpans.add(spans);
        }
        byte[] valuePool = stringPool(poolStrings, styleSpans);

        // Key pool and per-type entry tables.
        Map<String, Integer> keys = new LinkedHashMap<>();
        SortedMap<Integer, SortedMap<Integer, Entry>> byType = new TreeMap<>();
        for (Entry e : entries) {
            keys.putIfAbsent(e.key, keys.size());
            byType.computeIfAbsent(e.typeId, t -> new TreeMap<>()).put(e.entryIndex, e);
        }
        if (byType.lastKey() > typeNames.size()) {
            throw new IllegalArgumentException("Type " + byType.lastKey() + " has no name");
        }
        byte[] typePool = stringPool(typeNames, new ArrayList<>());
        byte[] keyPool = stringPool(new ArrayList<>(keys.keySet()), new ArrayList<>());

        Buf pkg = new Buf();
        pkg.u16(RES_TABLE_PACKAGE_TYPE).u16(PACKAGE_HEADER_SIZE).u32(0);
        pkg.u32(packageId);
        char[] name = new char[128];
        packageName.getChars(0, Math.min(127, packageName.length()), name, 0);
        for (char c : name) {
            pkg.u16(c);
        }
        pkg.u32(PACKAGE_HEADER_SIZE);
        pkg.u32(typeNames.size());
        pkg.u32(PACKAGE_HEADER_SIZE + typePool.length);
        pkg.u32(keys.size());
        pkg.u32(0); // typeIdOffset
        pkg.bytes(typePool).bytes(keyPool);
        for (Map.Entry<Integer, SortedMap<Integer, Entry>> type : byType.entrySet()) {
            writeType(pkg, type.getKey(), type.getValue(), config, styled, plain, keys);
        }
        pkg.setU32(4, pkg.length());

        Buf table = new Buf();
        table.u16(RES_TABLE_TYPE).u16(12).u32(0).u32(1);
        table.bytes(valuePool).bytes(pkg.toByteArray());
        table.setU32(4, table.length());
        return table.toByteArray();
    }

    private static void addValue(@NonNull StyledText value, @NonNull Map<StyledText, Integer> styled,
                                 @NonNull Map<String, Integer> plain) {
        if (value.isStyled()) {
            styled.putIfAbsent(value, styled.size());
        } else {
            plain.putIfAbsent(value.text, plain.size());
        }
    }

    private static int valueIndex(@NonNull StyledText value, @NonNull Map<StyledText, Integer> styled,
                                  @NonNull Map<String, Integer> plain) {
        Integer index = value.isStyled() ? styled.get(value) : plain.get(value.text);
        if (index == null) {
            throw new IllegalStateException("Value missing from pool");
        }
        return index;
    }

    private static void writeType(@NonNull Buf pkg, int typeId, @NonNull SortedMap<Integer, Entry> entries,
                                  @NonNull byte[] config, @NonNull Map<StyledText, Integer> styled,
                                  @NonNull Map<String, Integer> plain, @NonNull Map<String, Integer> keys) {
        int entryCount = entries.lastKey() + 1;
        // Type spec: which configuration axes each entry varies by.
        pkg.u16(RES_TABLE_TYPE_SPEC_TYPE).u16(16).u32(16 + 4 * entryCount);
        // id, res0, typesCount (one type chunk follows), entryCount
        pkg.u8(typeId).u8(0).u16(1).u32(entryCount);
        for (int e = 0; e < entryCount; ++e) {
            pkg.u32(entries.containsKey(e) ? CONFIG_LOCALE : 0);
        }
        // Type: one configuration (the language) and its entries.
        int typeStart = pkg.length();
        int entriesStart = TYPE_HEADER_SIZE + 4 * entryCount;
        pkg.u16(RES_TABLE_TYPE_TYPE).u16(TYPE_HEADER_SIZE).u32(0);
        pkg.u8(typeId).u8(0).u16(0).u32(entryCount).u32(entriesStart);
        pkg.bytes(config);
        int offsetsStart = pkg.length();
        for (int e = 0; e < entryCount; ++e) {
            pkg.u32(NO_ENTRY);
        }
        Buf data = new Buf();
        for (Map.Entry<Integer, Entry> item : entries.entrySet()) {
            pkg.setU32(offsetsStart + 4 * item.getKey(), data.length());
            Entry e = item.getValue();
            int key = keys.get(e.key);
            if (e.value != null) {
                data.u16(8).u16(0).u32(key);
                data.u16(8).u8(0).u8(TYPE_STRING).u32(valueIndex(e.value, styled, plain));
            } else {
                data.u16(16).u16(FLAG_COMPLEX).u32(key).u32(0).u32(e.bag.size());
                for (Map.Entry<Integer, StyledText> form : e.bag.entrySet()) {
                    data.u32(form.getKey());
                    data.u16(8).u8(0).u8(TYPE_STRING).u32(valueIndex(form.getValue(), styled, plain));
                }
            }
        }
        pkg.bytes(data.toByteArray());
        pkg.setU32(typeStart + 4, pkg.length() - typeStart);
    }

    /** A UTF-16 {@code ResStringPool}. {@code styles.get(i)} holds string i's spans as (name, first, last) triples. */
    @NonNull
    static byte[] stringPool(@NonNull List<String> strings, @NonNull List<int[]> styles) {
        int stringCount = strings.size();
        int styleCount = styles.size();
        Buf data = new Buf();
        int[] offsets = new int[stringCount];
        for (int s = 0; s < stringCount; ++s) {
            offsets[s] = data.length();
            String str = strings.get(s);
            int len = str.length();
            if (len > 0x7FFF) {
                data.u16(0x8000 | (len >>> 16)).u16(len & 0xFFFF);
            } else {
                data.u16(len);
            }
            for (int c = 0; c < len; ++c) {
                data.u16(str.charAt(c));
            }
            data.u16(0);
        }
        while ((data.length() & 3) != 0) {
            data.u8(0);
        }
        Buf styleData = new Buf();
        int[] styleOffsets = new int[styleCount];
        for (int s = 0; s < styleCount; ++s) {
            styleOffsets[s] = styleData.length();
            for (int v : styles.get(s)) {
                styleData.u32(v);
            }
            styleData.u32(SPAN_END);
        }
        if (styleCount > 0) {
            // The platform expects a whole ResStringPool_span of 0xFFFFFFFF closing the block.
            styleData.u32(SPAN_END).u32(SPAN_END);
        }
        int headerSize = 28;
        int stringsStart = headerSize + 4 * stringCount + 4 * styleCount;
        int stylesStart = styleCount > 0 ? stringsStart + data.length() : 0;
        Buf pool = new Buf();
        pool.u16(RES_STRING_POOL_TYPE).u16(headerSize).u32(0);
        pool.u32(stringCount).u32(styleCount).u32(0).u32(stringsStart).u32(stylesStart);
        for (int o : offsets) {
            pool.u32(o);
        }
        for (int o : styleOffsets) {
            pool.u32(o);
        }
        pool.bytes(data.toByteArray()).bytes(styleData.toByteArray());
        pool.setU32(4, pool.length());
        return pool.toByteArray();
    }

    /** A 64-byte {@code ResTable_config} that sets only the locale, as aapt2 writes it. */
    @NonNull
    static byte[] localeConfig(@NonNull String code) {
        Matcher m = LOCALE.matcher(code);
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad locale " + code);
        }
        byte[] config = new byte[CONFIG_SIZE];
        config[0] = (byte) CONFIG_SIZE;
        packLanguageOrRegion(m.group(1), 'a', config, 8);
        if (m.group(3) != null) {
            packLanguageOrRegion(m.group(3), '0', config, 10);
        }
        if (m.group(2) != null) {
            byte[] script = m.group(2).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(script, 0, config, 36, 4);
        }
        return config;
    }

    /** {@code packLanguageOrRegion} from the platform's ResourceTypes.cpp. */
    private static void packLanguageOrRegion(@NonNull String in, char base, @NonNull byte[] out, int offset) {
        if (in.length() == 2) {
            out[offset] = (byte) in.charAt(0);
            out[offset + 1] = (byte) in.charAt(1);
            return;
        }
        int first = (in.charAt(0) - base) & 0x7f;
        int second = (in.charAt(1) - base) & 0x7f;
        int third = (in.charAt(2) - base) & 0x7f;
        out[offset] = (byte) (0x80 | (third << 2) | (second >> 3));
        out[offset + 1] = (byte) ((second << 5) | first);
    }

    /** Little-endian byte buffer. */
    static final class Buf {
        private byte[] mData = new byte[256];
        private int mLength;

        private void ensure(int extra) {
            if (mLength + extra > mData.length) {
                mData = Arrays.copyOf(mData, Math.max(mData.length * 2, mLength + extra));
            }
        }

        Buf u8(int v) {
            ensure(1);
            mData[mLength++] = (byte) v;
            return this;
        }

        Buf u16(int v) {
            ensure(2);
            mData[mLength++] = (byte) v;
            mData[mLength++] = (byte) (v >>> 8);
            return this;
        }

        Buf u32(int v) {
            ensure(4);
            mData[mLength++] = (byte) v;
            mData[mLength++] = (byte) (v >>> 8);
            mData[mLength++] = (byte) (v >>> 16);
            mData[mLength++] = (byte) (v >>> 24);
            return this;
        }

        Buf bytes(@NonNull byte[] b) {
            ensure(b.length);
            System.arraycopy(b, 0, mData, mLength, b.length);
            mLength += b.length;
            return this;
        }

        void setU32(int pos, int v) {
            mData[pos] = (byte) v;
            mData[pos + 1] = (byte) (v >>> 8);
            mData[pos + 2] = (byte) (v >>> 16);
            mData[pos + 3] = (byte) (v >>> 24);
        }

        int length() {
            return mLength;
        }

        @NonNull
        byte[] toByteArray() {
            return Arrays.copyOf(mData, mLength);
        }
    }
}
