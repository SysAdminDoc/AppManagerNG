// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.Nullable;

/**
 * Remembers the mode-of-operation status whose dialog is on screen. Status events are delivered
 * once, so a screen recreated while that dialog was open (rotation, theme change) would otherwise
 * wait forever for an answer from a dialog that went away with the old screen.
 * <p>
 * Thread-safe: statuses are published on the main thread, but the requests that answer a dialog
 * (connect, pair, a granted permission) can come from anywhere.
 */
final class ModeStatusDialogTracker {
    @Nullable
    @Ops.Status
    private Integer mStatus;
    private boolean mShown;

    /**
     * Called on the main thread right before a status is handed to observers.
     */
    synchronized void onPublished(@Ops.Status int status) {
        mStatus = status;
        mShown = false;
    }

    /**
     * Called by the screen when it answered the last status with a dialog.
     */
    synchronized void onDialogShown() {
        mShown = true;
    }

    /**
     * Called when the user answered the dialog, so a recreated screen doesn't ask again while the
     * answer is being worked on.
     */
    synchronized void clear() {
        mStatus = null;
        mShown = false;
    }

    /**
     * The status whose dialog the screen showed and nobody answered yet, or {@code null}. A status
     * the screen hasn't seen isn't returned, because the event itself still reaches it.
     */
    @Nullable
    @Ops.Status
    synchronized Integer getLostDialogStatus() {
        return mShown ? mStatus : null;
    }
}
