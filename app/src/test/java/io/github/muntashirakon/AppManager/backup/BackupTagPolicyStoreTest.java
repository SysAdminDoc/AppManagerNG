// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.tags.AppTagStore;

@RunWith(RobolectricTestRunner.class)
public class BackupTagPolicyStoreTest {
    private static final String PACKAGE_NAME = "com.example.app";
    private Context mContext;
    private BackupTagPolicyStore mStore;
    private AppTagStore mTags;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.getSharedPreferences(BackupTagPolicyStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        mContext.getSharedPreferences("app_tags", Context.MODE_PRIVATE).edit().clear().commit();
        mStore = new BackupTagPolicyStore(mContext);
        mTags = new AppTagStore(mContext);
    }

    @Test
    public void firstMatchingRuleWinsAndMoveChangesWinner() {
        mTags.addTag(PACKAGE_NAME, "work");
        mTags.addTag(PACKAGE_NAME, "critical");
        BackupTagPolicyStore.Policy work = policy("work", BackupFlags.BACKUP_APK_FILES, 2, 7, null);
        BackupTagPolicyStore.Policy critical = policy("critical", BackupFlags.BACKUP_INT_DATA, 5, 30, null);
        mStore.setPolicies(Arrays.asList(work, critical));

        BackupTagPolicyStore.Resolution first = mStore.resolve(PACKAGE_NAME,
                BackupFlags.BACKUP_EXT_DATA | BackupFlags.BACKUP_MULTIPLE, available());

        assertEquals("work", first.policy.tag);
        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_MULTIPLE, first.flags);
        assertEquals(2, first.maxCount);
        mStore.move(1, 0);
        BackupTagPolicyStore.Resolution moved = mStore.resolve(PACKAGE_NAME,
                BackupFlags.BACKUP_EXT_DATA | BackupFlags.BACKUP_MULTIPLE, available());
        assertEquals("critical", moved.policy.tag);
        assertEquals(BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_MULTIPLE, moved.flags);
    }

    @Test
    public void unavailableCryptoAndDestinationFallBackWithoutLosingRule() {
        Uri destination = Uri.parse("content://backup/tree/missing");
        mTags.addTag(PACKAGE_NAME, "work");
        BackupTagPolicyStore.Policy policy = new BackupTagPolicyStore.Policy("work",
                BackupFlags.BACKUP_APK_FILES, CryptoUtils.MODE_AES, 3, 14, destination);
        mStore.add(policy);

        BackupTagPolicyStore.Resolution resolution = mStore.resolve(PACKAGE_NAME,
                BackupFlags.BACKUP_MULTIPLE, new BackupTagPolicyStore.Availability() {
                    @Override
                    public boolean isCryptoAvailable(@NonNull String mode) {
                        return false;
                    }

                    @NonNull
                    @Override
                    public String getDefaultCryptoMode() {
                        return CryptoUtils.MODE_NO_ENCRYPTION;
                    }

                    @Override
                    public boolean isDestinationAvailable(@NonNull Uri ignored) {
                        return false;
                    }
                });

        assertEquals("work", resolution.policy.tag);
        assertEquals(CryptoUtils.MODE_NO_ENCRYPTION, resolution.cryptoMode);
        assertNull(resolution.destination);
        assertTrue(resolution.cryptoFallback);
        assertTrue(resolution.destinationFallback);
        assertEquals(destination, mStore.getPolicies().get(0).destination);
    }

    @Test
    public void unsupportedPolicyPartsFallBackToCurrentParts() {
        mTags.addTag(PACKAGE_NAME, "work");
        mStore.add(policy("work", BackupFlags.BACKUP_INT_DATA, 1, 1, null));
        BackupTagPolicyStore.Availability availability = new BackupTagPolicyStore.Availability() {
            @Override
            public boolean isCryptoAvailable(@NonNull String mode) {
                return true;
            }

            @NonNull
            @Override
            public String getDefaultCryptoMode() {
                return CryptoUtils.MODE_NO_ENCRYPTION;
            }

            @Override
            public boolean isDestinationAvailable(@NonNull Uri destination) {
                return true;
            }

            @Override
            public int getSupportedContentFlags() {
                return BackupFlags.BACKUP_APK_FILES;
            }
        };

        BackupTagPolicyStore.Resolution resolution = mStore.resolve(PACKAGE_NAME,
                BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_MULTIPLE, availability);

        assertTrue(resolution.partsFallback);
        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_MULTIPLE, resolution.flags);
    }

    @Test
    public void malformedRulesAreSkippedButValidLaterRuleSurvives() throws Exception {
        JSONObject invalid = new JSONObject().put("tag", "bad tag").put("flags", -1)
                .put("crypto", "unknown");
        JSONObject valid = policy("work", BackupFlags.BACKUP_APK_FILES, 1, 1, null).toJson();
        mContext.getSharedPreferences(BackupTagPolicyStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(BackupTagPolicyStore.KEY_POLICIES,
                        new JSONArray().put(invalid).put(valid).toString()).commit();

        List<BackupTagPolicyStore.Policy> policies = mStore.getPolicies();

        assertEquals(1, policies.size());
        assertEquals("work", policies.get(0).tag);
        assertTrue(mStore.hasInvalidData());
    }

    @Test
    public void futureSchemaFallsBackVisibly() {
        mContext.getSharedPreferences(BackupTagPolicyStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(BackupTagPolicyStore.KEY_SCHEMA,
                        BackupTagPolicyStore.SCHEMA_VERSION + 1).commit();

        assertTrue(mStore.getPolicies().isEmpty());
        assertTrue(mStore.hasInvalidData());
    }

    @Test
    public void policySchemaPersistsAcrossStoreInstances() {
        mStore.add(policy("work", BackupFlags.BACKUP_APK_FILES, 4, 21,
                Uri.parse("file:///storage/test")));

        BackupTagPolicyStore.Policy restored = new BackupTagPolicyStore(mContext).getPolicies().get(0);

        assertEquals("work", restored.tag);
        assertEquals(4, restored.maxCount);
        assertEquals(21, restored.maxAgeDays);
        assertEquals("file:///storage/test", restored.destination.toString());
        assertEquals(BackupTagPolicyStore.SCHEMA_VERSION,
                mContext.getSharedPreferences(BackupTagPolicyStore.PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(BackupTagPolicyStore.KEY_SCHEMA, 0));
    }

    @Test
    public void removedPolicyKeepsDestinationDiscoverableForDatabaseRebuild() {
        Uri destination = Uri.parse("content://provider/tree/archive");
        mStore.add(policy("work", BackupFlags.BACKUP_APK_FILES, 1, 1, destination));

        mStore.remove(0);

        assertTrue(mStore.getPolicies().isEmpty());
        assertEquals(Arrays.asList(destination), mStore.getKnownDestinations());
    }

    @Test
    public void invalidPoliciesAreRejectedAtWriteBoundary() {
        assertThrows(IllegalArgumentException.class, () -> policy("two words",
                BackupFlags.BACKUP_APK_FILES, 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> policy("work",
                BackupFlags.BACKUP_MULTIPLE, 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new BackupTagPolicyStore.Policy("work",
                BackupFlags.BACKUP_APK_FILES, "unknown", 1, 1, null));
    }

    @Test
    public void unmatchedPackageUsesDefaults() {
        mStore.add(policy("work", BackupFlags.BACKUP_APK_FILES, 1, 1, null));

        BackupTagPolicyStore.Resolution resolution = mStore.resolve(PACKAGE_NAME,
                BackupFlags.BACKUP_EXT_DATA | BackupFlags.BACKUP_MULTIPLE, available());

        assertNull(resolution.policy);
        assertEquals(BackupFlags.BACKUP_EXT_DATA | BackupFlags.BACKUP_MULTIPLE, resolution.flags);
        assertFalse(resolution.cryptoFallback);
        assertFalse(resolution.destinationFallback);
    }

    @NonNull
    private static BackupTagPolicyStore.Policy policy(@NonNull String tag, int flags,
                                                      int maxCount, int maxAgeDays,
                                                      Uri destination) {
        return new BackupTagPolicyStore.Policy(tag, flags, CryptoUtils.MODE_NO_ENCRYPTION,
                maxCount, maxAgeDays, destination);
    }

    @NonNull
    private static BackupTagPolicyStore.Availability available() {
        return new BackupTagPolicyStore.Availability() {
            @Override
            public boolean isCryptoAvailable(@NonNull String mode) {
                return true;
            }

            @NonNull
            @Override
            public String getDefaultCryptoMode() {
                return CryptoUtils.MODE_NO_ENCRYPTION;
            }

            @Override
            public boolean isDestinationAvailable(@NonNull Uri destination) {
                return true;
            }
        };
    }
}
