// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SECURITY;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;

@RunWith(RobolectricTestRunner.class)
public class PackageInstallerServiceTest {
    @Test
    public void finalProgressNotificationUsesConcreteSuccessSubject() {
        Context context = RuntimeEnvironment.getApplication();
        FakeProgressHandler progressHandler = new FakeProgressHandler();
        String subject = PackageInstallerService.getStringFromStatus(context, STATUS_SUCCESS, "Example", null);

        PackageInstallerService.prepareFinalProgressNotification(progressHandler, "Example", subject, null);

        assertEquals("Example", progressHandler.lastInfo.getTitle());
        assertEquals(subject, progressHandler.lastInfo.getBody());
        assertNotEquals(context.getString(R.string.done), progressHandler.lastInfo.getBody());
        assertTrue(progressHandler.progressTextCleared);
    }

    @Test
    public void finalProgressNotificationUsesConcreteFailureSubjectAndDetails() {
        Context context = RuntimeEnvironment.getApplication();
        FakeProgressHandler progressHandler = new FakeProgressHandler();
        String subject = PackageInstallerService.getStringFromStatus(context, STATUS_FAILURE_SECURITY, "Example", null);
        NotificationCompat.Style details = new NotificationCompat.BigTextStyle()
                .bigText(subject + "\n\nINSTALL_FAILED_USER_RESTRICTED");

        PackageInstallerService.prepareFinalProgressNotification(progressHandler, "Example", subject, details);

        assertEquals("Example", progressHandler.lastInfo.getTitle());
        assertEquals(subject, progressHandler.lastInfo.getBody());
        assertEquals(details, progressHandler.lastInfo.getStyle());
        assertNotEquals(context.getString(R.string.done), progressHandler.lastInfo.getBody());
    }

    @Test
    public void recoveryHintFromStatusIsUserFacingText() {
        Context context = RuntimeEnvironment.getApplication();
        String hint = PackageInstallerService.getRecoveryHintFromStatus(context, STATUS_FAILURE_SECURITY);

        assertTrue(hint.contains("APK"));
        assertFalse(hint.contains("STATUS_FAILURE_SECURITY"));
    }

    @Test
    public void staleInstallSessionRequiresPositivePastTimestampBeyondTimeout() {
        long now = 60_000L;
        long timeout = 30_000L;

        assertTrue(PackageInstallerService.isStaleInstallSession(30_000L, now, timeout));
        assertFalse(PackageInstallerService.isStaleInstallSession(30_001L, now, timeout));
        assertFalse(PackageInstallerService.isStaleInstallSession(0L, now, timeout));
        assertFalse(PackageInstallerService.isStaleInstallSession(70_000L, now, timeout));
    }

    private static final class FakeProgressHandler extends QueuedProgressHandler {
        final NotificationInfo lastInfo = new NotificationInfo()
                .setTitle("Installing Example")
                .setBody("Installing");
        boolean progressTextCleared;

        @Override
        public void onQueue(@Nullable Object message) {
        }

        @Override
        public void onAttach(@Nullable Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
        }

        @Override
        public void onResult(@Nullable Object message) {
        }

        @Override
        public void onDetach(@Nullable Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return this;
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return lastInfo;
        }

        @Override
        public int getLastMax() {
            return 0;
        }

        @Override
        public float getLastProgress() {
            return 0;
        }

        @Override
        public void setProgressTextInterface(@Nullable ProgressTextInterface progressTextInterface) {
            progressTextCleared = progressTextInterface == null;
            super.setProgressTextInterface(progressTextInterface);
        }
    }
}
