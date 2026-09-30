// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.Nullable;

/**
 * Remembers the mode-of-operation status whose dialog is on screen. Status events are delivered
 * once, so a screen recreated while that dialog was open (rotation, theme change) would otherwise
 * wait forever for an answer from a dialog that went away with the old screen.
 * <p>
 * Main thread only. Not annotated, because lint's thread inference can't see that publishers only
 * reach {@link #onPublished(int)} after checking they are on the main thread.
 */
final class ModeStatusDialogTracker {
    @Nullable
    @Ops.Status
    private Integer mStatus;
    private boolean mShown;

    /**
     * Called on the main thread right before a status is handed to observers.
     */
    void onPublished(@Ops.Status int status) {
        mStatus = status;
        mShown = false;
    }

    /**
     * Called by the screen when it answered the last status with a dialog.
     */
    void onDialogShown() {
        mShown = true;
    }

    /**
     * The status whose dialog the screen showed and nobody answered yet, or {@code null}. A status
     * the screen hasn't seen isn't returned, because the event itself still reaches it.
     */
    @Nullable
    @Ops.Status
    Integer getLostDialogStatus() {
        return mShown ? mStatus : null;
    }
}
