// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.compressors.gzip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.ArchiveEntry;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveEntry;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitInputStream;
import io.github.muntashirakon.io.SplitOutputStream;

@RunWith(RobolectricTestRunner.class)
public class GzipCompressorOutputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final List<File> junkFiles = new ArrayList<>();

    @After
    public void tearDown() {
        for (File file : junkFiles) {
            file.delete();
        }
    }

    @Test
    public void testTarGzip() throws IOException {
        File base = new File("/tmp/AppManager_v2.5.22.apks.tar.gz");
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<Path> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(Paths.get(classLoader.getResource(fileName).getFile()));
        }

        try (FileOutputStream fos = new FileOutputStream(base);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GzipCompressorOutputStream gos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gos)) {
            for (Path file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tos.putArchiveEntry(tarEntry);
                try (InputStream is = file.openInputStream()) {
                    IoUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
            tos.finish();
        }

        // Check integrity
        List<String> actualFileNames = new ArrayList<>();
        try (FileInputStream sis = new FileInputStream(base);
             BufferedInputStream bis = new BufferedInputStream(sis);
             GzipCompressorInputStream gcis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gcis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                actualFileNames.add(entry.getName());
            }
        }

        Collections.sort(fileNames);
        Collections.sort(actualFileNames);
        assertEquals(fileNames, actualFileNames);
        junkFiles.add(base);
    }

    @Test
    public void testSplitTarGzip() throws IOException {
        List<String> fileNames = Arrays.asList("AppManager_v2.5.22.apks.0", "AppManager_v2.5.22.apks.1");
        List<Path> fileList = new ArrayList<>();
        assert classLoader != null;
        for (String fileName : fileNames) {
            fileList.add(Paths.get(classLoader.getResource(fileName).getFile()));
        }

        Path tmpPath = Paths.get("/tmp");
        try (SplitOutputStream sos = new SplitOutputStream(tmpPath, "AppManager_v2.5.22.apks.tar.gz", 1041921);
             BufferedOutputStream bos = new BufferedOutputStream(sos);
             GzipCompressorOutputStream gos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gos)) {
            for (Path file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tos.putArchiveEntry(tarEntry);
                try (InputStream is = file.openInputStream()) {
                    IoUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
            tos.finish();
        }

        // Check integrity
        List<String> actualFileNames = new ArrayList<>();
        List<Path> pathList = new ArrayList<>();
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.gz.0"));
        pathList.add(tmpPath.findFile("AppManager_v2.5.22.apks.tar.gz.1"));
        try (SplitInputStream sis = new SplitInputStream(pathList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             GzipCompressorInputStream gcis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gcis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                actualFileNames.add(entry.getName());
            }
        }
        Collections.sort(fileNames);
        Collections.sort(actualFileNames);
        assertEquals(fileNames, actualFileNames);
        for (Path path : pathList) {
            junkFiles.add(path.getFile());
        }
    }

    @Test
    public void aFailedCloseKeepsTheWriteErrorAndASecondCloseDoesNothing() throws IOException {
        BreakableStream out = new BreakableStream();
        GzipCompressorOutputStream gos = new GzipCompressorOutputStream(out);
        // The destination goes away after the header, as a backup folder can mid-write
        out.broken = true;
        byte[] data = new byte[64 * 1024];
        new Random(1).nextBytes(data);
        assertThrows(IOException.class, () -> gos.write(data));

        IOException closeError = assertThrows(IOException.class, gos::close);
        assertEquals("write failed", closeError.getMessage());
        assertEquals(1, closeError.getSuppressed().length);
        assertEquals("close failed", closeError.getSuppressed()[0].getMessage());
        // Closing again, as an enclosing stream and try-with-resources do, used to finish the ended deflater and
        // throw "Deflater has been closed", which replaced the write error above
        gos.close();
    }

    private static final class BreakableStream extends OutputStream {
        boolean broken;

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (broken) {
                throw new IOException("write failed");
            }
        }

        @Override
        public void close() throws IOException {
            if (broken) {
                throw new IOException("close failed");
            }
        }
    }
}
