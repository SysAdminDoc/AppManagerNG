// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Signed metadata for the remote debloat-definition feed.
 *
 * <p>The envelope carries the signed document verbatim as a base64 blob, so verification never
 * depends on JSON canonicalisation: the exact bytes that were signed are the exact bytes that are
 * parsed. Signatures are ECDSA-P256/SHA-256 over those bytes and are checked against a pinned key
 * set; the set holds more than one entry only while a key rotation is in flight.
 *
 * <p>The document also carries a monotonically increasing generation number and an expiry, which
 * lets the updater reject rollbacks and stale metadata, and it names both payload files, which is
 * what makes a mixed-generation activation impossible.
 */
public final class DebloatDefinitionManifest {
    public static final int SCHEMA_VERSION = 2;
    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /**
     * Pinned verification keys, keyed by the identifier that appears in the envelope. X.509
     * ({@code SubjectPublicKeyInfo}) DER, base64-encoded. Add the successor here, publish a
     * manifest signed by both, then drop the predecessor in a later release.
     */
    private static final Map<String, String> PINNED_KEYS;

    static {
        Map<String, String> keys = new HashMap<>(1);
        keys.put("ng-debloat-2026-07", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzauHM+RM+k5pfNQ4NgK6GmVsyb3f"
                + "IgubIhLhH4wRVXiozLFKVzyfhcxur193HXY0fWk1L850OtF4LJU31AG1Jg==");
        PINNED_KEYS = Collections.unmodifiableMap(keys);
    }

    @NonNull
    public final String version;
    public final long generation;
    public final long expiresAtMillis;
    @NonNull
    public final DefinitionFile debloat;
    @NonNull
    public final DefinitionFile suggestions;
    @NonNull
    public final String keyId;

    private DebloatDefinitionManifest(@NonNull SignedDocument document, @NonNull String keyId, long expiresAtMillis) {
        this.version = document.version;
        this.generation = document.generation;
        this.expiresAtMillis = expiresAtMillis;
        this.debloat = document.files.debloat;
        this.suggestions = document.files.suggestions;
        this.keyId = keyId;
    }

    /**
     * Parses and verifies a manifest envelope.
     *
     * @param nowMillis current time, used for the expiry check.
     * @throws IOException if the envelope is malformed, unsigned, signed by an unpinned key,
     *                     tampered with, expired, or describes an incomplete generation.
     */
    @NonNull
    public static DebloatDefinitionManifest verify(@NonNull Gson gson, @NonNull byte[] envelopeBytes, long nowMillis)
            throws IOException {
        return verify(gson, envelopeBytes, nowMillis, PINNED_KEYS);
    }

    @VisibleForTesting
    @NonNull
    static DebloatDefinitionManifest verify(@NonNull Gson gson,
                                            @NonNull byte[] envelopeBytes,
                                            long nowMillis,
                                            @NonNull Map<String, String> pinnedKeys) throws IOException {
        Envelope envelope = parse(gson, new String(envelopeBytes, StandardCharsets.UTF_8), Envelope.class);
        if (envelope == null || envelope.schema != SCHEMA_VERSION) {
            throw new IOException("Unsupported debloat definition manifest schema.");
        }
        if (envelope.signed == null || envelope.signatures == null || envelope.signatures.length == 0) {
            throw new IOException("The debloat definition manifest is not signed.");
        }
        byte[] signedBytes = decodeBase64(envelope.signed, "signed document");
        String verifiedKeyId = verifySignatures(envelope.signatures, signedBytes, pinnedKeys);
        SignedDocument document = parse(gson, new String(signedBytes, StandardCharsets.UTF_8), SignedDocument.class);
        if (document == null || document.schema != SCHEMA_VERSION) {
            throw new IOException("Unsupported debloat definition document schema.");
        }
        if (document.version == null || document.version.isEmpty()) {
            throw new IOException("The debloat definition document has no version.");
        }
        if (document.generation <= 0) {
            throw new IOException("The debloat definition document has no generation number.");
        }
        if (document.files == null || document.files.debloat == null || document.files.suggestions == null) {
            throw new IOException("The debloat definition document does not describe a complete generation.");
        }
        document.files.debloat.validate("debloat");
        document.files.suggestions.validate("suggestions");
        long expiresAtMillis = parseExpiry(document.expires);
        if (expiresAtMillis <= nowMillis) {
            throw new IOException("The debloat definition manifest expired on " + document.expires + ".");
        }
        return new DebloatDefinitionManifest(document, verifiedKeyId, expiresAtMillis);
    }

