// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class LanguagePackValidatorTest {
    private final FakeSourceStrings mSource = FakeSourceStrings.sample();

    @Test
    public void plainTranslationIsUsable() {
        LanguagePackValidator.Checked c = check("cancel", "Cancel", "Abbrechen");
        assertEquals(LanguagePackValidator.Status.OK, c.status);
        assertEquals("Abbrechen", c.text.text);
        assertNull(c.problem);
    }

    @Test
    public void changedEnglishMarksItOutdatedButUsable() {
        LanguagePackValidator.Checked c = check("cancel", "Cancel now", "Abbrechen");
        assertEquals(LanguagePackValidator.Status.OUTDATED, c.status);
        assertTrue(c.isUsable());
        assertEquals(LanguagePackValidator.Status.OK, check("cancel", null, "Abbrechen").status);
    }

    @Test
    public void unknownKeysAndWrongKindsAreRejected() {
        assertEquals(LanguagePackValidator.Status.UNKNOWN_KEY, check("nope", null, "x").status);
        assertEquals(LanguagePackValidator.Status.WRONG_KIND, checkForms("cancel", "other", "Abbrechen").status);
        LanguagePackValidator.Checked plural = check("apps", null, "Apps");
        assertEquals(LanguagePackValidator.Status.WRONG_KIND, plural.status);
        assertFalse(plural.isUsable());
        assertNotNull(plural.problem);
    }

    @Test
    public void placeholdersHaveToMatchTheEnglish() {
        assertEquals(LanguagePackValidator.Status.OK, check("apps_count", null, "%1$s hat %2$d Apps").status);
        assertEquals(LanguagePackValidator.Status.OK, check("apps_count", null, "%2$d Apps in %1$s").status);
        assertEquals(LanguagePackValidator.Status.BAD_PLACEHOLDERS, check("apps_count", null, "%1$d hat %2$d").status);
        assertEquals(LanguagePackValidator.Status.BAD_PLACEHOLDERS, check("apps_count", null, "%1$s hat Apps").status);
        assertEquals(LanguagePackValidator.Status.BAD_PLACEHOLDERS,
                check("apps_count", null, "%1$s hat %2$d Apps %").status);
    }

    @Test
    public void englishWithoutPlaceholdersTakesLiteralPercentSigns() {
        assertEquals(LanguagePackValidator.Status.OK, check("cancel", null, "Abbrechen 100%").status);
        LanguagePackValidator.Checked c = check("cancel", null, "Abbrechen %s");
        assertEquals(LanguagePackValidator.Status.BAD_PLACEHOLDERS, c.status);
        assertNotNull(c.problem);
    }

    @Test
    public void englishThatCantBeFormattedTakesAnything() {
        assertEquals(LanguagePackValidator.Status.OK, check("percent_done", null, "Halb fertig (50 %)").status);
        assertEquals(LanguagePackValidator.Status.OK, check("percent_done", null, "Halb %d fertig").status);
    }

    @Test
    public void markupIsOnlyReadWhereTheEnglishIsStyled() {
        LanguagePackValidator.Checked styled = check("warning", null, "<b>Sei vorsichtig</b> hier");
        assertEquals(LanguagePackValidator.Status.OK, styled.status);
        assertEquals("Sei vorsichtig hier", styled.text.text);
        assertTrue(styled.text.isStyled());
        assertEquals(LanguagePackValidator.Status.BAD_MARKUP, check("warning", null, "<script>x</script>").status);
        assertEquals(LanguagePackValidator.Status.BAD_MARKUP, check("warning", null, "<b>offen").status);
        LanguagePackValidator.Checked plain = check("cancel", null, "<b>Abbrechen</b>");
        assertEquals(LanguagePackValidator.Status.OK, plain.status);
        assertEquals("<b>Abbrechen</b>", plain.text.text);
        assertFalse(plain.text.isStyled());
    }

    @Test
    public void linksHaveToBeTheEnglishOnes() {
        String good = "Lies <a href=\"" + FakeSourceStrings.LINK + "\">die Hilfe</a> zuerst";
        assertEquals(LanguagePackValidator.Status.OK, check("help_link", null, good).status);
        String evil = "Lies <a href=\"https://evil.example\">die Hilfe</a> zuerst";
        assertEquals(LanguagePackValidator.Status.BAD_MARKUP, check("help_link", null, evil).status);
    }

    @Test
    public void controlCharactersAndHugeTextAreRejected() {
        assertEquals(LanguagePackValidator.Status.BAD_TEXT, check("cancel", null, "Ab" + (char) 1 + "brechen").status);
        assertEquals(LanguagePackValidator.Status.BAD_TEXT, check("cancel", null, "Ab" + (char) 0x9B + "brechen").status);
        assertEquals(LanguagePackValidator.Status.OK, check("cancel", null, "Ab\nbrechen\tjetzt").status);
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i <= LanguagePackReader.MAX_TEXT_CHARS; ++i) huge.append('x');
        assertEquals(LanguagePackValidator.Status.BAD_TEXT, check("cancel", null, huge.toString()).status);
    }

    @Test
    public void pluralsNeedAnOtherForm() {
        assertEquals(LanguagePackValidator.Status.NO_OTHER_FORM, checkForms("apps", "one", "%d App").status);
        assertEquals(LanguagePackValidator.Status.NO_OTHER_FORM,
                checkForms("apps", "one", "%d App", "other", "  ").status);
    }

    @Test
    public void pluralFormsMayLeaveTheNumberOut() {
        LanguagePackValidator.Checked c = checkForms("items_left", "one", "Noch ein Element", "other", "Noch %d Elemente");
        assertEquals(LanguagePackValidator.Status.OK, c.status);
        assertEquals("Noch ein Element", c.forms.get("one").text);
        assertEquals("Noch %d Elemente", c.forms.get("other").text);
    }

    @Test
    public void pluralFormsCantAddPlaceholders() {
        LanguagePackValidator.Checked c = checkForms("apps", "one", "%s App", "other", "%d Apps");
        assertEquals(LanguagePackValidator.Status.BAD_PLACEHOLDERS, c.status);
        assertTrue(c.problem, c.problem.startsWith("one: "));
        assertEquals(LanguagePackValidator.Status.BAD_TEXT,
                checkForms("apps", "other", "%d" + (char) 7 + "Apps").status);
    }

    @Test
    public void emptyPluralFormsAreLeftOut() {
        LanguagePackValidator.Checked c = checkForms("apps", "one", "", "other", "%d Apps");
        assertEquals(LanguagePackValidator.Status.OK, c.status);
        assertEquals(Collections.singleton("other"), c.forms.keySet());
    }

    @Test
    public void reportCountsEachKind() {
        Map<String, LanguagePack.Entry> entries = new LinkedHashMap<>();
        entries.put("cancel", entry("Cancel", "Abbrechen"));
        entries.put("apps_count", entry("%1$s has %2$d app(s)", "%1$s hat %2$d Apps"));
        entries.put("warning", entry(null, "<script>x</script>"));
        entries.put("gone_key", entry(null, "Weg"));
        LanguagePack pack = new LanguagePack("de", "Deutsch", 1, Collections.emptyList(), entries, 2);
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, mSource);
        assertEquals(7, report.sourceCount);
        assertEquals(2, report.usable);
        assertEquals(1, report.outdated);
        assertEquals(1, report.needsFix);
        assertEquals(3, report.unknown);
        assertEquals(LanguagePackValidator.Status.BAD_MARKUP, report.entries.get("warning").status);
    }

    private LanguagePackValidator.Checked check(String key, String original, String translation) {
        return LanguagePackValidator.checkEntry(key, entry(original, translation), mSource.get(key));
    }

    private LanguagePackValidator.Checked checkForms(String key, String... pairs) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            forms.put(pairs[i], pairs[i + 1]);
        }
        return LanguagePackValidator.checkEntry(key, new LanguagePack.Entry(null, TextValue.ofForms(forms)),
                mSource.get(key));
    }

    static LanguagePack.Entry entry(String original, String translation) {
        return new LanguagePack.Entry(original == null ? null : TextValue.ofString(original),
                TextValue.ofString(translation));
    }
}
