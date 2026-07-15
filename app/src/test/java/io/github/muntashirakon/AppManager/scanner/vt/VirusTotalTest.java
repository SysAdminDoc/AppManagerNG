// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;

import io.github.muntashirakon.AppManager.backup.convert.OABConverter;

public class VirusTotalTest {
    private static final String API_KEY = null;
    private static final String TEST_API_KEY = "test-secret-api-key";
    private static final URL TRUSTED_UPLOAD_URL;

    static {
        try {
            TRUSTED_UPLOAD_URL = new URL("https://www.virustotal.com/_ah/upload/one-time-token");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private final ClassLoader classLoader = getClass().getClassLoader();
    private VirusTotal vt;

    @Before
    public void setUp() throws Exception {
        if (API_KEY == null) return;
        vt = new VirusTotal(API_KEY);
    }

    @Test
    public void uploadFileThrowsNothing() throws IOException {
        if (vt == null) return;
        assert classLoader != null;
        File baseApk = new File(classLoader.getResource(OABConverter.PATH_SUFFIX).getFile(), "dnsfilter.android/base.apk");
        try (FileInputStream fis = new FileInputStream(baseApk)) {
            VirusTotal.ResponseV3<String> vtFileScanMeta = vt.uploadFile("dnsfilter.android", fis);
            System.out.println(vtFileScanMeta);
        }
    }

    @Test
    public void fetchFileReportThrowsNothing() throws IOException {
        if (vt == null) return;
        VtFileReport report1 = vt.fetchFileReport("029e2ed8dea7db94a293bdb7c0d197059f85d4dc51b6ff56548b29b65afe13c5").response;
        VtFileReport report2 = vt.fetchFileReport("a5146a143c7bbd6a0b8384a1aa233243b72cca94cbec62aa3d70a82f5b262550").response;
        // Throws nothing
        System.out.println(report1);
        System.out.println(report2);
    }

    @Test
    public void uploadUsesFinitePolicyAndDoesNotFollowRedirects() throws IOException {
        FakeHttpURLConnection connection = FakeHttpURLConnection.success(
                "{\"data\":{\"type\":\"analysis\",\"id\":\"analysis-id\"}}");
        TestVirusTotal virusTotal = new TestVirusTotal(123, 456, 1000, 128);
        virusTotal.enqueue(connection);

        VirusTotal.ResponseV3<String> response = virusTotal.uploadAnyFile(TRUSTED_UPLOAD_URL,
                "sample.apk", new ByteArrayInputStream(new byte[]{1, 2, 3}), null);

        assertEquals("analysis-id", response.response);
        assertEquals(123, connection.getConnectTimeout());
        assertEquals(456, connection.getReadTimeout());
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals(TEST_API_KEY, connection.getRequestProperty("x-apikey"));
        assertTrue(connection.requestBody.size() > 3);
        assertTrue(connection.disconnected);
    }

    @Test
    public void stalledUploadHitsDeadlineAndDisconnects() {
        FakeHttpURLConnection connection = FakeHttpURLConnection.success("{}");
        connection.requestStream = new StallingOutputStream();
        TestVirusTotal virusTotal = new TestVirusTotal(123, 456, 25, 128);
        virusTotal.enqueue(connection);

        IOException error = assertThrows(IOException.class,
                () -> virusTotal.uploadAnyFile(TRUSTED_UPLOAD_URL, "sample.apk",
                        new ByteArrayInputStream(new byte[]{1}), null));

        assertTrue(error instanceof SocketTimeoutException);
        assertFalse(error.getMessage().contains(TEST_API_KEY));
        assertTrue(connection.disconnected);
    }

    @Test
    public void redirectsRemainErrorsAndNeverOpenASecondConnection() throws IOException {
        FakeHttpURLConnection connection = FakeHttpURLConnection.error(
                HttpURLConnection.HTTP_MOVED_TEMP,
                "{\"error\":{\"code\":\"Redirect\",\"message\":\"not followed\"}}");
        TestVirusTotal virusTotal = new TestVirusTotal(123, 456, 1000, 128);
        virusTotal.enqueue(connection);

        VirusTotal.ResponseV3<String> response = virusTotal.uploadAnyFile(TRUSTED_UPLOAD_URL,
                "sample.apk", new ByteArrayInputStream(new byte[]{1}), null);

        assertNotNull(response.error);
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, response.httpCode);
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals(1, virusTotal.openCount);
    }

    @Test
    public void delegatedInvalidOriginsAreRejectedBeforeApiKeyCanReachThem() {
        assertDelegatedOriginRejected("https://uploads.evil.example/collect");
        assertDelegatedOriginRejected("http://www.virustotal.com/_ah/upload/token");
        assertDelegatedOriginRejected("https://www.virustotal.com:444/_ah/upload/token");
    }

    @Test
    public void delegatedTrustedOriginUsesTheSameConnectionPolicy() throws IOException {
        FakeHttpURLConnection uploadUrlResponse = FakeHttpURLConnection.success(
                "{\"data\":\"" + TRUSTED_UPLOAD_URL + "\"}");
        FakeHttpURLConnection uploadResponse = FakeHttpURLConnection.success(
                "{\"data\":{\"type\":\"analysis\",\"id\":\"large-analysis-id\"}}");
        TestVirusTotal virusTotal = new TestVirusTotal(123, 456, 1000, 256);
        virusTotal.enqueue(uploadUrlResponse);
        virusTotal.enqueue(uploadResponse);

        VirusTotal.ResponseV3<String> response = virusTotal.uploadLargeFile("sample.apk",
                new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertEquals("large-analysis-id", response.response);
        assertEquals(2, virusTotal.openCount);
        assertEquals(123, uploadResponse.getConnectTimeout());
        assertEquals(456, uploadResponse.getReadTimeout());
        assertFalse(uploadResponse.getInstanceFollowRedirects());
    }

    @Test
    public void oversizedSuccessAndErrorBodiesAreBoundedWithoutKeyDisclosure() {
        byte[] oversized = new byte[65];

        FakeHttpURLConnection success = new FakeHttpURLConnection(HttpURLConnection.HTTP_OK,
                new ByteArrayInputStream(oversized), null);
        TestVirusTotal successClient = new TestVirusTotal(123, 456, 1000, 64);
        successClient.enqueue(success);
        IOException successError = assertThrows(IOException.class,
                () -> successClient.uploadAnyFile(TRUSTED_UPLOAD_URL, "sample.apk",
                        new ByteArrayInputStream(new byte[]{1}), null));
        assertTrue(successError.getMessage().contains("64 bytes"));
        assertFalse(successError.getMessage().contains(TEST_API_KEY));
        assertTrue(success.disconnected);

        FakeHttpURLConnection failure = new FakeHttpURLConnection(
                HttpURLConnection.HTTP_BAD_REQUEST, null, new ByteArrayInputStream(oversized));
        TestVirusTotal failureClient = new TestVirusTotal(123, 456, 1000, 64);
        failureClient.enqueue(failure);
        IOException failureError = assertThrows(IOException.class,
                () -> failureClient.uploadAnyFile(TRUSTED_UPLOAD_URL, "sample.apk",
                        new ByteArrayInputStream(new byte[]{1}), null));
        assertTrue(failureError.getMessage().contains("64 bytes"));
        assertFalse(failureError.getMessage().contains(TEST_API_KEY));
        assertTrue(failure.disconnected);
    }

    private static void assertDelegatedOriginRejected(String delegatedUrl) {
        FakeHttpURLConnection uploadUrlResponse = FakeHttpURLConnection.success(
                "{\"data\":\"" + delegatedUrl + "\"}");
        TestVirusTotal virusTotal = new TestVirusTotal(123, 456, 1000, 256);
        virusTotal.enqueue(uploadUrlResponse);

        IOException error = assertThrows(IOException.class,
                () -> virusTotal.uploadLargeFile("sample.apk",
                        new ByteArrayInputStream(new byte[]{1})));

        assertEquals(1, virusTotal.openCount);
        assertFalse(error.getMessage().contains(TEST_API_KEY));
        assertTrue(uploadUrlResponse.disconnected);
    }

    private static final class TestVirusTotal extends VirusTotal {
        private final Queue<HttpURLConnection> connections = new ArrayDeque<>();
        int openCount;

        TestVirusTotal(int connectTimeoutMillis, int readTimeoutMillis,
                       long uploadTimeoutMillis, int maxJsonResponseBytes) {
            super(TEST_API_KEY, connectTimeoutMillis, readTimeoutMillis, uploadTimeoutMillis,
                    maxJsonResponseBytes);
        }

        void enqueue(HttpURLConnection connection) {
            connections.add(connection);
        }

        @Override
        protected HttpURLConnection openConnection(URL url) throws IOException {
            ++openCount;
            HttpURLConnection connection = connections.poll();
            if (connection == null) {
                throw new IOException("Unexpected connection to " + url.getHost());
            }
            return connection;
        }
    }

    private static final class FakeHttpURLConnection extends HttpURLConnection {
        private final InputStream responseStream;
        private final InputStream errorStream;
        final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        OutputStream requestStream = requestBody;
        boolean disconnected;

        FakeHttpURLConnection(int responseCode, InputStream responseStream, InputStream errorStream) {
            super(TRUSTED_UPLOAD_URL);
            this.responseCode = responseCode;
            this.responseStream = responseStream;
            this.errorStream = errorStream;
        }

        static FakeHttpURLConnection success(String json) {
            return new FakeHttpURLConnection(HttpURLConnection.HTTP_OK,
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), null);
        }

        static FakeHttpURLConnection error(int status, String json) {
            return new FakeHttpURLConnection(status, null,
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseStream == null) {
                throw new IOException("No success response.");
            }
            return responseStream;
        }

        @Override
        public InputStream getErrorStream() {
            return errorStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return requestStream;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }

    private static final class StallingOutputStream extends OutputStream {
        @Override
        public void write(int value) throws IOException {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("cancelled");
            }
        }
    }
}
