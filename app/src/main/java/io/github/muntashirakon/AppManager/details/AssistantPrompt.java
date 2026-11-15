// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.CountDownLatch;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * The dialog that asks the user to open the assistant while it points at an activity, and the launch that waits on
 * it. The launch gives the user's assistant back when its wait ends, so the wait has to end whenever nobody can
 * answer the dialog. A screen's dialog goes with the screen without calling its dismiss listener, so the screen
 * calls {@link #end()} when it goes. Main thread only.
 */
final class AssistantPrompt {
    private static final String TAG = AssistantPrompt.class.getSimpleName();

    @Nullable
    private CountDownLatch mWait;
    @Nullable
    private AlertDialog mDialog;

    /**
     * Shows the dialog for the launch waiting on {@code wait}, after ending the one that's showing. {@code onClosed}
     * runs when the user closes this dialog, and not when it's ended or replaced.
     *
     * @return {@code false} when the dialog can't be shown. That wait is over too.
     */
    @MainThread
    boolean show(@NonNull Context context, @NonNull CountDownLatch wait, @StringRes int message,
                 @Nullable Runnable onClosed) {
        end();
        mWait = wait;
        try {
            mDialog = new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.launch_activity_dialog_title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setOnDismissListener(dialog -> {
                        wait.countDown();
                        // The listener runs after the dismissal, so a newer dialog may be showing by then
                        if (mWait == wait) {
                            mWait = null;
                            mDialog = null;
                            if (onClosed != null) {
                                onClosed.run();
                            }
                        }
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not ask for the assistant.", e);
            mWait = null;
            mDialog = null;
            wait.countDown();
            return false;
        }
    }

    /** Ends the wait that's showing, if there's one, and takes its dialog away without running its close action. */
    @MainThread
    void end() {
        CountDownLatch wait = mWait;
        AlertDialog dialog = mDialog;
        mWait = null;
        mDialog = null;
        if (dialog != null) {
            dialog.dismiss();
        }
        if (wait != null) {
            wait.countDown();
        }
    }
}
