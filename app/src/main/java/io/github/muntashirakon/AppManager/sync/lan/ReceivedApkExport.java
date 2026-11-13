// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import io.github.muntashirakon.AppManager.fm.FmProvider;

/**
 * Ways out for a received APK when this app's installer can't put it on: Android's own installer
 * (single APKs only, it doesn't take splits) or a copy in Downloads.
 */
public final class ReceivedApkExport {
    private static final int MAX_NAME_CHARS = 96;
    private static final int MAX_NAME_TRIES = 1000;

    /**
     * Where a saved copy goes. {@link #open} is called once, then {@link #commit} after the stream
     * is closed, or {@link #abort} when anything failed.
     */
    interface Destination {
        @NonNull
        OutputStream open(@NonNull String displayName, @NonNull String mimeType) throws IOException;

        /**
         * @return the name the file ended up with
         */
        @NonNull
        String commit() throws IOException;

        void abort();
    }

    private ReceivedApkExport() {
    }

    /**
     * Saves the entry in the public Downloads folder: the APK itself, or the splits as one .apks
     * bundle. Below Android 10 this needs the storage permission.
     *
     * @return the name of the saved file
     */
    @WorkerThread
    @NonNull
    public static String saveToDownloads(@NonNull Context context, @NonNull ReceivedApkStore.Entry entry)
            throws IOException {
        Destination destination = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? new MediaStoreDownloads(context.getContentResolver())
                : new FolderDestination(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS));
        return save(entry, destination);
    }

    @VisibleForTesting
    @WorkerThread
    @NonNull
    static String save(@NonNull ReceivedApkStore.Entry entry, @NonNull Destination destination) throws IOException {
        if (!entry.complete || !entry.hasAllFiles()) {
            throw new IOException("Some files of " + entry.app.label + " are missing.");
        }
        boolean bundle = entry.isBundle();
        List<File> files = entry.files();
        boolean saved = false;
        try {
            OutputStream out = destination.open(fileNameFor(entry.app, bundle),
                    bundle ? LanSyncDownloader.MIME_APKS : LanSyncDownloader.MIME_APK);
            if (bundle) {
                LanSyncDownloader.writeBundle(out, entry.app.files, files);
            } else {
                try (OutputStream o = out; InputStream in = new FileInputStream(files.get(0))) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        o.write(buffer, 0, n);
                    }
                }
            }
            String name = destination.commit();
            saved = true;
            return name;
        } finally {
            if (!saved) {
                destination.abort();
            }
        }
    }

    /**
     * Label and version, with anything a file name can't hold swapped for an underscore.
     */
    @NonNull
    static String fileNameFor(@NonNull SyncApp app, boolean bundle) {
        String name = clean(app.label);
        if (name.isEmpty()) {
            name = clean(app.packageName);
        }
        String version = clean(app.versionName);
        StringBuilder sb = new StringBuilder(name);
        if (!version.isEmpty()) {
            sb.append('_').append(version);
        }
        if (sb.length() > MAX_NAME_CHARS) {
            sb.setLength(MAX_NAME_CHARS);
        }
        return sb.append(bundle ? ".apks" : ".apk").toString();
    }

    @NonNull
    private static String clean(@Nullable String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ' ' ? c : '_');
        }
        String cleaned = sb.toString().trim();
        int start = 0;
        while (start < cleaned.length() && cleaned.charAt(start) == '.') {
            ++start;
        }
        return cleaned.substring(start);
    }

    /**
     * An intent that opens the received APK in Android's own package installer, never in this app
     * (which handles APKs too), or null for a split set or when no such installer is there. The
     * file goes out through {@link FmProvider} with a read grant.
     */
    @Nullable
    @SuppressWarnings("deprecation") // ACTION_INSTALL_PACKAGE: only installers handle it
    public static Intent systemInstallerIntent(@NonNull Context context, @NonNull ReceivedApkStore.Entry entry) {
        if (entry.isBundle() || entry.app.files.isEmpty()) {
            return null;
        }
        Uri uri = FmProvider.getContentUri(entry.file(entry.app.files.get(0).name));
        PackageManager pm = context.getPackageManager();
        for (String action : new String[]{Intent.ACTION_INSTALL_PACKAGE, Intent.ACTION_VIEW}) {
            Intent intent = new Intent(action).setDataAndType(uri, LanSyncDownloader.MIME_APK);
            ComponentName installer = systemHandler(pm, intent, context.getPackageName());
            if (installer != null) {
                intent.setComponent(installer);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("", uri));
                return intent;
            }
        }
        return null;
    }

    @Nullable
    private static ComponentName systemHandler(@NonNull PackageManager pm, @NonNull Intent intent,
                                               @NonNull String self) {
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo handler : handlers) {
            ActivityInfo activity = handler.activityInfo;
            if (activity == null || activity.applicationInfo == null || self.equals(activity.packageName)) {
                continue;
            }
            if ((activity.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return new ComponentName(activity.packageName, activity.name);
            }
        }
        return null;
    }

    /**
     * Downloads through MediaStore, which needs no permission from Android 10 on.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    static final class MediaStoreDownloads implements Destination {
        @NonNull
        private final ContentResolver mResolver;
        @Nullable
        private Uri mUri;
        @NonNull
        private String mName = "";

        MediaStoreDownloads(@NonNull ContentResolver resolver) {
            mResolver = resolver;
        }

        @NonNull
        @Override
        public OutputStream open(@NonNull String displayName, @NonNull String mimeType) throws IOException {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            mUri = mResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (mUri == null) {
                throw new IOException("Downloads didn't take the file.");
            }
            mName = displayName;
            OutputStream out = mResolver.openOutputStream(mUri, "w");
            if (out == null) {
                throw new IOException("Couldn't write to Downloads.");
            }
            return out;
        }

        @NonNull
        @Override
        public String commit() throws IOException {
            if (mUri == null) {
                throw new IOException("Nothing was written.");
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            mResolver.update(mUri, values, null, null);
            // MediaStore renames a file whose name is taken, so read back the one it used
            try (Cursor cursor = mResolver.query(mUri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) {
                    mName = cursor.getString(0);
                }
            }
            return mName;
        }

        @Override
        public void abort() {
            if (mUri != null) {
                try {
                    mResolver.delete(mUri, null, null);
                } catch (SecurityException | IllegalArgumentException ignore) {
                    // The pending row expires on its own
                }
            }
        }
    }

    /**
     * A plain folder, the public Downloads folder below Android 10. A taken name gets a number.
     */
    static final class FolderDestination implements Destination {
        @NonNull
        private final File mDir;
        @Nullable
        private File mFile;

        FolderDestination(@NonNull File dir) {
            mDir = dir;
        }

        @NonNull
        @Override
        public OutputStream open(@NonNull String displayName, @NonNull String mimeType) throws IOException {
            if (!mDir.mkdirs() && !mDir.isDirectory()) {
                throw new IOException("Couldn't open " + mDir + ".");
            }
            mFile = claimName(mDir, displayName);
            return new FileOutputStream(mFile);
        }

        @NonNull
        @Override
        public String commit() throws IOException {
            if (mFile == null) {
                throw new IOException("Nothing was written.");
            }
            return mFile.getName();
        }

        @Override
        public void abort() {
            LanSyncDownloader.deleteQuietly(mFile);
        }

        @NonNull
        private static File claimName(@NonNull File dir, @NonNull String name) throws IOException {
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            for (int i = 0; i < MAX_NAME_TRIES; ++i) {
                File file = new File(dir, i == 0 ? name : stem + " (" + i + ")" + extension);
                if (file.createNewFile()) {
                    return file;
                }
            }
            throw new IOException("Too many files named " + name + " in " + dir + ".");
        }
    }
}
