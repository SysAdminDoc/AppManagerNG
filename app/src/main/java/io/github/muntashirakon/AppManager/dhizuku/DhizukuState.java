// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import androidx.annotation.NonNull;

/**
 * Where Dhizuku stands on this device, from AppManagerNG's side.
 */
public enum DhizukuState {
    /** Android is older than 6.0, the lowest version Dhizuku-API supports. */
    UNSUPPORTED_ANDROID,
    /** No Dhizuku app is installed and no Dhizuku server answered. */
    NOT_INSTALLED,
    /** Dhizuku is installed but isn't the device or profile owner. */
    NO_OWNER,
    /** Dhizuku is the owner, but its server didn't hand over a binder. */
    NOT_CONNECTED,
    /** Dhizuku answered, but hasn't allowed AppManagerNG. */
    NOT_PERMITTED,
    /** Dhizuku allowed AppManagerNG. */
    PERMITTED;

    /**
     * @param installed       the official Dhizuku app is installed
     * @param dhizukuIsOwner  the official Dhizuku receiver is the device or profile owner
     * @param connected       a Dhizuku server answered, which also covers forks that act as one
     * @param permitted       that server allowed this app
     */
    @NonNull
    public static DhizukuState of(int sdk, boolean installed, boolean dhizukuIsOwner, boolean connected,
                                  boolean permitted) {
        if (sdk < DhizukuBridge.MIN_SUPPORTED_SDK) {
            return UNSUPPORTED_ANDROID;
        }
        if (connected) {
            return permitted ? PERMITTED : NOT_PERMITTED;
        }
        if (dhizukuIsOwner) {
            return NOT_CONNECTED;
        }
        return installed ? NO_OWNER : NOT_INSTALLED;
    }
}
