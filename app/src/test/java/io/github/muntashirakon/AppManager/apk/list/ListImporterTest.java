// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.io.StringReader;
import java.util.Set;

public class ListImporterTest {
    @Test
    public void readPackageNamesAcceptsExportedJsonObjects() throws Exception {
        Set<String> packageNames = ListImporter.readPackageNames(new StringReader("["
                + "{\"name\":\"com.example.alpha\",\"versionName\":\"1\"},"
                + "{\"name\":\"org.example.beta\",\"versionCode\":2}"
                + "]"));

        assertArrayEquals(new String[]{"com.example.alpha", "org.example.beta"},
                packageNames.toArray(new String[0]));
    }

    @Test
    public void readPackageNamesIgnoresExtendedExportMetadata() throws Exception {
        Set<String> packageNames = ListImporter.readPackageNames(new StringReader("[{"
                + "\"name\":\"com.example.alpha\","
                + "\"userId\":10,"
                + "\"system\":true,"
                + "\"disabled\":false,"
                + "\"requestedPermissionCount\":3,"
                + "\"grantedPermissionCount\":2"
                + "}]"));

        assertArrayEquals(new String[]{"com.example.alpha"}, packageNames.toArray(new String[0]));
    }

    @Test
    public void readPackageNamesAcceptsWrappedAndStringEntries() throws Exception {
        Set<String> packageNames = ListImporter.readPackageNames(new StringReader("{\"packages\":["
                + "\"com.example.alpha\","
                + "{\"packageName\":\"org.example.beta\"}"
                + "]}"));

        assertArrayEquals(new String[]{"com.example.alpha", "org.example.beta"},
                packageNames.toArray(new String[0]));
    }

    @Test
    public void readPackageNamesAcceptsAppsWrapper() throws Exception {
        Set<String> packageNames = ListImporter.readPackageNames(new StringReader("{\"apps\":["
                + "{\"name\":\"com.example.alpha\"},"
                + "\"android\""
                + "]}"));

        assertArrayEquals(new String[]{"com.example.alpha", "android"},
                packageNames.toArray(new String[0]));
    }

    @Test
    public void readPackageNamesDeduplicatesAndSkipsInvalidEntries() throws Exception {
        Set<String> packageNames = ListImporter.readPackageNames(new StringReader("["
                + "{\"name\":\"com.example.alpha\"},"
                + "{\"name\":\"com.example.alpha\"},"
                + "{\"name\":\"bad package\"},"
                + "{\"label\":\"No package\"}"
                + "]"));

        assertArrayEquals(new String[]{"com.example.alpha"}, packageNames.toArray(new String[0]));
    }
}
