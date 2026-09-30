// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;

@RunWith(RobolectricTestRunner.class)
public class AdbLaunchFilesTest {
    // SELinux keeps the shell domain out of every one of these, whatever the file modes (issue #20)
    private static final String[] APP_DATA_ROOTS = {"/data/data/", "/data/user/", "/data/user_de/"};

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void adbLaunchFilesLiveInTheAppsOwnShellDirectory() {
        String dir = "/data/local/tmp/" + BuildConfig.APPLICATION_ID;
        assertEquals(dir, AdbLaunchFiles.STAGING_DIR);
        assertEquals(dir + "/run_server.sh", AdbLaunchFiles.SERVER_SCRIPT);
        assertEquals(dir + "/" + Constants.JAR_NAME, AdbLaunchFiles.SERVER_JAR);
        assertEquals(dir + "/main.jar", AdbLaunchFiles.MAIN_JAR);
        for (String path : new String[]{AdbLaunchFiles.SERVER_SCRIPT, AdbLaunchFiles.SERVER_JAR, AdbLaunchFiles.MAIN_JAR}) {
            assertOutsideAppData(path);
        }
    }

    @Test
    public void adbCommandRunsTheStagedLauncherAndFailsFast() {
        Context context = ApplicationProvider.getApplicationContext();
        String command = ServerConfig.getServerRunnerAdbCommand(12345);

        assertTrue(command.startsWith("sh " + AdbLaunchFiles.SERVER_SCRIPT + " 12345 "));
        assertTrue(command.endsWith(" || echo \"Error! Could not run the server launcher.\""));
        assertFalse(command.contains(ContextUtils.getDeContext(context).getCacheDir().getAbsolutePath()));
        assertOutsideAppData(command);
    }

    @Test
    public void stagedLauncherPointsAtTheStagedJar() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        String script = AssetsUtils.buildServerExecScript(context, AdbLaunchFiles.SERVER_JAR);

