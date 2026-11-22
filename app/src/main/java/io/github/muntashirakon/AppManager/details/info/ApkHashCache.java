// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.R;

/**
 * The SHA-256 of an APK, kept for as long as the file keeps its path, size and modification time, so App Info hashes
 * a big APK once rather than on every visit.
 */
public final class ApkHashCache {
    public interface Hasher {
        @NonNull
        String sha256(@NonNull File file) throws IOException;
    }

    /** Enough for the apps someone opens in one session. */
    private static final int MAX_ENTRIES = 64;
    private static final int SHORT_LENGTH = 8;

    @Nullable
    private static ApkHashCache sInstance;

    @NonNull
    public static synchronized ApkHashCache getInstance() {
        if (sInstance == null) {
            sInstance = new ApkHashCache(ApkHashCache::digest);
        }
        return sInstance;
    }

    @NonNull
    private final Hasher mHasher;
    private final Map<String, String> mHashes = new HashMap<>();

    public ApkHashCache(@NonNull Hasher hasher) {
        mHasher = hasher;
    }

    /**
     * The lowercase SHA-256 of the file, or {@code null} when it can't be read.
     */
    @WorkerThread
    @Nullable
    public String sha256(@NonNull File file) {
        if (!file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath() + '\n' + file.length() + '\n' + file.lastModified();
        synchronized (mHashes) {
            String hash = mHashes.get(key);
            if (hash != null) {
                return hash;
            }
        }
        String hash;
        try {
            hash = mHasher.sha256(file).toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            return null;
        }
        synchronized (mHashes) {
            if (mHashes.size() >= MAX_ENTRIES) {
                mHashes.clear();
            }
            mHashes.put(key, hash);
        }
        return hash;
    }

    /**
     * The first eight hex digits, which tell builds apart at a glance.
     */
    @NonNull
    public static String shortForm(@NonNull String hex) {
        String lower = hex.toLowerCase(Locale.ROOT);
        return lower.length() > SHORT_LENGTH ? lower.substring(0, SHORT_LENGTH) : lower;
    }

    /**
     * "0.8.0 (34) · SHA 7a4b8c2e", or the version alone while the hash isn't known.
     */
    @NonNull
    public static CharSequence versionWithShortSha(@NonNull Context context, @NonNull CharSequence version,
                                                   @Nullable String sha256) {
        if (sha256 == null || sha256.isEmpty()) {
            return version;
        }
        return context.getString(R.string.version_with_apk_sha, version, shortForm(sha256));
    }

    @NonNull
    private static String digest(@NonNull File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream is = new FileInputStream(file)) {
            int read;
            while ((read = is.read(buffer)) > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Interrupted");
                }
                digest.update(buffer, 0, read);
            }
        }
        return HexEncoding.encodeToString(digest.digest(), false);
    }
}
