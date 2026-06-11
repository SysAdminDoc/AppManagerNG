// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

final class AppVerifierShareFormatter {
    private AppVerifierShareFormatter() {
    }

    @NonNull
    static String format(@NonNull String packageName, @NonNull String sha256Fingerprint) {
        String normalizedPackageName = Objects.requireNonNull(packageName).trim();
        String normalizedFingerprint = Objects.requireNonNull(sha256Fingerprint)
                .trim()
                .toUpperCase(Locale.ROOT);
        if (normalizedPackageName.isEmpty()) {
            throw new IllegalArgumentException("packageName must not be empty");
        }
        if (normalizedFingerprint.isEmpty()) {
            throw new IllegalArgumentException("sha256Fingerprint must not be empty");
        }
        return normalizedPackageName + '\n' + normalizedFingerprint;
    }
}
