// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ProductionExceptionLoggingContractTest {
    @Test
    public void productionSourcesNeverPrintStackTracesDirectly() throws IOException {
        Path repoRoot = findRepoRoot();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/"))
                    .forEach(path -> collectOffender(repoRoot, path, offenders));
        }
        assertTrue("Production exceptions must use structured logging: " + offenders,
                offenders.isEmpty());
    }

    private static void collectOffender(Path repoRoot, Path path, List<String> offenders) {
        try {
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            if (source.matches("(?s).*\\.printStackTrace\\s*\\(\\s*\\).*")) {
                offenders.add(repoRoot.relativize(path).toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect " + path, e);
        }
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main"))
                    && Files.isDirectory(cursor.resolve("libcore"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