        assertTrue(script.contains("\nJAR_PATH=" + AdbLaunchFiles.SERVER_JAR + "\n"));
        assertTrue(script.contains("\nJAR_NAME=" + Constants.JAR_NAME + "\n"));
        assertFalse(script.contains("%ENV_VARS%"));
        assertFalse(script.contains("\r"));
        assertTrue(script.startsWith("#!/system/bin/sh\n"));
    }

    @Test
    public void mainJarCommandCopiesTheBundledBytes() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        byte[] jar = readAsset(context, AdbLaunchFiles.MAIN_JAR_NAME);

        String command = AdbLaunchFiles.stageMainJarCommand(context);

        assertTrue(command.contains(" count=" + jar.length + " "));
        assertTrue(command.contains(DigestUtils.getHexDigest(DigestUtils.SHA_256, jar)));
        assertTrue(command.contains(" '" + AdbLaunchFiles.MAIN_JAR + "' "));
        assertTrue(command, command.startsWith("{ mkdir -p '" + AdbLaunchFiles.STAGING_DIR + "' && chmod 700 '"
                + AdbLaunchFiles.STAGING_DIR + "' && dd "));
        assertTrue(command.endsWith(" && "));
        assertOutsideAppData(command.replace(context.getApplicationInfo().sourceDir, ""));
    }

    @Test
    public void extractCommandCopiesTheAssetOutOfTheApk() throws Exception {
        assumeTrue("needs a POSIX sh", shAvailable());
        byte[] jar = bytes(7173, 3);
        byte[] prefix = bytes(1031, 5);
        File apk = tmp.newFile("base.apk");
        Files.write(apk.toPath(), concat(prefix, jar, bytes(517, 11)));
        File stage = new File(tmp.getRoot(), "stage");
        File dest = new File(stage, "main.jar");
        String command = AdbLaunchFiles.buildExtractCommand(posix(apk), prefix.length, jar.length,
                DigestUtils.getHexDigest(DigestUtils.SHA_256, jar), posix(dest));

        String output = sh(command + "echo launched");

        assertTrue(output, output.contains("launched"));
        assertArrayEquals(jar, Files.readAllBytes(dest.toPath()));
        assertEquals(Arrays.asList("main.jar"), Arrays.asList(stage.list()));
    }

    @Test
    public void extractCommandStopsTheLaunchWhenTheCopyDiffers() throws Exception {
        assumeTrue("needs a POSIX sh", shAvailable());
        byte[] jar = bytes(4096, 3);
        File apk = tmp.newFile("base.apk");
        Files.write(apk.toPath(), concat(bytes(64, 5), jar));
        File stage = new File(tmp.getRoot(), "stage");
        File dest = new File(stage, "main.jar");
        byte[] other = jar.clone();
        other[0] ^= 1;
        String command = AdbLaunchFiles.buildExtractCommand(posix(apk), 64, jar.length,
                DigestUtils.getHexDigest(DigestUtils.SHA_256, other), posix(dest));

        String output = sh(command + "echo launched");

        assertFalse(output, output.contains("launched"));
        assertFalse(dest.exists());
        String[] left = stage.list();
        assertEquals(0, left == null ? 0 : left.length);
    }

    @Test
    public void lockCommandShutsTheDirectoryAndOnlyAFinishedRunSaysSo() throws Exception {
        String command = AdbLaunchFiles.lockDirCommand(AdbLaunchFiles.STAGING_DIR);
        assertEquals("mkdir -p '" + AdbLaunchFiles.STAGING_DIR + "' && chmod 700 '" + AdbLaunchFiles.STAGING_DIR
                + "' && echo AMNG_STAGING_''LOCKED", command);
        // A shell that echoes its input must not be mistaken for one that ran it
        assertFalse(command.contains(AdbLaunchFiles.LOCKED_MARKER));

        assumeTrue("needs a POSIX sh", shAvailable());
        File dir = new File(tmp.getRoot(), "stage");
        String output = sh(AdbLaunchFiles.lockDirCommand(posix(dir)));

        assertEquals(AdbLaunchFiles.LOCKED_MARKER, output.trim());
        assertTrue(dir.isDirectory());
        String failed = sh(AdbLaunchFiles.lockDirCommand(posix(tmp.newFile("in-the-way")) + "/stage"));
        assertFalse(failed, failed.contains(AdbLaunchFiles.LOCKED_MARKER));
    }

    @Test
    public void launchersNoLongerCopyFromTheAppCache() throws IOException {
        String manager = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java");
        String rootService = read("app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceManager.java");

        int stage = manager.indexOf("AdbLaunchFiles.stageServer(mContext, manager)");
        int launch = manager.indexOf("ServerConfig.getServerRunnerAdbCommand(localServerPort)");
        assertNotEquals(-1, stage);
        assertTrue(stage < launch);
        assertTrue(rootService.contains("classPath = AdbLaunchFiles.MAIN_JAR"));
        assertTrue(rootService.contains("AdbLaunchFiles.stageMainJarCommand(context)"));
        assertFalse(rootService.contains("PACKAGE_STAGING_DIRECTORY"));

        String launchFiles = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/AdbLaunchFiles.java");
        int lock = launchFiles.indexOf("lockStagingDir(manager);");
        assertNotEquals(-1, lock);
        assertTrue(lock < launchFiles.indexOf("AdbSync.open(manager)"));
    }

    private static void assertOutsideAppData(String text) {
        for (String root : APP_DATA_ROOTS) {
            assertFalse(text, text.contains(root));
        }
    }

    private static byte[] readAsset(Context context, String name) throws IOException {
        try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    private static byte[] bytes(int length, int seed) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; ++i) {
            data[i] = (byte) (i * seed + 1);
        }
        return data;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    // Git for Windows' sh takes C:/ paths, and forward slashes are what the command splits on
    private static String posix(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }

    private static boolean shAvailable() {
        try {
            Process p = new ProcessBuilder("sh", "-c", "command -v dd >/dev/null").redirectErrorStream(true).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    // The script goes in on stdin: Windows argument passing drops the quotes inside a -c argument
    private static String sh(String command) throws Exception {
        Process p = new ProcessBuilder("sh", "-s").redirectErrorStream(true).start();
        try (OutputStream in = p.getOutputStream()) {
            in.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        }
        byte[] output;
        try (InputStream in = p.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            output = out.toByteArray();
        }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        return new String(output, StandardCharsets.UTF_8);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(path)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
