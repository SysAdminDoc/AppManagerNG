// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Root starts the server through the native launcher in the app's library folder when the APK carries one, and
 * through run_server.sh when it doesn't. Either way the server runs am.jar where the app keeps it.
 */
@RunWith(RobolectricTestRunner.class)
public class ServerRunnerCommandTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private String mNativeLibraryDir;
    private File mDeCache;

    @Before
    public void setUp() {
        mNativeLibraryDir = mContext.getApplicationInfo().nativeLibraryDir;
        mDeCache = ContextUtils.getDeContext(mContext).getCacheDir();
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
                ServerConfig.getLocalToken(), new File(mDeCache, Constants.JAR_NAME).getAbsolutePath(),
                Constants.SERVER_NAME, AssetsUtils.getServerArgs()), command);
    }

    @Test
    public void rootNeverRunsTheJarFromTheShellUsersFolder() throws IOException {
        // The shell user owns /data/local/tmp and could rename its own file over a copy there
        // before app_process opened it
        File libDir = tmp.newFolder("lib");
        assertTrue(new File(libDir, ServerConfig.SERVER_RUNNER_NATIVE_NAME).createNewFile());
        mContext.getApplicationInfo().nativeLibraryDir = libDir.getAbsolutePath();
        String launcherCommand = ServerConfig.getRootServerRunnerCommand(mContext, 62001);
        mContext.getApplicationInfo().nativeLibraryDir = null;
        String scriptCommand = ServerConfig.getRootServerRunnerCommand(mContext, 62001);

        assertFalse(launcherCommand, launcherCommand.contains("/data/local/tmp"));
        assertFalse(scriptCommand, scriptCommand.contains("/data/local/tmp"));
    }

    @Test
    public void rootFallsBackToTheScriptWithoutTheNativeLauncher() throws IOException {
        mContext.getApplicationInfo().nativeLibraryDir = tmp.newFolder("empty").getAbsolutePath();

        assertEquals(expectedScriptCommand(), ServerConfig.getRootServerRunnerCommand(mContext, 62001));
    }

    @Test
    public void rootFallsBackToTheScriptWithoutALibraryFolder() {
        mContext.getApplicationInfo().nativeLibraryDir = null;

        assertEquals(expectedScriptCommand(), ServerConfig.getRootServerRunnerCommand(mContext, 62001));
    }

    @Test
    public void aFolderNamedLikeTheLauncherIsNotTheLauncher() throws IOException {
        File libDir = tmp.newFolder("lib");
        assertTrue(new File(libDir, ServerConfig.SERVER_RUNNER_NATIVE_NAME).mkdir());
        mContext.getApplicationInfo().nativeLibraryDir = libDir.getAbsolutePath();

        assertEquals(expectedScriptCommand(), ServerConfig.getRootServerRunnerCommand(mContext, 62001));
    }

    @Test
    public void theScriptCommandNamesTheScriptBeforeInit() {
        // ServerConfig.init hasn't run in this class, which used to make the command "sh null ..."
        assertEquals(expectedScriptCommand(), ServerConfig.getRootScriptCommand(mContext, 62001));
    }

    @Test
    public void onlyALauncherThatCouldntRunFallsBackToTheScript() {
        // What a shell exits with when the file can't be executed (126) or isn't there (127)
        assertTrue(LocalServerManager.launcherCouldNotRun(126));
        assertTrue(LocalServerManager.launcherCouldNotRun(127));
        // The launcher ran and refused, say a folder planted where the jar should be. The script
        // would only fail the same way.
        assertFalse(LocalServerManager.launcherCouldNotRun(1));
        assertFalse(LocalServerManager.launcherCouldNotRun(0));
    }

    @Test
    public void everyArgumentReachesTheLauncherAsOneWord() {
        String command = ServerConfig.buildNativeServerRunnerCommand("/data/app/~~a b/lib/x86_64/librun_server.so",
                62001, "0123abcd", "/data/user_de/0/it's/cache/am.jar", "amng_server",
                ",app:io.github.sysadmindoc.AppManagerNG,bgrun:1");

        assertEquals("'/data/app/~~a b/lib/x86_64/librun_server.so' 62001 '0123abcd'"
                + " '/data/user_de/0/it'\\''s/cache/am.jar' 'amng_server'"
                + " ',app:io.github.sysadmindoc.AppManagerNG,bgrun:1'", command);
    }

    private String expectedScriptCommand() {
        return "sh " + new File(mDeCache, "run_server.sh").getAbsolutePath() + " 62001 "
                + ServerConfig.getLocalToken();
    }
}
