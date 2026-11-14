// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import io.github.muntashirakon.AppManager.self.filecache.FileCache;

/**
 * Robolectric has no keystore to sign with, so this signs a copy by handing back a copy of an APK
 * that another key signed. With no APK set, signing fails the way it does without a key.
 */
@Implements(Signer.class)
public class ShadowFixtureSigner {
    @Nullable
    private static File sSignedApk;

    /** The APK every signed copy is, until it's set back to {@code null}. */
    public static void signWith(@Nullable File signedApk) {
        sSignedApk = signedApk;
    }

    @Implementation
    @NonNull
    protected static Signer.SignedApk signCopy(@NonNull File in, @NonNull FileCache cache) throws IOException {
        File signedApk = sSignedApk;
        if (signedApk == null) {
            throw new IOException("Failed to sign " + in);
        }
        File out = cache.createCachedFile("apk");
        Files.copy(signedApk.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new Signer.SignedApk(out, null);
    }
}
