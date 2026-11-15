// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The lists {@code IAppOpsService.getUidOps} and {@code getOpsForPackage} return, built from a fake service's stored
 * modes, for SDK 30 and later test configs.
 */
final class AppOpsListFixtures {
    private AppOpsListFixtures() {
    }

    /**
     * One package's or UID's entries for the stored modes the {@code ops} filter asks for, or null when none match,
     * as the service answers when it has nothing stored for them.
     */
    @Nullable
    static List<Parcelable> opList(@Nullable String packageName, int uid, @NonNull Map<Integer, Integer> modes,
                                   @Nullable int[] ops) throws ReflectiveOperationException {
        Constructor<?> newOpEntry = Class.forName("android.app.AppOpsManager$OpEntry")
                .getConstructor(int.class, int.class, Map.class);
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<Integer, Integer> stored : modes.entrySet()) {
            if (ops == null || contains(ops, stored.getKey())) {
                entries.add(newOpEntry.newInstance(stored.getKey(), stored.getValue(), Collections.emptyMap()));
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        Constructor<?> newPackageOps = Class.forName("android.app.AppOpsManager$PackageOps")
                .getConstructor(String.class, int.class, List.class);
        return Collections.singletonList((Parcelable) newPackageOps.newInstance(packageName, uid, entries));
    }

    private static boolean contains(@NonNull int[] ops, int op) {
        for (int candidate : ops) {
            if (candidate == op) {
                return true;
            }
        }
        return false;
    }
}
