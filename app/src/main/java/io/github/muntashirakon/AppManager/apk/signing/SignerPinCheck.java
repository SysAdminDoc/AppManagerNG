// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGate;
import io.github.muntashirakon.AppManager.sync.lan.SyncComparator;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Checks the APKs of an install against the user's signer pins before a session is opened. Only the
 * files and the local pin list are consulted, never a remote database.
 *
 * <p>When a package is pinned, every APK has to verify and be signed by the pinned set, or by one key
 * whose rotation lineage includes the single pinned key. Each APK is read once, from the same stream
 * the session will be written from, into a private copy that is both verified and hashed. The check
 * returns that SHA-256, and the installer refuses to commit a session whose bytes hash differently,
 * so a file swapped after the check can't be installed in its place.
 */
public final class SignerPinCheck {
    /** One APK as the session will receive it. */
    public interface Source {
        @NonNull
        String getName();

        @WorkerThread
        @NonNull
        InputStream openInputStream() throws IOException;
    }

    /** Reads which app a set of APKs installs. */
    public interface DeclaredPackage {
        /**
         * @return the package the APKs declare, or {@code null} when none of them could be read
         */
        @WorkerThread
        @Nullable
        String read();
    }

    /** The install has to stop; the message names the package and both fingerprints. */
    public static final class Blocked extends IOException {
        Blocked(@NonNull String message) {
            super(message);
        }
    }

    /** The folder of the private cache that holds the copies a check reads back. */
    public static final String COPY_FOLDER = "signer-pin";

    private static final String TAG = SignerPinCheck.class.getSimpleName();
    private static final long LEFTOVER_AGE_MILLIS = 24L * 60 * 60 * 1000;

    private SignerPinCheck() {
    }

    /**
     * @param packageNames the package the install claims and, when it differs, the one its base APK
     *                     declares; a pin on either applies
     * @param apks         the APKs that will be written to the session, in that order
     * @return the SHA-256 (lowercase hex) of the APKs' bytes in order, or {@code null} when no
     * package here is pinned and nothing was read
     * @throws Blocked when the pin list can't be read, or an APK doesn't satisfy a pin
     */
    @WorkerThread
    @Nullable
    public static String check(@NonNull Collection<String> packageNames, @NonNull List<? extends Source> apks,
                               @NonNull SignerPinStore store, @NonNull ApkSignatureGate.DigestReader reader)
            throws Blocked {
        Map<String, SignerPin> all = readAll(store);
        Map<String, SignerPin> pins = new LinkedHashMap<>();
        for (String packageName : packageNames) {
            SignerPin pin = all.get(packageName);
            if (pin != null) {
                pins.put(packageName, pin);
            }
        }
        if (pins.isEmpty()) {
            return null;
        }
        if (apks.isEmpty()) {
            throw new Blocked("There's no APK to check against the signer pin for "
                    + pins.keySet().iterator().next() + ".");
        }
        MessageDigest digest = newSha256();
        for (Source apk : apks) {
            File copy = copyAndDigest(apk, digest);
            try {
                ApkSignatureGate.SignerDigests signers = reader.read(copy);
                for (Map.Entry<String, SignerPin> pin : pins.entrySet()) {
                    requireMatch(apk.getName(), signers, pin.getKey(), pin.getValue());
                }
            } finally {
                deleteCopy(copy);
            }
        }
        return toHex(digest.digest());
    }

    /**
     * The same check for APKs that only claim their package, such as a backup's: a pin on the
     * claimed package or the one the APKs declare applies. When the APKs can't be read for their
     * package, a pin on any other app could be the one that applies, so the install is refused.
     * Nothing is parsed when no app is pinned.
     *
     * @param declaredPackage reads the package the APKs declare, {@code null} when it can't be read
     */
    @WorkerThread
    @Nullable
    public static String check(@NonNull String claimedPackage, @NonNull DeclaredPackage declaredPackage,
                               @NonNull List<? extends Source> apks, @NonNull SignerPinStore store,
                               @NonNull ApkSignatureGate.DigestReader reader) throws Blocked {
        Map<String, SignerPin> all = readAll(store);
        if (all.isEmpty()) {
            return null;
        }
        String declared = declaredPackage.read();
        List<String> packageNames = new ArrayList<>(2);
        packageNames.add(claimedPackage);
        if (declared == null) {
            for (String pinned : all.keySet()) {
                if (!claimedPackage.equals(pinned)) {
                    throw new Blocked("Could not read which app these APKs install, so they can't be checked"
                            + " against the signer pins.");
                }
            }
        } else if (!claimedPackage.equals(declared)) {
            packageNames.add(declared);
        }
        return check(packageNames, apks, store, reader);
    }

