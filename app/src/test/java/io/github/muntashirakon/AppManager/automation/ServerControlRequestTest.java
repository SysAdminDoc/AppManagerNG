// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Issue 19: what a server control broadcast may say. Everything outside this schema is refused, not
 * ignored, so a caller can't believe it asked for something it didn't get.
 */
@RunWith(RobolectricTestRunner.class)
public class ServerControlRequestTest {
    @Test
    public void onlyTheRightKeyAuthorizes() {
        assertTrue(ServerControlRequest.isAuthorized("the-key", "the-key"));

        assertFalse(ServerControlRequest.isAuthorized("the-key", "the-kez"));
        assertFalse(ServerControlRequest.isAuthorized("the-key", "the-key-and-more"));
        assertFalse(ServerControlRequest.isAuthorized("the-key", ""));
        assertFalse(ServerControlRequest.isAuthorized("the-key", null));
    }

    @Test
    public void aBlankStoredKeyOpensNothing() {
        assertFalse(ServerControlRequest.isAuthorized("", ""));
        assertFalse(ServerControlRequest.isAuthorized("   ", "   "));
        assertFalse(ServerControlRequest.isAuthorized(null, null));
        assertFalse(ServerControlRequest.isAuthorized(null, ""));
    }

    @Test
    public void everyModeOfOperationIsAccepted() {
        for (String mode : new String[]{"auto", "root", "shizuku", "adb_tcp", "adb_wifi", "no-root"}) {
            ServerControlRequest request = parse(action("SERVER_SET_MODE").putExtra("mode", mode));

            assertTrue(mode, request.valid);
            assertEquals(mode, request.mode);
        }
    }

    @Test
    public void anythingElseIsNotAMode() {
        for (String mode : new String[]{"", "ROOT", "adb", "root ", "root;id", "$(id)", "../root"}) {
            ServerControlRequest request = parse(action("SERVER_SET_MODE").putExtra("mode", mode));

            assertFalse(mode, request.valid);
            assertNull(mode, request.mode);
        }
    }

    @Test
    public void settingAModeNeedsAMode() {
        assertFalse(parse(action("SERVER_SET_MODE")).valid);
        // A mode of the wrong type reads as none
        assertFalse(parse(action("SERVER_SET_MODE").putExtra("mode", 1)).valid);
    }

    @Test
    public void startAndStopRefuseAModeRatherThanDropIt() {
        // SERVER_START with a mode would otherwise start the saved mode and look like it took
        assertTrue(parse(action("SERVER_START")).valid);
        assertTrue(parse(action("SERVER_STOP")).valid);

        assertFalse(parse(action("SERVER_START").putExtra("mode", Ops.MODE_ROOT)).valid);
        assertFalse(parse(action("SERVER_STOP").putExtra("mode", Ops.MODE_ROOT)).valid);
    }

    @Test
    public void anUnknownActionIsNotACommand() {
        for (String action : new String[]{BuildConfig.APPLICATION_ID + ".action.FREEZE",
                BuildConfig.APPLICATION_ID + ".action.SERVER_START2", "SERVER_START",
                "com.example.action.SERVER_START"}) {
            ServerControlRequest request = parse(new Intent(action));

            assertFalse(action, request.valid);
            assertEquals(ServerControlRequest.COMMAND_UNKNOWN, request.command);
        }
        assertFalse(parse(new Intent()).valid);
    }

    @Test
    public void aRequestIdIsShortAndPlain() {
        ServerControlRequest ok = parse(action("SERVER_STOP").putExtra("request_id", "Task-1_a.b:2"));
        assertTrue(ok.valid);
        assertEquals("Task-1_a.b:2", ok.requestId);

        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 65; ++i) {
            tooLong.append('a');
        }
        for (String id : new String[]{"", "a b", "a;b", "a\nb", "a'b", tooLong.toString()}) {
            ServerControlRequest bad = parse(action("SERVER_STOP").putExtra("request_id", id));
            assertFalse(id, bad.valid);
            assertNull(id, bad.requestId);
        }
    }

    @Test
    public void aResultPackageIsAPackageName() {
        ServerControlRequest ok = parse(action("SERVER_STOP").putExtra("result_package", "net.dinglisch.android.taskerm"));
        assertTrue(ok.valid);
        assertEquals("net.dinglisch.android.taskerm", ok.resultPackage);

        StringBuilder tooLong = new StringBuilder("a.");
        for (int i = 0; i < 255; ++i) {
            tooLong.append('a');
        }
        for (String name : new String[]{"", "nodots", ".a.b", "a..b", "a.1b", "a.b/c", "a.b;c", "a.b c", tooLong.toString()}) {
            ServerControlRequest bad = parse(action("SERVER_STOP").putExtra("result_package", name));
            assertFalse(name, bad.valid);
            assertNull(name, bad.resultPackage);
        }
    }

    @Test
    public void aGoodResultPackageSurvivesABadRequestSoTheCallerStillHearsWhy() {
        ServerControlRequest request = parse(action("SERVER_SET_MODE")
                .putExtra("mode", "nonsense")
                .putExtra("result_package", "com.example.app")
                .putExtra("request_id", "t1"));

        assertFalse(request.valid);
        assertEquals("com.example.app", request.resultPackage);
        assertEquals("t1", request.requestId);
    }

    @Test
    public void aStatusMapsToAResultCodeThatDoesNotCollideWithTheOthers() {
        assertEquals(-1, ServerControlRequest.resultCodeFor(Ops.STATUS_SUCCESS));
        assertEquals(101, ServerControlRequest.resultCodeFor(Ops.STATUS_FAILURE));
        assertEquals(111, ServerControlRequest.resultCodeFor(Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED));
    }

    @NonNull
    private static Intent action(@NonNull String command) {
        return new Intent(BuildConfig.APPLICATION_ID + ".action." + command);
    }

    @NonNull
    private static ServerControlRequest parse(@NonNull Intent intent) {
        return ServerControlRequest.from(intent);
    }
}
