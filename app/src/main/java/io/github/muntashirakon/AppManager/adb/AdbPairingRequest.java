// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AdbPairingRequest {
    public static final int PAIRING_CODE_LENGTH = 6;

    private final int mPort;
    @NonNull
    private final String mPairingCode;

    private AdbPairingRequest(int port, @NonNull String pairingCode) {
        mPort = port;
        mPairingCode = pairingCode;
    }

    @Nullable
    public static AdbPairingRequest create(int port, @Nullable CharSequence pairingCode) {
        String normalizedCode = normalizeCode(pairingCode);
        if (!isValidPort(port) || !isValidCode(normalizedCode)) {
            return null;
        }
        return new AdbPairingRequest(port, normalizedCode);
    }

    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    public static boolean isValidCode(@Nullable CharSequence pairingCode) {
        if (pairingCode == null || pairingCode.length() != PAIRING_CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < pairingCode.length(); ++i) {
            if (!Character.isDigit(pairingCode.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static String normalizeCode(@Nullable CharSequence pairingCode) {
        return pairingCode != null ? pairingCode.toString().trim() : "";
    }

    public int getPort() {
        return mPort;
    }

    @NonNull
    public String getPairingCode() {
        return mPairingCode;
    }
}
