// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsSearchOwnershipContractTest {
    @Test
    public void settingsActivityOwnsToolbarSearchListener() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/settings/SettingsActivity.java");

        assertTrue(source.contains("UIUtils.setupSearchView(actionBar, new SearchView.OnQueryTextListener()"));
        assertTrue(source.contains("applySettingsSearch(newText);"));
        assertTrue(source.contains("SettingsSearchIndex.get(appContext).search(text);"));
    }

    @Test
    public void mainPreferencesDoesNotOverrideSearchOverlay() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/settings/MainPreferences.java");

        assertFalse("MainPreferences must not replace SettingsActivity's toolbar search listener",
                source.contains("setOnQueryTextListener"));
        assertFalse("MainPreferences must not keep a second in-fragment filtering path",
                source.contains("filterPreferences("));
        assertFalse("The composed overlay empty state is the only no-results state",
                source.contains("settings_search_no_results"));
    }

    @Test
    public void dualPaneSearchProgressStaysInPrimaryPane() throws IOException {
        String activity = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/settings/SettingsActivity.java");
        String layout = readRepoFile("app/src/main/res/layout/activity_settings_dual_pane.xml");

        assertTrue(activity.contains("mSearchProgressIndicator = findViewById(R.id.settings_search_progress);"));
        assertTrue(activity.contains("mSearchProgressIndicator.show();"));
        assertTrue(activity.contains("mSearchProgressIndicator.hide();"));
        assertTrue(layout.contains("android:id=\"@+id/settings_search_progress\""));
        assertTrue(layout.indexOf("android:id=\"@+id/settings_search_progress\"")
                < layout.indexOf("android:id=\"@+id/settings_search_results\""));
    }

    @Test
    public void mainPreferencesObservesAfterViewLifecycleExists() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/settings/MainPreferences.java");
        int createPreferences = source.indexOf("public void onCreatePreferences(");
        int viewCreated = source.indexOf("public void onViewCreated(");
        int lifecycleObserver = source.indexOf("getOperationCompletedLiveData().observe(getViewLifecycleOwner()");

        assertTrue(createPreferences >= 0);
        assertTrue(viewCreated >= 0);
        assertTrue(lifecycleObserver >= 0);
        assertTrue(viewCreated > createPreferences);
        assertTrue(lifecycleObserver > viewCreated);
        assertFalse(source.substring(createPreferences, viewCreated).contains("getViewLifecycleOwner()"));
    }

    private static String readRepoFile(String relativePath) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
