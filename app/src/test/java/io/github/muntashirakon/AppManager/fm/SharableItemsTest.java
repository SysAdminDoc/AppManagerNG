// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class SharableItemsTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-share-items");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void constructorCopiesPathsAndNormalizesMimeType() throws IOException {
        Path file = root.createNewFile("note.txt", null);
        List<Path> source = new ArrayList<>();
        source.add(file);

        SharableItems items = new SharableItems(source, " Text/Plain ; charset=utf-8 ");
        source.clear();

        assertEquals(1, items.pathList.size());
        assertEquals(file, items.pathList.get(0));
        assertEquals("text/plain", items.mimeType);
        assertThrows(UnsupportedOperationException.class, () -> items.pathList.clear());
    }

    @Test
    public void constructorRejectsEmptyPathList() {
        assertThrows(IllegalArgumentException.class, () -> new SharableItems(Collections.emptyList()));
    }

    @Test
    public void constructorDefaultsMalformedMimeType() throws IOException {
        Path file = root.createNewFile("payload.bin", null);

        SharableItems items = new SharableItems(Collections.singletonList(file), "not-a-mime");

        assertEquals(ContentType2.OTHER.getMimeType(), items.mimeType);
    }

    @Test
    public void toSharableIntentBuildsChooserWithSingleStreamGrant() throws IOException {
        Path file = root.createNewFile("note.txt", null);
        Uri uri = FmProvider.getContentUri(file);

        Intent chooser = new SharableItems(Collections.singletonList(file), "text/plain").toSharableIntent();
        Intent target = chooser.getParcelableExtra(Intent.EXTRA_INTENT);

        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertTrue((chooser.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNotNull(target);
        assertEquals(Intent.ACTION_SEND, target.getAction());
        assertEquals("text/plain", target.getType());
        assertEquals(uri, target.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((target.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(target.getClipData());
        assertEquals(uri, target.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void toSharableIntentBuildsChooserWithMultipleStreamGrants() throws IOException {
        Path first = root.createNewFile("one.txt", null);
        Path second = root.createNewFile("two.txt", null);

        Intent chooser = new SharableItems(java.util.Arrays.asList(first, second), "text/plain").toSharableIntent();
        Intent target = chooser.getParcelableExtra(Intent.EXTRA_INTENT);

        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertNotNull(target);
        assertEquals(Intent.ACTION_SEND_MULTIPLE, target.getAction());
        assertEquals("text/plain", target.getType());
        List<Uri> streams = target.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertNotNull(streams);
        assertEquals(java.util.Arrays.asList(FmProvider.getContentUri(first), FmProvider.getContentUri(second)),
                streams);
        assertTrue((target.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(target.getClipData());
        assertEquals(2, target.getClipData().getItemCount());
    }
}
