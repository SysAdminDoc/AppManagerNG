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
