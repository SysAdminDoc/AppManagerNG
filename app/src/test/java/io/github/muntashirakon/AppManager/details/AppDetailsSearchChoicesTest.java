// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsActivityItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsAppOpItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsDefinedPermissionItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;

/**
 * Issue 52: a component row shows its label, but the search only looked at the class name, so "Smart" never found
 * the row labelled "Smart Button". The permission tabs had the same gap for the text their rows show.
 */
@RunWith(RobolectricTestRunner.class)
public class AppDetailsSearchChoicesTest {
    private static final int CONTAINS = AdvancedSearchView.SEARCH_TYPE_CONTAINS;
    private static final int PREFIX = AdvancedSearchView.SEARCH_TYPE_PREFIX;
    private static final int REGEX = AdvancedSearchView.SEARCH_TYPE_REGEX;
    /** AppOpsManager.OP_CAMERA */
    private static final int OP_CAMERA = 26;

    @Test
    public void aComponentIsFoundByEveryWordOfItsLabelInAnyCase() {
        AppDetailsActivityItem item = activity("com.example.ui.EntryPoint", "Smart Button");

        for (String typed : new String[]{"Smart", "button", "BUTTON", "smart button", "t b"}) {
            assertEquals(typed, 1, findComponents(typed, CONTAINS, item).size());
        }
        assertEquals(1, findComponents("smart", PREFIX, item).size());
        assertEquals(0, findComponents("camera", CONTAINS, item).size());
    }

    @Test
    public void theClassNameStillMatches() {
        AppDetailsActivityItem item = activity("com.example.ui.EntryPoint", "Smart Button");

        assertEquals(1, findComponents("EntryPoint", CONTAINS, item).size());
        assertEquals(1, findComponents("com.example", PREFIX, item).size());
    }

    @Test
    public void aRegexSearchReadsTheLabelAsWritten() {
        AppDetailsActivityItem item = activity("com.example.ui.EntryPoint", "Smart Button");

        assertEquals(1, findComponents("^Smart B", REGEX, item).size());
        assertEquals(0, findComponents("^smart b", REGEX, item).size());
    }

    @Test
    public void aComponentWithoutALabelIsSearchedByName() {
        AppDetailsActivityItem item = activity("com.example.ui.EntryPoint", null);

        assertEquals(1, findComponents("entry", CONTAINS, item).size());
        assertEquals(0, findComponents("smart", CONTAINS, item).size());
    }

    @Test
    public void aPermissionIsFoundByTheShortLabelItsRowShows() {
        PermissionInfo info = new PermissionInfo();
        info.name = "android.permission.READ_MEDIA_AUDIO";
        AppDetailsPermissionItem item = new AppDetailsPermissionItem(info,
                new ReadOnlyPermission(info.name, true, AppOpsManagerCompat.OP_NONE, false, 0), 0);
        item.name = info.name;
        item.label = "Music and audio";

        List<AppDetailsPermissionItem> found = AdvancedSearchView.matches(query("Music", CONTAINS),
                Collections.singletonList(item), (AdvancedSearchView.ChoicesGenerator<AppDetailsPermissionItem>)
                        it -> AppDetailsSearchChoices.forUsesPermission(it, CONTAINS), CONTAINS);

        assertEquals(1, found.size());
    }

    @Test
    public void aDeclaredPermissionIsFoundByItsLabel() {
        PermissionInfo info = new PermissionInfo();
        info.name = "com.example.permission.C2D";
        AppDetailsDefinedPermissionItem item = new AppDetailsDefinedPermissionItem(info, false);

        List<AppDetailsDefinedPermissionItem> found = AdvancedSearchView.matches(query("Cloud", CONTAINS),
                Collections.singletonList(item), (AdvancedSearchView.ChoicesGenerator<AppDetailsDefinedPermissionItem>)
                        it -> AppDetailsSearchChoices.forDefinedPermission(it, "Receive cloud messages", CONTAINS),
                CONTAINS);

        assertEquals(1, found.size());
    }

    @Test
    public void anAppOpIsFoundByThePermissionItsRowNames() {
        PermissionInfo info = new PermissionInfo();
        info.name = "android.permission.CAMERA";
        info.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
        AppDetailsAppOpItem item = new AppDetailsAppOpItem(OP_CAMERA, info, true, 0, true);

        List<AppDetailsAppOpItem> found = AdvancedSearchView.matches(query("permission.camera", CONTAINS),
                Collections.singletonList(item), (AdvancedSearchView.ChoicesGenerator<AppDetailsAppOpItem>)
                        it -> AppDetailsSearchChoices.forAppOp(it, CONTAINS), CONTAINS);

        assertEquals(1, found.size());
        assertTrue(AppDetailsSearchChoices.forAppOp(item, CONTAINS).contains(item.name.toLowerCase(Locale.ROOT)));
    }

    @NonNull
    private static List<AppDetailsItem<ComponentInfo>> findComponents(@NonNull String typed, int type,
                                                                      @NonNull AppDetailsActivityItem item) {
        List<AppDetailsItem<ComponentInfo>> items = Collections.singletonList(item);
        return AdvancedSearchView.matches(query(typed, type), items,
                (AdvancedSearchView.ChoicesGenerator<AppDetailsItem<ComponentInfo>>) it ->
                        AppDetailsSearchChoices.forComponent(it, type), type);
    }

    /**
     * What the view model stores: everything but a regex is lowercased once.
     */
    @NonNull
    private static String query(@NonNull String typed, int type) {
        return type == REGEX ? typed : typed.toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static AppDetailsActivityItem activity(@NonNull String name, @Nullable CharSequence label) {
        ActivityInfo info = new ActivityInfo();
        info.name = name;
        info.packageName = "com.example";
        info.applicationInfo = new ApplicationInfo();
        AppDetailsActivityItem item = new AppDetailsActivityItem(info);
        item.label = label;
        return item;
    }
}
