// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class AppListImportFuzzTarget {
    private AppListImportFuzzTarget() {
    }

    public static void fuzzerTestOneInput(byte[] data) {
        String input = new String(data, StandardCharsets.UTF_8);
        try {
            Set<String> packageNames = ListImporter.readPackageNames(new StringReader(input));
            if (packageNames.size() > Math.max(1, data.length)) {
                throw new AssertionError("App-list output exceeded its input-derived allocation bound");
            }
            for (String packageName : packageNames) {
                if (packageName.isEmpty() || packageName.length() > input.length()) {
                    throw new AssertionError("App-list parser produced an invalid package token");
                }
            }
        } catch (JsonParseException | IOException expectedParseError) {
            // Invalid JSON is an expected, classified import error.
        }
    }
}
