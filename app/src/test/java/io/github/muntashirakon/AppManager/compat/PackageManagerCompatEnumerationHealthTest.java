// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@RunWith(RobolectricTestRunner.class)
public class PackageManagerCompatEnumerationHealthTest {
    @Test
    public void matchingCountsKeepThePrivilegedResultAtTwoListCalls() {
        FakeEnumerationSource source = new FakeEnumerationSource();
        source.unprivilegedPackages = packages("one", "two", "three");
        source.privilegedResults.add(packages("one", "two", "three"));

        PackageManagerCompat.InstalledPackagesResult result
                = PackageManagerCompat.getInstalledPackagesWithStatus(source, 0x20, 0, 0);

        assertNull(result.warning);
        assertEquals(3, result.packages.size());
        assertEquals(1, source.unprivilegedListCalls);
        assertEquals(1, source.privilegedListCalls);
        assertEquals(0, source.packageInfoCalls);
    }

    @Test
    public void shorterPrivilegedResultUsesTheUnprivilegedFloorAndReportsBothCounts() {
        FakeEnumerationSource source = new FakeEnumerationSource();
        source.unprivilegedPackages = packages("one", "two", "three");
        source.privilegedResults.add(packages("one"));
        source.addPackageDetails("one", "two", "three");

        PackageManagerCompat.InstalledPackagesResult result
                = PackageManagerCompat.getInstalledPackagesWithStatus(source, 0x20, 0, 0);

        assertNotNull(result.warning);
        assertEquals(1, result.warning.privilegedCount);
        assertEquals(3, result.warning.unprivilegedCount);
        assertEquals(Arrays.asList("one", "two", "three"), packageNames(result.packages));
        assertEquals(2, source.unprivilegedListCalls);
        assertEquals(1, source.privilegedListCalls);
        assertEquals(3, source.packageInfoCalls);
    }

    @Test
    public void packageRemovedBetweenSnapshotsDoesNotTriggerFallbackOrReturnStaleEntry() {
        FakeEnumerationSource source = new FakeEnumerationSource();
        source.unprivilegedResults.add(packages("one", "two", "removed"));
        source.unprivilegedResults.add(packages("one", "two"));
        source.privilegedResults.add(packages("one", "two"));

        PackageManagerCompat.InstalledPackagesResult result
                = PackageManagerCompat.getInstalledPackagesWithStatus(source, 0x20, 0, 0);

        assertNull(result.warning);
        assertEquals(Arrays.asList("one", "two"), packageNames(result.packages));
        assertEquals(2, source.unprivilegedListCalls);
        assertEquals(1, source.privilegedListCalls);
        assertEquals(0, source.packageInfoCalls);
    }

    @Test
    public void unavailableUnprivilegedReferencePreservesThePrivilegedCompatibilityPath() {
        FakeEnumerationSource source = new FakeEnumerationSource();
        source.unprivilegedPackages = null;
        source.privilegedResults.add(packages("one", "two"));
        source.privilegedResults.add(packages("one", "two"));

        PackageManagerCompat.InstalledPackagesResult result
                = PackageManagerCompat.getInstalledPackagesWithStatus(source, 0x20, 0, 0);

        assertNull(result.warning);
        assertEquals(Arrays.asList("one", "two"), packageNames(result.packages));
        assertEquals(1, source.unprivilegedListCalls);
        assertEquals(2, source.privilegedListCalls);
        assertEquals(0, source.packageInfoCalls);
    }

    @NonNull
    private static List<PackageInfo> packages(@NonNull String... packageNames) {
        PackageInfo[] packages = new PackageInfo[packageNames.length];
        for (int i = 0; i < packageNames.length; ++i) {
            packages[i] = packageInfo(packageNames[i]);
        }
        return Arrays.asList(packages);
    }

    @NonNull
    private static PackageInfo packageInfo(@NonNull String packageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        return packageInfo;
    }

    @NonNull
    private static List<String> packageNames(@NonNull List<PackageInfo> packages) {
        String[] packageNames = new String[packages.size()];
        for (int i = 0; i < packages.size(); ++i) {
            packageNames[i] = packages.get(i).packageName;
        }
        return Arrays.asList(packageNames);
    }

    private static final class FakeEnumerationSource
            implements PackageManagerCompat.PackageEnumerationSource {
        @Nullable
        private List<PackageInfo> unprivilegedPackages = Collections.emptyList();
        @NonNull
        private final Queue<List<PackageInfo>> unprivilegedResults = new ArrayDeque<>();
        @NonNull
        private final Queue<List<PackageInfo>> privilegedResults = new ArrayDeque<>();
        @NonNull
        private final Map<String, PackageInfo> packageDetails = new HashMap<>();
        private int unprivilegedListCalls;
        private int privilegedListCalls;
        private int packageInfoCalls;

        private void addPackageDetails(@NonNull String... packageNames) {
            for (String packageName : packageNames) {
                packageDetails.put(packageName, packageInfo(packageName));
            }
        }

        @Nullable
        @Override
        public List<PackageInfo> getUnprivilegedPackages(int flags) {
            ++unprivilegedListCalls;
            if (!unprivilegedResults.isEmpty()) {
                return unprivilegedResults.remove();
            }
            return unprivilegedPackages;
        }

        @NonNull
        @Override
        public List<PackageInfo> getPrivilegedPackages(int flags, int userId) {
            ++privilegedListCalls;
            List<PackageInfo> packages = privilegedResults.poll();
            return packages != null ? packages : Collections.emptyList();
        }

        @NonNull
        @Override
        public PackageInfo getPrivilegedPackageInfo(@NonNull String packageName, int flags,
                                                     int userId) throws Exception {
            ++packageInfoCalls;
            PackageInfo packageInfo = packageDetails.get(packageName);
            if (packageInfo == null) {
                throw new PackageManager.NameNotFoundException(packageName);
            }
            return packageInfo;
        }
    }
}
