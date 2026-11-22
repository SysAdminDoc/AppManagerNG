// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * Dhizuku status text for onboarding, the Privileges screen and the operating mode screen, built
 * from {@link DhizukuCapabilities} so it never claims more than what runs.
 */
public final class DhizukuStatusText {
    private DhizukuStatusText() {
    }

    /**
     * What to say once a request for Dhizuku's consent comes back. Dhizuku turns down an app that's
     * blocked in its Application Management without asking the user, so a refusal says where to look.
     */
    @StringRes
    public static int requestResultRes(@Nullable DhizukuState state) {
        return state == DhizukuState.NOT_PERMITTED ? R.string.dhizuku_request_not_allowed : headlineRes(state);
    }

    /**
     * @param state {@code null} while nothing checked yet
     */
    @StringRes
    public static int headlineRes(@Nullable DhizukuState state) {
        if (state == null) {
            return R.string.dhizuku_status_checking;
        }
        switch (state) {
            case UNSUPPORTED_ANDROID:
                return R.string.dhizuku_status_unsupported;
            case NOT_INSTALLED:
                return R.string.dhizuku_status_not_installed;
            case NO_OWNER:
                return R.string.dhizuku_status_no_owner;
            case NOT_CONNECTED:
                return R.string.dhizuku_status_not_connected;
            case NOT_PERMITTED:
                return R.string.dhizuku_status_not_permitted;
            case PERMITTED:
            default:
                return R.string.dhizuku_status_permitted;
        }
    }

    /**
     * The short status word on the operating mode screen.
     */
    @StringRes
    public static int capabilityStatusRes(@Nullable DhizukuState state) {
        if (state == null) {
            return R.string.dhizuku_status_checking;
        }
        switch (state) {
            case UNSUPPORTED_ANDROID:
                return R.string.mode_of_op_capability_status_needs_android_6;
            case NOT_INSTALLED:
                return R.string.mode_of_op_capability_status_missing;
            case NO_OWNER:
                return R.string.mode_of_op_capability_status_not_owner;
            case NOT_CONNECTED:
                return R.string.mode_of_op_capability_status_inactive;
            case NOT_PERMITTED:
                return R.string.mode_of_op_capability_status_permission_required;
            case PERMITTED:
            default:
                return R.string.mode_of_op_capability_status_authorized;
        }
    }

    /**
     * The headline, and once Dhizuku allowed this app, what runs through it and what still needs
     * root, ADB or Shizuku.
     */
    @NonNull
    public static String describe(@NonNull Context context, @Nullable DhizukuState state, int sdk) {
        StringBuilder text = new StringBuilder(context.getString(headlineRes(state)));
        List<DhizukuCapabilities.Operation> runnable = DhizukuCapabilities.runnable(state, sdk);
        if (!runnable.isEmpty()) {
            text.append(' ').append(context.getString(R.string.dhizuku_runs_through, joinLabels(context, runnable)))
                    .append(' ').append(context.getString(R.string.dhizuku_still_needs_other_mode));
        }
        return text.toString();
    }

    @NonNull
    public static String joinLabels(@NonNull Context context, @NonNull List<DhizukuCapabilities.Operation> operations) {
        List<String> labels = new ArrayList<>(operations.size());
        for (DhizukuCapabilities.Operation operation : operations) {
            labels.add(context.getString(operation.labelRes));
        }
        return TextUtils.join(", ", labels);
    }
}
