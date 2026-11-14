// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.muntashirakon.AppManager.utils.DurableFile;

/**
 * The user's signer pins, one per package, in their own file.
 *
 * <p>They aren't kept with the signing-certificate snapshots: a snapshot is dropped when its app is
 * uninstalled and an unreadable snapshot file reads as empty, and a pin has to outlive the app (it
 * guards the next fresh install) and fail closed. A store that exists but can't be read makes every
 * read and write here throw {@link UnreadableException}, so the installer refuses instead of
 * installing past a pin it can't see. {@link #takeAll()} is the way out.
 *
 * <p>Schema:
 * <pre>
 * {
 *   "schema_version": 1,
 *   "pins": {
 *     "com.foo.bar": {"cert_shas256": ["ab12..."], "pinned_at": 1700000000000}
 *   }
 * }
 * </pre>
 */
public final class SignerPinStore {
    static final String FILE_NAME = "signer_pins.json";
    @VisibleForTesting
    static final int SCHEMA_VERSION = 1;
    @VisibleForTesting
    static final long MAX_STORE_BYTES = 4L * 1024 * 1024;

    /** The store is on disk but can't be read; nothing it guards may go ahead. */
    public static final class UnreadableException extends IOException {
        UnreadableException(@NonNull String message) {
            super(message);
        }
    }

    /** The pin isn't the one the user was shown anymore, so the change they chose wasn't made. */
    public static final class ChangedException extends IOException {
        ChangedException(@NonNull String message) {
            super(message);
        }
    }

    // One lock for every instance: the installer and the UI each make their own.
    private static final Object LOCK = new Object();

    @NonNull
    private final DurableFile mFile;

    public SignerPinStore(@NonNull Context context) {
        this(new File(context.getApplicationContext().getFilesDir(), FILE_NAME));
    }

    @VisibleForTesting
    public SignerPinStore(@NonNull File file) {
        mFile = new DurableFile(file);
    }

    @WorkerThread
    @NonNull
    public Map<String, SignerPin> readAll() throws UnreadableException {
        synchronized (LOCK) {
            String json = mFile.read(MAX_STORE_BYTES);
            if (json == null) {
                // Anything at the path counts, a folder too, and so does a backup that couldn't be
                // put back: only a missing list means no pins
                if (mFile.getBaseFile().exists() || mFile.hasUnrestoredBackup()) {
                    throw new UnreadableException("The signer pin list can't be read.");
                }
                return new TreeMap<>();
            }
            return parse(json);
        }
    }

    @WorkerThread
    @Nullable
    public SignerPin get(@NonNull String packageName) throws UnreadableException {
        return readAll().get(packageName);
    }

    /**
     * @return the pin it replaced, read in the same step as the write, or {@code null} when there
     * was none
     */
    @WorkerThread
    @Nullable
    public SignerPin put(@NonNull String packageName, @NonNull SignerPin pin) throws IOException {
        synchronized (LOCK) {
            Map<String, SignerPin> all = readAll();
            SignerPin previous = all.put(packageName, pin);
            write(all);
            return previous;
        }
    }

    /**
     * @return the pin that was removed, or {@code null} when the package had none
     */
    @WorkerThread
    @Nullable
    public SignerPin remove(@NonNull String packageName) throws IOException {
        synchronized (LOCK) {
            Map<String, SignerPin> all = readAll();
            SignerPin removed = all.remove(packageName);
            if (removed != null) {
                write(all);
            }
            return removed;
        }
    }

    /**
     * Changes {@code packageName}'s pin only while it still has the one the user was shown,
     * compared by key set, so a choice made on a screen that went stale changes nothing.
     *
     * @param shown the pin the user saw, or {@code null} when they saw none
     * @param pin   the new pin, or {@code null} to remove it
     * @return the pin it replaced or removed, or {@code null} when there was none
     * @throws ChangedException when the pin changed since it was shown; nothing is written
     */
    @WorkerThread
    @Nullable
    public SignerPin replace(@NonNull String packageName, @Nullable SignerPin shown, @Nullable SignerPin pin)
            throws IOException {
        synchronized (LOCK) {
            Map<String, SignerPin> all = readAll();
            SignerPin current = all.get(packageName);
            boolean unchanged = current == null ? shown == null
                    : shown != null && current.isSameSet(shown.certSha256);
            if (!unchanged) {
                throw new ChangedException("The signer pin of " + packageName + " changed after it was shown.");
            }
            if (pin != null) {
                all.put(packageName, pin);
            } else if (current == null) {
                return null;
            } else {
                all.remove(packageName);
            }
            write(all);
            return current;
        }
    }

