// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.activity.LeadingActivityTrackerActivity;
import io.github.muntashirakon.AppManager.crypto.auth.ActionAuthGate;
import io.github.muntashirakon.AppManager.editor.CodeEditorActivity;
import io.github.muntashirakon.AppManager.fm.FmActivity;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryActivity;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.terminal.TermActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.sysconfig.SysConfigActivity;

public class LabsActivity extends BaseActivity {
    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_labs);
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewGroup actionContainer = findViewById(R.id.action_container);
        addSection(actionContainer, R.string.labs_section_system_insight);
        if (FeatureController.isLogViewerEnabled()) {
            addAction(this, actionContainer, R.string.log_viewer, R.drawable.ic_view_list)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(this, LogViewerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    });
        }
        addAction(this, actionContainer, R.string.sys_config, R.drawable.ic_hammer_wrench)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, SysConfigActivity.class);
                    startActivity(intent);
                });
        addAction(this, actionContainer, R.string.title_ui_tracker, R.drawable.ic_cursor_default_click)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, LeadingActivityTrackerActivity.class);
                    startActivity(intent);
                });
        if (FeatureController.isInterceptorEnabled()) {
            addAction(this, actionContainer, R.string.interceptor, R.drawable.ic_transit_connection)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(this, ActivityInterceptor.class);
                        startActivity(intent);
                    });
        }
        addAction(this, actionContainer, R.string.op_history, R.drawable.ic_history)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, OpHistoryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });

        addSection(actionContainer, R.string.labs_section_workbench);
        addAction(this, actionContainer, R.string.files, R.drawable.ic_file_document_multiple)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, FmActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });
        if (FeatureController.isCodeEditorEnabled()) {
            addAction(this, actionContainer, R.string.title_code_editor, R.drawable.ic_code)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(this, CodeEditorActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    });
        }
        if (FeatureController.isTerminalEnabled()) {
            addAction(this, actionContainer, R.string.title_terminal_emulator, R.drawable.ic_frost_termux)
                    .setOnClickListener(v -> ActionAuthGate.authenticateAlways(this,
                            R.string.authenticate_to_open_terminal, () -> {
                                Intent intent = new Intent(this, TermActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    private static MaterialButton addAction(@NonNull Context context, @NonNull ViewGroup parent,
                                            @StringRes int stringResId, @DrawableRes int iconResId) {
        MaterialButton button = (MaterialButton) LayoutInflater.from(context).inflate(
                R.layout.item_labs_action, parent, false);
        button.setText(stringResId);
        button.setIconResource(iconResId);
        parent.addView(button);
        return button;
    }

    private void addSection(@NonNull ViewGroup parent, @StringRes int titleResId) {
        TextView title = (TextView) LayoutInflater.from(this).inflate(
                R.layout.item_labs_section, parent, false);
        title.setText(titleResId);
        parent.addView(title);
    }
}
