// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.widget.MaterialSpinner;

public class BroadcastSendDialogFragment extends DialogFragment {
    public static final String TAG = BroadcastSendDialogFragment.class.getSimpleName();

    private static final String ARG_PACKAGE_NAME = "pkg";
    private static final String ARG_RECEIVER_NAME = "receiver";
    private static final String ARG_RECEIVER_EXPORTED = "exported";
    private static final String ARG_RECEIVER_PERMISSION = "permission";
    private static final String ARG_USER_ID = "user_id";
    private static final String ARG_ACTIONS = "actions";
    private static final String ARG_CATEGORIES = "categories";

    public static void show(@NonNull FragmentManager fragmentManager, @NonNull String packageName,
                            @NonNull String receiverName, int userId, boolean receiverExported,
                            @Nullable String receiverPermission, @NonNull List<String> actions,
                            @NonNull List<String> categories) {
        BroadcastSendDialogFragment dialog = new BroadcastSendDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, packageName);
        args.putString(ARG_RECEIVER_NAME, receiverName);
        args.putBoolean(ARG_RECEIVER_EXPORTED, receiverExported);
        args.putString(ARG_RECEIVER_PERMISSION, receiverPermission);
        args.putInt(ARG_USER_ID, userId);
        args.putStringArrayList(ARG_ACTIONS, new ArrayList<>(actions));
        args.putStringArrayList(ARG_CATEGORIES, new ArrayList<>(categories));
        dialog.setArguments(args);
        dialog.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        Bundle args = requireArguments();
        String packageName = args.getString(ARG_PACKAGE_NAME);
        String receiverName = args.getString(ARG_RECEIVER_NAME);
        boolean receiverExported = args.getBoolean(ARG_RECEIVER_EXPORTED);
        String receiverPermission = args.getString(ARG_RECEIVER_PERMISSION);
        int userId = args.getInt(ARG_USER_ID);
        ArrayList<String> declaredActions = args.getStringArrayList(ARG_ACTIONS);
        ArrayList<String> declaredCategories = args.getStringArrayList(ARG_CATEGORIES);
        if (packageName == null || receiverName == null) {
            throw new IllegalArgumentException("Missing receiver target");
        }
        if (declaredActions == null) {
            declaredActions = new ArrayList<>();
        }
        if (declaredCategories == null) {
            declaredCategories = new ArrayList<>();
        }

        View view = View.inflate(context, R.layout.dialog_receiver_broadcast, null);
        TextView warningView = view.findViewById(R.id.broadcast_warning);
        MaterialSpinner actionSpinner = view.findViewById(R.id.action_selector_spinner);
        TextInputLayout customActionLayout = view.findViewById(R.id.custom_action_layout);
        TextInputEditText customActionInput = view.findViewById(R.id.custom_action);
        TextInputLayout categoriesLayout = view.findViewById(R.id.categories_layout);
        TextInputEditText categoriesInput = view.findViewById(R.id.categories);
        TextInputEditText extrasInput = view.findViewById(R.id.extras);
        TextView summaryView = view.findViewById(R.id.broadcast_summary);

        warningView.setText(R.string.receiver_broadcast_execution_warning);
        if (!declaredCategories.isEmpty()) {
            categoriesLayout.setHelperText(getString(R.string.receiver_broadcast_declared_categories,
                    TextUtils.join(", ", declaredCategories)));
        }

