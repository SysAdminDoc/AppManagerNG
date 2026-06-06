// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class ManifestParserTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());

    @Test
    public void testManifestIntentFilterParsing() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        ManifestParser parser = new ManifestParser(xmlBinary.getContentAsBinary());
        List<ManifestComponent> manifestComponents = parser.parseComponents();
        assertEquals(1598, manifestComponents.size());
        int intentFilterCount = 0;
        for (ManifestComponent component : manifestComponents) {
            intentFilterCount += component.intentFilters.size();
        }
        assertEquals(156, intentFilterCount);
    }

    @Test
    public void testManifestMetadataParsing() throws IOException {
        Path xmlBinary = Paths.get(classLoader.getResource("xml/HMS_Core_Android_Manifest.bin.xml").getFile());
        ManifestParser parser = new ManifestParser(xmlBinary.getContentAsBinary());
        List<ManifestMetadata> metadata = parser.parseMetadata();

        assertEquals(503, metadata.size());
        assertTrue(containsMetadata(metadata, ManifestMetadata.OWNER_APPLICATION, "com.huawei.hwid",
                "pushcore_version", "HMS100101302", "STRING", false));
        assertResourceMetadata(metadata, findMetadata(metadata, ManifestMetadata.OWNER_APPLICATION, "com.huawei.hwid",
                "permission.reason.android.permission-group.CALENDAR"),
                "permission.reason.android.permission-group.CALENDAR");
        assertResourceMetadata(metadata, findMetadata(metadata, ManifestComponent.TYPE_ACTIVITY,
                "com.huawei.hwid/.ui.extend.setting.StartUpGuideLoginActivity",
                "com.android.settings.title"), "com.android.settings.title");
        assertTrue(containsMetadata(metadata, ManifestComponent.TYPE_SERVICE,
                "com.huawei.hwid/.manager.accountmgr.HwAccMgrService",
                "android.accounts.AccountAuthenticator.customTokens", "true", "BOOLEAN", false));
        assertResourceMetadata(metadata, findMetadata(metadata, ManifestComponent.TYPE_PROVIDER,
                "com.huawei.hwid/com.huawei.hms.fwkit.kpms.core.provider.KpmsInstallPathProvider",
                "android.support.FILE_PROVIDER_PATHS"), "android.support.FILE_PROVIDER_PATHS");
    }

    @Test
    public void parseIntentFilterPriorityDefaultsMalformedValues() {
        assertEquals(0, ManifestParser.parseIntentFilterPriority(null));
        assertEquals(42, ManifestParser.parseIntentFilterPriority(" 42 "));
        assertEquals(0, ManifestParser.parseIntentFilterPriority("not-a-number"));
        assertEquals(0, ManifestParser.parseIntentFilterPriority("999999999999999999999"));
    }

    @Test
    public void normalizeIntentFilterNameDropsMissingAndBlankNames() {
        assertEquals(null, ManifestParser.normalizeIntentFilterName(null));
        assertEquals(null, ManifestParser.normalizeIntentFilterName("   "));
        assertEquals("android.intent.action.VIEW",
                ManifestParser.normalizeIntentFilterName(" android.intent.action.VIEW "));
    }

    private static void assertResourceMetadata(List<ManifestMetadata> metadata, ManifestMetadata row, String name) {
        assertNotNull("Missing resource metadata for " + name + "; candidates: "
                + describeCandidates(metadata, name), row);
        assertTrue(row.resource);
        assertEquals("REFERENCE", row.valueType);
        assertNotNull(row.value);
        assertTrue(row.value.startsWith("@"));
    }

    private static String describeCandidates(List<ManifestMetadata> metadata, String name) {
        StringBuilder builder = new StringBuilder();
        for (ManifestMetadata row : metadata) {
            if (name.equals(row.name)) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(row.ownerType).append(':').append(row.ownerName);
            }
        }
        return builder.toString();
    }

    private static ManifestMetadata findMetadata(List<ManifestMetadata> metadata, String ownerType, String ownerName,
                                                 String name) {
        for (ManifestMetadata row : metadata) {
            if (ownerType.equals(row.ownerType)
                    && ownerName.equals(row.ownerName)
                    && name.equals(row.name)) {
                return row;
            }
        }
        return null;
    }

    private static boolean containsMetadata(List<ManifestMetadata> metadata, String ownerType, String ownerName,
                                            String name, String value, String valueType, boolean resource) {
        for (ManifestMetadata row : metadata) {
            if (ownerType.equals(row.ownerType)
                    && ownerName.equals(row.ownerName)
                    && name.equals(row.name)
                    && value.equals(row.value)
                    && valueType.equals(row.valueType)
                    && row.resource == resource) {
                return true;
            }
        }
        return false;
    }
}
