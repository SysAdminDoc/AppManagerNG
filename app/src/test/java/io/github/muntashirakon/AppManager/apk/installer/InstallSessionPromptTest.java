// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Issue #22: installs queued in the background reach the SHA-256 prompt one after another, and
 * nothing on it said which app or file it was for, or what the update changed.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallSessionPromptTest {
    private static final String PACKAGE = "com.whatsapp";
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private Context mContext;

    @Before
    public void setUp() {
        mContext = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        Prefs.Installer.setShowSessionChanges(true);
    }

    @After
    public void tearDown() {
        Prefs.Installer.setShowSessionChanges(true);
    }

    @Test
    public void theAppAndFileComeFirstSoQueuedInstallsCanBeToldApart() {
        View root = InstallSessionPrompt.inflate(mContext,
                new InstallSessionDetails("WhatsApp", "WhatsApp-2.24.apk", null), PACKAGE, SHA256);

        assertEquals("WhatsApp", text(root, R.id.app_label));
        assertEquals(PACKAGE, text(root, R.id.package_name));
        assertEquals("File: WhatsApp-2.24.apk", text(root, R.id.apk_name));
        assertTrue(text(root, R.id.sha256).contains(PackageInstallerActivity.formatSha256ForDisplay(SHA256)));
    }

    @Test
    public void withoutALabelThePackageNameStandsInAndNoEmptyFileLineShows() {
        View root = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);

        assertEquals(PACKAGE, text(root, R.id.app_label));
        assertEquals(View.GONE, root.findViewById(R.id.package_name).getVisibility());
        assertEquals(View.GONE, root.findViewById(R.id.apk_name).getVisibility());
    }

    @Test
    public void anUpdateShowsItsVersionAndNamesThatOpenOnATap() {
        View root = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);

        InstallSessionPrompt.showChanges(root, PACKAGE, update());

        assertEquals("Version 1.0 (10) to 1.1 (11)", text(root, R.id.changes_status));
        ViewGroup rows = root.findViewById(R.id.change_rows);
        assertEquals(InstallerManifestSummary.Kind.values().length, rows.getChildCount());
        View activities = rows.getChildAt(InstallerManifestSummary.Kind.ACTIVITIES.ordinal());
        TextView header = activities.findViewById(R.id.header);
        TextView names = activities.findViewById(R.id.names);
        assertEquals("Activities: 1 added, 1 removed", header.getText().toString());
        assertEquals("names start folded away", View.GONE, names.getVisibility());

        header.performClick();

        assertEquals(View.VISIBLE, names.getVisibility());
        assertEquals("Added\n.Settings\nRemoved\n.Old", names.getText().toString());

        header.performClick();

        assertEquals(View.GONE, names.getVisibility());
        View services = rows.getChildAt(InstallerManifestSummary.Kind.SERVICES.ordinal());
        assertEquals("Services: no change", ((TextView) services.findViewById(R.id.header)).getText().toString());
        assertFalse("nothing to open", services.findViewById(R.id.header).hasOnClickListeners());
    }

    @Test
    public void aNewInstallShowsCountsOnly() {
        View root = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);
        PackageInfo apk = packageInfo("2.0", 20, ".Main", ".Settings");

        InstallSessionPrompt.showChanges(root, PACKAGE, InstallerManifestSummary.compare(null, apk));

        assertEquals("New install, version 2.0 (20)", text(root, R.id.changes_status));
        ViewGroup rows = root.findViewById(R.id.change_rows);
        TextView header = rows.getChildAt(InstallerManifestSummary.Kind.ACTIVITIES.ordinal()).findViewById(R.id.header);
        assertEquals("Activities: 2", header.getText().toString());
        assertFalse(header.hasOnClickListeners());
    }

    @Test
    public void anUnreadableApkSaysSo() {
        View root = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);

        InstallSessionPrompt.showChanges(root, PACKAGE, null);

        assertEquals("Couldn't read this APK's manifest.", text(root, R.id.changes_status));
        assertEquals(0, ((ViewGroup) root.findViewById(R.id.change_rows)).getChildCount());
    }

    @Test
    public void theSwitchTurnsTheComparisonOffAndTheNextPromptRemembers() {
        AtomicInteger loads = new AtomicInteger();
        InstallSessionPrompt.ChangesLoader loader = () -> {
            loads.incrementAndGet();
            return update();
        };
        View first = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);

        InstallSessionPrompt.bindChanges(first, PACKAGE, loader, Runnable::run);
        shadowOf(Looper.getMainLooper()).idle();

        MaterialSwitch toggle = first.findViewById(R.id.show_changes);
        assertTrue("on by default", toggle.isChecked());
        assertEquals(1, loads.get());
        assertEquals(View.VISIBLE, first.findViewById(R.id.changes).getVisibility());
        assertEquals(InstallerManifestSummary.Kind.values().length,
                ((ViewGroup) first.findViewById(R.id.change_rows)).getChildCount());

        toggle.setChecked(false);

        assertFalse(Prefs.Installer.showSessionChanges());
        assertEquals(View.GONE, first.findViewById(R.id.changes).getVisibility());

        View second = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);
        InstallSessionPrompt.bindChanges(second, PACKAGE, loader, Runnable::run);
        shadowOf(Looper.getMainLooper()).idle();

        MaterialSwitch secondToggle = second.findViewById(R.id.show_changes);
        assertFalse(secondToggle.isChecked());
        assertEquals("a prompt with the summary off reads nothing", 1, loads.get());
        assertEquals(View.GONE, second.findViewById(R.id.changes).getVisibility());

        secondToggle.setChecked(true);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(Prefs.Installer.showSessionChanges());
        assertEquals(2, loads.get());
        assertEquals("Version 1.0 (10) to 1.1 (11)", text(second, R.id.changes_status));

        secondToggle.setChecked(false);
        secondToggle.setChecked(true);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("turning it on again doesn't read the APK twice", 2, loads.get());
    }

    @Test
    public void withoutABaseApkThereIsNothingToCompare() {
        View root = InstallSessionPrompt.inflate(mContext, null, PACKAGE, SHA256);

        InstallSessionPrompt.bindChanges(root, PACKAGE, null, Runnable::run);

        assertEquals(View.GONE, root.findViewById(R.id.show_changes).getVisibility());
        assertEquals(View.GONE, root.findViewById(R.id.changes).getVisibility());
    }

    @Test
    public void theFileNameComesFromWhereTheApkWasOpened() throws IOException {
        File apk = mTemp.newFile("WhatsApp-2.24.apk");

        assertEquals("WhatsApp-2.24.apk", InstallSessionDetails.apkNameOf(
                ApkSource.getCachedApkSource(Uri.fromFile(apk), "application/vnd.android.package-archive")));
        assertNull(InstallSessionDetails.apkNameOf(null));
    }

    @Test
    public void theBaseApkIsTheOneNamedBaseOrTheOnlyOne() throws IOException {
        File base = mTemp.newFile("base.apk");
        File split = mTemp.newFile("split_config.arm64_v8a.apk");
        File single = mTemp.newFile("app.apk");

        assertEquals(base.getAbsolutePath(), PackageInstallerCompat.baseApkOf(
                new Path[]{Paths.get(split), Paths.get(base)}).getAbsolutePath());
        assertEquals(single.getAbsolutePath(), PackageInstallerCompat.baseApkOf(
                new Path[]{Paths.get(single)}).getAbsolutePath());
        assertNull("two files and neither is base", PackageInstallerCompat.baseApkOf(
                new Path[]{Paths.get(split), Paths.get(single)}));
    }

    @NonNull
    private static InstallerManifestSummary.Changes update() {
        return InstallerManifestSummary.compare(packageInfo("1.0", 10, ".Main", ".Old"),
                packageInfo("1.1", 11, ".Main", ".Settings"));
    }

    @NonNull
    private static PackageInfo packageInfo(String versionName, long versionCode, String... activities) {
        PackageInfo info = new PackageInfo();
        info.packageName = PACKAGE;
        info.versionName = versionName;
        info.setLongVersionCode(versionCode);
        info.activities = new ActivityInfo[activities.length];
        for (int i = 0; i < activities.length; ++i) {
            ActivityInfo activity = new ActivityInfo();
            activity.packageName = PACKAGE;
            activity.name = PACKAGE + activities[i];
            info.activities[i] = activity;
        }
        return info;
    }

    @NonNull
    private static String text(@NonNull View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }
}
