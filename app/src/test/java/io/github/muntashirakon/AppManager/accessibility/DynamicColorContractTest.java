// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Contract for the Material You dynamic-color audit
 * ({@code docs/audits/2026-05-26-material-you-dynamic-color.md}).
 *
 * <p>The audit established three regression-prevention invariants:
 * <ol>
 *   <li>{@code AppearanceUtils.ActivityAppearanceCallback} calls
 *       {@code DynamicColors.applyToActivityIfAvailable}.</li>
 *   <li>{@code AppearanceUtils.getThemedWidgetContext} calls
 *       {@code DynamicColors.wrapContextIfAvailable}.</li>
 *   <li>{@code AppWidgetThemeUtils.getPalette} reads every color through
 *       {@code MaterialColors.getColor(...)} with a non-zero fallback so the
 *       widget palette never returns black on devices without the dynamic-
 *       color overlay.</li>
 * </ol>
 *
 * <p>A future change that drops any of these calls silently disables the
 * wallpaper-derived overlay (activities) or returns an empty palette
 * (widgets). The tests are source-level greps to keep them
 * environment-independent.
 */
public class DynamicColorContractTest {

    @Test
    public void activityCallbackAppliesDynamicColors() throws IOException {
        String source = read(findAppProjectDir().resolve(
                "src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java"));
        assertTrue(
                "AppearanceUtils must call DynamicColors.applyToActivityIfAvailable so the "
                        + "per-activity wallpaper-derived overlay reaches every screen",
                source.contains("DynamicColors.applyToActivityIfAvailable("));
    }

    @Test
    public void widgetContextWrappedWithDynamicColors() throws IOException {
        String source = read(findAppProjectDir().resolve(
                "src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java"));
        assertTrue(
                "AppearanceUtils.getThemedWidgetContext must wrap the context with "
                        + "DynamicColors.wrapContextIfAvailable so widget palettes pick up the overlay",
                source.contains("DynamicColors.wrapContextIfAvailable("));
    }

    @Test
    public void widgetPaletteUsesFallbackResolution() throws IOException {
        String source = read(findAppProjectDir().resolve(
                "src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppWidgetThemeUtils.java"));
        // MaterialColors.getColor(context, attr, fallback) is the three-arg form
        // that guarantees a non-zero default; the two-arg form returns 0 when
        // the attribute is unresolved, which renders as black on widgets.
        assertTrue(
                "AppWidgetThemeUtils must read colors through MaterialColors.getColor(...) "
                        + "with explicit fallbacks so widgets never render black on devices "
                        + "without the dynamic-color overlay",
                source.contains("MaterialColors.getColor("));
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
