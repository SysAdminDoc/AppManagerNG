// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.pm.PermissionInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.details.struct.AppDetailsAppOpItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsComponentItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

/**
 * The texts a search in App Details looks at for one row: everything the row shows that names it, not only the
 * class or permission name. Each is lowercased unless the search is a regex, the one case-sensitive type, the same
 * way the query is.
 */
public final class AppDetailsSearchChoices {
    private AppDetailsSearchChoices() {
    }

    /**
     * A component row: its class name, its label (an activity labelled "Smart Button" is found by "smart"), and its
     * intent filters' actions and categories.
     */
    @NonNull
    public static List<String> forComponent(@NonNull AppDetailsItem<?> item, @AdvancedSearchView.SearchType int type) {
        List<String> choices = new ArrayList<>();
        choices.add(normalize(item.name, type));
        if (item instanceof AppDetailsComponentItem) {
            AppDetailsComponentItem ci = (AppDetailsComponentItem) item;
            addIfPresent(choices, ci.label, type);
            for (String action : ci.getIntentActions()) {
                choices.add(normalize(action, type));
            }
            for (String category : ci.getIntentCategories()) {
                choices.add(normalize(category, type));
            }
        }
        return choices;
    }

    /**
     * A requested permission: its name and the short label under it, like "Music and audio".
     */
    @NonNull
    public static List<String> forUsesPermission(@NonNull AppDetailsPermissionItem item,
                                                 @AdvancedSearchView.SearchType int type) {
        List<String> choices = new ArrayList<>(2);
        choices.add(normalize(item.name, type));
        addIfPresent(choices, item.label, type);
        return choices;
    }

    /**
     * A permission the app declares: its name and the label that heads its row.
     */
    @NonNull
    public static List<String> forDefinedPermission(@NonNull AppDetailsItem<PermissionInfo> item,
                                                    @Nullable CharSequence label,
                                                    @AdvancedSearchView.SearchType int type) {
        List<String> choices = new ArrayList<>(2);
        choices.add(normalize(item.name, type));
        addIfPresent(choices, label, type);
        return choices;
    }

    /**
     * An app op: its name and the permission its row names.
     */
    @NonNull
    public static List<String> forAppOp(@NonNull AppDetailsAppOpItem item, @AdvancedSearchView.SearchType int type) {
        List<String> choices = new ArrayList<>(2);
        choices.add(normalize(item.name, type));
        if (item.permissionInfo != null) {
            addIfPresent(choices, item.permissionInfo.name, type);
        }
        return choices;
    }

    private static void addIfPresent(@NonNull List<String> choices, @Nullable CharSequence text,
                                     @AdvancedSearchView.SearchType int type) {
        if (!TextUtils.isEmpty(text)) {
            choices.add(normalize(text, type));
        }
    }

    @NonNull
    private static String normalize(@NonNull CharSequence s, @AdvancedSearchView.SearchType int type) {
        return type == AdvancedSearchView.SEARCH_TYPE_REGEX ? s.toString() : s.toString().toLowerCase(Locale.ROOT);
    }
}
