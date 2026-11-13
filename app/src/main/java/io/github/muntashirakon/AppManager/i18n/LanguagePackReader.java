// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a language file. The file is untrusted: it's size-capped, decoded as strict UTF-8,
 * depth-checked before the JSON parser sees it, and only text fields are kept. Nothing in a
 * language file is ever run or used as a path except its validated language code.
 */
public final class LanguagePackReader {
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 4000;
    static final int MAX_NAME_CHARS = 64;
    static final int MAX_TRANSLATORS = 20;
    static final int MAX_TRANSLATOR_CHARS = 80;
    static final int MAX_KEY_CHARS = 200;
    static final int MAX_ENTRIES = 20000;
    private static final int MAX_DEPTH = 8;

    private static final Pattern CODE = Pattern.compile(
            "([a-zA-Z]{2,3})(?:[-_]([a-zA-Z]{4}))?(?:[-_]([a-zA-Z]{2}|[0-9]{3}))?");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.]+");

    private LanguagePackReader() {
    }

    @NonNull
    public static LanguagePack read(@NonNull byte[] data) throws LanguagePackException {
        if (data.length > MAX_FILE_BYTES) {
            throw new LanguagePackException(LanguagePackException.Reason.TOO_LARGE, "File is " + data.length + " bytes");
        }
        String json = decodeUtf8(data);
        if (!json.isEmpty() && json.charAt(0) == 0xFEFF) {
            json = json.substring(1);
        }
        if (nestingDepth(json) > MAX_DEPTH) {
            throw notAPack("Nested too deeply", null);
        }
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw notAPack("Not a JSON object", e);
        }
        Object schema = root.opt("schema_version");
        if (!(schema instanceof Number)) {
            throw notAPack("No schema_version", null);
        }
        int schemaVersion = ((Number) schema).intValue();
        if (schemaVersion > LanguagePack.SCHEMA_VERSION) {
            throw new LanguagePackException(LanguagePackException.Reason.NEWER_SCHEMA,
                    "schema_version " + schemaVersion);
        }
        if (schemaVersion < 1) {
            throw notAPack("schema_version " + schemaVersion, null);
        }
        Object translationsObj = root.opt("translations");
        if (!(translationsObj instanceof JSONObject)) {
            throw notAPack("No translations object", null);
        }
        Object codeObj = root.opt("language_code");
        String code = codeObj instanceof String ? normalizeCode((String) codeObj) : null;
        if (code == null) {
            throw new LanguagePackException(LanguagePackException.Reason.BAD_CODE, "language_code " + codeObj);
        }
        if (code.equals("en")) {
            throw new LanguagePackException(LanguagePackException.Reason.ENGLISH, "language_code en");
        }
        Object nameObj = root.opt("language_name");
        String name = nameObj instanceof String ? ((String) nameObj).trim() : "";
        if (name.isEmpty() || name.length() > MAX_NAME_CHARS || !isCleanLabel(name)) {
            throw new LanguagePackException(LanguagePackException.Reason.BAD_NAME, "language_name");
        }
        Object versionObj = root.opt("translation_version");
        int version = versionObj instanceof Number ? Math.max(0, ((Number) versionObj).intValue()) : 0;
        List<String> translators = readTranslators(root.opt("translators"));

        JSONObject translations = (JSONObject) translationsObj;
        Map<String, LanguagePack.Entry> entries = new LinkedHashMap<>();
        int malformed = 0;
        Iterator<String> keys = translations.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (entries.size() >= MAX_ENTRIES) {
                throw new LanguagePackException(LanguagePackException.Reason.TOO_LARGE, "Too many entries");
            }
            if (key.length() > MAX_KEY_CHARS || !KEY.matcher(key).matches()) {
                ++malformed;
                continue;
            }
            Object value = translations.opt(key);
            if (!(value instanceof JSONObject)) {
                ++malformed;
                continue;
            }
            JSONObject entry = (JSONObject) value;
            TextValue translation;
            TextValue original;
            try {
                translation = readValue(entry.opt("translation"));
                original = readValue(entry.opt("original"));
            } catch (IllegalArgumentException e) {
                ++malformed;
                continue;
            }
            if (translation == null || translation.isBlank()) {
                // Not translated yet.
                continue;
            }
            entries.put(key, new LanguagePack.Entry(original, translation));
        }
        return new LanguagePack(code, name, version, translators, entries, malformed);
    }

    /**
     * Returns {@code code} as {@code ll}, {@code ll-Ssss}, {@code ll-RR} or {@code ll-Ssss-RR}
     * with the usual casing, or {@code null} when it isn't one of those shapes.
     */
    @Nullable
    public static String normalizeCode(@NonNull String code) {
        Matcher m = CODE.matcher(code.trim());
        if (!m.matches()) {
            return null;
        }
        String language = m.group(1).toLowerCase(Locale.ROOT);
        if (language.equals("und") || language.equals("zxx") || language.equals("mul") || language.startsWith("qa")) {
            return null;
        }
        StringBuilder sb = new StringBuilder(language);
        if (m.group(2) != null) {
            String script = m.group(2);
            sb.append('-').append(Character.toUpperCase(script.charAt(0)))
                    .append(script.substring(1).toLowerCase(Locale.ROOT));
        }
        if (m.group(3) != null) {
            sb.append('-').append(m.group(3).toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    @Nullable
    private static TextValue readValue(@Nullable Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof String) {
            return TextValue.ofString((String) value);
        }
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            Map<String, String> forms = new LinkedHashMap<>();
            for (String quantity : SourceString.QUANTITIES) {
                Object form = obj.opt(quantity);
                if (form == null) {
                    continue;
                }
                if (!(form instanceof String)) {
                    throw new IllegalArgumentException("Plural form " + quantity);
                }
                forms.put(quantity, (String) form);
            }
            if (forms.size() != obj.length()) {
                throw new IllegalArgumentException("Unknown plural quantity");
            }
            return TextValue.ofForms(forms);
        }
        throw new IllegalArgumentException("Not text");
    }

    @NonNull
    private static List<String> readTranslators(@Nullable Object value) {
        List<String> out = new ArrayList<>();
        if (!(value instanceof JSONArray)) {
            return out;
        }
        JSONArray array = (JSONArray) value;
        for (int i = 0; i < array.length() && out.size() < MAX_TRANSLATORS; ++i) {
            Object item = array.opt(i);
            if (item instanceof String) {
                String name = ((String) item).trim();
                if (!name.isEmpty() && name.length() <= MAX_TRANSLATOR_CHARS && isCleanLabel(name)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    /** Labels shown in lists: no control characters and no bidirectional overrides. */
    static boolean isCleanLabel(@NonNull String s) {
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F) || (c >= 0x202A && c <= 0x202E)
                    || (c >= 0x2066 && c <= 0x2069)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static String decodeUtf8(@NonNull byte[] data) throws LanguagePackException {
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            return chars.toString();
        } catch (CharacterCodingException e) {
            throw notAPack("Not UTF-8", e);
        }
    }

    /** Deepest object/array nesting outside string literals. */
    static int nestingDepth(@NonNull String json) {
        int depth = 0;
        int max = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); ++i) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    ++i;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"':
                    inString = true;
                    break;
                case '{':
                case '[':
                    max = Math.max(max, ++depth);
                    break;
                case '}':
                case ']':
                    --depth;
                    break;
            }
        }
        return max;
    }

    @NonNull
    private static LanguagePackException notAPack(@NonNull String message, @Nullable Throwable cause) {
        return cause == null
                ? new LanguagePackException(LanguagePackException.Reason.NOT_A_PACK, message)
                : new LanguagePackException(LanguagePackException.Reason.NOT_A_PACK, message, cause);
    }
}
