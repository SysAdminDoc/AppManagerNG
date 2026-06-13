// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Contract test: every call site that obtains a {@code BackupItems.BackupItem}
 * via {@code backup.getItem()} must close it, because BackupItem implements
 * {@code Closeable} and encrypted backups hold temp copies and key material
 * until close.
 *
 * Allowed patterns:
 * - {@code try (BackupItems.BackupItem ... = ...getItem())} — try-with-resources
 * - Storing in a field that is closed by an enclosing lifecycle (BackupManager,
 *   BackupItems constructor) — exempted by path
 */
public class BackupItemClosureContractTest {
    private static final Pattern GET_ITEM_CALL = Pattern.compile("\\.getItem\\(\\)");
    private static final Pattern TRY_WITH_RESOURCES = Pattern.compile(
            "try\\s*\\(.*\\.getItem\\(\\)");

    private static final List<String> LIFECYCLE_MANAGED_FILES = List.of(
            "backup/BackupManager.java",
            "backup/BackupItems.java"
    );

    @Test
    public void allGetItemCallSitesAreClosedOrLifecycleManaged() throws Exception {
        Path root = findProjectRoot();
        Path mainJava = root.resolve("app/src/main/java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(mainJava)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> checkFile(root, mainJava, p, offenders));
        }
        Collections.sort(offenders);

        assertTrue("BackupItem.getItem() must be in try-with-resources or lifecycle-managed:\n"
                + String.join("\n", offenders), offenders.isEmpty());
    }

    private void checkFile(Path root, Path sourceRoot, Path file, List<String> offenders) {
        String relativePath = sourceRoot.relativize(file).toString().replace('\\', '/');
        for (String exempted : LIFECYCLE_MANAGED_FILES) {
            if (relativePath.endsWith(exempted)) {
                return;
            }
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher m = GET_ITEM_CALL.matcher(line);
                if (!m.find()) continue;

                int searchStart = Math.max(0, i - 2);
                boolean closed = false;
                for (int j = searchStart; j <= i; j++) {
                    if (TRY_WITH_RESOURCES.matcher(lines.get(j)).find()) {
                        closed = true;
                        break;
                    }
                }
                if (!closed) {
                    offenders.add(root.relativize(file) + ":" + (i + 1) + ": " + line.trim());
                }
            }
        } catch (IOException e) {
            offenders.add(root.relativize(file) + ": " + e.getMessage());
        }
    }

    private static Path findProjectRoot() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("settings.gradle"))
                    && Files.exists(cursor.resolve("app/src/main/AndroidManifest.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate AppManagerNG project root");
    }
}
