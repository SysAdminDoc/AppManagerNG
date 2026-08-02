// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.server.common.PeerAuthority;
import io.github.muntashirakon.AppManager.server.common.ServerUtils;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;

// Copyright 2017 Zheng Li
class ServerHandler implements DataTransmission.OnReceiveCallback, Closeable {
    private static final int MSG_TIMEOUT = 1;
    private static final int DEFAULT_TIMEOUT = 1000 * 60; // 1 min
    private static final int BG_TIMEOUT = DEFAULT_TIMEOUT * 10; // 10 min

    private final LifecycleAgent mLifecycleAgent;
    private final ConfigParams mConfigParams;
    private final Server mServer;
    private final boolean mRunInBackground;

    private Handler mHandler;
    private volatile boolean mIsDead = false;

    ServerHandler(@NonNull LifecycleAgent lifecycleAgent) throws IOException {
        mLifecycleAgent = lifecycleAgent;
        mConfigParams = mLifecycleAgent.getConfigParams();
        // Set params
        System.out.println("Config params: " + mConfigParams);
        String path = mConfigParams.getPath();
        int port = -1;
        try {
            if (path != null) port = Integer.parseInt(path);
        } catch (NumberFormatException ignore) {
        }
        String token = mConfigParams.getToken();
        if (token == null) throw new IOException("Token is not found.");
        mRunInBackground = mConfigParams.isRunInBackground();
        // Set server
        int expectedAppId = resolveExpectedAppId(mConfigParams.getAppName());
        if (port == -1) {
            mServer = new Server(path, token, expectedAppId, mLifecycleAgent, this);
        } else {
            mServer = new Server(port, token, expectedAppId, mLifecycleAgent, this);
        }
        mServer.mRunInBackground = mRunInBackground;
        // If run in background not requested, stop server on timeout
        if (!mRunInBackground) {
            HandlerThread handlerThread = new HandlerThread("am_server_watcher");
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper()) {
                @Override
                public void handleMessage(@NonNull Message message) {
                    super.handleMessage(message);
                    if (message.what == MSG_TIMEOUT) {
                        close();
                    }
                }
            };
            mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, DEFAULT_TIMEOUT);
        }
    }

    void start() throws IOException, RuntimeException {
        mServer.run();
    }

    /**
     * App id the channel is being started for, so a connecting peer can be checked against
     * something it does not choose itself.
     *
     * @return The app id, or {@link PeerAuthority#UID_UNKNOWN} when it cannot be resolved here
     */
    private static int resolveExpectedAppId(@Nullable String packageName) {
        if (packageName == null) {
            FLog.log("ServerHandler: no app name in the parameters; peer uid cannot be checked.");
            return PeerAuthority.UID_UNKNOWN;
        }
        try {
            Context context = ServerUtils.getSystemContext();
            int uid = context.getPackageManager().getApplicationInfo(packageName, 0).uid;
            return PeerAuthority.appIdOf(uid);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            FLog.log(e);
            FLog.log("ServerHandler: could not resolve the uid of " + packageName
                    + "; peer uid cannot be checked.");
            return PeerAuthority.UID_UNKNOWN;
        }
    }

    @Override
    public void close() {
        FLog.log("ServerHandler: Destroying...");
        try {
            if (!mRunInBackground && mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.removeMessages(MSG_TIMEOUT);
                mHandler.getLooper().quit();
            }
        } catch (RuntimeException e) {
            FLog.log(e);
        }
        try {
            mIsDead = true;
            mServer.close();
        } catch (IOException | RuntimeException e) {
            FLog.log(e);
        }
    }

    private void sendOpResult(Parcelable result) {
        try {
            mServer.sendResult(ParcelableUtil.marshall(result));
        } catch (IOException e) {
            FLog.log(e);
        }
    }

    @Override
    public void onMessage(@NonNull byte[] bytes) {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.removeMessages(MSG_TIMEOUT);
        }

        if (!mIsDead) {
            if (!mRunInBackground && mHandler != null) {
                mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, BG_TIMEOUT);
            }
            LifecycleAgent.sServerInfo.rxBytes += bytes.length;
            CallerResult result = null;
            try {
                BaseCaller baseCaller = ParcelableUtil.unmarshall(bytes, BaseCaller.CREATOR);
                int type = baseCaller.getType();
                switch (type) {
                    case BaseCaller.TYPE_CLOSE:
                        close();
                        return;
                    case BaseCaller.TYPE_SHELL:
                        byte[] rawBytes = baseCaller.getRawBytes();
                        if (rawBytes == null) {
                            throw new IOException("Shell request carried no payload");
                        }
                        ShellCaller shellCaller = ParcelableUtil.unmarshall(rawBytes, ShellCaller.CREATOR);
                        String command = shellCaller.getCommand();
                        if (command == null) {
                            throw new IOException("Shell request carried no command");
                        }
                        Shell shell = Shell.getShell("");
                        Shell.Result shellResult = shell.exec(command);
                        result = new CallerResult();
                        // Written with the concrete type, not writeValue(): the reply must not
                        // name the class the app instantiates when it reads this back.
                        result.setReply(ParcelableUtil.marshall(shellResult));
                }
                LifecycleAgent.sServerInfo.successCount++;
            } catch (IOException | RuntimeException e) {
                FLog.log(e);
                result = new CallerResult();
                result.setThrowable(e);
                LifecycleAgent.sServerInfo.errorCount++;
            } finally {
                if (result == null) {
                    result = new CallerResult();
                }
                sendOpResult(result);
            }
        }
    }
}
