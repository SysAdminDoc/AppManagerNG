// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.settings.Ops;

/** Issue 35: Settings > About copies the bug form's environment fields, filled in. */
@RunWith(RobolectricTestRunner.class)
public class BugReportTemplateTest {
    @Test
    public void everyFieldOfTheFormIsFilledInItsOrder() {
        BugReportTemplate.Inputs in = new BugReportTemplate.Inputs();
        in.versionName = "0.8.0";
        in.versionCode = 34;
        in.flavor = "floss";
        in.buildType = "release";
        in.activeMode = BugReportTemplate.ActiveMode.ADB;
        in.activeModeLabel = "ADB";
        in.configuredMode = Ops.MODE_AUTO;
        in.configuredModeLabel = "Auto";
        in.privilegeProvider = "Wireless debugging";
        in.bootstrapSignature = "LocalServer bootstrap ok: Google/oriole/oriole (sdk=36)";
        in.installerPackage = "dev.imranr.obtainium";
        in.selfPackage = "io.github.sysadmindoc.AppManagerNG";
        in.manufacturer = "Google";
        in.model = "Pixel 6";
        in.androidRelease = "16";
        in.apiLevel = 36;
        in.buildDisplay = "BP2A.250605.031";
        in.roms = Collections.singletonList("LineageOS 23.0");
        in.securityPatch = "2026-10-05";
        in.abis = new String[]{"arm64-v8a", "armeabi-v7a", "armeabi"};

        String text = BugReportTemplate.format(in);

        assertEquals("- AppManagerNG version (Settings → About): 0.8.0 (34), floss, release\n"
                + "- Active mode: ADB (setting: Auto)\n"
                + "- Privilege provider: Wireless debugging\n"
                + "- LocalServer bootstrap signature: LocalServer bootstrap ok: Google/oriole/oriole (sdk=36)\n"
                + "- Install source: dev.imranr.obtainium (Obtainium)\n"
                + "- Manufacturer + model: Google Pixel 6\n"
                + "- Android version + API level: 16 (API 36)\n"
                + "- Build / ROM: BP2A.250605.031 (LineageOS 23.0)\n"
                + "- Security patch level (YYYY-MM-DD): 2026-10-05\n"
                + "- Architecture: arm64-v8a (armeabi-v7a, armeabi)\n", text);
    }

    @Test
    public void missingValuesFallBackToUnknownOrNone() {
        String text = BugReportTemplate.format(new BugReportTemplate.Inputs());

        assertTrue(text, text.contains("- AppManagerNG version (Settings → About): unknown\n"));
        assertTrue(text, text.contains("- Active mode: unknown\n"));
        assertTrue(text, text.contains("- Privilege provider: None\n"));
        assertTrue(text, text.contains("- LocalServer bootstrap signature: None\n"));
        assertTrue(text, text.contains("- Install source: unknown\n"));
        assertTrue(text, text.contains("- Manufacturer + model: unknown\n"));
        assertTrue(text, text.contains("- Android version + API level: unknown\n"));
        assertTrue(text, text.contains("- Build / ROM: unknown\n"));
        assertTrue(text, text.contains("- Security patch level (YYYY-MM-DD): unknown\n"));
        assertTrue(text, text.contains("- Architecture: unknown\n"));
        assertEquals(10, text.split("\n").length);
    }

    @Test
    public void theSettingIsNamedOnlyWhenItDiffersFromTheModeInUse() {
        assertEquals("Root", activeMode(BugReportTemplate.ActiveMode.ROOT, "Root", Ops.MODE_ROOT, "Root"));
        assertEquals("ADB", activeMode(BugReportTemplate.ActiveMode.ADB, "ADB", Ops.MODE_ADB_WIFI,
                "Wireless debugging"));
        assertEquals("Shizuku (root)", activeMode(BugReportTemplate.ActiveMode.SHIZUKU, "Shizuku (root)",
                Ops.MODE_SHIZUKU, "Shizuku"));
        assertEquals("No root", activeMode(BugReportTemplate.ActiveMode.NO_ROOT, "No root", Ops.MODE_NO_ROOT,
                "No root"));
        // Auto always says so, and a mode that fell back says what was asked for.
        assertEquals("Root (setting: Auto)", activeMode(BugReportTemplate.ActiveMode.ROOT, "Root", Ops.MODE_AUTO,
                "Auto"));
        assertEquals("No root (setting: Root)", activeMode(BugReportTemplate.ActiveMode.NO_ROOT, "No root",
                Ops.MODE_ROOT, "Root"));
    }

