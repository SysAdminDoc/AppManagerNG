// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupPathExclusionPatterns;
import io.github.muntashirakon.AppManager.profiles.struct.AppsBaseProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.profiles.trigger.ProfileTrigger;
import io.github.muntashirakon.AppManager.profiles.trigger.ProfileTriggerStore;
import io.github.muntashirakon.AppManager.profiles.trigger.RoutineScheduler;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.self.SelfBatteryOptimization;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

public class ConfPreferences extends PreferenceFragmentCompat {
    private AppsBaseProfileActivity mActivity;
    private AppsProfileViewModel mModel;

    @BaseProfile.ProfileState
    private final List<String> mStates = Arrays.asList(BaseProfile.STATE_ON, BaseProfile.STATE_OFF);
    @Nullable
    private String[] mComponents;
    @Nullable
    private String[] mAppOps;
    @Nullable
    private String[] mPermissions;
    @Nullable
    private AppsBaseProfile.BackupInfo mBackupInfo;
    private ProfileTriggerStore mTriggerStore;
    private Preference mRoutineTriggersPref;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // https://github.com/androidx/androidx/blob/androidx-main/preference/preference/res/layout/preference_recyclerview.xml
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setFitsSystemWindows(true);
        recyclerView.setClipToPadding(false);
        UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_profile_config, rootKey);
        getPreferenceManager().setPreferenceDataStore(new ConfDataStore());
        mActivity = (AppsBaseProfileActivity) requireActivity();
        if (mActivity.model == null) {
            // ViewModel should never be null.
            // If it's null, it means that we're on the wrong Fragment
            return;
        }
        mModel = mActivity.model;
        mTriggerStore = new ProfileTriggerStore(mActivity);
        // Set profile ID
        Preference profileIdPref = Objects.requireNonNull(findPreference("profile_id"));
        profileIdPref.setSummary(mModel.getProfileId());
        profileIdPref.setOnPreferenceClickListener(preference -> {
            Utils.copyToClipboard(mActivity, ProfilesActivity.formatProfileMetadataLabel(mModel.getProfileName()),
                    mModel.getProfileId());
            return true;
        });
        // Set comment
        Preference commentPref = Objects.requireNonNull(findPreference("comment"));
        commentPref.setSummary(mModel.getComment());
        commentPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.comment)
                    .setTitle(R.string.comment)
                    .setInputText(mModel.getComment())
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        mModel.setComment(TextUtils.isEmpty(inputText) ? null : inputText.toString());
                        commentPref.setSummary(mModel.getComment());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Set state
        Preference statePref = Objects.requireNonNull(findPreference("state"));
        final String[] statesL = new String[]{
                getString(R.string.on),
                getString(R.string.off)
        };
        statePref.setTitle(getString(R.string.process_state, statesL[mStates.indexOf(mModel.getState())]));
        statePref.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(mActivity, mStates, statesL)
                    .setTitle(R.string.profile_state)
                    .setSelection(mModel.getState())
                    .setOnSingleChoiceClickListener((dialog, which, item, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        mModel.setState(mStates.get(which));
                        statePref.setTitle(getString(R.string.process_state, statesL[which]));
                        dialog.dismiss();
                    })
                    .show();
            return true;
        });
        // Set routine triggers
        mRoutineTriggersPref = Objects.requireNonNull(findPreference("routine_triggers"));
        updateRoutineTriggersPref();
        mRoutineTriggersPref.setOnPreferenceClickListener(preference -> {
            showRoutineTriggersDialog();
            refreshRoutineTriggerDiagnostics();
            return true;
        });
        refreshRoutineTriggerDiagnostics();
        // Set users
        Preference usersPref = Objects.requireNonNull(findPreference("users"));
        handleUsersPref(usersPref);
        // Set components
        Preference componentsPref = Objects.requireNonNull(findPreference("components"));
        updateComponentsPref(componentsPref);
        componentsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.input_signatures)
                    .setTitle(R.string.components)
                    .setInputText(mComponents == null ? "" : TextUtils.join(" ", mComponents))
                    .setHelperText(R.string.input_signatures_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newComponents = inputText.toString().split("\\s+");
                            mModel.setComponents(newComponents);
                        } else mModel.setComponents(null);
                        updateComponentsPref(componentsPref);
                    })
                    .setNeutralButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        mModel.setComponents(null);
                        updateComponentsPref(componentsPref);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Set app ops
        Preference appOpsPref = Objects.requireNonNull(findPreference("app_ops"));
        updateAppOpsPref(appOpsPref);
        appOpsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.input_app_ops)
                    .setTitle(R.string.app_ops)
                    .setInputText(mAppOps == null ? "" : TextUtils.join(" ", mAppOps))
                    .setHelperText(R.string.input_app_ops_description_profile)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newAppOps = inputText.toString().split("\\s+");
                            mModel.setAppOps(newAppOps);
                        } else mModel.setAppOps(null);
                        updateAppOpsPref(appOpsPref);
                    })
                    .setNeutralButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        mModel.setAppOps(null);
                        updateAppOpsPref(appOpsPref);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Set permissions
        Preference permissionsPref = Objects.requireNonNull(findPreference("permissions"));
        updatePermissionsPref(permissionsPref);
        permissionsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.input_permissions)
                    .setTitle(R.string.declared_permission)
                    .setInputText(mPermissions == null ? "" : TextUtils.join(" ", mPermissions))
                    .setHelperText(R.string.input_permissions_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newPermissions = inputText.toString().split("\\s+");
                            mModel.setPermissions(newPermissions);
                        } else mModel.setPermissions(null);
                        updatePermissionsPref(permissionsPref);
                    })
                    .setNeutralButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        mModel.setPermissions(null);
                        updatePermissionsPref(permissionsPref);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        Preference backupDataPref = Objects.requireNonNull(findPreference("backup_data"));
        mBackupInfo = mModel.getBackupInfo();
        backupDataPref.setSummary(mBackupInfo != null ? R.string.enabled : R.string.disabled_app);
        backupDataPref.setOnPreferenceClickListener(preference -> {
            View view = View.inflate(mActivity, R.layout.dialog_profile_backup_restore, null);
            final BackupFlags flags;
            if (mBackupInfo != null) {
                flags = new BackupFlags(mBackupInfo.flags);
            } else flags = BackupFlags.fromPref();
            final AtomicInteger backupFlags = new AtomicInteger(flags.getFlags());
            final AtomicReference<String[]> exclusionGlobs = new AtomicReference<>(
                    mBackupInfo != null ? BackupPathExclusionPatterns.sanitize(mBackupInfo.exclusionGlobs)
                            : new String[0]);
            view.findViewById(R.id.dialog_button).setOnClickListener(v -> {
                List<Integer> supportedBackupFlags = BackupFlags.getSupportedBackupFlagsAsArray();
                new SearchableMultiChoiceDialogBuilder<>(requireActivity(), supportedBackupFlags,
                        BackupFlags.getFormattedFlagNames(requireContext(), supportedBackupFlags))
                        .setTitle(R.string.backup_options)
                        .addSelections(flags.flagsToCheckedIndexes(supportedBackupFlags))
                        .hideSearchBar(true)
                        .showSelectAll(false)
                        .setPositiveButton(R.string.save, (dialog, which, selectedItems) -> {
                            int flagsInt = 0;
                            for (int flag : selectedItems) {
                                flagsInt |= flag;
                            }
                            flags.setFlags(flagsInt);
                            backupFlags.set(flags.getFlags());
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
            view.findViewById(R.id.dialog_exclusions_button).setOnClickListener(v ->
                    new TextInputDialogBuilder(mActivity, R.string.backup_exclusion_patterns)
                            .setTitle(R.string.backup_exclusion_patterns)
                            .setInputText(TextUtils.join("\n", exclusionGlobs.get()))
                            .setInputTypeface(Typeface.MONOSPACE)
                            .setInputInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                            .setHelperText(R.string.backup_exclusion_patterns_profile_helper)
                            .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) ->
                                    exclusionGlobs.set(BackupPathExclusionPatterns.parse(inputText)))
                            .setNeutralButton(R.string.clear, (dialog, which, inputText, isChecked) ->
                                    exclusionGlobs.set(new String[0]))
                            .setNegativeButton(R.string.cancel, null)
                            .show());
            final TextInputEditText editText = view.findViewById(android.R.id.input);
            if (mBackupInfo != null) {
                editText.setText(mBackupInfo.name);
            }
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.backup_restore)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        if (mBackupInfo == null) {
                            mBackupInfo = new AppsBaseProfile.BackupInfo();
                        }
                        CharSequence backupName = editText.getText();
                        BackupFlags backupFlags1 = new BackupFlags(backupFlags.get());
                        if (!TextUtils.isEmpty(backupName)) {
                            backupFlags1.addFlag(BackupFlags.BACKUP_MULTIPLE);
                            mBackupInfo.name = backupName.toString();
                        } else {
                            backupFlags1.removeFlag(BackupFlags.BACKUP_MULTIPLE);
                            mBackupInfo.name = null;
                        }
                        mBackupInfo.flags = backupFlags1.getFlags();
                        mBackupInfo.exclusionGlobs = exclusionGlobs.get();
                        mModel.setBackupInfo(mBackupInfo);
                        backupDataPref.setSummary(R.string.enabled);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which) -> {
                        mModel.setBackupInfo(mBackupInfo = null);
                        backupDataPref.setSummary(R.string.disabled_app);
                    })
                    .show();
            return true;
        });
        // Set export rules
        Preference exportRulesPref = Objects.requireNonNull(findPreference("export_rules"));
        int rulesCount = RulesTypeSelectionDialogFragment.RULE_TYPES.length;
        List<Integer> checkedItems = new ArrayList<>(rulesCount);
        List<Integer> selectedRules = updateExportRulesPref(exportRulesPref);
        for (int i = 0; i < rulesCount; ++i) checkedItems.add(1 << i);
        exportRulesPref.setOnPreferenceClickListener(preference -> {
            new SearchableMultiChoiceDialogBuilder<>(mActivity, checkedItems, R.array.rule_types)
                    .setTitle(R.string.options)
                    .hideSearchBar(true)
                    .addSelections(selectedRules)
                    .setPositiveButton(R.string.ok, (dialog, which, selectedItems) -> {
                        int value = 0;
                        for (int item : selectedItems) value |= item;
                        if (value != 0) {
                            mModel.setExportRules(value);
                        } else mModel.setExportRules(null);
                        selectedRules.clear();
                        selectedRules.addAll(updateExportRulesPref(exportRulesPref));
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, selectedItems) -> {
                        mModel.setExportRules(null);
                        selectedRules.clear();
                        selectedRules.addAll(updateExportRulesPref(exportRulesPref));
                    })
                    .show();
            return true;
        });
        // Set others
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("freeze")))
                .setChecked(mModel.getBoolean("freeze", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("force_stop")))
                .setChecked(mModel.getBoolean("force_stop", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("clear_cache")))
                .setChecked(mModel.getBoolean("clear_cache", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("clear_data")))
                .setChecked(mModel.getBoolean("clear_data", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("block_trackers")))
                .setChecked(mModel.getBoolean("block_trackers", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("save_apk")))
                .setChecked(mModel.getBoolean("save_apk", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("allow_routine")))
                .setChecked(mModel.getBoolean("allow_routine", false));
    }

    @NonNull
    private List<Integer> updateExportRulesPref(Preference pref) {
        Integer rules = mModel.getExportRules();
        List<Integer> selectedRules = new ArrayList<>();
        if (rules == null || rules == 0) pref.setSummary(R.string.disabled_app);
        else {
            List<String> selectedRulesStr = new ArrayList<>();
            int i = 0;
            while (rules != 0) {
                int flag = (rules & (~(1 << i)));
                if (flag != rules) {
                    selectedRulesStr.add(RulesTypeSelectionDialogFragment.RULE_TYPES[i].toString());
                    rules = flag;
                    selectedRules.add(1 << i);
                }
                ++i;
            }
            pref.setSummary(TextUtils.join(", ", selectedRulesStr));
        }
        return selectedRules;
    }

    private void updateComponentsPref(Preference pref) {
        mComponents = mModel.getComponents();
        if (mComponents == null || mComponents.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", mComponents));
        }
    }

    private void updateAppOpsPref(Preference pref) {
        mAppOps = mModel.getAppOpsStr();
        if (mAppOps == null || mAppOps.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", mAppOps));
        }
    }

    private void updatePermissionsPref(Preference pref) {
        mPermissions = mModel.getPermissions();
        if (mPermissions == null || mPermissions.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", mPermissions));
        }
    }

    private void updateRoutineTriggersPref() {
        if (mRoutineTriggersPref == null || mTriggerStore == null) {
            return;
        }
        String profileId = mModel.getProfileId();
        if (profileId == null) {
            mRoutineTriggersPref.setSummary(R.string.profile_routine_triggers_summary_none);
            return;
        }
        List<ProfileTrigger> triggers = mTriggerStore.forProfile(profileId);
        if (triggers.isEmpty()) {
            mRoutineTriggersPref.setSummary(R.string.profile_routine_triggers_summary_none);
            return;
        }
        int enabled = 0;
        for (ProfileTrigger trigger : triggers) {
            if (trigger.enabled) ++enabled;
        }
        mRoutineTriggersPref.setSummary(getResources().getQuantityString(
                R.plurals.profile_routine_triggers_summary, triggers.size(), triggers.size(), enabled));
    }

    private void refreshRoutineTriggerDiagnostics() {
        if (mTriggerStore == null || mModel == null || mActivity == null) {
            return;
        }
        String profileId = mModel.getProfileId();
        if (profileId == null) {
            return;
        }
        List<ProfileTrigger> triggers = mTriggerStore.forProfile(profileId);
        if (triggers.isEmpty()) {
            return;
        }
        Context appContext = mActivity.getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            for (ProfileTrigger trigger : triggers) {
                RoutineScheduler.refreshDiagnostics(appContext, trigger);
            }
            ThreadUtils.postOnMainThread(() -> {
                if (isAdded()) {
                    updateRoutineTriggersPref();
                }
            });
        });
    }

    private void showRoutineTriggersDialog() {
        if (mTriggerStore == null) {
            return;
        }
        String profileId = mModel.getProfileId();
        if (profileId == null) {
            return;
        }
        List<ProfileTrigger> triggers = mTriggerStore.forProfile(profileId);
        CharSequence[] items = new CharSequence[triggers.size() + 1];
        for (int i = 0; i < triggers.size(); ++i) {
            ProfileTrigger trigger = triggers.get(i);
            items[i] = RoutineScheduler.formatTriggerTitle(mActivity, trigger) + "\n"
                    + RoutineScheduler.formatTriggerSummary(mActivity, trigger);
        }
        items[items.length - 1] = getString(R.string.profile_trigger_add);
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.profile_routine_triggers)
                .setItems(items, (dialog, which) -> {
                    if (which == triggers.size()) {
                        showAddRoutineTriggerDialog(profileId);
                    } else {
                        showRoutineTriggerActions(triggers.get(which));
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showRoutineTriggerActions(@NonNull ProfileTrigger trigger) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(RoutineScheduler.formatTriggerTitle(mActivity, trigger))
                .setMessage(RoutineScheduler.formatTriggerSummary(mActivity, trigger))
                .setPositiveButton(trigger.enabled ? R.string.disable : R.string.enable, (dialog, which) -> {
                    boolean enabling = !trigger.enabled;
                    ProfileTrigger updated = mTriggerStore.setEnabled(trigger.id, !trigger.enabled);
                    if (updated != null) {
                        RoutineScheduler.scheduleOrCancel(mActivity, updated);
                        refreshRoutineTriggerDiagnostics();
                        if (enabling) {
                            ensureRoutineTriggerBatteryExemption();
                        }
                    }
                    updateRoutineTriggersPref();
                })
                .setNegativeButton(R.string.delete, (dialog, which) -> {
                    RoutineScheduler.cancel(mActivity, trigger);
                    mTriggerStore.remove(trigger.id);
                    RoutineScheduler.clearStoredState(mActivity, trigger.id);
                    updateRoutineTriggersPref();
                });
        if (shouldOfferRoutineTriggerBatteryOptimization()) {
            builder.setNeutralButton(R.string.profile_trigger_battery_optimization_fix,
                    (dialog, which) -> ensureRoutineTriggerBatteryExemption());
        } else {
            builder.setNeutralButton(R.string.close, null);
        }
        builder.show();
    }

    private void showAddRoutineTriggerDialog(@NonNull String profileId) {
        final int[] triggerTypes = {
                ProfileTrigger.TYPE_TIME_OF_DAY,
                ProfileTrigger.TYPE_ON_CHARGING,
                ProfileTrigger.TYPE_ON_NETWORK_WIFI,
                ProfileTrigger.TYPE_ON_NETWORK_ANY,
                ProfileTrigger.TYPE_ON_BOOT
        };
        CharSequence[] labels = {
                getString(R.string.profile_trigger_type_time_of_day),
                getString(R.string.profile_trigger_on_charging),
                getString(R.string.profile_trigger_on_network_wifi),
                getString(R.string.profile_trigger_on_network_any),
                getString(R.string.profile_trigger_on_boot)
        };
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.profile_trigger_add)
                .setItems(labels, (dialog, which) -> {
                    int type = triggerTypes[which];
                    if (type == ProfileTrigger.TYPE_TIME_OF_DAY) {
                        showTimeOfDayTriggerDialog(profileId);
                    } else {
                        addRoutineTrigger(new ProfileTrigger.Builder(profileId, type).build());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTimeOfDayTriggerDialog(@NonNull String profileId) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(mActivity, (view, hourOfDay, minute) -> addRoutineTrigger(
                new ProfileTrigger.Builder(profileId, ProfileTrigger.TYPE_TIME_OF_DAY)
                        .timeOfDay(hourOfDay, minute)
                        .build()),
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(mActivity))
                .show();
    }

    private void addRoutineTrigger(@NonNull ProfileTrigger trigger) {
        mTriggerStore.put(trigger);
        RoutineScheduler.scheduleOrCancel(mActivity, trigger);
        updateRoutineTriggersPref();
        refreshRoutineTriggerDiagnostics();
        if (trigger.enabled) {
            ensureRoutineTriggerBatteryExemption();
        }
    }

    private boolean shouldOfferRoutineTriggerBatteryOptimization() {
        return mActivity != null
                && SelfBatteryOptimization.isSupported()
                && !SelfBatteryOptimization.isExempt(mActivity);
    }

    private void ensureRoutineTriggerBatteryExemption() {
        if (!shouldOfferRoutineTriggerBatteryOptimization()) {
            return;
        }
        if (SelfBatteryOptimization.canAutoFix()) {
            Context appContext = mActivity.getApplicationContext();
            ThreadUtils.postOnBackgroundThread(() -> {
                @SelfBatteryOptimization.AutoFixResult int result =
                        SelfBatteryOptimization.autoFixIfPossible(appContext);
                ThreadUtils.postOnMainThread(() -> {
                    if (!isAdded() || mActivity == null) return;
                    if (result == SelfBatteryOptimization.RESULT_FIXED
                            || result == SelfBatteryOptimization.RESULT_ALREADY_EXEMPT) {
                        UIUtils.displayShortToast(R.string.profile_trigger_battery_auto_fixed);
                        refreshRoutineTriggerDiagnostics();
                    } else {
                        showRoutineTriggerBatteryOptimizationPrompt(mActivity);
                    }
                });
            });
            return;
        }
        showRoutineTriggerBatteryOptimizationPrompt(mActivity);
    }

    private void showRoutineTriggerBatteryOptimizationPrompt(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.profile_trigger_battery_optimization_title)
                .setMessage(R.string.profile_trigger_battery_optimization_msg)
                .setPositiveButton(R.string.pref_backup_schedule_battery_optimization_open,
                        (dialog, which) -> launchRoutineTriggerBatteryOptimizationSystemFlow(context))
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    private void launchRoutineTriggerBatteryOptimizationSystemFlow(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            UIUtils.displayLongToast(getString(R.string.pref_battery_optimization_unsupported));
        }
    }

    private List<Integer> mSelectedUsers;

    private void handleUsersPref(Preference pref) {
        List<UserInfo> users = Users.getUsers();
        if (users.size() > 1) {
            pref.setVisible(true);
            CharSequence[] userNames = new String[users.size()];
            List<Integer> userHandles = new ArrayList<>(users.size());
            int i = 0;
            for (UserInfo info : users) {
                userNames[i] = info.toLocalizedString(requireContext());
                userHandles.add(info.id);
                ++i;
            }
            mSelectedUsers = new ArrayList<>();
            for (Integer user : mModel.getUsers()) {
                mSelectedUsers.add(user);
            }
            mActivity.runOnUiThread(() -> {
                pref.setSummary(TextUtilsCompat.joinSpannable(", ", getUserInfo(users, mSelectedUsers)));
                pref.setOnPreferenceClickListener(v -> {
                    new SearchableMultiChoiceDialogBuilder<>(mActivity, userHandles, userNames)
                            .setTitle(R.string.select_user)
                            .addSelections(mSelectedUsers)
                            .showSelectAll(false)
                            .setPositiveButton(R.string.ok, (dialog, which, selectedUserHandles) -> {
                                if (selectedUserHandles.isEmpty()) {
                                    mSelectedUsers = userHandles;
                                } else mSelectedUsers = selectedUserHandles;
                                pref.setSummary(TextUtilsCompat.joinSpannable(", ", getUserInfo(users, mSelectedUsers)));
                                mModel.setUsers(ArrayUtils.convertToIntArray(mSelectedUsers));
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                });
            });
        } else {
            mActivity.runOnUiThread(() -> pref.setVisible(false));
        }
    }

    @NonNull
    private List<CharSequence> getUserInfo(@NonNull List<UserInfo> userInfoList, @NonNull List<Integer> userHandles) {
        List<CharSequence> userInfoOut = new ArrayList<>();
        for (UserInfo info : userInfoList) {
            if (userHandles.contains(info.id)) {
                userInfoOut.add(info.toLocalizedString(requireContext()));
            }
        }
        return userInfoOut;
    }

    public class ConfDataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            mModel.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defValue) {
            return mModel.getBoolean(key, defValue);
        }
    }
}
