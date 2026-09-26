// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.ScryptParams;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.AuthenticatedSafe;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedData;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.EncryptionScheme;
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc;
import org.bouncycastle.asn1.pkcs.MacData;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PBMAC1Params;
import org.bouncycastle.asn1.pkcs.PKCS12PBEParams;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.asn1.pkcs.SafeBag;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;

import javax.crypto.spec.SecretKeySpec;

import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreImportPolicy.RejectedKeyStoreException;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreImportPolicy.Rejection;

/**
 * CVE-2026-17508: Bouncy Castle derives keys with whatever salt size and iteration count a keystore
 * declares. Every hostile fixture here would pin the test process or exhaust its memory if the
 * policy let Bouncy Castle start on it, so each one also runs under a timeout.
 */
public class KeyStoreImportPolicyTest {
    private static final char[] PASSWORD = "correct horse battery".toCharArray();
    private static final Provider BC = new BouncyCastleProvider();
    private static final long BUDGET = 30_000;

    @Test(timeout = 60_000)
    public void aValidBksKeystoreLoadsAndItsSealedKeyOpens() throws Exception {
        byte[] bks = bksWithSecretKey();

        KeyStore keyStore = KeyStoreImportPolicy.load(bks, "BKS", BC, PASSWORD, BUDGET);

        assertEquals(Collections.singletonList("am-key"), Collections.list(keyStore.aliases()));
        Key key = keyStore.getKey("am-key", PASSWORD);
        assertArrayEquals(new byte[16], key.getEncoded());
    }

    @Test(timeout = 60_000)
    public void aValidPkcs12KeystoreLoads() throws Exception {
        byte[] pkcs12 = pkcs12WithPrivateKey();

        KeyStore keyStore = KeyStoreImportPolicy.load(pkcs12, "PKCS12", BC, PASSWORD, BUDGET);

        assertTrue(keyStore.isKeyEntry("signing"));
        assertTrue(keyStore.getCertificate("signing") instanceof X509Certificate);
    }

    @Test(timeout = 60_000)
    public void aWrongPasswordKeepsItsOrdinaryException() throws Exception {
        byte[] bks = bksWithSecretKey();

        try {
            KeyStoreImportPolicy.load(bks, "BKS", BC, "wrong".toCharArray(), BUDGET);
            fail("A wrong password must not open the keystore");
        } catch (IOException expected) {
            // BKS reports a failed integrity check as an IOException, exactly as it did before;
            // a RejectedKeyStoreException is a KeyStoreException and would not be caught here.
        }
    }

    @Test(timeout = 10_000)
    public void aBksSaltLengthNearTwoGigabytesIsRejectedBeforeAllocation() {
        byte[] header = ByteBuffer.allocate(8).putInt(2).putInt(Integer.MAX_VALUE).array();

        assertRejected(Rejection.SALT_TOO_LARGE, header, "BKS");
    }

