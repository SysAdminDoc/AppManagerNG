// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.MutationImageDao;
import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * The append-only store of mutation images. Each row's MAC covers its fields and the MAC of the row before it,
 * keyed with a secret only this install has, so a row changed or removed outside the app no longer verifies.
 * A plain hash wouldn't do: anyone who can edit the database can recompute one from the public code.
 * <p>
 * A row is trusted when its own MAC checks out and it links to the row stored before it. The first row left is the
 * anchor, so pruning the oldest rows keeps the rest trusted.
 */
public final class MutationJournal {
    public static final String TAG = MutationJournal.class.getSimpleName();

    /** The write went through and the value read back is the one asked for. */
    public static final String RESULT_APPLIED = "applied";
    /** The write went through, but the value read back differs or couldn't be read. */
    public static final String RESULT_UNCONFIRMED = "unconfirmed";
    /** The write failed. The value read back still says whether anything changed. */
    public static final String RESULT_FAILED = "failed";

    static final String KEY_FILE = "mutation_journal.key";
    private static final int KEY_BYTES = 32;
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final String MAC_VERSION = "v1";

    private static final Object sLock = new Object();
    @Nullable
    private static byte[] sKey;

    /** One stored image and whether it verified. */
    public static final class Entry {
        @NonNull
        public final MutationImage image;
        public final boolean trusted;

        Entry(@NonNull MutationImage image, boolean trusted) {
            this.image = image;
            this.trusted = trusted;
        }
    }

    @NonNull
    private final AppsDb mDb;
    @NonNull
    private final byte[] mKey;

    @VisibleForTesting
    MutationJournal(@NonNull AppsDb db, @NonNull byte[] key) {
        mDb = db;
        mKey = key.clone();
    }

    @WorkerThread
    @NonNull
    public static MutationJournal getInstance() {
        Context context = ContextUtils.getContext();
        return new MutationJournal(AppsDb.getInstance(), loadKey(context));
    }

    /** Fills in the chain fields and stores the image. Returns its row id. */
    @WorkerThread
    public long append(@NonNull MutationImage image) {
        synchronized (sLock) {
            MutationImageDao dao = mDb.mutationImageDao();
            return mDb.runInTransaction(() -> {
                MutationImage last = dao.getLast();
                image.id = 0;
                image.prevMac = last != null ? last.mac : "";
                image.mac = mac(mKey, image);
                image.id = dao.insert(image);
                return image.id;
            });
        }
    }

    /** Every stored image, oldest first, with whether it verified. */
    @WorkerThread
    @NonNull
    public List<Entry> readAll() {
        return verify(mKey, mDb.mutationImageDao().getAll());
    }

