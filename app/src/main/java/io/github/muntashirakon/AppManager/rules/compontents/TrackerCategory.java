// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

/**
 * Heuristic category for a known tracker, derived from its vendor name.
 *
 * <p>The bundled tracker dataset (see {@code R.array.tracker_signatures} /
 * {@code R.array.tracker_names}) only carries a vendor name per entry — it does
 * not classify trackers by purpose. A proper classification would require
 * authoring or importing a category table for ~600 entries (see exodus-privacy);
 * until that lands, this enum derives a category from substring matches against
 * the vendor name. The heuristic is deliberately conservative: when no keyword
 * matches, the result is {@link #OTHER}, never a wrong category.
 *
 * <p>Used by the per-component chip in App Details and (eventually) by the
 * aggregate breakdown on the main list status line.
 */
public enum TrackerCategory {
    AD(R.string.tracker_category_ad,
            new String[]{"admob", "applovin", "unity ads", "unity-ads", "tapjoy",
                    "vungle", "ironsource", "chartboost", "adcolony", "mopub",
                    "inmobi", "smaato", "appnext", "moat", "criteo", "mintegral",
                    "pangle", "fyber", " ads", "advert", "adjust", "doubleclick",
                    "verizon media", "yahoo ads", "audiencenetwork"}),
    ANALYTICS(R.string.tracker_category_analytics,
            new String[]{"analytics", "mixpanel", "amplitude", "segment", "flurry",
                    "appsflyer", "kochava", "branch", "umeng", "adobe experience",
                    "adobe target", "matomo", "kissmetrics", "heap", "countly",
                    "tealium", "snowplow", "localytics", "leanplum", "tune",
                    "swrve", "open measurement"}),
    CRASH(R.string.tracker_category_crash,
            new String[]{"crashlytics", "crashalytics", "bugsnag", "sentry",
                    "instabug", "raygun", "rollbar", "datadog rum", "newrelic",
                    "embrace", "splunk mint", "appcenter analytics", "shake",
                    "honeybadger"}),
    PUSH(R.string.tracker_category_push,
            new String[]{"firebase cloud messaging", "fcm", "onesignal", "pushwoosh",
                    "urban airship", "airship", "leanplum push", "pusher",
                    "pushy", "pushbots"}),
    LOCATION(R.string.tracker_category_location,
            new String[]{"foursquare", "factual", "fluxloop", "huq", "predicio",
                    "tamoco", "wireless registry", "x-mode", "xmode", "safegraph",
                    "geo location", "geofencing"}),
    IDENTIFICATION(R.string.tracker_category_identification,
            new String[]{"fingerprint", "didomi", "iovation", "threatmetrix",
                    "incognia", "trustdecision", "device id", "deviceatlas"}),
    SOCIAL(R.string.tracker_category_social,
            new String[]{"facebook login", "facebook share", "facebook social",
                    "twitter login", "google sign-in", "google login",
                    "linkedin sdk", "vkontakte", "wechat sdk", "line sdk"}),
    OTHER(R.string.tracker_category_other, new String[0]);

    @StringRes
    private final int mLabelRes;
    @NonNull
    private final String[] mKeywords;

    TrackerCategory(@StringRes int labelRes, @NonNull String[] keywords) {
        mLabelRes = labelRes;
        mKeywords = keywords;
    }

    @StringRes
    public int getLabelRes() {
        return mLabelRes;
    }

    /**
     * Best-effort category for a tracker vendor name. Substring match against the
     * lower-cased name; first-hit wins (categories are listed in priority order
     * so ad SDKs that also do analytics — e.g. AppsFlyer — classify as their
     * primary purpose).
     *
     * <p>Returns {@link #OTHER} when no keyword matches; never throws.
     */
    @NonNull
    public static TrackerCategory categorize(@Nullable String trackerName) {
        if (trackerName == null || trackerName.isEmpty()) {
            return OTHER;
        }
        String lower = trackerName.toLowerCase(java.util.Locale.ROOT);
        for (TrackerCategory cat : values()) {
            if (cat == OTHER) continue;
            for (String kw : cat.mKeywords) {
                if (lower.contains(kw)) {
                    return cat;
                }
            }
        }
        return OTHER;
    }
}
