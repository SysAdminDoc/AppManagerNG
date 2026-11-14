// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import android.Manifest;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Keeps the apps database from being thrown away: before anything replaces or upgrades it, every file of it is copied
 * into {@code files/db_recovery/}, and the copy is read back and compared byte for byte.
 */
final class AppsDbRecovery {
    private static final String TAG = AppsDbRecovery.class.getSimpleName();

    @VisibleForTesting
    static final String DIR = "db_recovery";
    /** The on-disk version couldn't be read, so Room gets to decide. */
    static final int UNKNOWN_VERSION = -1;
    /** Copies kept per database; older ones are removed when a new one is made. */
    @VisibleForTesting
    static final int COPIES_KEPT = 3;

    @VisibleForTesting
    static final String NOTIFICATION_TAG = "apps_db_recovery";
    /** What the user wasn't told because notifications are off, shown on the app's main screen until dismissed. */
    @VisibleForTesting
    static final String UNSEEN_NOTICE = "unseen_notice.txt";
    private static final String REASON = "reason.txt";
    /** Every file the database can have, all deleted when it starts over. */
    private static final String[] SUFFIXES = {"", "-wal", "-shm", "-journal"};
    /**
     * The files a copy needs. {@code -shm} is left out: it's an index SQLite rebuilds from the WAL, and it changes under
     * an open connection, so a copy of it would rarely match the original.
     */
    private static final String[] COPIED_SUFFIXES = {"", "-wal", "-journal"};
    /** A copy still being written. One a killed process left behind is removed on the next open. */
    private static final String PARTIAL = ".partial";
    /**
     * A copy made before an upgrade. It's removed once the upgrade works, and until it's kept it doesn't push out any
     * older copy. One a killed process left behind is removed on the next open.
     */
    private static final String UPGRADING = ".upgrading";
    /** Launches in a row whose open failed with trouble Room retries quietly, before the user hears about it. */
    @VisibleForTesting
    static final int FAILED_OPENS_BEFORE_TELLING = 3;
    private static final String FAILED_OPENS = ".failed_opens";

    /** A notice that couldn't be written to {@link #UNSEEN_NOTICE}, kept for as long as this process runs. */
    @Nullable
    private static String sUnsavedNotice;

    @VisibleForTesting
    interface FileCopier {
        void copy(@NonNull File source, @NonNull File target) throws IOException;
    }

    @VisibleForTesting
    interface Notifier {
        void show(@NonNull String text);
    }

    /** Stands in for the notification in tests, to make it throw. */
    @VisibleForTesting
    @Nullable
    static volatile Notifier sNotifierForTest;

    private AppsDbRecovery() {
    }

