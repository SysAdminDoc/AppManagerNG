// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.ServerActions;
import io.github.muntashirakon.AppManager.server.common.ServerInfo;

import static io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_TOKEN;
import static io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_UID;
import static io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_PATH;

// Copyright 2017 Zheng Li
final class LifecycleAgent {
    static final ServerInfo sServerInfo = new ServerInfo();

    @NonNull
    private final ConfigParams mConfigParams;

    public LifecycleAgent(@NonNull ConfigParams configParams) {
        mConfigParams = configParams;
    }

    @NonNull
    public ConfigParams getConfigParams() {
        return mConfigParams;
    }

    void onStarted() {
        BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_STARTED));
    }

    void onConnected() {
        BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_CONNECTED));
    }

    void onDisconnected() {
        BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_DISCONNECTED));
    }

    void onStopped() {
        BroadcastSender.sendBroadcast(makeIntent(ServerActions.ACTION_SERVER_STOPPED));
    }

    /**
     * The target package comes from the {@code app} launch parameter, i.e. from argv, and this
     * intent carries the channel token. That is sound rather than a leak: whoever supplies the
     * app name supplies the {@code token} parameter in the same argv, so directing the broadcast
     * elsewhere discloses nothing the sender did not already choose. The receiver class is fixed,
     * so the package is the only variable, and an explicit component keeps the token off any
     * implicit-broadcast path.
     */
    @NonNull
    private Intent makeIntent(String action) {
        return new Intent(action)
                .setClassName(mConfigParams.getAppName(), ServerActions.PACKAGE_NAME + ".servermanager.ServerStatusChangeReceiver")
                .putExtra(PARAM_TOKEN, mConfigParams.getToken())
                .putExtra(PARAM_UID, mConfigParams.getUid())
                .putExtra(PARAM_PATH, mConfigParams.getPath());
    }
}
