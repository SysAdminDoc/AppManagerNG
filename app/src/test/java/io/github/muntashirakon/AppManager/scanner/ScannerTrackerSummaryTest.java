// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory;

public class ScannerTrackerSummaryTest {
    @Test
    public void summarizeGroupsSignaturesByOrganization() {
        SignatureInfo admob = new SignatureInfo("com.google.ads.", "Google AdMob");
        admob.setCount(3);
        SignatureInfo admobLite = new SignatureInfo("com.google.android.gms.ads.", "Google AdMob");
        admobLite.setCount(4);
        SignatureInfo crashlytics = new SignatureInfo("com.crashlytics.", "Crashlytics");
        crashlytics.setCount(2);

        List<ScannerTrackerSummary.Organization> summaries = ScannerTrackerSummary.summarize(
                Arrays.asList(crashlytics, admob, admobLite));

        assertEquals(2, summaries.size());
        assertEquals("Crashlytics", summaries.get(0).label);
        assertEquals(TrackerCategory.CRASH, summaries.get(0).category);
        assertEquals(1, summaries.get(0).getSignatureCount());
        assertEquals(2, summaries.get(0).classCount);
        assertEquals("Google AdMob", summaries.get(1).label);
        assertEquals(TrackerCategory.AD, summaries.get(1).category);
        assertEquals(2, summaries.get(1).getSignatureCount());
        assertEquals(7, summaries.get(1).classCount);
    }

    @Test
    public void summarizeNormalizesSecondDegreePrefix() {
        SignatureInfo signatureInfo = new SignatureInfo("com.example.", "²Example Analytics");
        signatureInfo.setCount(1);

        List<ScannerTrackerSummary.Organization> summaries = ScannerTrackerSummary.summarize(
                Arrays.asList(signatureInfo));

        assertEquals("²Example Analytics", summaries.get(0).label);
        assertEquals("Example Analytics", summaries.get(0).normalizedLabel);
        assertEquals(TrackerCategory.ANALYTICS, summaries.get(0).category);
    }
}
