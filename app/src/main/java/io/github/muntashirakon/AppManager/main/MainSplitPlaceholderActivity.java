// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.os.Bundle;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;

public class MainSplitPlaceholderActivity extends BaseActivity {
    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_main_split_placeholder);
    }
}
