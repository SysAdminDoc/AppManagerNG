// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks downloaded APKs before they reach the installer. The signers are read from the files
 * themselves, never taken from the host's word: they must match what the host listed, every split
 * must share the base's signers, and an app that is already installed must be signed by the same
 * key (or a key whose rotation lineage includes it).
 */
public final class ApkSignatureGate {
    public static final class SignerDigests {
        @NonNull
        public final List<String> current;
        @NonNull
        public final List<String> lineage;

        public SignerDigests(@NonNull List<String> current, @NonNull List<String> lineage) {
            this.current = Collections.unmodifiableList(current);
            this.lineage = Collections.unmodifiableList(lineage);
        }
    }

    public interface DigestReader {
        /**
         * Verifies the APK's signature and returns its signer digests, or {@code null} when it
         * does not verify.
         */
        @WorkerThread
        @Nullable
        SignerDigests read(@NonNull File apk);
    }

    public static final class MismatchException extends IOException {
        MismatchException(@NonNull String message) {
            super(message);
        }
    }

    private ApkSignatureGate() {
    }

    /**
     * @param files     downloaded files, base first
     * @param claimed   the entry the host listed
     * @param installed the app installed here, or {@code null}
     */
    @WorkerThread
    public static void check(@NonNull List<File> files, @NonNull SyncApp claimed, @Nullable SyncApp installed,
                             @NonNull DigestReader reader) throws MismatchException {
        if (files.isEmpty()) {
            throw new MismatchException("Nothing was downloaded.");
        }
        SignerDigests base = reader.read(files.get(0));
        if (base == null || base.current.isEmpty()) {
            throw new MismatchException("The downloaded APK is not validly signed.");
        }
        Set<String> baseSigners = lower(base.current);
        if (!baseSigners.equals(lower(claimed.signers))) {
            throw new MismatchException("The downloaded APK is signed by a different key than the other device reported.");
        }
        for (int i = 1; i < files.size(); ++i) {
            SignerDigests split = reader.read(files.get(i));
            if (split == null || !lower(split.current).equals(baseSigners)) {
                throw new MismatchException("A split APK is not signed by the same key as the base APK.");
            }
        }
        if (installed != null && !SyncComparator.signersMatch(base.current, base.lineage, installed.signers)) {
            throw new MismatchException("The downloaded APK is signed by a different key than the installed app.");
        }
    }

    @NonNull
    private static Set<String> lower(@NonNull List<String> digests) {
        Set<String> out = new HashSet<>(digests.size());
        for (String digest : digests) {
            out.add(digest.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
