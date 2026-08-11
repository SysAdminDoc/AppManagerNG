// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;

/**
 * A backup's metadata has to distinguish "this archive carries the app's KeyStore entries" from
 * "the app had KeyStore entries and they are knowingly absent". Conflating the two is what made
 * restore look for a master key that was never written on Android 12 and later.
 */
@RunWith(RobolectricTestRunner.class)
public class KeyStoreBackupDisclosureTest {
    @Config(sdk = Build.VERSION_CODES.R)
    @Test
    public void keyStoreBackupIsSupportedBeforeAndroid12() {
        assertTrue(KeyStoreUtils.isKeyStoreBackupSupported());
    }

    @Config(sdk = Build.VERSION_CODES.S)
    @Test
    public void keyStoreBackupIsUnsupportedFromAndroid12() {
        assertFalse(KeyStoreUtils.isKeyStoreBackupSupported());
    }

    @Config(sdk = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void keyStoreBackupStaysUnsupportedOnLaterReleases() {
        assertFalse(KeyStoreUtils.isKeyStoreBackupSupported());
    }

    @Test
    public void skippedFlagSurvivesASerializationRoundTrip() throws JSONException {
        BackupMetadataV5.Metadata metadata = newSerializableMetadata();
        metadata.keyStore = false;
        metadata.keyStoreSkipped = true;

        JSONObject json = metadata.serializeToJson();
        assertFalse(json.getBoolean("key_store"));
        assertTrue(json.getBoolean("key_store_skipped"));

        BackupMetadataV5.Metadata restored = new BackupMetadataV5.Metadata(json);
        assertFalse(restored.keyStore);
        assertTrue(restored.keyStoreSkipped);
    }

    @Test
    public void metadataWithoutTheFieldReadsAsNotSkipped() throws JSONException {
        // Backups written before this field existed must not suddenly claim their KeyStore data
        // was dropped — absence means "nothing recorded", not "skipped".
        BackupMetadataV5.Metadata metadata = newSerializableMetadata();
        metadata.keyStore = true;
        JSONObject json = metadata.serializeToJson();
        json.remove("key_store_skipped");

        BackupMetadataV5.Metadata restored = new BackupMetadataV5.Metadata(json);
        assertTrue(restored.keyStore);
        assertFalse(restored.keyStoreSkipped);
    }

    @Test
    public void theSkippedFlagIsCopiedWithTheMetadata() {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata("test");
        metadata.packageName = "com.example";
        metadata.keyStoreSkipped = true;

        BackupMetadataV5.Metadata copy = new BackupMetadataV5.Metadata(metadata);

        assertTrue("a copied backup must keep knowing its KeyStore data is absent",
                copy.keyStoreSkipped);
    }

    @Test
    public void aSkippedBackupSaysSoWhereBackupsAreChosen() {
        Context context = ApplicationProvider.getApplicationContext();
        String note = context.getString(R.string.backup_keystore_not_included);

        BackupMetadataV5 skipped = newMetadata(true);
        BackupMetadataV5 carried = newMetadata(false);

        assertTrue("the restore picker must disclose that KeyStore data is missing",
                skipped.toLocalizedString(context).toString().contains(note));
        assertFalse("a backup that carries KeyStore data must not be labelled as missing it",
                carried.toLocalizedString(context).toString().contains(note));
    }

    @Test
    public void theTwoFlagsDescribeDifferentThings() {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata("test");
        metadata.keyStore = true;
        metadata.keyStoreSkipped = false;
        assertEquals("an archive that carries KeyStore data is not also missing it",
                metadata.keyStore, !metadata.keyStoreSkipped);
    }

    /** Metadata populated well enough to survive a JSON round trip. */
    private static BackupMetadataV5.Metadata newSerializableMetadata() {
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata("test");
        metadata.label = "Example";
        metadata.packageName = "com.example";
        metadata.versionName = "1.0";
        metadata.versionCode = 1;
        metadata.apkName = "base.apk";
        metadata.dataDirs = new String[0];
        metadata.splitConfigs = new String[0];
        return metadata;
    }

    private static BackupMetadataV5 newMetadata(boolean keyStoreSkipped) {
        BackupMetadataV5.Metadata metadata = newSerializableMetadata();
        metadata.keyStore = !keyStoreSkipped;
        metadata.keyStoreSkipped = keyStoreSkipped;
        BackupMetadataV5.Info info = new BackupMetadataV5.Info(
                System.currentTimeMillis(),
                new BackupFlags(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA),
                0,
                TarUtils.TAR_GZIP,
                DigestUtils.SHA_256,
                CryptoUtils.MODE_NO_ENCRYPTION,
                null,
                null,
                null);
        return new BackupMetadataV5(info, metadata);
    }
}
