// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGate;
import io.github.muntashirakon.AppManager.sync.lan.SyncComparator;

/**
 * What the Signatures tab shows for a package's signer pin and the APK on screen, using the same
 * rule the installer applies.
 */
public final class SignerPinReview {
    @IntDef({UNVERIFIED, UNPINNED, MATCHES, ROTATED, DIFFERENT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    /** The APK has no signature that verifies, so there's nothing to pin. */
    public static final int UNVERIFIED = 0;
    /** The package has no pin. */
    public static final int UNPINNED = 1;
    /** The APK is signed by the pinned set. */
    public static final int MATCHES = 2;
    /** The APK is signed by a newer key whose rotation lineage includes the single pinned key. */
    public static final int ROTATED = 3;
    /** The installer would refuse this APK. */
    public static final int DIFFERENT = 4;

    @State
    public final int state;
    @Nullable
    public final SignerPin pin;
    /** The APK's current signer set, or {@code null} when it doesn't verify. */
    @Nullable
    public final List<String> candidate;

    private SignerPinReview(@State int state, @Nullable SignerPin pin, @Nullable List<String> candidate) {
        this.state = state;
        this.pin = pin;
        this.candidate = candidate;
    }

    @NonNull
    public static SignerPinReview of(@Nullable SignerPin pin, @Nullable ApkSignatureGate.SignerDigests signers) {
        if (signers == null || signers.current.isEmpty()) {
            return new SignerPinReview(UNVERIFIED, pin, null);
        }
        if (pin == null) {
            return new SignerPinReview(UNPINNED, null, signers.current);
        }
        if (pin.isSameSet(signers.current)) {
            return new SignerPinReview(MATCHES, pin, signers.current);
        }
        if (SyncComparator.signersMatch(signers.current, signers.lineage, pin.certSha256)) {
            return new SignerPinReview(ROTATED, pin, signers.current);
        }
        return new SignerPinReview(DIFFERENT, pin, signers.current);
    }
}
