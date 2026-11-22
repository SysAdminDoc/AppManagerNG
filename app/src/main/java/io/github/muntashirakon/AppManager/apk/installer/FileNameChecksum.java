// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Some APK files carry the start of their SHA-256 at the end of their name, like
 * {@code App_1.0_0f3a9c7b.apk} (#44). Comparing it with the file tells whether the two agree, which
 * catches a broken or swapped download. It never says the file is genuine: anyone can rename a file.
 */
public final class FileNameChecksum {
    public enum Verdict {
        MATCHES,
        DIFFERS
    }

    private static final String[] EXTENSIONS = {".apk", ".apks", ".apkm", ".xapk"};
    private static final int MIN_HEX = 8;
    private static final int MAX_HEX = 64;

    private FileNameChecksum() {
    }

    /**
     * The checksum in a name ending in {@code -<hex>} or {@code _<hex>} before an APK extension, in
     * lower case, with 8 to 64 hex digits. A run of plain digits is taken for a version or a date, not
     * a checksum.
     *
     * @return {@code null} when the name carries none
     */
    @Nullable
    public static String parse(@Nullable String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        String stem = null;
        for (String extension : EXTENSIONS) {
            if (lower.endsWith(extension)) {
                stem = lower.substring(0, lower.length() - extension.length());
                break;
            }
        }
        if (stem == null) {
            return null;
        }
        int separator = Math.max(stem.lastIndexOf('-'), stem.lastIndexOf('_'));
        if (separator <= 0) {
            return null;
        }
        String hex = stem.substring(separator + 1);
        if (hex.length() < MIN_HEX || hex.length() > MAX_HEX) {
            return null;
        }
        boolean letter = false;
        for (int i = 0; i < hex.length(); ++i) {
            char c = hex.charAt(i);
            if (c >= 'a' && c <= 'f') {
                letter = true;
            } else if (c < '0' || c > '9') {
                return null;
            }
        }
        return letter ? hex : null;
    }

    /**
     * True when the file's full SHA-256 starts with the checksum from the name, in any case.
     */
    public static boolean matches(@NonNull String claimed, @NonNull String sha256Hex) {
        return claimed.length() <= sha256Hex.length()
                && sha256Hex.regionMatches(true, 0, claimed, 0, claimed.length());
    }

    /**
     * Hashes the whole stream and compares it with the checksum in the name.
     *
     * @return {@code null} when the name carries no checksum, in which case nothing is read
     */
    @WorkerThread
    @Nullable
    public static Verdict check(@Nullable String fileName, @NonNull InputStream in) throws IOException {
        String claimed = parse(fileName);
        if (claimed == null) {
            return null;
        }
        return matches(claimed, sha256Hex(in)) ? Verdict.MATCHES : Verdict.DIFFERS;
    }

    /**
     * The SHA-256 of everything left in the stream, in lower-case hex. Unlike {@code DigestUtils},
     * a read error is thrown rather than turned into an empty digest.
     */
    @WorkerThread
    @NonNull
    public static String sha256Hex(@NonNull InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
