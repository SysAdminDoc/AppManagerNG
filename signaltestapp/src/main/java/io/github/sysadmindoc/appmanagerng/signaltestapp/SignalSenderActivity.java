// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sysadmindoc.appmanagerng.signaltestapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class SignalSenderActivity extends Activity {
    public static final String EXTRA_TARGET_PACKAGE = "target_package";
    public static final String EXTRA_TARGET_ACTION = "target_action";
    public static final String EXTRA_CHANGED_PACKAGE = "changed_package";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent request = getIntent();
        String targetPackage = request.getStringExtra(EXTRA_TARGET_PACKAGE);
        String targetAction = request.getStringExtra(EXTRA_TARGET_ACTION);
        String changedPackage = request.getStringExtra(EXTRA_CHANGED_PACKAGE);
        if (targetPackage != null && targetAction != null && changedPackage != null) {
            sendBroadcast(new Intent(targetAction)
                    .setPackage(targetPackage)
                    .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, new String[]{changedPackage}));
        }
        finish();
    }
}
