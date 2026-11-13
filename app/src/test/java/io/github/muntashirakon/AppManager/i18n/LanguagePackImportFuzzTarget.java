// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Feeds untrusted bytes through everything an imported language file goes through: reading,
 * checking, compiling the resources table, and exporting it for editing. Only a classified
 * {@link LanguagePackException} may come out, and an exported file has to read back with the
 * same translations.
 */
public final class LanguagePackImportFuzzTarget {
    private static final String PACKAGE = "io.github.muntashirakon.AppManager";

    private LanguagePackImportFuzzTarget() {
    }

    public static void fuzzerTestOneInput(byte[] data) {
        LanguagePack pack;
        try {
            pack = LanguagePackReader.read(data);
        } catch (LanguagePackException expected) {
            return;
        }
        FakeSourceStrings source = FakeSourceStrings.sample();
        LanguagePackValidator.Report report = LanguagePackValidator.check(pack, source);
        if (report.usable + report.needsFix + report.unknown != pack.entries.size() + pack.malformedEntries) {
            throw new AssertionError("Report counts don't add up");
        }
        try {
            byte[] table = LanguagePackCompiler.compile(PACKAGE, source, pack, (p, t) -> "t" + t);
            if (report.usable == 0) {
                throw new AssertionError("Compiled a file with nothing usable");
            }
            int size = (table[4] & 0xFF) | (table[5] & 0xFF) << 8 | (table[6] & 0xFF) << 16 | (table[7] & 0xFF) << 24;
            if (table[0] != 0x02 || table[1] != 0x00 || size != table.length) {
                throw new AssertionError("Not a resources table");
            }
        } catch (LanguagePackException e) {
            if (report.usable != 0 || e.reason != LanguagePackException.Reason.NOTHING_USABLE) {
                throw new AssertionError("Compile failed for a usable file", e);
            }
        }

        String exported = LanguagePackWriter.forEditing(pack, report, source,
                Arrays.asList(SourceString.QUANTITIES), null);
        LanguagePack again;
        try {
            again = LanguagePackReader.read(exported.getBytes(StandardCharsets.UTF_8));
        } catch (LanguagePackException e) {
            if (e.reason == LanguagePackException.Reason.TOO_LARGE) {
                // The export adds every string and its status, so a file near the cap can outgrow it.
                return;
            }
            throw new AssertionError("An exported file can't be read back", e);
        }
        if (!pack.code.equals(again.code) || !pack.name.equals(again.name) || pack.version != again.version
                || again.malformedEntries != 0
                || !pack.translators.equals(again.translators)) {
            throw new AssertionError("Export changed the header");
        }
        if (!pack.entries.keySet().equals(again.entries.keySet())) {
            throw new AssertionError("Export lost or added entries");
        }
        for (Map.Entry<String, LanguagePack.Entry> e : pack.entries.entrySet()) {
            if (!Objects.equals(filled(e.getValue().translation), filled(again.entries.get(e.getKey()).translation))) {
                throw new AssertionError("Export changed the translation of " + e.getKey());
            }
        }
    }

    /** The value without empty plural forms, which an export adds for every form a language uses. */
    private static Object filled(TextValue value) {
        if (!value.isForms()) {
            return value.string();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> form : value.forms().entrySet()) {
            if (!form.getValue().isEmpty()) {
                out.put(form.getKey(), form.getValue());
            }
        }
        return out;
    }
}
