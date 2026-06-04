// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public final class AdbPairingSession {
    @NonNull
    private static final MutableLiveData<AdbPairingState> sState = new MutableLiveData<>(AdbPairingState.idle());
    @NonNull
    private static AdbPairingState sCurrentState = AdbPairingState.idle();

    private AdbPairingSession() {
    }

    @NonNull
    public static LiveData<AdbPairingState> getState() {
        return sState;
    }

    @NonNull
    public static AdbPairingState getCurrentState() {
        return sCurrentState;
    }

    static void searching() {
        publish(AdbPairingState.searching());
    }

    static void portFound(int port) {
        publish(AdbPairingState.portFound(port));
    }

    static void pairing(int port) {
        publish(AdbPairingState.pairing(port));
    }

    static void failed(int port) {
        publish(AdbPairingState.failed(port));
    }

    static void succeeded(int port) {
        publish(AdbPairingState.succeeded(port));
    }

    static void cancelled() {
        publish(AdbPairingState.cancelled());
    }

    public static boolean shouldWaitForRetry(@NonNull AdbPairingState state, boolean serviceRunning) {
        return serviceRunning && state.getStatus() != AdbPairingState.Status.CANCELLED;
    }

    private static void publish(@NonNull AdbPairingState state) {
        sCurrentState = state;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sState.setValue(state);
        } else {
            sState.postValue(state);
        }
    }
}
