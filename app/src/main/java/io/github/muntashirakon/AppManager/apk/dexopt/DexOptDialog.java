// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt;

import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchDexOptOptions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;

public class DexOptDialog extends DialogFragment {
    public static final String TAG = DexOptDialog.class.getSimpleName();

    private static final String ARG_PACKAGES = "pkg";

    @NonNull
    public static DexOptDialog getInstance(@Nullable String[] packages) {
        DexOptDialog dialog = new DexOptDialog();
        Bundle args = new Bundle();
        args.putStringArray(ARG_PACKAGES, packages);
        dialog.setArguments(args);
        return dialog;
    }

    private static final List<String> COMPILER_FILTERS = new ArrayList<String>() {{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            add("verify-none"); // = assume-verified
            add("verify-at-runtime"); // = extract
            add("verify-profile"); // = verify
            add("interpret-only"); // = quicken
            add("time"); // = space
            add("balanced"); // speed
        } else {
            add("assume-verified");
            add("extract");
            add("verify");
            add("quicken");
        }
        add("space");
        add("space-profile");
        add("speed");
        add("speed-profile");
        add("everything");
        add("everything-profile");
    }};

    private final DexOptOptions mOptions = DexOptOptions.getDefault();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mOptions.packages = requireArguments().getStringArray(ARG_PACKAGES);
        int uid = Users.getSelfOrRemoteUid();
        boolean isRootOrSystem = uid == Ops.SYSTEM_UID || uid == Ops.ROOT_UID;
        // Inflate view
        View view = View.inflate(requireContext(), R.layout.dialog_dexopt, null);
        String[] packages = mOptions.packages;
        MaterialTextView scopeView = view.findViewById(R.id.optimization_scope);
        TextInputLayout compilerFilterLayout = view.findViewById(R.id.compiler_filter_layout);
        AutoCompleteTextView compilerFilterSelectionView = view.findViewById(R.id.compiler_filter);
        MaterialCheckBox compileLayoutsCheck = view.findViewById(R.id.compile_layouts);
        MaterialTextView compileLayoutsSummary = view.findViewById(R.id.compile_layouts_summary);
        MaterialCheckBox clearProfileDataCheck = view.findViewById(R.id.clear_profile_data);
        MaterialTextView clearProfileDataSummary = view.findViewById(R.id.clear_profile_data_summary);
        MaterialCheckBox checkProfilesCheck = view.findViewById(R.id.check_profiles);
        MaterialCheckBox forceCompilationCheck = view.findViewById(R.id.force_compilation);
        MaterialCheckBox forceDexOptCheck = view.findViewById(R.id.force_dexopt);
        MaterialTextView forceDexOptSummary = view.findViewById(R.id.force_dexopt_summary);
        if (packages == null) {
            scopeView.setText(R.string.dexopt_scope_all);
        } else {
            scopeView.setText(getResources().getQuantityString(R.plurals.dexopt_scope_selected,
                    packages.length, packages.length));
        }
        compilerFilterSelectionView.setText(mOptions.compilerFiler);
        checkProfilesCheck.setChecked(mOptions.checkProfiles);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Compile layout options was introduced in Android 10 and removed in Android 12
            compileLayoutsCheck.setVisibility(View.GONE);
            compileLayoutsSummary.setVisibility(View.GONE);
        }
        if (!isRootOrSystem) {
            // clearProfileData and forceDexOpt can only be run as root/system
            clearProfileDataCheck.setEnabled(false);
            clearProfileDataSummary.setText(R.string.dexopt_root_only_summary);
            forceDexOptCheck.setEnabled(false);
            forceDexOptSummary.setText(R.string.dexopt_root_only_summary);
        }

        // Set listeners
        compilerFilterSelectionView.setAdapter(new AnyFilterArrayAdapter<>(requireContext(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item,
                COMPILER_FILTERS));
        compilerFilterSelectionView.setOnItemClickListener((parent, itemView, position, id) -> compilerFilterLayout.setError(null));
        compileLayoutsCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.compileLayouts = isChecked);
        clearProfileDataCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.clearProfileData = isChecked);
        checkProfilesCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.checkProfiles = isChecked);
        forceCompilationCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.forceCompilation = isChecked);
        forceDexOptCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.forceDexOpt = isChecked);
        if (isRootOrSystem) {
            forceDexOptCheck.setChecked(true);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_perform_runtime_optimization_to_apps)
                .setView(view)
                .setPositiveButton(R.string.action_run, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.dexopt_run_defaults, null)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (prepareOptionsFromInput(compilerFilterSelectionView, compilerFilterLayout)) {
                    dialog.dismiss();
                    launchOp();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                mOptions.compilerFiler = DexOptOptions.getDefaultCompilerFilterForInstallation();
                mOptions.forceCompilation = true;
                mOptions.clearProfileData = isRootOrSystem;
                dialog.dismiss();
                launchOp();
            });
        });
        return dialog;
    }

    private boolean prepareOptionsFromInput(@NonNull AutoCompleteTextView compilerFilterSelectionView,
                                            @NonNull TextInputLayout compilerFilterLayout) {
        Editable compilerFilterRaw = compilerFilterSelectionView.getText();
        if (TextUtils.isEmpty(compilerFilterRaw)) {
            compilerFilterLayout.setError(getString(R.string.dexopt_compiler_filter_required));
            return false;
        }
        String compilerFilter = compilerFilterRaw.toString().trim();
        if (!COMPILER_FILTERS.contains(compilerFilter)) {
            compilerFilterLayout.setError(getString(R.string.dexopt_compiler_filter_invalid));
            return false;
        }
        compilerFilterLayout.setError(null);
        mOptions.compilerFiler = compilerFilter;
        return true;
    }

    private void launchOp() {
        BatchDexOptOptions options = new BatchDexOptOptions(mOptions);
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(
                BatchOpsManager.OP_DEXOPT, null, null, options);
        Intent intent = BatchOpsService.getServiceIntent(requireContext(), queueItem);
        ContextCompat.startForegroundService(requireContext(), intent);
    }
}
