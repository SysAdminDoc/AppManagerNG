// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.R;

/**
 * Shows toasts from this app the way other apps do, so the user can see what the toast history catches: one toast,
 * one sent from a background thread through the main looper, and a quick run of three.
 */
public final class ToastTester {
    /** Three toasts, 150 ms apart, all within 300 ms. */
    public static final int BURST_COUNT = 3;
    public static final long BURST_GAP_MILLIS = 150L;

    private ToastTester() {
    }

    @MainThread
    public static void show(@NonNull Context context, @NonNull CharSequence text, boolean longDuration) {
        Toast.makeText(context.getApplicationContext(), text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * Like an app that works out its message on a worker thread: the background task hands the toast to the main
     * looper, which is the only thread a toast can be shown from.
     */
    @AnyThread
    public static void showFromBackground(@NonNull Context context, @NonNull CharSequence text, boolean longDuration,
                                          @NonNull Executor background) {
        Context app = context.getApplicationContext();
        String message = text.toString();
        background.execute(() -> new Handler(Looper.getMainLooper()).post(() -> show(app, message, longDuration)));
    }

    /** Each toast is numbered, so the history shows whether it kept all three or only some. */
    @AnyThread
    public static void showBurst(@NonNull Context context, @NonNull CharSequence text, boolean longDuration,
                                 @NonNull Handler main) {
        Context app = context.getApplicationContext();
        for (int i = 0; i < BURST_COUNT; ++i) {
            String message = app.getString(R.string.toast_history_test_burst_item, text, i + 1, BURST_COUNT);
            main.postDelayed(() -> show(app, message, longDuration), i * BURST_GAP_MILLIS);
        }
    }
}
