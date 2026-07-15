// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import java.nio.charset.StandardCharsets;

public final class SnapshotManifestFuzzTarget {
    private SnapshotManifestFuzzTarget() {
    }

    public static void fuzzerTestOneInput(byte[] data) {
        String input = new String(data, StandardCharsets.UTF_8);
        try {
            SnapshotBundle.ManifestSummary manifest = SnapshotBundle.ManifestSummary.parse(input);
            if (manifest.contents.size() > Math.max(1, data.length)) {
                throw new AssertionError("Snapshot manifest output exceeded its input-derived allocation bound");
            }
            if (manifest.schemaVersion < 0 || manifest.format.isEmpty()) {
                throw new AssertionError("Snapshot parser accepted a manifest without its required identity");
            }
        } catch (SnapshotImportException expectedParseError) {
            // Invalid manifests are an expected, classified import error.
        }
    }
}
