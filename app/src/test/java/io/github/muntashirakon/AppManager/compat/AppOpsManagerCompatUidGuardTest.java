// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.app.AppOpsManager;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import com.android.internal.app.IAppOpsService;

import io.github.muntashirakon.AppManager.safety.AppOpsUidGuard;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AppOpsManagerCompatUidGuardTest {
    private static final int UID = Process.FIRST_APPLICATION_UID + 12;
    private static final int OP_CAMERA = 26;
    private static final String PACKAGE = "com.example.one";

    @Test
    public void everyUidModeCallerSourceFailsBeforeBinderMutation() {
        for (AppOpsUidGuard.MutationSource source : AppOpsUidGuard.MutationSource.values()) {
            RecordingAppOpsService service = new RecordingAppOpsService();
            AppOpsManagerCompat compat = new AppOpsManagerCompat(service,
                    uid -> new String[]{PACKAGE, "com.example.two"});

            assertThrows(source.name(), AppOpsUidGuard.UnsafeUidMutationException.class,
                    () -> compat.setMode(OP_CAMERA, UID, PACKAGE,
                            AppOpsManager.MODE_IGNORED, source, null));
            assertEquals(source.name(), 0, service.mUidModeWrites);
        }
    }

    @Test
    public void resetFailsBeforeBinderMutationForSystemUid() {
        RecordingAppOpsService service = new RecordingAppOpsService();
        AppOpsManagerCompat compat = new AppOpsManagerCompat(service,
                uid -> new String[]{"android"});

        assertThrows(AppOpsUidGuard.UnsafeUidMutationException.class,
                () -> compat.resetAllModesForUid(0, "android", 1000,
                        AppOpsUidGuard.MutationSource.RESET, null));
        assertEquals(0, service.mResetWrites);
    }

    @Test
    public void singlePackageUidReachesBinderMutation() throws Exception {
        RecordingAppOpsService service = new RecordingAppOpsService();
        AppOpsManagerCompat compat = new AppOpsManagerCompat(service,
                uid -> new String[]{PACKAGE});

        compat.setMode(OP_CAMERA, UID, PACKAGE,
                AppOpsManager.MODE_IGNORED, AppOpsUidGuard.MutationSource.DIRECT, null);

        assertEquals(1, service.mUidModeWrites);
    }

    private static final class RecordingAppOpsService implements IAppOpsService {
        private int mUidModeWrites;
        private int mResetWrites;

        @Override
        public int checkOperation(int code, int uid, String packageName) {
            return AppOpsManager.MODE_DEFAULT;
        }

        @Override
        public int permissionToOpCode(String permission) {
            return AppOpsManagerCompat.OP_NONE;
        }

        @Override
        public int checkPackage(int uid, String packageName) {
            return AppOpsManager.MODE_ALLOWED;
        }

        @Override
        public List<Parcelable> getPackagesForOps(int[] ops) {
            return Collections.emptyList();
        }

        @Override
        public List<Parcelable> getOpsForPackage(int uid, String packageName, int[] ops) {
            return Collections.emptyList();
        }

        @Override
        public List<Parcelable> getUidOps(int uid, int[] ops) {
            return Collections.emptyList();
        }

        @Override
        public void setUidMode(int code, int uid, int mode) {
            ++mUidModeWrites;
        }

        @Override
        public void setMode(int code, int uid, String packageName, int mode) {
        }

        @Override
        public void resetAllModes() {
            ++mResetWrites;
        }

        @Override
        public void resetAllModes(int reqUserId, String reqPackageName) {
            ++mResetWrites;
        }

        @Override
        public boolean isOperationActive(int code, int uid, String packageName) {
            return false;
        }

        @Override
        public int checkOperationRaw(int code, int uid, String packageName) {
            return AppOpsManager.MODE_DEFAULT;
        }

        @Override
        public int checkOperationRaw(int code, int uid, String packageName, String attributionTag) {
            return AppOpsManager.MODE_DEFAULT;
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }
}