    /**
     * Adds the pins for packages that have none here. A package already pinned on this device keeps
     * its pin.
     *
     * @return the packages that were pinned
     */
    @WorkerThread
    @NonNull
    public List<String> addMissing(@NonNull Map<String, SignerPin> pins) throws IOException {
        synchronized (LOCK) {
            Map<String, SignerPin> all = readAll();
            List<String> added = new ArrayList<>();
            for (Map.Entry<String, SignerPin> entry : pins.entrySet()) {
                if (!all.containsKey(entry.getKey())) {
                    all.put(entry.getKey(), entry.getValue());
                    added.add(entry.getKey());
                }
            }
            if (!added.isEmpty()) {
                write(all);
            }
            return added;
        }
    }

    /** Removes every pin, a readable store or not. */
    @WorkerThread
    public void clear() {
        synchronized (LOCK) {
            mFile.delete();
        }
    }

    /**
     * Removes every pin, a readable store or not, reading them in the same step so a pin saved
     * meanwhile can't go unreported.
     *
     * @return the pins that were removed, or {@code null} when the list couldn't be read
     * @throws IOException when the list is still there afterwards, so every pin still applies
     */
    @WorkerThread
    @Nullable
    public Map<String, SignerPin> takeAll() throws IOException {
        synchronized (LOCK) {
            Map<String, SignerPin> pins;
            try {
                pins = readAll();
            } catch (UnreadableException e) {
                pins = null;
            }
            mFile.delete();
            if (mFile.getBaseFile().exists() || mFile.hasUnrestoredBackup()) {
                throw new IOException("Could not delete the signer pin list.");
            }
            return pins;
        }
    }

    @WorkerThread
    private void write(@NonNull Map<String, SignerPin> all) throws IOException {
        mFile.write(serialize(all).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads a store. Unlike a snapshot import, one bad entry makes the whole store unreadable: the
     * app wrote it, so a bad entry means it was changed behind the app's back.
     */
    @VisibleForTesting
    @NonNull
    static Map<String, SignerPin> parse(@NonNull String json) throws UnreadableException {
        try {
            JSONObject root = new JSONObject(json);
            int schema = root.optInt("schema_version", -1);
            if (schema < 1 || schema > SCHEMA_VERSION) {
                throw new UnreadableException("Unsupported signer pin list schema " + schema + ".");
            }
            JSONObject pins = root.getJSONObject("pins");
            Map<String, SignerPin> out = new TreeMap<>();
            for (Iterator<String> it = pins.keys(); it.hasNext(); ) {
                String packageName = it.next();
                SignerPin pin = parsePin(pins.getJSONObject(packageName));
                if (pin == null || packageName.trim().isEmpty()) {
                    throw new UnreadableException("The signer pin for " + packageName + " is damaged.");
                }
                out.put(packageName, pin);
            }
            return out;
        } catch (JSONException e) {
            throw new UnreadableException("The signer pin list is damaged: " + e.getMessage());
        }
    }

    /**
     * @return the pin, or {@code null} when the object isn't a valid one
     */
    @Nullable
    public static SignerPin parsePin(@Nullable JSONObject object) {
        if (object == null) {
            return null;
        }
        JSONArray shas = object.optJSONArray("cert_shas256");
        if (shas == null || shas.length() == 0) {
            return null;
        }
        List<String> digests = new ArrayList<>(shas.length());
        for (int i = 0; i < shas.length(); ++i) {
            String digest = SignerPin.normalize(shas.optString(i, null));
            if (digest == null) {
                return null;
            }
            digests.add(digest);
        }
        return new SignerPin(digests, object.optLong("pinned_at", 0));
    }

    @NonNull
    public static JSONObject toJson(@NonNull SignerPin pin) throws JSONException {
        JSONArray shas = new JSONArray();
        for (String digest : pin.certSha256) {
            shas.put(digest);
        }
        return new JSONObject().put("cert_shas256", shas).put("pinned_at", pin.pinnedAt);
    }

    @VisibleForTesting
    @NonNull
    static String serialize(@NonNull Map<String, SignerPin> all) throws IOException {
        try {
            JSONObject pins = new JSONObject();
            for (Map.Entry<String, SignerPin> entry : new TreeMap<>(all).entrySet()) {
                pins.put(entry.getKey(), toJson(entry.getValue()));
            }
            return new JSONObject().put("schema_version", SCHEMA_VERSION).put("pins", pins).toString(2);
        } catch (JSONException e) {
            // Writing a partial store would drop pins, so nothing is written.
            throw new IOException("Could not serialize the signer pins.", e);
        }
    }

    @NonNull
    public static Map<String, SignerPin> emptyMap() {
        return Collections.emptyMap();
    }
}
