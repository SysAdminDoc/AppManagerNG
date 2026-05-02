// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Registers Android 17 ProfilingManager triggers (OOM + anomaly) so the system
 * captures heap profiles when AppManagerNG hits low-memory or app-exit
 * conditions during heavy operations such as JADX decompile or APK parsing.
 *
 * <p>The full {@code android.os.profiling} API surface lands in API 37 and is
 * not yet present in compileSdk 36. We resolve every type and method
 * reflectively and fall through to a silent no-op on anything below API 37 or
 * when reflection fails — there are no hard build-time references to the new
 * symbols.
 *
 * <p>Capturing the resulting profile artifacts and attaching them to the
 * shareable diagnostic ZIP is a follow-up task gated on stable API 37
 * device-side test infrastructure; the registered triggers do not depend on
 * that wiring.
 */
public final class ProfilingTriggerHelper {
    private static final String TAG = "ProfilingTrigger";

    /** Android 17 / API 37 — first version that ships TRIGGER_TYPE_OOM + TRIGGER_TYPE_ANOMALY. */
    private static final int MIN_SDK_FOR_TRIGGERS = 37;

    private ProfilingTriggerHelper() {
    }

    /**
     * Registers ProfilingTrigger entries for OOM and anomaly events when running
     * on API 37+. No-op on any earlier release or when the platform API is
     * unavailable for any reason. Safe to call repeatedly.
     */
    public static void registerTriggersIfSupported(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < MIN_SDK_FOR_TRIGGERS) {
            return;
        }
        try {
            Class<?> profilingManagerCls = Class.forName("android.os.profiling.ProfilingManager");
            Object profilingManager = ctx.getSystemService(profilingManagerCls);
            if (profilingManager == null) {
                return;
            }
            Class<?> triggerCls = Class.forName("android.os.profiling.ProfilingTrigger");
            Class<?> builderCls = Class.forName("android.os.profiling.ProfilingTrigger$Builder");

            int oom = triggerCls.getField("TRIGGER_TYPE_OOM").getInt(null);
            int anomaly = triggerCls.getField("TRIGGER_TYPE_ANOMALY").getInt(null);

            Constructor<?> builderCtor = builderCls.getConstructor(int.class);
            Method buildMethod = builderCls.getMethod("build");

            List<Object> triggers = new ArrayList<>(2);
            triggers.add(buildMethod.invoke(builderCtor.newInstance(oom)));
            triggers.add(buildMethod.invoke(builderCtor.newInstance(anomaly)));

            Method addTriggers = profilingManagerCls.getMethod("addProfilingTriggers", List.class);
            addTriggers.invoke(profilingManager, triggers);

            Log.d(TAG, "Registered ProfilingManager OOM + anomaly triggers (API "
                    + Build.VERSION.SDK_INT + ")");
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            // API surface not yet present on this device; silently no-op.
            Log.d(TAG, "ProfilingManager API absent: " + e.getClass().getSimpleName());
        } catch (Throwable t) {
            // Any other reflection failure: don't crash app startup.
            Log.w(TAG, "Failed to register ProfilingManager triggers", t);
        }
    }
}
