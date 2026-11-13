// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.accessibility.topapp.TopAppTransition.Kind;

// Robolectric for the main looper that LiveData posts to.
@RunWith(RobolectricTestRunner.class)
public class TopAppHistoryTest {
    @Rule
    public final TemporaryFolder mFolder = new TemporaryFolder();

    private File mFile;
    private TopAppHistory mHistory;

    @Before
    public void setUp() {
        mFile = new File(mFolder.getRoot(), TopAppHistory.FILE_NAME);
        mHistory = new TopAppHistory(mFile, Runnable::run);
    }

    @NonNull
    private static TopAppTransition app(long millis, @NonNull String packageName) {
        return new TopAppTransition(millis, Kind.APP, packageName, null);
    }

    @NonNull
    private List<TopAppTransition> read() {
        AtomicReference<List<TopAppTransition>> read = new AtomicReference<>();
        mHistory.read(read::set);
        return read.get();
    }

    @Test
    public void nothingSavedReadsAsEmpty() {
        assertTrue(read().isEmpty());
    }

    @Test
    public void savedChangesReadBackNewestFirst() {
        mHistory.record(Collections.singletonList(app(1, "com.example.one")));
        mHistory.record(Arrays.asList(app(2, "com.example.two"),
                new TopAppTransition(2, Kind.OVERLAY_SHOWN, "com.example.ad", "com.example.two")));

        assertEquals(Arrays.asList(new TopAppTransition(2, Kind.OVERLAY_SHOWN, "com.example.ad", "com.example.two"),
                app(2, "com.example.two"), app(1, "com.example.one")), read());
    }

    @Test
    public void aNewInstanceReadsWhatAnEarlierOneSaved() {
        mHistory.record(Collections.singletonList(app(1, "com.example.one")));

        mHistory = new TopAppHistory(mFile, Runnable::run);

        assertEquals(Collections.singletonList(app(1, "com.example.one")), read());
    }

    @Test
    public void aDamagedLineCostsOnlyThatLine() throws IOException {
        Files.write(mFile.toPath(), ("1\tAPP\tcom.example.one\t\nbroken\n2\tAPP\tcom.example.two\tcom.example.one\n")
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(Arrays.asList(new TopAppTransition(2, Kind.APP, "com.example.two", "com.example.one"),
                app(1, "com.example.one")), read());
    }

    @Test
    public void theOldestAreDroppedPastTheLimit() {
        int total = TopAppHistory.MAX_ENTRIES + TopAppHistory.TRIM_SLACK + 1;
        for (int i = 0; i < total; ++i) {
            mHistory.record(Collections.singletonList(app(i, "com.example.app" + i)));
        }

        List<TopAppTransition> saved = read();
        assertEquals(TopAppHistory.MAX_ENTRIES, saved.size());
        assertEquals(app(total - 1, "com.example.app" + (total - 1)), saved.get(0));
        assertEquals(app(total - TopAppHistory.MAX_ENTRIES, "com.example.app" + (total - TopAppHistory.MAX_ENTRIES)),
                saved.get(saved.size() - 1));
    }

    @Test
    public void clearHandsBackWhatItRemovedAndUndoPutsItBack() {
        mHistory.record(Arrays.asList(app(1, "com.example.one"), app(2, "com.example.two")));
        AtomicReference<List<TopAppTransition>> removed = new AtomicReference<>();

        mHistory.clear(removed::set);

        assertNotNull(removed.get());
        assertEquals(Arrays.asList(app(2, "com.example.two"), app(1, "com.example.one")), removed.get());
        assertTrue(read().isEmpty());

        // Something new arrives before the undo, and both are kept in time order.
        mHistory.record(Collections.singletonList(app(3, "com.example.three")));
        mHistory.restore(removed.get());

        assertEquals(Arrays.asList(app(3, "com.example.three"), app(2, "com.example.two"),
                app(1, "com.example.one")), read());
    }

    @Test
    public void appendingAfterAnUndoKeepsCounting() {
        List<TopAppTransition> many = new ArrayList<>();
        for (int i = 0; i < TopAppHistory.MAX_ENTRIES; ++i) {
            many.add(app(i, "com.example.app" + i));
        }
        mHistory.record(many);
        AtomicReference<List<TopAppTransition>> removed = new AtomicReference<>();
        mHistory.clear(removed::set);
        mHistory.restore(removed.get());

        for (int i = 0; i <= TopAppHistory.TRIM_SLACK; ++i) {
            mHistory.record(Collections.singletonList(app(TopAppHistory.MAX_ENTRIES + i, "com.example.new" + i)));
        }

        assertEquals(TopAppHistory.MAX_ENTRIES, read().size());
    }

    @Test
    public void everyChangeIsAnnounced() {
        mHistory.record(Collections.singletonList(app(1, "com.example.one")));
        ShadowLooper.idleMainLooper();
        Long first = mHistory.getChanges().getValue();

        mHistory.clear(removed -> {
        });
        ShadowLooper.idleMainLooper();

        assertNotNull(first);
        assertTrue(mHistory.getChanges().getValue() > first);
    }
}
