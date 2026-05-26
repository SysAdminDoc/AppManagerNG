// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.content.ComponentName;

import java.util.ArrayList;
import java.util.List;

public class ManifestComponent {
    public static final String TYPE_ACTIVITY = "activity";
    public static final String TYPE_ACTIVITY_ALIAS = "activity-alias";
    public static final String TYPE_SERVICE = "service";
    public static final String TYPE_RECEIVER = "receiver";
    public static final String TYPE_PROVIDER = "provider";

    public final ComponentName cn;
    public final String type;
    public final List<ManifestIntentFilter> intentFilters;

    public ManifestComponent(ComponentName cn) {
        this(cn, null);
    }

    public ManifestComponent(ComponentName cn, String type) {
        this.cn = cn;
        this.type = type;
        intentFilters = new ArrayList<>();
    }
}
