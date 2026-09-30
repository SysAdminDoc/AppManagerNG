// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalServicesBindContractTest {
    @Test
    public void aliveCheckAndBindShareOneLock() throws IOException {
        // Two threads checking outside the lock both saw the services down, and the second one's
        // bindServices() unbound the binding the first had just made (DeadObjectException in
        // SelfPermissions.init on the first switch to ADB mode).
        String source = read("app/src/main/java/io/github/muntashirakon/AppManager/ipc/LocalServices.java");
        Matcher method = Pattern.compile("public static void bindServicesIfNotAlready\\(\\)[^{]*\\{(.*?)\\n    }",
                Pattern.DOTALL).matcher(source);
        assertTrue(method.find());
        String body = method.group(1);

        int lock = body.indexOf("synchronized (sBindLock)");
        int check = body.indexOf("activeServicesAlive()");
        assertTrue(body, lock != -1 && check > lock);
        assertTrue(body, body.indexOf("bindServices()") > check);
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
