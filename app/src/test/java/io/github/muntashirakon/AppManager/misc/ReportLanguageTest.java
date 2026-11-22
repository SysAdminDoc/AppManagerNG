// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.test.CachedAppContext;

/**
 * Issue 31 item 7: with the app in Russian, the crash report said {@code Inferred Mode: Без-ROOT}. Reports
 * go to the maintainer, so they stay English whatever language the app shows.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "ru")
public class ReportLanguageTest {
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    private final Context mContext = RuntimeEnvironment.getApplication();

    @Before
    @After
    public void forgetTheCachedAppContext() {
        // The app context cached in ContextUtils must be this test's Russian one, and must not outlive it.
        CachedAppContext.forget();
    }

    @Test
    public void theAppReallyIsInRussian() {
        // Otherwise the checks below would pass for nothing.
        assertNotEquals("No root", mContext.getString(R.string.no_root));
    }

    @Test
    public void crashReportDeviceInfoNamesTheModeInEnglish() {
        String report = new DeviceInfo(mContext).toString();

        assertTrue(report, report.contains("Inferred Mode: No root"));
        assertNoCyrillic(report);
    }

    @Test
    public void supportBundleNamesTheModeInEnglish() {
        SupportInfoBundle.SectionOptions sections = new SupportInfoBundle.SectionOptions();
        sections.includeDevice = false;
        sections.includeFeatureFlags = false;
        sections.includeCrashSink = false;
        sections.includeLogcat = false;

        String text = SupportInfoBundle.buildText(mContext, "2026-11-21T00:00:00Z", null, sections);

        assertTrue(text, text.contains("Inferred mode: No root"));
        assertNoCyrillic(text);
    }

    @Test
    public void diagnosticZipDeviceInfoNamesTheModeInEnglish() {
        String text = DiagnosticUtils.formatDeviceInfoForReport(new DeviceInfo(mContext).toString());

        assertTrue(text, text.contains("Inferred Mode: No root"));
        assertNoCyrillic(text);
    }

    @Test
    public void englishContextLeavesTheAppLanguageAlone() {
        Context english = ReportLocale.english(mContext);

        assertEquals("No root", english.getString(R.string.no_root));
        assertNotEquals("No root", mContext.getString(R.string.no_root));
    }

    private static void assertNoCyrillic(@NonNull String text) {
        assertFalse(text, CYRILLIC.matcher(text).find());
    }
}
