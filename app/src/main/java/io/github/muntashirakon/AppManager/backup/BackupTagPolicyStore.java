// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.tags.AppTagStore;
import io.github.muntashirakon.io.Paths;

/** Ordered, first-match backup policies keyed by app tag. */
public final class BackupTagPolicyStore {
    public static final String PREFS_NAME = "backup_tag_policies";
    public static final String KEY_SCHEMA = "_schema";
    public static final String KEY_POLICIES = "policies";
    public static final String KEY_DESTINATIONS = "destinations";
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_POLICIES = 64;
    private static final int MAX_RETENTION_COUNT = 36_500;
    private static final int MAX_RETENTION_AGE_DAYS = 36_500;

    @NonNull
    private final SharedPreferences mPrefs;
    @NonNull
    private final AppTagStore mTagStore;

    public BackupTagPolicyStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        mPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mTagStore = new AppTagStore(appContext);
        if (!mPrefs.contains(KEY_SCHEMA)) {
            mPrefs.edit().putInt(KEY_SCHEMA, SCHEMA_VERSION).apply();
        }
    }

    @AnyThread
    @NonNull
    public List<Policy> getPolicies() {
        String raw;
        try {
            if (mPrefs.getInt(KEY_SCHEMA, SCHEMA_VERSION) > SCHEMA_VERSION) {
                return Collections.emptyList();
            }
            raw = mPrefs.getString(KEY_POLICIES, null);
        } catch (ClassCastException e) {
            return Collections.emptyList();
        }
        if (raw == null) return Collections.emptyList();
        List<Policy> policies = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            int count = Math.min(array.length(), MAX_POLICIES);
            for (int i = 0; i < count; ++i) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                try {
                    policies.add(Policy.fromJson(object));
                } catch (IllegalArgumentException | JSONException ignore) {
                    // Skip only the malformed rule; later valid rules keep their order.
                }
            }
        } catch (JSONException ignore) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(policies);
    }

    /** True when persisted policy data cannot be fully loaded and rules are being skipped. */
    @AnyThread
    public boolean hasInvalidData() {
        String raw;
        try {
            if (mPrefs.getInt(KEY_SCHEMA, SCHEMA_VERSION) > SCHEMA_VERSION) return true;
            raw = mPrefs.getString(KEY_POLICIES, null);
        } catch (ClassCastException e) {
            return true;
        }
        if (raw == null) return false;
        try {
            JSONArray array = new JSONArray(raw);
            if (array.length() > MAX_POLICIES) return true;
            for (int i = 0; i < array.length(); ++i) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) return true;
                try {
                    Policy.fromJson(object);
                } catch (IllegalArgumentException | JSONException e) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            return true;
        }
    }

    @AnyThread
    public void setPolicies(@NonNull List<Policy> policies) {
        if (policies.size() > MAX_POLICIES) {
            throw new IllegalArgumentException("At most " + MAX_POLICIES + " backup tag policies are supported.");
        }
        JSONArray array = new JSONArray();
        List<Uri> destinations = new ArrayList<>(getKnownDestinations());
        for (Policy policy : policies) {
            if (policy == null) throw new IllegalArgumentException("Backup tag policy must not be null.");
            array.put(policy.toJson());
            if (policy.destination != null && !destinations.contains(policy.destination)) {
                destinations.add(policy.destination);
            }
        }
        JSONArray destinationArray = new JSONArray();
        int destinationCount = Math.min(destinations.size(), 128);
        for (int i = 0; i < destinationCount; ++i) destinationArray.put(destinations.get(i).toString());
        mPrefs.edit()
                .putInt(KEY_SCHEMA, SCHEMA_VERSION)
                .putString(KEY_POLICIES, array.toString())
                .putString(KEY_DESTINATIONS, destinationArray.toString())
                .apply();
    }

    /** Destinations ever referenced by a policy, retained so database rebuilds can find old backups. */
    @AnyThread
    @NonNull
    public List<Uri> getKnownDestinations() {
        List<Uri> destinations = new ArrayList<>();
        String raw;
        try {
            raw = mPrefs.getString(KEY_DESTINATIONS, null);
        } catch (ClassCastException e) {
            raw = null;
        }
        if (raw != null) {
            try {
                JSONArray array = new JSONArray(raw);
                int count = Math.min(array.length(), 128);
                for (int i = 0; i < count; ++i) {
                    Uri destination = Uri.parse(array.optString(i, ""));
                    if (isSupportedDestination(destination) && destination.toString().length() <= 4096
                            && !destinations.contains(destination)) {
                        destinations.add(destination);
                    }
                }
            } catch (JSONException ignore) {
            }
        }
        for (Policy policy : getPolicies()) {
            if (policy.destination != null && !destinations.contains(policy.destination)) {
                destinations.add(policy.destination);
            }
        }
        return Collections.unmodifiableList(destinations);
    }

    @AnyThread
    public void add(@NonNull Policy policy) {
        List<Policy> policies = new ArrayList<>(getPolicies());
        policies.add(policy);
        setPolicies(policies);
    }

    @AnyThread
    public void replace(int index, @NonNull Policy policy) {
        List<Policy> policies = new ArrayList<>(getPolicies());
        policies.set(index, policy);
        setPolicies(policies);
    }

    @AnyThread
    public void remove(int index) {
        List<Policy> policies = new ArrayList<>(getPolicies());
        policies.remove(index);
        setPolicies(policies);
    }

    @AnyThread
    public void move(int from, int to) {
        List<Policy> policies = new ArrayList<>(getPolicies());
        if (from < 0 || from >= policies.size() || to < 0 || to >= policies.size()) {
            throw new IndexOutOfBoundsException("Invalid backup tag policy move: " + from + " -> " + to);
        }
        if (from == to) return;
        policies.add(to, policies.remove(from));
        setPolicies(policies);
    }

    /** Resolve the first rule whose tag is attached to the package. */
    @WorkerThread
    @NonNull
    public Resolution resolve(@NonNull String packageName, @BackupFlags.BackupFlag int defaultFlags) {
        return resolve(packageName, defaultFlags, new RuntimeAvailability());
    }

    @VisibleForTesting
    @WorkerThread
    @NonNull
    Resolution resolve(@NonNull String packageName, @BackupFlags.BackupFlag int defaultFlags,
                       @NonNull Availability availability) {
        Set<String> appTags = mTagStore.getTags(packageName);
        int operationalFlags = defaultFlags & ~BackupFlags.BACKUP_CONTENT_FLAGS;
        for (Policy policy : getPolicies()) {
            if (!appTags.contains(policy.tag)) continue;
            boolean cryptoFallback = !availability.isCryptoAvailable(policy.cryptoMode);
            String cryptoMode = cryptoFallback ? availability.getDefaultCryptoMode() : policy.cryptoMode;
            boolean destinationFallback = policy.destination != null
                    && !availability.isDestinationAvailable(policy.destination);
            int supportedPolicyFlags = policy.flags & availability.getSupportedContentFlags();
            boolean partsFallback = supportedPolicyFlags != policy.flags;
            if (supportedPolicyFlags == 0) {
                supportedPolicyFlags = defaultFlags & BackupFlags.BACKUP_CONTENT_FLAGS;
            }
            Uri destination = destinationFallback || policy.destination != null
                    && policy.destination.equals(Prefs.Storage.getVolumePath())
                    ? null : policy.destination;
            return new Resolution(policy, operationalFlags | supportedPolicyFlags, cryptoMode,
                    policy.maxCount, policy.maxAgeDays, destination,
                    partsFallback, cryptoFallback, destinationFallback);
        }
        return new Resolution(null, defaultFlags, availability.getDefaultCryptoMode(),
                Prefs.BackupRestore.getMaxBackupsPerApp(),
                Prefs.BackupRestore.getMaxBackupAgeDays(), null, false, false, false);
    }

    public interface Availability {
        boolean isCryptoAvailable(@NonNull String mode);

        @NonNull
        String getDefaultCryptoMode();

        boolean isDestinationAvailable(@NonNull Uri destination);

        default int getSupportedContentFlags() {
            return BackupFlags.BACKUP_CONTENT_FLAGS;
        }
    }

    private static final class RuntimeAvailability implements Availability {
        @Override
        public boolean isCryptoAvailable(@NonNull String mode) {
            return isKnownCryptoMode(mode) && CryptoUtils.isAvailable(mode);
        }

        @NonNull
        @Override
        public String getDefaultCryptoMode() {
            return CryptoUtils.getMode();
        }

        @Override
        public boolean isDestinationAvailable(@NonNull Uri destination) {
            try {
                return isSupportedDestination(destination) && Paths.get(destination).isDirectory();
            } catch (RuntimeException e) {
                return false;
            }
        }

        @Override
        public int getSupportedContentFlags() {
            return BackupFlags.getSupportedBackupFlags() & BackupFlags.BACKUP_CONTENT_FLAGS;
        }
    }

    public static final class Policy {
        @NonNull
        public final String tag;
        @BackupFlags.BackupFlag
        public final int flags;
        @NonNull
        @CryptoUtils.Mode
        public final String cryptoMode;
        public final int maxCount;
        public final int maxAgeDays;
        @Nullable
        public final Uri destination;

        public Policy(@NonNull String tag, @BackupFlags.BackupFlag int flags,
                      @NonNull @CryptoUtils.Mode String cryptoMode,
                      int maxCount, int maxAgeDays, @Nullable Uri destination) {
            String normalizedTag = AppTagStore.normalizeTag(tag);
            if (normalizedTag == null) throw new IllegalArgumentException("Invalid app tag: " + tag);
            int contentFlags = flags & BackupFlags.BACKUP_CONTENT_FLAGS;
            if (contentFlags == 0 || contentFlags != flags) {
                throw new IllegalArgumentException("A tag policy must select only backup content parts.");
            }
            if (!isKnownCryptoMode(cryptoMode)) {
                throw new IllegalArgumentException("Unknown backup encryption mode: " + cryptoMode);
            }
            if (maxCount < 0 || maxCount > MAX_RETENTION_COUNT) {
                throw new IllegalArgumentException("Invalid retention count: " + maxCount);
            }
            if (maxAgeDays < 0 || maxAgeDays > MAX_RETENTION_AGE_DAYS) {
                throw new IllegalArgumentException("Invalid retention age: " + maxAgeDays);
            }
            if (destination != null && !isSupportedDestination(destination)) {
                throw new IllegalArgumentException("Unsupported backup destination: " + destination);
            }
            if (destination != null && destination.toString().length() > 4096) {
                throw new IllegalArgumentException("Backup destination is too long.");
            }
            this.tag = normalizedTag;
            this.flags = contentFlags;
            this.cryptoMode = cryptoMode;
            this.maxCount = maxCount;
            this.maxAgeDays = maxAgeDays;
            this.destination = destination;
        }

        @NonNull
        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("tag", tag);
                object.put("flags", flags);
                object.put("crypto", cryptoMode);
                object.put("max_count", maxCount);
                object.put("max_age_days", maxAgeDays);
                object.put("destination", destination != null ? destination.toString() : JSONObject.NULL);
            } catch (JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
            return object;
        }

        @NonNull
        static Policy fromJson(@NonNull JSONObject object) throws JSONException {
            String destination = object.isNull("destination") ? null : object.optString("destination", null);
            return new Policy(object.getString("tag"), object.getInt("flags"),
                    object.getString("crypto"), object.optInt("max_count", 0),
                    object.optInt("max_age_days", 0), destination != null ? Uri.parse(destination) : null);
        }
    }

    public static final class Resolution {
        @Nullable
        public final Policy policy;
        @BackupFlags.BackupFlag
        public final int flags;
        @NonNull
        @CryptoUtils.Mode
        public final String cryptoMode;
        public final int maxCount;
        public final int maxAgeDays;
        @Nullable
        public final Uri destination;
        public final boolean partsFallback;
        public final boolean cryptoFallback;
        public final boolean destinationFallback;

        Resolution(@Nullable Policy policy, int flags, @NonNull String cryptoMode,
                   int maxCount, int maxAgeDays, @Nullable Uri destination,
                   boolean partsFallback, boolean cryptoFallback, boolean destinationFallback) {
            this.policy = policy;
            this.flags = flags;
            this.cryptoMode = cryptoMode;
            this.maxCount = maxCount;
            this.maxAgeDays = maxAgeDays;
            this.destination = destination;
            this.partsFallback = partsFallback;
            this.cryptoFallback = cryptoFallback;
            this.destinationFallback = destinationFallback;
        }

        public boolean isFallback() {
            return policy == null || partsFallback || cryptoFallback || destinationFallback;
        }
    }

    private static boolean isKnownCryptoMode(@NonNull String mode) {
        return CryptoUtils.MODE_NO_ENCRYPTION.equals(mode)
                || CryptoUtils.MODE_AES.equals(mode)
                || CryptoUtils.MODE_RSA.equals(mode)
                || CryptoUtils.MODE_ECC.equals(mode)
                || CryptoUtils.MODE_OPEN_PGP.equals(mode);
    }

    private static boolean isSupportedDestination(@NonNull Uri destination) {
        String scheme = destination.getScheme();
        return (ContentResolver.SCHEME_FILE.equals(scheme) || ContentResolver.SCHEME_CONTENT.equals(scheme))
                && destination.getPath() != null;
    }
}
