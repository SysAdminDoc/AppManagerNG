// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.ActivityInfo;

import androidx.annotation.NonNull;

public class AppDetailsActivityItem extends AppDetailsComponentItem {
    public boolean canLaunchAssist;

    public AppDetailsActivityItem(@NonNull ActivityInfo componentInfo) {
        super(componentInfo);
    }

    /**
     * Whether the row offers a Launch button, directly or through the assistant. The Activities tab's
     * "Launchable only" filter uses the same test, so the two can't disagree.
     */
    public boolean isLaunchable() {
        return canLaunch || canLaunchAssist;
    }
}
