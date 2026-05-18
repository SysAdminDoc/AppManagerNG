// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.importers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

public class ExternalProfileImporterTest {

    // -----------------------------------------------------------------------
    // Canta
    // -----------------------------------------------------------------------

    @Test
    public void cantaParsesPackagesArray() throws JSONException {
        String json = "{\"packages\":[\"com.foo.bar\",\"com.example.baz\"]}";
        String[] out = ExternalProfileImporter.parseCanta(json);
        assertArrayEquals(new String[]{"com.foo.bar", "com.example.baz"}, out);
    }

    @Test
    public void cantaParsesAppsArray() throws JSONException {
        String json = "{\"apps\":[\"com.a.b\",\"com.c.d\"]}";
        String[] out = ExternalProfileImporter.parseCanta(json);
        assertArrayEquals(new String[]{"com.a.b", "com.c.d"}, out);
    }

    @Test
    public void cantaParsesBareArray() throws JSONException {
        String json = "[\"com.x.y\", \"com.z.w\"]";
        String[] out = ExternalProfileImporter.parseCanta(json);
        assertArrayEquals(new String[]{"com.x.y", "com.z.w"}, out);
    }

    @Test
    public void cantaDeduplicatesAndDropsNonPackageStrings() throws JSONException {
        String json = "{\"packages\":[\"com.foo.bar\",\"NOT a pkg\",\"com.foo.bar\",\"com.baz.qux\"]}";
        String[] out = ExternalProfileImporter.parseCanta(json);
        assertArrayEquals(new String[]{"com.foo.bar", "com.baz.qux"}, out);
    }

    @Test
    public void cantaThrowsWhenNoArrayPresent() {
        try {
            ExternalProfileImporter.parseCanta("{\"foo\":1}");
            fail("Expected JSONException when no recognisable array present");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("Canta preset"));
        }
    }

    // -----------------------------------------------------------------------
    // UAD-NG
    // -----------------------------------------------------------------------

    @Test
    public void uadNgWalksNestedSelectionMap() throws JSONException {
        String json = "{"
                + "\"selected_user_packages_serial\":{"
                + "  \"DEVICE-AAAA\":{\"user0\":[\"com.samsung.bloat.one\",\"com.samsung.bloat.two\"]},"
                + "  \"DEVICE-BBBB\":{\"user10\":[\"com.miui.bloat\",\"com.samsung.bloat.one\"]}"
                + "}}";
        String[] out = ExternalProfileImporter.parseUadNg(json);
        assertEquals(3, out.length);
        assertEquals("com.samsung.bloat.one", out[0]);
        assertEquals("com.samsung.bloat.two", out[1]);
        assertEquals("com.miui.bloat", out[2]);
    }

    @Test
    public void uadNgIgnoresNonPackageStrings() throws JSONException {
        String json = "{"
                + "\"selected_user_packages_serial\":{"
                + "  \"DEVICE-CCCC\":{\"user0\":[\"com.real.pkg\",\"random label text\",\"not-a-pkg\"]}"
                + "}}";
        String[] out = ExternalProfileImporter.parseUadNg(json);
        assertArrayEquals(new String[]{"com.real.pkg"}, out);
    }

    @Test
    public void uadNgThrowsWhenNoPackagesFound() {
        try {
            ExternalProfileImporter.parseUadNg("{\"label\":\"hello world\"}");
            fail("Expected JSONException for UAD-NG file with no packages");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("UAD-NG"));
        }
    }

    // -----------------------------------------------------------------------
    // Hail
    // -----------------------------------------------------------------------

    @Test
    public void hailParsesPlainList() {
        String text = "com.foo.bar\ncom.example.baz\n";
        String[] out = ExternalProfileImporter.parseHail(text);
        assertArrayEquals(new String[]{"com.foo.bar", "com.example.baz"}, out);
    }

    @Test
    public void hailIgnoresBlankAndCommentLines() {
        String text = "# header\n"
                + "com.foo.bar\n"
                + "\n"
                + "   \n"
                + "# another comment\n"
                + "com.example.baz\n";
        String[] out = ExternalProfileImporter.parseHail(text);
        assertArrayEquals(new String[]{"com.foo.bar", "com.example.baz"}, out);
    }

    @Test
    public void hailStripsTrailingFlagAfterPipeOrWhitespace() {
        String text = "com.foo.bar|f\n"
                + "com.example.baz   active\n"
                + "com.qux.zoo\tfrozen\n";
        String[] out = ExternalProfileImporter.parseHail(text);
        assertArrayEquals(new String[]{"com.foo.bar", "com.example.baz", "com.qux.zoo"}, out);
    }

    @Test
    public void hailDeduplicates() {
        String text = "com.foo.bar\ncom.foo.bar\ncom.example.baz\n";
        String[] out = ExternalProfileImporter.parseHail(text);
        assertArrayEquals(new String[]{"com.foo.bar", "com.example.baz"}, out);
    }

    @Test
    public void hailDropsNonPackageLines() {
        String text = "this is not a package\ncom.foo.bar\n";
        String[] out = ExternalProfileImporter.parseHail(text);
        assertArrayEquals(new String[]{"com.foo.bar"}, out);
    }

    // -----------------------------------------------------------------------
    // Format detection
    // -----------------------------------------------------------------------

    @Test
    public void detectionPicksCantaForObjectWithPackages() {
        assertEquals(ExternalProfileImporter.Format.CANTA,
                ExternalProfileImporter.detectFormat("{\"packages\":[]}", "canta-export.json"));
    }

    @Test
    public void detectionPicksUadNgForSelectedUserPackagesSerialKey() {
        assertEquals(ExternalProfileImporter.Format.UAD_NG,
                ExternalProfileImporter.detectFormat(
                        "{\"selected_user_packages_serial\":{}}", "settings.json"));
    }

    @Test
    public void detectionPicksUadNgFromFileNameHintWhenJsonShapeIsAmbiguous() {
        // No selected_user_packages key but the filename mentions UAD.
        assertEquals(ExternalProfileImporter.Format.UAD_NG,
                ExternalProfileImporter.detectFormat("{\"data\":{}}", "uad-export.json"));
    }

    @Test
    public void detectionPicksHailForBareTextList() {
        assertEquals(ExternalProfileImporter.Format.HAIL,
                ExternalProfileImporter.detectFormat("com.foo.bar\ncom.baz.qux\n", "tags.txt"));
    }

    // -----------------------------------------------------------------------
    // looksLikePackageName guard
    // -----------------------------------------------------------------------

    @Test
    public void looksLikePackageNameAcceptsValidNames() {
        assertTrue(ExternalProfileImporter.looksLikePackageName("com.android.vending"));
        assertTrue(ExternalProfileImporter.looksLikePackageName("io.github.sysadmindoc.AppManagerNG"));
        assertTrue(ExternalProfileImporter.looksLikePackageName("a.b"));
    }

    @Test
    public void looksLikePackageNameRejectsInvalidNames() {
        assertFalse(ExternalProfileImporter.looksLikePackageName(""));
        assertFalse(ExternalProfileImporter.looksLikePackageName("com.foo bar"));
        assertFalse(ExternalProfileImporter.looksLikePackageName("com.foo;rm"));
        assertFalse(ExternalProfileImporter.looksLikePackageName("noperiod"));
        assertFalse(ExternalProfileImporter.looksLikePackageName(".leadingdot"));
        assertFalse(ExternalProfileImporter.looksLikePackageName("1com.starts.with.digit"));
    }
}
