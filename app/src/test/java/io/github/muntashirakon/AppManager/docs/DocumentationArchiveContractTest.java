// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.docs;

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
import java.util.regex.Pattern;

public class DocumentationArchiveContractTest {
    private static final Pattern UNPUBLISHED_ARCHIVE_LINK = Pattern.compile(
            "\\]\\([^)]*(?:docs/(?:archive|roadmap/archive|patch-references)|archive/)[^)]*\\)");

    @Test
    public void publishedDocumentationDoesNotLinkToLocalArchives() throws IOException {
        Path repoRoot = findRepoRoot();
        List<String> publishedDocs = Arrays.asList(
                "README.md",
                "CHANGELOG.md",
                "ROADMAP.md",
                "docs/roadmap/README.md");
        for (String relativePath : publishedDocs) {
            String source = read(repoRoot.resolve(relativePath));
            assertFalse(relativePath + " links to an unpublished archive",
                    UNPUBLISHED_ARCHIVE_LINK.matcher(source).find());
        }
    }

    @Test
    public void localArchivePathsRemainExplicitlyIgnored() throws IOException {
        Path repoRoot = findRepoRoot();
        String ignore = read(repoRoot.resolve(".gitignore"));
        for (String path : Arrays.asList(
                "docs/archive/",
                "docs/roadmap/archive/",
                "docs/patch-references/",
                "docs/raw/changelog_old.md")) {
            assertTrue("Missing local archive ignore rule: " + path,
                    Arrays.asList(ignore.split("\\R")).contains(path));
        }
        String rawIgnore = read(repoRoot.resolve("docs/raw/.gitignore"));
        assertTrue("The nested docs/raw rules must not re-include changelog_old.md",
                Arrays.asList(rawIgnore.split("\\R")).contains("changelog_old.md"));
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main"))
                    && Files.exists(cursor.resolve("ROADMAP.md"))) {
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
