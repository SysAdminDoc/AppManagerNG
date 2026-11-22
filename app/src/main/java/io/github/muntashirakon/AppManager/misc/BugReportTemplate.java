// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.runner.RootManagerInfo;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * The environment fields of the GitHub bug form, filled in and ready to paste (issue #35). The text is English
 * whatever language the app shows, since it's written for the maintainer.
 */
public final class BugReportTemplate {
    @VisibleForTesting
    static final String UNKNOWN = "unknown";
    @VisibleForTesting
    static final String NONE = "None";

    /** What the app is in practice, whatever the setting says. */
    public enum ActiveMode {
        ROOT,
        ADB,
        SHIZUKU,
        NO_ROOT,
        /** A uid other than root, shell or the app's own, such as system. */
        OTHER,
    }

    /** Apps that install this one, by package name. Kept to names that say where the APK came from. */
    private static final Map<String, String> INSTALLERS = new HashMap<>();
    /** The platform's own installer screens: an APK file opened by hand, usually a GitHub download. */
    private static final List<String> SYSTEM_INSTALLERS = Arrays.asList(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller");

    static {
        INSTALLERS.put("dev.imranr.obtainium", "Obtainium");
        INSTALLERS.put("dev.imranr.obtainium.fdroid", "Obtainium");
        INSTALLERS.put("org.fdroid.fdroid", "F-Droid");
        INSTALLERS.put("org.fdroid.basic", "F-Droid Basic");
        INSTALLERS.put("eu.bubu1.fdroidclassic", "F-Droid Classic");
        INSTALLERS.put("com.looker.droidify", "Droid-ify");
        INSTALLERS.put("com.machiav3lli.fdroid", "Neo Store");
        INSTALLERS.put("in.sunilpaulmathew.izzyondroid", "IzzyOnDroid");
        INSTALLERS.put("com.android.shell", "adb");
    }

    /** Everything the form asks for, as plain values. */
    public static final class Inputs {
        @Nullable
        public String versionName;
        public long versionCode;
        @Nullable
        public String flavor;
        @Nullable
        public String buildType;
        @NonNull
        public ActiveMode activeMode = ActiveMode.NO_ROOT;
        /** The mode in use as the app names it in English, e.g. "ADB" or "Shizuku (root)". */
        @Nullable
        public String activeModeLabel;
        /** The mode setting's key, one of the {@code Ops.MODE_*} values. */
        @Nullable
        public String configuredMode;
        /** The mode setting as the app names it in English, e.g. "Auto". */
        @Nullable
        public String configuredModeLabel;
        @Nullable
        public String privilegeProvider;
        @Nullable
        public String bootstrapSignature;
        @Nullable
        public String installerPackage;
        @Nullable
        public String selfPackage;
        @Nullable
        public String manufacturer;
        @Nullable
        public String model;
        @Nullable
        public String androidRelease;
        public int apiLevel;
        @Nullable
        public String buildDisplay;
        @NonNull
        public List<String> roms = Collections.emptyList();
        @Nullable
        public String securityPatch;
        @Nullable
        public String[] abis;
    }

    /** Reads one system property, empty when it isn't set. */
    @VisibleForTesting
    interface PropertyReader {
        @NonNull
        String get(@NonNull String key);
    }

    private BugReportTemplate() {
    }

    /** Collects and formats the fields. Probes the root manager, so never on the main thread. */
    @WorkerThread
    @NonNull
    public static String build(@NonNull Context context) {
        return format(collect(context.getApplicationContext()));
    }

    @WorkerThread
    @NonNull
    static Inputs collect(@NonNull Context context) {
        Context english = ReportLocale.english(context);
        Inputs in = new Inputs();
        in.versionName = BuildConfig.VERSION_NAME;
        in.versionCode = BuildConfig.VERSION_CODE;
        in.flavor = BuildConfig.FLAVOR;
        in.buildType = BuildConfig.BUILD_TYPE;
        in.activeMode = currentMode();
        in.activeModeLabel = Ops.getInferredMode(english).toString();
        in.configuredMode = Ops.getMode();
        in.configuredModeLabel = modeSettingLabel(english, in.configuredMode);
        RootManagerInfo rootManager = RootManagerInfo.detect(context);
        in.privilegeProvider = describeProvider(in.activeMode, in.configuredMode, rootManager.displayName(),
                rootManager.suiPresent, ShizukuBridge.getDisplayVersion(context), Ops.isAdbShellRoot());
        in.bootstrapSignature = LocalServer.getLastBootstrapSignature();
        in.selfPackage = context.getPackageName();
        in.installerPackage = PackageManagerCompat.getInstallerPackageName(context.getPackageName(),
                UserHandleHidden.myUserId());
        in.manufacturer = Build.MANUFACTURER;
        in.model = Build.MODEL;
        in.androidRelease = Build.VERSION.RELEASE;
        in.apiLevel = Build.VERSION.SDK_INT;
        in.buildDisplay = Build.DISPLAY;
        in.roms = detectRoms(key -> SystemProperties.get(key, ""));
        in.securityPatch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Build.VERSION.SECURITY_PATCH : null;
        in.abis = Build.SUPPORTED_ABIS;
        return in;
    }

    @NonNull
    private static ActiveMode currentMode() {
        if (Ops.isShizuku()) {
            return ActiveMode.SHIZUKU;
        }
        int uid = Users.getSelfOrRemoteUid();
        if (uid == Ops.ROOT_UID) {
            return ActiveMode.ROOT;
        }
        if (uid == Ops.SHELL_UID) {
            return ActiveMode.ADB;
        }
        return uid == Process.myUid() ? ActiveMode.NO_ROOT : ActiveMode.OTHER;
    }

    @Nullable
    private static String modeSettingLabel(@NonNull Context english, @Nullable String mode) {
        if (mode == null) {
            return null;
        }
        switch (mode) {
            case Ops.MODE_AUTO:
                return english.getString(R.string.auto);
            case Ops.MODE_ROOT:
                return english.getString(R.string.root);
            case Ops.MODE_SHIZUKU:
                return english.getString(R.string.shizuku);
            case Ops.MODE_ADB_OVER_TCP:
                return english.getString(R.string.adb_over_tcp);
            case Ops.MODE_ADB_WIFI:
                return english.getString(R.string.wireless_debugging);
            case Ops.MODE_NO_ROOT:
                return english.getString(R.string.no_root);
            default:
                return mode;
        }
    }

    /** The bug form's fields as a Markdown list, one line each. */
    @NonNull
    public static String format(@NonNull Inputs in) {
        StringBuilder sb = new StringBuilder();
        line(sb, "AppManagerNG version (Settings → About)", formatVersion(in));
        line(sb, "Active mode", formatActiveMode(in));
        line(sb, "Privilege provider", orDefault(in.privilegeProvider, NONE));
        line(sb, "LocalServer bootstrap signature", formatBootstrapSignature(in.bootstrapSignature));
        line(sb, "Install source", formatInstallSource(in.installerPackage, in.selfPackage));
        line(sb, "Manufacturer + model", formatDevice(in.manufacturer, in.model));
        line(sb, "Android version + API level", formatAndroid(in.androidRelease, in.apiLevel));
        line(sb, "Build / ROM", formatBuild(in.buildDisplay, in.roms));
        line(sb, "Security patch level (YYYY-MM-DD)", orDefault(in.securityPatch, UNKNOWN));
        line(sb, "Architecture", formatAbis(in.abis));
        return sb.toString();
    }

    @NonNull
    private static String formatVersion(@NonNull Inputs in) {
        if (TextUtils.isEmpty(in.versionName)) {
            return UNKNOWN;
        }
        StringBuilder sb = new StringBuilder(in.versionName).append(" (").append(in.versionCode).append(')');
        if (!TextUtils.isEmpty(in.flavor)) {
            sb.append(", ").append(in.flavor);
        }
        if (!TextUtils.isEmpty(in.buildType)) {
            sb.append(", ").append(in.buildType);
        }
        return sb.toString();
    }

    @VisibleForTesting
    @NonNull
    static String formatActiveMode(@NonNull Inputs in) {
        String active = orDefault(in.activeModeLabel, UNKNOWN);
        if (settingMatches(in.configuredMode, in.activeMode) || TextUtils.isEmpty(in.configuredModeLabel)) {
            return active;
        }
        return active + " (setting: " + in.configuredModeLabel + ")";
    }

    /** Whether the setting asks for the mode the app ended up in, so the line needs only one of them. */
    private static boolean settingMatches(@Nullable String configuredMode, @NonNull ActiveMode active) {
        if (configuredMode == null) {
            return true;
        }
        switch (configuredMode) {
            case Ops.MODE_ROOT:
                return active == ActiveMode.ROOT;
            case Ops.MODE_SHIZUKU:
                return active == ActiveMode.SHIZUKU;
            case Ops.MODE_ADB_OVER_TCP:
            case Ops.MODE_ADB_WIFI:
                return active == ActiveMode.ADB;
            case Ops.MODE_NO_ROOT:
                return active == ActiveMode.NO_ROOT;
            case Ops.MODE_AUTO:
            default:
                return false;
        }
    }

    /** Who grants the privilege the active mode uses: the root manager, Shizuku or Sui, or how ADB connects. */
    @VisibleForTesting
    @NonNull
    static String describeProvider(@NonNull ActiveMode active, @Nullable String configuredMode,
                                   @Nullable String rootManager, boolean suiPresent,
                                   @Nullable String shizukuVersion, boolean adbShellRoot) {
        switch (active) {
            case ROOT:
                if (adbShellRoot) {
                    return "ADB root" + (rootManager != null ? " (" + rootManager + ")" : "");
                }
                return rootManager != null ? rootManager : "su binary";
            case SHIZUKU:
                if (suiPresent) {
                    return "Sui" + (shizukuVersion != null ? " " + shizukuVersion : "");
                }
                return "Shizuku " + (shizukuVersion != null ? shizukuVersion : "(version unknown)");
            case ADB:
                if (Ops.MODE_ADB_WIFI.equals(configuredMode)) {
                    return "Wireless debugging";
                }
                if (Ops.MODE_ADB_OVER_TCP.equals(configuredMode)) {
                    return "ADB over TCP (adb tcpip, set up over USB)";
                }
                return "ADB";
            case OTHER:
            case NO_ROOT:
            default:
                return NONE;
        }
    }

    @VisibleForTesting
    @NonNull
    static String formatBootstrapSignature(@Nullable String signature) {
        if (signature == null || signature.trim().isEmpty()) {
            return NONE;
        }
        // One line, so it stays one item of the list.
        return SupportInfoBundle.scrubForPublicIssue(signature.trim().replaceAll("\\s*\\R\\s*", "; "));
    }

    @VisibleForTesting
    @NonNull
    static String formatInstallSource(@Nullable String installer, @Nullable String selfPackage) {
        if (installer == null || installer.isEmpty()) {
            return UNKNOWN;
        }
        if (installer.equals(selfPackage)) {
            return installer + " (AppManagerNG)";
        }
        String friendly = INSTALLERS.get(installer);
        if (friendly != null) {
            return installer + " (" + friendly + ")";
        }
        if (SYSTEM_INSTALLERS.contains(installer)) {
            return installer + " (APK file opened in the package installer, such as a GitHub download)";
        }
        // Any other installer is an app the user has, which a public issue doesn't need to name.
        return SupportInfoBundle.scrubForPublicIssue(installer);
    }

    @NonNull
    private static String formatDevice(@Nullable String manufacturer, @Nullable String model) {
        boolean noMaker = TextUtils.isEmpty(manufacturer);
        boolean noModel = TextUtils.isEmpty(model);
        if (noMaker && noModel) {
            return UNKNOWN;
        }
        if (noMaker) {
            return model;
        }
        return noModel ? manufacturer : manufacturer + " " + model;
    }

    @NonNull
    private static String formatAndroid(@Nullable String release, int apiLevel) {
        String version = orDefault(release, UNKNOWN);
        return apiLevel > 0 ? version + " (API " + apiLevel + ")" : version;
    }

    @VisibleForTesting
    @NonNull
    static String formatBuild(@Nullable String display, @NonNull List<String> roms) {
        String build = orDefault(display, UNKNOWN);
        if (roms.isEmpty()) {
            return build;
        }
        return build + " (" + TextUtils.join(", ", roms) + ")";
    }

    @VisibleForTesting
    @NonNull
    static String formatAbis(@Nullable String[] abis) {
        if (abis == null || abis.length == 0 || TextUtils.isEmpty(abis[0])) {
            return UNKNOWN;
        }
        if (abis.length == 1) {
            return abis[0];
        }
        return abis[0] + " (" + TextUtils.join(", ", Arrays.copyOfRange(abis, 1, abis.length)) + ")";
    }

    /** The OEM skin or custom ROM, from the properties each one sets. */
    @VisibleForTesting
    @NonNull
    static List<String> detectRoms(@NonNull PropertyReader props) {
        List<String> roms = new ArrayList<>(2);
        String hyperOs = props.get("ro.mi.os.version.name");
        String miui = props.get("ro.miui.ui.version.name");
        if (!hyperOs.isEmpty()) {
            roms.add("HyperOS " + hyperOs);
        } else if (!miui.isEmpty()) {
            roms.add("MIUI " + miui);
        }
        String oneUi = props.get("ro.build.version.oneui");
        if (!oneUi.isEmpty()) {
            roms.add("One UI " + formatOneUiVersion(oneUi));
        }
        String colorOs = props.get("ro.build.version.oplusrom");
        if (colorOs.isEmpty()) {
            colorOs = props.get("ro.build.version.opporom");
        }
        if (!colorOs.isEmpty()) {
            roms.add("ColorOS " + colorOs);
        }
        String lineage = props.get("ro.lineage.version");
        if (!lineage.isEmpty()) {
            roms.add("LineageOS " + lineage);
        }
        return roms;
    }

    /** One UI writes its version as a number, 60100 for 6.1. */
    @NonNull
    private static String formatOneUiVersion(@NonNull String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= 10000) {
                return (value / 10000) + "." + (value / 100 % 100);
            }
        } catch (NumberFormatException ignore) {
        }
        return raw;
    }

    @NonNull
    private static String orDefault(@Nullable String value, @NonNull String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void line(@NonNull StringBuilder sb, @NonNull String name, @NonNull String value) {
        sb.append("- ").append(name).append(": ").append(value).append('\n');
    }
}
