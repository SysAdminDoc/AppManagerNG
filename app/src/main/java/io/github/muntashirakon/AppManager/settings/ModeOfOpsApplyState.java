// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class ModeOfOpsApplyState {
    @Nullable
    private String mPreviousMode;
    @Nullable
    private String mPendingMode;

    boolean begin(@NonNull @Ops.Mode String previousMode, @NonNull @Ops.Mode String pendingMode) {
        if (isApplying()) {
            return false;
        }
        mPreviousMode = previousMode;
        mPendingMode = pendingMode;
        return true;
    }

    boolean isApplying() {
        return mPendingMode != null;
    }

    @Nullable
    @Ops.Mode
    String getPendingMode() {
        return mPendingMode;
    }

    @Nullable
    @Ops.Mode
    String finishSuccess() {
        if (!isApplying()) {
            return null;
        }
        String pendingMode = mPendingMode;
        clear();
        return pendingMode;
    }

    @Nullable
    @Ops.Mode
    String finishFailure() {
        if (!isApplying()) {
            return null;
        }
        String previousMode = mPreviousMode;
        clear();
        return previousMode;
    }

    @Nullable
    @Ops.Mode
    String dismiss() {
        if (!isApplying()) {
            return null;
        }
        String previousMode = mPreviousMode;
        clear();
        return previousMode;
    }

    private void clear() {
        mPreviousMode = null;
        mPendingMode = null;
    }
}
