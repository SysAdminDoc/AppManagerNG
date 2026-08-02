// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.Path;

@RunWith(RobolectricTestRunner.class)
public class ScannerViewModelTest {
    @Test
    public void cacheFailurePostsToastWithoutThrowingOnWorkerThread() throws Exception {
        FailingFileCache fileCache = new FailingFileCache();
        ScannerViewModel viewModel = new ScannerViewModel(RuntimeEnvironment.getApplication(), fileCache);
        viewModel.setApkUri(Uri.parse("content://example.invalid/missing.apk"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                viewModel.cacheFileIfRequired();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        worker.start();
        worker.join();

        assertTrue(fileCache.called);
        assertNull("cache failure must not escape the worker task", failure.get());
        viewModel.onCleared();
    }

    private static final class FailingFileCache extends FileCache {
        private boolean called;

        @Override
        public File getCachedFile(Path source) throws IOException {
            called = true;
            throw new IOException("test cache failure");
        }
    }

    @Test
    public void sanitizeReportFilePartKeepsPortableCharacters() {
        assertEquals("com.example_app_1.apk",
                ScannerViewModel.sanitizeReportFilePart(" com.example/app:1.apk "));
        assertEquals("", ScannerViewModel.sanitizeReportFilePart(null));
    }

    @Test
    public void signatureMatchesToJsonExportsReadableMatchRows() throws Exception {
        SignatureInfo signatureInfo = new SignatureInfo("com.example.analytics.", "Example Analytics");
        signatureInfo.setCount(3);
        signatureInfo.addClass("com.example.analytics.Sdk");

        JSONArray items = ScannerViewModel.signatureMatchesToJson(Collections.singletonList(signatureInfo));
        JSONObject item = items.getJSONObject(0);

        assertEquals("Example Analytics", item.getString("label"));
        assertEquals("com.example.analytics.", item.getString("signature"));
        assertEquals("Tracker", item.getString("type"));
        assertEquals(3, item.getInt("match_count"));
        assertEquals("com.example.analytics.Sdk", item.getJSONArray("classes").getString(0));
        // Provenance travels with the match, so an exported report can be read on its own.
        assertEquals("Example Analytics", item.getString("display_label"));
        assertEquals(ScannerCertainty.DETECTION_METHOD, item.getString("detection_method"));
        assertFalse(item.getBoolean("tentative"));
        assertEquals("confirmed", item.getString("confidence"));
    }

    @Test
    public void exportedMatchesDistinguishTentativeSignaturesFromConfirmedOnes() throws Exception {
        SignatureInfo tentative = new SignatureInfo("com.example.maybe.", "²Example Maybe");
        tentative.setCount(2);
        SignatureInfo unmatched = new SignatureInfo("com.example.analytics.", "Example Analytics");
        unmatched.setCount(0);

        JSONArray items = ScannerViewModel.signatureMatchesToJson(Arrays.asList(tentative, unmatched));

        JSONObject first = items.getJSONObject(0);
        assertTrue(first.getBoolean("tentative"));
        assertEquals("tentative", first.getString("confidence"));
        assertEquals("Example Maybe", first.getString("display_label"));

        JSONObject second = items.getJSONObject(1);
        assertFalse("a signature that matched nothing is not a second-degree entry",
                second.getBoolean("tentative"));
        assertEquals("but it still supports nothing", "tentative", second.getString("confidence"));
    }

    @Test
    public void buildTrackerDatabaseJsonExportsFreshnessMetadata() throws Exception {
        JSONObject database = ScannerViewModel.buildTrackerDatabaseJson(
                "2026-04-30", 1985, "2026-06-11", 42L);

        assertEquals("2026-04-30", database.getString("bundled_version"));
        assertEquals(1985, database.getInt("signature_count"));
        assertEquals("2026-06-11", database.getString("latest_checked_version"));
        assertEquals(42L, database.getLong("last_check_time"));
    }

    @Test
    public void buildTrackerDatabaseJsonUsesNullsWhenNeverChecked() throws Exception {
        JSONObject database = ScannerViewModel.buildTrackerDatabaseJson(
                "2026-04-30", 1985, "", 0L);

        assertEquals(JSONObject.NULL, database.get("latest_checked_version"));
        assertEquals(JSONObject.NULL, database.get("last_check_time"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onlineReportFetchPolicyRequiresNetworkAndDigests() {
        Pair<String, String>[] digests = new Pair[]{
                new Pair<>("MD5", "md5"),
                new Pair<>("SHA-1", "sha1"),
                new Pair<>("SHA-256", "sha256")
        };

        assertFalse(ScannerViewModel.shouldFetchPithusReport(digests, false));
        assertFalse(ScannerViewModel.shouldFetchPithusReport(null, true));
        assertTrue(ScannerViewModel.shouldFetchPithusReport(digests, true));
        assertFalse(ScannerViewModel.shouldFetchVirusTotalReport(null, digests, true, true));
        assertFalse(ScannerViewModel.shouldFetchVirusTotalReport(null, digests, false, true));
    }
}
