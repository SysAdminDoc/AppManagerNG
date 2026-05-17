// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import io.github.muntashirakon.io.IoUtils;

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
        new Thread(() -> {
            Prefs.Privacy.setLastDebloatDefinitionsCheckTime(System.currentTimeMillis());
            try {
                UpdateResult result = updateNow(appContext);
                if (result.updated) {
                    Log.i(TAG, "Updated debloat definitions: %s", result.version);
                } else {
                    Log.i(TAG, "Debloat definitions already current: %s", result.version);
                }
            } catch (Throwable th) {
                Log.w(TAG, "Could not update debloat definitions.", th);
            }
        }, "debloat-definitions-updater").start();
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
        File file = getCachedFile(context, fileName);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return IoUtils.getInputStreamContent(inputStream);
        } catch (IOException e) {
            Log.w(TAG, "Could not read cached debloat definition file: %s", e, file);
            return null;
        }
    }

    @NonNull
    @WorkerThread
    static UpdateResult updateNow(@NonNull Context context) throws IOException {
        Gson gson = new Gson();
        byte[] manifestBytes = download(MANIFEST_URL, MAX_MANIFEST_BYTES);
        DefinitionManifest manifest = gson.fromJson(new String(manifestBytes, StandardCharsets.UTF_8),
                DefinitionManifest.class);
        if (!isValidManifest(manifest)) {
            throw new IOException("Invalid debloat definition manifest.");
        }
        DefinitionFile debloatFile = manifest.files.debloat;
        DefinitionFile suggestionsFile = manifest.files.suggestions;
        validateApprovedRawUrl(debloatFile.url);
        validateApprovedRawUrl(suggestionsFile.url);
        byte[] debloatBytes = download(debloatFile.url, MAX_DEBLOAT_BYTES);
        byte[] suggestionsBytes = download(suggestionsFile.url, MAX_SUGGESTIONS_BYTES);
        if (debloatFile.bytes != debloatBytes.length) {
            throw new IOException("Debloat definition length mismatch.");
        }
        if (suggestionsFile.bytes != suggestionsBytes.length) {
            throw new IOException("Debloat suggestions length mismatch.");
        }
        String debloatSha256 = sha256(debloatBytes);
        String suggestionsSha256 = sha256(suggestionsBytes);
        if (!debloatSha256.equalsIgnoreCase(debloatFile.sha256)) {
            throw new IOException("Debloat definition checksum mismatch.");
        }
        if (!suggestionsSha256.equalsIgnoreCase(suggestionsFile.sha256)) {
            throw new IOException("Debloat suggestions checksum mismatch.");
        }
        String debloatJson = new String(debloatBytes, StandardCharsets.UTF_8);
        String suggestionsJson = new String(suggestionsBytes, StandardCharsets.UTF_8);
        if (!isValidDatasetPair(gson, debloatJson, suggestionsJson)) {
            throw new IOException("Downloaded debloat definitions failed schema validation.");
        }
        boolean alreadyCurrent = debloatSha256.equalsIgnoreCase(Prefs.Privacy.getDebloatDefinitionsSha256());
        writeAtomically(getCachedFile(context, DEBLOAT_FILE), debloatBytes);
        writeAtomically(getCachedFile(context, SUGGESTIONS_FILE), suggestionsBytes);
        Prefs.Privacy.setDebloatDefinitionsVersion(manifest.version);
        Prefs.Privacy.setDebloatDefinitionsSha256(debloatSha256);
        StaticDataset.clearDebloatObjectsCache();
        return new UpdateResult(!alreadyCurrent, manifest.version);
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
        } catch (Throwable th) {
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

    private static boolean isValidManifest(@Nullable DefinitionManifest manifest) {
        return manifest != null
                && manifest.schema == 1
                && manifest.version != null
                && manifest.files != null
                && manifest.files.debloat != null
                && manifest.files.suggestions != null
                && isValidFile(manifest.files.debloat)
                && isValidFile(manifest.files.suggestions);
    }

    private static boolean isValidFile(@NonNull DefinitionFile file) {
        return file.url != null
                && file.sha256 != null
                && file.sha256.length() == 64
                && file.bytes > 0;
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

    private static void writeAtomically(@NonNull File target, @NonNull byte[] bytes) throws IOException {
        File dir = target.getParentFile();
        if (dir == null) {
            throw new IOException("Missing parent directory for " + target);
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        File tempFile = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            outputStream.write(bytes);
            outputStream.flush();
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Could not replace " + target);
        }
        if (!tempFile.renameTo(target)) {
            throw new IOException("Could not move " + tempFile + " to " + target);
        }
    }

    @NonNull
    private static File getCachedFile(@NonNull Context context, @NonNull String fileName) {
        return new File(new File(context.getFilesDir(), DATASET_DIR), fileName);
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
        return new String(out).toLowerCase(Locale.ROOT);
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

    private static final class DefinitionManifest {
        @SerializedName("schema")
        int schema;
        @SerializedName("version")
        String version;
        @SerializedName("files")
        DefinitionFiles files;
    }

    private static final class DefinitionFiles {
        @SerializedName("debloat")
        DefinitionFile debloat;
        @SerializedName("suggestions")
        DefinitionFile suggestions;
    }

    private static final class DefinitionFile {
        @SerializedName("url")
        String url;
        @SerializedName("sha256")
        String sha256;
        @SerializedName("bytes")
        long bytes;
    }
}
