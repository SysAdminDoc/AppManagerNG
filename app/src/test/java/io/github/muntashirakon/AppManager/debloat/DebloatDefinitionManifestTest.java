// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.util.Base64;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class DebloatDefinitionManifestTest {
    private static final long NOW = 1_780_000_000_000L;
    private static final String KEY_ID = "test-key-1";
    private static final String ROTATED_KEY_ID = "test-key-2";
    private static final String DEBLOAT_URL =
            "https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/app/src/main/assets/debloat.json";
    private static final String SUGGESTIONS_URL =
            "https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/app/src/main/assets/suggestions.json";
    private static final String DIGEST = "2006caa15d77d0c5b9c04e4cf5f57631f0e8f56b09edf6105abbc66e8b12194a";

    private static KeyPair sKeyPair;
    private static KeyPair sRotatedKeyPair;

    private Gson mGson;
    private Map<String, String> mPinnedKeys;

    @BeforeClass
    public static void generateKeys() throws GeneralSecurityException {
        sKeyPair = newKeyPair();
        sRotatedKeyPair = newKeyPair();
    }

    @Before
    public void setUp() {
        mGson = new Gson();
        mPinnedKeys = Collections.singletonMap(KEY_ID, encodePublicKey(sKeyPair));
    }

    @Test
    public void aSignedManifestVerifies() throws IOException {
        DebloatDefinitionManifest manifest = verify(envelope(document(3, "2026-07-29", expiry(30)),
                KEY_ID, sKeyPair));
        assertEquals(3, manifest.generation);
        assertEquals("2026-07-29", manifest.version);
        assertEquals(KEY_ID, manifest.keyId);
        assertEquals(DEBLOAT_URL, manifest.debloat.getUrl());
        assertEquals(DIGEST, manifest.suggestions.getSha256());
        assertTrue(manifest.expiresAtMillis > NOW);
    }

    @Test
    public void anUnsignedManifestIsRejected() {
        String envelope = "{\"schema\":2,\"signed\":\""
                + base64(document(3, "2026-07-29", expiry(30)))
                + "\",\"signatures\":[]}";
        assertThrows(IOException.class, () -> verify(envelope));
    }

    @Test
    public void theLegacyUnsignedSchemaIsRejected() {
        String legacy = "{\"schema\":1,\"version\":\"2026-05-17\",\"files\":{\"debloat\":{\"url\":\""
                + DEBLOAT_URL + "\",\"sha256\":\"" + DIGEST + "\",\"bytes\":10}}}";
        assertThrows(IOException.class, () -> verify(legacy));
    }

    @Test
    public void aManifestSignedByAnUnpinnedKeyIsRejected() {
        String envelope = envelope(document(3, "2026-07-29", expiry(30)), ROTATED_KEY_ID, sRotatedKeyPair);
        assertThrows(IOException.class, () -> verify(envelope));
    }

    @Test
    public void aTamperedDocumentIsRejected() {
        String honest = document(3, "2026-07-29", expiry(30));
        String tampered = honest.replace("\"generation\": 3", "\"generation\": 9");
        String signature = signature(honest, sKeyPair);
        String envelope = "{\"schema\":2,\"signed\":\"" + base64(tampered)
                + "\",\"signatures\":[{\"keyId\":\"" + KEY_ID + "\",\"alg\":\""
                + DebloatDefinitionManifest.SIGNATURE_ALGORITHM + "\",\"sig\":\"" + signature + "\"}]}";
        assertThrows(IOException.class, () -> verify(envelope));
    }

    @Test
    public void anExpiredManifestIsRejected() {
        String envelope = envelope(document(3, "2026-07-29", expiry(-1)), KEY_ID, sKeyPair);
        assertThrows(IOException.class, () -> verify(envelope));
    }

    @Test
    public void aManifestWithoutAGenerationIsRejected() {
        String envelope = envelope(document(0, "2026-07-29", expiry(30)), KEY_ID, sKeyPair);
        assertThrows(IOException.class, () -> verify(envelope));
    }

    @Test
    public void anIncompleteGenerationIsRejected() {
        String document = "{\"schema\":2,\"generation\":3,\"version\":\"2026-07-29\",\"expires\":\""
                + expiry(30) + "\",\"files\":{\"debloat\":" + file(DEBLOAT_URL) + "}}";
        String envelope = envelope(document, KEY_ID, sKeyPair);
        assertThrows(IOException.class, () -> verify(envelope));
    }

    @Test
    public void aKeyRotationVerifiesOnBothSides() throws IOException {
        // A rotation manifest carries a signature from the outgoing and the incoming key.
        String document = document(4, "2026-08-01", expiry(30));
        String envelope = "{\"schema\":2,\"signed\":\"" + base64(document) + "\",\"signatures\":["
                + "{\"keyId\":\"" + KEY_ID + "\",\"alg\":\"" + DebloatDefinitionManifest.SIGNATURE_ALGORITHM
                + "\",\"sig\":\"" + signature(document, sKeyPair) + "\"},"
                + "{\"keyId\":\"" + ROTATED_KEY_ID + "\",\"alg\":\""
                + DebloatDefinitionManifest.SIGNATURE_ALGORITHM
                + "\",\"sig\":\"" + signature(document, sRotatedKeyPair) + "\"}]}";
        // A build that only knows the outgoing key still accepts it.
        assertEquals(KEY_ID, verify(envelope).keyId);
        // A build that only knows the incoming key accepts it too.
        mPinnedKeys = Collections.singletonMap(ROTATED_KEY_ID, encodePublicKey(sRotatedKeyPair));
        assertEquals(ROTATED_KEY_ID, verify(envelope).keyId);
        // A build that knows both accepts it as well.
        Map<String, String> both = new HashMap<>(mPinnedKeys);
        both.put(KEY_ID, encodePublicKey(sKeyPair));
        mPinnedKeys = both;
        assertTrue(verify(envelope).keyId.startsWith("test-key-"));
    }

    @Test
    public void aSignatureFromARevokedKeyIsRejectedAfterRotation() {
        String document = document(4, "2026-08-01", expiry(30));
        String envelope = envelope(document, KEY_ID, sKeyPair);
        mPinnedKeys = Collections.singletonMap(ROTATED_KEY_ID, encodePublicKey(sRotatedKeyPair));
        assertThrows(IOException.class, () -> verify(envelope));
    }

    private DebloatDefinitionManifest verify(String envelope) throws IOException {
        return DebloatDefinitionManifest.verify(mGson, envelope.getBytes(StandardCharsets.UTF_8), NOW,
                mPinnedKeys);
    }

    private static String document(long generation, String version, String expires) {
        return "{\n  \"schema\": 2,\n  \"generation\": " + generation
                + ",\n  \"version\": \"" + version + "\",\n  \"expires\": \"" + expires
                + "\",\n  \"files\": {\"debloat\": " + file(DEBLOAT_URL)
                + ", \"suggestions\": " + file(SUGGESTIONS_URL) + "}\n}";
    }

    private static String file(String url) {
        return "{\"url\": \"" + url + "\", \"sha256\": \"" + DIGEST + "\", \"bytes\": 1063205}";
    }

    private static String envelope(String document, String keyId, KeyPair keyPair) {
        return "{\"schema\":2,\"signed\":\"" + base64(document) + "\",\"signatures\":[{\"keyId\":\""
                + keyId + "\",\"alg\":\"" + DebloatDefinitionManifest.SIGNATURE_ALGORITHM
                + "\",\"sig\":\"" + signature(document, keyPair) + "\"}]}";
    }

    private static String expiry(int daysFromNow) {
        long millis = NOW + TimeUnit.DAYS.toMillis(daysFromNow);
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date(millis));
    }

    private static String base64(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String signature(String document, KeyPair keyPair) {
        try {
            Signature signer = Signature.getInstance(DebloatDefinitionManifest.SIGNATURE_ALGORITHM);
            signer.initSign((PrivateKey) keyPair.getPrivate());
            signer.update(document.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(signer.sign(), Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static String encodePublicKey(KeyPair keyPair) {
        return Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.NO_WRAP);
    }

    private static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
