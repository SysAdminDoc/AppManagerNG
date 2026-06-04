// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;

public final class AdbPairingCodeDialog {
    private AdbPairingCodeDialog() {
    }

    @SuppressLint("InflateParams")
    public static void show(@NonNull FragmentActivity activity, @Nullable Runnable onDismiss) {
        View view = View.inflate(activity, R.layout.dialog_adb_pairing_code, null);
        AppCompatTextView statusView = view.findViewById(R.id.adb_pairing_status);
        AppCompatTextView portView = view.findViewById(R.id.adb_pairing_port);
        TextInputLayout codeLayout = view.findViewById(R.id.adb_pairing_code_layout);
        TextInputEditText codeInput = view.findViewById(R.id.adb_pairing_code);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(AdbPairingRequest.PAIRING_CODE_LENGTH)});
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        AtomicBoolean dismissedFromState = new AtomicBoolean(false);
        AtomicReference<Observer<AdbPairingState>> observerRef = new AtomicReference<>();
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.wireless_debugging)
                .setView(view)
                .setPositiveButton(R.string.adb_pair, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            Observer<AdbPairingState> observer = state -> {
                updateViews(activity, dialog, positiveButton, statusView, portView, codeLayout, codeInput, state,
                        dismissedFromState);
            };
            observerRef.set(observer);
            AdbPairingSession.getState().observe(activity, observer);
            updateViews(activity, dialog, positiveButton, statusView, portView, codeLayout, codeInput,
                    AdbPairingSession.getCurrentState(), dismissedFromState);
            positiveButton.setOnClickListener(v -> {
                Editable inputText = codeInput.getText();
                AdbPairingRequest request = AdbPairingRequest.create(AdbPairingSession.getCurrentState().getPort(),
                        inputText);
                if (request == null) {
                    codeLayout.setError(activity.getString(R.string.adb_pairing_code_invalid));
                    return;
                }
                codeLayout.setError(null);
                ContextCompat.startForegroundService(activity,
                        AdbPairingService.getStartPairingIntent(activity, request));
            });
            negativeButton.setOnClickListener(v -> {
                dismissedFromState.set(true);
                ContextCompat.startForegroundService(activity, AdbPairingService.getStopSearchingIntent(activity));
                dialog.dismiss();
            });
        });
        dialog.setOnDismissListener(dialogInterface -> {
            Observer<AdbPairingState> observer = observerRef.get();
            if (observer != null) {
                AdbPairingSession.getState().removeObserver(observer);
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        dialog.show();
    }

    private static void updateViews(@NonNull FragmentActivity activity, @NonNull AlertDialog dialog,
                                    @NonNull Button positiveButton, @NonNull AppCompatTextView statusView,
                                    @NonNull AppCompatTextView portView, @NonNull TextInputLayout codeLayout,
                                    @NonNull TextInputEditText codeInput, @NonNull AdbPairingState state,
                                    @NonNull AtomicBoolean dismissedFromState) {
        boolean hasPort = state.hasPort();
        boolean canEdit = hasPort && state.getStatus() != AdbPairingState.Status.PAIRING
                && state.getStatus() != AdbPairingState.Status.SUCCEEDED
                && state.getStatus() != AdbPairingState.Status.CANCELLED;
        positiveButton.setEnabled(canEdit);
        codeInput.setEnabled(canEdit);
        if (state.getStatus() != AdbPairingState.Status.FAILED) {
            codeLayout.setError(null);
        }
        if (hasPort) {
            portView.setText(activity.getString(R.string.adb_pairing_found_pairing_service_with_port, state.getPort()));
            codeLayout.setHelperText(activity.getString(R.string.adb_pairing_code_helper, state.getPort()));
        } else {
            portView.setText(R.string.adb_pairing_searching_for_port);
            codeLayout.setHelperText(activity.getString(R.string.adb_pairing_code_waiting_for_port));
        }
        switch (state.getStatus()) {
            case PAIRING:
                statusView.setText(R.string.adb_pairing_pairing_in_progress);
                break;
            case FAILED:
                statusView.setText(R.string.adb_pairing_failed_retry_in_app);
                break;
            case SUCCEEDED:
                statusView.setText(R.string.paired_successfully);
                dismissedFromState.set(true);
                dialog.dismiss();
                break;
            case CANCELLED:
                statusView.setText(R.string.adb_pairing_not_finished);
                if (!dismissedFromState.get()) {
                    dismissedFromState.set(true);
                    dialog.dismiss();
                }
                break;
            case PORT_FOUND:
                statusView.setText(R.string.adb_pairing_input_pairing_code);
                break;
            case IDLE:
            case SEARCHING:
            default:
                statusView.setText(R.string.adb_pairing_searching_for_port);
                break;
        }
    }
}
