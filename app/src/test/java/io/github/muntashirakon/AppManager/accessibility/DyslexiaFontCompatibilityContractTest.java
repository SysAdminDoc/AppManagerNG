// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Contract for the dyslexia-font compatibility audit
 * ({@code docs/audits/2026-05-26-dyslexia-font-compatibility.md}). NG relies on
 * Android's system-wide font path to deliver OpenDyslexic, Samsung custom
 * fonts, or Magisk font-override mods to body text. These tests fail if a new
 * change forces a theme-level font (which would suppress that path) or removes
 * a known-intentional monospace surface (which would regress the technical
 * readability invariants documented in the audit).
 */
public class DyslexiaFontCompatibilityContractTest {

    private static final List<String> INTENTIONAL_MONOSPACE_LAYOUTS = Arrays.asList(
            "activity_auth_management.xml",
            "activity_batch_ops_results.xml",
            "activity_hex_viewer.xml",
            "activity_term.xml",
            "dialog_keystore_password.xml",
            "dialog_running_app_details.xml",
            "dialog_set_apk_format.xml",
            "dialog_ssaid_info.xml",
            "fragment_mode_of_ops.xml",
            "fragment_log_viewer.xml",
            "fragment_fm.xml",
            "item_app_details_primary.xml",
            "item_logcat.xml",
            "item_running_app.xml",
            "item_text_input_layout_monospace.xml",
            "view_profile_apply_review.xml",
            "window_activity_tracker.xml"
    );

    @Test
    public void themesDoNotPinAGlobalFontFamily() throws IOException {
        Path appDir = findAppProjectDir();
        String themes = read(appDir.resolve("src/main/res/values/themes-v2.xml"));
        String styles = read(appDir.resolve("src/main/res/values/styles.xml"));

        assertNoFontFamilyAttribute("themes-v2.xml", themes);
        assertNoFontFamilyAttribute("styles.xml", styles);
    }

    @Test
    public void recordingWidgetHonorsSystemFont() throws IOException {
        Path appDir = findAppProjectDir();
        String layout = read(appDir.resolve("src/main/res/layout/widget_recording.xml"));

        // The widget subtitle previously forced sans-serif-condensed. If a
        // future change re-introduces android:fontFamily anywhere in this
        // layout, the system font (and any dyslexia replacement) no longer
        // reaches this non-technical surface.
        assertFalse(
                "widget_recording.xml must not pin android:fontFamily; the audit "
                        + "removed the sans-serif-condensed override so dyslexia-targeted system "
                        + "fonts reach this widget",
                layout.contains("android:fontFamily=\""));
    }

    @Test
    public void intentionalMonospaceSurfacesStayMonospace() throws IOException {
        Path layoutDir = findAppProjectDir().resolve("src/main/res/layout");

        for (String layoutName : INTENTIONAL_MONOSPACE_LAYOUTS) {
            String layout = read(layoutDir.resolve(layoutName));
            assertTrue(
                    layoutName + " is a documented technical surface and must keep its "
                            + "android:typeface=\"monospace\" or android:fontFamily=\"monospace\" "
                            + "override; removing it regresses the dyslexia-font compatibility audit "
                            + "boundary because hex / code / log alignment depends on equal-width glyphs",
                    layout.contains("android:fontFamily=\"monospace\"")
                            || layout.contains("android:typeface=\"monospace\""));
        }
    }

    private static void assertNoFontFamilyAttribute(String name, String contents) {
        // android:fontFamily on a <style> or <item> overrides every TextView
        // that inherits the theme - including system-font cascade.
        assertFalse(
                name + " must not declare android:fontFamily at the theme/style "
                        + "layer; doing so suppresses the system font path that OpenDyslexic, "
                        + "Samsung custom fonts, and Magisk font-override modules use",
                contents.contains("android:fontFamily")
                        || contents.contains("\"android:fontFamily\""));
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
