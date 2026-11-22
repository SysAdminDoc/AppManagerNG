// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.AppManager.main.ApplicationItem;

/**
 * Issue 41: the main list's "Blocks screen or audio capture" filter reads only what App Info already found. It never
 * scans an APK while the list loads, and a scan of an older version of the app doesn't count.
 */
@RunWith(RobolectricTestRunner.class)
public class MediaCaptureCacheTest {
    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void aScanIsKeptForThatVersionOfTheApp() {
        MediaCaptureCache.put(mContext, "com.example.bank", 7, 1000L, MediaCaptureScanner.FLAG_SECURE_WINDOW);

        assertEquals(Integer.valueOf(MediaCaptureScanner.FLAG_SECURE_WINDOW),
                MediaCaptureCache.get(mContext, "com.example.bank", 7, 1000L));
        assertNull("an update makes it stale", MediaCaptureCache.get(mContext, "com.example.bank", 8, 2000L));
        assertNull(MediaCaptureCache.get(mContext, "com.example.unknown", 7, 1000L));
    }

    @Test
    public void theFilterUsesOnlyTheKeptScans() {
        MediaCaptureCache.put(mContext, "com.example.bank", 7, 1000L, MediaCaptureScanner.FLAG_SECURE_WINDOW);
        MediaCaptureCache.put(mContext, "com.example.player", 3, 500L,
                MediaCaptureScanner.FLAG_AUDIO_CAPTURE_BLOCKED);
        MediaCaptureCache.put(mContext, "com.example.notes", 1, 100L, MediaCaptureScanner.FLAG_HIDES_RECENTS);

        assertTrue(MediaCaptureCache.blocksCapture(mContext, item("com.example.bank", 7, 1000L)));
        assertTrue(MediaCaptureCache.blocksCapture(mContext, item("com.example.player", 3, 500L)));
        assertFalse(MediaCaptureCache.blocksCapture(mContext, item("com.example.notes", 1, 100L)));
        assertFalse("never scanned", MediaCaptureCache.blocksCapture(mContext, item("com.example.new", 1, 1L)));
        assertFalse("scanned before an update", MediaCaptureCache.blocksCapture(mContext,
                item("com.example.bank", 8, 2000L)));
    }

    @NonNull
    private static ApplicationItem item(@NonNull String packageName, long versionCode, long lastUpdateTime) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        item.versionCode = versionCode;
        item.lastUpdateTime = lastUpdateTime;
        return item;
    }
}
