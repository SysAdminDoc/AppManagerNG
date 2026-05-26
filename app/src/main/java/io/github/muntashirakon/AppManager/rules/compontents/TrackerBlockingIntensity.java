// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/**
 * Three-tier tracker-blocking policy (NF-07). Lets the user trade off privacy
 * vs. app compatibility:
 *
 * <ul>
 *     <li>{@link #DETECT_ONLY} — the scanner reports trackers but blocks
 *         nothing. Matches the v0.4.x behaviour for users who only want
 *         visibility.</li>
 *     <li>{@link #STANDARD} — block trackers most likely to be hostile to the
 *         user (ad, analytics, identification). Leave crash reporters, push
 *         services, location, and social-login SDKs alone because those often
 *         carry real user-facing features and break apps when blocked.</li>
 *     <li>{@link #STRICT} — block every detected tracker. Matches the v0.4.x
 *         behaviour of the existing "Block trackers" batch op.</li>
 * </ul>
 *
 * <p>The mapping between {@link TrackerCategory} and intensity lives in
 * {@link #shouldBlock(TrackerCategory)} so callers can ask the policy "do I
 * block this tracker?" without re-implementing the rule.</p>
 */
public enum TrackerBlockingIntensity {
    DETECT_ONLY(R.string.tracker_blocking_intensity_detect_only,
            R.string.tracker_blocking_intensity_detect_only_summary),
    STANDARD(R.string.tracker_blocking_intensity_standard,
            R.string.tracker_blocking_intensity_standard_summary),
    STRICT(R.string.tracker_blocking_intensity_strict,
            R.string.tracker_blocking_intensity_strict_summary);

    @StringRes private final int mLabelRes;
    @StringRes private final int mSummaryRes;

    TrackerBlockingIntensity(@StringRes int labelRes, @StringRes int summaryRes) {
        mLabelRes = labelRes;
        mSummaryRes = summaryRes;
    }

    @StringRes
    public int getLabelRes() {
        return mLabelRes;
    }

    @StringRes
    public int getSummaryRes() {
        return mSummaryRes;
    }

    /**
     * True when {@code category} should be blocked under the current intensity.
     * The mapping is deliberately conservative — STANDARD only blocks
     * categories whose user-visible behaviour is almost always nuisance-only.
     */
    public boolean shouldBlock(@NonNull TrackerCategory category) {
        switch (this) {
            case DETECT_ONLY:
                return false;
            case STANDARD:
                switch (category) {
                    case AD:
                    case ANALYTICS:
                    case IDENTIFICATION:
                        return true;
                    case CRASH:
                    case PUSH:
                    case LOCATION:
                    case SOCIAL:
                    case OTHER:
                    default:
                        return false;
                }
            case STRICT:
            default:
                return true;
        }
    }

    /** Parse a stored preference value; unknown / null values default to STRICT. */
    @NonNull
    public static TrackerBlockingIntensity fromPrefValue(@Nullable String value) {
        if (value == null) return STRICT;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return STRICT;
        }
    }
}
