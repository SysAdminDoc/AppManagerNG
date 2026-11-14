// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SettingsKeyPathTest {
    @Test
    public void aRestoredPathSurvivesTheNextRecreateWhenThePageSavesBeforeThePushIsReported() {
        SettingsKeyPath path = new SettingsKeyPath();
        path.restore(Arrays.asList("appearance", "language_files"));

        // The restored Appearance page clicks Language files as it starts, which is before the
        // fragment manager reports Appearance's own push.
        path.save(0, "appearance");
        path.save(1, "language_files");
        path.onBackStackChanged(1);
        path.onBackStackChanged(2);

        assertEquals(Arrays.asList("appearance", "language_files"), path.toList());
    }

    @Test
    public void aRestoredPathSurvivesTheNextRecreateWhenThePushIsReportedFirst() {
        SettingsKeyPath path = new SettingsKeyPath();
        path.restore(Arrays.asList("appearance", "language_files"));

        path.save(0, "appearance");
        path.onBackStackChanged(1);
        // Saved now, a second recreate before Language files opens still knows where to go.
        assertEquals(Arrays.asList("appearance", "language_files"), path.toList());

        path.save(1, "language_files");
        path.onBackStackChanged(2);
        assertEquals(Arrays.asList("appearance", "language_files"), path.toList());
    }

    @Test
    public void goingBackDropsTheClosedPages() {
        SettingsKeyPath path = new SettingsKeyPath();
        path.save(0, "appearance");
        path.onBackStackChanged(1);
        path.save(1, "language_files");
        path.onBackStackChanged(2);

        path.onBackStackChanged(1);
        assertEquals(Collections.singletonList("appearance"), path.toList());
        assertNull(path.get(1));

        path.onBackStackChanged(0);
        assertEquals(Collections.emptyList(), path.toList());
    }

    @Test
    public void aDifferentPageStartsANewPath() {
        SettingsKeyPath path = new SettingsKeyPath();
        path.restore(Arrays.asList("appearance", "language_files"));

        path.save(0, "privacy");

        assertEquals(Collections.singletonList("privacy"), path.toList());
    }

    @Test
    public void tappingTheSamePageAgainStartsANewPath() {
        SettingsKeyPath path = new SettingsKeyPath();
        path.save(0, "appearance");
        path.save(1, "language_files");

        path.pick(0, "appearance");

        assertEquals(Collections.singletonList("appearance"), path.toList());
    }

    @Test
    public void tappingPastTheEndLeavesTheSkippedLevelsEmpty() {
        SettingsKeyPath path = new SettingsKeyPath();

        path.pick(1, "installer");

        assertEquals(Arrays.asList(null, "installer"), path.toList());
    }

    @Test
    public void savingPastTheEndLeavesTheSkippedLevelsEmpty() {
        SettingsKeyPath path = new SettingsKeyPath();

        path.save(2, "installer");

        assertEquals(Arrays.asList(null, null, "installer"), path.toList());
        assertNull(path.get(0));
        assertNull(path.get(5));
        assertNull(path.get(-1));
    }
}
