// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

public class PackageStateVerifierTest {
    private static final UserPackagePair PAIR = new UserPackagePair("com.example.app", 0);

    @Test
    public void uninstallRequiresPackageToReadBackUninstalled() {
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNINSTALL, PAIR, new FakeStateReader(false, false, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNINSTALL, PAIR, new FakeStateReader(true, false, false)));
    }

    @Test
    public void installExistingRequiresPackageToReadBackInstalled() {
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_INSTALL_EXISTING, PAIR, new FakeStateReader(true, false, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_INSTALL_EXISTING, PAIR, new FakeStateReader(false, false, false)));
    }

    @Test
    public void freezeRequiresInstalledFrozenState() {
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_FREEZE, PAIR, new FakeStateReader(true, true, false)));
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_ADVANCED_FREEZE, PAIR, new FakeStateReader(true, true, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_FREEZE, PAIR, new FakeStateReader(true, false, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_FREEZE, PAIR, new FakeStateReader(false, true, false)));
    }

    @Test
    public void unfreezeRequiresInstalledNotFrozenState() {
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNFREEZE, PAIR, new FakeStateReader(true, false, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNFREEZE, PAIR, new FakeStateReader(true, true, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNFREEZE, PAIR, new FakeStateReader(false, false, false)));
    }

    @Test
    public void archiveOperationsRequireExpectedArchiveState() {
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_ARCHIVE, PAIR, new FakeStateReader(true, false, true)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_ARCHIVE, PAIR, new FakeStateReader(true, false, false)));
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNARCHIVE, PAIR, new FakeStateReader(true, false, false)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_UNARCHIVE, PAIR, new FakeStateReader(true, false, true)));
    }

    @Test
    public void disableBackgroundRequiresBackgroundRunToReadBackDisabled() {
        assertTrue(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_DISABLE_BACKGROUND, PAIR,
                new FakeStateReader(true, false, false, true)));
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_DISABLE_BACKGROUND, PAIR,
                new FakeStateReader(true, false, false, false)));
    }

    @Test
    public void queryFailureFailsVerification() {
        assertFalse(PackageStateVerifier.matchesExpectedState(
                BatchOpsManager.OP_INSTALL_EXISTING, PAIR, new ThrowingStateReader()));
    }

    private static final class FakeStateReader implements PackageStateVerifier.StateReader {
        private final boolean mInstalled;
        private final boolean mFrozen;
        private final boolean mArchived;
        private final boolean mBackgroundRunDisabled;

        private FakeStateReader(boolean installed, boolean frozen, boolean archived) {
            this(installed, frozen, archived, false);
        }

        private FakeStateReader(boolean installed, boolean frozen, boolean archived, boolean backgroundRunDisabled) {
            mInstalled = installed;
            mFrozen = frozen;
            mArchived = archived;
            mBackgroundRunDisabled = backgroundRunDisabled;
        }

        @Override
        public boolean isInstalled(@NonNull UserPackagePair pair) {
            return mInstalled;
        }

        @Override
        public boolean isFrozen(@NonNull UserPackagePair pair) {
            return mFrozen;
        }

        @Override
        public boolean isArchived(@NonNull UserPackagePair pair) {
            return mArchived;
        }

        @Override
        public boolean isBackgroundRunDisabled(@NonNull UserPackagePair pair) {
            return mBackgroundRunDisabled;
        }
    }

    private static final class ThrowingStateReader implements PackageStateVerifier.StateReader {
        @Override
        public boolean isInstalled(@NonNull UserPackagePair pair) throws Throwable {
            throw new IllegalStateException("query failed");
        }

        @Override
        public boolean isFrozen(@NonNull UserPackagePair pair) throws Throwable {
            throw new IllegalStateException("query failed");
        }

        @Override
        public boolean isArchived(@NonNull UserPackagePair pair) throws Throwable {
            throw new IllegalStateException("query failed");
        }

        @Override
        public boolean isBackgroundRunDisabled(@NonNull UserPackagePair pair) throws Throwable {
            throw new IllegalStateException("query failed");
        }
    }
}
