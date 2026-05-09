// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.filters.FinderActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity;

/**
 * Trampoline for static launcher shortcuts.
 *
 * <p>Static shortcuts declared in {@code res/xml/shortcuts.xml} fire from the launcher process,
 * which means their target {@code Activity} component must be {@code exported="true"}. Several
 * AppManagerNG activities (e.g. {@link OneClickOpsActivity}) accept intent extras that trigger
 * destructive operations without confirmation when invoked through the home-screen widget or
 * pinned-shortcut PendingIntent path. Exporting those activities directly so the launcher can
 * dispatch shortcuts would also let any installed app fire the same destructive extras after
 * the user is process-authenticated. To preserve both the shortcut UX and the existing
 * widget/pinned-shortcut consent model, this trampoline:
 *
 * <ul>
 *     <li>Is the only exported component the launcher resolves to for AM-NG shortcuts.</li>
 *     <li>Hard-whitelists shortcut intent actions — never reads or forwards arbitrary extras.</li>
 *     <li>Constructs a fresh {@link Intent} for the unexported target, so untrusted callers
 *         cannot smuggle in destructive extras like {@code OneClickOpsActivity#EXTRA_OP}.</li>
 *     <li>Finishes immediately with no UI of its own.</li>
 * </ul>
 *
 * <p>If the action is unrecognised, the trampoline silently finishes without dispatching, so an
 * external app firing this activity with a bogus action is a no-op.
 */
public class ShortcutDispatchActivity extends Activity {
    private static final String TAG = "ShortcutDispatch";

    public static final String ACTION_OPEN_ONE_CLICK_OPS =
            "io.github.muntashirakon.AppManager.shortcut.action.OPEN_ONE_CLICK_OPS";
    public static final String ACTION_OPEN_FINDER =
            "io.github.muntashirakon.AppManager.shortcut.action.OPEN_FINDER";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent in = getIntent();
            String action = in == null ? null : in.getAction();
            Intent out;
            if (ACTION_OPEN_ONE_CLICK_OPS.equals(action)) {
                out = new Intent(this, OneClickOpsActivity.class);
            } else if (ACTION_OPEN_FINDER.equals(action)) {
                out = new Intent(this, FinderActivity.class);
            } else {
                // Unknown action — refuse to dispatch. Anything fired at this trampoline must
                // declare one of the explicit ACTION_* constants above. Treating unknown actions
                // as a no-op (instead of e.g. defaulting to MainActivity) keeps the surface tight
                // against unintentional or malicious callers. Log the *truncated* action so a
                // hostile caller cannot pollute the device log with arbitrary-length entries.
                String safeAction = action == null ? "<null>"
                        : (action.length() > 80 ? action.substring(0, 80) + "…" : action);
                Log.w(TAG, "Refusing to dispatch unknown shortcut action: " + safeAction);
                return;
            }
            // FLAG_ACTIVITY_NEW_TASK so we can launch a regular task without sharing affinity
            // with the (transparent / no-task) trampoline. CLEAR_TOP keeps a single instance of
            // the target if the user re-fires the shortcut while it's already foregrounded.
            out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(out);
            } catch (ActivityNotFoundException e) {
                // Target component disabled (e.g. user disabled it via PackageManager) or
                // removed by an upgrade migration that didn't update shortcuts.xml. Don't
                // crash the trampoline; just no-op and let the user retry from MainActivity.
                Log.w(TAG, "Shortcut target unavailable; trampoline finishing.", e);
            }
        } finally {
            // Single exit point. The Theme.NoDisplay activity contract requires finish() to be
            // called before returning from onCreate; the try/finally guarantees that even if
            // something unexpected throws above we don't leave a phantom no-display task on
            // the recents stack.
            finish();
        }
    }
}
