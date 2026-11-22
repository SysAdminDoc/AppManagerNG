// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.utils.StoragePermission;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class RecordLogDialogActivity extends BaseActivity {
    public static final String EXTRA_QUERY_SUGGESTIONS = "suggestions";

    private final StoragePermission mStoragePermission = StoragePermission.init(this);

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        Fragment shown = getSupportFragmentManager().findFragmentByTag(RecordLogDialogFragment.TAG);
        if (shown instanceof RecordLogDialogFragment) {
            // Back after a rotation: the dialog came back on its own, without what closes this screen
            ((RecordLogDialogFragment) shown).setOnDismissListener(v -> finish());
            return;
        }
        // The recording is written to shared storage, so ask for it first, as the log viewer's Record does
        mStoragePermission.request(granted -> {
            if (!granted || isFinishing() || isDestroyed()) {
                finish();
                return;
            }
            RecordLogDialogFragment dialog = RecordLogDialogFragment.getInstance(
                    getIntent().getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS), null);
            dialog.show(getSupportFragmentManager(), RecordLogDialogFragment.TAG);
            dialog.setOnDismissListener(v -> finish());
        });
    }
}
