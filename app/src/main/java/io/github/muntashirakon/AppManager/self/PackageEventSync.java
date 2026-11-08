// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.util.AtomicFile;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.monitor.AppUpdateChangeReportRecorder;
import io.github.muntashirakon.AppManager.permission.monitor.ComponentChangeDiff;
import io.github.muntashirakon.AppManager.permission.monitor.ComponentChangeMonitor;
import io.github.muntashirakon.AppManager.permission.monitor.PackageReappearanceMonitor;
import io.github.muntashirakon.AppManager.permission.monitor.PermissionChangeDiff;
import io.github.muntashirakon.AppManager.permission.monitor.PermissionChangeMonitor;
import io.github.muntashirakon.AppManager.permission.monitor.SigningCertChangeMonitor;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchDb;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchScheduler;
import io.github.muntashirakon.AppManager.profiles.trigger.ProfileTrigger;
import io.github.muntashirakon.AppManager.profiles.trigger.RoutineScheduler;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Works out which apps were installed, updated or removed since the last look and hands each change to the monitors
 * and routines that want it.
 *
 * <p>Android 8 and later don't deliver {@code PACKAGE_ADDED}, {@code PACKAGE_REPLACED} or {@code PACKAGE_REMOVED} to
 * a receiver declared in the manifest, so those broadcasts can't be the trigger. Instead each app's first install
 * and last update times are compared with the list saved by the previous sync. A sync runs when the app starts, on
 * every package broadcast the app does get (a receiver registered while it runs, or the manifest one below Android
 * 8), and every 15 minutes from WorkManager while something is listening.
 */
public final class PackageEventSync {
    public static final String TAG = "PackageEventSync";
    public static final String UNIQUE_WORK_NAME = "package_event_sync";
    @VisibleForTesting
    static final String STATE_FILE_NAME = "package_event_state.json";
    private static final int STATE_VERSION = 1;

    /** One app's install times. {@code system} is only known for the current look and isn't saved. */
    @VisibleForTesting
    static final class Seen {
        final long firstInstallTime;
        final long lastUpdateTime;
        final boolean system;

        Seen(long firstInstallTime, long lastUpdateTime, boolean system) {
            this.firstInstallTime = firstInstallTime;
            this.lastUpdateTime = lastUpdateTime;
            this.system = system;
        }
    }

    @VisibleForTesting
    static final class Changes {
        final List<String> added = new ArrayList<>();
        final List<String> updated = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
    }

    @VisibleForTesting
    static final class State {
        final int userId;
        @NonNull
        final String fingerprint;
        @NonNull
        final Map<String, Seen> packages;

        State(int userId, @NonNull String fingerprint, @NonNull Map<String, Seen> packages) {
            this.userId = userId;
            this.fingerprint = fingerprint;
            this.packages = packages;
        }
    }

    /** Who hears about each change. Tests swap it to record the calls. */
    @VisibleForTesting
    interface Dispatcher {
        void onAdded(@NonNull Context context, @NonNull String packageName, int userId);

        void onUpdated(@NonNull Context context, @NonNull String packageName, int userId);

        void onRemoved(@NonNull Context context, @NonNull String packageName, int userId);
    }

    /** Where the periodic job goes. Tests swap it so nothing reaches a real WorkManager. */
    @VisibleForTesting
    interface WorkSink {
        void schedule(@NonNull Context context, @NonNull PeriodicWorkRequest request);

        void cancel(@NonNull Context context);
    }

    private static final Dispatcher MONITORS = new Dispatcher() {
        @Override
        public void onAdded(@NonNull Context context, @NonNull String packageName, int userId) {
            try {
                PackageReappearanceMonitor.onPackageAdded(context, packageName);
            } catch (Exception e) {
                Log.w(TAG, "Could not check whether %s came back.", e, packageName);
            }
            checkWatchedPermissions(context, packageName, userId);
            enqueueRoutines(context, ProfileTrigger.TYPE_ON_APP_INSTALL, packageName);
        }

        @Override
        public void onUpdated(@NonNull Context context, @NonNull String packageName, int userId) {
            // An update can reset what the user set, so watched permissions are checked whatever the monitors say.
            checkWatchedPermissions(context, packageName, userId);
            runUpdateMonitors(context, packageName);
            enqueueRoutines(context, ProfileTrigger.TYPE_ON_APP_UPDATE, packageName);
        }

        @Override
        public void onRemoved(@NonNull Context context, @NonNull String packageName, int userId) {
            enqueueRoutines(context, ProfileTrigger.TYPE_ON_APP_UNINSTALL, packageName);
        }
    };

