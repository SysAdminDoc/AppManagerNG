// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

public class Pithus {
    private static final String BASE_URL = "https://beta.pithus.org/report";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    @WorkerThread
    @Nullable
    public static String resolveReport(@NonNull String sha256Sum) throws IOException {
        if (!isValidSha256(sha256Sum)) {
            return null;
        }
        URL url = new URL(BASE_URL + "/" + sha256Sum);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            if (isReportAvailableResponse(connection.getResponseCode())) {
                return url.toString();
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    static boolean isValidSha256(@Nullable String sha256Sum) {
        return sha256Sum != null && SHA256_PATTERN.matcher(sha256Sum).matches();
    }

    static boolean isReportAvailableResponse(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_OK;
    }
}
