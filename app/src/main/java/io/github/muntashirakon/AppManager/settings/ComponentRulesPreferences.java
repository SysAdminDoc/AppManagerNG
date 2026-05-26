// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentRulesPreview;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.view.ProgressIndicatorCompat;

public class ComponentRulesPreferences extends PreferenceFragment {
    @Nullable
    private Future<?> mLoadFuture;
    private SettingsActivity mActivity;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        mActivity = (SettingsActivity) requireActivity();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);
        bindLoading(screen);
        loadRules();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MotionUtils.applySharedAxisZTransition(this);
    }

    @Override
    public void onDestroy() {
        if (mLoadFuture != null) {
            mLoadFuture.cancel(true);
        }
        super.onDestroy();
    }

    @Override
    public int getTitle() {
        return R.string.pref_component_rules_editor;
    }

    private void bindLoading(@NonNull PreferenceScreen screen) {
        screen.removeAll();
        Preference loading = new Preference(requireContext());
        loading.setSelectable(false);
        loading.setTitle(R.string.loading);
        loading.setSummary(R.string.component_rules_loading_summary);
        screen.addPreference(loading);
    }

    private void loadRules() {
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, true);
        Context appContext = requireContext().getApplicationContext();
        PackageManager pm = appContext.getPackageManager();
        int userId = UserHandleHidden.myUserId();
        mLoadFuture = ThreadUtils.postOnBackgroundThread(() -> {
            List<ComponentRulesRow> rows = new ArrayList<>();
            for (String packageName : ComponentUtils.getAllPackagesWithComponentRuleFiles(appContext)) {
                if (ThreadUtils.isInterrupted()) return;
                try (ComponentsBlocker blocker = ComponentsBlocker.getInstance(packageName, userId)) {
                    List<ComponentRule> componentRules = blocker.getAllComponents();
                    ComponentRulesPreview.Summary summary = ComponentRulesPreview.summarize(componentRules);
                    if (summary.totalEntries == 0) {
                        continue;
                    }
                    CharSequence label = PackageUtils.getPackageLabel(pm, packageName, userId);
                    rows.add(new ComponentRulesRow(packageName, label != null ? label.toString() : packageName,
                            userId, summary, ComponentRulesPreview.buildIfwXml(packageName, componentRules),
                            describeComponents(componentRules)));
                } catch (Throwable ignored) {
                }
            }
            ThreadUtils.postOnMainThread(() -> bindRows(rows));
        });
    }

    private void bindRows(@NonNull List<ComponentRulesRow> rows) {
        if (!isAdded()) return;
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, false);
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        int ifwEntries = 0;
        int disabledEntries = 0;
        for (ComponentRulesRow row : rows) {
            ifwEntries += row.summary.ifwEntries;
            disabledEntries += row.summary.disabledOnlyEntries + row.summary.providerEntries;
        }

        Preference overview = new Preference(requireContext());
        overview.setSelectable(false);
        overview.setTitle(R.string.component_rules_overview_title);
        overview.setSummary(getString(R.string.component_rules_overview_summary, rows.size(),
                ifwEntries, disabledEntries, ComponentsBlocker.SYSTEM_RULES_PATH));
        screen.addPreference(overview);

        Preference refresh = new Preference(requireContext());
        refresh.setTitle(R.string.refresh);
        refresh.setSummary(R.string.component_rules_refresh_summary);
        refresh.setOnPreferenceClickListener(preference -> {
            bindLoading(screen);
            loadRules();
            return true;
        });
        screen.addPreference(refresh);

        if (rows.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setTitle(R.string.component_rules_empty_title);
            empty.setSummary(R.string.component_rules_empty_summary);
            screen.addPreference(empty);
            return;
        }

        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.component_rules_packages_title);
        screen.addPreference(category);

        for (ComponentRulesRow row : rows) {
            Preference preference = new Preference(requireContext());
            preference.setTitle(row.label);
            preference.setSummary(getString(R.string.component_rules_package_summary,
                    row.packageName, row.summary.ifwEntries,
                    row.summary.disabledOnlyEntries, row.summary.providerEntries,
                    row.summary.pendingEntries));
            preference.setOnPreferenceClickListener(pref -> {
                showRulesDialog(row);
                return true;
            });
            category.addPreference(preference);
        }
    }

    private void showRulesDialog(@NonNull ComponentRulesRow row) {
        String detail = getString(R.string.component_rules_dialog_summary, row.packageName,
                row.summary.ifwEntries, row.summary.disabledOnlyEntries,
                row.summary.providerEntries, row.summary.pendingEntries)
                + "\n\n" + row.componentText
                + "\n\n" + getString(R.string.component_rules_ifw_xml_title)
                + "\n\n" + row.ifwXml;
        new ScrollableDialogBuilder(requireActivity())
                .setTitle(row.label)
                .setMessage(detail)
                .enableAnchors()
                .setPositiveButton(R.string.copy, (dialog, which, isChecked) -> {
                    Utils.copyToClipboard(requireContext(), row.packageName + " IFW XML", row.ifwXml);
                    UIUtils.displayShortToast(R.string.component_rules_ifw_xml_copied);
                })
                .setNeutralButton(R.string.app_info, (dialog, which, isChecked) ->
                        startActivity(AppDetailsActivity.getIntent(requireContext(), row.packageName, row.userId)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    @NonNull
    private static String describeComponents(@NonNull List<ComponentRule> componentRules) {
        List<String> lines = new ArrayList<>();
        for (ComponentRule rule : componentRules) {
            if (rule.toBeRemoved()) {
                continue;
            }
            lines.add(rule.type.name() + "\t" + rule.getComponentStatus() + "\t" + rule.name);
        }
        return lines.isEmpty() ? "(none)" : TextUtils.join("\n", lines);
    }

    private static final class ComponentRulesRow {
        @NonNull
        final String packageName;
        @NonNull
        final String label;
        final int userId;
        @NonNull
        final ComponentRulesPreview.Summary summary;
        @NonNull
        final String ifwXml;
        @NonNull
        final String componentText;

        ComponentRulesRow(@NonNull String packageName, @NonNull String label, int userId,
                          @NonNull ComponentRulesPreview.Summary summary, @NonNull String ifwXml,
                          @NonNull String componentText) {
            this.packageName = packageName;
            this.label = label;
            this.userId = userId;
            this.summary = summary;
            this.ifwXml = ifwXml;
            this.componentText = componentText;
        }
    }
}
