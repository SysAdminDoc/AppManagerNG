// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.BatteryOptimizationRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.Path;

/**
 * Imported rules have to reach the package's rules file. Applying them takes the package's write lock, which
 * reloads the file, so rows staged before that must still be there afterwards.
 */
@RunWith(RobolectricTestRunner.class)
public class RulesImporterApplyTest {
    private static final String PACKAGE = "com.example.imported";

    @Before
    public void setUp() {
        deleteRulesFile();
    }

    @After
    public void tearDown() {
        deleteRulesFile();
    }

    /**
     * The rules file of the context the code uses. That context stays the same across tests, so its files do too.
     */
    private static void deleteRulesFile() {
        Path rulesFile = RulesStorageManager.getConfDir(ContextUtils.getContext()).findFileOrNull(PACKAGE + ".tsv");
        if (rulesFile != null) {
            assertTrue(rulesFile.delete());
        }
    }

    @Test
    public void anImportedRowIsSavedToTheRulesFile() {
        try (RulesImporter importer = new RulesImporter(Collections.singletonList(RuleType.BATTERY_OPT),
                new int[]{0})) {
            importer.importRow(PACKAGE + "\tSTUB\tBATTERY_OPT\tfalse");
            importer.applyRules(true);
        }

        List<RuleEntry> saved = savedRules();
        assertEquals(1, saved.size());
        assertTrue(saved.get(0) instanceof BatteryOptimizationRule);
        assertEquals(false, ((BatteryOptimizationRule) saved.get(0)).isEnabled());
    }

    @Test
    public void anImportKeepsTheRulesAlreadySaved() {
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(PACKAGE, 0)) {
            cb.setAppOp(100, 1);
        }

        try (RulesImporter importer = new RulesImporter(Collections.singletonList(RuleType.BATTERY_OPT),
                new int[]{0})) {
            importer.importRow(PACKAGE + "\tSTUB\tBATTERY_OPT\tfalse");
            importer.applyRules(true);
        }

        assertEquals(2, savedRules().size());
    }

    @Test
    public void anImportThatIsntCommittedSavesNothing() {
        try (RulesImporter importer = new RulesImporter(Collections.singletonList(RuleType.BATTERY_OPT),
                new int[]{0})) {
            importer.importRow(PACKAGE + "\tSTUB\tBATTERY_OPT\tfalse");
            importer.applyRules(false);
        }

        assertEquals(0, savedRules().size());
    }

    private static List<RuleEntry> savedRules() {
        try (ComponentsBlocker cb = ComponentsBlocker.getInstance(PACKAGE, 0, false)) {
            return cb.getAll();
        }
    }
}
