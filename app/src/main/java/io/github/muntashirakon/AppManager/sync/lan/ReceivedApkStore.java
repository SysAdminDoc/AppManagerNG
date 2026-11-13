// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * APKs that arrived from another device, kept for a while so they can be installed again without
 * it, and downloads that broke off, kept so they can continue.
 * <p>
 * Each entry is a folder named after what the host listed: the package, its version code, every
 * file name and size, and the signers. A different listing is a different entry, so a finished
 * entry is only ever reused for exactly what it holds. A finished file carries its own name and a
 * download in progress carries {@code .part} after it. Nothing here is trusted on its own: every
 * install runs the files through {@link ApkSignatureGate} again.
 */
public final class ReceivedApkStore {
    public static final String DIR_NAME = "received";
    static final String PART_SUFFIX = ".part";
    private static final String META = "meta.json";
    private static final String META_TEMP = "meta.json.tmp";
    private static final int KEY_HEX_CHARS = 32;
    /**
     * Every store instance works on the same folders, so they share one lock.
     */
    private static final Object LOCK = new Object();

    public static final class Entry {
        @NonNull
        public final String key;
        @NonNull
        public final File dir;
        @NonNull
        public final SyncApp app;
        public final boolean complete;
        /**
         * When it was last written to. Retention counts from here.
         */
        public final long updatedAt;

        Entry(@NonNull String key, @NonNull File dir, @NonNull SyncApp app, boolean complete, long updatedAt) {
            this.key = key;
            this.dir = dir;
            this.app = app;
            this.complete = complete;
            this.updatedAt = updatedAt;
        }

        @NonNull
        public File file(@NonNull String name) {
            return new File(dir, name);
        }

        @NonNull
        public File part(@NonNull String name) {
            return new File(dir, name + PART_SUFFIX);
        }

        /**
         * The finished files in the order the host listed them.
         */
        @NonNull
        public List<File> files() {
            List<File> files = new ArrayList<>(app.files.size());
            for (SyncApp.ApkFileInfo info : app.files) {
                files.add(file(info.name));
            }
            return files;
        }

        public boolean isBundle() {
            return app.files.size() > 1;
        }

        /**
         * Bytes already here, finished or not.
         */
        public long bytesHere() {
            long bytes = 0;
            for (SyncApp.ApkFileInfo info : app.files) {
                File file = file(info.name);
                if (file.isFile()) {
                    bytes += Math.min(file.length(), info.size);
                } else {
                    File part = part(info.name);
                    if (part.isFile()) {
                        bytes += Math.min(part.length(), info.size);
                    }
                }
            }
            return bytes;
        }

        /**
         * Whether every file is finished and has the size the host listed.
         */
        public boolean hasAllFiles() {
            for (SyncApp.ApkFileInfo info : app.files) {
                File file = file(info.name);
                if (!file.isFile() || file.length() != info.size) {
                    return false;
                }
            }
            return true;
        }
    }

    @NonNull
    private final File mRoot;

    /**
     * @param syncDir the Local sync cache folder, {@link LanSyncDownloader#DIR_NAME} in the cache
     */
    public ReceivedApkStore(@NonNull File syncDir) {
        mRoot = new File(syncDir, DIR_NAME);
    }

    @NonNull
    public static ReceivedApkStore of(@NonNull Context context) {
        return new ReceivedApkStore(new File(context.getCacheDir(), LanSyncDownloader.DIR_NAME));
    }

