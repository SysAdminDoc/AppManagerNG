// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.apk.parser.ManifestComponent;
import io.github.muntashirakon.AppManager.apk.parser.ManifestMetadata;

public class ManifestMetadataInfoTest {
    @Test
    public void fromManifestGroupsRowsByOwnerInManifestOrder() {
        ManifestMetadataInfo info = ManifestMetadataInfo.fromManifest(Arrays.asList(
                new ManifestMetadata(ManifestMetadata.OWNER_APPLICATION, "com.example",
                        "com.example.string", "hello", "STRING", false),
                new ManifestMetadata(ManifestMetadata.OWNER_APPLICATION, "com.example",
                        "com.example.flag", "true", "BOOLEAN", false),
                new ManifestMetadata(ManifestComponent.TYPE_SERVICE, "com.example/.SyncService",
                        "android.accounts.AccountAuthenticator", "@0x7f150004", "REFERENCE", true)));

        assertTrue(info.hasMetadata());
        assertEquals(3, info.getMetadataCount());
        assertEquals(2, info.owners.size());
        assertEquals("Application - com.example", info.owners.get(0).toDisplayTitle());
        assertEquals(2, info.owners.get(0).entries.size());
        assertEquals("Service - com.example/.SyncService", info.owners.get(1).toDisplayTitle());
    }

    @Test
    public void displayStringLabelsTypedAndResourceValues() {
        ManifestMetadataInfo info = ManifestMetadataInfo.fromManifest(Arrays.asList(
                new ManifestMetadata(ManifestMetadata.OWNER_APPLICATION, "com.example",
                        "com.example.string", "hello", "STRING", false),
                new ManifestMetadata(ManifestMetadata.OWNER_APPLICATION, "com.example",
                        "com.example.count", "400", "DEC", false),
                new ManifestMetadata(ManifestComponent.TYPE_PROVIDER, "com.example/.Files",
                        "android.support.FILE_PROVIDER_PATHS", "@0x7f15000e", "REFERENCE", true)));

        String display = info.toDisplayString();
        assertTrue(display.contains("com.example.string = hello (string)"));
        assertTrue(display.contains("com.example.count = 400 (integer)"));
        assertTrue(display.contains("android.support.FILE_PROVIDER_PATHS = @0x7f15000e (resource)"));
    }

    @Test
    public void copyTextIsTabSeparatedAndIncludesHeaders() {
        ManifestMetadataInfo info = ManifestMetadataInfo.fromManifest(Collections.singletonList(
                new ManifestMetadata(ManifestComponent.TYPE_RECEIVER, "com.example/.Receiver",
                        "com.example.enabled", "false", "BOOLEAN", false)));

        assertEquals("Owner\tName\tValue\tType\n"
                        + "Receiver - com.example/.Receiver\tcom.example.enabled\tfalse\tboolean",
                info.toCopyText());
    }

    @Test
    public void copyTextEscapesManifestControlledTsvCells() {
        ManifestMetadataInfo info = ManifestMetadataInfo.fromManifest(Collections.singletonList(
                new ManifestMetadata(ManifestComponent.TYPE_RECEIVER, "\t=Receiver\nName",
                        "\t=metadata\nname", "\n+SUM(1,1)", "STRING", false)));

        assertEquals("Owner\tName\tValue\tType\n"
                        + "Receiver -  =Receiver Name\t' =metadata name\t' +SUM(1,1)\tstring",
                info.toCopyText());
    }

    @Test
    public void emptyInputHasNoMetadata() {
        ManifestMetadataInfo info = ManifestMetadataInfo.fromManifest(Collections.emptyList());

        assertFalse(info.hasMetadata());
        assertEquals(0, info.getMetadataCount());
        assertEquals("", info.toDisplayString());
    }
}
