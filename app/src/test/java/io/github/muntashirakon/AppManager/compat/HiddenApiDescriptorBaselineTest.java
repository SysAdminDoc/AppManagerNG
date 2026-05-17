// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HiddenApiDescriptorBaselineTest {
    private static final String BASELINE_PATH =
            "app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json";
    private static final String HIDDEN_API_ROOT = "hiddenapi/src/main/java";
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");

    @Test
    public void baselineCoversEveryHiddenApiSourceFile() throws Exception {
        File root = findRepoRoot();
        JSONObject baseline = new JSONObject(read(new File(root, BASELINE_PATH)));
        JSONArray classes = baseline.getJSONArray("classes");

        assertEquals(1, baseline.getInt("schema"));
        assertEquals(HIDDEN_API_ROOT, baseline.getString("sourceRoot"));
        assertTrue("hidden API descriptor should cover the full stub tree", classes.length() >= 100);
        assertTrue("hidden API descriptor should include runtime members", countMembers(classes) >= 700);

        Set<String> expectedSourceFiles = collectHiddenApiSourceFiles(root);
        Set<String> actualSourceFiles = new HashSet<>();
        Set<String> seenStubs = new HashSet<>();
        for (int i = 0; i < classes.length(); ++i) {
            JSONObject item = classes.getJSONObject(i);
            assertRequiredString(item, "sourceFile");
            assertRequiredString(item, "stub");
            assertRequiredString(item, "runtime");
            assertTrue("minSdk must be >= 1", item.getInt("minSdk") >= 1);
            assertNotNull(item.getJSONArray("members"));
            assertTrue("duplicate stub descriptor: " + item.getString("stub"),
                    seenStubs.add(item.getString("stub")));
            actualSourceFiles.add(item.getString("sourceFile"));
        }

        assertEquals("Regenerate the baseline with scripts/generate-hidden-api-baseline.ps1",
                expectedSourceFiles, actualSourceFiles);
    }

    private static File findRepoRoot() {
        File cursor = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (cursor != null) {
            if (new File(cursor, "settings.gradle").isFile()
                    && new File(cursor, "hiddenapi").isDirectory()) {
                return cursor;
            }
            cursor = cursor.getParentFile();
        }
        throw new IllegalStateException("Could not locate repository root from user.dir");
    }

    private static Set<String> collectHiddenApiSourceFiles(File root) throws IOException {
        Path hiddenApiRoot = root.toPath().resolve(HIDDEN_API_ROOT);
        try (java.util.stream.Stream<Path> stream = Files.walk(hiddenApiRoot)) {
            List<Path> files = stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            Set<String> sourceFiles = new HashSet<>();
            for (Path file : files) {
                String relative = root.toPath().relativize(file).toString().replace(File.separatorChar, '/');
                if ("hiddenapi/src/main/java/misc/utils/HiddenUtil.java".equals(relative)) {
                    continue;
                }
                String text = read(file.toFile());
                Matcher matcher = PACKAGE_PATTERN.matcher(text);
                if (!matcher.find() || "android.annotation".equals(matcher.group(1))) {
                    continue;
                }
                sourceFiles.add(relative);
            }
            return sourceFiles;
        }
    }

    private static int countMembers(JSONArray classes) throws Exception {
        int total = 0;
        for (int i = 0; i < classes.length(); ++i) {
            total += classes.getJSONObject(i).getJSONArray("members").length();
        }
        return total;
    }

    private static void assertRequiredString(JSONObject item, String name) throws Exception {
        assertTrue("missing " + name, item.has(name));
        assertFalse(name + " must not be empty", item.getString(name).trim().isEmpty());
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
