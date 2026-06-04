// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class BackupArchiveFormatContractTest {
    @Test
    public void supportedBackupCompressionTypesRemainTarFamily() {
        Set<String> expectedExtensions = new HashSet<>(Arrays.asList(".tar.gz", ".tar.bz2", ".tar.zst"));

        for (String tarType : BackupUtils.TAR_TYPES) {
            String extension = BackupManager.getExt(tarType);

            assertTrue("Backup extension must remain tar-family: " + extension,
                    extension.startsWith(".tar."));
            assertFalse("Current NG backup formats must not claim SquashFS output: " + extension,
                    extension.contains("sqfs") || extension.contains("squashfs"));
            assertTrue("Unexpected backup extension: " + extension, expectedExtensions.remove(extension));
        }
        assertTrue("Missing backup extension coverage: " + expectedExtensions, expectedExtensions.isEmpty());
    }

    @Test
    public void productionSourcesDoNotDeclareSquashfsBackend() throws Exception {
        Path root = findProjectRoot();
        List<Path> scanRoots = Arrays.asList(
                root.resolve("app/build.gradle"),
                root.resolve("build.gradle"),
                root.resolve("settings.gradle"),
                root.resolve("versions.gradle"),
                root.resolve("app/src/main/java"),
                root.resolve("app/src/main/cpp"),
                root.resolve("libcore/compat/src/main/java"),
                root.resolve("libcore/io/src/main/java"),
                root.resolve("server/src/main/java"));
        List<String> offenders = new ArrayList<>();

        for (Path scanRoot : scanRoots) {
            if (!Files.exists(scanRoot)) {
                continue;
            }
            if (Files.isRegularFile(scanRoot)) {
                collectSquashfsBackendReferences(root, scanRoot, offenders);
                continue;
            }
            try (Stream<Path> stream = Files.walk(scanRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(BackupArchiveFormatContractTest::isSourceLike)
                        .forEach(path -> collectSquashfsBackendReferences(root, path, offenders));
            }
        }

        assertTrue("NG has no SquashFS writer today; add a real header/round-trip test before adding one:\n"
                + String.join("\n", offenders), offenders.isEmpty());
    }

    private static void collectSquashfsBackendReferences(Path root, Path path, List<String> offenders) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String lower = lines.get(i).toLowerCase(Locale.ROOT);
                if (lower.contains("squashfs")
                        || lower.contains("mksquashfs")
                        || lower.contains("unsquashfs")
                        || lower.contains("sqfs_open_image")
                        || lower.contains(".sqfs")
                        || lower.contains(".sqsh")) {
                    offenders.add(root.relativize(path) + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        } catch (IOException e) {
            offenders.add(root.relativize(path) + ": " + e.getMessage());
        }
    }

    private static boolean isSourceLike(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java")
                || name.endsWith(".kt")
                || name.endsWith(".cpp")
                || name.endsWith(".c")
                || name.endsWith(".h")
                || name.endsWith(".gradle")
                || name.endsWith(".gradle.kts")
                || name.endsWith(".xml");
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
