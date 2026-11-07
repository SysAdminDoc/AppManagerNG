// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.misc.ListOptions;

public class ProfileAppsListOptions extends ListOptions {
    public static final String TAG = ProfileAppsListOptions.class.getSimpleName();

    private static final LinkedHashMap<Integer, Integer> SORT_ITEMS_MAP = new LinkedHashMap<Integer, Integer>() {{
        put(MainListOptions.SORT_BY_APP_LABEL, R.string.sort_by_app_label);
        put(MainListOptions.SORT_BY_PACKAGE_NAME, R.string.sort_by_package_name);
        put(MainListOptions.SORT_BY_DOMAIN, R.string.sort_by_domain);
        put(MainListOptions.SORT_BY_INSTALLATION_DATE, R.string.sort_by_installation_date);
        put(MainListOptions.SORT_BY_LAST_UPDATE, R.string.sort_by_last_update);
    }};

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getSortIdLocaleMap() {
        return SORT_ITEMS_MAP;
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getFilterFlagLocaleMap() {
        return null;
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getOptionIdLocaleMap() {
        return null;
    }
}
