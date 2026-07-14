// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class DexUtilsTest {
    private static File realDexFile() {
        return new File(Objects.requireNonNull(DexUtilsTest.class.getClassLoader()
                .getResource("oandbackups/ademar.textlauncher/classes.dex")).getFile());
    }

    @Test
    public void isDexReturnsTrueForRealDex() throws IOException {
        Path path = Paths.get(realDexFile());
        assertTrue(DexUtils.isDex(path));
    }

    @Test
    public void isDexReturnsFalseForShortFile() throws IOException {
        File shortFile = File.createTempFile("short", ".bin");
        shortFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(shortFile)) {
            fos.write(new byte[]{0x64, 0x65}); // only two of the four magic bytes
        }
        assertFalse(DexUtils.isDex(Paths.get(shortFile)));
    }

    @Test
    public void loadDexContainerParsesRealDexFromStream() throws IOException {
        try (InputStream is = new FileInputStream(realDexFile())) {
            DexBackedDexFile dexFile = DexUtils.loadDexContainer(is, -1);
            assertNotNull(dexFile);
            assertFalse(dexFile.getClasses().isEmpty());
        }
    }

    @Test
    public void loadDexContainerParsesDexFromChunkedStream() throws IOException {
        // A one-byte-at-a-time stream reproduces the short-read behaviour of SAF/remote sources.
        try (InputStream is = new OneByteAtATimeStream(new FileInputStream(realDexFile()))) {
            DexBackedDexFile dexFile = DexUtils.loadDexContainer(is, -1);
            assertNotNull(dexFile);
            assertFalse(dexFile.getClasses().isEmpty());
        }
    }

    @Test
    public void loadDexContainerRejectsGarbageAfterTryingBothParsers() throws IOException {
        byte[] garbage = new byte[512];
        for (int i = 0; i < garbage.length; ++i) {
            garbage[i] = (byte) (i % 251);
        }
        try {
            DexUtils.loadDexContainer(new ByteArrayInputStream(garbage), -1);
            fail("Expected UnsupportedFileTypeException for non-dex/non-odex input");
        } catch (DexFileFactory.UnsupportedFileTypeException expected) {
            // Both the dex and odex parsers ran against the full buffer and rejected it.
        }
    }

    private static final class OneByteAtATimeStream extends FilterInputStream {
        OneByteAtATimeStream(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            int c = read();
            if (c == -1) {
                return -1;
            }
            b[off] = (byte) c;
            return 1;
        }
    }
}
