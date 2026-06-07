// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.Paths;

public class ClipboardUtils {
    private static final int MAX_CLIPBOARD_SIZE_BYTES = 1024 * 1024;

    /**
     * Copies text to clipboard, using URI fallback if text is larger.
     */
    public static void copyToClipboard(@NonNull Context context, @Nullable CharSequence label, @NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        byte[] textBytes = getUtf8Bytes(text);
        ClipData clip;
        if (textBytes.length < MAX_CLIPBOARD_SIZE_BYTES) {
            // Small text: copy directly
            clip = ClipData.newPlainText(label, text);
        } else {
            // Large text: save to file and copy Uri reference
            try {
                File cacheFile = FileCache.getGlobalFileCache().getCachedFile(textBytes, "txt");
                // Use FileProvider to get content Uri for the file
                Uri contentUri = FmProvider.getContentUri(Paths.get(cacheFile));
                // Grant temporary read permission
                clip = ClipData.newUri(context.getContentResolver(), label, contentUri);
            } catch (IOException e) {
                e.printStackTrace();
                // Fallback: copy truncated text if writing file fails
                clip = ClipData.newPlainText(label != null ? label : "text", truncateForPlainTextFallback(text));
            }
        }
        clipboard.setPrimaryClip(clip);
    }

    @VisibleForTesting
    static byte[] getUtf8Bytes(@NonNull String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @VisibleForTesting
    static String truncateForPlainTextFallback(@NonNull String text) {
        return truncateUtf8(text, MAX_CLIPBOARD_SIZE_BYTES - 1);
    }

    @VisibleForTesting
    static String truncateUtf8(@NonNull String text, int maxBytes) {
        if (maxBytes <= 0) {
            return "";
        }
        if (getUtf8Bytes(text).length <= maxBytes) {
            return text;
        }
        StringBuilder builder = new StringBuilder(Math.min(text.length(), maxBytes));
        int bytes = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int nextOffset = offset + Character.charCount(codePoint);
            String next = text.substring(offset, nextOffset);
            int nextBytes = getUtf8Bytes(next).length;
            if (bytes + nextBytes > maxBytes) {
                break;
            }
            builder.append(next);
            bytes += nextBytes;
            offset = nextOffset;
        }
        return builder.toString();
    }


    @Nullable
    public static CharSequence readClipboard(@NonNull Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).coerceToText(context);
        }
        return null;
    }

    @Nullable
    public static String readHashValueFromClipboard(@NonNull Context context) {
        CharSequence clipData = readClipboard(context);
        if (clipData != null) {
            String data = clipData.toString().trim().toLowerCase(Locale.ROOT);
            if (data.matches("[0-9a-f: \n]+")) {
                return data.replaceAll("[: \n]+", "");
            }
        }
        return null;
    }
}