    private static final WorkSink WORK_MANAGER_SINK = new WorkSink() {
        @Override
        public void schedule(@NonNull Context context, @NonNull PeriodicWorkRequest request) {
            WorkManager workManager = getWorkManager(context);
            if (workManager != null) {
                workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
            }
        }

        @Override
        public void cancel(@NonNull Context context) {
            WorkManager workManager = getWorkManager(context);
            if (workManager != null) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
            }
        }
    };

    @NonNull
    private static volatile Dispatcher sDispatcher = MONITORS;
    @NonNull
    private static volatile WorkSink sWorkSink = WORK_MANAGER_SINK;

    private static final Object sRequestLock = new Object();
    private static boolean sDraining;
    private static boolean sRequested;
    private static final List<BroadcastReceiver.PendingResult> sWaiting = new ArrayList<>();

    private PackageEventSync() {
    }

    @VisibleForTesting
    static void setDispatcherForTesting(@Nullable Dispatcher dispatcher) {
        sDispatcher = dispatcher != null ? dispatcher : MONITORS;
    }

    @VisibleForTesting
    static void setWorkSinkForTesting(@Nullable WorkSink sink) {
        sWorkSink = sink != null ? sink : WORK_MANAGER_SINK;
    }

    /**
     * Called once from {@link Application#onCreate()} in the app's main process: catches up on whatever changed while
     * the app wasn't running, then listens for changes until the process ends.
     */
    public static void start(@NonNull Application application) {
        if (!isMainProcess(application)) {
            // The audio player's process would sync too and every change would be handled twice.
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Below Android 8 the manifest receiver still gets these.
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            ContextCompat.registerReceiver(application, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    requestSync(context, null);
                }
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        requestSync(application, null);
    }

    /**
     * Syncs on a background thread. Requests that arrive while one runs share the next run. {@code pending}, from a
     * receiver's {@code goAsync()}, is finished once a sync that started after this request is done.
     */
    public static void requestSync(@NonNull Context context, @Nullable BroadcastReceiver.PendingResult pending) {
        Context appContext = context.getApplicationContext();
        synchronized (sRequestLock) {
            if (pending != null) {
                sWaiting.add(pending);
            }
            sRequested = true;
            if (sDraining) {
                return;
            }
            sDraining = true;
        }
        ThreadUtils.postOnBackgroundThread(() -> drain(appContext));
    }

    private static void drain(@NonNull Context appContext) {
        while (true) {
            List<BroadcastReceiver.PendingResult> finishing;
            synchronized (sRequestLock) {
                if (!sRequested) {
                    sDraining = false;
                    return;
                }
                sRequested = false;
                finishing = new ArrayList<>(sWaiting);
                sWaiting.clear();
            }
            try {
                syncNow(appContext);
            } catch (Exception e) {
                Log.w(TAG, "Could not look for app changes.", e);
            } finally {
                for (BroadcastReceiver.PendingResult result : finishing) {
                    result.finish();
                }
            }
        }
    }

    /**
     * Compares the installed apps with the last sync and tells the listeners what changed. The first sync, or one
     * after the saved list was lost, only saves the list.
     */
    @WorkerThread
    public static synchronized void syncNow(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Map<String, Seen> now = readInstalled(appContext);
        if (now != null) {
            int userId = UserHandleHidden.myUserId();
            String fingerprint = Objects.toString(Build.FINGERPRINT, "");
            File stateFile = stateFile(appContext);
            State before = readState(stateFile);
            // Saved before anyone hears about it, so a change that crashes a listener isn't handled again and again.
            writeState(stateFile, new State(userId, fingerprint, now));
            if (before != null && before.userId == userId) {
                dispatch(appContext, diff(before.packages, now, !fingerprint.equals(before.fingerprint)), userId);
            }
        }
        scheduleOrCancel(appContext);
    }

    /** Keeps the 15 minute job only while one of the features that react to app changes is on. */
    public static void scheduleOrCancel(@NonNull Context context) {
        if (hasListeners(context)) {
            sWorkSink.schedule(context, buildPeriodicRequest());
        } else {
            sWorkSink.cancel(context);
        }
    }

    @VisibleForTesting
    static boolean hasListeners(@NonNull Context context) {
        if (Prefs.Privacy.isPermissionChangeMonitorEnabled() || Prefs.Privacy.isSigningCertChangeMonitorEnabled()
                || Prefs.Privacy.isAppChangeAuditorEnabled() || Prefs.Privacy.isAppUpdateChangeReportEnabled()) {
            return true;
        }
        if (PermissionWatchDb.mightHaveRules(context)) {
            return true;
        }
        try {
            return RoutineScheduler.hasEnabledPackageEventTrigger(context);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read the routine triggers.", e);
            return false;
        }
    }

    /**
     * What changed between two looks. A new first install time means the app was removed and installed again.
     * After a system update ({@code systemUpdated}), preinstalled apps get new times without being updated, so
     * their time changes are taken as the new baseline.
     */
    @VisibleForTesting
    @NonNull
    static Changes diff(@NonNull Map<String, Seen> before, @NonNull Map<String, Seen> now, boolean systemUpdated) {
        Changes changes = new Changes();
        for (Map.Entry<String, Seen> entry : now.entrySet()) {
            String packageName = entry.getKey();
            Seen current = entry.getValue();
            Seen previous = before.get(packageName);
            if (previous == null) {
                changes.added.add(packageName);
            } else if (systemUpdated && current.system) {
                continue;
            } else if (previous.firstInstallTime != current.firstInstallTime) {
                changes.removed.add(packageName);
                changes.added.add(packageName);
            } else if (previous.lastUpdateTime != current.lastUpdateTime) {
                changes.updated.add(packageName);
            }
        }
        for (String packageName : before.keySet()) {
            if (!now.containsKey(packageName)) {
                changes.removed.add(packageName);
            }
        }
        return changes;
    }

    private static void dispatch(@NonNull Context context, @NonNull Changes changes, int userId) {
        Dispatcher dispatcher = sDispatcher;
        for (String packageName : changes.removed) {
            dispatcher.onRemoved(context, packageName, userId);
        }
        for (String packageName : changes.added) {
            dispatcher.onAdded(context, packageName, userId);
        }
        for (String packageName : changes.updated) {
            dispatcher.onUpdated(context, packageName, userId);
        }
    }

    /** Installed apps for this user, this app left out. Hidden apps count as installed. */
    @Nullable
    private static Map<String, Seen> readInstalled(@NonNull Context context) {
        List<PackageInfo> packages;
        try {
            packages = context.getPackageManager().getInstalledPackages(PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not list the installed apps.", e);
            return null;
        }
        return installedOnly(packages, context.getPackageName());
    }

    @NonNull
    @VisibleForTesting
    static Map<String, Seen> installedOnly(@NonNull List<PackageInfo> packages, @NonNull String self) {
        Map<String, Seen> seen = new TreeMap<>();
        for (PackageInfo info : packages) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null || self.equals(info.packageName) || (app.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                // Without FLAG_INSTALLED it was removed for this user, keeping its data.
                continue;
            }
            seen.put(info.packageName, new Seen(info.firstInstallTime, info.lastUpdateTime,
                    (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
        }
        return seen;
    }

    @NonNull
    @VisibleForTesting
    static File stateFile(@NonNull Context context) {
        return new File(context.getNoBackupFilesDir(), STATE_FILE_NAME);
    }

    @Nullable
    @VisibleForTesting
    static State readState(@NonNull File file) {
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream in = new AtomicFile(file).openRead()) {
            byte[] bytes = new byte[(int) Math.min(file.length(), Integer.MAX_VALUE)];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return parseState(new String(bytes, 0, read, StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "The saved app list couldn't be read, so this sync starts a new one.", e);
            return null;
        }
    }

    @Nullable
    @VisibleForTesting
    static State parseState(@NonNull String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("v") != STATE_VERSION) {
            return null;
        }
        JSONObject packagesJson = root.getJSONObject("packages");
        Map<String, Seen> packages = new TreeMap<>();
        Iterator<String> names = packagesJson.keys();
        while (names.hasNext()) {
            String name = names.next();
            JSONArray times = packagesJson.getJSONArray(name);
            packages.put(name, new Seen(times.getLong(0), times.getLong(1), false));
        }
        return new State(root.getInt("user"), root.optString("fp"), packages);
    }

    @NonNull
    @VisibleForTesting
    static String toJson(@NonNull State state) throws JSONException {
        JSONObject packages = new JSONObject();
        for (Map.Entry<String, Seen> entry : state.packages.entrySet()) {
            packages.put(entry.getKey(), new JSONArray()
                    .put(entry.getValue().firstInstallTime)
                    .put(entry.getValue().lastUpdateTime));
        }
        return new JSONObject()
                .put("v", STATE_VERSION)
                .put("user", state.userId)
                .put("fp", state.fingerprint)
                .put("packages", packages)
                .toString();
    }

    private static void writeState(@NonNull File file, @NonNull State state) {
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            byte[] bytes = toJson(state).getBytes(StandardCharsets.UTF_8);
            out = atomicFile.startWrite();
            out.write(bytes);
            atomicFile.finishWrite(out);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not save the app list.", e);
            if (out != null) {
                atomicFile.failWrite(out);
            }
        }
    }

    private static void checkWatchedPermissions(@NonNull Context context, @NonNull String packageName, int userId) {
        try {
            if (PermissionWatchDb.mightHaveRules(context)) {
                PermissionWatchScheduler.checkSoon(context, packageName, userId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not check the watched permissions of %s.", e, packageName);
        }
    }

    private static void runUpdateMonitors(@NonNull Context context, @NonNull String packageName) {
        boolean permissionMonitorOn = Prefs.Privacy.isPermissionChangeMonitorEnabled();
        boolean certMonitorOn = Prefs.Privacy.isSigningCertChangeMonitorEnabled();
        boolean componentMonitorOn = Prefs.Privacy.isAppChangeAuditorEnabled();
        boolean updateReportOn = Prefs.Privacy.isAppUpdateChangeReportEnabled();
        PermissionChangeDiff.Result permissionDiff = null;
        ComponentChangeDiff.Result componentDiff = null;
        if (permissionMonitorOn || updateReportOn) {
            try {
                permissionDiff = PermissionChangeMonitor.onPackageReplaced(context, packageName, permissionMonitorOn);
            } catch (Exception e) {
                Log.w(TAG, "Permission change monitor failed for %s.", e, packageName);
            }
        }
        if (certMonitorOn) {
            try {
                SigningCertChangeMonitor.onPackageReplaced(context, packageName);
            } catch (Exception e) {
                Log.w(TAG, "Signing key change monitor failed for %s.", e, packageName);
            }
        }
        if (componentMonitorOn || updateReportOn) {
            try {
                componentDiff = ComponentChangeMonitor.onPackageReplaced(context, packageName, componentMonitorOn);
            } catch (Exception e) {
                Log.w(TAG, "Component change monitor failed for %s.", e, packageName);
            }
        }
        if (updateReportOn) {
            try {
                AppUpdateChangeReportRecorder.record(permissionDiff, componentDiff);
            } catch (Exception e) {
                Log.w(TAG, "App update report failed for %s.", e, packageName);
            }
        }
    }

    private static void enqueueRoutines(@NonNull Context context, @ProfileTrigger.Type int type,
                                        @NonNull String packageName) {
        try {
            RoutineScheduler.enqueuePackageEventTriggers(context, type, packageName);
        } catch (Exception e) {
            Log.w(TAG, "Could not start the routines for %s.", e, packageName);
        }
    }

    @NonNull
    @VisibleForTesting
    static PeriodicWorkRequest buildPeriodicRequest() {
        return new PeriodicWorkRequest.Builder(PackageEventSyncWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    @Nullable
    private static WorkManager getWorkManager(@NonNull Context context) {
        try {
            return WorkManager.getInstance(context.getApplicationContext());
        } catch (IllegalStateException e) {
            Log.w(TAG, "WorkManager is unavailable for the app change sync.", e);
            return null;
        }
    }

    @VisibleForTesting
    static boolean isMainProcess(@NonNull Context context) {
        String processName = currentProcessName();
        return processName == null || processName.equals(context.getPackageName());
    }

    @Nullable
    private static String currentProcessName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int length = in.read(buffer);
            int end = 0;
            while (end < length && buffer[end] != 0) {
                ++end;
            }
            return end > 0 ? new String(buffer, 0, end, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
