// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.Path;

public class SharableItems {
    public final List<Path> pathList;
    public final String mimeType;

    public SharableItems(@NonNull List<Path> pathList) {
        this(pathList, findBestMimeType(pathList));
    }

    public SharableItems(@NonNull List<Path> pathList, @Nullable String mimeType) {
        if (pathList.isEmpty()) {
            throw new IllegalArgumentException("pathList cannot be empty");
        }
        this.pathList = Collections.unmodifiableList(new ArrayList<>(pathList));
        this.mimeType = FmMimeUtils.normalizeMimeTypeOrDefault(mimeType);
    }

    public Intent toSharableIntent() {
        Intent intent;
        // ClipData is required for FLAG_GRANT_READ_URI_PERMISSION to reach the
        // chooser target on Android 18+ (auto-grant for SEND/SEND_MULTIPLE
        // EXTRA_STREAM URIs removed).
        if (pathList.size() == 1) {
            Uri uri = FmProvider.getContentUri(pathList.get(0));
            intent = new Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("", uri));
        } else {
            ArrayList<Uri> sharableUris = new ArrayList<>(pathList.size());
            for (Path path : pathList) {
                sharableUris.add(FmProvider.getContentUri(path));
            }
            ClipData clipData = new ClipData("", new String[]{mimeType}, new ClipData.Item(sharableUris.get(0)));
            for (int i = 1; i < sharableUris.size(); i++) {
                clipData.addItem(new ClipData.Item(sharableUris.get(i)));
            }
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType(mimeType)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, sharableUris)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(clipData);
        }
        return Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @NonNull
    public static String findBestMimeType(@NonNull List<Path> pathList) {
        String mimeType = null;
        boolean splitMime = false;
        for (Path path : pathList) {
            String thisMime = resolveMimeType(path);
            if (splitMime) {
                thisMime = FmMimeUtils.getMimeMajorType(thisMime);
            }
            if (mimeType == null) {
                mimeType = thisMime;
            } else if (!mimeType.equals(thisMime)) {
                if (splitMime) {
                    // The first part aren't consistent
                    return "*/*";
                }
                String splitMimeType = FmMimeUtils.getMimeMajorType(mimeType);
                String thisSplitMime = FmMimeUtils.getMimeMajorType(thisMime);
                if (!splitMimeType.equals(thisSplitMime)) {
                    // The first part aren't consistent
                    return "*/*";
                }
                splitMime = true;
                mimeType = splitMimeType;
            }
        }
        if (mimeType == null) {
            mimeType = ContentType2.OTHER.getMimeType();
        }
        return splitMime ? (mimeType + "/*") : mimeType;
    }

    @NonNull
    private static String resolveMimeType(@NonNull Path path) {
        String mimeType = FmMimeUtils.normalizeMimeType(path.getPathContentInfo().getMimeType());
        if (mimeType == null) {
            mimeType = FmMimeUtils.normalizeMimeType(path.getType());
        }
        return mimeType != null ? mimeType : ContentType2.OTHER.getMimeType();
    }
}
