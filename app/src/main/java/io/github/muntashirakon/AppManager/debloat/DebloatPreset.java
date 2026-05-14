// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;

public enum DebloatPreset {
    PRIVACY(
            R.string.debloat_preset_privacy,
            R.string.debloat_preset_privacy_summary,
            1
    ) {
        @Override
        public boolean matches(@NonNull DebloatObject object) {
            return object.isInstalled()
                    && isSafeOrReplace(object)
                    && containsAny(object, "adservice", "ad services", "ads", "analytics", "cloud",
                    "federatedcompute", "market", "ondevicepersonalization", "personalization",
                    "privacy", "recommend", "shop", "telemetry", "tracking");
        }
    },
    GAMING(
            R.string.debloat_preset_gaming,
            R.string.debloat_preset_gaming_summary,
            1
    ) {
        @Override
        public boolean matches(@NonNull DebloatObject object) {
            return object.isInstalled()
                    && isSafeOrReplace(object)
                    && containsAny(object, "arcade", "game", "game booster", "game center", "game service", "gaming");
        }
    },
    MINIMAL_OEM(
            R.string.debloat_preset_minimal_oem,
            R.string.debloat_preset_minimal_oem_summary,
            2
    ) {
        @Override
        public boolean matches(@NonNull DebloatObject object) {
            return object.isInstalled()
                    && object.getRemoval() == DebloatObject.REMOVAL_SAFE
                    && ("oem".equals(object.type) || "carrier".equals(object.type))
                    && object.getDependencies().length == 0
                    && object.getRequiredBy().length == 0
                    && object.getWarning() == null;
        }
    };

    @IntDef({ACTION_FREEZE, ACTION_REMOVE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RecommendedAction {
    }

    public static final int ACTION_FREEZE = 1;
    public static final int ACTION_REMOVE = 2;

    @StringRes
    private final int mTitleRes;
    @StringRes
    private final int mSummaryRes;
    @RecommendedAction
    private final int mRecommendedAction;

    DebloatPreset(@StringRes int titleRes, @StringRes int summaryRes,
                  @RecommendedAction int recommendedAction) {
        mTitleRes = titleRes;
        mSummaryRes = summaryRes;
        mRecommendedAction = recommendedAction;
    }

    @StringRes
    public int getTitleRes() {
        return mTitleRes;
    }

    @StringRes
    public int getSummaryRes() {
        return mSummaryRes;
    }

    @RecommendedAction
    public int getRecommendedAction() {
        return mRecommendedAction;
    }

    public abstract boolean matches(@NonNull DebloatObject object);

    private static boolean isSafeOrReplace(@NonNull DebloatObject object) {
        int removal = object.getRemoval();
        return removal == DebloatObject.REMOVAL_SAFE || removal == DebloatObject.REMOVAL_REPLACE;
    }

    private static boolean containsAny(@NonNull DebloatObject object, @NonNull String... needles) {
        StringBuilder haystack = new StringBuilder(object.packageName).append('\n')
                .append(object.getLabelOrPackageName()).append('\n')
                .append(object.getDescription()).append('\n')
                .append(object.type);
        String warning = object.getWarning();
        if (warning != null) {
            haystack.append('\n').append(warning);
        }
        String normalized = haystack.toString().toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
