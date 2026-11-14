// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;

@RunWith(RobolectricTestRunner.class)
public class SignerPinEditorTest {
    private static final String SIGNER_A = repeat('a');
    private static final String SIGNER_B = repeat('b');
    private static final String PKG = "com.example.pinned";
    private static final String OTHER = "com.example.other";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private Context mContext;
    private File mFile;
    private SignerPinStore mStore;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mFile = new File(mTemp.getRoot(), SignerPinStore.FILE_NAME);
        mStore = new SignerPinStore(mFile);
        runOnBackground(() -> {
            OpHistoryManager.clearAllHistory();
            return null;
        });
    }

    @Test
    public void aPinIsSavedAndRecordedAsAHighRiskChangeThatCannotBeUndone() throws Exception {
        SignerPin previous = runOnBackground(() ->
                SignerPinEditor.pin(mContext, mStore, PKG, 10, null, Collections.singletonList(SIGNER_A)));

        assertNull(previous);
        assertTrue(new SignerPinStore(mFile).get(PKG).isSameSet(Collections.singletonList(SIGNER_A)));
        List<OpHistory> rows = rows();
        assertEquals(1, rows.size());
        OpHistory row = rows.get(0);
        assertEquals(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, row.type);
        assertEquals(OpHistoryManager.STATUS_SUCCESS, row.status);
        JSONObject data = new JSONObject(row.serializedData);
        assertEquals(SingleAppActionHistoryItem.ACTION_SIGNER_PIN, data.getString("action"));
        assertEquals(PKG, data.getString("package_name"));
        assertEquals(10, data.getInt("user_id"));
        assertEquals(SignerPin.format(SIGNER_A), data.getString("target_label"));
        assertFalse(data.has("detail"));
        OperationJournalMetadata metadata = OperationJournalMetadata.fromJson(row.serializedExtra);
        assertEquals(OperationJournalMetadata.RISK_HIGH, metadata.getRisk());
        assertFalse(metadata.isReversible());
    }

    @Test
    public void replacingAPinRecordsTheKeyItReplaced() throws Exception {
        SignerPin previous = runOnBackground(() -> {
            SignerPinEditor.pin(mContext, mStore, PKG, 0, null, Collections.singletonList(SIGNER_A));
            return SignerPinEditor.pin(mContext, mStore, PKG, 0, pin(SIGNER_A), Collections.singletonList(SIGNER_B));
        });

        assertTrue(previous.isSameSet(Collections.singletonList(SIGNER_A)));
        assertTrue(mStore.get(PKG).isSameSet(Collections.singletonList(SIGNER_B)));
        List<JSONObject> data = data(rows());
        assertEquals(2, data.size());
        JSONObject replaced = data.get(0).has("detail") ? data.get(0) : data.get(1);
        assertEquals(SignerPin.format(SIGNER_B), replaced.getString("target_label"));
        assertEquals(mContext.getString(R.string.signer_pin_history_replaced, SignerPin.format(SIGNER_A)),
                replaced.getString("detail"));
    }

    @Test
    public void removingAPinIsRecorded() throws Exception {
        mStore.put(PKG, new SignerPin(Collections.singletonList(SIGNER_A), 1));
        SignerPin removed = runOnBackground(() -> SignerPinEditor.unpin(mContext, mStore, PKG, 0, pin(SIGNER_A)));

        assertTrue(removed.isSameSet(Collections.singletonList(SIGNER_A)));
        assertNull(mStore.get(PKG));
        List<OpHistory> rows = rows();
        assertEquals(1, rows.size());
        assertEquals(OpHistoryManager.STATUS_SUCCESS, rows.get(0).status);
        JSONObject data = new JSONObject(rows.get(0).serializedData);
        assertEquals(SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, data.getString("action"));
        assertEquals(SignerPin.format(SIGNER_A), data.getString("target_label"));
        assertEquals(OperationJournalMetadata.RISK_HIGH,
                OperationJournalMetadata.fromJson(rows.get(0).serializedExtra).getRisk());
    }

    @Test
    public void aDamagedListIsNeverOverwrittenByAPinAndTheAttemptIsRecorded() throws Exception {
        byte[] damaged = "{not json".getBytes(StandardCharsets.UTF_8);
        Files.write(mFile.toPath(), damaged);

        assertThrows(SignerPinStore.UnreadableException.class, () -> runOnBackgroundRethrowing(() ->
                SignerPinEditor.pin(mContext, mStore, PKG, 0, null, Collections.singletonList(SIGNER_A))));

        assertArrayEquals(damaged, Files.readAllBytes(mFile.toPath()));
        assertOneFailure(SingleAppActionHistoryItem.ACTION_SIGNER_PIN, PKG);
    }

    @Test
    public void aPinThatCannotBeWrittenIsRecordedAsAFailure() throws Exception {
        File notADirectory = mTemp.newFile("plain-file");
        SignerPinStore store = new SignerPinStore(new File(notADirectory, SignerPinStore.FILE_NAME));

        assertThrows(IOException.class, () -> runOnBackgroundRethrowing(() ->
                SignerPinEditor.pin(mContext, store, PKG, 0, null, Collections.singletonList(SIGNER_A))));

        assertOneFailure(SingleAppActionHistoryItem.ACTION_SIGNER_PIN, PKG);
    }

    @Test
    public void aChoiceMadeOnAStaleDialogChangesNothingAndIsRecorded() throws Exception {
        // The dialog showed no pin, but one was saved since
        mStore.put(PKG, pin(SIGNER_B));
        assertThrows(SignerPinStore.ChangedException.class, () -> runOnBackgroundRethrowing(() ->
                SignerPinEditor.pin(mContext, mStore, PKG, 0, null, Collections.singletonList(SIGNER_A))));
        assertTrue(mStore.get(PKG).isSameSet(Collections.singletonList(SIGNER_B)));
        assertOneFailure(SingleAppActionHistoryItem.ACTION_SIGNER_PIN, PKG);

        // The dialog showed A, but it's B now
        runOnBackground(() -> {
            OpHistoryManager.clearAllHistory();
            return null;
        });
        assertThrows(SignerPinStore.ChangedException.class, () -> runOnBackgroundRethrowing(() ->
                SignerPinEditor.unpin(mContext, mStore, PKG, 0, pin(SIGNER_A))));
        assertTrue(mStore.get(PKG).isSameSet(Collections.singletonList(SIGNER_B)));
        assertOneFailure(SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, PKG);
    }

    @Test
    public void aClearThatCannotRemoveTheListIsRecordedAndReported() throws Exception {
        assertTrue(mFile.mkdirs());
        assertTrue(new File(mFile, "stray").createNewFile());

        assertThrows(IOException.class, () -> runOnBackgroundRethrowing(() ->
                SignerPinEditor.clearAll(mContext, mStore)));

        assertOneFailure(SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, SignerPinStore.FILE_NAME);
    }

    @Test
    public void aRestoreThatCannotWriteRecordsEachPinItOffered() throws Exception {
        Files.write(mFile.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));

        assertThrows(SignerPinStore.UnreadableException.class, () -> runOnBackgroundRethrowing(() ->
                SignerPinEditor.restore(mContext, mStore, Collections.singletonMap(OTHER, pin(SIGNER_B)))));

        assertOneFailure(SingleAppActionHistoryItem.ACTION_SIGNER_PIN, OTHER);
    }

    @Test
    public void clearingRecordsEveryPinItRemoved() throws Exception {
        mStore.put(PKG, new SignerPin(Collections.singletonList(SIGNER_A), 1));
        mStore.put(OTHER, new SignerPin(Collections.singletonList(SIGNER_B), 2));

        assertEquals(2, (int) runOnBackground(() -> SignerPinEditor.clearAll(mContext, mStore)));

        assertTrue(mStore.readAll().isEmpty());
        List<String> packages = new ArrayList<>();
        for (JSONObject data : data(rows())) {
            assertEquals(SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, data.getString("action"));
            packages.add(data.getString("package_name"));
        }
        Collections.sort(packages);
        assertEquals(Arrays.asList(OTHER, PKG), packages);
    }

    @Test
    public void clearingAnUnreadableListRecordsOneRemovalForTheWholeList() throws Exception {
        Files.write(mFile.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));

        assertEquals(-1, (int) runOnBackground(() -> SignerPinEditor.clearAll(mContext, mStore)));

        assertFalse(mFile.exists());
        assertTrue(mStore.readAll().isEmpty());
        List<JSONObject> data = data(rows());
        assertEquals(1, data.size());
        assertEquals(SignerPinStore.FILE_NAME, data.get(0).getString("package_name"));
        assertEquals(mContext.getString(R.string.signer_pin_history_unreadable_list),
                data.get(0).getString("target_label"));
    }

    @Test
    public void restoringOnlyAddsMissingPinsAndRecordsEachOne() throws Exception {
        mStore.put(PKG, new SignerPin(Collections.singletonList(SIGNER_A), 1));
        Map<String, SignerPin> snapshot = new LinkedHashMap<>();
        snapshot.put(PKG, new SignerPin(Collections.singletonList(SIGNER_B), 5));
        snapshot.put(OTHER, new SignerPin(Collections.singletonList(SIGNER_B), 6));

        List<String> added = runOnBackground(() -> SignerPinEditor.restore(mContext, mStore, snapshot));

        assertEquals(Collections.singletonList(OTHER), added);
        assertTrue(mStore.get(PKG).isSameSet(Collections.singletonList(SIGNER_A)));
        assertEquals(6, mStore.get(OTHER).pinnedAt);
        List<JSONObject> data = data(rows());
        assertEquals(1, data.size());
        assertEquals(OTHER, data.get(0).getString("package_name"));
        assertEquals(SingleAppActionHistoryItem.ACTION_SIGNER_PIN, data.get(0).getString("action"));
        assertEquals(mContext.getString(R.string.signer_pin_history_restored), data.get(0).getString("detail"));
    }

    private static void assertOneFailure(@NonNull String action, @NonNull String packageName) throws Exception {
        List<OpHistory> rows = rows();
        assertEquals(1, rows.size());
        assertEquals(OpHistoryManager.STATUS_FAILURE, rows.get(0).status);
        JSONObject data = new JSONObject(rows.get(0).serializedData);
        assertEquals(action, data.getString("action"));
        assertEquals(packageName, data.getString("package_name"));
    }

    @NonNull
    private static SignerPin pin(@NonNull String digest) {
        return new SignerPin(Collections.singletonList(digest), 1);
    }

    @NonNull
    private static List<OpHistory> rows() throws Exception {
        return runOnBackground(OpHistoryManager::getAllHistoryItems);
    }

    @NonNull
    private static List<JSONObject> data(@NonNull List<OpHistory> rows) throws Exception {
        List<JSONObject> data = new ArrayList<>();
        for (OpHistory row : rows) {
            data.add(new JSONObject(row.serializedData));
        }
        return data;
    }

    private static <T> T runOnBackground(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        new Thread(task).start();
        return task.get(20, TimeUnit.SECONDS);
    }

    /** Runs on a background thread and throws what the callable threw, not the wrapper. */
    private static <T> T runOnBackgroundRethrowing(Callable<T> callable) throws Exception {
        try {
            return runOnBackground(callable);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    @NonNull
    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