    @Test(timeout = 10_000)
    public void aBksHeaderIterationCountAboveTheCeilingIsRejected() throws Exception {
        byte[] bks = bksWithSecretKey();
        int saltLength = ByteBuffer.wrap(bks, 4, 4).getInt();
        ByteBuffer.wrap(bks, 8 + saltLength, 4).putInt(Integer.MAX_VALUE);

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, bks, "BKS");
    }

    @Test(timeout = 10_000)
    public void bksHasItsOwnTighterCeiling() throws Exception {
        // Allowed for PKCS12, where Bouncy Castle itself writes 1,200,000, but no BKS writer goes near it.
        byte[] bks = bksWithSecretKey();
        int saltLength = ByteBuffer.wrap(bks, 4, 4).getInt();
        ByteBuffer.wrap(bks, 8 + saltLength, 4).putInt(KeyStoreImportPolicy.MAX_BKS_ITERATIONS + 1);

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, bks, "BKS");
    }

    @Test(timeout = 10_000)
    public void aSealedBksEntryCarriesItsOwnIterationCountAndItIsChecked() throws IOException {
        ByteArrayOutputStream sealed = new ByteArrayOutputStream();
        DataOutputStream entry = new DataOutputStream(sealed);
        entry.writeInt(20);
        entry.write(new byte[20]);
        entry.writeInt(Integer.MAX_VALUE);
        entry.write(new byte[32]);

        byte[] bks = bks(out -> {
            out.write(4); // sealed
            out.writeUTF("am-key");
            out.writeLong(0);
            out.writeInt(0);
            out.writeInt(sealed.size());
            out.write(sealed.toByteArray());
        });

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, bks, "BKS");
    }

    @Test(timeout = 10_000)
    public void aBksCertificateChainLengthCannotAllocateAnArray() throws IOException {
        byte[] bks = bks(out -> {
            out.write(2); // key
            out.writeUTF("am-key");
            out.writeLong(0);
            out.writeInt(Integer.MAX_VALUE);
        });

        assertRejected(Rejection.MALFORMED, bks, "BKS");
    }

    @Test(timeout = 10_000)
    public void aBksBlockLongerThanTheFileIsMalformed() throws IOException {
        byte[] bks = bks(out -> {
            out.write(3); // secret
            out.writeUTF("am-key");
            out.writeLong(0);
            out.writeInt(0);
            out.writeInt(Integer.MAX_VALUE - 8);
        });

        assertRejected(Rejection.MALFORMED, bks, "BKS");
    }

    @Test(timeout = 10_000)
    public void aPkcs12MacIterationCountAboveTheCeilingIsRejected() throws IOException {
        MacData mac = new MacData(new DigestInfo(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1), new byte[20]),
                new byte[8], Integer.MAX_VALUE);

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(new ContentInfo[0], mac), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void aPbmac1MacIsBoundedByItsOwnKeyDerivation() throws IOException {
        // MacData's iteration count is ignored for PBMAC1, so a harmless 1 there must not help.
        AlgorithmIdentifier pbkdf2 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBKDF2,
                new PBKDF2Params(new byte[16], Integer.MAX_VALUE, 32,
                        new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256)));
        AlgorithmIdentifier pbmac1 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBMAC1,
                new PBMAC1Params(pbkdf2, new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256)));
        MacData mac = new MacData(new DigestInfo(pbmac1, new byte[32]), new byte[8], 1);

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(new ContentInfo[0], mac), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void aPbmac1KeyLongerThanAnyMacNeedsIsRejected() throws IOException {
        // Review finding: 5,000,000 iterations stay inside Bouncy Castle's own limits, but a 1,024-byte
        // key makes PBKDF2 run them 32 times, whatever password is typed, before the MAC is compared.
        MacData mac = pbmac1(new PBKDF2Params(new byte[16], 5_000_000, 1024,
                new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256)));

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(new ContentInfo[0], mac), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void pbkdf2WorkCountsEveryOutputBlock() throws Exception {
        // 4,000,000 iterations are allowed alone, but a 64-byte key over SHA-1 needs four blocks.
        MacData mac = pbmac1(new PBKDF2Params(new byte[16], 4_000_000, 64,
                new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA1)));
        MacData oneBlock = pbmac1(new PBKDF2Params(new byte[16], 4_000_000, 20,
                new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA1)));

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(new ContentInfo[0], mac), "PKCS12");
        assertEquals(4_000_000L, KeyStoreImportPolicy.check(pfx(new ContentInfo[0], oneBlock), "PKCS12", PASSWORD));
    }

    @Test(timeout = 10_000)
    public void anUnknownPbkdf2PrfIsRejected() throws IOException {
        MacData mac = pbmac1(new PBKDF2Params(new byte[16], 1000, 32,
                new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.3.4.5"))));

        assertRejected(Rejection.UNSUPPORTED_KDF, pfx(new ContentInfo[0], mac), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void countsBouncyCastleWouldRefuseAreRefusedFirstWithAReason() throws IOException {
        // Bouncy Castle 1.86 caps PKCS12 iterations at 5,000,000; between that and the old
        // 10,000,000 ceiling it used to fail later with an unclassified IOException.
        MacData mac = new MacData(new DigestInfo(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1), new byte[20]),
                new byte[8], 6_000_000);

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(new ContentInfo[0], mac), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void derivationsAddUpAcrossTheWholeFile() throws Exception {
        // Each shrouded key alone is within limits: four fit the total work allowed, five do not.
        SafeBag[] bags = new SafeBag[5];
        for (int i = 0; i < bags.length; ++i) {
            bags[i] = shroudedKey(1_000_000);
        }

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(plain(bags), null), "PKCS12");
        assertEquals(4 * 3_000_000L, KeyStoreImportPolicy.check(
                pfx(plain(java.util.Arrays.copyOf(bags, 4)), null), "PKCS12", PASSWORD));
    }

    @Test(timeout = 10_000)
    public void tooManyContentBlocksAreRejected() throws IOException {
        ContentInfo[] contents = new ContentInfo[KeyStoreImportPolicy.MAX_ENTRIES + 1];
        for (int i = 0; i < contents.length; ++i) {
            contents[i] = plain(new SafeBag[0])[0];
        }

        assertRejected(Rejection.MALFORMED, pfx(contents, null), "PKCS12");
    }

    @Test(timeout = 30_000)
    public void derivationsInsideEncryptedContentsAreCountedBeforeTheLoad() throws Exception {
        // Review finding: bags inside encrypted contents were invisible to the check. With the right
        // password they are opened and priced first.
        byte[] keyStore = pfx(encrypted(PASSWORD, shroudedKey(4_000_000), shroudedKey(4_000_000)), null);

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, keyStore, "PKCS12");
    }

    @Test(timeout = 30_000)
    public void encryptedContentsWithoutAMacMustOpenWithThePassword() throws Exception {
        byte[] keyStore = pfx(encrypted("another password".toCharArray(), shroudedKey(1000)), null);

        assertRejected(Rejection.MALFORMED, keyStore, "PKCS12");
    }

    @Test(timeout = 60_000)
    public void aWrongPasswordOnAMacProtectedPkcs12KeepsItsOrdinaryException() throws Exception {
        byte[] pkcs12 = pkcs12WithPrivateKey();

        try {
            KeyStoreImportPolicy.load(pkcs12, "PKCS12", BC, "wrong".toCharArray(), BUDGET);
            fail("A wrong password must not open the keystore");
        } catch (RejectedKeyStoreException e) {
            fail("A wrong password is not a hostile file: " + e);
        } catch (IOException expected) {
            // Bouncy Castle reports the failed MAC check as an IOException.
        }
    }

    @Test(timeout = 60_000)
    public void aBouncyCastlePkcs12LeavesRoomForThreeMoreKeys() throws Exception {
        long work = KeyStoreImportPolicy.check(pkcs12WithPrivateKey(), "PKCS12", PASSWORD);

        // MAC 1,200,000 x 1, shrouded key 600,000 x 3, encrypted certificates 600,000 x 3.
        assertEquals(4_800_000L, work);
        assertTrue(work + 3 * 1_800_000L <= KeyStoreImportPolicy.MAX_TOTAL_KDF_WORK);
    }

    @Test(timeout = 10_000)
    public void aPbes2Pbkdf2IterationCountAboveTheCeilingIsRejected() throws IOException {
        KeyDerivationFunc kdf = new KeyDerivationFunc(PKCSObjectIdentifiers.id_PBKDF2,
                new PBKDF2Params(new byte[16], Integer.MAX_VALUE));

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(encryptedWith(kdf), null), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void scryptMemoryIsBounded() throws IOException {
        KeyDerivationFunc kdf = new KeyDerivationFunc(MiscObjectIdentifiers.id_scrypt,
                new ScryptParams(new byte[16], 1 << 25, 8, 1, 32));

        assertRejected(Rejection.KDF_TOO_EXPENSIVE, pfx(encryptedWith(kdf), null), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void anUnknownKeyDerivationIsRejected() throws IOException {
        KeyDerivationFunc kdf = new KeyDerivationFunc(new ASN1ObjectIdentifier("1.2.3.4"), new DERSequence());

        assertRejected(Rejection.UNSUPPORTED_KDF, pfx(encryptedWith(kdf), null), "PKCS12");
    }

    @Test(timeout = 10_000)
    public void hostileAsn1NestingIsClassifiedInsteadOfCrashing() {
        byte[] nested = new byte[512 * 1024];
        for (int i = 0; i < nested.length; i += 2) {
            nested[i] = 0x30; // SEQUENCE
            nested[i + 1] = (byte) 0x80; // indefinite length
        }

        try {
            KeyStoreImportPolicy.load(nested, "PKCS12", BC, PASSWORD, BUDGET);
            fail("Nested garbage must not load");
        } catch (RejectedKeyStoreException e) {
            assertEquals(Rejection.MALFORMED, e.reason);
        } catch (Exception e) {
            fail("Expected a classified rejection, got " + e);
        }
    }

    @Test(timeout = 10_000)
    public void aFileAboveTheSizeCeilingIsRejectedWithoutReadingItAll() {
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 0;
            }
        };

        try {
            KeyStoreImportPolicy.load(endless, "JKS", null, PASSWORD);
            fail("An endless stream must not load");
        } catch (RejectedKeyStoreException e) {
            assertEquals(Rejection.TOO_LARGE, e.reason);
        } catch (Exception e) {
            fail("Expected a size rejection, got " + e);
        }
    }

    @Test(timeout = 10_000)
    public void aLoadThatOutlivesItsBudgetIsAbandoned() {
        Provider slow = new Provider("SlowTestProvider", 1.0, "blocks forever") {
        };
        slow.put("KeyStore.SLOW", BlockingKeyStoreSpi.class.getName());

        try {
            KeyStoreImportPolicy.load(new byte[16], "SLOW", slow, PASSWORD, 200);
            fail("A load that never finishes must not be waited on forever");
        } catch (RejectedKeyStoreException e) {
            assertEquals(Rejection.TIMED_OUT, e.reason);
        } catch (Exception e) {
            fail("Expected a timeout rejection, got " + e);
        }
    }

    private static void assertRejected(Rejection expected, byte[] keyStore, String type) {
        try {
            KeyStoreImportPolicy.load(keyStore, type, BC, PASSWORD, BUDGET);
            fail("Expected " + expected);
        } catch (RejectedKeyStoreException e) {
            assertEquals(e.getMessage(), expected, e.reason);
        } catch (Exception e) {
            fail("Expected " + expected + ", got " + e);
        }
    }

    private static byte[] bksWithSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("BKS", BC);
        keyStore.load(null, null);
        keyStore.setKeyEntry("am-key", new SecretKeySpec(new byte[16], "AES"), PASSWORD, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyStore.store(out, PASSWORD);
        return out.toByteArray();
    }

    private static byte[] pkcs12WithPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BC);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=AppManagerNG test");
        Date now = new Date();
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BC).getCertificate(
                new JcaX509v3CertificateBuilder(name, BigInteger.ONE, now, new Date(now.getTime() + 86_400_000L),
                        name, keyPair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withECDSA").setProvider(BC).build(keyPair.getPrivate())));
        KeyStore keyStore = KeyStore.getInstance("PKCS12", BC);
        keyStore.load(null, null);
        keyStore.setKeyEntry("signing", keyPair.getPrivate(), PASSWORD, new Certificate[]{certificate});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyStore.store(out, PASSWORD);
        return out.toByteArray();
    }

    private interface EntryWriter {
        void write(DataOutputStream out) throws IOException;
    }

    /** A BKS container with a sane header, the given entries, and a placeholder MAC. */
    private static byte[] bks(EntryWriter entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(2);
        out.writeInt(20);
        out.write(new byte[20]);
        out.writeInt(1024);
        entries.write(out);
        out.write(0);
        out.write(new byte[20]);
        return bytes.toByteArray();
    }

    private static ContentInfo[] encryptedWith(KeyDerivationFunc kdf) {
        AlgorithmIdentifier pbes2 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBES2, new PBES2Parameters(kdf,
                new EncryptionScheme(NISTObjectIdentifiers.id_aes256_CBC, new DEROctetString(new byte[16]))));
        EncryptedData encrypted = new EncryptedData(PKCSObjectIdentifiers.data, pbes2, new DEROctetString(new byte[32]));
        return new ContentInfo[]{new ContentInfo(PKCSObjectIdentifiers.encryptedData, encrypted)};
    }

    private static MacData pbmac1(PBKDF2Params kdf) {
        AlgorithmIdentifier pbkdf2 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBKDF2, kdf);
        AlgorithmIdentifier pbmac1 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBMAC1,
                new PBMAC1Params(pbkdf2, new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256)));
        return new MacData(new DigestInfo(pbmac1, new byte[32]), new byte[8], 1);
    }

    /** A shrouded key bag declaring triple-DES PKCS12 encryption; its ciphertext is never opened. */
    private static SafeBag shroudedKey(int iterations) {
        AlgorithmIdentifier pbe = new AlgorithmIdentifier(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC,
                new PKCS12PBEParams(new byte[20], iterations));
        return new SafeBag(PKCSObjectIdentifiers.pkcs8ShroudedKeyBag,
                new EncryptedPrivateKeyInfo(pbe, new byte[64]).toASN1Primitive());
    }

    private static ContentInfo[] plain(SafeBag[] bags) throws IOException {
        return new ContentInfo[]{new ContentInfo(PKCSObjectIdentifiers.data,
                new DEROctetString(new DERSequence(bags).getEncoded()))};
    }

    /** Encrypted safe contents holding {@code bags}, opened by {@code password} with cheap PBES2. */
    private static ContentInfo[] encrypted(char[] password, SafeBag... bags) throws Exception {
        OutputEncryptor encryptor = new JcePKCSPBEOutputEncryptorBuilder(NISTObjectIdentifiers.id_aes256_CBC)
                .setProvider(BC).setIterationCount(1000).build(password);
        ByteArrayOutputStream ciphertext = new ByteArrayOutputStream();
        try (OutputStream out = encryptor.getOutputStream(ciphertext)) {
            out.write(new DERSequence(bags).getEncoded());
        }
        EncryptedData data = new EncryptedData(PKCSObjectIdentifiers.data, encryptor.getAlgorithmIdentifier(),
                new DEROctetString(ciphertext.toByteArray()));
        return new ContentInfo[]{new ContentInfo(PKCSObjectIdentifiers.encryptedData, data)};
    }

    private static byte[] pfx(ContentInfo[] contents, MacData mac) throws IOException {
        ContentInfo authSafe = new ContentInfo(PKCSObjectIdentifiers.data,
                new DEROctetString(new AuthenticatedSafe(contents).getEncoded()));
        return new Pfx(authSafe, mac).getEncoded();
    }

    /** A keystore implementation whose load never returns unless interrupted. */
    public static final class BlockingKeyStoreSpi extends KeyStoreSpi {
        @Override
        public void engineLoad(InputStream stream, char[] password) throws IOException {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
        }

        @Override
        public Key engineGetKey(String alias, char[] password) {
            return null;
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            return null;
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            return null;
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            return null;
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert) {
        }

        @Override
        public void engineDeleteEntry(String alias) {
        }

        @Override
        public Enumeration<String> engineAliases() {
            return Collections.emptyEnumeration();
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return false;
        }

        @Override
        public int engineSize() {
            return 0;
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return false;
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return false;
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            return null;
        }

        @Override
        public void engineStore(OutputStream stream, char[] password) {
        }
    }
}
