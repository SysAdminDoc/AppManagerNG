// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;

/**
 * Shows the latest release's changelog once after an in-place app update.
 *
 * The auto-display flag is set elsewhere when {@code PREF_LAST_VERSION_CODE_LONG}
 * crosses {@code BuildConfig.VERSION_CODE}; this helper consumes the flag,
 * parses {@link io.github.muntashirakon.AppManager.changelog.ChangelogParser}'s
 * top-most release block, and presents it through the existing
 * {@link AlertDialogBuilder} + {@link ChangelogRecyclerAdapter} surface.
 *
 * Fresh installs never see the dialog because the migration helper primes
 * {@code PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG} on first run without
 * setting {@code PREF_DISPLAY_CHANGELOG_BOOL}.
 */
public final class ChangelogAutoDisplay {
    private ChangelogAutoDisplay() {
    }

    /**
     * If the in-place update flag is set, parse the bundled changelog on a
     * background thread and post the latest release dialog back to the main
     * thread. Safe to call from {@code Activity.onAuthenticated} / similar
     * post-auth hooks; the dialog uses {@code activity} so callers must
     * guarantee it is still alive when {@link #postLatestRelease} runs.
     */
    @AnyThread
    public static void showIfPending(@NonNull Activity activity) {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL)) {
            return;
        }
        // Consume the flag immediately so we never double-show on screen rotation.
        AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, false);
        Context appContext = activity.getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            Changelog changelog;
            try {
                changelog = new ChangelogParser(appContext, R.raw.changelog).parse();
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
                return;
            }
            ThreadUtils.postOnMainThread(() -> postLatestRelease(activity, changelog));
        });
    }

    @MainThread
    private static void postLatestRelease(@NonNull Activity activity, @Nullable Changelog full) {
        if (activity.isFinishing() || activity.isDestroyed() || full == null) {
            return;
        }
        List<ChangelogItem> latest = latestReleaseItems(full.getChangelogItems());
        if (latest.isEmpty()) {
            return;
        }
        View v = View.inflate(activity, R.layout.dialog_whats_new, null);
        RecyclerView recyclerView = v.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        ChangelogRecyclerAdapter adapter = new ChangelogRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setAdapterList(latest);
        try {
            new AlertDialogBuilder(activity, true)
                    .setTitle(R.string.whats_new)
                    .setView(recyclerView)
                    .show();
        } catch (Throwable ignored) {
            // Window may have been torn down between the post and the show; ignore.
        }
    }

    /**
     * Return only the rows under the first {@link ChangelogHeader} (the most
     * recent release block). If the parser produced no header — unusual but
     * not fatal — the full list falls through.
     */
    @NonNull
    static List<ChangelogItem> latestReleaseItems(@NonNull List<ChangelogItem> all) {
        if (all.isEmpty()) {
            return all;
        }
        int firstHeader = indexOfHeader(all, 0);
        if (firstHeader < 0) {
            return all;
        }
        int nextHeader = indexOfHeader(all, firstHeader + 1);
        if (nextHeader < 0) {
            return new ArrayList<>(all.subList(firstHeader, all.size()));
        }
        return new ArrayList<>(all.subList(firstHeader, nextHeader));
    }

    private static int indexOfHeader(@NonNull List<ChangelogItem> all, int from) {
        for (int i = from; i < all.size(); ++i) {
            if (all.get(i) instanceof ChangelogHeader) {
                return i;
            }
        }
        return -1;
    }
}
