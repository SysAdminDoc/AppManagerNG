// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class AppSelinuxContextsTest {
    @Test
    public void collectProcessContexts_matchesPkgListAndPackageProcessNames() {
        List<ActivityManager.RunningAppProcessInfo> processes = Arrays.asList(
                process(100, "com.example", "com.example"),
                process(101, "com.example:remote"),
                process(102, "com.other", "com.other"));

        List<AppSelinuxContexts.ProcessContext> contexts = AppSelinuxContexts.collectProcessContexts(
                "com.example", processes, pid -> " u:r:ctx" + pid + ":s0 ");

        assertEquals(2, contexts.size());
        assertEquals(100, contexts.get(0).pid);
        assertEquals("com.example", contexts.get(0).processName);
        assertEquals("u:r:ctx100:s0", contexts.get(0).context);
        assertEquals(101, contexts.get(1).pid);
        assertEquals("com.example:remote", contexts.get(1).processName);
        assertEquals("u:r:ctx101:s0", contexts.get(1).context);
    }

    @Test
    public void collectProcessContexts_skipsBlankAndUnreadableContexts() {
        List<ActivityManager.RunningAppProcessInfo> processes = Arrays.asList(
                process(100, "com.example", "com.example"),
                process(101, "com.example:remote"),
                process(102, "com.example:worker"));

        List<AppSelinuxContexts.ProcessContext> contexts = AppSelinuxContexts.collectProcessContexts(
                "com.example", processes, pid -> {
                    if (pid == 100) {
                        return "";
                    }
                    if (pid == 101) {
                        return null;
                    }
                    return "u:r:worker:s0";
                });

        assertEquals(1, contexts.size());
        assertEquals(102, contexts.get(0).pid);
        assertEquals("u:r:worker:s0", contexts.get(0).context);
    }

    @Test
    public void collectProcessContexts_fallsBackToPackageNameWhenProcessNameMissing() {
        List<ActivityManager.RunningAppProcessInfo> processes = Arrays.asList(process(100, null, "com.example"));

        List<AppSelinuxContexts.ProcessContext> contexts = AppSelinuxContexts.collectProcessContexts(
                "com.example", processes, pid -> "u:r:ctx:s0");

        assertEquals(1, contexts.size());
        assertEquals("com.example", contexts.get(0).processName);
    }

    @Test
    public void collectProcessContexts_returnsEmptyForNoMatches() {
        List<AppSelinuxContexts.ProcessContext> contexts = AppSelinuxContexts.collectProcessContexts(
                "com.example", Arrays.asList(process(100, "com.other", "com.other")),
                pid -> "u:r:other:s0");

        assertTrue(contexts.isEmpty());
    }

    private static ActivityManager.RunningAppProcessInfo process(int pid, String processName, String... pkgList) {
        ActivityManager.RunningAppProcessInfo process = new ActivityManager.RunningAppProcessInfo();
        process.pid = pid;
        process.processName = processName;
        process.pkgList = pkgList;
        return process;
    }
}