    /**
     * Reads {@code PRAGMA user_version} through a read-only connection, which neither changes the journal mode nor
     * checkpoints the WAL. 0 means a new or empty file.
     */
    static int readVersion(@NonNull File dbFile) {
        if (!dbFile.isFile()) {
            return 0;
        }
        // The default error handler deletes a file it finds corrupt, so this one leaves the file for Room.
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS, dbObj -> {
                })) {
            return db.getVersion();
        } catch (SQLiteException e) {
            return UNKNOWN_VERSION;
        }
    }

    /** Whether the migrations walk {@code from} up to {@code to}, the way Room picks them. */
    static boolean canMigrate(int from, int to, @NonNull Migration[] migrations) {
        if (from == UNKNOWN_VERSION || from == 0 || from == to) {
            return true;
        }
        int version = from;
        while (version < to) {
            int next = -1;
            for (Migration migration : migrations) {
                if (migration.startVersion == version && migration.endVersion <= to
                        && migration.endVersion > next) {
                    next = migration.endVersion;
                }
            }
            if (next <= version) {
                return false;
            }
            version = next;
        }
        return version == to;
    }

    /**
     * Copies every file of the database into a new folder under {@link #DIR}, with a note saying why, then removes
     * all but the newest {@link #COPIES_KEPT} copies of it. The one just made is never removed.
     *
     * @return the folder holding the copy
     * @throws IOException if any file couldn't be copied exactly, in which case nothing may be deleted. No partial
     *                     folder is left behind.
     */
    @NonNull
    static File preserve(@NonNull Context context, @NonNull String name, int version, @NonNull String reason)
            throws IOException {
        return preserve(context, name, version, reason, false, AppsDbRecovery::copyExactly);
    }

    /**
     * Copies the database before an upgrade. The copy goes with {@link #discard} once the upgrade works, or becomes a
     * kept one with {@link #keep} if it doesn't, and until then it doesn't count toward {@link #COPIES_KEPT}.
     */
    @NonNull
    static File preserveBeforeUpgrade(@NonNull Context context, @NonNull String name, int version,
                                      @NonNull String reason) throws IOException {
        return preserve(context, name, version, reason, true, AppsDbRecovery::copyExactly);
    }

    @VisibleForTesting
    @NonNull
    static File preserve(@NonNull Context context, @NonNull String name, int version, @NonNull String reason,
                         boolean beforeUpgrade, @NonNull FileCopier copier) throws IOException {
        File dbFile = context.getDatabasePath(name);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        // The stamp comes first so the folders sort oldest to newest by name.
        File root = new File(context.getFilesDir(), DIR);
        String base = name + "-" + stamp + (version == UNKNOWN_VERSION ? "-unreadable" : "-v" + version);
        String kind = beforeUpgrade ? UPGRADING : "";
        File dir = new File(root, base + kind);
        for (int i = 2; dir.exists() || new File(root, dir.getName() + PARTIAL).exists(); ++i) {
            dir = new File(root, base + "-" + i + kind);
        }
        // Written under another name and renamed once complete, so a copy cut short never looks like a finished one.
        File partial = new File(root, dir.getName() + PARTIAL);
        if (!partial.mkdirs()) {
            throw new IOException("Could not create " + partial);
        }
        try {
            List<File> sources = new ArrayList<>();
            for (String suffix : COPIED_SUFFIXES) {
                File source = new File(dbFile.getPath() + suffix);
                if (source.isFile()) {
                    copier.copy(source, new File(partial, source.getName()));
                    sources.add(source);
                }
            }
            // A connection still open (as when SQLite reports corruption) can write the main file while the WAL is
            // copied, which leaves files that each match but don't fit together. Checked once all are copied.
            for (File source : sources) {
                if (!Arrays.equals(sha256Of(source), sha256Of(new File(partial, source.getName())))) {
                    throw new IOException(source + " changed while it was copied");
                }
            }
            writeReason(partial, reason);
            if (!partial.renameTo(dir)) {
                throw new IOException("Could not rename " + partial + " to " + dir);
            }
        } catch (IOException | RuntimeException e) {
            deleteRecursively(partial);
            throw e;
        }
        if (!beforeUpgrade) {
            prune(context, name, dir);
        }
        return dir;
    }

    /**
     * Keeps a copy made by {@link #preserveBeforeUpgrade}, with a note saying why, and removes the oldest copies past
     * {@link #COPIES_KEPT}.
     *
     * @return the kept copy, or null if it's missing or couldn't be kept, so a new one has to be made
     */
    @Nullable
    static File keep(@NonNull Context context, @NonNull String name, @NonNull File copy, @NonNull String reason) {
        if (!new File(copy, context.getDatabasePath(name).getName()).isFile()) {
            Log.w(TAG, "The copy in " + copy + " is gone");
            return null;
        }
        String copyName = copy.getName();
        File kept = copyName.endsWith(UPGRADING)
                ? new File(copy.getParentFile(), copyName.substring(0, copyName.length() - UPGRADING.length()))
                : copy;
        if (kept != copy && (kept.exists() || !copy.renameTo(kept))) {
            Log.w(TAG, "Could not rename " + copy + " to " + kept);
            return null;
        }
        try {
            writeReason(kept, reason);
        } catch (IOException e) {
            Log.w(TAG, "Could not write why " + kept + " was kept", e);
        }
        prune(context, name, kept);
        return kept;
    }

    /** Removes a copy made by {@link #preserveBeforeUpgrade} that isn't needed any more. */
    static void discard(@NonNull File copy) {
        deleteRecursively(copy);
    }

    /** Removes copies of {@code name} that a killed process left half made or never kept. */
    static void clearLeftovers(@NonNull Context context, @NonNull String name) {
        File[] dirs = new File(context.getFilesDir(), DIR).listFiles(file -> file.isDirectory()
                && file.getName().startsWith(name + "-")
                && (file.getName().endsWith(PARTIAL) || file.getName().endsWith(UPGRADING)));
        if (dirs != null) {
            for (File dir : dirs) {
                deleteRecursively(dir);
            }
        }
    }

    static void writeReason(@NonNull File copy, @NonNull String reason) throws IOException {
        try (OutputStream out = new FileOutputStream(new File(copy, REASON))) {
            out.write((reason + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Deletes the database and every file that goes with it.
     *
     * @return whether none of them is left
     */
    static boolean deleteDatabaseFiles(@NonNull Context context, @NonNull String name) {
        File dbFile = context.getDatabasePath(name);
        boolean allGone = true;
        for (String suffix : SUFFIXES) {
            File file = new File(dbFile.getPath() + suffix);
            if (file.exists() && !file.delete()) {
                allGone = false;
            }
        }
        return allGone;
    }

    static void deleteRecursively(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete " + file);
        }
    }

    /**
     * Tells the user what happened to their saved data: started over with the old file in {@code copy}; or, when
     * {@code inMemory}, that this run keeps changes in memory only, with the old file copied to {@code copy} or, when
     * it's null, left untouched. Without notifications the message waits for the app's main screen.
     */
    static void notifyUser(@NonNull Context context, @Nullable File copy, boolean inMemory) {
        String text;
        if (!inMemory) {
            text = context.getString(R.string.apps_db_recovered_text, String.valueOf(copy));
        } else if (copy != null) {
            text = context.getString(R.string.apps_db_copied_in_memory_text, copy.getAbsolutePath());
        } else {
            text = context.getString(R.string.apps_db_in_memory_text);
        }
        deliver(context, text);
    }

    /** Tells the user SQLite found the file corrupt but it couldn't be copied, so it's still used as it is. */
    static void notifyLeftInPlace(@NonNull Context context) {
        deliver(context, context.getString(R.string.apps_db_corrupt_kept_text));
    }

    /** Tells the user the file needed an upgrade but couldn't be copied first, so this run keeps data in memory. */
    static void notifyUpgradeSkipped(@NonNull Context context) {
        deliver(context, context.getString(R.string.apps_db_upgrade_skipped_text));
    }

    /**
     * Counts a launch whose open failed with trouble a fresh file wouldn't fix. Room retries those quietly, so after
     * {@link #FAILED_OPENS_BEFORE_TELLING} in a row the user is told, and the count starts again.
     */
    static void countFailedOpen(@NonNull Context context, @NonNull String name) {
        File counter = failedOpens(context, name);
        int count = readCount(counter) + 1;
        if (count >= FAILED_OPENS_BEFORE_TELLING) {
            deliver(context, context.getString(R.string.apps_db_open_failing_text));
            count = 0;
        }
        if (count == 0) {
            clearFailedOpens(context, name);
            return;
        }
        try (OutputStream out = new FileOutputStream(counter)) {
            out.write(String.valueOf(count).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Storage too full or broken to keep a count, which is reason enough to say so now
            Log.w(TAG, "Could not count the failed open in " + counter, e);
            deliver(context, context.getString(R.string.apps_db_open_failing_text));
        }
    }

    /** The database opened, so earlier failures to open it no longer count. */
    static void clearFailedOpens(@NonNull Context context, @NonNull String name) {
        File counter = failedOpens(context, name);
        if (counter.exists() && !counter.delete()) {
            Log.w(TAG, "Could not delete " + counter);
        }
    }

    @NonNull
    private static File failedOpens(@NonNull Context context, @NonNull String name) {
        // Kept out of backups: it describes this device's storage
        return new File(ContextCompat.getNoBackupFilesDir(context), name + FAILED_OPENS);
    }

    private static int readCount(@NonNull File counter) {
        if (!counter.isFile()) {
            return 0;
        }
        try (InputStream in = new FileInputStream(counter)) {
            byte[] bytes = new byte[16];
            int length = Math.max(in.read(bytes), 0);
            return Math.max(Integer.parseInt(new String(bytes, 0, length, StandardCharsets.UTF_8).trim()), 0);
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "Could not read " + counter, e);
            return 0;
        }
    }

    private static void deliver(@NonNull Context context, @NonNull String text) {
        if (canNotify(context)) {
            try {
                Notifier notifier = sNotifierForTest;
                if (notifier != null) {
                    notifier.show(text);
                    return;
                }
                NotificationUtils.displayHighPriorityNotification(context, NOTIFICATION_TAG, builder -> builder
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setContentTitle(context.getString(R.string.apps_db_recovered_title))
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .build());
                return;
            } catch (RuntimeException e) {
                // Some systems throw from notify() even with notifications allowed. The main screen shows it instead.
                Log.w(TAG, "Could not show the notification, so it waits for the app", e);
            }
        }
        saveUnseenNotice(context, text);
    }

    /**
     * Keeps a message for the app's main screen. It's written whole and then renamed, so a reader never sees half. If
     * it can't be written, it's kept in memory for as long as this process runs.
     */
    static synchronized void saveUnseenNotice(@NonNull Context context, @NonNull String text) {
        File notice = unseenNotice(context);
        File temp = new File(notice.getPath() + ".tmp");
        File parent = notice.getParentFile();
        try {
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // Android replaces the target in one step. Hosts that won't rename over a file get it deleted first.
            if (!temp.renameTo(notice) && !(notice.delete() && temp.renameTo(notice))) {
                throw new IOException("Could not rename " + temp + " to " + notice);
            }
            sUnsavedNotice = null;
        } catch (IOException e) {
            Log.e(TAG, "Could not keep the notice for later, so it's only kept until the app closes: " + text, e);
            sUnsavedNotice = text;
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** The message notifications couldn't show, if there is one. It stays until {@link #dismissUnseenNotice}. */
    @Nullable
    static synchronized String peekUnseenNotice(@NonNull Context context) {
        String text = readUnseenNotice(unseenNotice(context));
        return text != null ? text : sUnsavedNotice;
    }

    /** Removes {@code text} once the user has dismissed it, unless a newer message replaced it in the meantime. */
    static synchronized void dismissUnseenNotice(@NonNull Context context, @NonNull String text) {
        if (text.equals(sUnsavedNotice)) {
            sUnsavedNotice = null;
        }
        File notice = unseenNotice(context);
        if (text.equals(readUnseenNotice(notice)) && !notice.delete()) {
            Log.w(TAG, "Could not delete " + notice);
        }
    }

    @NonNull
    private static File unseenNotice(@NonNull Context context) {
        return new File(new File(context.getFilesDir(), DIR), UNSEEN_NOTICE);
    }

    @Nullable
    private static String readUnseenNotice(@NonNull File notice) {
        if (!notice.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(notice)) {
            byte[] bytes = new byte[(int) Math.min(notice.length(), 64 * 1024)];
            int length = 0;
            int read;
            while (length < bytes.length && (read = in.read(bytes, length, bytes.length - length)) != -1) {
                length += read;
            }
            String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            Log.w(TAG, "Could not read " + notice, e);
            return null;
        }
    }

    /**
     * Wraps the framework open helper so that a file SQLite reports as corrupt is copied aside before it's deleted.
     * Room keeps the androidx default, which deletes it with no copy and no word to the user. If the copy fails, the
     * file stays where it is and the error reaches the caller.
     */
    @NonNull
    static SupportSQLiteOpenHelper.Factory corruptionGuard(@NonNull Context context, @NonNull String name) {
        FrameworkSQLiteOpenHelperFactory framework = new FrameworkSQLiteOpenHelperFactory();
        return configuration -> framework.create(SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(new CorruptionGuard(context, name, configuration.callback))
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build());
    }

    private static final class CorruptionGuard extends SupportSQLiteOpenHelper.Callback {
        @NonNull
        private final Context mContext;
        @NonNull
        private final String mName;
        @NonNull
        private final SupportSQLiteOpenHelper.Callback mDelegate;

        CorruptionGuard(@NonNull Context context, @NonNull String name,
                        @NonNull SupportSQLiteOpenHelper.Callback delegate) {
            super(delegate.version);
            mContext = context;
            mName = name;
            mDelegate = delegate;
        }

        @Override
        public void onConfigure(@NonNull SupportSQLiteDatabase db) {
            mDelegate.onConfigure(db);
        }

        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            mDelegate.onCreate(db);
        }

        @Override
        public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
            mDelegate.onUpgrade(db, oldVersion, newVersion);
        }

        @Override
        public void onDowngrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
            mDelegate.onDowngrade(db, oldVersion, newVersion);
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            mDelegate.onOpen(db);
        }

        @Override
        public void onCorruption(@NonNull SupportSQLiteDatabase db) {
            File copy;
            try {
                copy = preserve(mContext, mName, UNKNOWN_VERSION, "SQLite reported the file as corrupt");
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not copy the corrupt " + mName + " aside, so it stays where it is", e);
                notifyLeftInPlace(mContext);
                return;
            }
            Log.e(TAG, "SQLite reported " + mName + " as corrupt. It's copied to " + copy + " and started over.");
            mDelegate.onCorruption(db);
            notifyUser(mContext, copy, false);
        }
    }

    /**
     * Removes all but the newest {@link #COPIES_KEPT} kept copies of {@code name}. {@code keep}, the copy just made, is
     * never one of them, even when a clock that went back makes it sort oldest. Copies before an upgrade and half-made
     * ones aren't counted.
     */
    private static void prune(@NonNull Context context, @NonNull String name, @NonNull File keep) {
        File[] dirs = new File(context.getFilesDir(), DIR).listFiles(file -> file.isDirectory()
                && file.getName().startsWith(name + "-")
                && !file.getName().endsWith(PARTIAL) && !file.getName().endsWith(UPGRADING)
                && !file.getName().equals(keep.getName()));
        if (dirs == null || dirs.length < COPIES_KEPT) {
            return;
        }
        List<File> sorted = new ArrayList<>(Arrays.asList(dirs));
        Collections.sort(sorted, (a, b) -> a.getName().compareTo(b.getName()));
        for (File old : sorted.subList(0, sorted.size() - (COPIES_KEPT - 1))) {
            deleteRecursively(old);
        }
    }

    private static boolean canNotify(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            return false;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        // A blocked Alerts channel drops the notification without a word. Before it exists it can't be blocked.
        NotificationChannelCompat channel = manager.getNotificationChannelCompat(NotificationUtils.HIGH_PRIORITY_CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManagerCompat.IMPORTANCE_NONE;
    }

    private static void copyExactly(@NonNull File source, @NonNull File target) throws IOException {
        byte[] expected;
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
            expected = digest.digest();
        }
        if (!Arrays.equals(expected, sha256Of(target)) || !Arrays.equals(expected, sha256Of(source))) {
            throw new IOException("The copy of " + source + " doesn't match the original");
        }
    }

    @NonNull
    private static byte[] sha256Of(@NonNull File file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    @NonNull
    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
