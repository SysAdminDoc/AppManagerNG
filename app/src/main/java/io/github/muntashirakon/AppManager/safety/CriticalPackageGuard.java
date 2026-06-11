// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.safety;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class CriticalPackageGuard {
    private CriticalPackageGuard() {}

    /** Critical OS / vendor packages that should not be modified by bulk destructive actions. */
    public static final Set<String> CRITICAL_PACKAGES;
    static {
        HashSet<String> s = new HashSet<>();
        // AOSP core
        s.add("android");
        s.add("com.android.systemui");
        s.add("com.android.settings");
        s.add("com.android.phone");
        s.add("com.android.server.telecom");
        s.add("com.android.providers.telephony");
        s.add("com.android.providers.contacts");
        s.add("com.android.providers.calendar");
        s.add("com.android.providers.media");
        s.add("com.android.providers.media.module");
        s.add("com.android.bluetooth");
        s.add("com.android.nfc");
        s.add("com.android.location.fused");
        s.add("com.android.shell");
        s.add("com.android.permissioncontroller");
        // Google services
        s.add("com.google.android.gms");
        s.add("com.google.android.gsf");
        s.add("com.google.android.ext.services");
        s.add("com.google.android.permissioncontroller");
        s.add("com.google.android.packageinstaller");
        s.add("com.google.android.apps.maps");
        // Samsung One UI
        s.add("com.samsung.android.location");
        s.add("com.samsung.android.providers.context");
        s.add("com.samsung.android.bluetooth");
        s.add("com.samsung.android.dialer");
        s.add("com.samsung.android.incallui");
        s.add("com.samsung.android.app.telephonyui");
        s.add("com.samsung.android.callassistant");
        s.add("com.samsung.android.emergency");
        s.add("com.sec.location.nsflp2");
        s.add("com.sec.location.nfwlocationprivacy");
        s.add("com.sec.android.app.camera");
        s.add("com.sec.phone");
        s.add("com.sec.imsservice");
        s.add("com.sec.imslogger");
        s.add("com.sec.epdg");
        // AppManager itself
        s.add("io.github.muntashirakon.AppManager");
        s.add("io.github.sysadmindoc.AppManagerNG");
        s.add("io.github.sysadmindoc.AppManagerNG.debug");
        CRITICAL_PACKAGES = Collections.unmodifiableSet(s);
    }

    public static boolean isCriticalPackage(@NonNull String packageName) {
        return CRITICAL_PACKAGES.contains(packageName)
                || packageName.startsWith("com.android.server.")
                || packageName.startsWith("com.google.android.gms.");
    }

    @NonNull
    public static List<UserPackagePair> getCriticalTargets(@NonNull List<UserPackagePair> targets) {
        ArrayList<UserPackagePair> criticalTargets = new ArrayList<>();
        for (UserPackagePair target : targets) {
            if (isCriticalPackage(target.getPackageName())) {
                criticalTargets.add(target);
            }
        }
        return criticalTargets;
    }
}
