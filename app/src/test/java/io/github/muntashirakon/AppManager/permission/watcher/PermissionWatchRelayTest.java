// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class PermissionWatchRelayTest {
    private static final int APP_UID = 10123;
    private static final int UID_A = 10050;
    private static final int UID_A_PROFILE = 1010050;
    private static final int UID_SHARED = 10070;

    private final List<String> mSent = new ArrayList<>();
    private final List<Runnable> mPosted = new ArrayList<>();
    private final List<Set<String>> mWatched = new ArrayList<>();
    private int mUnwatches;
    private boolean mFailWatch;
    private PermissionWatchRelay mRelay;

    @Before
    public void setUp() {
        PermissionWatchRelay.Listeners listeners = new PermissionWatchRelay.Listeners() {
            @Override
            public void watch(@NonNull PermissionWatchRelay relay, @NonNull Set<String> packages) throws Exception {
                if (mFailWatch) {
                    throw new SecurityException("no OBSERVE_GRANT_REVOKE_PERMISSIONS");
                }
                mWatched.add(new HashSet<>(packages));
            }

            @Override
            public void unwatch() {
                ++mUnwatches;
            }
        };
        mRelay = new PermissionWatchRelay(listeners,
                (appUid, packageName, uid) -> mSent.add(appUid + " " + packageName + " " + uid),
                (runnable, delayMillis) -> {
                    assertEquals(PermissionWatchRelay.SETTLE_MILLIS, delayMillis);
                    mPosted.add(runnable);
                });
        mRelay.setTargets(APP_UID, new int[]{UID_A, UID_A_PROFILE, UID_SHARED, UID_SHARED},
                new String[]{"com.a", "com.a", "com.c", "com.d"});
    }

    private void runPosted() {
        List<Runnable> posted = new ArrayList<>(mPosted);
        mPosted.clear();
        for (Runnable runnable : posted) {
            runnable.run();
        }
    }

    @Test
    public void watchesEveryTargetAppForAppOps() {
        assertEquals(Collections.singletonList(new HashSet<>(Arrays.asList("com.a", "com.c", "com.d"))), mWatched);
    }

    @Test
    public void aPermissionChangeTellsTheAppAboutThatUserOnly() {
        mRelay.onChanged(UID_A_PROFILE, null);
        runPosted();

        assertEquals(Collections.singletonList(APP_UID + " com.a " + UID_A_PROFILE), mSent);
    }

    @Test
    public void aPermissionChangeOnASharedUidCoversEveryWatchedAppOnIt() {
        mRelay.onChanged(UID_SHARED, null);
        runPosted();

        assertEquals(new HashSet<>(Arrays.asList(APP_UID + " com.c " + UID_SHARED, APP_UID + " com.d " + UID_SHARED)),
                new HashSet<>(mSent));
    }

    @Test
    public void anAppOpChangeNamesItsOwnPackage() {
        mRelay.onChanged(UID_SHARED, "com.d");
        runPosted();

        assertEquals(Collections.singletonList(APP_UID + " com.d " + UID_SHARED), mSent);
    }

    @Test
    public void changesToAppsWithoutRulesAreDropped() {
        mRelay.onChanged(10999, null);
        mRelay.onChanged(UID_SHARED, "com.other");
        mRelay.onChanged(UID_A, "com.c");

        assertTrue(mPosted.isEmpty());
        runPosted();
        assertTrue(mSent.isEmpty());
    }

    @Test
    public void aBurstSettlesIntoOneMessagePerApp() {
        // Granting location flips two permissions and their app ops in quick succession
        mRelay.onChanged(UID_A, null);
        mRelay.onChanged(UID_A, null);
        mRelay.onChanged(UID_A, "com.a");

        assertEquals(1, mPosted.size());
        runPosted();
        assertEquals(Collections.singletonList(APP_UID + " com.a " + UID_A), mSent);

        mRelay.onChanged(UID_A, null);
        assertEquals(1, mPosted.size());
    }

    @Test
    public void newTargetsReplaceTheOldOnes() {
        mRelay.setTargets(APP_UID, new int[]{UID_SHARED}, new String[]{"com.c"});
        mRelay.onChanged(UID_A, null);
        mRelay.onChanged(UID_SHARED, null);
        runPosted();

        assertEquals(Collections.singletonList(APP_UID + " com.c " + UID_SHARED), mSent);
        assertEquals(Collections.singleton("com.c"), mWatched.get(mWatched.size() - 1));
    }

    @Test
    public void noTargetsStopsWatching() {
        mRelay.setTargets(APP_UID, new int[0], new String[0]);
        mRelay.onChanged(UID_A, null);

        assertEquals(1, mUnwatches);
        assertTrue(mPosted.isEmpty());
    }

    @Test
    public void malformedEntriesAreSkipped() {
        mRelay.setTargets(APP_UID, new int[]{UID_A, -1, UID_SHARED, 10080},
                new String[]{null, "com.negative", "", "com.e"});

        assertEquals(Collections.singleton("com.e"), mWatched.get(mWatched.size() - 1));
        mRelay.setTargets(APP_UID, null, new String[]{"com.a"});
        assertEquals(1, mUnwatches);
    }

    @Test
    public void aRefusedWatchKeepsTheTargetsForTheOtherListener() {
        mFailWatch = true;
        mRelay.setTargets(APP_UID, new int[]{UID_A}, new String[]{"com.a"});
        mRelay.onChanged(UID_A, "com.a");
        runPosted();

        assertEquals(Collections.singletonList(APP_UID + " com.a " + UID_A), mSent);
    }
}
