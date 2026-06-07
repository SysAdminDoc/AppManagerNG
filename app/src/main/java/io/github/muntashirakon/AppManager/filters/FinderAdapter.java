// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.util.AdapterUtils;

public class FinderAdapter extends RecyclerView.Adapter<FinderAdapter.ViewHolder> {
    private static final int MAX_MATCHED_EXTRA_PREVIEW = 3;
    private final List<FilterItem.FilteredItemInfo<FilterableAppInfo>> mAdapterList = new ArrayList<>();

    @UiThread
    public void setDefaultList(List<FilterItem.FilteredItemInfo<FilterableAppInfo>> list) {
        synchronized (mAdapterList) {
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_finder, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FilterItem.FilteredItemInfo<FilterableAppInfo> itemInfo;
        synchronized (mAdapterList) {
            itemInfo = mAdapterList.get(position);
        }
        FilterableAppInfo appInfo = itemInfo.info;
        Context context = holder.itemView.getContext();
        ImageLoader.getInstance().displayImage(appInfo.getPackageName(), appInfo.getApplicationInfo(), holder.icon);
        holder.label.setText(appInfo.getAppLabel());
        holder.pkg.setText(appInfo.getPackageName());
        holder.item1.setVisibility(View.VISIBLE);
        holder.item1.setText(context.getString(R.string.finder_user_state,
                appInfo.getUserId(), getPackageStateLabel(context, appInfo)));
        String[] knownPreinstallOems = appInfo.getKnownPreinstallOems();
        if (knownPreinstallOems.length > 0) {
            holder.item2.setVisibility(View.VISIBLE);
            holder.item2.setText(context.getString(R.string.finder_known_preinstall_oems,
                    TextUtils.join(", ", knownPreinstallOems)));
        } else {
            holder.item2.setVisibility(View.GONE);
        }
        CharSequence matchedExtras = formatMatchedExtras(context, itemInfo.result);
        if (TextUtils.isEmpty(matchedExtras)) {
            holder.item3.setVisibility(View.GONE);
        } else {
            holder.item3.setVisibility(View.VISIBLE);
            holder.item3.setText(matchedExtras);
        }
        holder.toggleBtn.setVisibility(View.GONE);
        holder.itemView.setStrokeColor(Color.TRANSPARENT);
    }

    @Nullable
    @VisibleForTesting
    static CharSequence formatMatchedExtras(@NonNull Context context, @NonNull FilterOption.TestResult result) {
        List<String> lines = new ArrayList<>();
        appendMatches(context, lines, R.string.finder_matched_permissions, result.getMatchedPermissions());
        appendMatches(context, lines, R.string.finder_matched_components,
                getComponentNames(result.getMatchedComponents()));
        appendMatches(context, lines, R.string.finder_matched_trackers,
                getComponentNames(result.getMatchedTrackers()));
        appendMatches(context, lines, R.string.finder_matched_backups, getBackupNames(context, result.getMatchedBackups()));
        appendMatches(context, lines, R.string.finder_matched_signatures, result.getMatchedSubjectLines());
        return lines.isEmpty() ? null : TextUtils.join("\n", lines);
    }

    private static void appendMatches(@NonNull Context context, @NonNull List<String> lines,
                                      @StringRes int labelStringRes, @Nullable Collection<? extends CharSequence> rawValues) {
        CharSequence preview = formatPreview(context, rawValues);
        if (!TextUtils.isEmpty(preview)) {
            lines.add(context.getString(labelStringRes, preview));
        }
    }

    @Nullable
    private static CharSequence formatPreview(@NonNull Context context,
                                              @Nullable Collection<? extends CharSequence> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return null;
        }
        Set<String> values = new LinkedHashSet<>();
        for (CharSequence rawValue : rawValues) {
            if (!TextUtils.isEmpty(rawValue)) {
                values.add(rawValue.toString());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        List<String> preview = new ArrayList<>();
        int count = 0;
        for (String value : values) {
            if (count++ < MAX_MATCHED_EXTRA_PREVIEW) {
                preview.add(value);
            }
        }
        int remaining = values.size() - preview.size();
        if (remaining > 0) {
            preview.add(context.getString(R.string.finder_more_matches, remaining));
        }
        return TextUtils.join(", ", preview);
    }

    @Nullable
    private static List<String> getComponentNames(@Nullable Map<ComponentInfo, Integer> components) {
        if (components == null || components.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (ComponentInfo component : components.keySet()) {
            if (component != null && !TextUtils.isEmpty(component.name)) {
                names.add(component.name);
            }
        }
        return names;
    }

    @Nullable
    private static List<CharSequence> getBackupNames(@NonNull Context context, @Nullable List<Backup> backups) {
        if (backups == null || backups.isEmpty()) {
            return null;
        }
        List<CharSequence> names = new ArrayList<>();
        for (Backup backup : backups) {
            if (backup == null) {
                continue;
            }
            names.add(BackupUtils.getDisplayBackupName(context, backup.backupName));
        }
        return names;
    }

    @NonNull
    private static String getPackageStateLabel(@NonNull Context context, @NonNull FilterableAppInfo appInfo) {
        if (!appInfo.isInstalled()) {
            return context.getString(R.string.package_state_not_installed);
        }
        if (appInfo.isFrozen()) {
            return context.getString(R.string.package_state_disabled);
        }
        return context.getString(R.string.package_state_enabled);
    }

    @Override
    public int getItemCount() {
        synchronized (mAdapterList) {
            return mAdapterList.size();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public MaterialCardView itemView;
        public AppCompatImageView icon;
        public MaterialTextView label;
        public MaterialTextView pkg;
        public MaterialTextView item1;
        public MaterialTextView item2;
        public MaterialTextView item3;
        public MaterialSwitch toggleBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            pkg = itemView.findViewById(R.id.package_name);
            item1 = itemView.findViewById(R.id.item1);
            item2 = itemView.findViewById(R.id.item2);
            item3 = itemView.findViewById(R.id.item3);
            toggleBtn = itemView.findViewById(R.id.toggle_button);
        }
    }


}
