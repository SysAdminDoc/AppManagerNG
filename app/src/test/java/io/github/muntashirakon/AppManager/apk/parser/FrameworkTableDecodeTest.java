// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.io.IoUtils;

/**
 * Binary manifests compiled by aapt2 from {@code xml/framework_ref.source.xml} against SDK 21, 28,
 * 35, 37.0 and 37.2. Its activity label is {@code @*android:dimen/status_bar_height}, a private
 * framework resource whose ID moves with nearly every SDK (0x01050010 in 21, 0x0105019d in 28,
 * 0x010502fb in 35, 0x01050368 in 37.0, 0x01050365 in 37.2). Every other value is public.
 */
@RunWith(RobolectricTestRunner.class)
public class FrameworkTableDecodeTest {
    /** android:versionName in the fixtures; its resource map entry is swapped for #2040. */
    private static final int ATTR_VERSION_NAME = 0x0101021c;
    /** android:backgroundPermission in the API 29 to 32 tables, autofillSaveCustomSubtitleMaxHeight from 33 on. */
    private static final int PRIVATE_ATTR_2040 = 0x01120017;
    private static final int RES_XML_RESOURCE_MAP_TYPE = 0x0180;
    /** primaryUserOnly in the Android 5.0 framework table, android:isSplitRequired in the newest. */
    private static final int PLATFORM_PRIVATE_ATTR = 0x01010591;
    /**
     * The decoder writes a framework reference without its package, as the existing expected outputs
     * do ({@code @style/Theme.Holo.Light}); an app reference prints as a raw ID, so a named one is
     * always the framework's.
     */
    private static final String NAMED_STATUS_BAR_HEIGHT = "@\\*?(android:)?dimen/status_bar_height";

    @Test
    public void publicAttributesAndValuesDecodeTheSameForEveryApi() throws IOException {
        for (String api : new String[]{"21", "28", "35", "37_0", "37_2"}) {
            String xml = decodeFixture(api);
            String where = "API " + api + ":\n" + xml;
            assertEquals(where, "7", value(xml, "android:versionCode"));
            assertEquals(where, "1.7", value(xml, "android:versionName"));
            assertEquals(where, "21", value(xml, "android:minSdkVersion"));
            assertEquals(where, "21", value(xml, "android:targetSdkVersion"));
            assertEquals(where, "Fixture", value(xml, "android:label"));
            assertEquals(where, "false", value(xml, "android:allowBackup"));
            assertEquals(where, "@style/Theme.Material.Light", value(xml, "android:theme"));
            assertEquals(where, "singleTask", value(xml, "android:launchMode"));
            assertEquals(where, "portrait", value(xml, "android:screenOrientation"));
            assertEquals(where, flags("orientation", "keyboardHidden", "screenSize"),
                    flags(value(xml, "android:configChanges")));
            assertEquals(where, flags("stateHidden", "adjustResize"), flags(value(xml, "android:windowSoftInputMode")));
            assertEquals(where, "true", value(xml, "android:exported"));
            assertEquals(where, "5", value(xml, "android:priority"));
            assertTrue(where, xml.contains("android:name=\"android.intent.action.MAIN\""));
        }
    }

    @Test
    public void aPrivateReferenceIsNamedOnlyWhenTheCompileSdkPinsItsTable() throws IOException {
        // No compile metadata before API 28, and the SDKs disagree on 0x01050010
        assertEquals("@0x01050010", activityLabel(decodeFixture("21")));
        // One SDK per API level: the table is certain
        assertNamedStatusBarHeight(activityLabel(decodeFixture("28")));
        assertNamedStatusBarHeight(activityLabel(decodeFixture("35")));
        // 37.0, 37.1 and 37.2 all write compileSdkVersion 37 and place the dimen differently
        assertEquals("@0x01050368", activityLabel(decodeFixture("37_0")));
        assertEquals("@0x01050365", activityLabel(decodeFixture("37_2")));
    }

    @Test
    public void theCompileSdkComesFromTheManifest() throws IOException {
        assertEquals(FrameworkTables.UNKNOWN_API, compileSdkOf(fixture("21")));
        assertEquals(28, compileSdkOf(fixture("28")));
        assertEquals(35, compileSdkOf(fixture("35")));
        assertEquals(37, compileSdkOf(fixture("37_0")));
        assertEquals(37, compileSdkOf(fixture("37_2")));
    }

    @Test
    public void platformBuildVersionCodeStandsInForCompileSdkVersion() throws IOException {
        byte[] manifest = fixture("35");
        // android:compileSdkVersion becomes an app attribute, leaving platformBuildVersionCode="35"
        replaceResourceId(manifest, FrameworkTables.ATTR_COMPILE_SDK_VERSION, 0x7f010000);

        assertEquals(35, compileSdkOf(manifest));
    }

