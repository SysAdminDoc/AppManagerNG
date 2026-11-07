// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * The peer broke the Local sync protocol: a bad frame, a failed authentication tag, a commitment
 * that does not open, or a message the current state does not allow. The session that raised it
 * is dead and must not be used again.
 */
public class SyncProtocolException extends IOException {
    public SyncProtocolException(@NonNull String message) {
        super(message);
    }

    public SyncProtocolException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
