// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.android16;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;
import android.os.IBinder;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Android16BinderCompatTest {
    @After
    public void tearDown() {
        Android16BinderCompat.resetTransactInvokersForTesting();
    }

    @Test
    public void shouldAttemptReflectiveFallback_onlyOnAndroid16AndNewer() {
        assertFalse(Android16BinderCompat.shouldAttemptReflectiveFallback(Build.VERSION_CODES.VANILLA_ICE_CREAM));
        assertTrue(Android16BinderCompat.shouldAttemptReflectiveFallback(Build.VERSION_CODES.BAKLAVA));
    }

    @Test
    public void transactForSdk_usesReflectiveFallbackOnAndroid16RuntimeFailure() throws Exception {
        AtomicBoolean reflectiveCalled = new AtomicBoolean(false);
        Android16BinderCompat.setTransactInvokersForTesting(
                (binder, code, data, reply, flags) -> {
                    throw new IllegalStateException("direct transact denied");
                },
                (binder, code, data, reply, flags) -> {
                    reflectiveCalled.set(true);
                    return true;
                });

        assertTrue(Android16BinderCompat.transactForSdk(Build.VERSION_CODES.BAKLAVA, null,
                IBinder.FIRST_CALL_TRANSACTION, null, null, 0));
        assertTrue(reflectiveCalled.get());
    }

    @Test
    public void transactForSdk_rethrowsDirectFailureBeforeAndroid16() throws Exception {
        AtomicBoolean reflectiveCalled = new AtomicBoolean(false);
        Android16BinderCompat.setTransactInvokersForTesting(
                (binder, code, data, reply, flags) -> {
                    throw new IllegalStateException("direct transact denied");
                },
                (binder, code, data, reply, flags) -> {
                    reflectiveCalled.set(true);
                    return true;
                });

        try {
            Android16BinderCompat.transactForSdk(Build.VERSION_CODES.VANILLA_ICE_CREAM, null,
                    IBinder.FIRST_CALL_TRANSACTION, null, null, 0);
            fail("Expected direct failure before Android 16");
        } catch (IllegalStateException expected) {
            assertFalse(reflectiveCalled.get());
        }
    }

    @Test
    public void rawBinderTransactsStayBehindAndroid16Compat() throws Exception {
        Path root = findProjectRoot();
        List<Path> sourceRoots = Arrays.asList(
                root.resolve("app/src/main/java"),
                root.resolve("libcore/io/src/main/java"),
                root.resolve("libcore/compat/src/main/java"),
                root.resolve("server/src/main/java"),
                root.resolve("hiddenapi/src/main/java"));
        List<String> offenders = new ArrayList<>();

        for (Path sourceRoot : sourceRoots) {
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(sourceRoot)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(path -> collectRawBinderTransactOffenders(root, path, offenders));
            }
        }

        assertTrue("Raw binder transact calls must route through Android16BinderCompat:\n"
                + String.join("\n", offenders), offenders.isEmpty());
    }

    private static void collectRawBinderTransactOffenders(Path root,
                                                          Path path,
                                                          List<String> offenders) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.contains(".transact(") || isAllowedTransactLine(path, line)) {
                    continue;
                }
                offenders.add(root.relativize(path) + ":" + (i + 1) + ": " + line.trim());
            }
        } catch (IOException e) {
            offenders.add(root.relativize(path) + ": " + e.getMessage());
        }
    }

    private static boolean isAllowedTransactLine(Path path, String line) {
        String normalizedPath = path.toString().replace('\\', '/');
        return normalizedPath.endsWith("/Android16BinderCompat.java")
                || line.contains("Android16BinderCompat.transact(")
                || line.contains("proxyBinder.transact(");
    }

    private static Path findProjectRoot() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("settings.gradle"))
                    && Files.exists(cursor.resolve("app/src/main/AndroidManifest.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate AppManagerNG project root");
    }
}
