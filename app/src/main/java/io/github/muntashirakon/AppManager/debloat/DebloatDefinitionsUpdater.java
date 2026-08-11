// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class DebloatDefinitionsUpdater {
    private static final String TAG = DebloatDefinitionsUpdater.class.getSimpleName();
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/docs/debloat-definitions/manifest.json";
    private static final String APPROVED_RAW_HOST = "raw.githubusercontent.com";
    private static final String APPROVED_RAW_PATH_PREFIX = "/SysAdminDoc/AppManagerNG/";
    private static final String DATASET_DIR = "debloat-definitions";
    private static final String DEBLOAT_FILE = "debloat.json";
    private static final String SUGGESTIONS_FILE = "suggestions.json";
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final int MAX_DEBLOAT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SUGGESTIONS_BYTES = 1024 * 1024;
    @VisibleForTesting
    static final long UPDATE_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24);

    private DebloatDefinitionsUpdater() {
    }

    public static void scheduleUpdateIfAllowed(@NonNull Context context) {
        if (!isUpdateAllowed()) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastCheck = Prefs.Privacy.getLastDebloatDefinitionsCheckTime();
        if (lastCheck > 0 && now - lastCheck < UPDATE_INTERVAL_MILLIS) {
            return;
        }
        Context appContext = context.getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            Prefs.Privacy.setLastDebloatDefinitionsCheckTime(System.currentTimeMillis());
            try {
                UpdateResult result = updateNow(appContext);
                if (result.updated) {
                    Log.i(TAG, "Updated debloat definitions: %s", result.version);
                } else {
                    Log.i(TAG, "Debloat definitions already current: %s", result.version);
                }
            } catch (Exception th) {
                Log.w(TAG, "Could not update debloat definitions.", th);
            }
        });
    }

    public static boolean isUpdateAllowed() {
        return Prefs.Privacy.autoUpdateDebloatDefinitions() && FeatureController.isInternetEnabled();
    }

    @Nullable
    @WorkerThread
    public static String readCachedDefinition(@NonNull Context context, @NonNull String fileName) {
        if (!DEBLOAT_FILE.equals(fileName) && !SUGGESTIONS_FILE.equals(fileName)) {
            return null;
        }
        return getStore(context).read(fileName);
    }

    @NonNull
    @VisibleForTesting
    static DebloatDefinitionStore getStore(@NonNull Context context) {
        return new DebloatDefinitionStore(new File(context.getFilesDir(), DATASET_DIR));
    }

    @NonNull
    @WorkerThread
    static UpdateResult updateNow(@NonNull Context context) throws IOException {
        Gson gson = new Gson();
        byte[] envelopeBytes = download(MANIFEST_URL, MAX_MANIFEST_BYTES);
        DebloatDefinitionManifest manifest = DebloatDefinitionManifest.verify(gson, envelopeBytes,
                System.currentTimeMillis());
        DebloatDefinitionStore store = getStore(context);
        if (!isUpdateRequired(manifest.generation, store.getActiveGeneration())) {
            return new UpdateResult(false, manifest.version);
        }
        validateApprovedRawUrl(manifest.debloat.getUrl());
        validateApprovedRawUrl(manifest.suggestions.getUrl());
        byte[] debloatBytes = downloadVerified(manifest.debloat, MAX_DEBLOAT_BYTES, "Debloat definition");
        byte[] suggestionsBytes = downloadVerified(manifest.suggestions, MAX_SUGGESTIONS_BYTES, "Debloat suggestions");
        String debloatJson = new String(debloatBytes, StandardCharsets.UTF_8);
        String suggestionsJson = new String(suggestionsBytes, StandardCharsets.UTF_8);
        if (!isValidDatasetPair(gson, debloatJson, suggestionsJson)) {
            throw new IOException("Downloaded debloat definitions failed schema validation.");
        }
        // Both payloads come from the same signed document and are published together, so a reader
        // can never see one file from this generation next to the other from a different one.
        store.publish(manifest.generation, DebloatDefinitionStore.generationFiles(
                new DebloatDefinitionStore.GenerationFile(DEBLOAT_FILE, debloatBytes),
                new DebloatDefinitionStore.GenerationFile(SUGGESTIONS_FILE, suggestionsBytes)));
        Prefs.Privacy.setDebloatDefinitionsVersion(manifest.version);
        Prefs.Privacy.setDebloatDefinitionsSha256(manifest.debloat.getSha256().toLowerCase(Locale.ROOT));
        StaticDataset.clearDebloatObjectsCache();
        return new UpdateResult(true, manifest.version);
    }

    /**
     * @return {@code true} if the signed generation is newer than the published one.
     * @throws IOException if the signed generation is older, i.e. a rollback attempt.
     */
    @VisibleForTesting
    static boolean isUpdateRequired(long candidateGeneration, long activeGeneration) throws IOException {
        if (candidateGeneration < activeGeneration) {
            throw new IOException("Refusing to roll the debloat definitions back from generation "
                    + activeGeneration + " to " + candidateGeneration + ".");
        }
        return candidateGeneration > activeGeneration;
    }

    @NonNull
    private static byte[] downloadVerified(@NonNull DebloatDefinitionManifest.DefinitionFile file,
                                           int maxBytes,
                                           @NonNull String what) throws IOException {
        byte[] bytes = download(file.getUrl(), maxBytes);
        if (file.getBytes() != bytes.length) {
            throw new IOException(what + " length mismatch.");
        }
        if (!sha256(bytes).equalsIgnoreCase(file.getSha256())) {
            throw new IOException(what + " checksum mismatch.");
        }
        return bytes;
    }

    @VisibleForTesting
    static boolean isValidDatasetPair(@NonNull Gson gson, @NonNull String debloatJson,
                                      @NonNull String suggestionsJson) {
        try {
            DebloatObject[] debloatObjects = gson.fromJson(debloatJson, DebloatObject[].class);
            SuggestionObject[] suggestionObjects = gson.fromJson(suggestionsJson, SuggestionObject[].class);
            if (debloatObjects == null || debloatObjects.length == 0 || suggestionObjects == null) {
                return false;
            }
            for (DebloatObject debloatObject : debloatObjects) {
                if (debloatObject == null || debloatObject.packageName == null || debloatObject.type == null) {
                    return false;
                }
            }
            for (SuggestionObject suggestionObject : suggestionObjects) {
                if (suggestionObject == null || suggestionObject.suggestionId == null
                        || suggestionObject.packageName == null) {
                    return false;
                }
            }
            return true;
        } catch (Exception th) {
            return false;
        }
    }

    @VisibleForTesting
    static boolean isApprovedRawGithubUrl(@NonNull String urlString) {
        try {
            URL url = new URL(urlString);
            return "https".equals(url.getProtocol())
                    && APPROVED_RAW_HOST.equals(url.getHost())
                    && url.getPath().startsWith(APPROVED_RAW_PATH_PREFIX);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    @VisibleForTesting
    @NonNull
    static String sha256(@NonNull byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void validateApprovedRawUrl(@NonNull String urlString) throws IOException {
        if (!isApprovedRawGithubUrl(urlString)) {
            throw new IOException("Unapproved debloat definition URL: " + urlString);
        }
    }

    @NonNull
    private static byte[] download(@NonNull String urlString, int maxBytes) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AppManagerNG-DebloatDefinitions");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP " + responseCode + " for " + urlString);
            }
            int length = connection.getContentLength();
            if (length > maxBytes) {
                throw new IOException("Response is too large for " + urlString + ": " + length);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return readBounded(inputStream, maxBytes);
            }
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private static byte[] readBounded(@NonNull InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response exceeded " + maxBytes + " bytes.");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; ++i) {
            int value = bytes[i] & 0xff;
            out[i * 2] = hex[value >>> 4];
            out[i * 2 + 1] = hex[value & 0x0f];
        }
        return new String(out);
    }

    static final class UpdateResult {
        final boolean updated;
        @NonNull
        final String version;

        UpdateResult(boolean updated, @NonNull String version) {
            this.updated = updated;
            this.version = version;
        }
    }

}