    @Test
    public void upstream2040BackgroundPermissionIsNotNamedFromANewerTable() throws IOException {
        // The Android 12 framework manifest sets android:backgroundPermission, private ID 0x01120017
        byte[] manifest = fixture("21");
        replaceResourceId(manifest, ATTR_VERSION_NAME, PRIVATE_ATTR_2040);

        assertEquals("1.7", value(AndroidBinXmlDecoder.decode(ByteBuffer.wrap(manifest), 31), "android:backgroundPermission"));
        assertEquals("1.7", value(AndroidBinXmlDecoder.decode(ByteBuffer.wrap(manifest), 30), "android:backgroundPermission"));
        // Compiled against 33, the same ID really is that attribute
        assertEquals("1.7", value(AndroidBinXmlDecoder.decode(ByteBuffer.wrap(manifest), 33),
                "android:autofillSaveCustomSubtitleMaxHeight"));
        // Unknown: neither name is certain
        String unknown = AndroidBinXmlDecoder.decode(ByteBuffer.wrap(manifest));
        assertEquals(unknown, "1.7", value(unknown, "android:r0x01120017"));
        assertFalse(unknown, unknown.contains("autofillSaveCustomSubtitleMaxHeight"));
        assertFalse(unknown, unknown.contains("backgroundPermission"));
    }

    /**
     * The platform builds its own manifests against its full table and wrote no compile metadata
     * before API 28. The Android 5.0 framework manifest sets 0x01010591, private primaryUserOnly
     * there and isSplitRequired in the newest table.
     */
    @Test
    public void aManifestTargetingAnApiBeforeMetadataNamesNoDisputedAttribute() throws IOException {
        for (String usesSdk : new String[]{
                "<uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"21\"/>",
                "<uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"27\"/>",
                "<uses-sdk android:minSdkVersion=\"21\"/>",
                "",
        }) {
            String xml = decodeWithVersionNameAt(usesSdk, PLATFORM_PRIVATE_ATTR);
            assertEquals(xml, "1.7", value(xml, "android:r0x01010591"));
            assertFalse(xml, xml.contains("isSplitRequired"));
        }
        // An app built with the public SDK can only have meant the public attribute
        for (String usesSdk : new String[]{
                "<uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"28\"/>",
                "<uses-sdk android:minSdkVersion=\"28\"/>",
        }) {
            String xml = decodeWithVersionNameAt(usesSdk, PLATFORM_PRIVATE_ATTR);
            assertEquals(xml, "1.7", value(xml, "android:isSplitRequired"));
        }
        byte[] manifest = fixture("21");
        replaceResourceId(manifest, ATTR_VERSION_NAME, PLATFORM_PRIVATE_ATTR);
        String xml = AndroidBinXmlDecoder.decode(manifest);
        assertEquals(xml, "1.7", value(xml, "android:r0x01010591"));
        // Its public attributes keep their names
        assertEquals(xml, "7", value(xml, "android:versionCode"));
        assertEquals(xml, "21", value(xml, "android:targetSdkVersion"));
    }

    @NonNull
    private static String decodeWithVersionNameAt(@NonNull String usesSdk, int id) throws IOException {
        byte[] manifest = AndroidBinXmlEncoder.encodeString("<manifest"
                + " xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"org.example\""
                + " android:versionName=\"1.7\">" + usesSdk + "</manifest>");
        replaceResourceId(manifest, ATTR_VERSION_NAME, id);
        return AndroidBinXmlDecoder.decode(manifest);
    }

    /**
     * Android reads a uses-sdk value stored as a string as a codename, digits or not, meaning a
     * preview SDK. A reference it resolves first, which the decoder can't, so an unresolved target
     * counts as missing and the minimum stands in for it, as Android does for a missing one.
     */
    @Test
    public void aStringSdkVersionIsACodenameAndAReferenceTargetFallsBackToTheMinimum() throws IOException {
        String stringTarget = decodeWithUsesSdk(
                "<uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"QZ\"/>", "QZ", "21");
        assertEquals(stringTarget, "21", value(stringTarget, "android:targetSdkVersion"));
        assertEquals(stringTarget, "1.7", value(stringTarget, "android:isSplitRequired"));

        String stringMin = decodeWithUsesSdk("<uses-sdk android:minSdkVersion=\"QZ\"/>", "QZ", "21");
        assertEquals(stringMin, "21", value(stringMin, "android:minSdkVersion"));
        assertEquals(stringMin, "1.7", value(stringMin, "android:isSplitRequired"));

        String reference = " android:targetSdkVersion=\"@android:integer/config_mediumAnimTime\"/>";
        String oldMin = decodeWithUsesSdk("<uses-sdk android:minSdkVersion=\"21\"" + reference, null, null);
        assertEquals(oldMin, "1.7", value(oldMin, "android:r0x01010591"));
        assertFalse(oldMin, oldMin.contains("isSplitRequired"));
        String newMin = decodeWithUsesSdk("<uses-sdk android:minSdkVersion=\"28\"" + reference, null, null);
        assertEquals(newMin, "1.7", value(newMin, "android:isSplitRequired"));
    }

