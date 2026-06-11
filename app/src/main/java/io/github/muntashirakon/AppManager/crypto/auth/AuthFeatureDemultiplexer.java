// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierReceiver;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AuthFeatureDemultiplexer extends BaseActivity {
    public static final String EXTRA_FEATURE = "feature";

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_AUTH) || !intent.hasExtra(EXTRA_FEATURE)) {
            // It does not have the required extras, ignore the request
            finishAndRemoveTask();
            return;
        }
        handleRequest(intent);
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void handleRequest(@NonNull Intent intent) {
        String auth = intent.getStringExtra(EXTRA_AUTH);
        String feature = intent.getStringExtra(EXTRA_FEATURE);

        intent.removeExtra(EXTRA_AUTH);
        intent.removeExtra(EXTRA_FEATURE);

        if (!AuthManager.getKey().equals(auth)) {
            UIUtils.displayLongToast(R.string.auth_feature_request_rejected);
            finishAndRemoveTask();
            return;
        }

        switch (String.valueOf(feature)) {
            case "profile":
                launchProfile(intent);
                break;
            default:
                UIUtils.displayLongToast(R.string.auth_feature_not_supported);
                finishAndRemoveTask();
                return;
        }
        finish();
    }

    public void launchProfile(@NonNull Intent intent) {
        String profileId = intent.getStringExtra(ProfileApplierActivity.EXTRA_PROFILE_ID);
        if (profileId == null || profileId.trim().isEmpty()) {
            return;
        }
        String state = intent.getStringExtra(ProfileApplierActivity.EXTRA_STATE);
        Intent receiverIntent = ProfileApplierReceiver.getAutomationIntent(getApplicationContext(), profileId, state);
        String runtimePackage = intent.getStringExtra(ProfileApplierReceiver.EXTRA_RUNTIME_PACKAGE);
        if (runtimePackage != null) {
            receiverIntent.putExtra(ProfileApplierReceiver.EXTRA_RUNTIME_PACKAGE, runtimePackage);
        }
        sendBroadcast(receiverIntent);
    }
}
