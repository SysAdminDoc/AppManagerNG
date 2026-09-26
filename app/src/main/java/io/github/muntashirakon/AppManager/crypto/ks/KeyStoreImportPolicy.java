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
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedData;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc;
import org.bouncycastle.asn1.pkcs.MacData;
import org.bouncycastle.asn1.pkcs.PBEParameter;
import org.bouncycastle.asn1.pkcs.EncryptionScheme;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PBMAC1Params;
import org.bouncycastle.asn1.pkcs.PKCS12PBEParams;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.pkcs.SafeBag;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.InputDecryptor;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;

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
 * <p>
 * A key derivation can't be interrupted once it starts, so the time budget only frees the caller.
 * What bounds the CPU a file can take is the work check: every derivation the load will run is
 * priced up front, including those inside encrypted PKCS12 contents, and the whole file must fit
 * {@link #MAX_TOTAL_KDF_WORK}.
 */
public final class KeyStoreImportPolicy {
    /** Signing and AppManagerNG keystores hold a handful of keys and are a few kilobytes. */
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    /** Bouncy Castle writes BKS files with 1,024 to 2,047 iterations. */
    public static final int MAX_BKS_ITERATIONS = 100_000;
    /**
     * The most iterations one PKCS12 derivation may ask for. It matches the limit Bouncy Castle 1.86
     * enforces itself, so a count it would refuse is refused here first, with a reason. Writers
     * differ widely: OpenSSL uses 2,048, keytool 10,000, and Bouncy Castle 1.86 600,000 for keys and
     * certificates and 1,200,000 for the MAC.
     */
    public static final int MAX_PKCS12_ITERATIONS = 5_000_000;
    /** The longest key a derivation may produce: an HMAC-SHA512 key. */
    public static final int MAX_DERIVED_KEY_BYTES = 64;
    /**
     * The total key-derivation work one file may cost, in PRF rounds: iterations times output
     * blocks for PBKDF2, iterations times hash blocks for the PKCS12 KDF, and 4 x N x r x p Salsa
     * rounds for scrypt. A Bouncy Castle 1.86 PKCS12 file holding one key costs 4.8 million, and each
     * further key 1.8 million, so four keys fit.
     */
    public static final long MAX_TOTAL_KDF_WORK = 12_000_000L;
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
                check(bytes, type, password);
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
     * Rejects a container whose declared sizes or key-derivation costs exceed the ceilings, and
     * returns the key-derivation work the load will cost. The structure is read without deriving
     * a key, except that encrypted PKCS12 contents are opened with {@code password}, at a cost
     * already counted, so the derivations inside them are priced too.
     */
    @VisibleForTesting
    static long check(@NonNull byte[] bytes, @NonNull String type, @Nullable char[] password)
            throws RejectedKeyStoreException {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new RejectedKeyStoreException(Rejection.TOO_LARGE, "Keystore is larger than " + MAX_FILE_BYTES + " bytes");
        }
        WorkBudget budget = new WorkBudget();
        switch (type) {
            case KeyStoreUtils.KEY_STORE_TYPE_BKS:
            case "BKS-V1":
                checkBks(bytes, budget);
                break;
            case KeyStoreUtils.KEY_STORE_TYPE_PKCS12:
                checkPkcs12(bytes, password, budget);
                break;
            default:
                // JKS derives nothing from the password and is bounded by the file size.
                break;
        }
        return budget.spent;
    }

    /** Adds up the derivations a load will run and refuses the file once they exceed the total. */
    private static final class WorkBudget {
        long spent;

        void add(@NonNull BigInteger iterations, long roundsPerIteration, @NonNull String what)
                throws RejectedKeyStoreException {
            BigInteger cost = iterations.multiply(BigInteger.valueOf(roundsPerIteration));
            BigInteger total = cost.add(BigInteger.valueOf(spent));
            if (total.compareTo(BigInteger.valueOf(MAX_TOTAL_KDF_WORK)) > 0) {
                throw new RejectedKeyStoreException(Rejection.KDF_TOO_EXPENSIVE, what + " brings the key-derivation work to "
                        + total + " rounds, over " + MAX_TOTAL_KDF_WORK);
            }
            spent = total.longValue();
        }
    }

    // Bouncy Castle BcKeyStoreSpi layout: version, salt, iteration count, then typed entries.
    private static final int BKS_CERTIFICATE = 1;
    private static final int BKS_KEY = 2;
    private static final int BKS_SECRET = 3;
    private static final int BKS_SEALED = 4;

    // BKS derives its MAC key and its sealed-entry keys with the PKCS12 KDF over SHA-1: one hash
    // block for the 20-byte MAC key, three for a triple-DES key and its IV.
    private static final int BKS_MAC_ROUNDS = 1;
    private static final int BKS_SEALED_ROUNDS = 3;

    private static void checkBks(@NonNull byte[] bytes, @NonNull WorkBudget budget) throws RejectedKeyStoreException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            int version = in.readInt();
            if (version < 0 || version > 2) {
                throw malformed("Unknown BKS version " + version);
            }
            skipSalt(in);
            BigInteger macIterations = BigInteger.valueOf(in.readInt());
            checkIterations(macIterations, MAX_BKS_ITERATIONS);
            budget.add(macIterations, BKS_MAC_ROUNDS, "The BKS MAC");
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
                        BigInteger sealedIterations = BigInteger.valueOf(sealed.readInt());
                        checkIterations(sealedIterations, MAX_BKS_ITERATIONS);
                        budget.add(sealedIterations, BKS_SEALED_ROUNDS, "A sealed BKS entry");
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

    // The PKCS12 KDF hashes one block per iteration for each hash-length piece of output. A MAC key
    // is one piece. Key plus IV for the PKCS12 ciphers is at most three (triple DES), which prices
    // every PKCS12 cipher; PBES1's PBKDF1 hashes once per iteration.
    private static final int PKCS12_MAC_ROUNDS = 1;
    private static final int PKCS12_PBE_ROUNDS = 3;
    private static final int PBES1_ROUNDS = 1;

    private static void checkPkcs12(@NonNull byte[] bytes, @Nullable char[] password, @NonNull WorkBudget budget)
            throws RejectedKeyStoreException {
        try {
            Pfx pfx = Pfx.getInstance(readAsn1(bytes));
            MacData macData = pfx.getMacData();
            if (macData != null) {
                AlgorithmIdentifier macAlgorithm = macData.getMac().getAlgorithmId();
                if (PKCSObjectIdentifiers.id_PBMAC1.equals(macAlgorithm.getAlgorithm())) {
                    // RFC 9579: the MAC key comes from these parameters, not MacData's iterations.
                    AlgorithmIdentifier kdf = PBMAC1Params.getInstance(macAlgorithm.getParameters())
                            .getKeyDerivationFunc();
                    checkKeyDerivation(kdf.getAlgorithm(), kdf.getParameters(), MAX_DERIVED_KEY_BYTES, budget,
                            "The PBMAC1 MAC");
                } else {
                    checkIterations(macData.getIterationCount(), MAX_PKCS12_ITERATIONS);
                    budget.add(macData.getIterationCount(), PKCS12_MAC_ROUNDS, "The PKCS12 MAC");
                }
                checkSaltLength(macData.getSalt().length);
            }
            ContentInfo authSafe = pfx.getAuthSafe();
            if (!PKCSObjectIdentifiers.data.equals(authSafe.getContentType())) {
                throw malformed("Unsupported authenticated safe " + authSafe.getContentType());
            }
            AuthenticatedSafe safe = AuthenticatedSafe.getInstance(
                    readAsn1(ASN1OctetString.getInstance(authSafe.getContent()).getOctets()));
            ContentInfo[] contents = safe.getContentInfo();
            if (contents.length > MAX_ENTRIES) {
                throw malformed("More than " + MAX_ENTRIES + " content blocks");
            }
            for (ContentInfo info : contents) {
                if (PKCSObjectIdentifiers.encryptedData.equals(info.getContentType())) {
                    EncryptedData encryptedData = EncryptedData.getInstance(info.getContent());
                    checkEncryption(encryptedData.getEncryptionAlgorithm(), budget);
                    byte[] decrypted = decrypt(encryptedData, password, macData == null);
                    if (decrypted != null) {
                        // The bags inside cost their own derivations once the load opens them.
                        checkSafeBags(ASN1Sequence.getInstance(readAsn1(decrypted)), 0, budget);
                    }
                } else if (PKCSObjectIdentifiers.data.equals(info.getContentType())) {
                    checkSafeBags(ASN1Sequence.getInstance(
                            readAsn1(ASN1OctetString.getInstance(info.getContent()).getOctets())), 0, budget);
                }
            }
        } catch (RejectedKeyStoreException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw malformed("Keystore is not a readable PKCS12 file", e);
        }
    }

    /**
     * Opens encrypted safe contents the way the load will, so their bags can be priced first. Its own
     * derivation was counted before this runs.
     *
     * @return the decrypted safe contents, or {@code null} when there is no password to open them
     * with, or when a MAC protects the file and the password doesn't open them. The load then checks
     * that MAC before it decrypts anything and fails on the wrong password with its usual error.
     */
    @Nullable
    private static byte[] decrypt(@NonNull EncryptedData encryptedData, @Nullable char[] password, boolean noMac)
            throws RejectedKeyStoreException {
        ASN1OctetString content = encryptedData.getContent();
        if (password == null || content == null) {
            return null;
        }
        try {
            InputDecryptor decryptor = new JcePKCSPBEInputDecryptorProviderBuilder()
                    .setProvider(BouncyCastleHolder.PROVIDER)
                    .setTryWrongPKCS12Zero(true)
                    .build(password)
                    .get(encryptedData.getEncryptionAlgorithm());
            try (InputStream in = decryptor.getInputStream(new ByteArrayInputStream(content.getOctets()))) {
                return IoUtils.readFully(in, MAX_FILE_BYTES, false);
            }
        } catch (Exception e) {
            if (noMac) {
                // Without a MAC nothing stops the load from decrypting these contents in a way this
                // check could not, and then running derivations nobody priced.
                throw malformed("Encrypted contents could not be opened for checking", e);
            }
            return null;
        }
    }

    private static final class BouncyCastleHolder {
        static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    }

    private static void checkSafeBags(@NonNull ASN1Sequence bags, int depth, @NonNull WorkBudget budget)
            throws RejectedKeyStoreException {
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
                checkEncryption(EncryptedPrivateKeyInfo.getInstance(bag.getBagValue()).getEncryptionAlgorithm(), budget);
            } else if (PKCSObjectIdentifiers.safeContentsBag.equals(bagId)) {
                checkSafeBags(ASN1Sequence.getInstance(bag.getBagValue()), depth + 1, budget);
            }
        }
    }

    private static void checkEncryption(@NonNull AlgorithmIdentifier algorithm, @NonNull WorkBudget budget)
            throws RejectedKeyStoreException {
        ASN1ObjectIdentifier oid = algorithm.getAlgorithm();
        if (PKCSObjectIdentifiers.id_PBES2.equals(oid)) {
            PBES2Parameters params = PBES2Parameters.getInstance(algorithm.getParameters());
            KeyDerivationFunc kdf = params.getKeyDerivationFunc();
            checkKeyDerivation(kdf.getAlgorithm(), kdf.getParameters(), impliedKeyBytes(params.getEncryptionScheme()),
                    budget, "PBES2 encryption");
        } else if (oid.on(PKCSObjectIdentifiers.pkcs_12PbeIds)) {
            PKCS12PBEParams params = PKCS12PBEParams.getInstance(algorithm.getParameters());
            checkIterations(params.getIterations(), MAX_PKCS12_ITERATIONS);
            checkSaltLength(params.getIV().length);
            budget.add(params.getIterations(), PKCS12_PBE_ROUNDS, "PKCS12 encryption");
        } else if (oid.on(PKCSObjectIdentifiers.pkcs_5)) {
            // PBES1: PKCS #5 password-based encryption with a salt and an iteration count.
            PBEParameter params = PBEParameter.getInstance(algorithm.getParameters());
            checkIterations(params.getIterationCount(), MAX_PKCS12_ITERATIONS);
            checkSaltLength(params.getSalt().length);
            budget.add(params.getIterationCount(), PBES1_ROUNDS, "PBES1 encryption");
        }
        // Anything else is not password based and derives nothing.
    }

    /**
     * Prices one PBKDF2 or scrypt derivation.
     *
     * @param defaultKeyBytes the key length the scheme implies when the parameters don't state one
     */
    private static void checkKeyDerivation(@NonNull ASN1ObjectIdentifier kdfOid, @Nullable ASN1Encodable parameters,
                                           int defaultKeyBytes, @NonNull WorkBudget budget, @NonNull String what)
            throws RejectedKeyStoreException {
        if (PKCSObjectIdentifiers.id_PBKDF2.equals(kdfOid)) {
            PBKDF2Params params = PBKDF2Params.getInstance(parameters);
            checkIterations(params.getIterationCount(), MAX_PKCS12_ITERATIONS);
            checkSaltLength(params.getSalt().length);
            int prfBytes = prfOutputBytes(params.getPrf().getAlgorithm());
            BigInteger keyBytes = params.getKeyLength() != null ? params.getKeyLength() : BigInteger.valueOf(defaultKeyBytes);
            if (keyBytes.signum() <= 0) {
                throw malformed("Invalid key length " + keyBytes);
            }
            if (keyBytes.compareTo(BigInteger.valueOf(MAX_DERIVED_KEY_BYTES)) > 0) {
                throw new RejectedKeyStoreException(Rejection.KDF_TOO_EXPENSIVE,
                        "Derived key of " + keyBytes + " bytes exceeds " + MAX_DERIVED_KEY_BYTES);
            }
            // PBKDF2 runs every iteration once for each PRF-sized block of the key.
            long blocks = (keyBytes.longValue() + prfBytes - 1) / prfBytes;
            budget.add(params.getIterationCount(), blocks, what);
        } else if (MiscObjectIdentifiers.id_scrypt.equals(kdfOid)) {
            ScryptParams params = ScryptParams.getInstance(parameters);
            checkScrypt(params.getCostParameter(), params.getBlockSize(), params.getParallelizationParameter());
            checkSaltLength(params.getSalt().length);
            if (params.getKeyLength() != null
                    && params.getKeyLength().compareTo(BigInteger.valueOf(MAX_DERIVED_KEY_BYTES)) > 0) {
                throw new RejectedKeyStoreException(Rejection.KDF_TOO_EXPENSIVE,
                        "Derived key of " + params.getKeyLength() + " bytes exceeds " + MAX_DERIVED_KEY_BYTES);
            }
            // Each of the p lanes runs 2N BlockMix steps of 2r Salsa20/8 rounds.
            BigInteger rounds = params.getCostParameter().multiply(params.getBlockSize())
                    .multiply(params.getParallelizationParameter());
            budget.add(rounds, 4, what);
        } else {
            throw new RejectedKeyStoreException(Rejection.UNSUPPORTED_KDF, "Unsupported key derivation " + kdfOid);
        }
    }

    /** Output bytes of the PBKDF2 PRFs Bouncy Castle offers; anything else is not accepted. */
    private static int prfOutputBytes(@NonNull ASN1ObjectIdentifier prf) throws RejectedKeyStoreException {
        if (PKCSObjectIdentifiers.id_hmacWithSHA1.equals(prf)) {
            return 20;
        }
        if (PKCSObjectIdentifiers.id_hmacWithSHA224.equals(prf) || NISTObjectIdentifiers.id_hmacWithSHA3_224.equals(prf)) {
            return 28;
        }
        if (PKCSObjectIdentifiers.id_hmacWithSHA256.equals(prf) || NISTObjectIdentifiers.id_hmacWithSHA3_256.equals(prf)) {
            return 32;
        }
        if (PKCSObjectIdentifiers.id_hmacWithSHA384.equals(prf) || NISTObjectIdentifiers.id_hmacWithSHA3_384.equals(prf)) {
            return 48;
        }
        if (PKCSObjectIdentifiers.id_hmacWithSHA512.equals(prf) || NISTObjectIdentifiers.id_hmacWithSHA3_512.equals(prf)) {
            return 64;
        }
        throw new RejectedKeyStoreException(Rejection.UNSUPPORTED_KDF, "Unsupported PBKDF2 PRF " + prf);
    }

    /** The key length a PBES2 cipher needs when PBKDF2 doesn't state it. */
    private static int impliedKeyBytes(@NonNull EncryptionScheme scheme) {
        ASN1ObjectIdentifier cipher = scheme.getAlgorithm();
        if (cipher.on(NISTObjectIdentifiers.aes)) {
            // AES OIDs number their modes 1 to 9 for 128-bit keys, 21 to 29 for 192 and 41 to 49 for 256.
            String id = cipher.getId();
            int mode = Integer.parseInt(id.substring(id.lastIndexOf('.') + 1));
            return mode < 20 ? 16 : mode < 40 ? 24 : 32;
        }
        if (PKCSObjectIdentifiers.des_EDE3_CBC.equals(cipher)) {
            return 24;
        }
        // Anything else is priced as the longest key allowed.
        return MAX_DERIVED_KEY_BYTES;
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
