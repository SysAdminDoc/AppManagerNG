// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupTagPolicyStore;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.tags.AppTagStore;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

/** Settings editor and preview for ordered per-tag backup policies. */
public class BackupTagPoliciesPreferences extends PreferenceFragment {
    private BackupTagPolicyStore mStore;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        mStore = new BackupTagPolicyStore(requireContext());
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
        bindPolicies();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MotionUtils.applySharedAxisZTransition(this);
    }

    @Override
    public int getTitle() {
        return R.string.backup_tag_policies;
    }

    private void bindPolicies() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        Preference overview = new Preference(requireContext());
        overview.setSelectable(false);
        overview.setTitle(R.string.backup_tag_policies);
        overview.setSummary(R.string.backup_tag_policies_overview);
        screen.addPreference(overview);

        if (mStore.hasInvalidData()) {
            Preference warning = new Preference(requireContext());
            warning.setSelectable(false);
            warning.setTitle(R.string.backup_tag_policy_invalid_data_title);
            warning.setSummary(R.string.backup_tag_policy_invalid_data_summary);
            screen.addPreference(warning);
        }

        Preference add = new Preference(requireContext());
        add.setTitle(R.string.backup_tag_policy_add);
        add.setSummary(R.string.backup_tag_policy_add_summary);
        add.setOnPreferenceClickListener(preference -> {
            showTagStep(-1, null);
            return true;
        });
        screen.addPreference(add);

        Preference preview = new Preference(requireContext());
        preview.setTitle(R.string.backup_tag_policy_preview);
        preview.setSummary(R.string.backup_tag_policy_preview_summary);
        preview.setOnPreferenceClickListener(preference -> {
            showPreview();
            return true;
        });
        screen.addPreference(preview);

        List<BackupTagPolicyStore.Policy> policies = mStore.getPolicies();
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.backup_tag_policy_rules);
        screen.addPreference(category);
        if (policies.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setSummary(R.string.backup_tag_policy_empty);
            category.addPreference(empty);
            return;
        }
        Set<String> knownTags = new AppTagStore(requireContext()).getAllKnownTags();
        for (int i = 0; i < policies.size(); ++i) {
            int index = i;
            BackupTagPolicyStore.Policy policy = policies.get(i);
            Preference row = new Preference(requireContext());
            row.setTitle((i + 1) + ". #" + policy.tag);
            row.setSummary(describePolicy(policy, knownTags));
            row.setOnPreferenceClickListener(preference -> {
                showPolicyActions(index, policy, policies.size());
                return true;
            });
            category.addPreference(row);
        }
    }

    @NonNull
    private CharSequence describePolicy(@NonNull BackupTagPolicyStore.Policy policy,
                                        @NonNull Set<String> knownTags) {
        String unlimited = getString(R.string.backup_tag_policy_unlimited);
        String count = policy.maxCount == 0 ? unlimited : Integer.toString(policy.maxCount);
        String age = policy.maxAgeDays == 0 ? unlimited : Integer.toString(policy.maxAgeDays);
        String destination = policy.destination == null
                ? getString(R.string.backup_tag_policy_configured_destination) : policy.destination.toString();
        StringBuilder summary = new StringBuilder()
                .append(new BackupFlags(policy.flags).toLocalisedString(requireContext()))
                .append(" · ").append(cryptoLabel(policy.cryptoMode))
                .append(" · ").append(getString(R.string.backup_tag_policy_retention_summary, count, age))
                .append("\n").append(destination);
        if (!knownTags.contains(policy.tag)) {
            summary.append("\n").append(getString(R.string.backup_tag_policy_orphan_warning));
        }
        return summary;
    }

    private void showPolicyActions(int index, @NonNull BackupTagPolicyStore.Policy policy, int size) {
        List<String> actionLabels = new ArrayList<>();
        List<Integer> actionIds = new ArrayList<>();
        actionLabels.add(getString(R.string.backup_tag_policy_edit));
        actionIds.add(0);
        if (index > 0) {
            actionLabels.add(getString(R.string.backup_tag_policy_move_up));
            actionIds.add(1);
        }
        if (index + 1 < size) {
            actionLabels.add(getString(R.string.backup_tag_policy_move_down));
            actionIds.add(2);
        }
        actionLabels.add(getString(R.string.backup_tag_policy_delete));
        actionIds.add(3);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_tag_policy_actions)
                .setItems(actionLabels.toArray(new String[0]), (dialog, which) -> {
                    switch (actionIds.get(which)) {
                        case 0:
                            showTagStep(index, policy);
                            break;
                        case 1:
                            if (index > 0) mStore.move(index, index - 1);
                            bindPolicies();
                            break;
                        case 2:
                            if (index + 1 < size) mStore.move(index, index + 1);
                            bindPolicies();
                            break;
                        case 3:
                            confirmDelete(index, policy);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(int index, @NonNull BackupTagPolicyStore.Policy policy) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_tag_policy_delete)
                .setMessage(getString(R.string.backup_tag_policy_delete_confirm, policy.tag))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    mStore.remove(index);
                    bindPolicies();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTagStep(int index, @Nullable BackupTagPolicyStore.Policy policy) {
        new TextInputDialogBuilder(requireContext(), R.string.backup_tag_policy_tag_prompt)
                .setTitle(index >= 0 ? R.string.backup_tag_policy_edit : R.string.backup_tag_policy_add)
                .setInputText(policy != null ? policy.tag : "")
                .setHelperText(R.string.backup_tag_policy_tag_helper)
                .setPositiveButton(R.string.backup_tag_policy_continue, (dialog, which, input, checked) -> {
                    String tag = input != null ? input.toString() : "";
                    String normalized = AppTagStore.normalizeTag(tag);
                    if (normalized == null) {
                        UIUtils.displayShortToast(R.string.invalid_tag);
                        return;
                    }
                    Draft draft = policy != null ? new Draft(policy) : new Draft();
                    draft.tag = normalized;
                    showPartsStep(index, draft);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPartsStep(int index, @NonNull Draft draft) {
        List<Integer> flags = new ArrayList<>();
        for (int flag : BackupFlags.getSupportedBackupFlagsAsArray()) {
            if ((flag & BackupFlags.BACKUP_CONTENT_FLAGS) != 0) flags.add(flag);
        }
        boolean[] checked = new boolean[flags.size()];
        for (int i = 0; i < flags.size(); ++i) checked[i] = (draft.flags & flags.get(i)) != 0;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_tag_policy_parts)
                .setMultiChoiceItems(BackupFlags.getFormattedFlagNames(requireContext(), flags), checked,
                        (dialog, which, selected) -> checked[which] = selected)
                .setPositiveButton(R.string.backup_tag_policy_continue, (dialog, which) -> {
                    int selectedFlags = 0;
                    for (int i = 0; i < flags.size(); ++i) {
                        if (checked[i]) selectedFlags |= flags.get(i);
                    }
                    if (selectedFlags == 0) {
                        UIUtils.displayShortToast(R.string.backup_tag_policy_select_part);
                        return;
                    }
                    draft.flags = selectedFlags;
                    showEncryptionStep(index, draft);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEncryptionStep(int index, @NonNull Draft draft) {
        String[] modes = cryptoModes();
        CharSequence[] labels = new CharSequence[modes.length];
        int selected = 0;
        for (int i = 0; i < modes.length; ++i) {
            labels[i] = cryptoLabel(modes[i]);
            if (modes[i].equals(draft.cryptoMode)) selected = i;
        }
        final int[] choice = {selected};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_tag_policy_encryption)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> choice[0] = which)
                .setPositiveButton(R.string.backup_tag_policy_continue, (dialog, which) -> {
                    draft.cryptoMode = modes[choice[0]];
                    showRetentionCountStep(index, draft);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRetentionCountStep(int index, @NonNull Draft draft) {
        new TextInputDialogBuilder(requireContext(), R.string.backup_tag_policy_retention_count)
                .setTitle(R.string.backup_tag_policy_retention_count)
                .setInputText(Integer.toString(draft.maxCount))
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setHelperText(R.string.backup_tag_policy_retention_count_helper)
                .setPositiveButton(R.string.backup_tag_policy_continue, (dialog, which, input, checked) -> {
                    Integer value = parseNumber(input != null ? input.toString() : null);
                    if (value == null) return;
                    draft.maxCount = value;
                    showRetentionAgeStep(index, draft);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRetentionAgeStep(int index, @NonNull Draft draft) {
        new TextInputDialogBuilder(requireContext(), R.string.backup_tag_policy_retention_age)
                .setTitle(R.string.backup_tag_policy_retention_age)
                .setInputText(Integer.toString(draft.maxAgeDays))
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setHelperText(R.string.backup_tag_policy_retention_age_helper)
                .setPositiveButton(R.string.backup_tag_policy_continue, (dialog, which, input, checked) -> {
                    Integer value = parseNumber(input != null ? input.toString() : null);
                    if (value == null) return;
                    draft.maxAgeDays = value;
                    showDestinationStep(index, draft);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDestinationStep(int index, @NonNull Draft draft) {
        android.content.Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            ArrayMap<String, Uri> locations = StorageUtils.getAllStorageLocations(appContext);
            ThreadUtils.postOnMainThread(() -> {
                if (isAdded()) showDestinationChoices(index, draft, locations);
            });
        });
    }

    private void showDestinationChoices(int index, @NonNull Draft draft,
                                        @NonNull ArrayMap<String, Uri> locations) {
        LinkedHashMap<String, Uri> choices = new LinkedHashMap<>();
        choices.put(getString(R.string.backup_tag_policy_configured_destination), null);
        for (int i = 0; i < locations.size(); ++i) {
            Uri uri = locations.valueAt(i);
            if (!containsUri(choices, uri)) choices.put(locations.keyAt(i), uri);
        }
        if (draft.destination != null && !containsUri(choices, draft.destination)) {
            choices.put(getString(R.string.backup_tag_policy_saved_destination), draft.destination);
        }
        List<Map.Entry<String, Uri>> entries = new ArrayList<>(choices.entrySet());
        CharSequence[] labels = new CharSequence[entries.size()];
        int selected = 0;
        for (int i = 0; i < entries.size(); ++i) {
            labels[i] = entries.get(i).getKey();
            if (java.util.Objects.equals(entries.get(i).getValue(), draft.destination)) selected = i;
        }
        final int[] choice = {selected};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_tag_policy_destination)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> choice[0] = which)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    draft.destination = entries.get(choice[0]).getValue();
                    BackupTagPolicyStore.Policy saved = draft.toPolicy();
                    if (index >= 0) mStore.replace(index, saved);
                    else mStore.add(saved);
                    bindPolicies();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPreview() {
        Map<String, Set<String>> tagged = new AppTagStore(requireContext()).snapshot();
        List<String> packages = new ArrayList<>(tagged.keySet());
        Collections.sort(packages);
        if (packages.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.backup_tag_policy_preview)
                    .setMessage(R.string.backup_tag_policy_preview_empty)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        int defaultFlags = Prefs.BackupRestore.getBackupFlags();
        android.content.Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            StringBuilder message = new StringBuilder();
            for (String packageName : packages) {
                BackupTagPolicyStore.Resolution resolution = mStore.resolve(packageName, defaultFlags);
                String winner = resolution.policy != null ? "#" + resolution.policy.tag
                        : appContext.getString(R.string.backup_tag_policy_default);
                if (resolution.cryptoFallback) winner += " · " + appContext.getString(R.string.backup_tag_policy_crypto_warning);
                if (resolution.partsFallback) winner += " · " + appContext.getString(R.string.backup_tag_policy_parts_warning);
                if (resolution.destinationFallback) winner += " · " + appContext.getString(R.string.backup_tag_policy_destination_warning);
                if (message.length() > 0) message.append('\n');
                message.append(appContext.getString(R.string.backup_tag_policy_preview_line, packageName, winner));
            }
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                new ScrollableDialogBuilder(requireActivity())
                        .setTitle(R.string.backup_tag_policy_preview)
                        .setMessage(message)
                        .setPositiveButton(R.string.close, null)
                        .show();
            });
        });
    }

    @Nullable
    private Integer parseNumber(@Nullable String raw) {
        try {
            int value = Integer.parseInt(raw != null ? raw.trim() : "");
            if (value < 0 || value > 36_500) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            UIUtils.displayShortToast(R.string.backup_tag_policy_invalid_number);
            return null;
        }
    }

    private static boolean containsUri(@NonNull Map<String, Uri> choices, @NonNull Uri uri) {
        for (Uri value : choices.values()) if (uri.equals(value)) return true;
        return false;
    }

    @NonNull
    private CharSequence cryptoLabel(@NonNull String mode) {
        switch (mode) {
            case CryptoUtils.MODE_AES:
                return getText(R.string.backup_tag_policy_aes_encryption);
            case CryptoUtils.MODE_RSA:
                return getText(R.string.backup_tag_policy_rsa_encryption);
            case CryptoUtils.MODE_ECC:
                return getText(R.string.backup_tag_policy_ecc_encryption);
            case CryptoUtils.MODE_OPEN_PGP:
                return getText(R.string.backup_tag_policy_pgp_encryption);
            default:
                return getText(R.string.backup_tag_policy_none_encryption);
        }
    }

    @NonNull
    private static String[] cryptoModes() {
        return new String[]{CryptoUtils.MODE_NO_ENCRYPTION, CryptoUtils.MODE_AES,
                CryptoUtils.MODE_RSA, CryptoUtils.MODE_ECC, CryptoUtils.MODE_OPEN_PGP};
    }

    private static final class Draft {
        @NonNull
        String tag = "";
        int flags = BackupFlags.BACKUP_APK_FILES;
        @NonNull
        String cryptoMode = CryptoUtils.getMode();
        int maxCount = Prefs.BackupRestore.getMaxBackupsPerApp();
        int maxAgeDays = Prefs.BackupRestore.getMaxBackupAgeDays();
        @Nullable
        Uri destination;

        Draft() {
        }

        Draft(@NonNull BackupTagPolicyStore.Policy policy) {
            tag = policy.tag;
            flags = policy.flags;
            cryptoMode = policy.cryptoMode;
            maxCount = policy.maxCount;
            maxAgeDays = policy.maxAgeDays;
            destination = policy.destination;
        }

        @NonNull
        BackupTagPolicyStore.Policy toPolicy() {
            return new BackupTagPolicyStore.Policy(tag, flags, cryptoMode, maxCount, maxAgeDays, destination);
        }
    }
}
