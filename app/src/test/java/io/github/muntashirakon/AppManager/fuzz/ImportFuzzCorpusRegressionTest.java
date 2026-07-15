// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fuzz;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import io.github.muntashirakon.AppManager.apk.list.AppListImportFuzzTarget;
import io.github.muntashirakon.AppManager.rules.struct.RuleImportFuzzTarget;
import io.github.muntashirakon.AppManager.snapshot.SnapshotManifestFuzzTarget;
import io.github.muntashirakon.AppManager.utils.ArchiveImportFuzzTarget;

public class ImportFuzzCorpusRegressionTest {
    @Test
    public void everyTrackedCorpusInputRemainsNonCrashing() throws Exception {
        URL resource = getClass().getClassLoader().getResource("fuzz-corpus");
        assertNotNull(resource);
        Path corpusRoot = Paths.get(resource.toURI());
        int fixtures = 0;
        try (Stream<Path> paths = Files.walk(corpusRoot)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                byte[] data = Files.readAllBytes(path);
                String target = corpusRoot.relativize(path).getName(0).toString();
                switch (target) {
                    case "appList":
                        AppListImportFuzzTarget.fuzzerTestOneInput(data);
                        break;
                    case "rules":
                        RuleImportFuzzTarget.fuzzerTestOneInput(data);
                        break;
                    case "snapshot":
                        SnapshotManifestFuzzTarget.fuzzerTestOneInput(data);
                        break;
                    case "archive":
                        ArchiveImportFuzzTarget.fuzzerTestOneInput(data);
                        break;
                    default:
                        throw new AssertionError("Unknown fuzz corpus target: " + target);
                }
                ++fixtures;
            }
        }
        assertTrue("Fuzz corpus must contain seed/regression inputs", fixtures >= 8);
    }
}