    /**
     * A manifest whose android:versionName is moved onto {@link #PLATFORM_PRIVATE_ATTR}, with the one
     * string {@code from} in its pool rewritten to {@code to} of the same length.
     */
    @NonNull
    private static String decodeWithUsesSdk(@NonNull String usesSdk, @Nullable String from, @Nullable String to)
            throws IOException {
        byte[] manifest = AndroidBinXmlEncoder.encodeString("<manifest"
                + " xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"org.example\""
                + " android:versionName=\"1.7\">" + usesSdk + "</manifest>");
        replaceResourceId(manifest, ATTR_VERSION_NAME, PLATFORM_PRIVATE_ATTR);
        if (from != null && to != null) {
            replaceText(manifest, from, to);
        }
        return AndroidBinXmlDecoder.decode(manifest);
    }

    /** Rewrites the one place {@code from} is stored, as UTF-8 or UTF-16, to {@code to} of the same length. */
    private static void replaceText(@NonNull byte[] data, @NonNull String from, @NonNull String to) {
        assertEquals(from.length(), to.length());
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, StandardCharsets.UTF_16LE}) {
            byte[] needle = from.getBytes(charset);
            byte[] replacement = to.getBytes(charset);
            int found = -1;
            for (int i = 0; i + needle.length <= data.length; ++i) {
                if (Arrays.equals(Arrays.copyOfRange(data, i, i + needle.length), needle)) {
                    assertEquals("more than one " + from, -1, found);
                    found = i;
                }
            }
            if (found >= 0) {
                System.arraycopy(replacement, 0, data, found, replacement.length);
                return;
            }
        }
        throw new AssertionError(from + " is not in the document");
    }

    @Test
    public void aManifestsOwnCompileSdkWinsOverTheCallers() throws IOException {
        assertNamedStatusBarHeight(activityLabel(AndroidBinXmlDecoder.decode(ByteBuffer.wrap(fixture("35")), 21)));
    }

    private static void assertNamedStatusBarHeight(@NonNull String label) {
        assertTrue(label, label.matches(NAMED_STATUS_BAR_HEIGHT));
    }

    @Test
    public void hexNamesAndReferencesSurviveARoundTripThroughTheEncoder() throws IOException {
        byte[] manifest = fixture("21");
        replaceResourceId(manifest, ATTR_VERSION_NAME, PRIVATE_ATTR_2040);
        String decoded = AndroidBinXmlDecoder.decode(manifest);

        byte[] encoded = AndroidBinXmlEncoder.encodeString(decoded);

        assertEquals(decoded, AndroidBinXmlDecoder.decode(encoded));
    }

    @Test
    public void theExistingFixturesStillDecodeWithTheNewestTable() throws IOException {
        // Neither carries compile metadata, so both decode with the newest table and the no-metadata sets
        assertNotNull(AndroidBinXmlDecoder.decode(resource("xml/HMS_Core_Android_Manifest.bin.xml")));
        assertNotNull(AndroidBinXmlDecoder.decode(resource("xml/test_layout.bin.xml")));
    }

    @NonNull
    private static String decodeFixture(@NonNull String api) throws IOException {
        return AndroidBinXmlDecoder.decode(fixture(api));
    }

    @NonNull
    private static byte[] fixture(@NonNull String api) throws IOException {
        return resource("xml/framework_ref_api" + api + ".bin.xml");
    }

    @NonNull
    private static byte[] resource(@NonNull String name) throws IOException {
        ClassLoader classLoader = Objects.requireNonNull(FrameworkTableDecodeTest.class.getClassLoader());
        try (InputStream in = classLoader.getResourceAsStream(name)) {
            assertNotNull(name, in);
            return IoUtils.readFully(in, -1, true);
        }
    }

    private static int compileSdkOf(@NonNull byte[] manifest) throws IOException {
        return AndroidBinXmlDecoder.compileSdkOf(AndroidBinXmlDecoder.readDocument(manifest));
    }

    /** The value of the first attribute with this exact name, or {@code null}. */
    @Nullable
    private static String value(@NonNull String xml, @NonNull String name) {
        Matcher m = Pattern.compile("\\s" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    @NonNull
    private static String activityLabel(@NonNull String xml) {
        int activity = xml.indexOf("<activity");
        assertTrue(xml, activity >= 0);
        String label = value(xml.substring(activity), "android:label");
        assertNotNull(xml, label);
        return label;
    }

    @NonNull
    private static Set<String> flags(@Nullable String value) {
        assertNotNull(value);
        return new HashSet<>(Arrays.asList(value.split("\\|")));
    }

    @NonNull
    private static Set<String> flags(@NonNull String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    /** Swaps one ID in the document's resource map, which names attributes by resource ID. */
    private static void replaceResourceId(@NonNull byte[] data, int from, int to) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int pos = b.getShort(2) & 0xffff;
        while (pos + 8 <= data.length) {
            int type = b.getShort(pos) & 0xffff;
            int header = b.getShort(pos + 2) & 0xffff;
            int size = b.getInt(pos + 4);
            if (type == RES_XML_RESOURCE_MAP_TYPE) {
                for (int i = pos + header; i + 4 <= pos + size; i += 4) {
                    if (b.getInt(i) == from) {
                        b.putInt(i, to);
                        return;
                    }
                }
            }
            pos += size;
        }
        throw new AssertionError(String.format("0x%08x is not in the resource map", from));
    }
}
