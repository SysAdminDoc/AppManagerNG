// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class RulesImporterTest {
    @Test
    public void deselectedTypeDoesNotRegisterPackageBlocker() {
        try (RulesImporter importer = new RulesImporter(
                Collections.singletonList(RuleType.PERMISSION), new int[]{0})) {
            importer.importRow("com.example\tSTUB\tBATTERY_OPT\ttrue");

            assertTrue(importer.getPackages().isEmpty());
        }
    }
}
