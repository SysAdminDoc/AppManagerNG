// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
public class OverlayTogglerTest {
    private static final String OVERLAY = "com.example.overlay";
    private static final String OTHER = "com.example.other";

    @Test
    public void aSecondChangeToAnOverlayStillChangingIsRefused() {
        QueueExecutor executor = new QueueExecutor();
        List<String> applied = new ArrayList<>();
        OverlayToggler toggler = new OverlayToggler(executor, (overlay, enable) -> {
            applied.add(overlay + "=" + enable);
            return true;
        }, () -> {
        });
        List<Boolean> results = new ArrayList<>();

        assertTrue(toggler.setEnabled(OVERLAY, true, results::add));
        assertTrue(toggler.isChanging(OVERLAY));
        assertFalse(toggler.setEnabled(OVERLAY, false, results::add));
        // A different overlay isn't held up.
        assertTrue(toggler.setEnabled(OTHER, true, results::add));
        executor.runAll();

        assertEquals(Arrays.asList(OVERLAY + "=true", OTHER + "=true"), applied);
        assertEquals(Arrays.asList(true, true), results);
        assertFalse(toggler.isChanging(OVERLAY));
        assertFalse(toggler.isChanging(OTHER));
        // Once it's done, the overlay can be changed again.
        assertTrue(toggler.setEnabled(OVERLAY, false, results::add));
    }

    @Test
    public void theRealStateIsReadBackAfterAChangeWithTheSwitchFreedFirst() {
        QueueExecutor executor = new QueueExecutor();
        List<String> order = new ArrayList<>();
        OverlayToggler[] toggler = new OverlayToggler[1];
        toggler[0] = new OverlayToggler(executor, (overlay, enable) -> {
            order.add("apply");
            return true;
        }, () -> order.add("reload, changing=" + toggler[0].isChanging(OVERLAY)));

        toggler[0].setEnabled(OVERLAY, true, success -> order.add("done " + success));
        executor.runAll();

        assertEquals(Arrays.asList("apply", "reload, changing=false", "done true"), order);
    }

    @Test
    public void aChangeAndroidTurnsDownStillReadsTheStateBack() {
        assertReadBackAfterFailure((overlay, enable) -> false);
    }

    @Test
    public void aChangeThatThrowsStillReadsTheStateBack() {
        assertReadBackAfterFailure((overlay, enable) -> {
            throw new RemoteException("overlay manager died");
        });
        assertReadBackAfterFailure((overlay, enable) -> {
            throw new SecurityException("CHANGE_OVERLAY_PACKAGES was revoked");
        });
        // A hidden API that changed shape on this Android version
        assertReadBackAfterFailure((overlay, enable) -> {
            throw new NoSuchMethodError("IOverlayManager.setEnabled");
        });
    }

    private static void assertReadBackAfterFailure(@NonNull OverlayToggler.Applier applier) {
        QueueExecutor executor = new QueueExecutor();
        int[] reloads = new int[1];
        OverlayToggler toggler = new OverlayToggler(executor, applier, () -> ++reloads[0]);
        List<Boolean> results = new ArrayList<>();

        assertTrue(toggler.setEnabled(OVERLAY, true, results::add));
        executor.runAll();

        assertEquals(1, reloads[0]);
        assertEquals(Collections.singletonList(false), results);
        assertFalse(toggler.isChanging(OVERLAY));
    }

    private static final class QueueExecutor implements Executor {
        private final List<Runnable> mTasks = new ArrayList<>();

        @Override
        public void execute(@NonNull Runnable command) {
            mTasks.add(command);
        }

        void runAll() {
            while (!mTasks.isEmpty()) {
                mTasks.remove(0).run();
            }
        }
    }
}