    /** The images of one app, oldest first. The whole chain is checked, since a link needs the row before it. */
    @WorkerThread
    @NonNull
    public List<Entry> readFor(@NonNull String packageName, int userId) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : readAll()) {
            if (entry.image.userId == userId && packageName.equals(entry.image.packageName)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Drops the oldest run of images written before {@code cutoffTime}; see {@link MutationImageDao#deleteOldestBefore}. */
    @WorkerThread
    public int prune(long cutoffTime) {
        synchronized (sLock) {
            return mDb.mutationImageDao().deleteOldestBefore(cutoffTime);
        }
    }

    @WorkerThread
    public void clear() {
        synchronized (sLock) {
            mDb.mutationImageDao().deleteAll();
        }
    }

    @VisibleForTesting
    @NonNull
    static List<Entry> verify(@NonNull byte[] key, @NonNull List<MutationImage> rows) {
        List<Entry> entries = new ArrayList<>(rows.size());
        String previousMac = null;
        for (MutationImage row : rows) {
            boolean macOk = constantTimeEquals(mac(key, row), row.mac);
            boolean linkOk = previousMac == null || previousMac.equals(row.prevMac);
            entries.add(new Entry(row, macOk && linkOk));
            previousMac = row.mac;
        }
        return Collections.unmodifiableList(entries);
    }

    /** The images as JSON for the history export, each with whether it verified. */
    @NonNull
    public static JSONArray toJson(@NonNull List<Entry> entries) throws JSONException {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            MutationImage image = entry.image;
            JSONObject object = new JSONObject();
            object.put("id", image.id);
            object.put("execution_id", image.executionId);
            object.put("executor", image.executor);
            object.put("operation", image.operation);
            object.put("package_name", image.packageName);
            object.put("user_id", image.userId);
            object.put("kind", image.kind);
            object.put("target", image.target);
            object.put("before_value", image.beforeValue != null ? image.beforeValue : JSONObject.NULL);
            object.put("requested_value", image.requestedValue);
            object.put("post_value", image.postValue != null ? image.postValue : JSONObject.NULL);
            object.put("result", image.result);
            object.put("time", image.time);
            object.put("prev_mac", image.prevMac);
            object.put("mac", image.mac);
            object.put("verified", entry.trusted);
            array.put(object);
        }
        return array;
    }

    @VisibleForTesting
    @NonNull
    static String mac(@NonNull byte[] key, @NonNull MutationImage image) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, MAC_ALGORITHM));
            return toHex(mac.doFinal(canonical(image)));
        } catch (GeneralSecurityException | IOException e) {
            // Every Android release ships HmacSHA256, so this is a broken runtime, not a bad row
            throw new IllegalStateException(e);
        }
    }

    /** Each field length-prefixed, so no two different rows encode to the same bytes. */
    @NonNull
    private static byte[] canonical(@NonNull MutationImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeField(out, MAC_VERSION);
            writeField(out, image.prevMac);
            writeField(out, image.executionId);
            writeField(out, image.executor);
            writeField(out, image.operation);
            writeField(out, image.packageName);
            out.writeInt(image.userId);
            writeField(out, image.kind);
            writeField(out, image.target);
            writeField(out, image.beforeValue);
            writeField(out, image.requestedValue);
            writeField(out, image.postValue);
            writeField(out, image.result);
            out.writeLong(image.time);
        }
        return bytes.toByteArray();
    }

    private static void writeField(@NonNull DataOutputStream out, @Nullable String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static boolean constantTimeEquals(@NonNull String a, @Nullable String b) {
        if (b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; ++i) {
            out[i * 2] = digits[(bytes[i] >> 4) & 0xf];
            out[i * 2 + 1] = digits[bytes[i] & 0xf];
        }
        return new String(out);
    }

    /**
     * The install's key, made on first use. A key that can't be saved still signs this run's rows; they won't
     * verify after a restart, which only sends them to manual review.
     */
    @NonNull
    static byte[] loadKey(@NonNull Context context) {
        synchronized (sLock) {
            if (sKey != null) {
                return sKey;
            }
            File file = new File(context.getFilesDir(), KEY_FILE);
            try {
                if (file.isFile()) {
                    byte[] stored = readKey(file);
                    if (stored != null) {
                        sKey = stored;
                        return sKey;
                    }
                    Log.w(TAG, "The journal key has the wrong length, so earlier images won't verify");
                }
            } catch (IOException e) {
                Log.w(TAG, "Could not read the journal key, so earlier images won't verify", e);
            }
            byte[] key = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(key);
            try {
                writeKey(file, key);
            } catch (IOException e) {
                Log.e(TAG, "Could not save the journal key; this run's images won't verify after a restart", e);
            }
            sKey = key;
            return sKey;
        }
    }

    /** The key, or null when the file isn't exactly one key long. */
    @Nullable
    private static byte[] readKey(@NonNull File file) throws IOException {
        if (file.length() != KEY_BYTES) {
            return null;
        }
        byte[] key = new byte[KEY_BYTES];
        try (FileInputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < KEY_BYTES) {
                int count = in.read(key, read, KEY_BYTES - read);
                if (count < 0) {
                    return null;
                }
                read += count;
            }
            return in.read() < 0 ? key : null;
        }
    }

    private static void writeKey(@NonNull File file, @NonNull byte[] key) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Could not make " + dir);
        }
        File temp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(key);
            out.getFD().sync();
        }
        if (!temp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not move the key into " + file);
        }
    }

    @VisibleForTesting
    static void forgetCachedKey() {
        synchronized (sLock) {
            sKey = null;
        }
    }
}
