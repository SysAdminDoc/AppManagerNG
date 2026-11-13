// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runs in the privileged process (root, ADB or Shizuku) and tells the app as soon as a runtime permission or app op
 * of a watched app changes. Android sends apps no broadcast when a permission is granted in Settings, and it may
 * freeze the app while it sits in the background, but not this process. So a temporary allow is undone within
 * seconds instead of at the next check. The process ends with the app's, and the app hands over its targets again
 * whenever it binds the privileged services.
 */
public final class PermissionWatchRelay {
    private static final String TAG = "PermissionWatchRelay";

    /** Permission changes come in bursts (a grant also flips its app op), so they settle into one check. */
    static final long SETTLE_MILLIS = 300;

    interface Listeners {
        /** Starts listening, or moves the app op watch to {@code packages}. */
        void watch(@NonNull PermissionWatchRelay relay, @NonNull Set<String> packages) throws Exception;

        void unwatch();
    }

    interface Messenger {
        void send(int appUid, @NonNull String packageName, int uid);
    }

    interface Delayer {
        void postDelayed(@NonNull Runnable runnable, long delayMillis);
    }

    @Nullable
    private static PermissionWatchRelay sInstance;

    /**
     * @param context A context of this package in the privileged process, used to reach the app
     */
    @NonNull
    public static synchronized PermissionWatchRelay getInstance(@NonNull Context context) {
        if (sInstance == null) {
            HandlerThread thread = new HandlerThread("PermissionWatchRelay");
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            sInstance = new PermissionWatchRelay(new PermissionWatchListeners(), new BroadcastMessenger(context),
                    handler::postDelayed);
        }
        return sInstance;
    }

    private static final class Target {
        @NonNull
        final String packageName;
        final int uid;

        Target(@NonNull String packageName, int uid) {
            this.packageName = packageName;
            this.uid = uid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Target)) return false;
            Target target = (Target) o;
            return uid == target.uid && packageName.equals(target.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, uid);
        }
    }

    @NonNull
    private final Listeners mListeners;
    @NonNull
    private final Messenger mMessenger;
    @NonNull
    private final Delayer mDelayer;
    private final Object mLock = new Object();
    @NonNull
    private Map<Integer, Set<String>> mPackagesByUid = Collections.emptyMap();
    private int mAppUid = -1;
    private final Set<Target> mPending = new LinkedHashSet<>();
    private boolean mFlushPosted;

    @VisibleForTesting
    PermissionWatchRelay(@NonNull Listeners listeners, @NonNull Messenger messenger, @NonNull Delayer delayer) {
        mListeners = listeners;
        mMessenger = messenger;
        mDelayer = delayer;
    }

    /**
     * Replaces what is watched. {@code uids} and {@code packageNames} pair up by index; empty arrays stop the watch.
     *
     * @param appUid The uid of the app instance to tell, so a work profile's copy hears about its own apps
     */
    public void setTargets(int appUid, @Nullable int[] uids, @Nullable String[] packageNames) {
        Map<Integer, Set<String>> packagesByUid = new HashMap<>();
        Set<String> packages = new HashSet<>();
        int count = uids == null || packageNames == null ? 0 : Math.min(uids.length, packageNames.length);
        for (int i = 0; i < count; ++i) {
            String packageName = packageNames[i];
            if (packageName == null || packageName.isEmpty() || uids[i] < 0) {
                continue;
            }
            Set<String> forUid = packagesByUid.get(uids[i]);
            if (forUid == null) {
                forUid = new HashSet<>();
                packagesByUid.put(uids[i], forUid);
            }
            forUid.add(packageName);
            packages.add(packageName);
        }
        synchronized (mLock) {
            mAppUid = appUid;
            mPackagesByUid = packagesByUid;
        }
        try {
            if (packages.isEmpty()) {
                mListeners.unwatch();
            } else {
                mListeners.watch(this, packages);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not watch permission changes; the scheduled check still runs.", e);
        }
    }

    /**
     * A runtime permission or app op of {@code uid} changed.
     *
     * @param packageName The app whose app op changed, or {@code null} when only the uid is known
     */
    public void onChanged(int uid, @Nullable String packageName) {
        boolean post;
        synchronized (mLock) {
            Set<String> packages = mPackagesByUid.get(uid);
            if (packages == null) {
                return;
            }
            if (packageName != null) {
                if (!packages.contains(packageName)) {
                    return;
                }
                mPending.add(new Target(packageName, uid));
            } else {
                for (String watched : packages) {
                    mPending.add(new Target(watched, uid));
                }
            }
            post = !mFlushPosted;
            mFlushPosted = true;
        }
        if (post) {
            mDelayer.postDelayed(this::flush, SETTLE_MILLIS);
        }
    }

    @VisibleForTesting
    void flush() {
        List<Target> targets;
        int appUid;
        synchronized (mLock) {
            targets = new ArrayList<>(mPending);
            mPending.clear();
            mFlushPosted = false;
            appUid = mAppUid;
        }
        for (Target target : targets) {
            mMessenger.send(appUid, target.packageName, target.uid);
        }
    }

    private static final class BroadcastMessenger implements Messenger {
        @NonNull
        private final Context mContext;

        BroadcastMessenger(@NonNull Context context) {
            mContext = context;
        }

        @Override
        public void send(int appUid, @NonNull String packageName, int uid) {
            Intent intent = WatchedAppChangedReceiver.intentFor(packageName, uid);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && appUid >= 0) {
                    mContext.sendBroadcastAsUser(intent, UserHandle.getUserHandleForUid(appUid));
                } else {
                    mContext.sendBroadcast(intent);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not tell the app that " + packageName + " changed.", e);
            }
        }
    }
}
