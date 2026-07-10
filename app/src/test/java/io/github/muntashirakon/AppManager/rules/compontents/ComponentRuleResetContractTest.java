// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ComponentRuleResetContractTest {
    @Test
    public void resetSnapshotsEveryUserBeforeMutationAndPersistsRetryLedger() throws IOException {
        String componentUtils = read("app/src/main/java/io/github/muntashirakon/AppManager/rules/compontents/ComponentUtils.java");
        assertTrue(componentUtils.contains("getAllPackagesWithComponentRuleFiles(context)"));
        assertTrue(componentUtils.contains("ComponentRuleResetPlan.fromRules(packageName, userIds"));
        assertTrue(componentUtils.contains("ComponentRuleResetRunner.run(plans"));
        assertTrue(componentUtils.contains("persistRetryRules(plans, result.getSuccessfulTargetIds())"));
        assertTrue(componentUtils.contains("COMPONENT_ENABLED_STATE_DEFAULT"));
        assertTrue(componentUtils.contains("retryRule.restoreTo(blocker)"));
    }

    @Test
    public void rulesScreenOwnsDeterminateCancellationAndHistoryUi() throws IOException {
        String rules = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/RulesPreferences.java");
        String main = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/MainPreferences.java");
        assertTrue(rules.contains("getComponentRuleResetState().observe(getViewLifecycleOwner()"));
        assertTrue(rules.contains("setIndeterminate(false)"));
        assertTrue(rules.contains("setProgressCompat(state.completed, true)"));
        assertTrue(rules.contains("mModel.cancelRuleReset()"));
        assertTrue(rules.contains("retryFailedRuleResetTargets()"));
        assertTrue(rules.contains("OpHistoryManager.getHistoryActivityIntent"));
        assertFalse(main.contains("getOperationCompletedLiveData()"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(path)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
