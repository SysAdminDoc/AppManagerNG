// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ApkUtilsTest {
    @Test
    public void formatApkFilenameUsesLiteralPlaceholderValues() {
        assertEquals("Price $5\\Beta-com.example-1$2\\3-42-35-23-2026-06-06",
                ApkUtils.formatApkFilename("%label%-%package_name%-%version%-%version_code%-%target_sdk%-%min_sdk%-%datetime%",
                        "Price $5\\Beta",
                        "com.example",
                        "1$2\\3",
                        42,
                        35,
                        23,
                        "2026-06-06"));
    }

    @Test
    public void formatApkFilenameHandlesMissingVersion() {
        assertEquals("Example--1",
                ApkUtils.formatApkFilename("%label%-%version%-%version_code%",
                        "Example",
                        "com.example",
                        null,
                        1,
                        35,
                        23,
                        "2026-06-06"));
    }
}
