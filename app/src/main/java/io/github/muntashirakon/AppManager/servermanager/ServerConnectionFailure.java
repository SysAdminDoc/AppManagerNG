// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * A failure to reach the privileged server that the user can do something about, as opposed to a
 * plain transport error.
 */
public class ServerConnectionFailure extends IOException {
    public enum Reason {
        /**
         * The launch through ADB or root failed, or the server never started listening.
         */
        SERVER_START,
        /**
         * Something took the connection and never answered the handshake.
         */
        UNRESPONSIVE,
        /**
         * The server answered without proving it holds this app's token.
         */
        NOT_ACKNOWLEDGED,
    }

    @NonNull
    private final Reason mReason;

    public ServerConnectionFailure(@NonNull Reason reason, @Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
        mReason = reason;
    }

    @NonNull
    public Reason getReason() {
        return mReason;
    }

    /**
     * The first failure of this kind in the cause chain, or {@code null}. Callers wrap it, so it
     * rarely arrives on top.
     */
    @Nullable
    public static ServerConnectionFailure find(@Nullable Throwable failure) {
        // Bounded, in case a cause chain loops
        for (int depth = 0; failure != null && depth < 16; ++depth) {
            if (failure instanceof ServerConnectionFailure) {
                return (ServerConnectionFailure) failure;
            }
            failure = failure.getCause();
        }
        return null;
    }
}
