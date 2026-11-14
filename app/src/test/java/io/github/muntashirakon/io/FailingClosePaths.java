// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Local paths whose streams fail to close after every byte was read, the way closing a file on a
 * FUSE-backed folder can.
 */
public final class FailingClosePaths {
    private FailingClosePaths() {
    }

    @NonNull
    public static Path of(@NonNull File file) {
        return new PathImpl(ContextUtils.getContext(), file.getAbsolutePath()) {
            @NonNull
            @Override
            public InputStream openInputStream() throws IOException {
                return new FilterInputStream(super.openInputStream()) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        throw new IOException("close error");
                    }
                };
            }
        };
    }
}
