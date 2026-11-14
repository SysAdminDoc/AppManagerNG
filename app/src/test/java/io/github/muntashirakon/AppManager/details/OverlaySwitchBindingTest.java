// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class OverlaySwitchBindingTest {
    private static final String OVERLAY = "com.example.overlay";
    private static final String OTHER = "com.example.other";

    private CompoundButton mToggle;
    private FakeChanges mChanges;

    @Before
    public void setUp() {
        mToggle = new CheckBox(ApplicationProvider.getApplicationContext());
        mChanges = new FakeChanges();
    }

    @Test
    public void aTapAsksForTheChangeAndTurnsTheSwitchOffUntilTheReadBack() {
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, false, mChanges);
        assertTrue(mToggle.isEnabled());
        assertFalse(mToggle.isChecked());

        assertTrue(mToggle.performClick());

        assertEquals(Collections.singletonList(OVERLAY + "=true"), mChanges.requests);
        assertTrue(mToggle.isChecked());
        assertFalse(mToggle.isEnabled());
    }

    @Test
    public void aRefusedTapPutsTheSwitchBack() {
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, true, mChanges);
        mChanges.refuse = true;

        mToggle.performClick();

        assertEquals(Collections.singletonList(OVERLAY + "=false"), mChanges.requests);
        assertTrue(mToggle.isChecked());
        assertTrue(mToggle.isEnabled());
    }

    @Test
    public void aRowDrawnWhileItsOverlayIsChangingHasItsSwitchOff() {
        mChanges.changing.add(OVERLAY);

        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, true, mChanges);

        assertFalse(mToggle.isEnabled());
        assertTrue(mToggle.isChecked());
        assertEquals(View.VISIBLE, mToggle.getVisibility());
    }

    @Test
    public void theReadBackRedrawsTheRowWithTheStateAndTheSwitchBack() {
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, false, mChanges);
        mToggle.performClick();
        assertFalse(mToggle.isEnabled());

        // The change failed, so the read back still says off.
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, false, mChanges);

        assertFalse(mToggle.isChecked());
        assertTrue(mToggle.isEnabled());
    }

    @Test
    public void aRecycledRowChangesOnlyTheOverlayItShowsNow() {
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, false, mChanges);
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OTHER, true, true, mChanges);

        mToggle.performClick();

        assertEquals(Collections.singletonList(OTHER + "=false"), mChanges.requests);
    }

    @Test
    public void anOverlayThatCantChangeHasNoSwitch() {
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, false, mChanges);
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OTHER, false, true, mChanges);

        assertEquals(View.GONE, mToggle.getVisibility());
        assertFalse(mToggle.isEnabled());
        mToggle.performClick();
        assertTrue(mChanges.requests.isEmpty());
    }

    @Test
    public void withoutAViewModelTheSwitchStaysOff() {
        AppDetailsOverlaysFragment.bindOverlaySwitch(mToggle, OVERLAY, true, true, null);

        assertFalse(mToggle.isEnabled());
        assertTrue(mToggle.isChecked());
    }

    private static final class FakeChanges implements AppDetailsOverlaysFragment.OverlayChanges {
        final List<String> requests = new ArrayList<>();
        final Set<String> changing = new HashSet<>();
        boolean refuse;

        @Override
        public boolean setOverlayEnabled(@NonNull String overlayPackage, boolean enable) {
            requests.add(overlayPackage + "=" + enable);
            return !refuse;
        }

        @Override
        public boolean isOverlayChanging(@NonNull String overlayPackage) {
            return changing.contains(overlayPackage);
        }
    }
}
