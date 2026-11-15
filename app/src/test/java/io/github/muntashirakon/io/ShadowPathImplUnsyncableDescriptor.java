// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Handler;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hands out, for every file, a descriptor that takes writes but can't be synced and keeps nothing, the way a file
 * only the root service can reach does: there the descriptor is the write end of a pipe, and fsync on a pipe fails.
 * Robolectric's own pipes are plain files, so a null device stands in for one.
 */
@Implements(value = PathImpl.class, isInAndroidSdk = false)
public class ShadowPathImplUnsyncableDescriptor {
    @Implementation
    @NonNull
    public ParcelFileDescriptor openFileDescriptor(@NonNull String mode, @NonNull Handler callbackHandler)
            throws FileNotFoundException {
        File sink = new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");
        return ParcelFileDescriptor.open(sink, ParcelFileDescriptor.MODE_WRITE_ONLY);
    }
}
