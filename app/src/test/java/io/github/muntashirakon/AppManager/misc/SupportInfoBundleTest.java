// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class SupportInfoBundleTest {
    @Test
    public void buildShareIntentPinsTextStreamGrantAndSubject() {
        Uri uri = Uri.parse("content://io.github.sysadmindoc.AppManagerNG.filecache/support-info/report.txt");

        Intent intent = SupportInfoBundle.buildShareIntent("Support info", uri);

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals("Support info", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void scrubForPublicIssue_masksPackagePathUriEmailAndUidData() {
        String scrubbed = SupportInfoBundle.scrubForPublicIssue(
                "uid=10345 appUid=2000 userId=10 u0_a123 com.example.secret/.Main "
                        + "content://com.android.providers.downloads.documents/document/123 "
                        + "file:///sdcard/Download/private.apk /storage/emulated/0/DCIM/private.jpg "
                        + "person@example.com 123456");

        assertTrue(scrubbed.contains("uid=<redacted>"));
        assertTrue(scrubbed.contains("appUid=<redacted>"));
        assertTrue(scrubbed.contains("userId=<redacted>"));
        assertTrue(scrubbed.contains("u<redacted>"));
        assertTrue(scrubbed.contains("<package>"));
        assertTrue(scrubbed.contains("<email>"));
        assertTrue(scrubbed.contains("<id>"));
        assertFalse(scrubbed.contains("10345"));
        assertFalse(scrubbed.contains("com.example.secret"));
        assertFalse(scrubbed.contains("private.apk"));
        assertFalse(scrubbed.contains("private.jpg"));
        assertFalse(scrubbed.contains("person@example.com"));
    }

    /**
     * Fork issue #12 arrived with every frame reduced to a placeholder, so the report named
     * neither the exception nor a single line of code. This is the raw form the device produced
     * before the scrubber ran: a real inflation failure inside the code editor.
     */
    @Test
    public void scrubForPublicIssue_keepsStackFrameClassNames() {
        String rawTrace = "android.view.InflateException: Binary XML file line #51 in "
                + "io.github.sysadmindoc.AppManagerNG:layout/fragment_code_editor: "
                + "Error inflating class io.github.muntashirakon.AppManager.editor.CodeEditorWidget\n"
                + " Caused by: java.lang.NullPointerException: Attempt to invoke virtual method "
                + "'j$.time.ZoneId java.util.TimeZone.toZoneId()' on a null object reference\n"
                + "   at j$.time.ZoneId.systemDefault(ZoneId.java:12345)\n"
                + "   at io.github.rosemoe.sora.widget.CodeEditor.<init>(CodeEditor.java:51)\n"
                + "   at io.github.muntashirakon.AppManager.editor.CodeEditorWidget.<init>(CodeEditorWidget.java:29)\n"
                + "   at android.view.LayoutInflater.createView(LayoutInflater.java:858)\n"
                + "   at androidx.appcompat.app.AppCompatViewInflater.createView(AppCompatViewInflater.java:193)\n"
                + "   at com.google.android.material.internal.ViewUtils.doOnApplyWindowInsets(ViewUtils.java:107)\n"
                + "   at dalvik.system.VMStack.getThreadStackTrace(Native Method)\n"
                + "   at com.acme.injector.Hook.before(Hook.java:7)\n"
                + " reporter@example.com";

        String scrubbed = SupportInfoBundle.scrubForPublicIssue(rawTrace);

        // The exception and every frame that belongs to the platform, the language runtime, or
        // this application must survive, or the report cannot be acted on.
        assertTrue(scrubbed.contains("android.view.InflateException"));
        assertTrue(scrubbed.contains("java.lang.NullPointerException"));
        assertTrue(scrubbed.contains("j$.time.ZoneId"));
        assertTrue(scrubbed.contains("java.util.TimeZone"));
        assertTrue(scrubbed.contains("android.view.LayoutInflater"));
        assertTrue(scrubbed.contains("androidx.appcompat.app.AppCompatViewInflater"));
        assertTrue(scrubbed.contains("com.google.android.material.internal.ViewUtils"));
        assertTrue(scrubbed.contains("dalvik.system.VMStack"));
        assertTrue(scrubbed.contains("io.github.muntashirakon.AppManager.editor.CodeEditorWidget"));
        assertTrue(scrubbed.contains("io.github.sysadmindoc.AppManagerNG:layout/fragment_code_editor"));
        // A line number is not an identifier. The source file name beside it is still masked,
        // which is fine: the class, the method and the line all survive.
        assertTrue(scrubbed.contains(":12345"));

        // Everything that could name something the user installed is still masked.
        assertFalse(scrubbed.contains("com.acme.injector"));
        assertFalse(scrubbed.contains("io.github.rosemoe"));
        assertFalse(scrubbed.contains("reporter@example.com"));
        assertTrue(scrubbed.contains("<package>"));
        assertTrue(scrubbed.contains("<email>"));
    }

    @Test
    public void buildFileName_sanitizesDeviceNameAndUsesUtcTimestamp() {
        String fileName = SupportInfoBundle.buildFileName("Pixel 9/Pro", new Date(0));

        assertEquals("support-info-Pixel_9_Pro-19700101-000000.txt", fileName);
    }

    @Test
    public void formatPreambleForPublicIssueScrubsCallerReport() {
        String preamble = SupportInfoBundle.formatPreambleForPublicIssue(
                "Mode Doctor probe\ncom.example.secret /sdcard/private uid=10345 person@example.com");

        assertTrue(preamble.startsWith("Mode Doctor probe\n"));
        assertTrue(preamble.endsWith("\n\n"));
        assertTrue(preamble.contains("<package>"));
        assertTrue(preamble.contains("<path>"));
        assertTrue(preamble.contains("<email>"));
        assertTrue(preamble.contains("uid=<redacted>"));
        assertFalse(preamble.contains("com.example.secret"));
        assertFalse(preamble.contains("/sdcard/private"));
        assertFalse(preamble.contains("person@example.com"));
        assertFalse(preamble.contains("uid=10345"));
    }

    @Test
    public void formatBundleTextForPublicIssueSanitizesStandaloneLines() {
        assertEquals("'=cmd payload\nplain line",
                SupportInfoBundle.formatBundleTextForPublicIssue("=cmd\tpayload\nplain\rline"));
    }

    @Test
    public void excludedSectionsAreAbsentFromOutput() {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        SupportInfoBundle.SectionOptions options = new SupportInfoBundle.SectionOptions();
        options.includeDevice = false;
        options.includePrivilegeState = false;
        options.includeFeatureFlags = true;
        options.includeCrashSink = false;
        options.includeLogcat = false;

        String text = SupportInfoBundle.buildText(ctx, "2026-06-19T00:00:00Z", null, options);

        assertTrue("Header must always appear", text.contains("AppManagerNG support info"));
        assertFalse("Device section must be excluded", text.contains("Device\n------"));
        assertFalse("Privilege section must be excluded", text.contains("Privilege state"));
        assertTrue("Feature flags section must be included", text.contains("Feature flags"));
        assertFalse("Crash sink section must be excluded", text.contains("Local crash sink"));
        assertFalse("Logcat section must be excluded", text.contains("Scrubbed logcat tail"));
    }

    @Test
    public void allSectionsEnabledIncludesEverything() {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        SupportInfoBundle.SectionOptions options = new SupportInfoBundle.SectionOptions();

        String text = SupportInfoBundle.buildText(ctx, "2026-06-19T00:00:00Z", "test logcat line", options);

        assertTrue(text.contains("Device\n------"));
        assertTrue(text.contains("Privilege state"));
        assertTrue(text.contains("Feature flags"));
        assertTrue(text.contains("Local crash sink"));
        assertTrue(text.contains("Scrubbed logcat tail"));
        assertTrue(text.contains("test logcat line"));
    }
}
