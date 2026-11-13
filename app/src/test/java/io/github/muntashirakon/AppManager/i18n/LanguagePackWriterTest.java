// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LanguagePackWriterTest {
    private static final List<String> ONE_OTHER = Arrays.asList("one", "other");

    private final FakeSourceStrings mSource = FakeSourceStrings.sample();

    @Test
    public void templateListsEveryEnglishStringInOrder() throws Exception {
        Map<String, TextValue> prefill = new HashMap<>();
        prefill.put("cancel", TextValue.ofString("Abbrechen"));
        Map<String, String> appsForms = new LinkedHashMap<>();
        appsForms.put("other", "%d Apps");
        prefill.put("apps", TextValue.ofForms(appsForms));
        String json = LanguagePackWriter.template("de", "Deutsch", mSource, prefill, ONE_OTHER, "AppManagerNG 1.0");

        JSONObject root = new JSONObject(json);
        assertEquals(LanguagePack.SCHEMA_VERSION, root.getInt("schema_version"));
        assertEquals("de", root.getString("language_code"));
        assertEquals("AppManagerNG 1.0", root.getString("made_with"));
        assertEquals(LanguagePackWriter.INSTRUCTIONS, root.getString("instructions"));
        JSONObject translations = root.getJSONObject("translations");
        assertEquals(new HashSet<>(mSource.keys()), keySet(translations));
        assertEquals("Cancel", translations.getJSONObject("cancel").getString("original"));
        assertEquals("Abbrechen", translations.getJSONObject("cancel").getString("translation"));
        assertEquals("", translations.getJSONObject("apps_count").getString("translation"));
        assertEquals("<b>Be careful</b> here", translations.getJSONObject("warning").getString("original"));
        assertTrue(translations.getJSONObject("help_link").getString("original").contains(FakeSourceStrings.LINK));
        JSONObject apps = translations.getJSONObject("apps").getJSONObject("translation");
        assertEquals("", apps.getString("one"));
        assertEquals("%d Apps", apps.getString("other"));
        assertEquals("%d apps", translations.getJSONObject("apps").getJSONObject("original").getString("other"));

        int last = -1;
        for (String key : mSource.keys()) {
            int at = json.indexOf("\n    \"" + key + "\": {");
            assertTrue(key, at > last);
            last = at;
        }
        // Links stay readable for whoever edits the file
        assertTrue(json.contains("https://example.org/help"));
    }

    @Test
    public void templateReadsBackWithOnlyTheFilledInStrings() throws Exception {
        Map<String, TextValue> prefill = Collections.singletonMap("cancel", TextValue.ofString("Abbrechen"));
        String json = LanguagePackWriter.template("pt-BR", "Portugues", mSource, prefill, ONE_OTHER, null);
        assertFalse(json.contains("made_with"));
        LanguagePack pack = LanguagePackReader.read(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("pt-BR", pack.code);
        assertEquals(Collections.singleton("cancel"), pack.entries.keySet());
        assertEquals(0, pack.malformedEntries);
    }

    @Test
    public void templateUsesTheTargetLanguagesPluralForms() throws Exception {
        String json = LanguagePackWriter.template("ru", "Russkii", mSource, Collections.emptyMap(),
                Arrays.asList("many", "one", "few", "other"), null);
        JSONObject apps = new JSONObject(json).getJSONObject("translations").getJSONObject("apps")
                .getJSONObject("translation");
        assertEquals(new HashSet<>(Arrays.asList("one", "few", "many", "other")), keySet(apps));
        int one = json.indexOf("\"one\": \"\"");
        int few = json.indexOf("\"few\": \"\"");
        int many = json.indexOf("\"many\": \"\"");
        int other = json.indexOf("\"other\": \"\"");
        assertTrue(one < few && few < many && many < other);
    }

    @Test
    public void stringsTheFileCantShowAreLeftOut() throws Exception {
        FakeSourceStrings source = new FakeSourceStrings()
                .string("plain", "Plain")
                .styled("odd", new StyledText("x", Collections.singletonList(new StyledText.Span("font;color=red", 0, 1))));
        String json = LanguagePackWriter.template("de", "Deutsch", source, Collections.emptyMap(), ONE_OTHER, null);
        assertEquals(Collections.singleton("plain"), keySet(new JSONObject(json).getJSONObject("translations")));
    }

    @Test
    public void orderedQuantitiesAlwaysEndWithOther() {
        assertEquals(Arrays.asList("one", "few", "many", "other"),
                LanguagePackWriter.orderedQuantities(Arrays.asList("many", "one", "few")));
        assertEquals(Collections.singletonList("other"), LanguagePackWriter.orderedQuantities(Collections.emptyList()));
        assertEquals(Arrays.asList("zero", "one", "two", "few", "many", "other"),
                LanguagePackWriter.orderedQuantities(Arrays.asList(SourceString.QUANTITIES)));
    }

    @Test
    public void stringsSurviveTheRoundTrip() throws Exception {
        String tricky = "say \"hi\" \\ back\nnew\r\ttab " + (char) 1 + " " + (char) 0x2028 + (char) 0x2029
                + " lone " + (char) 0xD800 + " emoji " + (char) 0xD83D + (char) 0xDE00 + " low " + (char) 0xDC00
                + " </b> & <";
        String out = LanguagePackWriter.string(new StringBuilder(), tricky).toString();
        for (int i = 0; i < out.length(); ++i) {
            char c = out.charAt(i);
            boolean emoji = c == (char) 0xD83D || c == (char) 0xDE00;
            assertTrue("raw char " + (int) c, emoji || (c >= 0x20 && c != 0x2028 && c != 0x2029 && !Character.isSurrogate(c)));
        }
        assertTrue(out.contains(esc("2028")));
        assertTrue(out.contains(esc("d800")));
        assertTrue(out.contains(esc("0001")));
        assertEquals(tricky, new JSONObject("{\"v\": " + out + "}").getString("v"));
    }

    @Test
    public void exportMarksWhatNeedsWork() throws Exception {
        Map<String, LanguagePack.Entry> entries = new LinkedHashMap<>();
        entries.put("gone_key", LanguagePackValidatorTest.entry("Gone", "Weg"));
        entries.put("cancel", LanguagePackValidatorTest.entry("Cancel", "Abbrechen"));
        entries.put("apps_count", LanguagePackValidatorTest.entry("%1$s has %2$d app(s)", "%1$s hat %2$d Apps"));
        entries.put("warning", LanguagePackValidatorTest.entry(null, "<script>x</script>"));
        entries.put("apps", LanguagePackValidatorTest.entry(null, "Apps"));
        Map<String, String> items = new LinkedHashMap<>();
        items.put("one", "Noch ein Element");
        items.put("other", "Noch %d Elemente");
        entries.put("items_left", new LanguagePack.Entry(null, TextValue.ofForms(items)));
        LanguagePack pack = new LanguagePack("de", "Deutsch", 4, Arrays.asList("Ana", "Ben"), entries, 0);
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, mSource);

        String json = LanguagePackWriter.forEditing(pack, report, mSource, ONE_OTHER, null);
        JSONObject root = new JSONObject(json);
        assertEquals(4, root.getInt("translation_version"));
        assertEquals(2, root.getJSONArray("translators").length());
        JSONObject t = root.getJSONObject("translations");
        assertFalse(t.getJSONObject("cancel").has("status"));
        assertEquals("Abbrechen", t.getJSONObject("cancel").getString("translation"));
        assertEquals(LanguagePackWriter.STATUS_OUTDATED, t.getJSONObject("apps_count").getString("status"));
        assertEquals("%1$s has %2$d app(s)", t.getJSONObject("apps_count").getString("previous_original"));
        assertEquals("%1$s has %2$d apps", t.getJSONObject("apps_count").getString("original"));
        assertEquals(LanguagePackWriter.STATUS_NEEDS_FIX, t.getJSONObject("warning").getString("status"));
        assertFalse(t.getJSONObject("warning").getString("problem").isEmpty());
        assertEquals(LanguagePackWriter.STATUS_NEEDS_FIX, t.getJSONObject("apps").getString("status"));
        assertEquals("Apps", t.getJSONObject("apps").getString("translation"));
        assertEquals(LanguagePackWriter.STATUS_MISSING, t.getJSONObject("percent_done").getString("status"));
        assertEquals(LanguagePackWriter.STATUS_MISSING, t.getJSONObject("help_link").getString("status"));
        assertFalse(t.getJSONObject("items_left").has("status"));
        assertEquals(LanguagePackWriter.STATUS_UNUSED, t.getJSONObject("gone_key").getString("status"));
        assertTrue(json.indexOf("\"gone_key\"") > json.indexOf("\"items_left\""));

        LanguagePack again = LanguagePackReader.read(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(entries.keySet(), again.entries.keySet());
        for (String key : entries.keySet()) {
            assertEquals(key, entries.get(key).translation, again.entries.get(key).translation);
        }
        assertEquals(Arrays.asList("Ana", "Ben"), again.translators);
        assertEquals(4, again.version);
    }

    private static String esc(String hex) {
        return (char) 92 + "u" + hex;
    }

    private static Set<String> keySet(JSONObject o) {
        Set<String> out = new HashSet<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }
}
