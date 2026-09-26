// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import io.github.muntashirakon.io.Paths;

/**
 * The editor is exported for XML and text, so another app can hand it any file. XML is read with a
 * ceiling before it reaches a binary XML decoder.
 */
@RunWith(RobolectricTestRunner.class)
public class CodeEditorXmlLimitTest {
    @Rule
    public final TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void xmlAtTheLimitIsRead() throws IOException {
        byte[] bytes = CodeEditorViewModel.readXmlBytes(Paths.get(file("at.xml", 1_000)), 1_000);

        assertNotNull(bytes);
        assertEquals(1_000, bytes.length);
    }

    @Test
    public void xmlOverTheLimitIsRefused() throws IOException {
        assertNull(CodeEditorViewModel.readXmlBytes(Paths.get(file("over.xml", 1_001)), 1_000));
    }

    private File file(String name, int size) throws IOException {
        File file = mTemp.newFile(name);
        Files.write(file.toPath(), new byte[size]);
        return file;
    }
}
