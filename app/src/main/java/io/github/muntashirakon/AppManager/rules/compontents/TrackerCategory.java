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
 *
 * <p>Order of declaration is the priority order for {@link #categorize}. The
 * priority is: AD &gt; ANALYTICS &gt; CRASH &gt; PUSH &gt; LOCATION &gt;
 * IDENTIFICATION &gt; SOCIAL. Many ad SDKs also do analytics (AppsFlyer,
 * Adjust, Branch, AppLovin); they classify as their primary purpose. Crash
 * reporters that also do basic analytics (Bugsnag, Sentry) still classify as
 * crash. Push services that also report engagement (Braze, OneSignal) classify
 * as push. The ordering is the result of triaging real overlaps in the bundled
 * dataset; changing the order will reshuffle real apps.
 */
public enum TrackerCategory {
    AD(R.string.tracker_category_ad,
            new String[]{
                    // Direct ad / mediation SDKs.
                    "admob", "applovin", "unity ads", "unity-ads", "tapjoy",
                    "vungle", "ironsource", "chartboost", "adcolony", "mopub",
                    "inmobi", "smaato", "appnext", "moat", "criteo", "mintegral",
                    "pangle", "fyber", "doubleclick", "doubleverify",
                    "verizon media", "yahoo ads", "audiencenetwork",
                    "audience network", "audienceon", "facebook ads",
                    "facebook audience", "amazon advertisement", "amazon ads",
                    "appbrain", "appmonet", "appnexus", "amobee", "appodeal",
                    "appvador", "adsmogo", "alimama", "altamob", "ad generation",
                    "ad4screen", "adcash", "adcenix", "adbrix", "adfurikun",
                    "adfit", "adform", "adgatemedia", "adgem", "adlocus",
                    "admarvel", "admuing", "admitad", "admixer", "admost",
                    "adot", "ad(x)", "adlib", "amoad", "adincube", "adbuddiz",
                    "adfalcon", "adtiming", "adtrial", "adswizz", "adsense",
                    "adjoe", "aarki", "aerserv", "airpush", "anvato", "audience studio",
                    "auditude", "axonix", "baidu mobile ads", "bidmachine",
                    "buzzad", "cauly", "cheetah ads", "cloudmobi", "conversant",
                    "duapps", "freewheel", "geniee", "glispa", "gom factory adpie",
                    "heyzap", "hyprmx", "iab open measurement", "open measurement",
                    "millennial media", "ogury", "pubmatic", "pubnative",
                    "remerge", "rubicon", "smartadserver", "smartad",
                    "startapp", "supersonic", "tapsell", "huawei ads",
                    "yandex ads", "yandex mobile ads", "liftoff", "kidoz",
                    "google ad", "googleads", "google ima", " ads", "ads sdk",
                    "advertis", "ad mediation", "ad network", "ad sdk",
                    "amazon mobile associates", "amazon associates"}),
    ANALYTICS(R.string.tracker_category_analytics,
            new String[]{
                    "analytics", "mixpanel", "amplitude", "segment", "flurry",
                    "appsflyer", "kochava", "branch", "umeng", "adobe experience",
                    "adobe target", "adobe campaign", "adobe", "matomo",
                    "kissmetrics", "heap", "countly", "tealium", "snowplow",
                    "localytics", "leanplum", "tune", "swrve", "abtasty",
                    "ab tasty", "atinternet", "at internet", "alooma",
                    "alphonso", "alohalytics", "appmetrica", "appcelerator",
                    "appdynamics", "applause", "apptentive", "apptimize",
                    "apsalar", "blueconic", "braze", "appboy", "chartbeat",
                    "clevertap", "comscore", "cooladata", "demdex", "dynamic yield",
                    "dynatrace", "ensighten", "eulerian", "exponea",
                    "followanalytics", "gameanalytics", "gemius", "gigya",
                    "google tag manager", "tag manager", "ibm digital analytics",
                    "ibm mobile marketing", "infonline", "youbora", "npaw",
                    "krux", "mparticle", "appsee", "smartlook", "fullstory",
                    "logrocket", "hotjar", "pendo", "yandex metrica",
                    "yandex appmetrica", "yandex.metrica", "amap", "anagog",
                    "akamai map", "appcues", "amptools", "baidu mobile stat",
                    "baidu analytics", "baidu apps", "baidu appx", "tracking",
                    "tracker"}),
    CRASH(R.string.tracker_category_crash,
            new String[]{
                    "crashlytics", "crashalytics", "bugsnag", "sentry",
                    "instabug", "raygun", "rollbar", "datadog rum", "newrelic",
                    "new relic", "embrace", "splunk mint", "splunk", "shake",
                    "honeybadger", "bugsense", "bugfender", "bugly", "bugsee",
                    "hockeyapp", "apteligent", "crittercism", "fabric",
                    "appcenter analytics", "appcenter crashes", "appcenter"}),
    PUSH(R.string.tracker_category_push,
            new String[]{
                    "firebase cloud messaging", "fcm", "onesignal", "pushwoosh",
                    "urban airship", "airship", "leanplum push", "pusher",
                    "pushy", "pushbots", "wonderpush", "batch", "carnival",
                    "jpush", "jiguang", "mipush", "mi push", "xiaomi push",
                    "huawei push", "baidu push", "tencent push", "vivo push",
                    "oppo push", "meizu push", "exact target",
                    "salesforce mobile push", "kumulos", "notificationhub"}),
    LOCATION(R.string.tracker_category_location,
            new String[]{
                    "foursquare", "factual", "fluxloop", "huq", "predicio",
                    "tamoco", "wireless registry", "x-mode", "xmode", "safegraph",
                    "geo location", "geofencing", "anagog location", "altbeacon",
                    "areametrics", "beaconsinspace", "fysical", "beintoo",
                    "blesh", "bluecats", "blueshift", "carto", "nutiteq",
                    "colocator", "coulus", "cuebiq", "estimote", "esri arcgis",
                    "footmarks", "gimbal", "glympse", "herow", "hypertrack",
                    "openstreetmap", "mapbox", "skyhook", "placer", "placeiq",
                    "sense360", "ground truth", "sense networks",
                    "baidu map", "baidu location", "baidu navigation",
                    "tom tom", "tomtom", "geomarketing", "tencent map",
                    "tencent location", "amap location", "near.co",
                    "groundtruth", "near"}),
    IDENTIFICATION(R.string.tracker_category_identification,
            new String[]{
                    "fingerprint", "didomi", "iovation", "threatmetrix",
                    "incognia", "trustdecision", "device id", "deviceatlas",
                    "acuant", "ipqualityscore", "perimeterx", "sift science",
                    "onfido", "jumio", "mitek", "veridiumid", "shape security"}),
    SOCIAL(R.string.tracker_category_social,
            new String[]{
                    "facebook login", "facebook share", "facebook social",
                    "facebook places", "facebook notifications",
                    "twitter login", "google sign-in", "google login",
                    "google+", "google plus", "linkedin sdk", "vkontakte",
                    "wechat sdk", "line sdk", "weibo", "qq sdk", "tencent qq",
                    "kakao sdk", "kakaotalk", "naver sdk", "snapchat sdk",
                    "tiktok sdk", "instagram sdk", "giphy", "accountkit",
                    "account kit"}),
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
