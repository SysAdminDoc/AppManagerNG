// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.servermanager.AdbFailure;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.TextInputTextView;

public class ModeOfOpsPreference extends Fragment {
    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_SHIZUKU,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private MaterialTextView mInferredModeView;
    private MaterialTextView mRemoteServerStatusView;
    private MaterialTextView mRemoteServicesStatusView;
    private MaterialTextView mModeOfOpsView;
    private MaterialTextView mAdbFailureView;
    @Nullable
    private MaterialButton mChangeModeView;
    private MainPreferencesViewModel mModel;
    private ModeOfOpsApplyState mModeApplyState;
    private AlertDialog mModeOfOpsAlertDialog;
    private String[] mModes;
    @Ops.Mode
    private String mCurrentMode;
    private boolean mConnecting;
    @Nullable
    private ColorStateList mColorActive;
    @Nullable
    private ColorStateList mColorInactive;
    @Nullable
    private ColorStateList mColorError;
    @DrawableRes
    private int mIconActive;
    @DrawableRes
    private int mIconInactive;
    @DrawableRes
    private int mIconProgress;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mModeApplyState = mModel.getModeApplyState();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mode_of_ops, container, false);
        boolean secondary = false;
        if (getArguments() != null) {
            secondary = requireArguments().getBoolean(PreferenceFragment.PREF_SECONDARY);
            requireArguments().remove(PreferenceFragment.PREF_KEY);
            requireArguments().remove(PreferenceFragment.PREF_SECONDARY);
        }
        if (secondary) {
            UiUtils.applyWindowInsetsAsPadding(view, false, true, false, true);
        } else UiUtils.applyWindowInsetsAsPaddingNoTop(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mColorActive = MaterialColors.getColorStateListOrNull(view.getContext(), com.google.android.material.R.attr.colorOnPrimaryContainer);
        mColorInactive = MaterialColors.getColorStateListOrNull(view.getContext(), com.google.android.material.R.attr.colorOutline);
        mColorError = MaterialColors.getColorStateListOrNull(view.getContext(), com.google.android.material.R.attr.colorOnErrorContainer);
        mIconActive = R.drawable.ic_check_circle;
        mIconInactive = io.github.muntashirakon.ui.R.drawable.ic_caution;
        mIconProgress = R.drawable.ic_sync;
        mModeOfOpsAlertDialog = UIUtils.getProgressDialog(requireActivity(), getString(R.string.loading), true);
        mModes = getResources().getStringArray(R.array.modes);
        mCurrentMode = Ops.getMode();
        if (mModeApplyState.isApplying()) {
            String pendingMode = mModeApplyState.getPendingMode();
            if (pendingMode != null) {
                mCurrentMode = pendingMode;
            }
            mConnecting = true;
        }
        mInferredModeView = view.findViewById(R.id.inferred_mode);
        mRemoteServerStatusView = view.findViewById(R.id.remote_server_status);
        mRemoteServicesStatusView = view.findViewById(R.id.remote_services_status);
        mModeOfOpsView = view.findViewById(R.id.op_name);
        mAdbFailureView = view.findViewById(R.id.adb_failure);
        bindCapabilities(view);
        mChangeModeView = view.findViewById(R.id.action_settings);
        List<String> disabledItems;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Utils.isTv(requireContext())) {
            disabledItems = Collections.singletonList(Ops.MODE_ADB_WIFI);
        } else disabledItems = null;
        mChangeModeView.setOnClickListener(v -> {
            if (mModeApplyState.isApplying() || mModel.isModeOperationPending()) {
                return;
            }
            new SearchableSingleChoiceDialogBuilder<>(requireActivity(), MODE_NAMES, mModes)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSelection(mCurrentMode)
                    .addDisabledItems(disabledItems)
                    .setPositiveButton(R.string.apply, (dialog, which, selectedItem) -> {
                        if (selectedItem != null) {
                            beginModeApply(selectedItem);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        TextInputTextView customCommand0 = view.findViewById(android.R.id.text1);
        TextInputLayout customCommand0Layout = TextInputLayoutCompat.fromTextInputEditText(customCommand0);
        customCommand0Layout.setEndIconOnClickListener(v -> copyCommand(requireContext(), customCommand0.getText()));
        TextInputTextView customCommand1 = view.findViewById(android.R.id.text2);
        TextInputLayout customCommand1Layout = TextInputLayoutCompat.fromTextInputEditText(customCommand1);
        customCommand1Layout.setEndIconOnClickListener(v -> copyCommand(requireContext(), customCommand1.getText()));
        mModel.loadCustomCommands();
        updateViews();
        // Mode of ops
        mModel.getModeOfOpsStatus().observe(getViewLifecycleOwner(), this::handleModeStatus);
        if (mModeApplyState.isApplying()) {
            // Recreated in the middle of a switch (rotation, theme change)
            setModeApplyUiEnabled(false);
            if (mModel.isModeOperationPending()) {
                showModeProgressDialog();
            } else {
                // The dialog asking the user for something went away with the old screen
                Integer lostStatus = mModel.getLostDialogStatus();
                if (lostStatus != null) {
                    handleModeStatus(lostStatus);
                }
            }
        }
        // Services can bind or stop after the screen opened, a mode switch or a late SERVER_STARTED
        LocalServices.state().observe(getViewLifecycleOwner(), ignored -> updateViews());
        mModel.getCustomCommand0().observe(getViewLifecycleOwner(), customCommand0::setText);
        View rootCommandLabel = view.findViewById(R.id.root_command_label);
        mModel.getCustomCommand1().observe(getViewLifecycleOwner(), rootCommand -> {
            // Only a device with su gets the root command
            int visibility = rootCommand != null ? View.VISIBLE : View.GONE;
            rootCommandLabel.setVisibility(visibility);
            customCommand1Layout.setVisibility(visibility);
            customCommand1.setText(rootCommand);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_mode_of_operations);
        // Re-evaluate capability hints every time we resume — a user who left to
        // toggle Wireless debugging or grant root from another app should see the
        // new state on return without having to leave Settings entirely.
        if (getView() != null) {
            bindCapabilities(getView());
        }
    }

    @Override
    public void onDestroyView() {
        if (!requireActivity().isChangingConfigurations()) {
            // Leaving the screen gives up on the switch. A configuration change keeps it: the
            // recreated screen picks it up again from the ViewModel.
            dismissPendingModeApply();
        }
        dismissModeProgressDialog();
        mChangeModeView = null;
        super.onDestroyView();
    }

    private void handleModeStatus(@Ops.Status int status) {
        if (!mModeApplyState.isApplying() || !isAdded()) {
            return;
        }
        FragmentActivity activity = requireActivity();
        switch (status) {
            case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    updateViews();
                    mModel.autoConnectWirelessDebugging();
                    return;
                } // fall-through
            case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    dismissModeProgressDialog();
                    updateViews();
                    Ops.connectWirelessDebugging(activity, mModel);
                    mModel.onModeStatusDialogShown();
                    return;
                } // fall-through
            case Ops.STATUS_ADB_CONNECT_REQUIRED:
                dismissModeProgressDialog();
                updateViews();
                Ops.connectAdbInput(activity, mModel);
                mModel.onModeStatusDialogShown();
                return;
            case Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED:
                dismissModeProgressDialog();
                updateViews();
                Ops.requestShizukuPermission(activity, mModel);
                mModel.onModeStatusDialogShown();
                return;
            case Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED:
                dismissModeProgressDialog();
                updateViews();
                Ops.displayLocalNetworkPermissionMessage(activity, mModel);
                mModel.onModeStatusDialogShown();
                return;
            case Ops.STATUS_ADB_PAIRING_REQUIRED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    dismissModeProgressDialog();
                    updateViews();
                    Ops.pairAdbInput(activity, mModel);
                    mModel.onModeStatusDialogShown();
                    return;
                } // fall-through
            case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                dismissModeProgressDialog();
                // The switch ends once the user has read why it failed
                Ops.displayIncompleteUsbDebuggingMessage(activity, this::onIncompleteUsbDebuggingDismissed);
                mModel.onModeStatusDialogShown();
                return;
            case Ops.STATUS_FAILURE_SERVER_START:
            case Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE:
            case Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED:
                // Ops already told the user what went wrong, so no rollback message on top
                finishModeApply(false, false);
                return;
            case Ops.STATUS_SUCCESS:
                finishModeApply(true, false);
                return;
            case Ops.STATUS_FAILURE:
                finishModeApply(false, true);
        }
    }

    private void onIncompleteUsbDebuggingDismissed() {
        // Not when the dialog went away with a screen that is gone
        if (getView() != null) {
            finishModeApply(false, true);
        }
    }

    private void beginModeApply(@NonNull @Ops.Mode String mode) {
        if (mModel.isModeOperationPending()) {
            // A request from a screen that was closed is still running
            UIUtils.displayShortToast(R.string.mode_of_op_busy);
            return;
        }
        if (!mModeApplyState.begin(Ops.getMode(), mode)) {
            return;
        }
        mCurrentMode = mode;
        if (Ops.MODE_ADB_OVER_TCP.equals(mode)) {
            ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
        }
        showModeProgressDialog();
        mConnecting = true;
        setModeApplyUiEnabled(false);
        updateViews();
        mModel.setModeOfOps(mode);
    }

    private void finishModeApply(boolean success, boolean showFailure) {
        mConnecting = false;
        dismissModeProgressDialog();
        if (success) {
            String pendingMode = mModeApplyState.finishSuccess();
            if (pendingMode != null) {
                Ops.setMode(pendingMode);
            }
        } else {
            rollbackPendingModeApply(showFailure);
        }
        mCurrentMode = Ops.getMode();
        setModeApplyUiEnabled(true);
        if (getView() != null) {
            updateViews();
        }
    }

    private void rollbackPendingModeApply(boolean showFailure) {
        String previousMode = mModeApplyState.finishFailure();
        if (previousMode != null) {
            Ops.setMode(previousMode);
            if (showFailure) {
                UIUtils.displayLongToast(R.string.mode_of_op_apply_failed_rollback);
            }
        }
    }

    private void dismissPendingModeApply() {
        String previousMode = mModeApplyState.dismiss();
        if (previousMode != null) {
            Ops.setMode(previousMode);
        }
    }

    private void showModeProgressDialog() {
        if (!mModeOfOpsAlertDialog.isShowing()) {
            mModeOfOpsAlertDialog.show();
        }
    }

    private void dismissModeProgressDialog() {
        if (mModeOfOpsAlertDialog.isShowing()) {
            mModeOfOpsAlertDialog.dismiss();
        }
    }

    private void setModeApplyUiEnabled(boolean enabled) {
        if (mChangeModeView != null) {
            mChangeModeView.setEnabled(enabled);
        }
    }

    private void updateViews() {
        boolean serverActive = LocalServer.alive(requireContext());
        boolean serverRequired = requireRemoteServer(mCurrentMode);
        boolean servicesActive = LocalServices.alive();
        boolean servicesRequired = requireRemoteServices(mCurrentMode);
        // Mode
        if (mConnecting) {
            mInferredModeView.setText(R.string.status_connecting);
            mInferredModeView.setTextColor(mColorActive);
            TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorActive);
            mModeOfOpsView.setTextColor(mColorActive);
            mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconProgress, 0, 0, 0);
            mModeOfOpsView.setText(getString(R.string.status_connecting_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)]));
        } else {
            int uid = Users.getSelfOrRemoteUid();
            boolean goodMode = !badInferredMode(mCurrentMode, uid);
            mInferredModeView.setText(Ops.getInferredMode(requireContext()));
            if (goodMode) {
                mInferredModeView.setTextColor(mColorActive);
                TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorActive);
                mModeOfOpsView.setTextColor(mColorActive);
                mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconActive, 0, 0, 0);
                CharSequence mode;
                if (serverActive && uid != Process.myUid()) {
                    mode = "remote service";
                } else mode = mModes[MODE_NAMES.indexOf(mCurrentMode)];
                mModeOfOpsView.setText(getString(R.string.status_connected_via_mode, mode));
            } else {
                mInferredModeView.setTextColor(mColorError);
                TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorError);
                mModeOfOpsView.setTextColor(mColorError);
                mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconInactive, 0, 0, 0);
                mModeOfOpsView.setText(getString(R.string.status_not_connected_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)]));
            }
        }
        // Server
        if (serverRequired) {
            mRemoteServerStatusView.setTextColor(serverActive ? mColorActive : mColorError);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServerStatusView, serverActive ? mColorActive : mColorError);
        } else {
            mRemoteServerStatusView.setTextColor(mColorInactive);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServerStatusView, mColorInactive);
        }
        mRemoteServerStatusView.setCompoundDrawablesRelativeWithIntrinsicBounds(serverActive ? mIconActive : mIconInactive, 0, 0, 0);
        mRemoteServerStatusView.setText(serverActive ? R.string.status_remote_server_active : R.string.status_remote_server_inactive);
        // Services
        if (servicesRequired) {
            mRemoteServicesStatusView.setTextColor(servicesActive ? mColorActive : mColorError);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServicesStatusView, servicesActive ? mColorActive : mColorError);
        } else {
            mRemoteServicesStatusView.setTextColor(mColorInactive);
            TextViewCompat.setCompoundDrawableTintList(mRemoteServicesStatusView, mColorInactive);
        }
        mRemoteServicesStatusView.setCompoundDrawablesRelativeWithIntrinsicBounds(servicesActive ? mIconActive : mIconInactive, 0, 0, 0);
        mRemoteServicesStatusView.setText(servicesActive ? R.string.status_remote_services_active : R.string.status_remote_services_inactive);
        // Why the last ADB connect failed, until one works
        CharSequence adbFailure = adbFailureText(requireContext(), mCurrentMode, mConnecting || serverActive,
                AdbFailure.getLast());
        mAdbFailureView.setText(adbFailure);
        mAdbFailureView.setVisibility(adbFailure != null ? View.VISIBLE : View.GONE);
    }

    /**
     * Both commands carry the server's token, so Android is asked to keep it out of the clipboard
     * preview.
     */
    @VisibleForTesting
    static void copyCommand(@NonNull Context context, @Nullable CharSequence command) {
        if (!TextUtils.isEmpty(command)) {
            ClipboardUtils.copySensitiveText(context, "command", command.toString());
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
        }
    }

    /**
     * What to show under the mode while an ADB mode isn't connected, or {@code null} for nothing.
     */
    @VisibleForTesting
    @Nullable
    static CharSequence adbFailureText(@NonNull Context context, @NonNull String mode,
                                       boolean connectingOrConnected, @Nullable AdbFailure failure) {
        if (failure == null || connectingOrConnected) {
            return null;
        }
        if (!Ops.MODE_ADB_WIFI.equals(mode) && !Ops.MODE_ADB_OVER_TCP.equals(mode)) {
            return null;
        }
        return failure.explain(context);
    }

    /**
     * Mirror onboarding's live capability hints inside the Settings mode picker so a
     * user changing modes here gets the same context onboarding gave them on first
     * launch (root present? USB debugging on? wireless debugging active?). Reads
     * {@code adb_enabled} and {@code adb_wifi_enabled} from {@link android.provider
     * .Settings.Global} -- both readable without permissions. Fails closed (reports
     * inactive) on read errors.
     */
    private void bindCapabilities(@NonNull View view) {
        com.google.android.material.textview.MaterialTextView rootRow = view.findViewById(R.id.capability_root);
        com.google.android.material.textview.MaterialTextView shizukuRow = view.findViewById(R.id.capability_shizuku);
        com.google.android.material.textview.MaterialTextView adbWifiRow = view.findViewById(R.id.capability_adb_wifi);
        com.google.android.material.textview.MaterialTextView adbUsbRow = view.findViewById(R.id.capability_adb_usb);
        MaterialTextView shizukuAutoStartHint = view.findViewById(R.id.hint_shizuku_autostart);
        MaterialButton shizukuAutoStartAction = view.findViewById(R.id.action_shizuku_autostart);
        if (rootRow != null) {
            rootRow.setText(getString(R.string.mode_of_op_capability_root,
                    getString(Ops.hasRoot()
                            ? R.string.mode_of_op_capability_status_detected
                            : R.string.mode_of_op_capability_status_missing)));
        }
        if (shizukuRow != null) {
            int statusRes;
            if (ShizukuBridge.hasPermission()) {
                statusRes = ShizukuBridge.isRootBacked()
                        ? R.string.mode_of_op_capability_status_root_backed
                        : R.string.mode_of_op_capability_status_authorized;
            } else if (ShizukuBridge.supportsUserService()) {
                statusRes = R.string.mode_of_op_capability_status_permission_required;
            } else {
                statusRes = R.string.mode_of_op_capability_status_missing;
            }
            shizukuRow.setText(getString(R.string.mode_of_op_capability_shizuku, getString(statusRes)));
        }
        bindShizukuAutoStartControls(shizukuAutoStartHint, shizukuAutoStartAction);
        if (adbWifiRow != null) {
            adbWifiRow.setText(getString(R.string.mode_of_op_capability_adb_wifi,
                    getString(getWirelessDebuggingStatusText())));
        }
        if (adbUsbRow != null) {
            adbUsbRow.setText(getString(R.string.mode_of_op_capability_adb_usb,
                    getString(isUsbDebuggingEnabled()
                            ? R.string.mode_of_op_capability_status_enabled
                            : R.string.mode_of_op_capability_status_disabled)));
        }
    }

    private void bindShizukuAutoStartControls(@Nullable MaterialTextView hint, @Nullable MaterialButton action) {
        if (ShizukuBridge.isRootBacked()) {
            if (hint != null) {
                hint.setVisibility(View.VISIBLE);
                hint.setText(R.string.mode_of_op_shizuku_root_backed_hint);
            }
            if (action != null) {
                action.setVisibility(View.VISIBLE);
                action.setText(R.string.mode_of_op_shizuku_root_backed_action);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action.setTooltipText(getString(R.string.mode_of_op_shizuku_root_backed_tooltip));
                }
                action.setOnClickListener(v -> switchShizukuToAdbMode());
            }
            return;
        }
        ShizukuBridge.OemCompatibilityWarning oemWarning =
                ShizukuBridge.getOemCompatibilityWarning(requireContext());
        if (oemWarning != null) {
            if (hint != null) {
                hint.setVisibility(View.VISIBLE);
                hint.setText(getString(oemWarning.bannerTextRes, oemWarning.fallbackVersion));
            }
            if (action != null) {
                action.setVisibility(View.VISIBLE);
                action.setText(R.string.shizuku_oem_downgrade_action);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action.setTooltipText(null);
                }
                action.setOnClickListener(v -> openShizukuPinnedArchive());
            }
            return;
        }
        boolean show = ShizukuBridge.shouldOfferTrustedWlanAutoStart(requireContext());
        if (hint != null) {
            hint.setVisibility(show ? View.VISIBLE : View.GONE);
            hint.setText(R.string.mode_of_op_shizuku_autostart_hint);
        }
        if (action != null) {
            action.setVisibility(show ? View.VISIBLE : View.GONE);
            action.setText(R.string.shizuku_autostart_configure);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action.setTooltipText(null);
            }
            action.setOnClickListener(v -> openShizukuAutoStartSettings());
        }
    }

    private void switchShizukuToAdbMode() {
        String nextMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Utils.isTv(requireContext())
                && AdbUtils.isWifiConnected(requireContext())
                ? Ops.MODE_ADB_WIFI
                : Ops.MODE_ADB_OVER_TCP;
        beginModeApply(nextMode);
    }

    private void openShizukuAutoStartSettings() {
        Intent intent = ShizukuBridge.getTrustedWlanAutoStartIntent(requireContext());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            UIUtils.displayShortToast(R.string.shizuku_autostart_open_failed);
        }
    }

    private void openShizukuPinnedArchive() {
        try {
            startActivity(ShizukuBridge.getPinnedSafeManagerArchiveIntent());
        } catch (ActivityNotFoundException | SecurityException e) {
            UIUtils.displayShortToast(R.string.shizuku_oem_archive_open_failed);
        }
    }

    private boolean isUsbDebuggingEnabled() {
        try {
            return android.provider.Settings.Global.getInt(
                    requireContext().getContentResolver(), "adb_enabled", 0) != 0;
        } catch (Exception t) {
            return false;
        }
    }

    private boolean isWirelessDebuggingActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        try {
            return android.provider.Settings.Global.getInt(
                    requireContext().getContentResolver(), "adb_wifi_enabled", 0) != 0;
        } catch (Exception t) {
            return false;
        }
    }

    private int getWirelessDebuggingStatusText() {
        if (isWirelessDebuggingActive()) {
            return R.string.mode_of_op_capability_status_active;
        }
        if (ServerConfig.hasPairedAdbDevice()) {
            return R.string.mode_of_op_capability_status_paired;
        }
        return R.string.mode_of_op_capability_status_inactive;
    }

    private static boolean requireRemoteServer(@NonNull String mode) {
        return Ops.MODE_ADB_OVER_TCP.equals(mode) || Ops.MODE_ADB_WIFI.equals(mode);
    }

    private static boolean requireRemoteServices(@NonNull String mode) {
        return !Ops.MODE_AUTO.equals(mode) && !Ops.MODE_NO_ROOT.equals(mode);
    }

    private static boolean badInferredMode(@NonNull String mode, int uid) {
        switch (mode) {
            case Ops.MODE_ROOT:
                return uid != Ops.ROOT_UID;
            case Ops.MODE_SHIZUKU:
                return uid != Ops.ROOT_UID && uid != Ops.SYSTEM_UID && uid != Ops.SHELL_UID;
            case Ops.MODE_ADB_OVER_TCP:
            case Ops.MODE_ADB_WIFI:
                return uid > Ops.SHELL_UID;
            default:
                return false;
        }
    }
}
