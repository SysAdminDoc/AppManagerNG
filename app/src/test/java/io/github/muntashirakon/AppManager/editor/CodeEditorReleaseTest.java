// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.shadows.ShadowWindowManagerImpl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private File mFile;

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

    @Test
    public void anEarlierSaveDoesNotCountLaterEditsAsSavedOnANewView() throws Exception {
        CodeEditorActivity activity = open(JSON);
        CodeEditorWidget editor = waitForText(activity, JSON);
        editor.getText().insert(0, 0, "// one\n");
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save)));
        waitForFile(mFile, "// one\n" + JSON);
        waitForSubtitle(activity, "Subtitle");
        editor.getText().insert(0, 0, "// two\n");
        idle();
        assertEquals("* Subtitle", subtitle(activity));

        activity = mController.recreate().get();

        // The save above is old news to the new view: these edits still aren't saved
        waitForText(activity, "// two\n// one\n" + JSON);
        idle();
        assertEquals("* Subtitle", subtitle(activity));
    }

    @Test
    public void aSaveAsTargetIsStillTheTargetAfterARotation() throws Exception {
        CodeEditorActivity activity = open(JSON);
        CodeEditorWidget editor = waitForText(activity, JSON);
        editor.getText().insert(0, 0, "// mine\n");
        File copy = mTemp.newFile("copy.json");
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save_as)));
        idle();
        Intent picker = shadowOf(activity).getNextStartedActivityForResult().intent;
        shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, new Intent()
                .setData(Uri.fromFile(copy))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        waitForFile(copy, "// mine\n" + JSON);

        activity = mController.recreate().get();
        editor = waitForText(activity, "// mine\n" + JSON);
        editor.getText().insert(0, 0, "// more\n");
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save)));

        waitForFile(copy, "// more\n// mine\n" + JSON);
        assertEquals(JSON, read(mFile));
    }

    @Test
    public void aDialogLeftOpenGoesAwayWithTheView() throws Exception {
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON).getText().insert(0, 0, "x");
        idle();
        activity.getOnBackPressedDispatcher().onBackPressed();
        idle();
        Dialog unsaved = ShadowDialog.getLatestDialog();
        assertTrue("Back with unsaved edits asks first", unsaved != null && unsaved.isShowing());

        // The Java view replacing the editor while the question is up
        leaveForAnotherScreen(activity);

        // Its Save and exit would reach an editor that's gone
        assertFalse(unsaved.isShowing());
    }

    @Test
    public void aNewDocumentKeepsItsTextAcrossARotation() throws Exception {
        mController = Robolectric.buildActivity(CodeEditorActivity.class,
                new Intent(ApplicationProvider.getApplicationContext(), CodeEditorActivity.class)
                        .putExtra(Intent.EXTRA_TITLE, "Title")
                        .putExtra(Intent.EXTRA_SUBJECT, "Subtitle"));
        CodeEditorActivity activity = mController.setup().get();
        CodeEditorWidget first = waitForText(activity, "");
        first.getText().insert(0, 0, "notes\n");
        idle();

        activity = mController.recreate().get();

        waitForText(activity, "notes\n");
        idle();
        assertEquals("* Subtitle", subtitle(activity));
    }

    @Test
    public void anEditTypedWhileSavingStillCountsAsUnsaved() throws Exception {
        CodeEditorActivity activity = open(JSON);
        CodeEditorWidget editor = waitForText(activity, JSON);
        editor.getText().insert(0, 0, "// one\n");
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save)));
        // Typed while the file is being written, before the result comes back
        editor.getText().insert(0, 0, "// two\n");

        waitForFile(mFile, "// one\n" + JSON);
        waitForSavedToast(activity);
        assertEquals("* Subtitle", subtitle(activity));

        // A new view compares against what was written, not against what was on screen
        activity = mController.recreate().get();
        waitForText(activity, "// two\n// one\n" + JSON);
        idle();
        assertEquals("* Subtitle", subtitle(activity));
    }

    @Test
    public void aLanguagePickedByHandIsKeptAcrossARotation() throws Exception {
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON);
        TextView language = activity.findViewById(R.id.language);
        assertEquals("json", String.valueOf(language.getText()));

        language.performClick();
        idle();
        pick(onlyPopup(activity), "xml");
        assertEquals("xml", String.valueOf(language.getText()));

        activity = mController.recreate().get();
        waitForText(activity, JSON);
        idle();
        assertEquals("xml", String.valueOf(((TextView) activity.findViewById(R.id.language)).getText()));
    }

    @Test
    public void aSaveAsToAnotherKindOfFileSwitchesTheLanguageRightAway() throws Exception {
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON);
        File copy = mTemp.newFile("copy.xml");

        saveAs(activity, copy, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        waitForFile(copy, JSON);

        assertEquals("xml", String.valueOf(((TextView) activity.findViewById(R.id.language)).getText()));
    }

    @Test
    public void aCopySavedWithoutALastingGrantLeavesTheOpenFileUnsaved() throws Exception {
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON).getText().insert(0, 0, "// mine\n");
        File copy = mTemp.newFile("copy.json");

        // The picker gave no lasting access, so the editor stays on its own file
        saveAs(activity, copy, 0);
        waitForFile(copy, "// mine\n" + JSON);
        waitForSavedToast(activity);

        assertEquals(JSON, read(mFile));
        assertEquals("* Subtitle", subtitle(activity));

        // Saving to its own file still clears the mark
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save)));
        waitForFile(mFile, "// mine\n" + JSON);
        waitForSubtitle(activity, "Subtitle");
    }

    @Test
    public void saveAndExitLeavesOnceACopyOfAFileItCannotWriteIsSaved() throws Exception {
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON).getText().insert(0, 0, "// mine\n");
        idle();
        assertTrue(mFile.setWritable(false));
        try {
            activity.getOnBackPressedDispatcher().onBackPressed();
            idle();
            androidx.appcompat.app.AlertDialog unsaved = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
            unsaved.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
            idle();
            // The file can't be written, so it asks to save a copy instead
            androidx.appcompat.app.AlertDialog readOnly = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
            assertNotSame(unsaved, readOnly);
            readOnly.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            idle();
            File copy = mTemp.newFile("copy.json");
            // A picker that gives no lasting access, so the editor stays on its own file
            Intent picker = shadowOf(activity).getNextStartedActivityForResult().intent;
            shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(copy)));
            waitForFile(copy, "// mine\n" + JSON);
            waitForSavedToast(activity);

            // Everything typed is in the copy, so it leaves without asking again
            assertTrue(activity.isFinishing());
            assertSame(readOnly, ShadowDialog.getLatestDialog());
        } finally {
            mFile.setWritable(true);
        }
    }

    @Test
    public void aPopupLeftOpenGoesAwayWithTheView() throws Exception {
        CodeEditorActivity activity = open(JSON);
        waitForText(activity, JSON);
        activity.findViewById(R.id.language).performClick();
        idle();
        onlyPopup(activity);

        leaveForAnotherScreen(activity);

        // Picking from it would reach an editor that's gone
        assertEquals(Collections.emptyList(), popupWindows(activity));
    }

    @NonNull
    private CodeEditorActivity open(@NonNull String text) throws Exception {
        mFile = mTemp.newFile("file.json");
        Files.write(mFile.toPath(), text.getBytes(StandardCharsets.UTF_8));
        mController = Robolectric.buildActivity(CodeEditorActivity.class, CodeEditorActivity.getIntent(
                ApplicationProvider.getApplicationContext(), Uri.fromFile(mFile), "Title", "Subtitle"));
        return mController.setup().get();
    }

    @NonNull
    private static CodeEditorFragment fragment(@NonNull CodeEditorActivity activity) {
        return (CodeEditorFragment) activity.getSupportFragmentManager().findFragmentById(R.id.container);
    }

    @NonNull
    private static String subtitle(@NonNull CodeEditorActivity activity) {
        return String.valueOf(activity.getSupportActionBar().getSubtitle());
    }

    @NonNull
    private static String read(@NonNull File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void waitForFile(@NonNull File file, @NonNull String text) throws Exception {
        // Saves are written on a background thread
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!text.equals(read(file))) {
            if (System.nanoTime() > deadline) {
                assertEquals(text, read(file));
            }
            idle();
            Thread.sleep(20);
        }
        idle();
    }

    private static void waitForSubtitle(@NonNull CodeEditorActivity activity, @NonNull String text)
            throws InterruptedException {
        // The saved result comes back through the main thread
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!text.equals(subtitle(activity))) {
            if (System.nanoTime() > deadline) {
                assertEquals(text, subtitle(activity));
            }
            idle();
            Thread.sleep(20);
        }
    }

    private static void saveAs(@NonNull CodeEditorActivity activity, @NonNull File target, int grantFlags) {
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save_as)));
        idle();
        Intent picker = shadowOf(activity).getNextStartedActivityForResult().intent;
        shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, new Intent()
                .setData(Uri.fromFile(target))
                .addFlags(grantFlags));
    }

    private static void waitForSavedToast(@NonNull CodeEditorActivity activity) throws InterruptedException {
        // The file is complete before the result is posted back, so the toast is the sign it arrived
        String saved = activity.getString(R.string.saved_successfully);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!saved.equals(ShadowToast.getTextOfLatestToast())) {
            if (System.nanoTime() > deadline) {
                assertEquals(saved, ShadowToast.getTextOfLatestToast());
            }
            idle();
            Thread.sleep(20);
        }
    }

    /** The popup windows on screen: what PopupMenu adds to the window manager when it shows. */
    @NonNull
    private static List<View> popupWindows(@NonNull Activity activity) {
        ShadowWindowManagerImpl windowManager = Shadow.extract(activity.getWindowManager());
        List<View> popups = new ArrayList<>();
        for (View view : windowManager.getViews()) {
            if (view.getClass().getName().endsWith("PopupDecorView")) {
                popups.add(view);
            }
        }
        return popups;
    }

    @NonNull
    private static View onlyPopup(@NonNull Activity activity) {
        List<View> popups = popupWindows(activity);
        assertEquals("popups on screen", 1, popups.size());
        return popups.get(0);
    }

    private static void pick(@NonNull View popupWindow, @NonNull String title) {
        ListView list = findListView(popupWindow);
        assertNotNull("the popup has no list", list);
        ListAdapter adapter = list.getAdapter();
        for (int i = 0; i < adapter.getCount(); ++i) {
            Object item = adapter.getItem(i);
            if (item instanceof MenuItem && title.contentEquals(((MenuItem) item).getTitle())) {
                // What a tap on the row does
                list.performItemClick(null, i, adapter.getItemId(i));
                idle();
                return;
            }
        }
        throw new AssertionError("No " + title + " in the popup");
    }

    @Nullable
    private static ListView findListView(@NonNull View view) {
        if (view instanceof ListView) {
            return (ListView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                ListView list = findListView(group.getChildAt(i));
                if (list != null) {
                    return list;
                }
            }
        }
        return null;
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
