// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;

class NormalShell extends Runner {
    private final Shell mShell;

    public NormalShell(boolean isRoot) {
        // Shell.getShell() builds libsu's default shell, which asks for root. A non-root shell may
        // only reuse a main shell that already exists and is itself non-root.
        Shell mainShell = isRoot ? Shell.getShell() : Shell.getCachedShell();
        if (mainShell != null && mainShell.isRoot() == isRoot) {
            mShell = mainShell;
            return;
        }
        int flags = isRoot ? Shell.FLAG_MOUNT_MASTER : Shell.FLAG_NON_ROOT_SHELL;
        mShell = Shell.Builder.create().setFlags(flags).setTimeout(10).build();
    }

    @Override
    public boolean isRoot() {
        return mShell.isRoot();
    }

    @Override
    void close() {
        if (mShell == Shell.getCachedShell()) {
            // libsu's main shell stays for everyone else
            return;
        }
        try {
            mShell.close();
        } catch (IOException e) {
            Log.w(TAG, "Could not close the shell.", e);
        }
    }

    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        List<String> stdout = new ArrayList<>();
        List<String> stderr = new ArrayList<>();
        Shell.Job job = mShell.newJob().add(commands.toArray(new String[0])).to(stdout, stderr);
        for (InputStream is : inputStreams) {
            job.add(is);
        }
        Shell.Result result = job.exec();
        return new Result(stdout, stderr, result.getCode());
    }
}
