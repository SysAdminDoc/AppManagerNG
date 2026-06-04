// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.BaseActivity;

@RequiresApi(Build.VERSION_CODES.R)
public class AdbPairingInputActivity extends BaseActivity {
    private boolean mDialogShown;

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (mDialogShown) {
            return;
        }
        mDialogShown = true;
        ContextCompat.startForegroundService(this, AdbPairingService.getStartSearchingIntent(this));
        AdbPairingCodeDialog.show(this, this::finish);
    }
}
