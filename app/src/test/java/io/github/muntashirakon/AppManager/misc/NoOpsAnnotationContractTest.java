// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

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
import java.util.stream.Stream;

public class NoOpsAnnotationContractTest {
    @Test
    public void noOpsMembersWithDirectOpsReferencesDeclareUsed() throws IOException {
        Path root = findProjectRoot();
        Path sourceRoot = root.resolve("app/src/main/java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> collectNoOpsOffenders(root, path, offenders));
        }
        Collections.sort(offenders);

        assertTrue("@NoOps members with direct Ops.* references must declare @NoOps(used = true):\n"
                + String.join("\n", offenders), offenders.isEmpty());
    }

    private static void collectNoOpsOffenders(Path root, Path path, List<String> offenders) {
        if (path.toString().replace('\\', '/').endsWith("/settings/Ops.java")) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.contains("@NoOps") || line.contains("used = true")) {
                    continue;
                }
                int bodyStart = findBodyStart(lines, i);
                if (bodyStart < 0) {
                    continue;
                }
                int bodyEnd = findBodyEnd(lines, bodyStart);
                for (int j = bodyStart; j <= bodyEnd; j++) {
                    if (lines.get(j).contains("Ops.")) {
                        offenders.add(root.relativize(path) + ":" + (i + 1) + " -> " + (j + 1)
                                + ": " + line.trim());
                        break;
                    }
                }
            }
        } catch (IOException e) {
            offenders.add(root.relativize(path) + ": " + e.getMessage());
        }
    }

    private static int findBodyStart(List<String> lines, int annotationIndex) {
        for (int i = annotationIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("{")) {
                return i;
            }
            if (line.contains(";")) {
                return -1;
            }
        }
        return -1;
    }

    private static int findBodyEnd(List<String> lines, int bodyStart) {
        int depth = 0;
        for (int i = bodyStart; i < lines.size(); i++) {
            String line = lines.get(i);
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return lines.size() - 1;
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
