// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.content.res.ColorStateList;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.TooltipCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;

/**
 * What the reference flag next to a permission or app op does when tapped. It used to pin every
 * time, so a pinned reference could never be taken off again. Now it works as a toggle: pin when
 * there is no reference, unpin when there is one, and restore when the current state has drifted
 * from the reference and can be changed. A drifted reference that can't be restored unpins, so the
 * button is never stuck.
 */
public final class ReferenceAction {
    public static final int PIN = 0;
    public static final int UNPIN = 1;
    public static final int RESTORE = 2;

    @IntDef({PIN, UNPIN, RESTORE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {
    }

    private ReferenceAction() {
    }

    @Action
    public static int next(boolean hasReference, boolean drifted, boolean canRestore) {
        if (!hasReference) return PIN;
        if (drifted && canRestore) return RESTORE;
        return UNPIN;
    }

    @StringRes
    public static int label(@Action int action) {
        switch (action) {
            case UNPIN:
                return R.string.permission_reference_unpin;
            case RESTORE:
                return R.string.permission_reference_restore;
            case PIN:
            default:
                return R.string.permission_reference_pin;
        }
    }

    @StringRes
    public static int doneMessage(@Action int action) {
        switch (action) {
            case UNPIN:
                return R.string.permission_reference_unpinned;
            case RESTORE:
                return R.string.permission_reference_restored;
            case PIN:
            default:
                return R.string.permission_reference_pinned;
        }
    }

    @StringRes
    public static int failedMessage(@Action int action) {
        switch (action) {
            case UNPIN:
                return R.string.permission_reference_unpin_failed;
            case RESTORE:
                return R.string.permission_reference_restore_failed;
            case PIN:
            default:
                return R.string.permission_reference_pin_failed;
        }
    }

    @DrawableRes
    public static int icon(@Action int action, boolean hasReference) {
        if (action == RESTORE) return R.drawable.ic_restore;
        return hasReference ? R.drawable.ic_flag_filled : R.drawable.ic_flag;
    }

    /** Shows the action on the button: a filled, accent-colored flag while a reference is pinned. */
    public static void bind(@NonNull MaterialButton button, @Action int action, boolean hasReference) {
        String label = button.getResources().getString(label(action));
        button.setContentDescription(label);
        TooltipCompat.setTooltipText(button, label);
        button.setIconResource(icon(action, hasReference));
        int tint = MaterialColors.getColor(button, hasReference
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        button.setIconTint(ColorStateList.valueOf(tint));
    }
}
