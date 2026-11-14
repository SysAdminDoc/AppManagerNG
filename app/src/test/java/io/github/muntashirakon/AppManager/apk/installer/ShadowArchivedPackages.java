// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.ComponentName;
import android.content.pm.ArchivedActivityInfo;
import android.content.pm.ArchivedPackageInfo;
import android.content.pm.SigningInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers getArchivedPackage the way Android 15 does for the calling user: the launcher entries it
 * kept, or IllegalArgumentException when it kept none. Robolectric has no archive support of its own.
 */
@Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
public class ShadowArchivedPackages extends ShadowApplicationPackageManager {
    public static final List<String> sAsked = new ArrayList<>();
    private static final List<ArchivedActivityInfo> sEntries = new ArrayList<>();
    private static boolean sKeptNone;

    public static void reset() {
        sAsked.clear();
        sEntries.clear();
        sKeptNone = false;
    }

    public static void keep(@NonNull String packageName, @Nullable CharSequence label, @Nullable Drawable icon) {
        ArchivedActivityInfo entry = new ArchivedActivityInfo(label != null ? label : "",
                new ComponentName(packageName, packageName + ".Launcher" + sEntries.size()));
        entry.setIcon(icon);
        sEntries.add(entry);
    }

    public static void keepNone() {
        sKeptNone = true;
    }

    @Implementation(minSdk = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @NonNull
    protected ArchivedPackageInfo getArchivedPackage(@NonNull String packageName) {
        sAsked.add(packageName);
        if (sKeptNone) {
            // What PackageManagerService reports when the archive state has no launcher activity
            throw new IllegalArgumentException("Package does not have a main activity");
        }
        SigningInfo signingInfo = ReflectionHelpers.callConstructor(SigningInfo.class);
        return new ArchivedPackageInfo(packageName, signingInfo, new ArrayList<>(sEntries));
    }
}
