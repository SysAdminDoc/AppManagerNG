// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.PopupMenu;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * The long-press menu on a permission or app op row in App Details: the row's own items, then Always allow, Always
 * deny, Ignore changes and, for a rule that exists, Stop watching.
 */
public final class WatchRuleMenu {
    private static final String TAG = "WatchRuleMenu";

    @VisibleForTesting
    static final int GROUP_EXTRAS = 1;
    @VisibleForTesting
    static final int GROUP_STATES = 2;
    @VisibleForTesting
    static final int ID_ALLOW = 101;
    @VisibleForTesting
    static final int ID_DENY = 102;
    @VisibleForTesting
    static final int ID_IGNORE = 103;
    @VisibleForTesting
    static final int ID_STOP = 104;

    /** An item the row had before the watcher, such as the app op mode picker. */
    public static final class Extra {
        @StringRes
        final int title;
        @NonNull
        final Runnable action;

        public Extra(@StringRes int title, @NonNull Runnable action) {
            this.title = title;
            this.action = action;
        }
    }

    public interface OnRuleChanged {
        /** @param rule the rule just saved, or {@code null} once the row is no longer watched */
        @MainThread
        void onRuleChanged(@Nullable WatchRule rule);
    }

    private WatchRuleMenu() {
    }

    /**
     * @param watchable whether the row can be watched at all. A read-only permission can't, and neither can a row
     *                  with no installed app behind it.
     */
    @MainThread
    public static void show(@NonNull View anchor, @NonNull String packageName, int userId, @WatchRule.Type int type,
                            @NonNull String name, int op, @NonNull List<Extra> extras, boolean watchable,
                            @NonNull OnRuleChanged listener) {
        if (!watchable) {
            showMenu(anchor, packageName, userId, type, name, op, extras, false, null, listener);
            return;
        }
        Context appContext = anchor.getContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            WatchRule existing = find(appContext, packageName, userId, type, name);
            ThreadUtils.postOnMainThread(() -> {
                if (anchor.isAttachedToWindow()) {
                    showMenu(anchor, packageName, userId, type, name, op, extras, true, existing, listener);
                }
            });
        });
    }

    @MainThread
    private static void showMenu(@NonNull View anchor, @NonNull String packageName, int userId,
                                 @WatchRule.Type int type, @NonNull String name, int op, @NonNull List<Extra> extras,
                                 boolean watchable, @Nullable WatchRule existing, @NonNull OnRuleChanged listener) {
        if (extras.isEmpty() && !watchable) {
            return;
        }
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        populate(popup.getMenu(), extras, watchable, existing);
        Context appContext = anchor.getContext().getApplicationContext();
        popup.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == GROUP_EXTRAS) {
                extras.get(item.getOrder()).action.run();
                return true;
            }
            int itemId = item.getItemId();
            if (itemId == ID_STOP) {
                if (existing != null) {
                    remove(appContext, existing, listener);
                }
                return true;
            }
            int state = stateFor(itemId);
            if (state < 0) {
                return false;
            }
            if (existing == null || existing.state != state) {
                save(appContext, new WatchRule(packageName, userId, type, name, op, state, System.currentTimeMillis()),
                        listener);
            }
            return true;
        });
        popup.show();
    }

    @VisibleForTesting
    static void populate(@NonNull Menu menu, @NonNull List<Extra> extras, boolean watchable,
                         @Nullable WatchRule existing) {
        for (int i = 0; i < extras.size(); ++i) {
            // The order doubles as the index, so a click finds its action.
            menu.add(GROUP_EXTRAS, Menu.NONE, i, extras.get(i).title);
        }
        if (!watchable) {
            return;
        }
        int order = extras.size();
        addState(menu, ID_ALLOW, order++, R.string.permission_watcher_always_allow, existing, WatchRule.STATE_ALLOW);
        addState(menu, ID_DENY, order++, R.string.permission_watcher_always_deny, existing, WatchRule.STATE_DENY);
        addState(menu, ID_IGNORE, order++, R.string.permission_watcher_ignore, existing, WatchRule.STATE_IGNORE);
        menu.setGroupCheckable(GROUP_STATES, true, true);
        if (existing != null) {
            menu.add(Menu.NONE, ID_STOP, order, R.string.permission_watcher_stop);
        }
    }

    private static void addState(@NonNull Menu menu, int id, int order, @StringRes int title,
                                 @Nullable WatchRule existing, @WatchRule.State int state) {
        MenuItem item = menu.add(GROUP_STATES, id, order, title);
        item.setChecked(existing != null && existing.state == state);
    }

    @VisibleForTesting
    static int stateFor(int itemId) {
        switch (itemId) {
            case ID_ALLOW:
                return WatchRule.STATE_ALLOW;
            case ID_DENY:
                return WatchRule.STATE_DENY;
            case ID_IGNORE:
                return WatchRule.STATE_IGNORE;
            default:
                return -1;
        }
    }

    @WorkerThread
    @Nullable
    private static WatchRule find(@NonNull Context context, @NonNull String packageName, int userId,
                                  @WatchRule.Type int type, @NonNull String name) {
        try {
            return PermissionWatchDb.getInstance(context).ruleDao().find(packageName, userId, type, name);
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't read the watch rule for %s.", e, name);
            return null;
        }
    }

    private static void save(@NonNull Context context, @NonNull WatchRule rule, @NonNull OnRuleChanged listener) {
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean saved;
            try {
                PermissionWatchDb.getInstance(context).ruleDao().upsert(rule);
                saved = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "Couldn't save the watch rule.", e);
                saved = false;
            }
            boolean ok = saved;
            ThreadUtils.postOnMainThread(() -> {
                UIUtils.displayShortToast(ok ? R.string.permission_watcher_rule_saved
                        : R.string.permission_watcher_rule_save_failed);
                if (ok) {
                    listener.onRuleChanged(rule);
                }
            });
        });
    }

    private static void remove(@NonNull Context context, @NonNull WatchRule rule, @NonNull OnRuleChanged listener) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                PermissionWatchDb.getInstance(context).ruleDao().delete(rule);
            } catch (RuntimeException e) {
                Log.e(TAG, "Couldn't remove the watch rule.", e);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(
                        R.string.permission_watcher_rule_save_failed));
                return;
            }
            ThreadUtils.postOnMainThread(() -> {
                UIUtils.displayShortToast(R.string.permission_watcher_rule_removed, rule.shortName());
                listener.onRuleChanged(null);
            });
        });
    }
}
