// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.ScryptParams;
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedData;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc;
import org.bouncycastle.asn1.pkcs.MacData;
import org.bouncycastle.asn1.pkcs.PBEParameter;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PBMAC1Params;
import org.bouncycastle.asn1.pkcs.PKCS12PBEParams;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.pkcs.SafeBag;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.io.IoUtils;

/**
 * Loads a user-selected keystore under fixed ceilings. Bouncy Castle allocates buffers and derives
 * keys from lengths, salt sizes and iteration counts that the file itself declares
 * (CVE-2026-17508), so a hostile BKS or PKCS12 file could otherwise pin a CPU or exhaust memory.
 * The container is checked before any key derivation runs, and the load runs on its own worker
 * with a time budget, so a failure comes back as a {@link RejectedKeyStoreException} instead of a
 * stuck thread or a dead process.
 */
public final class KeyStoreImportPolicy {
    /** Signing and AppManagerNG keystores hold a handful of keys and are a few kilobytes. */
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    /** Bouncy Castle writes BKS files with 1,024 to 2,047 iterations. */
    public static final int MAX_BKS_ITERATIONS = 100_000;
    /**
     * PKCS12 writers differ widely: OpenSSL uses 2,048, keytool 10,000, and Bouncy Castle 1.86 itself
     * 1,200,000. The ceiling leaves room for strong settings; the time budget bounds the rest.
     */
    public static final int MAX_PKCS12_ITERATIONS = 10_000_000;
    public static final int MAX_SALT_BYTES = 1024;
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_CERTIFICATE_CHAIN = 64;
    public static final long MAX_SCRYPT_MEMORY_BYTES = 64L * 1024 * 1024;
    public static final int MAX_SCRYPT_PARALLELISM = 16;
    public static final long TIME_BUDGET_MILLIS = 30_000;

    private static final int MAX_SAFE_CONTENTS_DEPTH = 4;

    public enum Rejection {
        TOO_LARGE,
        MALFORMED,
        KDF_TOO_EXPENSIVE,
        SALT_TOO_LARGE,
        UNSUPPORTED_KDF,
        TIMED_OUT,
    }

    public static final class RejectedKeyStoreException extends KeyStoreException {
        @NonNull
        public final Rejection reason;

        RejectedKeyStoreException(@NonNull Rejection reason, @NonNull String message) {
            super(reason + ": " + message);
            this.reason = reason;
        }

        RejectedKeyStoreException(@NonNull Rejection reason, @NonNull String message, @NonNull Throwable cause) {
            super(reason + ": " + message, cause);
            this.reason = reason;
        }
    }

    private KeyStoreImportPolicy() {
    }

