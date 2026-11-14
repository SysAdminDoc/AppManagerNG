// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.utils.DurableFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class SignerPinStoreTest {
    private static final String SIGNER_A = repeat('a');
    private static final String SIGNER_B = repeat('b');

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private File mFile;
    private SignerPinStore mStore;

    @Before
    public void setUp() {
        mFile = new File(mTemp.getRoot(), SignerPinStore.FILE_NAME);
        mStore = new SignerPinStore(mFile);
    }

    @Test
    public void pinsSurviveANewStoreOnTheSameFile() throws IOException {
        mStore.put("com.example.one", new SignerPin(Arrays.asList(SIGNER_B, SIGNER_A.toUpperCase(Locale.ROOT)), 42));
        mStore.put("com.example.two", new SignerPin(Collections.singletonList(SIGNER_B), 7));

        Map<String, SignerPin> read = new SignerPinStore(mFile).readAll();

        assertEquals(Arrays.asList("com.example.one", "com.example.two"), Arrays.asList(read.keySet().toArray()));
        assertEquals(Arrays.asList(SIGNER_A, SIGNER_B), read.get("com.example.one").certSha256);
        assertEquals(42, read.get("com.example.one").pinnedAt);
        assertTrue(read.get("com.example.two").isSameSet(Collections.singletonList(SIGNER_B.toUpperCase(Locale.ROOT))));
    }

    @Test
    public void aMissingFileHasNoPins() throws IOException {
        assertTrue(mStore.readAll().isEmpty());
        assertNull(mStore.get("com.example.one"));
        assertNull(mStore.remove("com.example.one"));
        assertFalse(mFile.exists());
    }

    @Test
    public void removeDropsOnlyThatPackage() throws IOException {
        mStore.put("com.example.one", pin(SIGNER_A));
        mStore.put("com.example.two", pin(SIGNER_B));

        SignerPin removed = mStore.remove("com.example.one");

        assertTrue(removed.isSameSet(Collections.singletonList(SIGNER_A)));
        assertNull(mStore.get("com.example.one"));
        assertTrue(mStore.get("com.example.two").isSameSet(Collections.singletonList(SIGNER_B)));
    }

    @Test
    public void addMissingNeverReplacesAPinThisDeviceHas() throws IOException {
        mStore.put("com.example.one", pin(SIGNER_A));
        Map<String, SignerPin> incoming = new LinkedHashMap<>();
        incoming.put("com.example.one", pin(SIGNER_B));
        incoming.put("com.example.two", pin(SIGNER_B));

        List<String> added = mStore.addMissing(incoming);

        assertEquals(Collections.singletonList("com.example.two"), added);
        assertTrue(mStore.get("com.example.one").isSameSet(Collections.singletonList(SIGNER_A)));
        assertTrue(mStore.get("com.example.two").isSameSet(Collections.singletonList(SIGNER_B)));
    }

    @Test
    public void putHandsBackThePinItReplaced() throws IOException {
        assertNull(mStore.put("com.example.one", pin(SIGNER_A)));

        SignerPin replaced = mStore.put("com.example.one", pin(SIGNER_B));

        assertNotNull(replaced);
        assertTrue(replaced.isSameSet(Collections.singletonList(SIGNER_A)));
        assertTrue(mStore.get("com.example.one").isSameSet(Collections.singletonList(SIGNER_B)));
    }

    @Test
    public void aDamagedFileIsUnreadableAndIsNotOverwritten() throws IOException {
        byte[] damaged = "{\"schema_version\":1,\"pins\":{\"com.example.one\":{\"cert_shas256\":[\"xyz\"]}}}"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(mFile.toPath(), damaged);

        assertThrows(SignerPinStore.UnreadableException.class, () -> mStore.get("com.example.one"));
        assertThrows(SignerPinStore.UnreadableException.class, () -> mStore.put("com.example.two", pin(SIGNER_A)));
        assertThrows(SignerPinStore.UnreadableException.class,
                () -> mStore.addMissing(Collections.singletonMap("com.example.two", pin(SIGNER_A))));

        assertTrue(Arrays.equals(damaged, Files.readAllBytes(mFile.toPath())));
    }

    @Test
    public void aListFromANewerSchemaIsUnreadable() throws IOException {
        Files.write(mFile.toPath(), ("{\"schema_version\":" + (SignerPinStore.SCHEMA_VERSION + 1)
                + ",\"pins\":{}}").getBytes(StandardCharsets.UTF_8));

        assertThrows(SignerPinStore.UnreadableException.class, () -> mStore.readAll());
    }

    @Test
    public void clearRemovesEvenADamagedList() throws IOException {
        Files.write(mFile.toPath(), "not json".getBytes(StandardCharsets.UTF_8));

        mStore.clear();

        assertTrue(mStore.readAll().isEmpty());
        mStore.put("com.example.one", pin(SIGNER_A));
        assertTrue(mStore.get("com.example.one").isSameSet(Collections.singletonList(SIGNER_A)));
    }

    @Test
    public void replaceOnlyChangesThePinTheUserWasShown() throws IOException {
        assertNull(mStore.replace("com.example.one", null, pin(SIGNER_A)));
        // Shown as unpinned, but pinned since then
        assertThrows(SignerPinStore.ChangedException.class, () -> mStore.replace("com.example.one", null, pin(SIGNER_B)));
        // Shown with B, but it's A
        assertThrows(SignerPinStore.ChangedException.class,
                () -> mStore.replace("com.example.one", pin(SIGNER_B), null));
        assertTrue(mStore.get("com.example.one").isSameSet(Collections.singletonList(SIGNER_A)));

        SignerPin replaced = mStore.replace("com.example.one", pin(SIGNER_A), pin(SIGNER_B));
        assertTrue(replaced.isSameSet(Collections.singletonList(SIGNER_A)));
        SignerPin removed = mStore.replace("com.example.one", pin(SIGNER_B), null);
        assertTrue(removed.isSameSet(Collections.singletonList(SIGNER_B)));
        assertNull(mStore.get("com.example.one"));
        // Shown with B, but it's gone
        assertThrows(SignerPinStore.ChangedException.class,
                () -> mStore.replace("com.example.one", pin(SIGNER_B), null));
    }

    @Test
    public void aFolderWhereTheListGoesIsUnreadableAndAFailedClearSaysSo() throws IOException {
        assertTrue(mFile.mkdirs());
        assertTrue(new File(mFile, "stray").createNewFile());

        assertThrows(SignerPinStore.UnreadableException.class, () -> mStore.readAll());
        assertThrows(IOException.class, () -> mStore.takeAll());
        assertTrue(mFile.isDirectory());
    }

    @Test
    public void aBackupThatCantBePutBackIsUnreadable() throws IOException {
        mStore.put("com.example.one", pin(SIGNER_A));
        File backup = new File(mFile.getPath() + ".bak");
        // A write that stopped right after moving the list aside
        assertTrue(mFile.renameTo(backup));
        File folder = mTemp.getRoot();
        // An open RandomAccessFile blocks the rename back on Windows (some JDKs open streams with
        // FILE_SHARE_DELETE, so a stream may not), a read-only folder on POSIX
        try (RandomAccessFile ignored = new RandomAccessFile(backup, "r")) {
            folder.setWritable(false);
            try {
                new DurableFile(mFile).recover();
                assumeTrue("recover() put the backup back here", backup.exists() && !mFile.exists());

                assertThrows(SignerPinStore.UnreadableException.class, () -> mStore.readAll());
                assertThrows(SignerPinStore.UnreadableException.class, () -> mStore.get("com.example.one"));
                assertThrows(SignerPinStore.UnreadableException.class,
                        () -> mStore.put("com.example.two", pin(SIGNER_B)));
                assertThrows(IOException.class, () -> mStore.takeAll());
            } finally {
                folder.setWritable(true);
            }
        }
        assertEquals(Collections.singletonList("com.example.one"),
                Arrays.asList(mStore.readAll().keySet().toArray()));
    }

    @Test
    public void takeAllHandsBackWhatItRemoved() throws IOException {
        mStore.put("com.example.one", pin(SIGNER_A));

        Map<String, SignerPin> taken = mStore.takeAll();

        assertEquals(Collections.singletonList("com.example.one"), Arrays.asList(taken.keySet().toArray()));
        assertFalse(mFile.exists());
        Files.write(mFile.toPath(), "not json".getBytes(StandardCharsets.UTF_8));
        assertNull(mStore.takeAll());
        assertFalse(mFile.exists());
    }

    @Test
    public void aPinNeedsAtLeastOneRealSha256() {
        assertThrows(IllegalArgumentException.class, () -> new SignerPin(Collections.emptyList(), 0));
        assertThrows(IllegalArgumentException.class, () -> new SignerPin(Collections.singletonList("abc"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SignerPin(Collections.singletonList(SIGNER_A.substring(1) + "g"), 0));
    }

    @NonNull
    private static SignerPin pin(@NonNull String digest) {
        return new SignerPin(Collections.singletonList(digest), 1);
    }

    @NonNull
    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
