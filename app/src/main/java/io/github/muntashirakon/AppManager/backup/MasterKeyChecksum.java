// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

final class MasterKeyChecksum {
    static final int RAW_BYTES_VERSION = 8;

    private MasterKeyChecksum() {
    }

    @NonNull
    @WorkerThread
    static String calculate(@DigestUtils.Algorithm @NonNull String algorithm, int metadataVersion,
                            @NonNull Path masterKey) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = masterKey.openInputStream()) {
            bytes = IoUtils.readFully(inputStream, -1, true);
        }
        try {
            return calculate(algorithm, metadataVersion, bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @NonNull
    @VisibleForTesting
    static String calculate(@DigestUtils.Algorithm @NonNull String algorithm, int metadataVersion,
                            @NonNull byte[] masterKey) {
        if (metadataVersion >= RAW_BYTES_VERSION) {
            return DigestUtils.getHexDigest(algorithm, masterKey);
        }
        return calculateLegacy(algorithm, masterKey, Charset.defaultCharset());
    }

    @NonNull
    @VisibleForTesting
    static String calculateLegacy(@DigestUtils.Algorithm @NonNull String algorithm,
                                  @NonNull byte[] masterKey, @NonNull Charset charset) {
        // Versions 1-7 converted the binary key to a String and back with the
        // platform default charset. Reproduce that lossy transform only when
        // verifying an existing backup; version 8+ always hashes raw bytes.
        return DigestUtils.getHexDigest(algorithm,
                new String(masterKey, charset).getBytes(charset));
    }
}
