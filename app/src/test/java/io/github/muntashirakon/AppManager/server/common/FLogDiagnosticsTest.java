// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertFalse;
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

public class FLogDiagnosticsTest {
    @Test
    public void throwableDiagnosticsAreBoundedAndRedacted() {
        RuntimeException failure = new RuntimeException(
                "token:123e4567-e89b-12d3-a456-426614174000 auth=abcdef password=secret");
        StackTraceElement[] frames = new StackTraceElement[80];
        for (int i = 0; i < frames.length; ++i) {
            frames[i] = new StackTraceElement("pkg.Frame" + i, "method", "Frame" + i + ".java", i + 1);
        }
        failure.setStackTrace(frames);

        String diagnostic = FLog.formatThrowable(failure);

        assertTrue(diagnostic.contains("token:" + "<redacted>"));
        assertTrue(diagnostic.contains("auth=" + "<redacted>"));
        assertTrue(diagnostic.contains("password=" + "<redacted>"));
        assertFalse(diagnostic.contains("123e4567-e89b-12d3-a456-426614174000"));
        assertFalse(diagnostic.contains("abcdef"));
        assertFalse(diagnostic.contains("password=secret"));
        assertTrue(diagnostic.contains("pkg.Frame31.method"));
        assertFalse(diagnostic.contains("pkg.Frame32.method"));
        assertTrue(diagnostic.contains("more frames"));
        assertTrue(diagnostic.length() <= FLog.MAX_LOG_CHARS + FLog.DIAGNOSTIC_TRUNCATED_MARKER.length());
    }

    @Test
    public void plainDiagnosticsAreBoundedAndRedacted() {
        StringBuilder noisy = new StringBuilder("token=secret-value ");
        for (int i = 0; i < FLog.MAX_LOG_CHARS + 512; ++i) {
            noisy.append('x');
        }

        String diagnostic = FLog.sanitizeAndLimit(noisy.toString());

        assertTrue(diagnostic.startsWith("token=" + "<redacted>"));
        assertFalse(diagnostic.contains("secret-value"));
        assertTrue(diagnostic.contains(FLog.DIAGNOSTIC_TRUNCATED_MARKER));
        assertTrue(diagnostic.length() <= FLog.MAX_LOG_CHARS + FLog.DIAGNOSTIC_TRUNCATED_MARKER.length());
    }

    @Test
    public void privilegedServerSourcesDoNotPrintStackTracesDirectly() throws IOException {
        Path repoRoot = findRepoRoot();
        List<Path> sourceRoots = new ArrayList<>();
        sourceRoots.add(repoRoot.resolve("server/src/main/java"));
        sourceRoots.add(repoRoot.resolve("libserver/src/main/java"));

        List<String> offenders = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                String source = read(path);
                                if (source.contains("printStackTrace(")) {
                                    offenders.add(repoRoot.relativize(path).toString());
                                }
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
            }
        }

        assertTrue("Privileged server diagnostics must route through bounded FLog, not direct stderr: "
                + offenders, offenders.isEmpty());
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

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
