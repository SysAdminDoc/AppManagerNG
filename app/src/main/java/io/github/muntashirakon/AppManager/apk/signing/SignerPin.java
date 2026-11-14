// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The signer set a user pinned for one package: installs and updates of that package are refused
 * unless the APK is signed by exactly this set, or by one key whose verified rotation lineage
 * includes the single pinned key.
 *
 * <p>Digests are kept as 64 lowercase hex characters, sorted, so two pins of the same set are equal.
 */
public final class SignerPin {
    private static final int SHA256_HEX_LENGTH = 64;

    @NonNull
    public final List<String> certSha256;
    public final long pinnedAt;

    /**
     * @throws IllegalArgumentException when the set is empty or a digest isn't a SHA-256
     */
    public SignerPin(@NonNull Collection<String> certSha256, long pinnedAt) {
        TreeSet<String> sorted = new TreeSet<>();
        for (String digest : certSha256) {
            String normalized = normalize(digest);
            if (normalized == null) {
                throw new IllegalArgumentException("Not a SHA-256 digest: " + digest);
            }
            sorted.add(normalized);
        }
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("A signer pin needs at least one certificate.");
        }
        this.certSha256 = Collections.unmodifiableList(new ArrayList<>(sorted));
        this.pinnedAt = pinnedAt;
    }

    /** True when {@code digests} is the same signer set as this pin. */
    public boolean isSameSet(@NonNull Collection<String> digests) {
        TreeSet<String> other = new TreeSet<>();
        for (String digest : digests) {
            String normalized = normalize(digest);
            if (normalized == null) {
                return false;
            }
            other.add(normalized);
        }
        return new ArrayList<>(other).equals(certSha256);
    }

    /**
     * Accepts upper or lower case, with or without colons or spaces between the bytes.
     *
     * @return 64 lowercase hex characters, or {@code null} when {@code digest} isn't a SHA-256
     */
    @Nullable
    public static String normalize(@Nullable String digest) {
        if (digest == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(SHA256_HEX_LENGTH);
        for (int i = 0; i < digest.length(); ++i) {
            char c = digest.charAt(i);
            if (c == ':' || c == ' ') {
                continue;
            }
            char lower = Character.toLowerCase(c);
            if ((lower < '0' || lower > '9') && (lower < 'a' || lower > 'f')) {
                return null;
            }
            out.append(lower);
        }
        return out.length() == SHA256_HEX_LENGTH ? out.toString() : null;
    }

    /** {@code AB:CD:...}, the way keytool and the Signatures tab print a fingerprint. */
    @NonNull
    public static String format(@NonNull String digest) {
        String upper = digest.toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(upper.length() + upper.length() / 2);
        for (int i = 0; i < upper.length(); i += 2) {
            if (out.length() > 0) {
                out.append(':');
            }
            out.append(upper, i, Math.min(i + 2, upper.length()));
        }
        return out.toString();
    }

    /** Every digest formatted, one per line. */
    @NonNull
    public static String formatAll(@NonNull Collection<String> digests) {
        StringBuilder out = new StringBuilder();
        for (String digest : digests) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(format(digest));
        }
        return out.toString();
    }
}
