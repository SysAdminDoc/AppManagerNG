// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.Nullable;

/**
 * Android hands a package verifier's refusal (Play Protect's "Harmful app blocked", or a verifier
 * that never answered) back as {@link PackageInstallerCompat#STATUS_FAILURE_ABORTED}, the status a
 * user cancel gets. Read that way, the result said "cancelled, keep the installer open", and Local
 * sync took it for a cancel and offered no way out, so a refusal counts as blocked instead. Only the
 * status message tells the two apart: Android starts it with the legacy result name.
 */
final class VerifierRefusal {
    private static final String[] REFUSALS = {
            "INSTALL_FAILED_VERIFICATION_FAILURE",
            "INSTALL_FAILED_VERIFICATION_TIMEOUT",
    };

    private VerifierRefusal() {
    }

    @PackageInstallerCompat.Status
    static int classify(@PackageInstallerCompat.Status int status, @Nullable String statusMessage) {
        if (status != PackageInstallerCompat.STATUS_FAILURE_ABORTED || statusMessage == null) {
            return status;
        }
        for (String refusal : REFUSALS) {
            if (statusMessage.startsWith(refusal)) {
                return PackageInstallerCompat.STATUS_FAILURE_BLOCKED;
            }
        }
        return status;
    }
}
