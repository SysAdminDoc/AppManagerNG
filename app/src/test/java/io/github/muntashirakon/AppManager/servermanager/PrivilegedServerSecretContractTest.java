// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@RunWith(RobolectricTestRunner.class)
public class PrivilegedServerSecretContractTest {
    private static final String TOKEN_KEY = "l_token";
    private static final String LEGACY_TOKEN = "restored-legacy-token";

    private Context context;
    private SharedPreferences legacyPreferences;
    private SharedPreferences secretPreferences;

    @Before
    public void setUp() {
        context = ContextUtils.getContext();
        legacyPreferences = context.getSharedPreferences("server_config", Context.MODE_PRIVATE);
        secretPreferences = context.getSharedPreferences(ServerConfig.SECRET_PREFS_NAME, Context.MODE_PRIVATE);
        clearPreferences();
    }

    @After
    public void tearDown() {
        clearPreferences();
    }

    @Test
    public void restoredLegacyTokenIsDiscardedAndRotated() {
        legacyPreferences.edit().putString(TOKEN_KEY, LEGACY_TOKEN).commit();

        String token = ServerConfig.getLocalToken();

        assertNotEquals(LEGACY_TOKEN, token);
        assertFalse(legacyPreferences.contains(TOKEN_KEY));
        assertEquals(token, secretPreferences.getString(TOKEN_KEY, null));
        assertEquals(64, token.length());
    }

    @Test
    public void concurrentTokenReadsReturnOneStableSecret() throws Exception {
        int taskCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        Set<Future<String>> futures = new HashSet<>();
        try {
            for (int i = 0; i < taskCount; ++i) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return ServerConfig.getLocalToken();
                }));
            }
            start.countDown();
            Set<String> tokens = new HashSet<>();
            for (Future<String> future : futures) {
                tokens.add(future.get());
            }
            assertEquals(1, tokens.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void androidBackupAndTransferAlwaysExcludeSecretFile() throws IOException {
        Path appDir = findAppProjectDir();
        String modernRules = read(appDir.resolve("src/main/res/xml/backup_rules.xml"));
        String legacyRules = read(appDir.resolve("src/main/res/xml/full_backup_rules.xml"));
        String exclusion = "<exclude domain=\"sharedpref\" path=\"server_secrets.xml\" />";

        assertEquals(2, occurrences(modernRules, exclusion));
        assertEquals(1, occurrences(legacyRules, exclusion));
    }

    @Test
    public void serializedServerDiagnosticsRedactAuthenticator() {
        ConfigParams params = new ConfigParams();
        params.put(ConfigParams.PARAM_TOKEN, LEGACY_TOKEN);
        params.put(ConfigParams.PARAM_PATH, "12345");

        assertFalse(params.toString().contains(LEGACY_TOKEN));
        assertTrue(params.toString().contains("<redacted>"));
        assertEquals("path:12345,token:<redacted>",
                ConfigParams.redact("path:12345,token:" + LEGACY_TOKEN));
    }

    @Test
    public void productionLoggingDoesNotEmitRawServerArguments() throws IOException {
        Path repoRoot = findAppProjectDir().getParent();
        String manager = read(repoRoot.resolve("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java"));
        String receiver = read(repoRoot.resolve("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerStatusChangeReceiver.java"));
        String runner = read(repoRoot.resolve("server/src/main/java/io/github/muntashirakon/AppManager/server/ServerRunner.java"));
        String script = read(repoRoot.resolve("app/src/main/assets/run_server.sh"));

        assertFalse(manager.contains("useAdbStartServer: %s\", command"));
        assertFalse(manager.contains("useRootStartServer: %s\", command"));
        assertFalse(receiver.contains("Expected: %s, Received: %s"));
        assertFalse(runner.contains("Arrays.toString(args)"));
        assertFalse(script.contains("echo \"Args: $ARGS\""));
    }

    private void clearPreferences() {
        legacyPreferences.edit().clear().commit();
        secretPreferences.edit().clear().commit();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            ++count;
            index += needle.length();
        }
        return count;
    }

    private static Path findAppProjectDir() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("src/main/res"))) {
                return cursor;
            }
            Path appDir = cursor.resolve("app");
            if (Files.isDirectory(appDir.resolve("src/main/res"))) {
                return appDir;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate app project directory");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
