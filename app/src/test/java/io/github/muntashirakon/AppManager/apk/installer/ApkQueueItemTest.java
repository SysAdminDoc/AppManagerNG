// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.apk.ApkSource;

@RunWith(RobolectricTestRunner.class)
public class ApkQueueItemTest {
    private static final String REVIEWED = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Test
    public void theReviewedPackageSurvivesTheTripToTheService() {
        ApkQueueItem item = reviewedItem();
        Parcel parcel = Parcel.obtain();
        try {
            item.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ApkQueueItem restored = ApkQueueItem.CREATOR.createFromParcel(parcel);

            assertEquals(REVIEWED, restored.getReviewedSha256());
            assertEquals(4096, restored.getReviewedSize());
            assertEquals("Example", restored.getAppLabel());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void aHistoryReplayKeepsTheReviewedPackage() throws JSONException {
        ApkQueueItem restored = ApkQueueItem.DESERIALIZER.deserialize(reviewedItem().serializeToJson());

        assertEquals(REVIEWED, restored.getReviewedSha256());
        assertEquals(4096, restored.getReviewedSize());
    }

    @Test
    public void historyFromBeforeTheCheckReplaysWithNothingReviewed() throws JSONException {
        JSONObject older = reviewedItem().serializeToJson();
        older.remove("reviewed_sha256");
        older.remove("reviewed_size");

        ApkQueueItem restored = ApkQueueItem.DESERIALIZER.deserialize(older);

        assertNull(restored.getReviewedSha256());
        assertEquals(-1, restored.getReviewedSize());
    }

    @Test
    public void aSizeWithoutADigestIsDropped() {
        ApkQueueItem item = reviewedItem();
        item.setReviewedPayload(null, 4096);

        assertNull(item.getReviewedSha256());
        assertEquals(-1, item.getReviewedSize());
    }

    @NonNull
    private static ApkQueueItem reviewedItem() {
        ApkQueueItem item = ApkQueueItem.fromApkSource(ApkSource.getApkSource(
                Uri.parse("file:///sdcard/Download/example.apk"), "application/vnd.android.package-archive"));
        item.setAppLabel("Example");
        item.setSelectedSplits(new ArrayList<>());
        item.setReviewedPayload(REVIEWED, 4096);
        return item;
    }
}
