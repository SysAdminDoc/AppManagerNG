// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link FeatureController} keeps a hand-written map from feature bit to display name, and the
 * feature-toggle screen renders exactly what that map contains. A bit that is declared but never
 * added to the map is therefore invisible and untoggleable, and nothing about the code makes that
 * obvious — the list simply enumerates whatever it enumerates.
 *
 * <p>These tests assert the map is complete with respect to the declared bits, so a flag added
 * later cannot silently fail to appear. Deliberate omissions are named here with their reason,
 * which makes the exclusion a decision on record rather than an oversight.
 */
@RunWith(RobolectricTestRunner.class)
public class FeatureFlagCoverageTest {
    /**
     * Bits intentionally absent from the toggle list.
     *
     * <p>{@code FEAT_INTERNET} is the "Use the Internet" master privacy switch and has its own
     * dedicated preference in Settings → Privacy ({@code PrivacyPreferences}, the
     * {@code toggle_internet} switch). Listing it a second time in the generic feature chooser
     * would give one bit two independent controls that could disagree on screen.
     */
    private static final Set<String> DELIBERATELY_UNLISTED = new HashSet<>(
            Arrays.asList("FEAT_INTERNET"));

    /**
     * Bits listed only when the build compiles in the optional network features.
     */
    private static final Set<String> FLAVOR_CONDITIONAL = new HashSet<>(
            Arrays.asList("FEAT_VIRUS_TOTAL"));

    private static List<Field> declaredFeatureBits() {
        List<Field> bits = new ArrayList<>();
        for (Field field : FeatureController.class.getDeclaredFields()) {
            if (field.getName().startsWith("FEAT_")
                    && field.getType() == int.class
                    && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                bits.add(field);
            }
        }
        return bits;
    }

    private static int valueOf(Field field) {
        try {
            return field.getInt(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Could not read " + field.getName(), e);
        }
    }

    @Test
    public void everyDeclaredFeatureBitIsListedOrDeliberatelyExcluded() {
        List<Field> bits = declaredFeatureBits();
        assertFalse("no FEAT_* constants found — this test would pass vacuously", bits.isEmpty());

        for (Field bit : bits) {
            String name = bit.getName();
            boolean listed = FeatureController.featureFlags.contains(valueOf(bit));
            if (DELIBERATELY_UNLISTED.contains(name)) {
                assertFalse(name + " is recorded as deliberately unlisted but now appears in the"
                        + " toggle list; either drop it from DELIBERATELY_UNLISTED or remove the"
                        + " duplicate control.", listed);
                continue;
            }
            if (FLAVOR_CONDITIONAL.contains(name)) {
                assertEquals(name + " must be listed exactly when optional network features are"
                                + " compiled in.",
                        FeatureController.areOptionalNetworkFeaturesAvailable(), listed);
                continue;
            }
            assertTrue(name + " is declared but missing from FeatureController's flag map, so it"
                    + " can never be shown or toggled. Add it to sFeatureFlagsMap, or record it in"
                    + " DELIBERATELY_UNLISTED with the reason.", listed);
        }
    }

    @Test
    public void everyListedFlagHasADisplayName() {
        // getFormattedFlagNames dereferences the map for each listed flag, so a flag present in
        // featureFlags but absent from the map would throw here rather than render blank.
        CharSequence[] names = FeatureController.getFormattedFlagNames(
                ApplicationProvider.getApplicationContext());

        assertEquals(FeatureController.featureFlags.size(), names.length);
        for (int i = 0; i < names.length; ++i) {
            CharSequence name = names[i];
            assertTrue("flag at index " + i + " has an empty display name",
                    name != null && name.length() > 0);
        }
    }

    @Test
    public void featureBitsAreDistinct() {
        Set<Integer> seen = new HashSet<>();
        for (Field bit : declaredFeatureBits()) {
            int value = valueOf(bit);
            assertTrue(bit.getName() + " reuses a bit already taken by another feature",
                    seen.add(value));
        }
    }
}
