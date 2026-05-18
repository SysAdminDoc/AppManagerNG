// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-package signing-certificate snapshot. Immutable value type used by
 * {@link SigningCertSnapshotStore} to detect cert rotations across app
 * updates.
 *
 * <p>{@code certShas256} holds the SHA-256 hex digests (colon-separated
 * uppercase, matching the release-fingerprint convention) of every signer
 * Android reports for the package. Storing the set rather than a single value
 * means signature scheme V3 rotation (multi-signer history) is preserved —
 * a true cert change replaces the set entirely; a rotation extends it.
 */
public final class SigningCertSnapshot {
    public final long versionCode;
    @NonNull
    public final Set<String> certShas256;

    public SigningCertSnapshot(long versionCode, @NonNull Set<String> certShas256) {
        this.versionCode = versionCode;
        // Sorted + immutable for deterministic equality and serialisation.
        this.certShas256 = Collections.unmodifiableSet(new TreeSet<>(certShas256));
    }

    @VisibleForTesting
    public static SigningCertSnapshot of(long versionCode, @NonNull String... shas) {
        Set<String> set = new LinkedHashSet<>();
        for (String s : shas) if (s != null && !s.isEmpty()) set.add(s);
        return new SigningCertSnapshot(versionCode, set);
    }
}
