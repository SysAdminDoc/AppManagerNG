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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PremiumShapeContractTest {
    private static final double MIN_NORMAL_TEXT_CONTRAST = 4.5;
    private static final Pattern COLOR_PATTERN = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\">([^<]+)</color>");

    @Test
    public void v2ControlResourcesDoNotUseDeprecatedPillToken() throws IOException {
        Path appDir = findAppProjectDir();
        String resources = read(appDir.resolve("src/main/res/values/dimens-v2.xml"))
                + read(appDir.resolve("src/main/res/values/themes-v2.xml"))
                + read(appDir.resolve("src/main/res/values/styles.xml"))
                + read(appDir.resolve("src/main/res/values/strings.xml"));

        assertTrue(resources.contains("premium_radius_control"));
        assertFalse(resources.contains("premium_radius_pill"));
        assertFalse(resources.contains("ShapeAppearance.AppTheme.V2.Pill"));
        assertFalse(resources.contains("pill-shaped"));
    }

    @Test
    public void v2TextAndSemanticColorPairsMeetContrastFloor() throws IOException {
        Path appDir = findAppProjectDir();

        Map<String, String> lightColors = loadColors(appDir.resolve("src/main/res/values/colors-v2.xml"));
        Map<String, String> darkColors = new HashMap<>(lightColors);
        darkColors.putAll(loadColors(appDir.resolve("src/main/res/values-night/colors-v2.xml")));

        assertThemeContrast(lightColors);
        assertThemeContrast(darkColors);
    }

    @Test
    public void warningStringsDoNotHardcodeRedText() throws IOException {
        Path appDir = findAppProjectDir();
        String strings = read(appDir.resolve("src/main/res/values/strings.xml")).toLowerCase();

        assertFalse(strings.contains("fgcolor=\"#ff0000\""));
        assertFalse(strings.contains("<font fgcolor"));
    }

    private static void assertThemeContrast(Map<String, String> colors) {
        assertContrast(colors, "premium_text_high", "premium_surface_0");
        assertContrast(colors, "premium_text_high", "premium_surface_1");
        assertContrast(colors, "premium_text_high", "premium_surface_2");
        assertContrast(colors, "premium_text_high", "premium_surface_3");
        assertContrast(colors, "premium_text_medium", "premium_surface_0");
        assertContrast(colors, "premium_text_medium", "premium_surface_1");
        assertContrast(colors, "premium_text_medium", "premium_surface_2");
        assertContrast(colors, "premium_text_medium", "premium_surface_3");
        assertContrast(colors, "premium_color_on_primary", "premium_color_primary");
        assertContrast(colors, "premium_color_on_primary_container", "premium_color_primary_container");
        assertContrast(colors, "premium_color_on_secondary", "premium_color_secondary");
        assertContrast(colors, "premium_color_on_secondary_container", "premium_color_secondary_container");
        assertContrast(colors, "premium_success_content", "premium_success_bg");
        assertContrast(colors, "premium_warning_content", "premium_warning_bg");
        assertContrast(colors, "premium_danger_content", "premium_danger_bg");
        assertContrast(colors, "premium_info_content", "premium_info_bg");
    }

    private static void assertContrast(Map<String, String> colors, String foreground, String background) {
        int foregroundColor = resolveColor(colors, foreground, new HashSet<>());
        int backgroundColor = resolveColor(colors, background, new HashSet<>());
        double contrast = contrastRatio(foregroundColor, backgroundColor);

        assertTrue(foreground + " on " + background + " contrast is " + contrast,
                contrast >= MIN_NORMAL_TEXT_CONTRAST);
    }

    private static Map<String, String> loadColors(Path path) throws IOException {
        Map<String, String> colors = new HashMap<>();
        Matcher matcher = COLOR_PATTERN.matcher(read(path));
        while (matcher.find()) {
            colors.put(matcher.group(1), matcher.group(2).trim());
        }
        return colors;
    }

    private static int resolveColor(Map<String, String> colors, String name, Set<String> seen) {
        if (!seen.add(name)) {
            throw new IllegalArgumentException("Circular color reference: " + name);
        }
        String value = colors.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing color: " + name);
        }
        if (value.startsWith("@color/")) {
            return resolveColor(colors, value.substring("@color/".length()), seen);
        }
        if (!value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Unsupported color value for " + name + ": " + value);
        }
        return Integer.parseInt(value.substring(1), 16);
    }

    private static double contrastRatio(int foreground, int background) {
        double foregroundLuminance = relativeLuminance(foreground);
        double backgroundLuminance = relativeLuminance(background);
        double lighter = Math.max(foregroundLuminance, backgroundLuminance);
        double darker = Math.min(foregroundLuminance, backgroundLuminance);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int color) {
        double red = linearizedChannel((color >> 16) & 0xFF);
        double green = linearizedChannel((color >> 8) & 0xFF);
        double blue = linearizedChannel(color & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearizedChannel(int channel) {
        double value = channel / 255d;
        return value <= 0.03928
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
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
