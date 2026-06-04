// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONException;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class TaskerPluginEditActivity extends BaseActivity {
    private boolean mFinished;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        String configuredUri = TaskerPluginBroker.getConfiguredUri(getIntent());
        TextInputDialogBuilder builder = new TextInputDialogBuilder(this, R.string.tasker_plugin_uri_label)
                .setTitle(R.string.tasker_plugin_label)
                .setInputText(configuredUri != null ? configuredUri : "")
                .setInputInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .setHelperText(R.string.tasker_plugin_uri_helper)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> finishCanceled())
                .setOnDismissListener(dialog -> {
                    if (!mFinished) {
                        finishCanceled();
                    }
                });
        builder.setOnShowListener(dialog -> ((AlertDialog) dialog)
                .getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String uri = builder.getInputText() != null ? builder.getInputText().toString().trim() : "";
                    if (!TaskerPluginBroker.isSupportedAutomationUri(uri)) {
                        UIUtils.displayShortToast(R.string.tasker_plugin_invalid_uri);
                        return;
                    }
                    try {
                        setResult(RESULT_OK, TaskerPluginBroker.buildEditResultIntent(uri));
                        mFinished = true;
                        dialog.dismiss();
                        finish();
                    } catch (JSONException | RuntimeException e) {
                        UIUtils.displayShortToast(R.string.tasker_plugin_invalid_uri);
                    }
                }));
        builder.show();
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void finishCanceled() {
        setResult(RESULT_CANCELED);
        mFinished = true;
        finish();
    }
}
