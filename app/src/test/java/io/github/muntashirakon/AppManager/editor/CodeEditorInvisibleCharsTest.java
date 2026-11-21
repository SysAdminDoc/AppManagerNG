// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.net.Uri;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.fakes.RoboMenuItem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer;
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint;
import io.github.rosemoe.sora.text.Content;

/**
 * The code viewer marks zero-width and other invisible characters with a badge, and the marks
 * never become part of the text that gets saved (#53).
 */
@RunWith(RobolectricTestRunner.class)
public class CodeEditorInvisibleCharsTest {
    private static final String ZWSP = String.valueOf((char) 0x200B);
    private static final String RLO = String.valueOf((char) 0x202E);
    private static final String NBSP = String.valueOf((char) 0x00A0);
    private static final String TAG_A = new String(Character.toChars(0xE0061));

    private boolean mShowBefore;
    private File mFile;
    private ActivityController<CodeEditorActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mShowBefore = Prefs.Editor.isShowInvisibleCharsEnabled();
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        if (mFile != null) {
            mFile.delete();
        }
        Prefs.Editor.setShowInvisibleCharsEnabled(mShowBefore);
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
    }

    @Test
    public void hintsSitWhereTheHiddenCharactersAre() {
        Content content = new Content("plain line\nx" + ZWSP + "y\n" + NBSP + "\t" + RLO + "\n" + "ab" + TAG_A);

        InlayHintsContainer hints = EditorInvisibleChars.buildHints(content, 100);

        assertNotNull(hints);
        assertEquals(0, hints.getForLine(0).size());
        assertEquals(Arrays.asList("1:ZWSP"), describe(hints.getForLine(1)));
        // A tab is ordinary indentation, not a hidden character
        assertEquals(Arrays.asList("0:NBSP", "2:RLO"), describe(hints.getForLine(2)));
        assertEquals(Arrays.asList("2:TAG a"), describe(hints.getForLine(3)));
    }

    @Test
    public void plainTextGetsNoHints() {
        assertNull(EditorInvisibleChars.buildHints(new Content("nothing\nhidden\there"), 100));
        assertNull(EditorInvisibleChars.buildHints(new Content(""), 100));
    }

    @Test
    public void aFileFullOfHiddenCharactersStopsAtTheLimit() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 50; ++i) {
            text.append(ZWSP).append('\n');
        }

        InlayHintsContainer hints = EditorInvisibleChars.buildHints(new Content(text), 10);

        assertNotNull(hints);
        int count = 0;
        for (int line : hints.getLineNumbers()) {
            count += hints.getForLine(line).size();
        }
        assertEquals(10, count);
    }

    @Test
    public void buildingHintsLeavesTheTextAlone() {
        String text = "a" + ZWSP + "b\n" + RLO;
        Content content = new Content(text);

        EditorInvisibleChars.buildHints(content, 100);

        assertEquals(text, content.toString());
    }

    @Test
    public void theViewerMarksHiddenCharactersAndTheMenuTurnsThemOff() throws Exception {
        String text = "key=va" + ZWSP + "lue\nplain";
        Prefs.Editor.setShowInvisibleCharsEnabled(true);
        CodeEditorWidget editor = openEditor(text);

        assertEquals(Arrays.asList("6:ZWSP"), describe(editor.getInlayHints().getForLine(0)));

        RoboMenuItem item = new RoboMenuItem(R.id.action_show_invisible_chars);
        item.setCheckable(true);
        item.setChecked(true);
        assertTrue(editorFragment().onMenuItemSelected(item));
        settle();

        assertFalse(Prefs.Editor.isShowInvisibleCharsEnabled());
        assertFalse(item.isChecked());
        assertNoHints(editor);
        assertEquals("turning the marks off changed the text", text, editor.getText().toString());

        assertTrue(editorFragment().onMenuItemSelected(item));
        settle();

        assertTrue(Prefs.Editor.isShowInvisibleCharsEnabled());
        assertTrue(item.isChecked());
        assertEquals(Arrays.asList("6:ZWSP"), describe(editor.getInlayHints().getForLine(0)));
        assertEquals(text, editor.getText().toString());
    }

    @Test
    public void theViewerShowsNoMarksWhenTheyAreTurnedOff() throws Exception {
        Prefs.Editor.setShowInvisibleCharsEnabled(false);

        CodeEditorWidget editor = openEditor("a" + ZWSP + "b");

        assertNoHints(editor);
    }

    @Test
    public void typingAHiddenCharacterMarksItToo() throws Exception {
        Prefs.Editor.setShowInvisibleCharsEnabled(true);
        CodeEditorWidget editor = openEditor("abc");
        assertNoHints(editor);

        editor.getText().insert(0, 1, ZWSP);
        settle();

        assertEquals(Arrays.asList("1:ZWSP"), describe(editor.getInlayHints().getForLine(0)));
    }

    private CodeEditorWidget openEditor(String text) throws IOException, InterruptedException {
        mFile = File.createTempFile("invisible", ".txt");
        Files.write(mFile.toPath(), text.getBytes(StandardCharsets.UTF_8));
        mController = Robolectric.buildActivity(CodeEditorActivity.class, CodeEditorActivity.getIntent(
                ApplicationProvider.getApplicationContext(), Uri.fromFile(mFile), "Title", "Subtitle"));
        mController.setup();
        CodeEditorWidget editor = mController.get().findViewById(R.id.editor);
        long deadline = System.currentTimeMillis() + 10_000;
        while (!text.equals(editor.getText().toString())) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("the file never loaded: " + editor.getText());
            }
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        settle();
        return editor;
    }

    private CodeEditorFragment editorFragment() {
        Fragment fragment = mController.get().getSupportFragmentManager().findFragmentById(R.id.container);
        return (CodeEditorFragment) fragment;
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    }

    private static void assertNoHints(CodeEditorWidget editor) {
        InlayHintsContainer hints = editor.getInlayHints();
        assertTrue(hints == null || hints.getLineNumbers().length == 0);
    }

    private static List<String> describe(List<InlayHint> hints) {
        List<String> described = new ArrayList<>();
        for (InlayHint hint : hints) {
            described.add(hint.getColumn() + ":" + ((TextInlayHint) hint).getText());
        }
        return described;
    }
}
