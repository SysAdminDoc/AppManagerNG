// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(RobolectricTestRunner.class)
public class PathContentInfoImplTest {
    private java.nio.file.Path mTempDir;
    private Path mRoot;

    @Before
    public void setUp() throws IOException {
        mTempDir = Files.createTempDirectory("appmanagerng-path-content");
        mRoot = Paths.get(mTempDir.toFile());
    }

    @After
    public void tearDown() {
        if (mRoot != null) {
            mRoot.delete();
        }
    }

    @Test
    public void fromPathPrefersMagicBytesOverWrongExtension() throws Exception {
        Path pngWithTextExtension = mRoot.createNewFile("icon.txt", null);
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader())
                .getResourceAsStream("images/test_icon.png");
             OutputStream out = pngWithTextExtension.openOutputStream()) {
            IoUtils.copy(Objects.requireNonNull(in), out);
        }

        PathContentInfo contentInfo = pngWithTextExtension.getPathContentInfo();

        assertEquals("image/png", contentInfo.getMimeType());
    }

    @Test
    public void fromPathKeepsExtensionForPartialArchiveMatch() throws Exception {
        Path apksFile = mRoot.createNewFile("bundle.apks", null);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(apksFile.openOutputStream())) {
            zipOutputStream.putNextEntry(new ZipEntry("base.apk"));
            zipOutputStream.write("placeholder".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }

        PathContentInfo contentInfo = apksFile.getPathContentInfo();

        assertEquals("application/x-apks", contentInfo.getMimeType());
    }
}
