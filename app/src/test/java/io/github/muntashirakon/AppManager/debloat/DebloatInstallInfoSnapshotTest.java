// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.App;

@RunWith(RobolectricTestRunner.class)
public class DebloatInstallInfoSnapshotTest {
    private static final String BLOAT = "com.example.bloat";
    private static final String ALTERNATIVE = "org.example.alt";

    private Context mContext;
    // Room refuses database work on the main thread, where Robolectric runs the test.
    private ExecutorService mWorker;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mWorker = Executors.newSingleThreadExecutor();
        // Set directly, so the test doesn't depend on reading the definition files.
        SuggestionObject alternative = new SuggestionObject();
        alternative.suggestionId = "s1";
        alternative.packageName = ALTERNATIVE;
        DebloatObject bloat = new DebloatObject();
        bloat.packageName = BLOAT;
        bloat.type = "oem";
        bloat.setSuggestions(Collections.singletonList(alternative));
        DebloatObject other = new DebloatObject();
        other.packageName = "com.example.other";
        other.type = "aosp";
        StaticDataset.setDebloatObjectsForTesting(Arrays.asList(bloat, other));
    }

    @After
    public void tearDown() throws Exception {
        onWorker(() -> {
            AppsDb.getInstance().appDao().deleteAll();
            return null;
        });
        mWorker.shutdownNow();
        StaticDataset.clearDebloatObjectsCache();
    }

    @Test
    public void eachReadFillsNewRowsAndLeavesEarlierOnesAsTheyWere() throws Exception {
        List<DebloatObject> first = onWorker(() -> StaticDataset.getDebloatObjectsWithInstalledInfo(mContext));
        assertEquals(2, first.size());
        DebloatObject before = find(first, BLOAT);
        assertNotNull(before);
        assertFalse(before.isInstalled());
        assertNull(before.getUsers());
        assertNull(before.getSuggestions().get(0).getUsers());

        // Between the two reads the package gets installed and frozen, and its suggested
        // replacement gets installed.
        onWorker(() -> {
            AppsDb.getInstance().appDao().insert(app(BLOAT, false));
            AppsDb.getInstance().appDao().insert(app(ALTERNATIVE, true));
            return null;
        });
        List<DebloatObject> second = onWorker(() -> StaticDataset.getDebloatObjectsWithInstalledInfo(mContext));
        DebloatObject after = find(second, BLOAT);
        assertNotNull(after);
        assertTrue(after.isInstalled());
        assertTrue(after.isFrozen());
        assertArrayEquals(new int[]{0}, after.getUsers());
        assertArrayEquals(new int[]{0}, after.getSuggestions().get(0).getUsers());

        // A list may still be showing the first read's rows. They say what was true then.
        assertNotSame(before, after);
        assertFalse(before.isInstalled());
        assertFalse(before.isFrozen());
        assertNull(before.getUsers());
        assertNull(before.getSuggestions().get(0).getUsers());
        Set<Object> firstRows = Collections.newSetFromMap(new IdentityHashMap<>());
        firstRows.addAll(first);
        for (DebloatObject row : first) {
            firstRows.addAll(row.getSuggestions() == null ? Collections.emptyList() : row.getSuggestions());
        }
        for (DebloatObject row : second) {
            assertFalse("a row was handed out by both reads", firstRows.contains(row));
            if (row.getSuggestions() != null) {
                for (Object suggestion : row.getSuggestions()) {
                    assertFalse("a suggestion was handed out by both reads", firstRows.contains(suggestion));
                }
            }
        }
    }

    @Test
    public void anIconLoadedOnceIsReusedAndNeverLandsOnTheSharedEntry() throws Exception {
        PackageInfo onDevice = new PackageInfo();
        onDevice.packageName = BLOAT;
        onDevice.applicationInfo = new ApplicationInfo();
        onDevice.applicationInfo.packageName = BLOAT;
        onDevice.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        shadowOf(mContext.getPackageManager()).installPackage(onDevice);
        onWorker(() -> {
            AppsDb.getInstance().appDao().insert(app(BLOAT, true));
            return null;
        });

        DebloatObject first = find(onWorker(() -> StaticDataset.getDebloatObjectsWithInstalledInfo(mContext)), BLOAT);
        DebloatObject second = find(onWorker(() -> StaticDataset.getDebloatObjectsWithInstalledInfo(mContext)), BLOAT);
        DebloatObject one = onWorker(() -> StaticDataset.getDebloatObjectWithInstalledInfo(mContext, BLOAT));

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(one);
        assertNotNull(first.getIcon());
        assertSame(first.getIcon(), second.getIcon());
        assertSame(first.getIcon(), one.getIcon());
        DebloatObject definition = find(StaticDataset.getDebloatObjects(), BLOAT);
        assertNotNull(definition);
        assertNull(definition.getIcon());
        assertFalse(definition.isInstalled());
    }

    @Test
    public void theDetailsDialogGetsItsOwnFilledCopy() throws Exception {
        onWorker(() -> {
            AppsDb.getInstance().appDao().insert(app(BLOAT, false));
            AppsDb.getInstance().appDao().insert(app(ALTERNATIVE, true));
            return null;
        });

        DebloatObject first = onWorker(() -> StaticDataset.getDebloatObjectWithInstalledInfo(mContext, BLOAT));
        DebloatObject second = onWorker(() -> StaticDataset.getDebloatObjectWithInstalledInfo(mContext, BLOAT));

        assertNotNull(first);
        assertTrue(first.isInstalled());
        assertTrue(first.isFrozen());
        assertArrayEquals(new int[]{0}, first.getUsers());
        assertArrayEquals(new int[]{0}, first.getSuggestions().get(0).getUsers());
        // Two dialogs, or a dialog and the list, never share a row.
        assertNotNull(second);
        assertNotSame(first, second);
        assertNotSame(first.getSuggestions().get(0), second.getSuggestions().get(0));
        // Opening the dialog twice doesn't add the same user twice.
        assertArrayEquals(new int[]{0}, second.getUsers());
        assertArrayEquals(new int[]{0}, second.getSuggestions().get(0).getUsers());
        DebloatObject definition = find(StaticDataset.getDebloatObjects(), BLOAT);
        assertNotNull(definition);
        assertNotSame(first, definition);
        assertFalse(definition.isInstalled());
        assertNull(definition.getUsers());
        assertNull(definition.getSuggestions().get(0).getUsers());
    }

    @Test
    public void theDetailsDialogGetsNothingForAPackageNotOnTheList() throws Exception {
        assertNull(onWorker(() -> StaticDataset.getDebloatObjectWithInstalledInfo(mContext, "com.example.unlisted")));
    }

    private <T> T onWorker(@NonNull Callable<T> work) throws Exception {
        return mWorker.submit(work).get();
    }

    @NonNull
    private static App app(@NonNull String packageName, boolean enabled) {
        App app = new App();
        app.packageName = packageName;
        app.userId = 0;
        app.packageLabel = packageName;
        app.isInstalled = true;
        app.isEnabled = enabled;
        return app;
    }

    @Nullable
    private static DebloatObject find(@NonNull List<DebloatObject> rows, @NonNull String packageName) {
        for (DebloatObject row : rows) {
            if (packageName.equals(row.packageName)) {
                return row;
            }
        }
        return null;
    }
}