    @Test
    public void providerFollowsTheModeInUse() {
        assertEquals("Magisk", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.ROOT,
                Ops.MODE_ROOT, "Magisk", false, null, false));
        assertEquals("su binary", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.ROOT,
                Ops.MODE_ROOT, null, false, null, false));
        assertEquals("ADB root (KernelSU)", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.ROOT,
                Ops.MODE_ADB_WIFI, "KernelSU", false, null, true));
        assertEquals("Shizuku 13.6.0", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.SHIZUKU,
                Ops.MODE_SHIZUKU, null, false, "13.6.0", false));
        assertEquals("Shizuku (version unknown)", BugReportTemplate.describeProvider(
                BugReportTemplate.ActiveMode.SHIZUKU, Ops.MODE_SHIZUKU, null, false, null, false));
        assertEquals("Sui api 13 (hidden)", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.SHIZUKU,
                Ops.MODE_SHIZUKU, "Magisk", true, "api 13 (hidden)", false));
        assertEquals("Wireless debugging", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.ADB,
                Ops.MODE_ADB_WIFI, null, false, null, false));
        assertTrue(BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.ADB, Ops.MODE_ADB_OVER_TCP,
                null, false, null, false).startsWith("ADB over TCP"));
        assertEquals("ADB", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.ADB, Ops.MODE_AUTO,
                null, false, null, false));
        assertEquals("None", BugReportTemplate.describeProvider(BugReportTemplate.ActiveMode.NO_ROOT,
                Ops.MODE_AUTO, "Magisk", false, "13.6.0", false));
    }

    @Test
    public void installSourceNamesKnownInstallersAndHidesOtherApps() {
        String self = "io.github.sysadmindoc.AppManagerNG";
        assertEquals("org.fdroid.fdroid (F-Droid)", BugReportTemplate.formatInstallSource("org.fdroid.fdroid", self));
        assertEquals("in.sunilpaulmathew.izzyondroid (IzzyOnDroid)",
                BugReportTemplate.formatInstallSource("in.sunilpaulmathew.izzyondroid", self));
        assertEquals("com.android.shell (adb)", BugReportTemplate.formatInstallSource("com.android.shell", self));
        assertEquals(self + " (AppManagerNG)", BugReportTemplate.formatInstallSource(self, self));
        assertTrue(BugReportTemplate.formatInstallSource("com.google.android.packageinstaller", self)
                .contains("GitHub"));
        assertEquals("unknown", BugReportTemplate.formatInstallSource(null, self));
        String other = BugReportTemplate.formatInstallSource("com.example.private.installer", self);
        assertFalse(other, other.contains("com.example.private.installer"));
    }

    @Test
    public void bootstrapSignatureIsScrubbedAndKeptOnOneLine() {
        String line = BugReportTemplate.formatBootstrapSignature(
                "LocalServer bootstrap failed: x\nat com.example.secret.Thing uid=10234");

        assertFalse(line, line.contains("\n"));
        assertFalse(line, line.contains("com.example.secret"));
        assertFalse(line, line.contains("10234"));
        assertEquals("None", BugReportTemplate.formatBootstrapSignature("  "));
    }

    @Test
    public void romsAreReadFromTheirProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("ro.build.version.oneui", "60100");
        props.put("ro.mi.os.version.name", "OS2.0");
        props.put("ro.miui.ui.version.name", "V140");
        props.put("ro.build.version.oplusrom", "V15.0");
        props.put("ro.lineage.version", "22.1");

        List<String> roms = BugReportTemplate.detectRoms(key -> value(props, key));

        assertEquals(Arrays.asList("HyperOS OS2.0", "One UI 6.1", "ColorOS V15.0", "LineageOS 22.1"), roms);
        props.remove("ro.mi.os.version.name");
        assertTrue(BugReportTemplate.detectRoms(key -> value(props, key)).contains("MIUI V140"));
        assertTrue(BugReportTemplate.detectRoms(key -> "").isEmpty());
        assertEquals("UQ1A (One UI 7.0)",
                BugReportTemplate.formatBuild("UQ1A", Collections.singletonList("One UI 7.0")));
    }

    @Test
    public void architectureListsTheRestInParentheses() {
        assertEquals("x86_64", BugReportTemplate.formatAbis(new String[]{"x86_64"}));
        assertEquals("x86_64 (x86, arm64-v8a)", BugReportTemplate.formatAbis(new String[]{"x86_64", "x86", "arm64-v8a"}));
        assertEquals("unknown", BugReportTemplate.formatAbis(null));
    }

    @NonNull
    private static String activeMode(@NonNull BugReportTemplate.ActiveMode mode, @NonNull String label,
                                     @NonNull String configured, @NonNull String configuredLabel) {
        BugReportTemplate.Inputs in = new BugReportTemplate.Inputs();
        in.activeMode = mode;
        in.activeModeLabel = label;
        in.configuredMode = configured;
        in.configuredModeLabel = configuredLabel;
        return BugReportTemplate.formatActiveMode(in);
    }

    @NonNull
    private static String value(@NonNull Map<String, String> props, @NonNull String key) {
        String value = props.get(key);
        return value != null ? value : "";
    }
}
