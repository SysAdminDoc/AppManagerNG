// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.ResolveInfo;
import android.os.UserHandleHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * How an app fits a TV, from what its manifest declares. A TV launcher lists only apps with a LEANBACK_LAUNCHER
 * activity, and Google Play offers TVs only apps that don't require a touchscreen.
 */
public final class TvSupport {
    public static final String TAG = TvSupport.class.getSimpleName();

    @IntDef({TOUCH_ONLY, WORKS_WITHOUT_TOUCH, MADE_FOR_TV})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Level {
    }

    /** Needs a touchscreen, which a TV doesn't have. */
    public static final int TOUCH_ONLY = 0;
    /** Says it can run without a touchscreen, though it isn't built for a TV. */
    public static final int WORKS_WITHOUT_TOUCH = 1;
    /** Has a TV launcher entry or uses the leanback (TV) feature. */
    public static final int MADE_FOR_TV = 2;

    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    private TvSupport() {
    }

    /**
     * @param hasLeanbackLauncher Whether an activity of the app handles MAIN with LEANBACK_LAUNCHER
     * @param reqFeatures         {@link android.content.pm.PackageInfo#reqFeatures}
     */
    @Level
    public static int classify(boolean hasLeanbackLauncher, @Nullable FeatureInfo[] reqFeatures) {
        if (hasLeanbackLauncher) {
            return MADE_FOR_TV;
        }
        boolean touchOptional = false;
        if (reqFeatures != null) {
            for (FeatureInfo feature : reqFeatures) {
                if (feature == null || feature.name == null) continue;
                if (FEATURE_LEANBACK.equals(feature.name)) {
                    return MADE_FOR_TV;
                }
                if (FEATURE_TOUCHSCREEN.equals(feature.name)
                        && (feature.flags & FeatureInfo.FLAG_REQUIRED) == 0) {
                    touchOptional = true;
                }
            }
        }
        return touchOptional ? WORKS_WITHOUT_TOUCH : TOUCH_ONLY;
    }

    @StringRes
    public static int labelRes(@Level int level) {
        switch (level) {
            case MADE_FOR_TV:
                return R.string.tv_support_made_for_tv;
            case WORKS_WITHOUT_TOUCH:
                return R.string.tv_support_works_without_touch;
            case TOUCH_ONLY:
            default:
                return R.string.tv_support_touch_only;
        }
    }

    /**
     * The packages a TV launcher would list, for each of these users. The current user is asked through the app's
     * own package manager, other users through the privileged one when it's there; a user it can't read is left out.
     */
    @WorkerThread
    @NonNull
    public static Set<String> leanbackPackages(@NonNull Context context, @NonNull int[] userIds) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> packages = new HashSet<>();
        for (int userId : userIds) {
            for (ResolveInfo info : query(context, intent, userId)) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    packages.add(info.activityInfo.packageName);
                }
            }
        }
        return packages;
    }

    /**
     * Whether an activity of this installed package handles MAIN with LEANBACK_LAUNCHER for the user.
     */
    @WorkerThread
    public static boolean hasLeanbackLauncher(@NonNull Context context, @NonNull String packageName, int userId) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage(packageName);
        return !query(context, intent, userId).isEmpty();
    }

    @WorkerThread
    @NonNull
    private static List<ResolveInfo> query(@NonNull Context context, @NonNull Intent intent, int userId) {
        try {
            if (userId == UserHandleHidden.myUserId()) {
                return context.getPackageManager().queryIntentActivities(intent,
                        PackageManagerCompat.MATCH_DISABLED_COMPONENTS);
            }
            return PackageManagerCompat.queryIntentActivities(context, intent,
                    PackageManagerCompat.MATCH_DISABLED_COMPONENTS, userId);
        } catch (Exception e) {
            Log.w(TAG, "Couldn't list TV activities of user %d", e, userId);
            return Collections.emptyList();
        }
    }
}