        ArrayList<String> actionChoices = new ArrayList<>(declaredActions);
        String customActionChoice = getString(R.string.receiver_broadcast_custom_action);
        actionChoices.add(customActionChoice);
        int initialSelection = 0;
        final int[] selectedActionIndex = {initialSelection};
        ArrayAdapter<String> adapter = new SelectedArrayAdapter<>(context,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, actionChoices);
        actionSpinner.setAdapter(adapter);
        actionSpinner.setSelection(initialSelection);
        customActionLayout.setVisibility(declaredActions.isEmpty() ? View.VISIBLE : View.GONE);
        actionSpinner.setOnItemClickListener((parent, itemView, position, id) -> {
            selectedActionIndex[0] = position;
            customActionLayout.setVisibility(position == actionChoices.size() - 1 ? View.VISIBLE : View.GONE);
            updateSummary(summaryView, receiverName, userId, receiverExported,
                    resolveAction(actionChoices, selectedActionIndex[0], customActionInput));
        });
        customActionInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSummary(summaryView, receiverName, userId, receiverExported,
                        resolveAction(actionChoices, selectedActionIndex[0], customActionInput));
            }
        });
        updateSummary(summaryView, receiverName, userId, receiverExported,
                resolveAction(actionChoices, selectedActionIndex[0], customActionInput));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.receiver_broadcast_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.send, null)
                .create();
        ArrayList<String> finalActionChoices = actionChoices;
        ArrayList<String> finalDeclaredCategories = declaredCategories;
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                handleSend(dialog, packageName, receiverName, receiverExported, receiverPermission, userId,
                        finalActionChoices, selectedActionIndex[0], customActionInput, categoriesInput,
                        extrasInput, finalDeclaredCategories)));
        return dialog;
    }

    private void handleSend(@NonNull AlertDialog formDialog, @NonNull String packageName, @NonNull String receiverName,
                            boolean receiverExported, @Nullable String receiverPermission, int userId,
                            @NonNull List<String> actionChoices, int selectedActionIndex,
                            @NonNull TextInputEditText customActionInput, @NonNull TextInputEditText categoriesInput,
                            @NonNull TextInputEditText extrasInput, @NonNull List<String> declaredCategories) {
        String action = resolveAction(actionChoices, selectedActionIndex, customActionInput);
        if (TextUtils.isEmpty(action)) {
            UIUtils.displayLongToast(R.string.receiver_broadcast_action_required);
            return;
        }
        List<String> categories = ReceiverBroadcastUtils.parseCategories(categoriesInput.getText());
        Bundle extras;
        try {
            extras = ReceiverBroadcastUtils.parseExtras(extrasInput.getText());
        } catch (IllegalArgumentException e) {
            UIUtils.displayLongToast(e.getMessage());
            return;
        }
        int currentUserId = UserHandleHidden.myUserId();
        boolean needsPrivilegedDispatch = ReceiverBroadcastUtils.needsPrivilegedDispatch(action, receiverExported,
                userId, currentUserId);
        if (needsPrivilegedDispatch && !SelfPermissions.isSystemOrRootOrShell()) {
            UIUtils.displayLongToast(R.string.receiver_broadcast_privileged_required);
            return;
        }
        Intent intent = ReceiverBroadcastUtils.buildBroadcastIntent(packageName, receiverName, action, categories,
                extras, true);
        String route = getString(needsPrivilegedDispatch
                ? R.string.receiver_broadcast_route_privileged
                : R.string.receiver_broadcast_route_unprivileged);
        String permission = receiverPermission == null ? getString(R.string.require_no_permission) : receiverPermission;
        String declaredCategoriesSummary = declaredCategories.isEmpty()
                ? getString(R.string.receiver_broadcast_no_declared_categories)
                : TextUtils.join(", ", declaredCategories);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_confirm_execution)
                .setMessage(getString(R.string.receiver_broadcast_confirm_message, receiverName, action, userId,
                        route, permission, declaredCategoriesSummary, categories.size(), extras.size()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.send, (dialogInterface, which) -> {
                    formDialog.dismiss();
                    dispatchBroadcast(requireContext().getApplicationContext(), intent, userId, needsPrivilegedDispatch);
                })
                .show();
    }

    private void dispatchBroadcast(@NonNull Context context, @NonNull Intent intent, int userId, boolean privileged) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                if (privileged) {
                    ActivityManagerCompat.sendBroadcast(intent, userId);
                } else {
                    context.sendBroadcast(intent);
                }
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.receiver_broadcast_sent));
            } catch (Throwable e) {
                String errorMessage = context.getString(R.string.receiver_broadcast_failed, getErrorMessage(e));
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(errorMessage));
            }
        });
    }

    @NonNull
    private static String resolveAction(@NonNull List<String> actionChoices, int selectedActionIndex,
                                        @NonNull TextInputEditText customActionInput) {
        if (selectedActionIndex >= actionChoices.size() - 1) {
            Editable editable = customActionInput.getText();
            return editable == null ? "" : editable.toString().trim();
        }
        return actionChoices.get(selectedActionIndex).trim();
    }

    private void updateSummary(@NonNull TextView summaryView, @NonNull String receiverName, int userId,
                               boolean receiverExported, @NonNull String action) {
        boolean privileged = ReceiverBroadcastUtils.needsPrivilegedDispatch(action, receiverExported, userId,
                UserHandleHidden.myUserId());
        summaryView.setText(getString(R.string.receiver_broadcast_summary, receiverName,
                TextUtils.isEmpty(action) ? getString(R.string.receiver_broadcast_no_action) : action, userId,
                getString(privileged
                        ? R.string.receiver_broadcast_route_privileged
                        : R.string.receiver_broadcast_route_unprivileged)));
    }

    @NonNull
    private static String getErrorMessage(@NonNull Throwable throwable) {
        String message = throwable.getLocalizedMessage();
        return TextUtils.isEmpty(message) ? throwable.getClass().getSimpleName() : message;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
