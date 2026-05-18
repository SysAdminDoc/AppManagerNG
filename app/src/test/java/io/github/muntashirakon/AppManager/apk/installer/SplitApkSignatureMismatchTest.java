// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SplitApkSignatureMismatchTest {
    @Test
    public void matchingSplitSignerSetProducesNoRows() {
        List<SplitApkSignatureMismatch.Mismatch> rows = SplitApkSignatureMismatch.findMismatches(Arrays.asList(
                entry("base", "base.apk", true, true, "aa", "bb"),
                entry("config", "config.arm64_v8a", false, false, "bb", "aa")
        ));

        assertTrue(rows.isEmpty());
    }

    @Test
    public void signerCountMismatchIsReported() {
        List<SplitApkSignatureMismatch.Mismatch> rows = SplitApkSignatureMismatch.findMismatches(Arrays.asList(
                entry("base", "base.apk", true, true, "aa", "bb"),
                entry("config", "config.en", false, false, "aa")
        ));

        assertEquals(1, rows.size());
        assertEquals(SplitApkSignatureMismatch.REASON_SIGNER_COUNT_DIFFERS, rows.get(0).reason);
        assertTrue(rows.get(0).canRemove());
    }

    @Test
    public void unreadableRequiredSplitIsReportedButNotRemovable() {
        List<SplitApkSignatureMismatch.Mismatch> rows = SplitApkSignatureMismatch.findMismatches(Arrays.asList(
                entry("base", "base.apk", true, true, "aa"),
                entry("required", "feature.required", false, true)
        ));

        assertEquals(1, rows.size());
        assertEquals(SplitApkSignatureMismatch.REASON_SPLIT_CERT_UNREADABLE, rows.get(0).reason);
        assertFalse(rows.get(0).canRemove());
    }

    @Test
    public void baseWithoutReadableCertMarksSelectedSplits() {
        List<SplitApkSignatureMismatch.Mismatch> rows = SplitApkSignatureMismatch.findMismatches(Arrays.asList(
                entry("base", "base.apk", true, true),
                entry("config", "config.xxhdpi", false, false, "aa")
        ));

        assertEquals(1, rows.size());
        assertEquals(SplitApkSignatureMismatch.REASON_BASE_CERT_UNREADABLE, rows.get(0).reason);
    }

    private static SplitApkSignatureMismatch.EntryReport entry(String id, String name, boolean base,
                                                              boolean required, String... certs) {
        return new SplitApkSignatureMismatch.EntryReport(id, name, "1.0 (1)",
                certs.length == 0 ? Collections.emptyList() : Arrays.asList(certs), base, required);
    }
}
