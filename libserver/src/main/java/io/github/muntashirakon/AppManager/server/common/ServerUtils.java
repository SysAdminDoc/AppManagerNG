// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later
package io.github.muntashirakon.AppManager.server.common;

import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;

// Copyright 2020 John "topjohnwu" Wu
// Must be accessed via reflection
public final class ServerUtils {
    public static final String CMDLINE_START_SERVICE = "start";
    public static final String CMDLINE_START_DAEMON = "daemon";
    public static final String CMDLINE_STOP_SERVICE = "stop";

    public static final String CMDLINE_STOP_SERVER = "stopServer";

    public static Context getSystemContext() {
        try {
            synchronized (Looper.class) {
                if (Looper.getMainLooper() == null)
                    Looper.prepareMainLooper();
            }

            Class<?> atClazz = Class.forName("android.app.ActivityThread");
            Object activityThread = null;
            try {
                // Reuse the ActivityThread this process already attached. ServerRunner calls
                // systemMain() during startup, and systemMain() attaches a *new* ActivityThread
                // every time -- calling it twice in one process is not something the platform
                // expects.
                activityThread = atClazz.getMethod("currentActivityThread").invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Not reachable on this platform; fall through to attaching one.
            }
            if (activityThread == null) {
                activityThread = atClazz.getMethod("systemMain").invoke(null);
            }
            Method getSystemContext = atClazz.getMethod("getSystemContext");
            return (Context) getSystemContext.invoke(activityThread);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    // Put "app-manager-" in front of the service name to prevent possible conflicts
    public static String getServiceName(String pkg) {
        return "app-manager-" + pkg;
    }

    /** Validated form of the root-service trampoline's command line. */
    public static final class LaunchArgs {
        public final ComponentName component;
        public final int uid;
        public final boolean isDaemon;
        public final boolean stop;

        LaunchArgs(ComponentName component, int uid, boolean isDaemon, boolean stop) {
            this.component = component;
            this.uid = uid;
            this.isDaemon = isDaemon;
            this.stop = stop;
        }
    }

    /**
     * Validate the trampoline's command line before any of it is used to select a user, a
     * package, or a class to load. Every element arrives from whoever spawned the trampoline,
     * so a malformed one must produce a named failure rather than an unchecked exception from
     * deep inside the launch path.
     *
     * @throws IllegalArgumentException if the arity, component name, uid or action is malformed
     */
    @NonNull
    public static LaunchArgs parseLaunchArgs(@Nullable String[] args) {
        if (args == null || args.length < 3) {
            throw new IllegalArgumentException("Expected 3 arguments, got "
                    + (args == null ? 0 : args.length));
        }
        ComponentName component = ComponentName.unflattenFromString(args[0]);
        if (component == null || component.getPackageName().isEmpty()
                || component.getClassName().isEmpty()) {
            throw new IllegalArgumentException("Malformed component name: " + args[0]);
        }
        final int uid;
        try {
            uid = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed uid: " + args[1]);
        }
        if (uid < 0) {
            throw new IllegalArgumentException("Negative uid: " + uid);
        }
        switch (args[2]) {
            case CMDLINE_STOP_SERVICE:
                return new LaunchArgs(component, uid, true, true);
            case CMDLINE_START_DAEMON:
                return new LaunchArgs(component, uid, true, false);
            case CMDLINE_START_SERVICE:
                return new LaunchArgs(component, uid, false, false);
            default:
                throw new IllegalArgumentException("Unknown action: " + args[2]);
        }
    }

    /**
     * A candidate is only the old privileged server when it carries the expected process name
     * <em>and</em> is owned by the same uid the current server runs as. Any process may name
     * itself {@link Constants#SERVER_NAME}; only one running with our privileges could be the
     * server we are replacing.
     *
     * @param processName Process name read from {@code /proc/<pid>/cmdline}
     * @param procUid     Owner of {@code /proc/<pid>}, or {@code -1} when it could not be read
     * @param selfUid     Uid of the process performing the kill
     */
    public static boolean isOldServer(@Nullable String processName, int procUid, int selfUid) {
        if (!Constants.SERVER_NAME.equals(processName)) {
            return false;
        }
        return procUid >= 0 && procUid == selfUid;
    }
}