    /**
     * Names the entry for one listing. Two listings share a key only when they describe the same
     * files, signed by the same keys.
     */
    @NonNull
    static String keyOf(@NonNull SyncApp app) {
        StringBuilder sb = new StringBuilder();
        sb.append(app.packageName).append('\n').append(app.versionCode).append('\n');
        for (SyncApp.ApkFileInfo info : app.files) {
            sb.append(info.name).append(':').append(info.size).append('\n');
        }
        List<String> signers = new ArrayList<>(app.signers);
        Collections.sort(signers);
        for (String signer : signers) {
            sb.append(signer).append('\n');
        }
        MessageDigest digest = SyncCrypto.sha256();
        return SyncCrypto.toHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)))
                .substring(0, KEY_HEX_CHARS);
    }

    /**
     * The entry for this listing, made if it isn't here yet. Opening it counts as a write, so an
     * old unfinished download isn't cleaned up while it continues.
     */
    @NonNull
    public Entry open(@NonNull SyncApp app, long now) throws IOException {
        String key = keyOf(app);
        synchronized (LOCK) {
            File dir = new File(mRoot, key);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Could not create " + dir);
            }
            Entry existing = read(dir);
            boolean complete = existing != null && existing.complete;
            Entry entry = new Entry(key, dir, app, complete, now);
            write(entry);
            return entry;
        }
    }

    @NonNull
    public Entry markComplete(@NonNull Entry entry, long now) throws IOException {
        synchronized (LOCK) {
            Entry done = new Entry(entry.key, entry.dir, entry.app, true, now);
            write(done);
            return done;
        }
    }

    @Nullable
    public Entry find(@NonNull String key) {
        if (!isKey(key)) {
            return null;
        }
        synchronized (LOCK) {
            return read(new File(mRoot, key));
        }
    }

    /**
     * Every entry, newest first. A folder that isn't a readable entry is removed on the way.
     */
    @NonNull
    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        synchronized (LOCK) {
            File[] dirs = mRoot.listFiles();
            if (dirs == null) {
                return entries;
            }
            for (File dir : dirs) {
                Entry entry = dir.isDirectory() && isKey(dir.getName()) ? read(dir) : null;
                if (entry == null) {
                    deleteTree(dir);
                } else {
                    entries.add(entry);
                }
            }
        }
        Collections.sort(entries, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return entries;
    }

    public void delete(@NonNull Entry entry) {
        synchronized (LOCK) {
            deleteTree(entry.dir);
        }
    }

    /**
     * Removes entries nobody wrote to for longer than {@code maxAgeMillis}, finished or not.
     *
     * @return how many went
     */
    public int purge(long maxAgeMillis, long now) {
        int removed = 0;
        for (Entry entry : list()) {
            if (now - entry.updatedAt > maxAgeMillis) {
                delete(entry);
                ++removed;
            }
        }
        return removed;
    }

    /**
     * Removes entries whose app is already installed at the same version or a later one, signed
     * by the same key. They have nothing left to offer.
     *
     * @return how many went
     */
    public int removeInstalled(@NonNull Map<String, SyncApp> installed) {
        int removed = 0;
        for (Entry entry : list()) {
            SyncComparator.Status status = SyncComparator.compare(entry.app, installed.get(entry.app.packageName));
            if (status == SyncComparator.Status.SAME || status == SyncComparator.Status.OLDER) {
                delete(entry);
                ++removed;
            }
        }
        return removed;
    }

    // Storage

    private static boolean isKey(@NonNull String name) {
        if (name.length() != KEY_HEX_CHARS) {
            return false;
        }
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static void write(@NonNull Entry entry) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("package", entry.app.packageName);
            json.put("label", entry.app.label);
            json.put("versionCode", entry.app.versionCode);
            json.put("versionName", entry.app.versionName);
            json.put("system", entry.app.system);
            json.put("signers", new JSONArray(entry.app.signers));
            json.put("pastSigners", new JSONArray(entry.app.pastSigners));
            JSONArray files = new JSONArray();
            for (SyncApp.ApkFileInfo info : entry.app.files) {
                files.put(new JSONObject().put("name", info.name).put("size", info.size));
            }
            json.put("files", files);
            json.put("complete", entry.complete);
            json.put("updatedAt", entry.updatedAt);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File temp = new File(entry.dir, META_TEMP);
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
        File meta = new File(entry.dir, META);
        if (!temp.renameTo(meta)) {
            // Some file systems won't rename over an existing file
            if (!meta.delete() || !temp.renameTo(meta)) {
                throw new IOException("Could not save " + meta);
            }
        }
    }

    /**
     * Reads an entry back. Anything that doesn't check out the way the host's list is checked
     * reads as no entry, so a damaged file can't name a path outside the folder.
     */
    @Nullable
    private static Entry read(@NonNull File dir) {
        File meta = new File(dir, META);
        if (!meta.isFile() || meta.length() > SyncMessages.MAX_CONTROL * 16L) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(new String(readAll(meta), StandardCharsets.UTF_8));
            String packageName = json.getString("package");
            if (!SyncMessages.isValidPackageName(packageName)) {
                return null;
            }
            JSONArray filesJson = json.getJSONArray("files");
            if (filesJson.length() == 0 || filesJson.length() > SyncMessages.MAX_FILES) {
                return null;
            }
            List<SyncApp.ApkFileInfo> files = new ArrayList<>(filesJson.length());
            for (int i = 0; i < filesJson.length(); ++i) {
                JSONObject file = filesJson.getJSONObject(i);
                String name = file.getString("name");
                long size = file.getLong("size");
                if (!SyncMessages.isSafeFileName(name) || size < 0 || size > SyncMessages.MAX_FILE_SIZE) {
                    return null;
                }
                files.add(new SyncApp.ApkFileInfo(name, size));
            }
            SyncApp app = new SyncApp(packageName, json.optString("label", packageName),
                    json.getLong("versionCode"), json.optString("versionName", ""), json.optBoolean("system"),
                    strings(json.getJSONArray("signers")), strings(json.getJSONArray("pastSigners")), files);
            if (!dir.getName().equals(keyOf(app))) {
                return null;
            }
            return new Entry(dir.getName(), dir, app, json.getBoolean("complete"), json.getLong("updatedAt"));
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        }
    }

    @NonNull
    private static List<String> strings(@NonNull JSONArray array) throws JSONException {
        if (array.length() > SyncMessages.MAX_SIGNERS) {
            throw new JSONException("Too many signers");
        }
        List<String> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); ++i) {
            list.add(array.getString(i));
        }
        return list;
    }

    @NonNull
    private static byte[] readAll(@NonNull File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < bytes.length) {
                int n = in.read(bytes, off, bytes.length - off);
                if (n < 0) {
                    throw new IOException("Short read of " + file);
                }
                off += n;
            }
        }
        return bytes;
    }

    private static void deleteTree(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
