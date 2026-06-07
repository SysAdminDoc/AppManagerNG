// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.StringWriter;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class ListExporterTest {
    @Test
    public void csvDefaultKeepsLegacyColumns() throws Exception {
        String output = exportCsv(false);
        String header = output.split("\n")[0];

        assertTrue(header.contains("installerPackageName"));
        assertFalse(header.contains("disabled"));
        assertFalse(header.contains("requestedPermissionCount"));
    }

    @Test
    public void csvExtendedAppendsOperationalMetadata() throws Exception {
        String output = exportCsv(true);
        String[] lines = output.split("\n");

        assertTrue(lines[0].endsWith("userId,system,disabled,hidden,suspended,stopped,"
                + "requestedPermissionCount,grantedPermissionCount,splitCount,sourceDir,publicSourceDir"));
        assertTrue(lines[1].endsWith("10,true,true,true,false,true,3,2,2,/data/app/base.apk,/data/app/public.apk"));
    }

    @Test
    public void csvDefusesFormulaLikeAppMetadata() throws Exception {
        AppListItem item = buildItem();
        item.setPackageLabel(" \t=LABEL(\"http://evil/\")");
        item.setVersionName("\n=VERSION(\"http://evil/\")");
        item.setInstallerPackageLabel("@INSTALLER(\"http://evil/\")");
        item.setSourceDir(" \t=SOURCE(\"http://evil/\")");

        String output = exportCsv(true, item);

        assertTrue(output.contains("\"' \t=LABEL(\"\"http://evil/\"\")\""));
        assertTrue(output.contains("\"'\n=VERSION(\"\"http://evil/\"\")\""));
        assertTrue(output.contains("\"'@INSTALLER(\"\"http://evil/\"\")\""));
        assertTrue(output.contains("\"' \t=SOURCE(\"\"http://evil/\"\")\""));
        assertFalse(output.contains("\" \t=LABEL"));
        assertFalse(output.contains("\"\n=VERSION"));
        assertFalse(output.contains("\"@INSTALLER"));
        assertFalse(output.contains("\" \t=SOURCE"));
    }

    @Test
    public void csvUsesEmptyFieldsForNullableTextMetadata() throws Exception {
        AppListItem item = buildItem();
        item.setPackageLabel(null);
        item.setVersionName(null);
        item.setSignatureSha256(null);
        item.setInstallerPackageName(null);
        item.setInstallerPackageLabel(null);
        item.setSourceDir(null);
        item.setPublicSourceDir(null);

        String output = exportCsv(true, item);
        String row = output.split("\n")[1];

        assertTrue(row.startsWith("com.example.app,,42,,"));
        assertFalse(output.contains("null"));
    }

    @Test
    public void jsonDefaultDoesNotAddExtendedKeys() throws Exception {
        JSONObject object = exportJson(false).getJSONObject(0);

        assertEquals("com.android.vending", object.getString("installerPackageName"));
        assertFalse(object.has("disabled"));
        assertFalse(object.has("requestedPermissionCount"));
    }

    @Test
    public void jsonExtendedAddsOperationalMetadata() throws Exception {
        JSONObject object = exportJson(true).getJSONObject(0);

        assertEquals("com.android.vending", object.getString("installerPackageName"));
        assertTrue(object.getBoolean("system"));
        assertTrue(object.getBoolean("disabled"));
        assertEquals(3, object.getInt("requestedPermissionCount"));
        assertEquals(2, object.getInt("grantedPermissionCount"));
    }

    @Test
    public void markdownEscapesAppControlledMetadata() throws Exception {
        AppListItem item = buildItem();
        item.setPackageLabel("Example\n# Inject <script>");
        item.setVersionName("[1.2.3](http://evil/)");
        item.setInstallerPackageLabel("Store **bold**");
        item.setInstallerPackageName("com.evil[installer]");
        item.setPublicSourceDir("/data/app/<script>.apk");

        String output = exportMarkdown(true, item);

        assertTrue(output.contains("## Example \\# Inject &lt;script&gt;"));
        assertTrue(output.contains("**Version:** \\[1.2.3\\]\\(http://evil/\\) (42)"));
        assertTrue(output.contains("**Installer:** Store \\*\\*bold\\*\\* (com.evil\\[installer\\])"));
        assertTrue(output.contains("**Source:** /data/app/&lt;script&gt;.apk"));
        assertFalse(output.contains("\n# Inject"));
        assertFalse(output.contains("[1.2.3](http://evil/)"));
        assertFalse(output.contains("<script>"));
    }

    @Test
    public void xmlSkipsNullableTextAttributes() throws Exception {
        AppListItem item = buildItem();
        item.setPackageLabel(null);
        item.setVersionName(null);
        item.setSignatureSha256(null);
        item.setInstallerPackageName(null);
        item.setInstallerPackageLabel(null);
        item.setSourceDir(null);
        item.setPublicSourceDir(null);

        String output = exportXml(true, item);

        assertTrue(output.contains("<package"));
        assertTrue(output.contains("name=\"com.example.app\""));
        assertTrue(output.contains("versionCode=\"42\""));
        assertFalse(output.contains("label="));
        assertFalse(output.contains("versionName="));
        assertFalse(output.contains("signature="));
        assertFalse(output.contains("installerPackageName="));
        assertFalse(output.contains("installerPackageLabel="));
        assertFalse(output.contains("sourceDir="));
        assertFalse(output.contains("publicSourceDir="));
    }

    private static String exportCsv(boolean includeExtendedMetadata) throws Exception {
        return exportCsv(includeExtendedMetadata, buildItem());
    }

    private static String exportCsv(boolean includeExtendedMetadata, AppListItem item) throws Exception {
        try (StringWriter writer = new StringWriter()) {
            ListExporter.exportItems(writer, ListExporter.EXPORT_TYPE_CSV,
                    Collections.singletonList(item), includeExtendedMetadata,
                    RuntimeEnvironment.getApplication());
            return writer.toString();
        }
    }

    private static String exportMarkdown(boolean includeExtendedMetadata, AppListItem item) throws Exception {
        try (StringWriter writer = new StringWriter()) {
            ListExporter.exportItems(writer, ListExporter.EXPORT_TYPE_MARKDOWN,
                    Collections.singletonList(item), includeExtendedMetadata,
                    RuntimeEnvironment.getApplication());
            return writer.toString();
        }
    }

    private static String exportXml(boolean includeExtendedMetadata, AppListItem item) throws Exception {
        try (StringWriter writer = new StringWriter()) {
            ListExporter.exportItems(writer, ListExporter.EXPORT_TYPE_XML,
                    Collections.singletonList(item), includeExtendedMetadata,
                    RuntimeEnvironment.getApplication());
            return writer.toString();
        }
    }

    private static JSONArray exportJson(boolean includeExtendedMetadata) throws Exception {
        try (StringWriter writer = new StringWriter()) {
            ListExporter.exportItems(writer, ListExporter.EXPORT_TYPE_JSON,
                    Collections.singletonList(buildItem()), includeExtendedMetadata,
                    RuntimeEnvironment.getApplication());
            return new JSONArray(writer.toString());
        }
    }

    private static AppListItem buildItem() {
        AppListItem item = new AppListItem("com.example.app");
        item.setPackageLabel("Example");
        item.setVersionCode(42);
        item.setVersionName("1.2.3");
        item.setMinSdk(21);
        item.setTargetSdk(35);
        item.setSignatureSha256("AA:BB");
        item.setFirstInstallTime(1000);
        item.setLastUpdateTime(2000);
        item.setInstallerPackageName("com.android.vending");
        item.setInstallerPackageLabel("Play Store");
        item.setUserId(10);
        item.setSystemApp(true);
        item.setEnabled(false);
        item.setHidden(true);
        item.setSuspended(false);
        item.setStopped(true);
        item.setRequestedPermissionCount(3);
        item.setGrantedPermissionCount(2);
        item.setSplitCount(2);
        item.setSourceDir("/data/app/base.apk");
        item.setPublicSourceDir("/data/app/public.apk");
        return item;
    }
}
