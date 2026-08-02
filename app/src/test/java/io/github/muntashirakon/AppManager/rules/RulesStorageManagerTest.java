// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class RulesStorageManagerTest {
    private static final String PACKAGE_NAME = "sample.transaction";

    private java.nio.file.Path rulesFile;

    @Before
    public void setUp() throws Exception {
        rulesFile = Files.createTempFile("appmanagerng-rules", ".tsv");
        Files.deleteIfExists(rulesFile);
        TestRulesStorageManager.rulesFile = rulesFile;
    }

    @After
    public void tearDown() throws Exception {
        Files.deleteIfExists(rulesFile);
        TestRulesStorageManager.rulesFile = null;
    }

    @Test
    public void mutableTransactionsReloadAfterThePreviousWriterCommits() throws Exception {
        TestRulesStorageManager first = new TestRulesStorageManager(true);
        first.setMutable();
        first.setAppOp(100, AppOpsManager.MODE_ALLOWED);

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> second = executor.submit(() -> {
            ready.countDown();
            try (TestRulesStorageManager rules = new TestRulesStorageManager(true)) {
                rules.setMutable();
                rules.setAppOp(101, AppOpsManager.MODE_ALLOWED);
            }
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        assertTrue("the second writer must wait for the first transaction", !second.isDone());

        first.close();
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        try (TestRulesStorageManager result = new TestRulesStorageManager(false)) {
            assertEquals(2, result.entryCount());
            assertTrue(result.getAll(AppOpRule.class).contains(new AppOpRule(PACKAGE_NAME, 100,
                    AppOpsManager.MODE_ALLOWED)));
            assertTrue(result.getAll(AppOpRule.class).contains(new AppOpRule(PACKAGE_NAME, 101,
                    AppOpsManager.MODE_ALLOWED)));
        }
    }

    private static final class TestRulesStorageManager extends RulesStorageManager {
        private static java.nio.file.Path rulesFile;

        private TestRulesStorageManager(boolean lockBeforeLoad) {
            super(PACKAGE_NAME, 0, lockBeforeLoad);
        }

        @Override
        protected Path getDesiredFile(boolean create) {
            return Paths.get(rulesFile.toFile());
        }
    }
}
