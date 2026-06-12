// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ActionLabelAccessibilityContractTest {
    @Test
    public void trackerWindowControlsExposeLabelsAndTouchTargets() throws IOException {
        String layout = read(findAppProjectDir().resolve("src/main/res/layout/window_activity_tracker.xml"));

        assertControlContentDescription(layout, "icon", "@string/tracker_window_expand");
        assertControlContentDescription(layout, "drag", "@string/tracker_window_move");
        assertControlContentDescription(layout, "info", "@string/tracker_window_open_app_info");
        assertControlContentDescription(layout, "mini", "@string/tracker_window_minimize");
        assertControlContentDescription(layout, "action_play_pause", "@string/tracker_window_pause");
        assertControlContentDescription(layout, "closeButton", "@string/tracker_window_close");
        assertIconButtonTouchTarget(layout, "drag");
        assertIconButtonTouchTarget(layout, "info");
        assertIconButtonTouchTarget(layout, "mini");
        assertIconButtonTouchTarget(layout, "action_play_pause");
        assertIconButtonTouchTarget(layout, "closeButton");
    }

    @Test
    public void trackerWindowPlayPauseLabelChangesWithState() throws IOException {
        String source = read(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/accessibility/activity/TrackerWindow.java"));

        assertTrue("TrackerWindow should update the pause/resume label when toggled",
                source.contains("updatePlayPauseButton();"));
        assertTrue("TrackerWindow should announce the paused-state resume action",
                source.contains("R.string.tracker_window_resume"));
        assertTrue("TrackerWindow should announce the running-state pause action",
                source.contains("R.string.tracker_window_pause"));
        assertTrue("TrackerWindow should update the button content description",
                source.contains("mPlayPauseButton.setContentDescription"));
    }

    @Test
    public void bloatwareDetailsInfoActionsExposeLabelsAndTouchTargets() throws IOException {
        Path appDir = findAppProjectDir();
        String dialogLayout = read(appDir.resolve("src/main/res/layout/dialog_bloatware_details.xml"));
        String itemLayout = read(appDir.resolve("src/main/res/layout/item_bloatware_details.xml"));
        String source = read(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/debloat/BloatwareDetailsDialog.java"));

        assertControlContentDescription(dialogLayout, "info", "@string/app_info");
        assertIconButtonTouchTarget(dialogLayout, "info");
        assertControlContentDescription(itemLayout, "info", "@string/app_info");
        assertIconButtonTouchTarget(itemLayout, "info");
        assertTrue("Installed debloat suggestions should announce App info",
                source.contains("R.string.app_info"));
        assertTrue("Market debloat suggestions should announce the app store action",
                source.contains("R.string.open_in_app_store"));
    }

    @Test
    public void audioPlayerControlsExposeLabelsTouchTargetsAndDynamicStateLabels() throws IOException {
        String layout = read(findAppProjectDir().resolve("src/main/res/layout/dialog_audio_player.xml"));
        String source = read(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/viewer/audio/AudioPlayerDialogFragment.java"));

        assertControlContentDescription(layout, "action_rewind", "@string/audio_player_rewind_10");
        assertControlContentDescription(layout, "action_play_pause", "@string/audio_player_play");
        assertControlContentDescription(layout, "action_forward", "@string/audio_player_forward_10");
        assertControlContentDescription(layout, "action_repeat", "@string/audio_player_repeat_off");
        assertImageButtonTouchTarget(layout, "action_rewind");
        assertImageButtonTouchTarget(layout, "action_play_pause");
        assertImageButtonTouchTarget(layout, "action_forward");
        assertImageButtonTouchTarget(layout, "action_repeat");
        assertTrue("Audio player should update play/pause/replay labels with icon state",
                source.contains("setPlayPauseButtonState"));
        assertTrue("Audio player should update repeat labels with repeat mode",
                source.contains("setRepeatButtonState"));
        assertTrue("Audio player should not refresh progress at a 10 ms cadence",
                source.contains("PROGRESS_UPDATE_INTERVAL_MS = 250L"));
    }

    private static void assertControlContentDescription(String layout,
                                                        String viewId,
                                                        String expectedDescription) {
        assertControlAttribute(layout, viewId,
                "android:contentDescription=\"" + expectedDescription + "\"");
    }

    private static void assertIconButtonTouchTarget(String layout, String viewId) {
        assertControlAttribute(layout, viewId, "android:layout_width=\"48dp\"");
        assertControlAttribute(layout, viewId, "android:layout_height=\"48dp\"");
        assertControlAttribute(layout, viewId, "app:iconSize=\"24dp\"");
    }

    private static void assertImageButtonTouchTarget(String layout, String viewId) {
        assertControlAttribute(layout, viewId, "android:layout_width=\"48dp\"");
        assertControlAttribute(layout, viewId, "android:layout_height=\"48dp\"");
    }

    private static void assertControlAttribute(String layout,
                                               String viewId,
                                               String expectedAttribute) {
        int start = findViewStart(layout, viewId);
        int end = layout.indexOf("/>", start);
        assertTrue("Missing self-closing view block for " + viewId, end > start);
        String block = layout.substring(start, end);
        assertTrue(viewId + " should use " + expectedAttribute,
                block.contains(expectedAttribute));
    }

    private static int findViewStart(String layout, String viewId) {
        String appIdMarker = "android:id=\"@+id/" + viewId + "\"";
        int start = layout.indexOf(appIdMarker);
        if (start >= 0) {
            return start;
        }
        String androidIdMarker = "android:id=\"@android:id/" + viewId + "\"";
        start = layout.indexOf(androidIdMarker);
        assertTrue("Missing view id " + viewId, start >= 0);
        return start;
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/res"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static Path findAppProjectDir() {
        return findRepoRoot().resolve("app");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
