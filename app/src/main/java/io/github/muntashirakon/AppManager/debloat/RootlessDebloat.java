// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.BroadcastUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public final class RootlessDebloat {
    private RootlessDebloat() {
    }

    public static boolean canUsePmUninstall() {
        return LocalServices.alive() && Users.getSelfOrRemoteUid() == Ops.SHELL_UID;
    }

    @NonNull
    public static String getProviderLabel(@NonNull Context context) {
        return context.getString(Ops.isShizuku() ? R.string.shizuku : R.string.adb);
    }

    public static boolean uninstallForUser(@NonNull UserPackagePair pair, boolean keepData) {
        if (!PackageInstallerCompat.factoryResetUpdatedSystemApp(pair.getPackageName(), pair.getUserId(), keepData)) {
            return false;
        }

        ArrayList<String> command = new ArrayList<>();
        command.add("pm");
        command.add("uninstall");
        if (keepData) {
            command.add("-k");
        }
        command.add("--user");
        command.add(String.valueOf(pair.getUserId()));
        command.add(pair.getPackageName());

        Runner.Result result = Runner.runCommand(command.toArray(new String[0]));
        String output = result.getOutput().trim();
        boolean success = result.isSuccessful() && output.startsWith("Success");
        if (success) {
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), new String[]{pair.getPackageName()});
        }
        return success;
    }
}
