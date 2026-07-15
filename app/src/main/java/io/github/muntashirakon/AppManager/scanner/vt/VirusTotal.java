// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class VirusTotal {
    // ~10 minutes of 30 s polls after the initial wait.
    private static final int MAX_POLL_ATTEMPTS = 20;
    static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    static final int READ_TIMEOUT_MILLIS = 60_000;
    static final long UPLOAD_TIMEOUT_MILLIS = 20L * 60 * 1000;
    static final int MAX_JSON_RESPONSE_BYTES = 1024 * 1024;
    private static final int UPLOAD_CHUNK_BYTES = 64 * 1024;
    private static final String VIRUSTOTAL_UPLOAD_HOST = "www.virustotal.com";

    public interface FullScanResponseInterface {
        boolean uploadFile();

        void onUploadInitiated();

        void onUploadCompleted(@NonNull String permalink);

        void onReportReceived(@NonNull VtFileReport report);
    }

    public static class ResponseV3<T> {
        @Nullable
        public final T response;
        @Nullable
        public final VtError error;
        public final int httpCode;

        public ResponseV3(@Nullable T response, @Nullable VtError error) {
            // The params are mutually exclusive
            assert (response != null && error == null) || (response == null && error != null);
            this.response = response;
            this.error = error;
            if (error != null) {
                httpCode = error.httpErrorCode;
            } else httpCode = HttpURLConnection.HTTP_OK;
        }

        public boolean shouldRetry() {
            // It should only retry when the quota is exceeded, or the resource is not found, or
            // the resource is not yet available
            if (error == null || error.code == null) {
                return false;
            }
            return (error.code.equals("NotAvailableYet")
                    || error.code.equals("NotFoundError")
                    || error.code.equals("QuotaExceededError"));
        }

        @NonNull
        @Override
        public String toString() {
            return "ResponseV3{" +
                    "response=" + response +
                    ", error=" + error +
                    ", httpCode=" + httpCode +
                    '}';
        }
    }

    protected static final String FORM_DATA_BOUNDARY = "--AppManagerDataBoundary9f3d77ed3a";
    protected static final String API_V3_PREFIX = "https://www.virustotal.com/api/v3";
    protected static final String URL_FILE_UPLOAD = API_V3_PREFIX + "/files";
    protected static final String URL_LARGE_FILE_UPLOAD = API_V3_PREFIX + "/files/upload_url";
    protected static final String URL_FILE_REPORT = API_V3_PREFIX + "/files/";

    @Nullable
    public static VirusTotal getInstance() {
        String apiKey = Prefs.VirusTotal.getApiKey();
        if (FeatureController.isVirusTotalEnabled() && apiKey != null) {
            return new VirusTotal(apiKey);
        }
        return null;
    }

    private final String mApiKey;
    private final int mConnectTimeoutMillis;
    private final int mReadTimeoutMillis;
    private final long mUploadTimeoutMillis;
    private final int mMaxJsonResponseBytes;

    public VirusTotal(@NonNull String apiKey) {
        this(apiKey, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS, UPLOAD_TIMEOUT_MILLIS,
                MAX_JSON_RESPONSE_BYTES);
    }

    VirusTotal(@NonNull String apiKey, int connectTimeoutMillis, int readTimeoutMillis,
               long uploadTimeoutMillis, int maxJsonResponseBytes) {
        mApiKey = Objects.requireNonNull(apiKey);
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0 || uploadTimeoutMillis <= 0
                || maxJsonResponseBytes <= 0) {
            throw new IllegalArgumentException("VirusTotal I/O limits must be positive.");
        }
        mConnectTimeoutMillis = connectTimeoutMillis;
        mReadTimeoutMillis = readTimeoutMillis;
        mUploadTimeoutMillis = uploadTimeoutMillis;
        mMaxJsonResponseBytes = maxJsonResponseBytes;
    }

    public void fetchFileReportOrScan(@NonNull Path file,
                                      @NonNull String checksum,
                                      @NonNull FullScanResponseInterface response)
            throws IOException {
        ResponseV3<VtFileReport> responseReport = fetchFileReport(checksum);
        if (responseReport.response != null && responseReport.response.hasReport()) {
            // A report is found
            response.onReportReceived(responseReport.response);
            return;
        }
        // No report found: either failed or still queued
        boolean queued = responseReport.response != null && !responseReport.response.hasReport();
        if (!queued && !responseReport.shouldRetry()) {
            // Retry is not available
            throw new FileNotFoundException("Fetch error: " + responseReport.error);
        }
        // Scan or retry
        boolean waitFirst = false;
        if (!queued && responseReport.error != null
                && "NotFoundError".equals(responseReport.error.code)) {
            // Initiate scan
            if (!response.uploadFile()) {
                // Scanning disabled
                throw new FileNotFoundException("File not found in VirusTotal.");
            }
            waitFirst = true;
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("vt_upload");
            wakeLock.acquire();
            try {
                long fileSize = file.length();
                if (fileSize > 650_000_000) {
                    throw new IOException("APK is larger than 650 MB.");
                }
                boolean largeFile = fileSize > 32_000_000L;
                response.onUploadInitiated();
                String filename = file.getName();
                ResponseV3<String> uploadResponse;
                try (InputStream is = file.openInputStream()) {
                    uploadResponse = largeFile
                            ? uploadLargeFile(filename, is)
                            : uploadFile(filename, is);
                }
                if (uploadResponse.response != null) {
                    response.onUploadCompleted(getPermalink(checksum));
                }
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
        }
        // Initial wait scales with file size — VT engines need proportionally
        // longer to process larger APKs, so polling earlier just burns rate-limit
        // quota. Floor: 60 s for files ≤10 MB. Ceiling: 240 s. Roughly +1 s per
        // MB beyond the 10 MB threshold (so 100 MB ≈ 150 s, 200 MB clamps to
        // 240 s, 600 MB also 240 s).
        long firstWaitMs = computeInitialPollWait(file.length());
        long waitDuration = firstWaitMs;
        int attempts = 0;
        while (queued || responseReport.shouldRetry()) {
            if (++attempts > MAX_POLL_ATTEMPTS) {
                // Quota-exceeded / not-available-yet can persist indefinitely on the
                // free tier; give up instead of holding a scanner thread forever.
                throw new IOException("Report not available after " + MAX_POLL_ATTEMPTS
                        + " polls: " + responseReport.error);
            }
            if (waitFirst) {
                // Effectively makes it a do-while loop
                waitFirst = false;
            } else {
                responseReport = fetchFileReport(checksum);
                queued = responseReport.response != null && !responseReport.response.hasReport();
            }
            SystemClock.sleep(waitDuration);
            // 30 s is the lowest poll interval the free API tolerates
            // (4 requests / minute rate limit).
            waitDuration = 30_000L;
        }
        if (responseReport.response != null) {
            response.onReportReceived(responseReport.response);
        } else {
            throw new IOException("Scan error: " + responseReport.error);
        }
    }

    /**
     * First-poll wait scaled to the upload size. Ramps roughly +1 s per MB
     * above a 10 MB threshold, clamped to [60 s, 240 s]. Subsequent polls
     * stay at the API rate-limit floor of 30 s; only the *initial* wait
     * benefits from the file-size hint.
     */
    static long computeInitialPollWait(long fileSize) {
        final long baseMs = 60_000L;
        final long maxMs = 240_000L;
        final long thresholdBytes = 10L * 1024L * 1024L;
        if (fileSize <= thresholdBytes) {
            return baseMs;
        }
        // (fileSize − 10 MB) / 1 KB ≈ 1 ms per KB ≈ 1 s per MB.
        long extraMs = (fileSize - thresholdBytes) / 1024L;
        return Math.min(maxMs, baseMs + extraMs);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadFile(@NonNull String filename, @NonNull InputStream is)
            throws IOException {
        return uploadFile(filename, is, null);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadFile(@NonNull String filename, @NonNull InputStream is,
                                         @Nullable String password) throws IOException {
        URL url = new URL(URL_FILE_UPLOAD);
        return uploadAnyFile(url, filename, is, password);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadLargeFile(@NonNull String filename, @NonNull InputStream is)
            throws IOException {
        return uploadLargeFile(filename, is, null);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadLargeFile(@NonNull String filename, @NonNull InputStream is,
                                              @Nullable String password) throws IOException {
        // First retrieve the upload URL
        URL url = new URL(URL_LARGE_FILE_UPLOAD);
        HttpURLConnection connection = openConfiguredConnection(url);
        try {
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            // Set headers
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("x-apikey", mApiKey);
            // Response
            int status = connection.getResponseCode();
            if (status < 300) {
                // Success
                // Upload the actual file
                URL uploadUrl = getLargeFileUploadUrl(connection, mMaxJsonResponseBytes);
                validateUploadUrl(uploadUrl);
                return uploadAnyFile(uploadUrl, filename, is, password);
            } else {
                // Failed
                return new ResponseV3<>(null, getErrorResponse(connection, mMaxJsonResponseBytes));
            }
        } finally {
            connection.disconnect();
        }
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadAnyFile(@NonNull URL uploadUrl, @NonNull String filename,
                                            @NonNull InputStream is, @Nullable String password)
            throws IOException {
        validateUploadUrl(uploadUrl);
        HttpURLConnection connection = openConfiguredConnection(uploadUrl);
        try {
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(UPLOAD_CHUNK_BYTES);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            // Set headers
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("x-apikey", mApiKey);
            connection.setRequestProperty("content-type", "multipart/form-data; boundary=" + FORM_DATA_BOUNDARY);
            // Set form data
            writeUploadBodyWithDeadline(connection, filename, is, password);
            // Response
            int status = connection.getResponseCode();
            if (status < 300) {
                // Success
                // Example response: {
                //  "data": {
                //    "type": "analysis",
                //    "id": "base64_hash",
                //    "links": {
                //      "self": "https://www.virustotal.com/api/v3/analyses/base64_hash"
                //    }
                //  }
                //}
                return new ResponseV3<>(getAnalysisId(connection, mMaxJsonResponseBytes), null);
            } else {
                // Failed
                return new ResponseV3<>(null, getErrorResponse(connection, mMaxJsonResponseBytes));
            }
        } finally {
            connection.disconnect();
        }
    }

    @WorkerThread
    @NonNull
    public ResponseV3<VtFileReport> fetchFileReport(@NonNull String id) throws IOException {
        URL url = new URL(URL_FILE_REPORT + id);
        HttpURLConnection connection = openConfiguredConnection(url);
        try {
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            // Set headers
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("x-apikey", mApiKey);
            // Response
            int status = connection.getResponseCode();
            if (status < 300) {
                // Success
                try {
                    JSONObject jsonObject = new JSONObject(
                            getResponseV3(connection, mMaxJsonResponseBytes));
                    return new ResponseV3<>(new VtFileReport(jsonObject), null);
                } catch (JSONException e) {
                    throw new IOException(e);
                }
            } else {
                // Failed
                return new ResponseV3<>(null, getErrorResponse(connection, mMaxJsonResponseBytes));
            }
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    public static String getPermalink(@NonNull String id) {
        return "https://www.virustotal.com/gui/file/" + id;
    }

    @NonNull
    public static String getAnalysisId(@NonNull HttpURLConnection connection) throws IOException {
        return getAnalysisId(connection, MAX_JSON_RESPONSE_BYTES);
    }

    @NonNull
    static String getAnalysisId(@NonNull HttpURLConnection connection, int maxBytes) throws IOException {
        // https://docs.virustotal.com/reference/files-scan
        try {
            JSONObject dataObject = new JSONObject(getResponseV3(connection, maxBytes))
                    .getJSONObject("data");
            assert dataObject.getString("type").equals("analysis");
            return dataObject.getString("id");
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    public static URL getLargeFileUploadUrl(@NonNull HttpURLConnection connection) throws IOException {
        return getLargeFileUploadUrl(connection, MAX_JSON_RESPONSE_BYTES);
    }

    @NonNull
    static URL getLargeFileUploadUrl(@NonNull HttpURLConnection connection, int maxBytes)
            throws IOException {
        // https://docs.virustotal.com/reference/files-upload-url
        try {
            return new URL(new JSONObject(getResponseV3(connection, maxBytes)).getString("data"));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static void addMultipartFormData(@NonNull OutputStream os, @NonNull String key, String value) throws IOException {
        os.write(("--" + FORM_DATA_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Disposition: form-data; name=\"" + key + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Type: text/plain; charset=UTF-8\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void addMultipartFormData(@NonNull OutputStream os, @NonNull String key, @NonNull String filename,
                                            @NonNull InputStream is) throws IOException {
        os.write(("--" + FORM_DATA_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Disposition: form-data; name=\"" + key + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Type: application/octet-stream\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        IoUtils.copy(is, os);
    }

    @WorkerThread
    @NonNull
    public static String getResponseV3(@NonNull HttpURLConnection connection) throws IOException {
        return getResponseV3(connection, MAX_JSON_RESPONSE_BYTES);
    }

    @NonNull
    static String getResponseV3(@NonNull HttpURLConnection connection, int maxBytes)
            throws IOException {
        int contentLength = connection.getContentLength();
        if (contentLength > maxBytes) {
            throw responseTooLarge(maxBytes);
        }
        try (InputStream inputStream = connection.getInputStream()) {
            return readUtf8Bounded(inputStream, maxBytes);
        }
    }

    @WorkerThread
    @NonNull
    public static VtError getErrorResponse(@NonNull HttpURLConnection connection) throws IOException {
        return getErrorResponse(connection, MAX_JSON_RESPONSE_BYTES);
    }

    @NonNull
    static VtError getErrorResponse(@NonNull HttpURLConnection connection, int maxBytes)
            throws IOException {
        int status = connection.getResponseCode();
        int contentLength = connection.getContentLength();
        if (contentLength > maxBytes) {
            throw responseTooLarge(maxBytes);
        }
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            try {
                errorStream = connection.getInputStream();
            } catch (IOException ignore) {
                return new VtError(status, null);
            }
        }
        try (InputStream inputStream = errorStream) {
            return new VtError(status, readUtf8Bounded(inputStream, maxBytes));
        }
    }

    @NonNull
    protected HttpURLConnection openConnection(@NonNull URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    @NonNull
    private HttpURLConnection openConfiguredConnection(@NonNull URL url) throws IOException {
        HttpURLConnection connection = openConnection(url);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setConnectTimeout(mConnectTimeoutMillis);
        connection.setReadTimeout(mReadTimeoutMillis);
        return connection;
    }

    static void validateUploadUrl(@NonNull URL uploadUrl) throws IOException {
        int port = uploadUrl.getPort();
        if (!"https".equalsIgnoreCase(uploadUrl.getProtocol())
                || !VIRUSTOTAL_UPLOAD_HOST.equalsIgnoreCase(uploadUrl.getHost())
                || (port != -1 && port != 443)
                || uploadUrl.getUserInfo() != null) {
            throw new IOException("Refusing untrusted VirusTotal upload origin.");
        }
    }

    private void writeUploadBodyWithDeadline(@NonNull HttpURLConnection connection,
                                             @NonNull String filename,
                                             @NonNull InputStream is,
                                             @Nullable String password) throws IOException {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "VirusTotalUpload");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> upload = executor.submit(() -> {
            try (OutputStream outputStream = connection.getOutputStream()) {
                if (password != null) {
                    addMultipartFormData(outputStream, "password", password);
                }
                addMultipartFormData(outputStream, "file", filename, is);
                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                outputStream.write(("--" + FORM_DATA_BOUNDARY + "--\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            return null;
        });
        try {
            upload.get(mUploadTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            connection.disconnect();
            upload.cancel(true);
            throw new SocketTimeoutException("VirusTotal upload exceeded the configured deadline.");
        } catch (InterruptedException e) {
            connection.disconnect();
            upload.cancel(true);
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "VirusTotal upload was interrupted.");
            interrupted.initCause(e);
            throw interrupted;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException("VirusTotal upload failed.", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    @NonNull
    private static String readUtf8Bounded(@NonNull InputStream inputStream, int maxBytes)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (read > maxBytes - total) {
                throw responseTooLarge(maxBytes);
            }
            outputStream.write(buffer, 0, read);
            total += read;
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    @NonNull
    private static IOException responseTooLarge(int maxBytes) {
        return new IOException("VirusTotal response exceeded " + maxBytes + " bytes.");
    }
}
