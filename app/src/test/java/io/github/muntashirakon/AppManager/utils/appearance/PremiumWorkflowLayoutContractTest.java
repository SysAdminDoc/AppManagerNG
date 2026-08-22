// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PremiumWorkflowLayoutContractTest {
    @Test
    public void labsUsesSectionedRowsInsteadOfActionChips() throws IOException {
        Path appDir = findAppProjectDir();
        String layout = read(appDir.resolve("src/main/res/layout/activity_labs.xml"));
        String activity = read(appDir.resolve(
                "src/main/java/io/github/muntashirakon/AppManager/misc/LabsActivity.java"));

        assertTrue(layout.contains("androidx.appcompat.widget.LinearLayoutCompat"));
        assertFalse(layout.contains("io.github.muntashirakon.widget.FlowLayout"));
        assertTrue(activity.contains("R.layout.item_labs_action"));
        assertTrue(activity.contains("addSection(actionContainer"));
        assertFalse(activity.contains("setBackgroundTintList"));
    }

    @Test
    public void historyKeepsAdvancedFiltersBehindOneCompactControl() throws IOException {
        Path appDir = findAppProjectDir();
        String layout = read(appDir.resolve("src/main/res/layout/activity_op_history.xml"));
        String activity = read(appDir.resolve(
                "src/main/java/io/github/muntashirakon/AppManager/history/ops/OpHistoryActivity.java"));

        assertTrue(layout.contains("@+id/history_filters_button"));
        assertTrue(layout.contains("Widget.AppTheme.TextInputLayout.SearchFlat"));
        assertTrue(layout.contains("android:id=\"@+id/history_filter_scroll\""));
        assertTrue(layout.contains("android:visibility=\"gone\""));
        assertTrue(activity.contains("showHistoryFiltersDialog"));
        assertTrue(activity.contains("setMultiChoiceItems"));
    }

    @Test
    public void denseDiagnosticSurfacesAvoidDecorativeSlabsAndRails() throws IOException {
        Path appDir = findAppProjectDir();
        String memoryHeader = read(appDir.resolve(
                "src/main/res/layout/header_running_apps_memory_info.xml"));
        String logLayout = read(appDir.resolve("src/main/res/layout/item_logcat.xml"));
        String logAdapter = read(appDir.resolve(
                "src/main/java/io/github/muntashirakon/AppManager/logcat/LogViewerRecyclerAdapter.java"));
        String appInfo = read(appDir.resolve("src/main/res/layout/pager_app_info.xml"));

        assertTrue(memoryHeader.contains("Widget.AppTheme.V2.Card.ListRow"));
        assertFalse(memoryHeader.contains("Widget.AppTheme.V2.Card.Header"));
        assertTrue(logLayout.contains("@+id/log_level_indicator"));
        assertFalse(logAdapter.contains("item_semi_transparent"));
        assertFalse(appInfo.contains("HorizontalScrollView"));
        assertTrue(appInfo.contains("app:rowSpacing=\"0dp\""));
    }

    @Test
    public void scannerLoadingRowsAlwaysHaveMeaningfulLabels() throws IOException {
        Path appDir = findAppProjectDir();
        String layout = read(appDir.resolve("src/main/res/layout/fragment_scanner.xml"));

        assertTrue(layout.contains("@string/scanner_section_code"));
        assertTrue(layout.contains("@string/scanner_section_integrity"));
        assertTrue(layout.contains("@string/scanner_loading_classes"));
        assertTrue(layout.contains("@string/scanner_loading_trackers"));
        assertTrue(layout.contains("@string/scanner_loading_libraries"));
        assertTrue(layout.contains("@string/scanner_loading_apk"));
        assertTrue(layout.contains("@string/scanner_loading_signature_summary"));
    }

    @Test
    public void diagnosticRowsAndEmptyStatesStayCompact() throws IOException {
        Path appDir = findAppProjectDir();
        String runningItem = read(appDir.resolve("src/main/res/layout/item_running_app.xml"));
        String emptyState = read(appDir.resolve("src/main/res/layout/view_list_empty_state.xml"));

        assertTrue(runningItem.contains("android:id=\"@+id/process_kind\""));
        assertTrue(runningItem.contains("android:id=\"@+id/selinux_context\""));
        assertTrue(runningItem.contains("android:visibility=\"gone\""));
        assertTrue(emptyState.contains("android:layout_width=\"@dimen/premium_icon_48\""));
        assertFalse(emptyState.contains("main_empty_icon_background"));
    }

    private static Path findAppProjectDir() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("src/main/res"))) {
                return cursor;
            }
            Path appDir = cursor.resolve("app");
            if (Files.isDirectory(appDir.resolve("src/main/res"))) {
                return appDir;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate app project directory");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
