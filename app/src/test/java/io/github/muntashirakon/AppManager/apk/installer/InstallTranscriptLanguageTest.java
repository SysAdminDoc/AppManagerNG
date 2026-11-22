// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

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

/** Issue 31 item 7: the install transcript is pasted into issues, so its explanation stays English. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "ru")
public class InstallTranscriptLanguageTest {
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    private final Context mContext = RuntimeEnvironment.getApplication();

    @Before
    @After
    public void forgetTheCachedAppContext() {
        // The app context cached in ContextUtils must be this test's Russian one, and must not outlive it.
        CachedAppContext.forget();
    }

    @Test
    public void explanationAndHintAreEnglishWithTheAppInRussian() {
        // The app itself shows Russian, so the transcript has to choose English on purpose.
        assertNotEquals("There is not enough storage space to install this app.",
                mContext.getString(R.string.installer_error_storage));

        String text = PackageInstallerActivity.newInstallTranscript(mContext, "com.example.app",
                PackageInstallerCompat.STATUS_FAILURE_STORAGE, null, null).toShareableText();

        assertTrue(text, text.contains("Status explanation: There is not enough storage space to install this app."));
        assertTrue(text, text.contains("Recovery hint: Free device storage and retry the install."));
        assertFalse(text, CYRILLIC.matcher(text).find());
    }
}
