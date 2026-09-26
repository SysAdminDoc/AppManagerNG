// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;

/**
 * A shared file that turns out not to be an APK, or whose read grant is gone, has to end with App Details
 * reporting that it could not fetch the package, not with a crash.
 */
@RunWith(RobolectricTestRunner.class)
public class AppDetailsExternalApkFailureTest {
    private static final String REVOKED_AUTHORITY = "io.github.muntashirakon.AppManager.test.revoked";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void wrongMimeTypeEndsWithoutPackageInfo() throws Exception {
        File text = tmp.newFile("notes.txt");
        Files.write(text.toPath(), "not an apk".getBytes(StandardCharsets.UTF_8));
        ApkSource source = ApkSource.getApkSource(Uri.fromFile(text), "text/plain");

        assertThrows(ApkFile.ApkFileException.class, source::resolve);
        assertEquals(Collections.singletonList(null), loadInViewModel(source));
    }

    @Test
    public void revokedGrantEndsWithoutPackageInfo() throws Exception {
        Robolectric.setupContentProvider(RevokedGrantProvider.class, REVOKED_AUTHORITY);
        Uri uri = Uri.parse("content://" + REVOKED_AUTHORITY + "/shared/app.apk");
        ApkSource source = ApkSource.getApkSource(uri, "application/vnd.android.package-archive");

        assertThrows(ApkFile.ApkFileException.class, source::resolve);
        assertEquals(Collections.singletonList(null), loadInViewModel(source));
    }

    @NonNull
    private static List<PackageInfo> loadInViewModel(@NonNull ApkSource source) throws InterruptedException {
        AppDetailsViewModel model = new AppDetailsViewModel(RuntimeEnvironment.getApplication());
        List<PackageInfo> results = new ArrayList<>();
        try {
            model.setPackage(source).observeForever(results::add);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (results.isEmpty() && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
        } finally {
            model.onCleared();
        }
        return results;
    }

    /** Behaves like a provider after the sender's read grant has been revoked. */
    public static class RevokedGrantProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Nullable
        @Override
        public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
            throw denied(uri);
        }

        @Nullable
        @Override
        public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
            throw denied(uri);
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            throw denied(uri);
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            throw denied(uri);
        }

        @Override
        public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
            throw denied(uri);
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                          @Nullable String[] selectionArgs) {
            throw denied(uri);
        }

        @NonNull
        private static SecurityException denied(@NonNull Uri uri) {
            return new SecurityException("Permission Denial: reading " + uri + " requires a URI grant");
        }
    }
}
