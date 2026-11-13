// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.accessibility.topapp.TopAppTransition.Kind;
import io.github.muntashirakon.AppManager.accessibility.topapp.TopAppTransition.Layers;

public class TopAppTransitionTest {
    private static final String APP = "com.example.app";
    private static final String OTHER_APP = "com.example.other";
    private static final String AD = "com.example.ad";
    private static final String OTHER_AD = "com.example.otherad";
    private static final long NOW = 1_700_000_000_000L;

    @NonNull
    private static TopAppTransition t(@NonNull Kind kind, @NonNull String packageName, @Nullable String other) {
        return new TopAppTransition(NOW, kind, packageName, other);
    }

    @NonNull
    private static List<TopAppTransition> between(@NonNull Layers before, @NonNull Layers after) {
        return TopAppTransition.between(before, after, NOW);
    }

    @Test
    public void theFirstAppComesUpFromNothing() {
        assertEquals(Collections.singletonList(t(Kind.APP, APP, null)),
                between(Layers.EMPTY, new Layers(APP, null, false)));
    }

    @Test
    public void aNewAppNamesTheOneItReplaced() {
        assertEquals(Collections.singletonList(t(Kind.APP, OTHER_APP, APP)),
                between(new Layers(APP, null, false), new Layers(OTHER_APP, null, false)));
    }

    @Test
    public void anOverlayNamesTheAppItWasDrawnOver() {
        assertEquals(Collections.singletonList(t(Kind.OVERLAY_SHOWN, AD, APP)),
                between(new Layers(APP, null, false), new Layers(APP, AD, true)));
    }

    @Test
    public void anOverlayThatGoesAwayIsRecordedOnce() {
        Layers shown = new Layers(APP, AD, true);
        Layers gone = new Layers(APP, AD, false);

        assertEquals(Collections.singletonList(t(Kind.OVERLAY_GONE, AD, APP)), between(shown, gone));
        // Still named in the bubble, but nothing more happened.
        assertTrue(between(gone, gone).isEmpty());
        assertTrue(between(gone, new Layers(APP, null, false)).isEmpty());
    }

    @Test
    public void oneOverlayReplacingAnotherIsAGoneAndAShown() {
        assertEquals(Arrays.asList(t(Kind.OVERLAY_GONE, AD, APP), t(Kind.OVERLAY_SHOWN, OTHER_AD, APP)),
                between(new Layers(APP, AD, true), new Layers(APP, OTHER_AD, true)));
    }

    @Test
    public void anAppAndAnOverlayArrivingTogetherComeInTheOrderTheyHappened() {
        assertEquals(Arrays.asList(t(Kind.APP, OTHER_APP, APP), t(Kind.OVERLAY_SHOWN, AD, OTHER_APP)),
                between(new Layers(APP, null, false), new Layers(OTHER_APP, AD, true)));
    }

    @Test
    public void aLiveOverlayOverTheNextAppIsOnlyTheAppChange() {
        assertEquals(Collections.singletonList(t(Kind.APP, OTHER_APP, APP)),
                between(new Layers(APP, AD, true), new Layers(OTHER_APP, AD, true)));
    }

    @Test
    public void nothingChangingIsNothing() {
        Layers layers = new Layers(APP, AD, true);

        assertTrue(between(layers, layers).isEmpty());
        assertTrue(between(Layers.EMPTY, Layers.EMPTY).isEmpty());
    }

    @Test
    public void anOverlayOverTheHomeScreenHasNoAppUnderneath() {
        assertEquals(Collections.singletonList(t(Kind.OVERLAY_SHOWN, AD, null)),
                between(Layers.EMPTY, new Layers(null, AD, true)));
    }

    @Test
    public void aLineReadsBackAsTheSameTransition() {
        for (TopAppTransition transition : Arrays.asList(t(Kind.APP, APP, null), t(Kind.OVERLAY_SHOWN, AD, APP),
                t(Kind.OVERLAY_GONE, AD, APP))) {
            assertEquals(transition, TopAppTransition.parse(transition.toLine()));
        }
    }

    @Test
    public void aDamagedLineIsSkipped() {
        assertNull(TopAppTransition.parse(""));
        assertNull(TopAppTransition.parse("garbage"));
        assertNull(TopAppTransition.parse("notanumber\tAPP\tcom.example.app\t"));
        assertNull(TopAppTransition.parse("1\tNOT_A_KIND\tcom.example.app\t"));
        assertNull(TopAppTransition.parse("1\tAPP\t\t"));
        assertNull(TopAppTransition.parse("1\tAPP\tcom.example.app"));
        assertNull(TopAppTransition.parse("1\tAPP\tcom.example.app\tb\textra"));
    }
}
