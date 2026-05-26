// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebloatImpactPreviewTest {

    @Test
    public void resultDefensiveCopyShieldsAgainstPostConstructMutation() {
        LinkedHashMap<String, String> seed = new LinkedHashMap<>();
        seed.put("android.app.role.SMS", "com.sample.sms");
        DebloatImpactPreview.Result result = DebloatImpactPreview.resultFromMapForTest(seed);
        seed.put("android.app.role.DIALER", "com.sample.dialer");
        assertEquals("Defensive copy: post-construction mutation must not leak in",
                1, result.roleLosses.size());
    }

    @Test
    public void emptyResultHasNoSignal() {
        DebloatImpactPreview.Result result = DebloatImpactPreview.resultFromMapForTest(null);
        assertFalse(result.hasAny());
        assertEquals("", DebloatImpactPreview.render(result));
    }

    @Test
    public void renderProducesOneLinePerRoleLoss() {
        LinkedHashMap<String, String> seed = new LinkedHashMap<>();
        seed.put("android.app.role.SMS", "com.sample.sms");
        seed.put("android.app.role.DIALER", "com.sample.dialer");
        DebloatImpactPreview.Result result = DebloatImpactPreview.resultFromMapForTest(seed);
        String rendered = DebloatImpactPreview.render(result);
        String[] lines = rendered.split("\n");
        assertEquals(2, lines.length);
        for (String line : lines) {
            assertTrue("each rendered line starts with a bullet", line.startsWith("  • "));
        }
        assertTrue(rendered.contains("com.sample.sms"));
        assertTrue(rendered.contains("com.sample.dialer"));
    }

    @Test
    public void shortRoleLabelHumanisesEnumStyleConstants() {
        assertEquals("Sms", DebloatImpactPreview.shortRoleLabel("android.app.role.SMS"));
        assertEquals("Dialer", DebloatImpactPreview.shortRoleLabel("android.app.role.DIALER"));
        assertEquals("Call redirection",
                DebloatImpactPreview.shortRoleLabel("android.app.role.CALL_REDIRECTION"));
        // No dot in input -> echo back unchanged.
        assertEquals("CUSTOM_ROLE", DebloatImpactPreview.shortRoleLabel("CUSTOM_ROLE"));
    }

    @Test
    public void checkedRolesIncludesTheUserDisruptiveDefaults() {
        HashSet<String> roles = new HashSet<>(Arrays.asList(DebloatImpactPreview.checkedRolesCopy()));
        assertTrue("SMS role must be checked", roles.contains("android.app.role.SMS"));
        assertTrue("Dialer role must be checked", roles.contains("android.app.role.DIALER"));
        assertTrue("Home role must be checked", roles.contains("android.app.role.HOME"));
        assertTrue("Browser role must be checked", roles.contains("android.app.role.BROWSER"));
    }

    @Test
    public void checkedRolesCopyIsADefensiveCopy() {
        String[] first = DebloatImpactPreview.checkedRolesCopy();
        String[] second = DebloatImpactPreview.checkedRolesCopy();
        assertEquals(first.length, second.length);
        // Mutating the returned array must not affect future callers.
        first[0] = "MUTATED";
        String[] third = DebloatImpactPreview.checkedRolesCopy();
        assertNotEquals("MUTATED", third[0]);
        assertEquals(second[0], third[0]);
    }
}
