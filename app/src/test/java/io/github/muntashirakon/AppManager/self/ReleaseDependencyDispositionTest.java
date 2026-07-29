// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Pins the release disposition of transitive dependencies that resolve below their fixed version.
 *
 * <p>These advisories sit under the local CVSS 9.0 threshold, so the dependency CVE gate does not
 * block the build on them. That is a reason to decide explicitly, not to ship an affected version
 * by default: {@code app/build.gradle} constrains each one up, and this test fails if a
 * dependency bump ever lets the affected version back onto a release runtime classpath.
 *
 * <p>Reachability was also checked directly: neither library survives R8 into the packaged
 * {@code flossRelease}/{@code fullRelease} APKs, so nothing affected is shipped either way.
 */
public class ReleaseDependencyDispositionTest {
    /** module coordinate prefix -> version that must not appear on a release runtime classpath. */
    private static final List<String[]> BANNED_ON_RELEASE_RUNTIME = Arrays.asList(
            // GHSA-7g45-4rm6-3mm3, GHSA-5mg8-w23w-74h3 — fixed in 32.1.3-android.
            new String[]{"com.google.guava:guava", "31.1-android"},
            // GHSA-735f-pc8j-v9w8 — fixed in 3.25.5.
            new String[]{"com.google.protobuf:protobuf-java", "3.22.3"});

    private static final List<String> RELEASE_RUNTIME_CLASSPATHS = Arrays.asList(
            "flossReleaseRuntimeClasspath", "fullReleaseRuntimeClasspath");

    @Test
    public void noAffectedVersionResolvesOntoAReleaseRuntimeClasspath() throws IOException {
        List<String> lines = Files.readAllLines(lockfile(), StandardCharsets.UTF_8);
        for (String[] banned : BANNED_ON_RELEASE_RUNTIME) {
            String coordinate = banned[0] + ":" + banned[1] + "=";
            for (String line : lines) {
                if (!line.startsWith(coordinate)) {
                    continue;
                }
                String classpaths = line.substring(coordinate.length());
                for (String releaseClasspath : RELEASE_RUNTIME_CLASSPATHS) {
                    assertFalse(banned[0] + " " + banned[1] + " is still on " + releaseClasspath,
                            containsClasspath(classpaths, releaseClasspath));
                }
            }
        }
    }

    @Test
    public void theFixedVersionsAreTheOnesOnTheReleaseRuntimeClasspaths() throws IOException {
        List<String> lines = Files.readAllLines(lockfile(), StandardCharsets.UTF_8);
        assertResolvedOnReleaseRuntime(lines, "com.google.guava:guava", "32.1.3-android");
        assertResolvedOnReleaseRuntime(lines, "com.google.protobuf:protobuf-java", "3.25.5");
    }

    private static void assertResolvedOnReleaseRuntime(@NonNull List<String> lines,
                                                       @NonNull String module,
                                                       @NonNull String version) {
        String coordinate = module + ":" + version + "=";
        for (String line : lines) {
            if (line.startsWith(coordinate)) {
                String classpaths = line.substring(coordinate.length());
                for (String releaseClasspath : RELEASE_RUNTIME_CLASSPATHS) {
                    assertTrue(module + " " + version + " is missing from " + releaseClasspath,
                            containsClasspath(classpaths, releaseClasspath));
                }
                return;
            }
        }
        fail(module + " " + version + " is not in the lockfile at all");
    }

    private static boolean containsClasspath(@NonNull String classpaths, @NonNull String name) {
        for (String entry : classpaths.split(",")) {
            if (entry.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static Path lockfile() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("app/gradle.lockfile");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new UncheckedIOException(new IOException("Could not locate app/gradle.lockfile"));
    }
}