    @NonNull
    private static String verifySignatures(@NonNull EnvelopeSignature[] signatures,
                                           @NonNull byte[] signedBytes,
                                           @NonNull Map<String, String> pinnedKeys) throws IOException {
        IOException lastFailure = null;
        for (EnvelopeSignature signature : signatures) {
            if (signature == null || signature.keyId == null || signature.sig == null) {
                continue;
            }
            String encodedKey = pinnedKeys.get(signature.keyId);
            if (encodedKey == null) {
                // Signatures from keys we do not pin (predecessors, successors) are simply ignored.
                continue;
            }
            if (signature.alg != null && !SIGNATURE_ALGORITHM.equals(signature.alg)) {
                lastFailure = new IOException("Unsupported signature algorithm: " + signature.alg);
                continue;
            }
            try {
                if (isSignatureValid(encodedKey, signedBytes, decodeBase64(signature.sig, "signature"))) {
                    return signature.keyId;
                }
                lastFailure = new IOException("The debloat definition manifest signature does not verify.");
            } catch (GeneralSecurityException e) {
                lastFailure = new IOException("Could not verify the debloat definition manifest signature.", e);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IOException("The debloat definition manifest is not signed by a pinned key.");
    }

    private static boolean isSignatureValid(@NonNull String encodedPublicKey,
                                            @NonNull byte[] signedBytes,
                                            @NonNull byte[] signatureBytes) throws GeneralSecurityException {
        PublicKey publicKey = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(decodeBase64Unchecked(encodedPublicKey)));
        Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(signedBytes);
        return verifier.verify(signatureBytes);
    }

    private static long parseExpiry(@Nullable String expires) throws IOException {
        if (expires == null) {
            throw new IOException("The debloat definition manifest has no expiry.");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        try {
            Date date = format.parse(expires);
            if (date == null) {
                throw new IOException("Malformed debloat definition manifest expiry: " + expires);
            }
            return date.getTime();
        } catch (ParseException e) {
            throw new IOException("Malformed debloat definition manifest expiry: " + expires, e);
        }
    }

    @Nullable
    private static <T> T parse(@NonNull Gson gson, @NonNull String json, @NonNull Class<T> type) throws IOException {
        try {
            return gson.fromJson(json, type);
        } catch (RuntimeException e) {
            throw new IOException("Malformed debloat definition manifest.", e);
        }
    }

    @NonNull
    private static byte[] decodeBase64(@NonNull String value, @NonNull String what) throws IOException {
        try {
            return decodeBase64Unchecked(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed base64 in the debloat definition manifest " + what + ".", e);
        }
    }

    @NonNull
    private static byte[] decodeBase64Unchecked(@NonNull String value) {
        return Base64.decode(value, Base64.DEFAULT);
    }

    public static final class DefinitionFile {
        @SerializedName("url")
        String url;
        @SerializedName("sha256")
        String sha256;
        @SerializedName("bytes")
        long bytes;

        @NonNull
        public String getUrl() {
            return url;
        }

        @NonNull
        public String getSha256() {
            return sha256;
        }

        public long getBytes() {
            return bytes;
        }

        void validate(@NonNull String what) throws IOException {
            if (url == null || url.isEmpty()) {
                throw new IOException("The " + what + " entry has no URL.");
            }
            if (sha256 == null || sha256.length() != 64) {
                throw new IOException("The " + what + " entry has no SHA-256 digest.");
            }
            if (bytes <= 0) {
                throw new IOException("The " + what + " entry has no length.");
            }
        }
    }

    private static final class Envelope {
        @SerializedName("schema")
        int schema;
        @SerializedName("signed")
        String signed;
        @SerializedName("signatures")
        EnvelopeSignature[] signatures;
    }

    private static final class EnvelopeSignature {
        @SerializedName("keyId")
        String keyId;
        @SerializedName("alg")
        String alg;
        @SerializedName("sig")
        String sig;
    }

    private static final class SignedDocument {
        @SerializedName("schema")
        int schema;
        @SerializedName("generation")
        long generation;
        @SerializedName("version")
        String version;
        @SerializedName("expires")
        String expires;
        @SerializedName("files")
        SignedFiles files;
    }

    private static final class SignedFiles {
        @SerializedName("debloat")
        DefinitionFile debloat;
        @SerializedName("suggestions")
        DefinitionFile suggestions;
    }
}
