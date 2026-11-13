// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

@RunWith(RobolectricTestRunner.class)
public class LanSyncRowFilterTest {
    private static final int CONTAINS = AdvancedSearchView.SEARCH_TYPE_CONTAINS;

    private final List<LanSyncViewModel.AppRow> mRows = Arrays.asList(
            row(app("org.mozilla.firefox", "Firefox", 2015, "128.0.3"), app("org.mozilla.firefox", "Firefox", 2014, "127.0")),
            row(app("org.mozilla.focus", "Firefox Focus", 330, "13.1"), null),
            row(app("ademar.textlauncher", "Text Launcher", 7, "2.4"), null),
            row(app("com.termux", "Termux", 118, "0.118.1"), app("com.termux", "Termux", 118, "0.118.1")));

    @Test
    public void noSearchShowsEverythingInOrder() {
        assertEquals(names(mRows), names(LanSyncRowFilter.matching(mRows, null, CONTAINS)));
        assertEquals(names(mRows), names(LanSyncRowFilter.matching(mRows, "", CONTAINS)));
        assertTrue(LanSyncRowFilter.matching(null, "fire", CONTAINS).isEmpty());
    }

    @Test
    public void theNameAndThePackageMatchInAnyCase() {
        assertEquals(Arrays.asList("org.mozilla.firefox", "org.mozilla.focus"),
                names(LanSyncRowFilter.matching(mRows, "FIREFOX", CONTAINS)));
        assertEquals(Collections.singletonList("ademar.textlauncher"),
                names(LanSyncRowFilter.matching(mRows, "ademar", CONTAINS)));
        assertEquals(Collections.singletonList("com.termux"),
                names(LanSyncRowFilter.matching(mRows, "com.", AdvancedSearchView.SEARCH_TYPE_PREFIX)));
    }

    @Test
    public void theVersionNameOrCodeMatchesOnEitherDevice() {
        assertEquals("The other device's version", Collections.singletonList("org.mozilla.firefox"),
                names(LanSyncRowFilter.matching(mRows, "128.0", CONTAINS)));
        assertEquals("The version installed here", Collections.singletonList("org.mozilla.firefox"),
                names(LanSyncRowFilter.matching(mRows, "127.0", CONTAINS)));
        assertEquals("A version code", Collections.singletonList("org.mozilla.focus"),
                names(LanSyncRowFilter.matching(mRows, "330", CONTAINS)));
    }

    @Test
    public void whoSignedItMatchesOnEitherDevice() {
        List<LanSyncViewModel.AppRow> rows = Arrays.asList(
                row(signed("com.whatsapp", "WhatsApp", "CN=Brian Acton,OU=Engineering,O=WhatsApp Inc."), null),
                row(signed("org.example.fork", "Fork", "CN=Jane Doe"),
                        signed("org.example.fork", "Fork", "CN=Upstream Release,O=Example Apps")),
                row(app("com.termux", "Termux", 118, "0.118.1"), null));

        assertEquals("The publisher on the other device", Collections.singletonList("com.whatsapp"),
                names(LanSyncRowFilter.matching(rows, "whatsapp inc", CONTAINS)));
        assertEquals("The publisher of the copy installed here", Collections.singletonList("org.example.fork"),
                names(LanSyncRowFilter.matching(rows, "Example Apps", CONTAINS)));
        assertEquals(Collections.singletonList("org.example.fork"),
                names(LanSyncRowFilter.matching(rows, "O=Example", AdvancedSearchView.SEARCH_TYPE_REGEX)));
        assertTrue("An app sent without subjects still matches by name",
                LanSyncRowFilter.matching(rows, "termux", CONTAINS).size() == 1);
    }

    @Test
    public void aRegexMatchesAndABrokenOneMatchesNothing() {
        assertEquals(Arrays.asList("org.mozilla.firefox", "org.mozilla.focus"),
                names(LanSyncRowFilter.matching(mRows, "^org\\.mozilla\\.f", AdvancedSearchView.SEARCH_TYPE_REGEX)));
        assertEquals("A regex keeps its case", Collections.<String>emptyList(),
                names(LanSyncRowFilter.matching(mRows, "^TERMUX$", AdvancedSearchView.SEARCH_TYPE_REGEX)));
        assertTrue(LanSyncRowFilter.matching(mRows, "([", AdvancedSearchView.SEARCH_TYPE_REGEX).isEmpty());
    }

    @NonNull
    private static SyncApp app(@NonNull String packageName, @NonNull String label, long versionCode,
                               @NonNull String versionName) {
        return new SyncApp(packageName, label, versionCode, versionName, false, Collections.singletonList(SIGNER_A),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
    }

    @NonNull
    private static SyncApp signed(@NonNull String packageName, @NonNull String label, @NonNull String subject) {
        return new SyncApp(packageName, label, 1, "1", false, Collections.singletonList(SIGNER_A),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)),
                Collections.singletonList(subject));
    }

    @NonNull
    private static LanSyncViewModel.AppRow row(@NonNull SyncApp remote, @Nullable SyncApp local) {
        return new LanSyncViewModel.AppRow(remote, local);
    }

    @NonNull
    private static List<String> names(@NonNull List<LanSyncViewModel.AppRow> rows) {
        List<String> names = new ArrayList<>(rows.size());
        for (LanSyncViewModel.AppRow row : rows) {
            names.add(row.remote.packageName);
        }
        return names;
    }
}
