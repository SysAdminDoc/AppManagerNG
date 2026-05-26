// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LauncherIconAliasPlanTest {

    private static Set<LauncherIconAliasPlan.LauncherIconStyle> set(
            LauncherIconAliasPlan.LauncherIconStyle... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @Test
    public void planFromDefaultToNeutralEnablesNeutralDisablesDefault() {
        List<LauncherIconAliasPlan.Change> plan = LauncherIconAliasPlan.plan(
                set(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT),
                LauncherIconAliasPlan.LauncherIconStyle.NEUTRAL_SQUARE);
        assertEquals(2, plan.size());
        // Canonical order: DEFAULT, NG_MARK, NEUTRAL_SQUARE, MONOCHROME
        // So DEFAULT-off appears first; NEUTRAL-on appears second.
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT, plan.get(0).alias);
        assertEquals(false, plan.get(0).enabled);
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.NEUTRAL_SQUARE, plan.get(1).alias);
        assertEquals(true, plan.get(1).enabled);
    }

    @Test
    public void planFromNoneToDefaultJustEnablesDefault() {
        List<LauncherIconAliasPlan.Change> plan = LauncherIconAliasPlan.plan(
                Collections.<LauncherIconAliasPlan.LauncherIconStyle>emptySet(),
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT);
        assertEquals(1, plan.size());
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT, plan.get(0).alias);
        assertTrue(plan.get(0).enabled);
    }

    @Test
    public void planFromTargetAlreadyMatchingIsEmpty() {
        List<LauncherIconAliasPlan.Change> plan = LauncherIconAliasPlan.plan(
                set(LauncherIconAliasPlan.LauncherIconStyle.NG_MARK),
                LauncherIconAliasPlan.LauncherIconStyle.NG_MARK);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void planFromMultiEnabledCollapsesToSingleTarget() {
        // Malformed state: two aliases enabled. Plan must take us to a
        // single enabled alias (the target), disabling the others.
        Set<LauncherIconAliasPlan.LauncherIconStyle> current = EnumSet.of(
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT,
                LauncherIconAliasPlan.LauncherIconStyle.NG_MARK,
                LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME);
        List<LauncherIconAliasPlan.Change> plan = LauncherIconAliasPlan.plan(
                current, LauncherIconAliasPlan.LauncherIconStyle.NEUTRAL_SQUARE);

        // DEFAULT off, NG_MARK off, NEUTRAL_SQUARE on, MONOCHROME off => 4 changes.
        assertEquals(4, plan.size());
        // Iteration must follow canonical order.
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT, plan.get(0).alias);
        assertEquals(false, plan.get(0).enabled);
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.NG_MARK, plan.get(1).alias);
        assertEquals(false, plan.get(1).enabled);
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.NEUTRAL_SQUARE, plan.get(2).alias);
        assertEquals(true, plan.get(2).enabled);
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME, plan.get(3).alias);
        assertEquals(false, plan.get(3).enabled);
    }

    @Test
    public void planIsDeterministicAcrossDifferentInputSetImplementations() {
        // HashSet vs LinkedHashSet vs EnumSet must not change output ordering.
        List<LauncherIconAliasPlan.Change> a = LauncherIconAliasPlan.plan(
                new HashSet<>(Arrays.asList(
                        LauncherIconAliasPlan.LauncherIconStyle.NG_MARK,
                        LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME)),
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT);
        List<LauncherIconAliasPlan.Change> b = LauncherIconAliasPlan.plan(
                EnumSet.of(LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME,
                        LauncherIconAliasPlan.LauncherIconStyle.NG_MARK),
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT);
        assertEquals(a, b);
    }

    @Test
    public void resolveCurrentPicksSinglyEnabled() {
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME,
                LauncherIconAliasPlan.resolveCurrent(set(
                        LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME)));
    }

    @Test
    public void resolveCurrentDefaultsWhenNothingEnabled() {
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT,
                LauncherIconAliasPlan.resolveCurrent(
                        Collections.<LauncherIconAliasPlan.LauncherIconStyle>emptySet()));
    }

    @Test
    public void resolveCurrentPrefersDefaultWhenMultipleEnabled() {
        Set<LauncherIconAliasPlan.LauncherIconStyle> current = EnumSet.of(
                LauncherIconAliasPlan.LauncherIconStyle.NG_MARK,
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT,
                LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME);
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT,
                LauncherIconAliasPlan.resolveCurrent(current));
    }

    @Test
    public void resolveCurrentFallsBackToCanonicalOrderWhenDefaultMissing() {
        Set<LauncherIconAliasPlan.LauncherIconStyle> current = EnumSet.of(
                LauncherIconAliasPlan.LauncherIconStyle.MONOCHROME,
                LauncherIconAliasPlan.LauncherIconStyle.NG_MARK);
        assertEquals(LauncherIconAliasPlan.LauncherIconStyle.NG_MARK,
                LauncherIconAliasPlan.resolveCurrent(current));
    }

    @Test
    public void changeEqualityIsValueBased() {
        LauncherIconAliasPlan.Change a = new LauncherIconAliasPlan.Change(
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT, true);
        LauncherIconAliasPlan.Change b = new LauncherIconAliasPlan.Change(
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT, true);
        LauncherIconAliasPlan.Change c = new LauncherIconAliasPlan.Change(
                LauncherIconAliasPlan.LauncherIconStyle.DEFAULT, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(!a.equals(c));
    }

    @Test
    public void planTolerantOfRawSetWithNullElements() {
        Set<LauncherIconAliasPlan.LauncherIconStyle> dirty = new LinkedHashSet<>();
        dirty.add(LauncherIconAliasPlan.LauncherIconStyle.DEFAULT);
        dirty.add(null);
        List<LauncherIconAliasPlan.Change> plan = LauncherIconAliasPlan.plan(
                dirty, LauncherIconAliasPlan.LauncherIconStyle.NG_MARK);
        // Null is dropped; DEFAULT off, NG_MARK on => 2 changes.
        assertEquals(2, plan.size());
    }
}
