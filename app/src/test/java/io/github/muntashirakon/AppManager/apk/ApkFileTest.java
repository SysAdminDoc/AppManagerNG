// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ApkFileTest {
    @Test
    public void recordSplitNameAcceptsDistinctNames() throws ApkFile.ApkFileException {
        Set<String> splitNames = new HashSet<>();

        ApkFile.recordSplitName(manifestAttrs("config.en"), splitNames, "split_config.en.apk");
        ApkFile.recordSplitName(manifestAttrs("config.xxhdpi"), splitNames, "split_config.xxhdpi.apk");
    }

    @Test
    public void recordSplitNameRejectsDuplicateNames() throws ApkFile.ApkFileException {
        Set<String> splitNames = new HashSet<>();
        ApkFile.recordSplitName(manifestAttrs("config.en"), splitNames, "split_config.en.apk");

        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.recordSplitName(manifestAttrs("config.en"), splitNames, "duplicate.apk"));
    }

    @Test
    public void recordSplitNameRejectsMissingNames() {
        assertThrows(ApkFile.ApkFileException.class,
                () -> ApkFile.recordSplitName(manifestAttrs(null), new HashSet<>(), "missing.apk"));
    }

    private static HashMap<String, String> manifestAttrs(String splitName) {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        manifestAttrs.put("split", splitName);
        return manifestAttrs;
    }
}
