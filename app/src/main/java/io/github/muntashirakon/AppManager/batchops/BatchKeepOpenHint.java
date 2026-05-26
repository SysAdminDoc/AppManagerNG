// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.app.Activity;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

import java.util.WeakHashMap;

import io.github.muntashirakon.AppManager.R;

/**
 * Hail / Neo-Backup-style "keep the app open" hint surfaced as an indefinite
 * Material Snackbar while long-running foreground operations run.
 *
 * <p>The existing {@link BatchOpsService} already raises a foreground
 * notification; the snackbar adds an in-app surface so a user who is still
 * looking at the activity sees the guidance even if they have notifications
 * suppressed for AppManagerNG. The hint can be dismissed manually; activities
 * call {@link #dismiss(Activity)} when the operation completes.</p>
 *
 * <p>The helper does nothing on activities that lack a CoordinatorLayout-like
 * content view (Snackbar.make handles this defensively — it walks up the
 * window decor view until it finds a suitable parent). Wire from any
 * BatchOps launch site by calling
 * {@code BatchKeepOpenHint.show(activity, R.string.batch_keep_open_default)}.</p>
 */
public final class BatchKeepOpenHint {
    /**
     * Tracks the most-recent live Snackbar per activity so
     * {@link #dismiss(Activity)} can clear it. WeakHashMap keys avoid leaking
     * destroyed activities; the Snackbar holds a strong ref to its own view
     * but that's torn down by Snackbar's lifecycle.
     */
    private static final WeakHashMap<Activity, Snackbar> sActive = new WeakHashMap<>();

    private BatchKeepOpenHint() {
    }

    /** Show the hint with the default copy. */
    @MainThread
    @Nullable
    public static Snackbar show(@NonNull Activity activity) {
        return show(activity, R.string.batch_keep_open_default);
    }

    /**
     * Show the hint with a caller-supplied body. Returns the live Snackbar so
     * the caller can extend it with an action; safe to ignore the return
     * value. If a previous hint is still showing on the same activity it is
     * dismissed first so we never stack two.
     */
    @MainThread
    @Nullable
    public static Snackbar show(@NonNull Activity activity, @StringRes int bodyRes) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return null;
        dismiss(activity);
        try {
            Snackbar snackbar = Snackbar.make(root, bodyRes, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.close, v -> snackbar.dismiss());
            sActive.put(activity, snackbar);
            snackbar.show();
            return snackbar;
        } catch (Throwable t) {
            // Defensive: Snackbar.make can throw if the view tree has been torn down.
            return null;
        }
    }

    /** Dismiss any hint previously shown on {@code activity}. No-op if absent. */
    @MainThread
    public static void dismiss(@NonNull Activity activity) {
        Snackbar prior = sActive.remove(activity);
        if (prior != null) {
            try {
                prior.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /** True when {@code activity} currently has a hint visible. */
    @MainThread
    public static boolean isShowing(@NonNull Activity activity) {
        Snackbar snackbar = sActive.get(activity);
        return snackbar != null && snackbar.isShown();
    }
}
