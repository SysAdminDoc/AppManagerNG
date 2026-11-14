// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reading Magisk's version from su -v and su -V, the way a hidden Magisk app is recognized. Each
 * case runs {@link MagiskSuProbeFixture} as the su, so the processes and pipes are real.
 */
@RunWith(RobolectricTestRunner.class)
public class MagiskSuProbeTest {
    /** Long enough for two JVMs to start on a busy machine. */
    private static final long ROOMY_TIMEOUT_MILLIS = 15_000;

    @Test
    public void magisksSuGivesItsVersionCode() throws Exception {
        assertEquals(Long.valueOf(31100), RunnerUtils.readMagiskSuVersionCode(su("magisk"), ROOMY_TIMEOUT_MILLIS));
    }

    @Test
    public void anotherSuGivesNoVersionCode() throws Exception {
        assertNull(RunnerUtils.readMagiskSuVersionCode(su("other"), ROOMY_TIMEOUT_MILLIS));
    }

    @Test(timeout = 30_000)
    public void minusVAndMinusVShareOneTimeout() throws Exception {
        long timeoutMillis = 2_000;
        long start = System.nanoTime();
        assertNull(RunnerUtils.readMagiskSuVersionCode(su("stallCode"), timeoutMillis));
        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // -v alone takes 500 ms past its JVM's start, so a timeout per process would run past this
        assertTrue("took " + tookMillis + " ms", tookMillis < timeoutMillis + 300);
    }

    @Test(timeout = 30_000)
    public void aChildHoldingThePipeDoesNotHoldUpTheAnswer() throws Exception {
        long start = System.nanoTime();
        assertEquals(Long.valueOf(31100), RunnerUtils.readMagiskSuVersionCode(su("keepsPipe"), ROOMY_TIMEOUT_MILLIS));
        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // The child sleeps 10 s, and reading to the end of the pipe would wait for it, twice
        assertTrue("took " + tookMillis + " ms", tookMillis < 8_000);
    }

    private static List<String> su(String mode) throws Exception {
        File java = new File(new File(System.getProperty("java.home"), "bin"), "java");
        return Arrays.asList(java.getPath(), "-cp", classesRoot(), MagiskSuProbeFixture.class.getName(), mode);
    }

    /** The directory the fixture's class file was compiled into, which is all its JVM needs. */
    private static String classesRoot() throws Exception {
        String resource = MagiskSuProbeFixture.class.getName().replace('.', '/') + ".class";
        URL url = ClassLoader.getSystemResource(resource);
        assertNotNull(resource, url);
        File root = new File(url.toURI());
        for (int i = resource.split("/").length; i > 0; --i) {
            root = root.getParentFile();
        }
        return root.getPath();
    }
}
