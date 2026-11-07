// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.util.Map;

/**
 * Devices paired with "Allow and remember", keyed by identity fingerprint. A remembered device is
 * let in without comparing digits, so entries are only added after the user allowed it once.
 */
public interface TrustedDevices {
    boolean isTrusted(@NonNull String fingerprint);

    void remember(@NonNull String fingerprint, @NonNull String name);

    void forget(@NonNull String fingerprint);

    /**
     * Fingerprint to display name.
     */
    @NonNull
    Map<String, String> list();
}
