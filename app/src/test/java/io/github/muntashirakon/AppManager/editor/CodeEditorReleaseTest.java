// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.rosemoe.sora.event.PublishSearchResultEvent;

/**
 * The editor was never released when its view went away. The highlighter's thread kept running and
 * held the editor and its screen, and the old editor stayed hooked to the text the view model keeps
 * for the next view, so every trip to the Java view and back left one more of each behind.
 */
@RunWith(RobolectricTestRunner.class)
public class CodeEditorReleaseTest {
    private static final String JSON = "{\"a\": 1}\n";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private ActivityController<CodeEditorActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
    }

    @Test
    public void anEditorSentToTheBackStackIsReleasedAndItsEditsComeBack() throws Exception {
        CodeEditorActivity activity = open(JSON);
        CodeEditorWidget first = waitForText(activity, JSON);
        first.getText().insert(0, 0, "// edited\n");
        CodeEditorFragment fragment = (CodeEditorFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.container);

        leaveForAnotherScreen(activity);

        assertTrue(first.isReleased());
        assertNull(fragment.getView());

        activity.getSupportFragmentManager().popBackStackImmediate();
        CodeEditorWidget second = waitForText(activity, "// edited\n" + JSON);
        assertNotSame(first, second);
        assertFalse(second.isReleased());
    }

    @Test
    public void editsSurviveARotationAndStillCountAsUnsaved() throws Exception {
        CodeEditorActivity activity = open(JSON);
        CodeEditorWidget first = waitForText(activity, JSON);
        first.getText().insert(0, 0, "// edited\n");
        idle();

        activity = mController.recreate().get();

        CodeEditorWidget second = waitForText(activity, "// edited\n" + JSON);
        assertNotSame(first, second);
        assertTrue(first.isReleased());
        // The new screen still knows the edit isn't saved, so Back asks before dropping it
        assertEquals("* Subtitle", String.valueOf(activity.getSupportActionBar().getSubtitle()));
    }

    @Test
    public void lateEventsFromAReleasedEditorAreIgnored() throws Exception {
        CodeEditorActivity activity = open(JSON);
        CodeEditorWidget first = waitForText(activity, JSON);

        leaveForAnotherScreen(activity);
        // A search result or an edit landing after the view went away. The fragment no longer has
        // the views its handlers update, so neither may reach them.
        first.dispatchEvent(new PublishSearchResultEvent(first));
        first.getText().insert(0, 0, "x");
        idle();

        activity.getSupportFragmentManager().popBackStackImmediate();
        CodeEditorWidget second = waitForText(activity, "x" + JSON);
        first.dispatchEvent(new PublishSearchResultEvent(first));
        idle();
        assertFalse(second.isReleased());
    }

    @Test
    public void goingBackAndForthLeavesNoHighlighterThreadsBehind() throws Exception {
        Set<Thread> before = analyzerThreads();
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON);
        assertTrue("the JSON highlighter never started, so this shows nothing",
                waitForNewAnalyzers(before, 1) >= 1);

        for (int i = 0; i < 5; ++i) {
            leaveForAnotherScreen(activity);
            activity.getSupportFragmentManager().popBackStackImmediate();
            waitForText(activity, JSON);
        }

        // Only the editor on screen has one
        assertEquals(1, waitForNewAnalyzers(before, 1));
    }

    @NonNull
    private CodeEditorActivity open(@NonNull String text) throws Exception {
        File file = mTemp.newFile("file.json");
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        mController = Robolectric.buildActivity(CodeEditorActivity.class, CodeEditorActivity.getIntent(
                ApplicationProvider.getApplicationContext(), Uri.fromFile(file), "Title", "Subtitle"));
        return mController.setup().get();
    }

    private static void leaveForAnotherScreen(@NonNull CodeEditorActivity activity) {
        // What the Java view does: the editor's fragment waits on the back stack without its view
        FragmentManager fm = activity.getSupportFragmentManager();
        fm.beginTransaction()
                .replace(R.id.container, new Fragment())
                .addToBackStack(null)
                .commit();
        fm.executePendingTransactions();
        idle();
    }

    @NonNull
    private static CodeEditorWidget waitForText(@NonNull CodeEditorActivity activity, @NonNull String text)
            throws InterruptedException {
        // The file is read on a background thread
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            idle();
            CodeEditorWidget editor = activity.findViewById(R.id.editor);
            // Sora measures new text on a pool thread and an edit made before that finishes lands in
            // a half-built line table, so wait for the measuring to post back as done too
            if (editor != null && text.equals(editor.getText().toString()) && !isLayoutBusy(editor)) {
                return editor;
            }
            if (System.nanoTime() > deadline) {
                assertEquals(text, editor == null ? null : editor.getText().toString());
            }
            Thread.sleep(20);
        }
    }

    private static boolean isLayoutBusy(@NonNull CodeEditorWidget editor) {
        try {
            Field field = io.github.rosemoe.sora.widget.CodeEditor.class.getDeclaredField("layoutBusy");
            field.setAccessible(true);
            return field.getBoolean(editor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Sora no longer has CodeEditor.layoutBusy", e);
        }
    }

    private static int waitForNewAnalyzers(@NonNull Set<Thread> before, int expected) throws InterruptedException {
        // A released highlighter's thread ends on its own shortly after
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Set<Thread> now;
        do {
            now = analyzerThreads();
            now.removeAll(before);
            if (now.size() == expected) {
                break;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        return now.size();
    }

    @NonNull
    private static Set<Thread> analyzerThreads() {
        Set<Thread> threads = new HashSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("AsyncAnalyzer-")) {
                threads.add(thread);
            }
        }
        return threads;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
