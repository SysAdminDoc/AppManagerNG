// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.net.Uri;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class SelfUriManager {
    public static final String APP_MANAGER_SCHEME = "app-manager";
    /**
     * Short alias scheme. Mirrors {@code hail://}; documented at {@code docs/intent-api.md}.
     * ROADMAP iter-22 T8 row "{@code am://} URI Scheme — Concrete Schema" [S246].
     */
    public static final String AM_SCHEME = "am";
    public static final String SETTINGS_HOST = "settings";
    public static final String DETAILS_HOST = "details";
    /** Short alias host on {@code am://app/<pkg>}, equivalent to {@code app-manager://details?id=<pkg>}. */
    public static final String APP_HOST = "app";

    @Nullable
    public static UserPackagePair getUserPackagePairFromUri(@Nullable Uri detailsUri) {
        // Accepted formats:
        //   app-manager://details?id=<pkg>&user=<user_id>   (canonical)
        //   am://app/<pkg>?user=<user_id>                   (short alias — iter-22 [S246])
        if (detailsUri == null) {
            return null;
        }
        String scheme = detailsUri.getScheme();
        String host = detailsUri.getHost();
        String pkg;
        if (APP_MANAGER_SCHEME.equals(scheme) && DETAILS_HOST.equals(host)) {
            pkg = detailsUri.getQueryParameter("id");
        } else if (AM_SCHEME.equals(scheme) && APP_HOST.equals(host)) {
            // am://app/<pkg> — package is the first path segment
            java.util.List<String> segments = detailsUri.getPathSegments();
            pkg = (segments != null && !segments.isEmpty()) ? segments.get(0) : null;
        } else {
            return null;
        }
        String userIdStr = detailsUri.getQueryParameter("user");
        if (pkg != null && PackageUtils.validateName(pkg.trim())) {
            int userId;
            if (userIdStr != null && TextUtils.isDigitsOnly(userIdStr)) {
                userId = Integer.parseInt(userIdStr);
            } else userId = UserHandleHidden.myUserId();
            return new UserPackagePair(pkg, userId);
        }
        return null;
    }
}
