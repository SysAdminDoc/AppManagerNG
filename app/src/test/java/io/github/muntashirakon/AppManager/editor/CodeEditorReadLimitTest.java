// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.io.Paths;
import io.github.rosemoe.sora.text.Content;

/**
 * The editor is exported for XML and any text type, so another app can hand it any file under any
 * name. Every file is read with a ceiling before it becomes editor text or reaches a binary XML
 * decoder.
 */
@RunWith(RobolectricTestRunner.class)
public class CodeEditorReadLimitTest {
    private static final String AUTHORITY = "io.github.muntashirakon.AppManager.test.editor";
    private static File sServedFile;

    @Rule
    public final TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void xmlAtTheLimitIsRead() throws IOException {
        byte[] bytes = CodeEditorViewModel.readFileBytes(Paths.get(file("at.xml", 1_000)), 1_000);

        assertNotNull(bytes);
        assertEquals(1_000, bytes.length);
    }

    @Test
    public void xmlOverTheLimitIsRefused() throws IOException {
        assertNull(CodeEditorViewModel.readFileBytes(Paths.get(file("over.xml", 1_001)), 1_000));
    }

    @Test
    public void aProviderStreamIsBoundedAtBelowAndAboveTheLimit() throws IOException {
        Robolectric.setupContentProvider(FileProvider.class, AUTHORITY);
        Uri uri = Uri.parse("content://" + AUTHORITY + "/shared/doc");

        sServedFile = file("below", 999);
        byte[] below = CodeEditorViewModel.readFileBytes(Paths.get(uri), 1_000);
        sServedFile = file("at", 1_000);
        byte[] at = CodeEditorViewModel.readFileBytes(Paths.get(uri), 1_000);
        sServedFile = file("above", 1_001);
        byte[] above = CodeEditorViewModel.readFileBytes(Paths.get(uri), 1_000);

        assertNotNull(below);
        assertEquals(999, below.length);
        assertNotNull(at);
        assertEquals(1_000, at.length);
        assertNull(above);
    }

    @Test(timeout = 60_000)
    public void aTextFileOverTheLimitIsRefusedLikeXml() throws Exception {
        // Review finding: only the xml extensions were bounded, so a file named big.txt, or with no
        // extension at all, was read whole.
        File oversized = file("big.txt", CodeEditorViewModel.MAX_FILE_BYTES + 1);
        File small = mTemp.newFile("notes");
        Files.write(small.toPath(), "hello\n".getBytes());

        List<Content> refused = loaded(oversized);
        assertEquals(1, refused.size());
        assertNull(refused.get(0));
        Content content = loaded(small).get(0);
        assertNotNull(content);
        assertEquals("hello\n", content.toString());
    }

    @NonNull
    private static List<Content> loaded(@NonNull File file) throws InterruptedException {
        CodeEditorViewModel model = new CodeEditorViewModel(RuntimeEnvironment.getApplication());
        model.setOptions(new CodeEditorFragment.Options.Builder().setUri(Uri.fromFile(file)).build());
        List<Content> results = new ArrayList<>();
        model.getContentLiveData().observeForever(results::add);
        model.loadFileContentIfAvailable();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (results.isEmpty() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        return results;
    }

    private File file(String name, int size) throws IOException {
        File file = mTemp.newFile(name);
        Files.write(file.toPath(), new byte[size]);
        return file;
    }

    /** Serves {@link #sServedFile} for any URI, the way a file manager shares a document. */
    public static class FileProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Nullable
        @Override
        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
            return ParcelFileDescriptor.open(sServedFile, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        @Nullable
        @Override
        public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
            return null;
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            return "text/plain";
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                          @Nullable String[] selectionArgs) {
            return 0;
        }
    }
}
