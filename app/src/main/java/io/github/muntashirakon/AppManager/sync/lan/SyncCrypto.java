// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Locale;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Primitives for Local sync pairing: P-256 keys with strict point checks, ECDH, HKDF-SHA256, the
 * six digit short authentication string and key fingerprints. Everything here is available from
 * API 21 through the platform providers, and none of it touches Android classes.
 */
public final class SyncCrypto {
    public static final int POINT_LENGTH = 65;
    public static final int FIELD_BYTES = 32;
    public static final int NONCE_LENGTH = 32;
    public static final int HASH_LENGTH = 32;

    private SyncCrypto() {
    }

    @NonNull
    public static KeyPair generateKeyPair(@NonNull SecureRandom random) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), random);
        return generator.generateKeyPair();
    }

    /**
     * The uncompressed SEC1 form of a P-256 public key: {@code 0x04 || X || Y}, always 65 bytes.
     */
    @NonNull
    public static byte[] encodePoint(@NonNull PublicKey publicKey) throws InvalidKeyException {
        if (!(publicKey instanceof ECPublicKey)) {
            throw new InvalidKeyException("Not an EC key.");
        }
        ECPoint w = ((ECPublicKey) publicKey).getW();
        byte[] out = new byte[POINT_LENGTH];
        out[0] = 0x04;
        writeFixed(w.getAffineX(), out, 1);
        writeFixed(w.getAffineY(), out, 1 + FIELD_BYTES);
        return out;
    }

    /**
     * Parses a peer's public point and refuses anything that is not a valid point on the same
     * curve as {@code params}. The check runs here instead of trusting the provider, so an
     * invalid-curve point never reaches the key agreement.
     */
    @NonNull
    public static ECPublicKey decodePoint(@NonNull byte[] encoded, @NonNull ECParameterSpec params)
            throws GeneralSecurityException {
        if (encoded.length != POINT_LENGTH || encoded[0] != 0x04) {
            throw new InvalidKeyException("Public key is not an uncompressed P-256 point.");
        }
        BigInteger x = new BigInteger(1, copyOfRange(encoded, 1, 1 + FIELD_BYTES));
        BigInteger y = new BigInteger(1, copyOfRange(encoded, 1 + FIELD_BYTES, POINT_LENGTH));
        EllipticCurve curve = params.getCurve();
        ECField field = curve.getField();
        if (!(field instanceof ECFieldFp) || field.getFieldSize() != 256) {
            throw new InvalidKeyException("Unexpected curve.");
        }
        BigInteger p = ((ECFieldFp) field).getP();
        if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0) {
            throw new InvalidKeyException("Point coordinate is out of range.");
        }
        BigInteger left = y.multiply(y).mod(p);
        BigInteger right = x.multiply(x).multiply(x).add(curve.getA().multiply(x)).add(curve.getB()).mod(p);
        if (!left.equals(right)) {
            throw new InvalidKeyException("Point is not on the curve.");
        }
        KeyFactory factory = KeyFactory.getInstance("EC");
        return (ECPublicKey) factory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), params));
    }

    @NonNull
    public static ECParameterSpec paramsOf(@NonNull KeyPair keyPair) {
        return ((ECPublicKey) keyPair.getPublic()).getParams();
    }

    /**
     * The provider's own P-256 parameters, taken from a throwaway key once. AlgorithmParameters
     * for "EC" is not available on every Android release, a generated key always is.
     */
    @NonNull
    public static ECParameterSpec p256() throws GeneralSecurityException {
        ECParameterSpec params = sP256;
        if (params == null) {
            params = paramsOf(generateKeyPair(new SecureRandom()));
            sP256 = params;
        }
        return params;
    }

    private static volatile ECParameterSpec sP256;

    @NonNull
    public static PrivateKey decodePrivateKey(@NonNull byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    /**
     * Rebuilds a stored key pair from its PKCS#8 private key and encoded public point.
     */
    @NonNull
    public static KeyPair restoreKeyPair(@NonNull byte[] pkcs8, @NonNull byte[] publicPoint)
            throws GeneralSecurityException {
        PrivateKey privateKey = decodePrivateKey(pkcs8);
        if (!(privateKey instanceof ECPrivateKey)) {
            throw new InvalidKeyException("Stored key is not an EC key.");
        }
        PublicKey publicKey = decodePoint(publicPoint, ((ECPrivateKey) privateKey).getParams());
        return new KeyPair(publicKey, privateKey);
    }

    @NonNull
    public static byte[] ecdh(@NonNull PrivateKey ours, @NonNull PublicKey theirs) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ours);
        agreement.doPhase(theirs, true);
        return agreement.generateSecret();
    }

    /**
     * HKDF-SHA256 (RFC 5869), extract then expand.
     */
    @NonNull
    public static byte[] hkdf(@NonNull byte[] salt, @NonNull byte[] ikm, @NonNull byte[] info, int length)
            throws GeneralSecurityException {
        if (length <= 0 || length > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException("Bad HKDF length " + length);
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[HASH_LENGTH] : salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[length];
        byte[] block = new byte[0];
        int offset = 0;
        for (int counter = 1; offset < length; ++counter) {
            mac.update(block);
            mac.update(info);
            mac.update((byte) counter);
            block = mac.doFinal();
            int n = Math.min(block.length, length - offset);
            System.arraycopy(block, 0, out, offset, n);
            offset += n;
        }
        return out;
    }

    /**
     * SHA-256 over a domain label and parts, each part prefixed with its length so that no two
     * different part lists hash the same.
     */
    @NonNull
    public static byte[] hash(@NonNull String label, @NonNull byte[]... parts) {
        MessageDigest digest = sha256();
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        digest.update(FrameIo.lengthHeader(labelBytes.length));
        digest.update(labelBytes);
        for (byte[] part : parts) {
            digest.update(FrameIo.lengthHeader(part.length));
            digest.update(part);
        }
        return digest.digest();
    }

    /**
     * Six decimal digits both screens show. Derived from the transcript hash, so anyone who swaps
     * a key or nonce in transit changes the digits on one side only.
     */
    @NonNull
    public static String shortAuthString(@NonNull byte[] transcriptHash) {
        byte[] h = hash("AMNG-SYNC/1 sas", transcriptHash);
        long value = ((h[0] & 0xFFL) << 24) | ((h[1] & 0xFFL) << 16) | ((h[2] & 0xFFL) << 8) | (h[3] & 0xFFL);
        return String.format(Locale.ROOT, "%06d", value % 1_000_000L);
    }

    @NonNull
    public static String fingerprint(@NonNull byte[] encodedPublicKey) {
        return toHex(hash("AMNG-SYNC/1 identity", encodedPublicKey));
    }

    @NonNull
    public static byte[] sign(@NonNull PrivateKey key, @NonNull byte[] data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        signature.update(data);
        return signature.sign();
    }

    public static boolean verify(@NonNull PublicKey key, @NonNull byte[] data, @NonNull byte[] sig)
            throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(key);
        signature.update(data);
        try {
            return signature.verify(sig);
        } catch (SignatureException e) {
            // A malformed DER signature is a failed verification, not a crash
            return false;
        }
    }

    @NonNull
    public static String toHex(@NonNull byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; ++i) {
            out[i * 2] = digits[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = digits[bytes[i] & 0xF];
        }
        return new String(out);
    }

    @NonNull
    public static byte[] fromHex(@NonNull String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Odd hex length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; ++i) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Not hex");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    @NonNull
    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }

    @NonNull
    static byte[] copyOfRange(@NonNull byte[] src, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(src, from, out, 0, out.length);
        return out;
    }

    private static void writeFixed(@NonNull BigInteger value, @NonNull byte[] out, int offset) {
        byte[] raw = value.toByteArray();
        int start = 0;
        int length = raw.length;
        // Drop the sign byte BigInteger adds for a set top bit
        while (length > FIELD_BYTES && raw[start] == 0) {
            ++start;
            --length;
        }
        if (length > FIELD_BYTES) {
            throw new IllegalArgumentException("Coordinate does not fit in 32 bytes.");
        }
        System.arraycopy(raw, start, out, offset + FIELD_BYTES - length, length);
    }
}
