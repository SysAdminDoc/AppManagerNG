// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGate;

@RunWith(RobolectricTestRunner.class)
public class SignerPinReviewTest {
    private static final String OLD = repeat('a');
    private static final String NEW = repeat('b');
    private static final String THIRD = repeat('c');

    @Test
    public void anApkWithoutVerifiedSignersHasNothingToPin() {
        SignerPin pin = pin(OLD);

        assertEquals(SignerPinReview.UNVERIFIED, SignerPinReview.of(null, null).state);
        SignerPinReview review = SignerPinReview.of(pin, signers(Collections.emptyList(), Collections.emptyList()));
        assertEquals(SignerPinReview.UNVERIFIED, review.state);
        assertSame(pin, review.pin);
        assertNull(review.candidate);
    }

    @Test
    public void anUnpinnedAppOffersItsSigners() {
        SignerPinReview review = SignerPinReview.of(null, signers(Collections.singletonList(NEW), Collections.emptyList()));

        assertEquals(SignerPinReview.UNPINNED, review.state);
        assertEquals(Collections.singletonList(NEW), review.candidate);
    }

    @Test
    public void theSameKeyInAnyCaseMatches() {
        SignerPinReview review = SignerPinReview.of(pin(OLD),
                signers(Collections.singletonList(OLD.toUpperCase(Locale.ROOT)), Collections.emptyList()));

        assertEquals(SignerPinReview.MATCHES, review.state);
    }

    @Test
    public void aSingleKeyThePinnedOneHandedOverToIsARotation() {
        SignerPinReview review = SignerPinReview.of(pin(OLD),
                signers(Collections.singletonList(NEW), Arrays.asList(OLD, NEW)));

        assertEquals(SignerPinReview.ROTATED, review.state);
        assertEquals(Collections.singletonList(NEW), review.candidate);
    }

    @Test
    public void aKeyWithNoLineageBackToThePinIsDifferent() {
        assertEquals(SignerPinReview.DIFFERENT, SignerPinReview.of(pin(OLD),
                signers(Collections.singletonList(NEW), Collections.singletonList(NEW))).state);
        // A lineage only counts between single keys, the same rule the installer applies.
        assertEquals(SignerPinReview.DIFFERENT, SignerPinReview.of(pin(OLD, THIRD),
                signers(Collections.singletonList(NEW), Arrays.asList(OLD, THIRD, NEW))).state);
    }

    @NonNull
    private static SignerPin pin(@NonNull String... digests) {
        return new SignerPin(Arrays.asList(digests), 1);
    }

    @NonNull
    private static ApkSignatureGate.SignerDigests signers(@NonNull List<String> current, @NonNull List<String> lineage) {
        return new ApkSignatureGate.SignerDigests(current, lineage);
    }

    @NonNull
    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
