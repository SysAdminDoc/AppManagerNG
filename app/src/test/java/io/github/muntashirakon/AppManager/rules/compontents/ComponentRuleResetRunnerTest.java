// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;

public class ComponentRuleResetRunnerTest {
    @Test
    public void partialFailureKeepsOnlyFailedTargetForRetryAndReportsBoundedProgress() {
        ComponentRuleResetPlan plan = plan();
        List<Integer> progress = new ArrayList<>();

        ComponentRuleResetResult result = ComponentRuleResetRunner.run(
                Arrays.asList(plan), () -> false,
                target -> target.getUserId() == 0 && target.getLabel().equals("B")
                        ? ComponentRuleResetResult.Outcome.failure(target, "denied")
                        : ComponentRuleResetResult.Outcome.success(target),
                (completed, total, target) -> {
                    assertEquals(6, total);
                    progress.add(completed);
                });

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), progress);
        assertEquals(6, result.getCompleted());
        assertEquals(5, result.getSucceeded());
        assertEquals(1, result.getFailed());
        assertFalse(result.isSuccessful());
        List<ComponentRuleResetPlan> retryPlans = ComponentUtils.getRetryPlans(
                Arrays.asList(plan), result);
        assertEquals(1, retryPlans.size());
        assertEquals(1, retryPlans.get(0).size());
        assertEquals("B", retryPlans.get(0).getTargets().get(0).getLabel());
        assertEquals(0, retryPlans.get(0).getTargets().get(0).getUserId());
    }

    @Test
    public void cancellationStopsBeforeNextTargetAndRetainsUnattemptedTargets() {
        ComponentRuleResetPlan plan = plan();
        AtomicInteger calls = new AtomicInteger();

        ComponentRuleResetResult result = ComponentRuleResetRunner.run(
                Arrays.asList(plan), () -> calls.get() >= 2,
                target -> {
                    calls.incrementAndGet();
                    return ComponentRuleResetResult.Outcome.success(target);
                }, (completed, total, target) -> {
                });

        assertTrue(result.isCancelled());
        assertEquals(2, result.getCompleted());
        assertEquals(4, result.getPending());
        List<ComponentRuleResetPlan> retryPlans = ComponentUtils.getRetryPlans(
                Arrays.asList(plan), result);
        assertEquals(4, retryPlans.get(0).size());
    }

    private static ComponentRuleResetPlan plan() {
        List<RuleEntry> rules = Arrays.asList(
                new ComponentRule("com.example", "A", RuleType.ACTIVITY,
                        ComponentRule.COMPONENT_DISABLED),
                new ComponentRule("com.example", "B", RuleType.SERVICE,
                        ComponentRule.COMPONENT_BLOCKED_IFW),
                new ComponentRule("com.example", "C", RuleType.RECEIVER,
                        ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE));
        return ComponentRuleResetPlan.fromRules("com.example", new int[]{0, 10}, rules);
    }
}
