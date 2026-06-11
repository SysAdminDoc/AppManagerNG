// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class TrackerDatabaseFreshnessChecker {
    private static final String TAG = TrackerDatabaseFreshnessChecker.class.getSimpleName();
    private static final String TRACKERS_RESOURCE_URL =
            "https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/app/src/main/res/values/trackers.xml";
    private static final int MAX_TRACKERS_RESOURCE_BYTES = 512 * 1024;
    @VisibleForTesting
    static final long CHECK_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "<string\\s+[^>]*name=\"tracker_database_version\"[^>]*>([^<]+)</string>");

    private TrackerDatabaseFreshnessChecker() {
    }

    public static void scheduleCheckIfAllowed(@NonNull Context context) {
        if (!isCheckAllowed()) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastCheck = Prefs.Privacy.getLastTrackerDatabaseCheckTime();
        if (lastCheck > 0 && now - lastCheck < CHECK_INTERVAL_MILLIS) {
            return;
        }
        new Thread(() -> {
            Prefs.Privacy.setLastTrackerDatabaseCheckTime(System.currentTimeMillis());
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion != null) {
                    Prefs.Privacy.setLatestTrackerDatabaseVersion(latestVersion);
                    Log.i(TAG, "Latest tracker database version: %s", latestVersion);
                }
            } catch (Throwable th) {
                Log.w(TAG, "Could not check tracker database freshness.", th);
            }
        }, "tracker-database-freshness").start();
    }

    public static boolean isCheckAllowed() {
        return Prefs.Privacy.checkTrackerDatabaseFreshness()
                && FeatureController.areOptionalNetworkFeaturesAvailable()
                && FeatureController.isInternetEnabled();
    }

    @Nullable
    static String fetchLatestVersion() throws IOException {
        return extractVersion(download(TRACKERS_RESOURCE_URL, MAX_TRACKERS_RESOURCE_BYTES));
    }

    @Nullable
    @VisibleForTesting
    static String extractVersion(@NonNull String resourceXml) {
        Matcher matcher = VERSION_PATTERN.matcher(resourceXml);
        if (!matcher.find()) {
            return null;
        }
        String version = matcher.group(1);
        return version != null ? version.trim() : null;
    }

    @NonNull
    private static String download(@NonNull String urlString, int maxBytes) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setRequestProperty("Accept", "application/xml,text/xml");
            connection.setRequestProperty("User-Agent", "AppManagerNG-TrackerDatabaseFreshness");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP " + responseCode + " for " + urlString);
            }
            int length = connection.getContentLength();
            if (length > maxBytes) {
                throw new IOException("Response is too large for " + urlString + ": " + length);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return new String(readBounded(inputStream, maxBytes), StandardCharsets.UTF_8);
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
}
