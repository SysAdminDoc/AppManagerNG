// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.pm.ComponentInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;

@RunWith(RobolectricTestRunner.class)
public class FinderAdapterTest {
    @Test
    public void formatMatchedExtrasShowsResultEvidence() {
        Context context = RuntimeEnvironment.getApplication();
        FilterOption.TestResult result = new FilterOption.TestResult()
                .setMatchedPermissions(Arrays.asList("android.permission.CAMERA",
                        "android.permission.RECORD_AUDIO"))
                .setMatchedComponents(componentMap("com.example.MainActivity",
                        ComponentsOption.COMPONENT_TYPE_ACTIVITY))
                .setMatchedTrackers(componentMap("com.example.AnalyticsService",
                        ComponentsOption.COMPONENT_TYPE_SERVICE))
                .setMatchedBackups(Arrays.asList(backup("nightly"), backup("before-upgrade")))
                .setMatchedSubjectLines(Arrays.asList("CN=Example", "O=Example Org"));

        assertEquals("Permissions: android.permission.CAMERA, android.permission.RECORD_AUDIO\n"
                        + "Components: com.example.MainActivity\n"
                        + "Trackers: com.example.AnalyticsService\n"
                        + "Backups: nightly, before-upgrade\n"
                        + "Signatures: CN=Example, O=Example Org",
                FinderAdapter.formatMatchedExtras(context, result));
    }

    @Test
    public void formatMatchedExtrasTrimsEmptyDuplicatesAndLongLists() {
        Context context = RuntimeEnvironment.getApplication();
        FilterOption.TestResult result = new FilterOption.TestResult()
                .setMatchedPermissions(Arrays.asList("one", "", "two", "one", "three", "four"));

        assertEquals("Permissions: one, two, three, +1 more",
                FinderAdapter.formatMatchedExtras(context, result));
    }

    @Test
    public void formatMatchedExtrasReturnsNullWithoutEvidence() {
        assertNull(FinderAdapter.formatMatchedExtras(RuntimeEnvironment.getApplication(),
                new FilterOption.TestResult()));
    }

    private static Map<ComponentInfo, Integer> componentMap(String name, int type) {
        Map<ComponentInfo, Integer> components = new LinkedHashMap<>();
        ComponentInfo component = new ComponentInfo();
        component.name = name;
        components.put(component, type);
        return components;
    }

    private static Backup backup(String backupName) {
        Backup backup = new Backup();
        backup.packageName = "com.example";
        backup.backupName = backupName;
        return backup;
    }
}
