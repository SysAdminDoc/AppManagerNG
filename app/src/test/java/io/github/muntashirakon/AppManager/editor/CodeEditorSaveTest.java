// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.rosemoe.sora.text.Content;

/**
 * Save and exit used to leave before the save had run, so a failure was never seen and the text was
 * gone. The editor now closes only for a save that was written and read back.
 */
@RunWith(RobolectricTestRunner.class)
public class CodeEditorSaveTest {
    @Rule
    public final TemporaryFolder mTemp = new TemporaryFolder();

    @Test(timeout = 30_000)
    public void saveAndExitClosesOnlyAfterTheSaveReadsBack() throws Exception {
        File file = mTemp.newFile("notes.txt");
        Files.write(file.toPath(), "old\n".getBytes(StandardCharsets.UTF_8));
        CodeEditorViewModel model = modelFor(file);

        model.setExitAfterSave(true);
        CodeEditorViewModel.SaveResult result = save(model, "new text\n");

        assertTrue(result.success);
        assertArrayEquals("new text\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file.toPath()));
        assertTrue(model.consumeExitAfterSave(result.success));
        assertFalse("the request is used once", model.consumeExitAfterSave(true));
    }

    @Test(timeout = 30_000)
    public void aFailedSaveKeepsTheEditorOpenAndTheFileAsItWas() throws Exception {
        File directory = mTemp.newFolder("not-a-file.txt");
        CodeEditorViewModel model = modelFor(directory);

        model.setExitAfterSave(true);
        CodeEditorViewModel.SaveResult result = save(model, "new text\n");

        assertFalse(result.success);
        assertTrue(result.originalIntact);
        assertFalse(model.consumeExitAfterSave(result.success));
        assertTrue(directory.isDirectory());
    }

    @Test
    public void plainTextIsEncodedAsUtf8() throws Exception {
        assertArrayEquals("é\n".getBytes(StandardCharsets.UTF_8),
                CodeEditorViewModel.encode("é\n", CodeEditorViewModel.XML_TYPE_NONE));
    }

    @NonNull
    private static CodeEditorViewModel modelFor(@NonNull File file) {
        CodeEditorViewModel model = new CodeEditorViewModel(RuntimeEnvironment.getApplication());
        model.setOptions(new CodeEditorFragment.Options.Builder().setUri(Uri.fromFile(file)).build());
        return model;
    }

    @NonNull
    private static CodeEditorViewModel.SaveResult save(@NonNull CodeEditorViewModel model, @NonNull String text)
            throws InterruptedException {
        List<CodeEditorViewModel.SaveResult> results = new ArrayList<>();
        model.getSaveFileLiveData().observeForever(results::add);
        model.saveFile(new Content(text), null);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (results.isEmpty() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertEquals(1, results.size());
        return results.get(0);
    }
}
