// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/**
 * What a scanner result is actually evidence of.
 *
 * <p>Every tracker and library finding here comes from one detector: matching class names against
 * a list of known signatures. That detector is precise when it fires and blind when it does not.
 * A match means code whose class names match a known signature is present in the package — not
 * that the code runs, and not that data leaves the device. No match means nothing matched the
 * bundled signature list; identifier-renaming obfuscation, reflection, dynamically loaded code,
 * and signatures added upstream after this build all defeat it. Reporting either result as more
 * than that would be a claim the scanner cannot support.
 *
 * <p>Second-degree entries in the upstream dataset are prefixed with {@code ²}: the signature is
 * associated with an organisation but is not itself confirmed as a tracker. Those are surfaced as
 * tentative rather than folded in with confirmed matches.
 */
public final class ScannerCertainty {
    /** Marker the upstream tracker dataset uses for a not-yet-confirmed (second-degree) entry. */
    public static final String TENTATIVE_PREFIX = "²";

    /** How much a single signature match supports the claim that the named component is present. */
    public enum Confidence {
        /**
         * The signature is confirmed upstream and matched at least one class: the code is present.
         */
        CONFIRMED("confirmed", R.string.scanner_confidence_confirmed),
        /**
         * The signature is recorded upstream as second-degree, or matched no class. Presence is
         * suggested, not established.
         */
        TENTATIVE("tentative", R.string.scanner_confidence_tentative);

        @NonNull
        public final String id;
        @StringRes
        public final int labelRes;

        Confidence(@NonNull String id, @StringRes int labelRes) {
            this.id = id;
            this.labelRes = labelRes;
        }
    }

    /** The only detector behind these results; recorded with every exported match. */
    public static final String DETECTION_METHOD = "class-name-signature";

    private ScannerCertainty() {
    }

    /** Whether the dataset records this label as second-degree rather than confirmed. */
    public static boolean isTentative(@NonNull String label) {
        return label.trim().startsWith(TENTATIVE_PREFIX);
    }

    /** The label without the dataset's second-degree marker. */
    @NonNull
    public static String displayLabel(@NonNull String label) {
        String trimmed = label.trim();
        return trimmed.startsWith(TENTATIVE_PREFIX)
                ? trimmed.substring(TENTATIVE_PREFIX.length()).trim()
                : trimmed;
    }

    @NonNull
    public static Confidence confidenceOf(@NonNull SignatureInfo signatureInfo) {
        return confidenceOf(signatureInfo.label, signatureInfo.getCount());
    }

    /**
     * @param matchedClasses Number of classes the signature matched. Zero means the signature was
     *                       carried through the pipeline without any class actually matching it,
     *                       which supports nothing on its own.
     */
    @NonNull
    public static Confidence confidenceOf(@NonNull String label, int matchedClasses) {
        if (matchedClasses <= 0 || isTentative(label)) {
            return Confidence.TENTATIVE;
        }
        return Confidence.CONFIRMED;
    }
}