    @WorkerThread
    @NonNull
    private static Map<String, SignerPin> readAll(@NonNull SignerPinStore store) throws Blocked {
        try {
            return store.readAll();
        } catch (SignerPinStore.UnreadableException e) {
            throw new Blocked(e.getMessage() + " Installs stay blocked until it's cleared in"
                    + " Settings > Privacy > Signer pins.");
        }
    }

    private static void requireMatch(@NonNull String apkName, @Nullable ApkSignatureGate.SignerDigests signers,
                                     @NonNull String packageName, @NonNull SignerPin pin) throws Blocked {
        if (signers == null || signers.current.isEmpty()) {
            throw new Blocked(apkName + " has no signature that verifies, and " + packageName
                    + " is pinned to " + SignerPin.formatAll(pin.certSha256) + ".");
        }
        if (!SyncComparator.signersMatch(signers.current, signers.lineage, pin.certSha256)) {
            throw new Blocked(apkName + " is signed by " + SignerPin.formatAll(signers.current)
                    + ", but " + packageName + " is pinned to " + SignerPin.formatAll(pin.certSha256) + ".");
        }
    }

    @WorkerThread
    @NonNull
    private static File copyAndDigest(@NonNull Source apk, @NonNull MessageDigest digest) throws Blocked {
        // The signer is read back from this copy and the digest is of the bytes written to it
        File copy = null;
        boolean handedOver = false;
        try {
            try (InputStream in = new DigestInputStream(apk.openInputStream(), digest)) {
                copy = privateCopyOf(in);
            } catch (IOException | SecurityException e) {
                // A content URI whose read grant is gone throws SecurityException
                throw new Blocked("Could not read " + apk.getName() + " to check it against its signer pin.");
            }
            handedOver = true;
            return copy;
        } finally {
            // Closing the source can still fail after the whole copy is written
            if (!handedOver && copy != null) {
                deleteCopy(copy);
            }
        }
    }

    /**
     * Copies {@code in} to a new file where no other app can write, for a check that reads the copy
     * back. FileCache prefers the external cache, where before Android 11 any app with storage access
     * could swap the copy between the write and the read. The copy is gone again if this throws;
     * otherwise the caller deletes it with {@link #deleteCopy(File)}.
     */
    @WorkerThread
    @NonNull
    public static File privateCopyOf(@NonNull InputStream in) throws IOException {
        File copy = newPrivateCopy();
        boolean copied = false;
        try {
            try (OutputStream out = new FileOutputStream(copy)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            copied = true;
            return copy;
        } finally {
            if (!copied) {
                deleteCopy(copy);
            }
        }
    }

    public static void deleteCopy(@NonNull File copy) {
        if (!copy.delete() && copy.exists()) {
            Log.w(TAG, "Could not delete the signer pin check's copy %s", copy);
        }
    }

    @NonNull
    private static File newPrivateCopy() throws IOException {
        File folder = new File(ContextUtils.getContext().getCacheDir(), COPY_FOLDER);
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("Could not create " + folder);
        }
        // No check runs for a day, so a copy that old was left by a process that died mid-check
        long cutoff = System.currentTimeMillis() - LEFTOVER_AGE_MILLIS;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoff) {
                    deleteCopy(file);
                }
            }
        }
        return File.createTempFile("copy-", ".apk", folder);
    }

    @NonNull
    private static MessageDigest newSha256() throws Blocked {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new Blocked("SHA-256 is unavailable, so the checked APKs can't be tied to the install.");
        }
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }
}
