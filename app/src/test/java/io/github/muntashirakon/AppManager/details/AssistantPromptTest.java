// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.DialogInterface;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;

/**
 * The launch through the assistant gives the user's assistant back when its wait ends. A screen's dialog goes with
 * the screen without calling its dismiss listener, so every way the prompt goes has to end the wait.
 */
@RunWith(RobolectricTestRunner.class)
public class AssistantPromptTest {
    private ActivityController<AppCompatActivity> mController;
    private AppCompatActivity mActivity;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(AppCompatActivity.class);
        mController.get().setTheme(R.style.AppTheme_V2);
        mController.setup();
        mActivity = mController.get();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
    }

    @Test
    public void endingThePromptEndsTheWaitWithoutRunningTheCloseAction() {
        AssistantPrompt prompt = new AssistantPrompt();
        CountDownLatch wait = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();

        assertTrue(prompt.show(mActivity, wait, R.string.launch_activity_dialog_message, closed::incrementAndGet));
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        assertEquals(1, wait.getCount());

        prompt.end();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, wait.getCount());
        assertFalse(dialog.isShowing());
        assertEquals(0, closed.get());
    }

    @Test
    public void closingThePromptEndsTheWaitAndRunsTheCloseAction() {
        AssistantPrompt prompt = new AssistantPrompt();
        CountDownLatch wait = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();
        prompt.show(mActivity, wait, R.string.launch_activity_dialog_message, closed::incrementAndGet);
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, wait.getCount());
        assertFalse(dialog.isShowing());
        assertEquals(1, closed.get());
        // Nothing is left for a later end() to count down or dismiss twice
        prompt.end();
        assertEquals(1, closed.get());
    }

    @Test
    public void aNewerPromptEndsTheOlderWaitWithoutRunningItsCloseAction() {
        AssistantPrompt prompt = new AssistantPrompt();
        CountDownLatch firstWait = new CountDownLatch(1);
        AtomicInteger firstClosed = new AtomicInteger();
        prompt.show(mActivity, firstWait, R.string.launch_activity_dialog_message, firstClosed::incrementAndGet);
        AlertDialog first = (AlertDialog) ShadowDialog.getLatestDialog();

        CountDownLatch secondWait = new CountDownLatch(1);
        AtomicInteger secondClosed = new AtomicInteger();
        prompt.show(mActivity, secondWait, R.string.launch_activity_dialog_message, secondClosed::incrementAndGet);
        // The first dialog's dismiss listener runs after the second one is up
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog second = (AlertDialog) ShadowDialog.getLatestDialog();

        assertNotSame(first, second);
        assertEquals(0, firstWait.getCount());
        assertFalse(first.isShowing());
        assertEquals(0, firstClosed.get());
        assertEquals(1, secondWait.getCount());
        assertTrue(second.isShowing());

        // The first dialog's late listener didn't take the newer one's place, so ending still reaches it
        prompt.end();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, secondWait.getCount());
        assertFalse(second.isShowing());
        assertEquals(0, secondClosed.get());
    }

    @Test
    public void aPromptThatCantBeShownEndsItsWait() {
        AssistantPrompt prompt = new AssistantPrompt();
        CountDownLatch wait = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();

        // Not a string resource, so building the dialog throws
        assertFalse(prompt.show(mActivity, wait, 0, closed::incrementAndGet));

        assertEquals(0, wait.getCount());
        assertEquals(0, closed.get());
    }

    @Test
    public void appDetailsAsksNobodyOnceItsScreenIsGone() {
        AppDetailsComponentsFragment fragment = new AppDetailsComponentsFragment();
        CountDownLatch wait = new CountDownLatch(1);

        fragment.askForTheAssistant(mActivity, wait);

        assertEquals(0, wait.getCount());
        assertNull(ShadowDialog.getLatestDialog());
    }
}
