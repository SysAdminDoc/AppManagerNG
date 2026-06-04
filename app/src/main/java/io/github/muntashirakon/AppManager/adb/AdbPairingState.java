// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import androidx.annotation.NonNull;

public final class AdbPairingState {
    public enum Status {
        IDLE,
        SEARCHING,
        PORT_FOUND,
        PAIRING,
        FAILED,
        SUCCEEDED,
        CANCELLED
    }

    @NonNull
    private final Status mStatus;
    private final int mPort;

    private AdbPairingState(@NonNull Status status, int port) {
        mStatus = status;
        mPort = port;
    }

    @NonNull
    public static AdbPairingState idle() {
        return new AdbPairingState(Status.IDLE, -1);
    }

    @NonNull
    public static AdbPairingState searching() {
        return new AdbPairingState(Status.SEARCHING, -1);
    }

    @NonNull
    public static AdbPairingState portFound(int port) {
        return new AdbPairingState(Status.PORT_FOUND, port);
    }

    @NonNull
    public static AdbPairingState pairing(int port) {
        return new AdbPairingState(Status.PAIRING, port);
    }

    @NonNull
    public static AdbPairingState failed(int port) {
        return new AdbPairingState(Status.FAILED, port);
    }

    @NonNull
    public static AdbPairingState succeeded(int port) {
        return new AdbPairingState(Status.SUCCEEDED, port);
    }

    @NonNull
    public static AdbPairingState cancelled() {
        return new AdbPairingState(Status.CANCELLED, -1);
    }

    @NonNull
    public Status getStatus() {
        return mStatus;
    }

    public int getPort() {
        return mPort;
    }

    public boolean hasPort() {
        return AdbPairingRequest.isValidPort(mPort);
    }

    public boolean canSubmitCode(@NonNull CharSequence code) {
        return hasPort() && mStatus != Status.PAIRING && mStatus != Status.SUCCEEDED
                && mStatus != Status.CANCELLED && AdbPairingRequest.isValidCode(AdbPairingRequest.normalizeCode(code));
    }
}
