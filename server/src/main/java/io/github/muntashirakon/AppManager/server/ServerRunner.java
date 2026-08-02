// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.ServerUtils;

import static io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_UID;

/**
 * ServerRunner runs the server based on the parameters given. It takes two arguments:
 * <ol>
 *     <li>
 *         <b>Parameters.</b> Each parameter is a key-value pair separated by a comma and key-values
 *         are separated by a colon. See {@link ConfigParams} to see a list of parameters.
 *     </li>
 *     <li>
 *         <b>Process ID.</b> The old process ID that has to be killed. This is an optional argument
 *     </li>
 * </ol>
 */
// Copyright 2017 Zheng Li
public final class ServerRunner {
    /**
     * The main method
     *
     * @param args See {@link ServerRunner}
     */
    public static void main(String[] args) {
        try {
            FLog.writeLog = true;
            if (args == null || args.length == 0) {
                return;
            }
            // Get arguments
            String paramsStr = args[0];
            FLog.log("Arguments: [" + ConfigParams.redact(paramsStr)
                    + (args.length > 1 ? ", " + args[1] : "") + "]");
            int oldPid = -1;
            if (args.length > 1) {
                try {
                    oldPid = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignore) {
                }
            }
            // Make it main looper
            //noinspection deprecation
            Looper.prepareMainLooper();
            Class.forName("android.app.ActivityThread")
                    .getMethod("systemMain")
                    .invoke(null);
            // Parse arguments
            final ConfigParams configParams;
            try {
                configParams = ConfigParams.parse(paramsStr);
            } catch (IllegalArgumentException e) {
                // Never echo the raw argument: it carries the privileged-channel token.
                FLog.log("Refusing to start: " + e.getMessage());
                return;
            }
            configParams.put(PARAM_UID, "" + Process.myUid());
            // Set server info
            LifecycleAgent.sServerInfo.startArgs = ConfigParams.redact(paramsStr);
            LifecycleAgent.sServerInfo.startTime = System.currentTimeMillis();
            LifecycleAgent.sServerInfo.startRealTime = SystemClock.elapsedRealtime();
            // Print debug
            System.out.println("UID: " + configParams.getUid() + ", UID: " + Process.myUid());
            System.out.println("Params: " + configParams);
            // Kill old server if requested
            if (oldPid != -1) {
                killOldServer(oldPid);
                SystemClock.sleep(1000);
            }
            // Start server
            Thread thread = new Thread(() -> {
                new ServerRunner().runServer(configParams);
                // Exit current thread, regardless of whether the server started or not
                FLog.close();
                killSelfProcess();
            });
            thread.setName("AM-IPC");
            thread.start();
            Looper.loop();
        } catch (ReflectiveOperationException | RuntimeException e) {
            FLog.log(e);
        } finally {
            // Exit current process, regardless of whether the server started or not
            FLog.log("Log closed.");
            FLog.close();
            killSelfProcess();
        }
    }

    /**
     * Kill old server by process ID. Both the process name and its owning uid are verified
     * before the kill, because the name alone is chosen by whoever started the process.
     *
     * @param oldPid Process ID of the old server
     */
    private static void killOldServer(int oldPid) {
        try {
            if (!ServerUtils.isOldServer(getProcessName(oldPid), getProcessUid(oldPid), Process.myUid())) {
                FLog.log("Refusing to kill pid " + oldPid + ": not our server");
                return;
            }
            Process.killProcess(oldPid);
            FLog.log("Killed old server with pid " + oldPid);
        } catch (RuntimeException e) {
            FLog.log(e);
        }
    }

    /**
     * Owner of a process, read from its {@code /proc} entry.
     *
     * @return The uid owning the process, or {@code -1} when it cannot be determined
     */
    private static int getProcessUid(int pid) {
        try {
            return Os.stat("/proc/" + pid).st_uid;
        } catch (ErrnoException | RuntimeException e) {
            FLog.log(e);
            return -1;
        }
    }

    /**
     * Kill current process
     */
    private static void killSelfProcess() {
        int pid = Process.myPid();
        System.out.println("Killing self process with pid " + pid);
        killProcess(pid);
    }

    /**
     * Kill a process by process ID
     *
     * @param pid Process ID to be killed
     */
    private static void killProcess(int pid) {
        try {
            Process.killProcess(pid);
        } catch (RuntimeException e) {
            FLog.log(e);
        } finally {
            try {
                // Kill using SIGNAL 9
                Os.execve("/system/bin/kill", new String[]{"-9", String.valueOf(pid)}, null);
            } catch (ErrnoException | RuntimeException e) {
                FLog.log(e);
            }
        }
    }

    /**
     * Get the process name from process ID
     *
     * @param pid Process ID
     * @return Process name or empty string
     */
    @NonNull
    private static String getProcessName(int pid) {
        File cmdLine = new File("/proc/" + pid + "/cmdline");
        if (!cmdLine.exists()) return "";
        try (FileInputStream fis = new FileInputStream(cmdLine)) {
            byte[] buff = new byte[512];
            int len = fis.read(buff);
            if (len > 0) {
                int i;
                for (i = 0; i < len; i++) {
                    if (buff[i] == '\0') {
                        break;
                    }
                }
                return new String(buff, 0, i);
            }
        } catch (IOException e) {
            FLog.log(e);
        }
        return "";
    }

    private ServerRunner() {
    }

    /**
     * Run the local server. The server is actually run by {@link ServerHandler}.
     *
     * @param configParams The parameters to be used during and after the server starts.
     */
    private void runServer(@NonNull ConfigParams configParams) {
        LifecycleAgent lifecycleAgent = new LifecycleAgent(configParams);
        try (ServerHandler serverHandler = new ServerHandler(lifecycleAgent)) {
            System.out.println("Success! Server has started.");
            int pid = Process.myPid();
            System.out.println("Process: " + getProcessName(pid) + ", PID: " + pid);
            // Send broadcast message to the system that the server has started
            lifecycleAgent.onStarted();
            // Start server
            serverHandler.start();
        } catch (IOException | RuntimeException e) {
            System.out.println("Error! Could not start server. " + e.getMessage());
            FLog.log(e);
        } finally {
            // Send broadcast message to the system that the server has stopped
            lifecycleAgent.onStopped();
        }
    }
}
