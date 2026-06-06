// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.filters.options.PackageNameOption;
import io.github.muntashirakon.AppManager.profiles.struct.AppsFilterProfile;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;

final class ProfileMembershipFilter {
    private enum Mode {
        DISABLED,
        MATCH_ALL,
        MATCH_NONE,
        FILTER
    }

    @NonNull
    private final Mode mMode;
    @NonNull
    private final FilterItem mFilterItem;
    private final boolean mInverse;

    private ProfileMembershipFilter(@NonNull Mode mode, @NonNull FilterItem filterItem, boolean inverse) {
        mMode = mode;
        mFilterItem = filterItem;
        mInverse = inverse;
    }

    @NonNull
    static ProfileMembershipFilter none() {
        return new ProfileMembershipFilter(Mode.DISABLED, new FilterItem(), false);
    }

    @NonNull
    static ProfileMembershipFilter fromProfile(@NonNull BaseProfile profile, boolean inverse) {
        if (profile instanceof AppsProfile) {
            return fromPackageNames(((AppsProfile) profile).packages, inverse);
        }
        if (profile instanceof AppsFilterProfile) {
            return fromFilterItem(((AppsFilterProfile) profile).getFilterItem(), inverse);
        }
        return none();
    }

    @NonNull
    static ProfileMembershipFilter fromPackageNames(@NonNull String[] packageNames, boolean inverse) {
        if (packageNames.length == 0) {
            return new ProfileMembershipFilter(inverse ? Mode.MATCH_ALL : Mode.MATCH_NONE, new FilterItem(), false);
        }
        PackageNameOption option = new PackageNameOption();
        option.setKeyValue("eq_any", String.join("\n", packageNames));
        FilterItem filterItem = new FilterItem();
        filterItem.addFilterOption(option);
        return fromFilterItem(filterItem, inverse);
    }

    @NonNull
    static ProfileMembershipFilter fromFilterItem(@NonNull FilterItem filterItem, boolean inverse) {
        return new ProfileMembershipFilter(Mode.FILTER, filterItem, inverse);
    }

    boolean isFiltering() {
        return mMode != Mode.DISABLED;
    }

    boolean matches(@NonNull IFilterableAppInfo item) {
        switch (mMode) {
            case DISABLED:
            case MATCH_ALL:
                return true;
            case MATCH_NONE:
                return false;
            case FILTER:
            default:
                boolean matched = mFilterItem.matches(item);
                return mInverse ? !matched : matched;
        }
    }

    int getTimesUsageInfoUsed() {
        return mMode == Mode.FILTER ? mFilterItem.getTimesUsageInfoUsed() : 0;
    }

    int getTimesRunningOptionUsed() {
        return mMode == Mode.FILTER ? mFilterItem.getTimesRunningOptionUsed() : 0;
    }
}
