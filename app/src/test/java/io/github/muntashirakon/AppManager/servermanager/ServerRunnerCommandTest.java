// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.server.common.Constants;

/**
 * Root starts the server through the native launcher in the app's library folder when the APK carries one, and
 * through run_server.sh when it doesn't.
 */
@RunWith(RobolectricTestRunner.class)
public class ServerRunnerCommandTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private String mNativeLibraryDir;

    @Before
    public void setUp() {
        mNativeLibraryDir = mContext.getApplicationInfo().nativeLibraryDir;
    }

    @After
    public void tearDown() {
        mContext.getApplicationInfo().nativeLibraryDir = mNativeLibraryDir;
    }

    @Test
    public void rootUsesTheNativeLauncherWhenTheApkHasOne() throws IOException {
        File libDir = tmp.newFolder("lib");
        File launcher = new File(libDir, ServerConfig.SERVER_RUNNER_NATIVE_NAME);
        assertTrue(launcher.createNewFile());
        mContext.getApplicationInfo().nativeLibraryDir = libDir.getAbsolutePath();

        String command = ServerConfig.getRootServerRunnerCommand(mContext, 62001);

        assertEquals(ServerConfig.buildNativeServerRunnerCommand(launcher.getAbsolutePath(), 62001,
                ServerConfig.getLocalToken(), ServerConfig.getServerJarFile(mContext).getAbsolutePath(),
                ServerConfig.ROOT_EXEC_JAR, Constants.SERVER_NAME, AssetsUtils.getServerArgs()), command);
    }

    @Test
    public void rootFallsBackToTheScriptWithoutTheNativeLauncher() throws IOException {
        mContext.getApplicationInfo().nativeLibraryDir = tmp.newFolder("empty").getAbsolutePath();

        String command = ServerConfig.getRootServerRunnerCommand(mContext, 62001);

        assertTrue(command, command.startsWith("sh "));
        assertEquals(ServerConfig.getServerRunnerCommand(1, 62001), command);
    }

    @Test
    public void rootFallsBackToTheScriptWithoutALibraryFolder() {
        mContext.getApplicationInfo().nativeLibraryDir = null;

        assertEquals(ServerConfig.getServerRunnerCommand(1, 62001),
                ServerConfig.getRootServerRunnerCommand(mContext, 62001));
    }

    @Test
    public void aFolderNamedLikeTheLauncherIsNotTheLauncher() throws IOException {
        File libDir = tmp.newFolder("lib");
        assertTrue(new File(libDir, ServerConfig.SERVER_RUNNER_NATIVE_NAME).mkdir());
        mContext.getApplicationInfo().nativeLibraryDir = libDir.getAbsolutePath();

        assertEquals(ServerConfig.getServerRunnerCommand(1, 62001),
                ServerConfig.getRootServerRunnerCommand(mContext, 62001));
    }

    @Test
    public void everyArgumentReachesTheLauncherAsOneWord() {
        String command = ServerConfig.buildNativeServerRunnerCommand("/data/app/~~a b/lib/x86_64/librun_server.so",
                62001, "0123abcd", "/data/user_de/0/it's/cache/am.jar", "/data/local/tmp/amng_server.jar",
                "amng_server", ",app:io.github.sysadmindoc.AppManagerNG,bgrun:1");

        assertEquals("'/data/app/~~a b/lib/x86_64/librun_server.so' 62001 '0123abcd'"
                + " '/data/user_de/0/it'\\''s/cache/am.jar' '/data/local/tmp/amng_server.jar' 'amng_server'"
                + " ',app:io.github.sysadmindoc.AppManagerNG,bgrun:1'", command);
    }
}