    /**
     * Reads at most {@link #MAX_FILE_BYTES} from {@code in} and loads it as a keystore of
     * {@code type}. Wrong passwords and other ordinary load failures keep their original
     * exception; anything outside the ceilings is a {@link RejectedKeyStoreException}.
     */
    @WorkerThread
    @NonNull
    public static KeyStore load(@NonNull InputStream in, @NonNull String type, @Nullable Provider provider,
                                @Nullable char[] password) throws IOException, GeneralSecurityException {
        byte[] bytes = IoUtils.readFully(in, MAX_FILE_BYTES + 1, false);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new RejectedKeyStoreException(Rejection.TOO_LARGE,
                    "Keystore is larger than " + MAX_FILE_BYTES + " bytes");
        }
        return load(bytes, type, provider, password, TIME_BUDGET_MILLIS);
    }

    @VisibleForTesting
    @WorkerThread
    @NonNull
    static KeyStore load(@NonNull byte[] bytes, @NonNull String type, @Nullable Provider provider,
                         @Nullable char[] password, long budgetMillis)
            throws IOException, GeneralSecurityException {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "keystore-import");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<KeyStore> future = executor.submit(() -> {
                check(bytes, type);
                KeyStore keyStore = provider != null ? KeyStore.getInstance(type, provider) : KeyStore.getInstance(type);
                keyStore.load(new ByteArrayInputStream(bytes), password);
                return keyStore;
            });
            try {
                return future.get(budgetMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RejectedKeyStoreException(Rejection.TIMED_OUT,
                        "Keystore did not load within " + budgetMillis + " ms");
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while loading a keystore");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                if (cause instanceof GeneralSecurityException) {
                    throw (GeneralSecurityException) cause;
                }
                // Parser failures, including a stack or allocation failure caused by hostile nesting
                // or lengths, stay on the worker; the caller gets a classified rejection.
                throw new RejectedKeyStoreException(Rejection.MALFORMED, "Keystore could not be parsed: "
                        + (cause != null ? cause.getClass().getSimpleName() : "unknown failure"),
                        cause != null ? cause : e);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Rejects a container whose declared sizes or key-derivation costs exceed the ceilings. Only
     * the structure is read here; no key is derived.
     */
    @VisibleForTesting
    static void check(@NonNull byte[] bytes, @NonNull String type) throws RejectedKeyStoreException {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new RejectedKeyStoreException(Rejection.TOO_LARGE, "Keystore is larger than " + MAX_FILE_BYTES + " bytes");
        }
        switch (type) {
            case KeyStoreUtils.KEY_STORE_TYPE_BKS:
            case "BKS-V1":
                checkBks(bytes);
                break;
            case KeyStoreUtils.KEY_STORE_TYPE_PKCS12:
                checkPkcs12(bytes);
                break;
            default:
                // JKS derives nothing from the password and is bounded by the file size.
                break;
        }
    }

    // Bouncy Castle BcKeyStoreSpi layout: version, salt, iteration count, then typed entries.
    private static final int BKS_CERTIFICATE = 1;
    private static final int BKS_KEY = 2;
    private static final int BKS_SECRET = 3;
    private static final int BKS_SEALED = 4;

    private static void checkBks(@NonNull byte[] bytes) throws RejectedKeyStoreException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            int version = in.readInt();
            if (version < 0 || version > 2) {
                throw malformed("Unknown BKS version " + version);
            }
            skipSalt(in);
            checkIterations(BigInteger.valueOf(in.readInt()), MAX_BKS_ITERATIONS);
            int entries = 0;
            int type;
            while ((type = in.read()) > 0) {
                if (++entries > MAX_ENTRIES) {
                    throw malformed("More than " + MAX_ENTRIES + " entries");
                }
                in.readUTF(); // alias
                in.readLong(); // creation date
                int chainLength = in.readInt();
                if (chainLength < 0 || chainLength > MAX_CERTIFICATE_CHAIN) {
                    throw malformed("Certificate chain length " + chainLength);
                }
                for (int i = 0; i < chainLength; ++i) {
                    skipBksCertificate(in);
                }
                switch (type) {
                    case BKS_CERTIFICATE:
                        skipBksCertificate(in);
                        break;
                    case BKS_KEY:
                        in.read(); // key encoding
                        in.readUTF(); // format
                        in.readUTF(); // algorithm
                        readBlock(in);
                        break;
                    case BKS_SECRET:
                        readBlock(in);
                        break;
                    case BKS_SEALED:
                        // Sealed keys carry their own salt and iteration count, used by getKey().
                        DataInputStream sealed = new DataInputStream(new ByteArrayInputStream(readBlock(in)));
                        skipSalt(sealed);
                        checkIterations(BigInteger.valueOf(sealed.readInt()), MAX_BKS_ITERATIONS);
                        break;
                    default:
                        throw malformed("Unknown BKS entry type " + type);
                }
            }
            if (type < 0) {
                throw malformed("Keystore ends before its entry list does");
            }
        } catch (EOFException e) {
            throw malformed("Keystore is truncated", e);
        } catch (IOException e) {
            throw malformed("Keystore is not a readable BKS file", e);
        }
    }

    private static void skipBksCertificate(@NonNull DataInputStream in)
            throws IOException, RejectedKeyStoreException {
        in.readUTF(); // certificate type
        readBlock(in);
    }

    @NonNull
    private static byte[] readBlock(@NonNull DataInputStream in) throws IOException, RejectedKeyStoreException {
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
            throw malformed("Declared length " + length + " exceeds the file");
        }
        byte[] block = new byte[length];
        in.readFully(block);
        return block;
    }

    private static void skipSalt(@NonNull DataInputStream in) throws IOException, RejectedKeyStoreException {
        int length = in.readInt();
        if (length <= 0) {
            throw malformed("Invalid salt length " + length);
        }
        checkSaltLength(length);
        if (length > in.available()) {
            throw malformed("Declared salt length " + length + " exceeds the file");
        }
        in.readFully(new byte[length]);
    }

    private static void checkPkcs12(@NonNull byte[] bytes) throws RejectedKeyStoreException {
        try {
            Pfx pfx = Pfx.getInstance(readAsn1(bytes));
            MacData macData = pfx.getMacData();
            if (macData != null) {
                AlgorithmIdentifier macAlgorithm = macData.getMac().getAlgorithmId();
                if (PKCSObjectIdentifiers.id_PBMAC1.equals(macAlgorithm.getAlgorithm())) {
                    // RFC 9579: the MAC key comes from these parameters, not MacData's iterations.
                    AlgorithmIdentifier kdf = PBMAC1Params.getInstance(macAlgorithm.getParameters())
                            .getKeyDerivationFunc();
                    checkKeyDerivation(kdf.getAlgorithm(), kdf.getParameters());
                } else {
                    checkIterations(macData.getIterationCount(), MAX_PKCS12_ITERATIONS);
                }
                checkSaltLength(macData.getSalt().length);
            }
            ContentInfo authSafe = pfx.getAuthSafe();
            if (!PKCSObjectIdentifiers.data.equals(authSafe.getContentType())) {
                throw malformed("Unsupported authenticated safe " + authSafe.getContentType());
            }
            AuthenticatedSafe safe = AuthenticatedSafe.getInstance(
                    readAsn1(ASN1OctetString.getInstance(authSafe.getContent()).getOctets()));
            for (ContentInfo info : safe.getContentInfo()) {
                if (PKCSObjectIdentifiers.encryptedData.equals(info.getContentType())) {
                    checkEncryption(EncryptedData.getInstance(info.getContent()).getEncryptionAlgorithm());
                } else if (PKCSObjectIdentifiers.data.equals(info.getContentType())) {
                    checkSafeBags(ASN1Sequence.getInstance(
                            readAsn1(ASN1OctetString.getInstance(info.getContent()).getOctets())), 0);
                }
            }
        } catch (RejectedKeyStoreException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw malformed("Keystore is not a readable PKCS12 file", e);
        }
    }

    private static void checkSafeBags(@NonNull ASN1Sequence bags, int depth) throws RejectedKeyStoreException {
        if (depth > MAX_SAFE_CONTENTS_DEPTH) {
            throw malformed("Safe contents are nested too deeply");
        }
        if (bags.size() > MAX_ENTRIES) {
            throw malformed("More than " + MAX_ENTRIES + " entries");
        }
        for (ASN1Encodable element : bags) {
            SafeBag bag = SafeBag.getInstance(element);
            ASN1ObjectIdentifier bagId = bag.getBagId();
            if (PKCSObjectIdentifiers.pkcs8ShroudedKeyBag.equals(bagId)) {
                checkEncryption(EncryptedPrivateKeyInfo.getInstance(bag.getBagValue()).getEncryptionAlgorithm());
            } else if (PKCSObjectIdentifiers.safeContentsBag.equals(bagId)) {
                checkSafeBags(ASN1Sequence.getInstance(bag.getBagValue()), depth + 1);
            }
        }
    }

    private static void checkEncryption(@NonNull AlgorithmIdentifier algorithm) throws RejectedKeyStoreException {
        ASN1ObjectIdentifier oid = algorithm.getAlgorithm();
        if (PKCSObjectIdentifiers.id_PBES2.equals(oid)) {
            KeyDerivationFunc kdf = PBES2Parameters.getInstance(algorithm.getParameters()).getKeyDerivationFunc();
            checkKeyDerivation(kdf.getAlgorithm(), kdf.getParameters());
        } else if (oid.on(PKCSObjectIdentifiers.pkcs_12PbeIds)) {
            PKCS12PBEParams params = PKCS12PBEParams.getInstance(algorithm.getParameters());
            checkIterations(params.getIterations(), MAX_PKCS12_ITERATIONS);
            checkSaltLength(params.getIV().length);
        } else if (oid.on(PKCSObjectIdentifiers.pkcs_5)) {
            // PBES1: PKCS #5 password-based encryption with a salt and an iteration count.
            PBEParameter params = PBEParameter.getInstance(algorithm.getParameters());
            checkIterations(params.getIterationCount(), MAX_PKCS12_ITERATIONS);
            checkSaltLength(params.getSalt().length);
        }
        // Anything else is not password based and derives nothing.
    }

    private static void checkKeyDerivation(@NonNull ASN1ObjectIdentifier kdfOid, @Nullable ASN1Encodable parameters)
            throws RejectedKeyStoreException {
        if (PKCSObjectIdentifiers.id_PBKDF2.equals(kdfOid)) {
            PBKDF2Params params = PBKDF2Params.getInstance(parameters);
            checkIterations(params.getIterationCount(), MAX_PKCS12_ITERATIONS);
            checkSaltLength(params.getSalt().length);
        } else if (MiscObjectIdentifiers.id_scrypt.equals(kdfOid)) {
            ScryptParams params = ScryptParams.getInstance(parameters);
            checkScrypt(params.getCostParameter(), params.getBlockSize(), params.getParallelizationParameter());
            checkSaltLength(params.getSalt().length);
        } else {
            throw new RejectedKeyStoreException(Rejection.UNSUPPORTED_KDF, "Unsupported key derivation " + kdfOid);
        }
    }

    private static void checkIterations(@NonNull BigInteger iterations, int ceiling) throws RejectedKeyStoreException {
        if (iterations.signum() <= 0) {
            throw malformed("Invalid iteration count " + iterations);
        }
        if (iterations.compareTo(BigInteger.valueOf(ceiling)) > 0) {
            throw new RejectedKeyStoreException(Rejection.KDF_TOO_EXPENSIVE,
                    "Iteration count " + iterations + " exceeds " + ceiling);
        }
    }

    private static void checkSaltLength(int length) throws RejectedKeyStoreException {
        if (length > MAX_SALT_BYTES) {
            throw new RejectedKeyStoreException(Rejection.SALT_TOO_LARGE,
                    "Salt of " + length + " bytes exceeds " + MAX_SALT_BYTES);
        }
    }

    private static void checkScrypt(@NonNull BigInteger cost, @NonNull BigInteger blockSize,
                                    @NonNull BigInteger parallelism) throws RejectedKeyStoreException {
        if (cost.signum() <= 0 || blockSize.signum() <= 0 || parallelism.signum() <= 0) {
            throw malformed("Invalid scrypt parameters");
        }
        // scrypt keeps 128 * r * N bytes in memory for each derivation.
        BigInteger memory = BigInteger.valueOf(128).multiply(blockSize).multiply(cost);
        if (memory.compareTo(BigInteger.valueOf(MAX_SCRYPT_MEMORY_BYTES)) > 0
                || parallelism.compareTo(BigInteger.valueOf(MAX_SCRYPT_PARALLELISM)) > 0) {
            throw new RejectedKeyStoreException(Rejection.KDF_TOO_EXPENSIVE,
                    "scrypt needs " + memory + " bytes and parallelism " + parallelism);
        }
    }

    @NonNull
    private static ASN1Primitive readAsn1(@NonNull byte[] bytes) throws IOException {
        try (ASN1InputStream in = new ASN1InputStream(bytes)) {
            ASN1Primitive primitive = in.readObject();
            if (primitive == null) {
                throw new EOFException("Empty ASN.1 structure");
            }
            return primitive;
        }
    }

    @NonNull
    private static RejectedKeyStoreException malformed(@NonNull String message) {
        return new RejectedKeyStoreException(Rejection.MALFORMED, message);
    }

    @NonNull
    private static RejectedKeyStoreException malformed(@NonNull String message, @NonNull Throwable cause) {
        return new RejectedKeyStoreException(Rejection.MALFORMED, message, cause);
    }
}
