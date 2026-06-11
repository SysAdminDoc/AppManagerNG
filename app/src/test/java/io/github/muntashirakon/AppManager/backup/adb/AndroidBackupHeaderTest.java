// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AndroidBackupHeaderTest {
    private static final String AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding";
    private static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();

    @Test
    public void parseHeaderIntAcceptsTrimmedValues() throws Exception {
        assertEquals(5, AndroidBackupHeader.parseHeaderInt(" 5 ", "backup file version"));
    }

    @Test
    public void parseHeaderIntRejectsMalformedValuesAsIoException() {
        assertThrows(IOException.class, () ->
                AndroidBackupHeader.parseHeaderInt("not-a-number", "backup file version"));
        assertThrows(IOException.class, () ->
                AndroidBackupHeader.parseHeaderInt("999999999999999999999", "PBKDF2 rounds"));
    }

    @Test
    public void parseCompressionFlagAcceptsOnlyDefinedValues() throws Exception {
        assertEquals(false, AndroidBackupHeader.parseCompressionFlag(" 0 "));
        assertEquals(true, AndroidBackupHeader.parseCompressionFlag("1"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.parseCompressionFlag("2"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.parseCompressionFlag("-1"));
    }

    @Test
    public void readKeyBlobSegmentRejectsMalformedLengths() throws Exception {
        int[] offset = {0};
        assertArrayEquals(new byte[]{1, 2}, AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{2, 1, 2, 1, 3}, offset, "encryption IV"));
        assertEquals(3, offset[0]);

        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[0], new int[]{0}, "encryption IV"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{0}, new int[]{0}, "encryption IV"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{3, 1, 2}, new int[]{0}, "encryption IV"));
        assertThrows(IOException.class, () -> AndroidBackupHeader.readKeyBlobSegment(
                new byte[]{1, 2}, new int[]{2}, "encryption key"));
    }

    @Test
    public void hexToByteArray_roundTripsHeaderBytes() {
        byte[] data = {0x00, 0x0f, (byte) 0xa0, (byte) 0xff};

        assertArrayEquals(data, AndroidBackupHeader.hexToByteArray(AndroidBackupHeader.byteArrayToHex(data)));
    }

    @Test
    public void hexToByteArray_rejectsOddLengthInput() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackupHeader.hexToByteArray("abc"));
    }

    @Test
    public void hexToByteArray_rejectsNonHexInput() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackupHeader.hexToByteArray("00zz"));
    }

    @Test
    public void hexToByteArray_rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackupHeader.hexToByteArray(null));
    }

    @Test
    public void encryptedHeadersRoundTripForKnownBackupVersions() throws Exception {
        byte[] payload = "round-trip payload".getBytes(StandardCharsets.UTF_8);
        for (int version : new int[]{1, 2, 3, 4, 5}) {
            char[] password = "correct horse battery staple".toCharArray();
            ByteArrayOutputStream backup = new ByteArrayOutputStream();
            try (OutputStream out = new AndroidBackupHeader(version, false, password).write(backup)) {
                out.write(payload);
            }

            byte[] actual = readAll(new AndroidBackupHeader(Constants.BACKUP_FILE_VERSION, true, password).read(
                    new ByteArrayInputStream(backup.toByteArray())));

            assertArrayEquals("version " + version, payload, actual);
        }
    }

    @Test
    public void encryptedVersion1HeaderFallsBackToLegacy8BitPbkdf() throws Exception {
        char[] password = "legacy-\u0101-password".toCharArray();
        byte[] payload = "legacy v1 payload".getBytes(StandardCharsets.UTF_8);
        byte[] backup = createLegacyVersion1Backup(password, payload);

        byte[] actual = readAll(new AndroidBackupHeader(Constants.BACKUP_FILE_VERSION, true, password)
                .read(new ByteArrayInputStream(backup)));

        assertArrayEquals(payload, actual);
    }

    private static byte[] createLegacyVersion1Backup(char[] password, byte[] payload) throws Exception {
        byte[] userSalt = filledBytes(64, 0x10);
        byte[] checksumSalt = filledBytes(64, 0x60);
        byte[] userIv = filledBytes(16, 0x20);
        byte[] encryptionIv = filledBytes(16, 0x30);
        byte[] encryptionKey = filledBytes(32, 0x40);

        SecretKey userKey = buildExplicitPbkdfKey(Constants.PBKDF_FALLBACK, password, userSalt,
                Constants.PBKDF2_HASH_ROUNDS);
        byte[] checksum = makeExplicitKeyChecksum(Constants.PBKDF_FALLBACK, encryptionKey, checksumSalt,
                Constants.PBKDF2_HASH_ROUNDS);
        byte[] encryptedKeyBlob = encryptKeyBlob(userKey, userIv, encryptionIv, encryptionKey, checksum);

        ByteArrayOutputStream backup = new ByteArrayOutputStream();
        backup.write((Constants.BACKUP_FILE_HEADER_MAGIC
                + "1\n"
                + "0\n"
                + Constants.ENCRYPTION_ALGORITHM_NAME + "\n"
                + AndroidBackupHeader.byteArrayToHex(userSalt) + "\n"
                + AndroidBackupHeader.byteArrayToHex(checksumSalt) + "\n"
                + Constants.PBKDF2_HASH_ROUNDS + "\n"
                + AndroidBackupHeader.byteArrayToHex(userIv) + "\n"
                + AndroidBackupHeader.byteArrayToHex(encryptedKeyBlob) + "\n")
                .getBytes(StandardCharsets.UTF_8));

        Cipher dataCipher = Cipher.getInstance(AES_CBC_PKCS5);
        dataCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                new IvParameterSpec(encryptionIv));
        backup.write(dataCipher.doFinal(payload));
        return backup.toByteArray();
    }

    private static byte[] encryptKeyBlob(SecretKey userKey, byte[] userIv, byte[] encryptionIv,
                                         byte[] encryptionKey, byte[] checksum) throws Exception {
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(blob);
        data.writeByte(encryptionIv.length);
        data.write(encryptionIv);
        data.writeByte(encryptionKey.length);
        data.write(encryptionKey);
        data.writeByte(checksum.length);
        data.write(checksum);
        data.flush();

        Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(userKey.getEncoded(), "AES"),
                new IvParameterSpec(userIv));
        return cipher.doFinal(blob.toByteArray());
    }

    private static byte[] makeExplicitKeyChecksum(String algorithm, byte[] keyBytes, byte[] salt, int rounds)
            throws Exception {
        char[] keyChars = new char[keyBytes.length];
        try {
            for (int i = 0; i < keyBytes.length; i++) {
                keyChars[i] = (char) keyBytes[i];
            }
            return buildExplicitPbkdfKey(algorithm, keyChars, salt, rounds).getEncoded();
        } finally {
            Arrays.fill(keyChars, '\u0000');
        }
    }

    private static SecretKey buildExplicitPbkdfKey(String algorithm, char[] password, byte[] salt, int rounds)
            throws Exception {
        SecretKeyFactory keyFactory = Constants.PBKDF_FALLBACK.equals(algorithm)
                ? SecretKeyFactory.getInstance(algorithm, BOUNCY_CASTLE)
                : SecretKeyFactory.getInstance(algorithm);
        KeySpec keySpec = new PBEKeySpec(password, salt, rounds, Constants.PBKDF2_KEY_SIZE);
        return keyFactory.generateSecret(keySpec);
    }

    private static byte[] filledBytes(int size, int firstValue) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (firstValue + i);
        }
        return bytes;
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
    }
}
