// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

class PermissionAppsAdapter extends RecyclerView.Adapter<PermissionAppsAdapter.VH> {
    interface OnAppToggle {
        void onToggle(@NonNull PermissionAppsViewModel.AppRow row);
    }

    private final List<PermissionAppsViewModel.AppRow> mRows = new ArrayList<>();
    private final OnAppToggle mToggle;

    PermissionAppsAdapter(OnAppToggle toggle) {
        mToggle = toggle;
    }

    void submit(List<PermissionAppsViewModel.AppRow> rows) {
        mRows.clear();
        if (rows != null) mRows.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_permission_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PermissionAppsViewModel.AppRow r = mRows.get(position);
        if (r.icon != null) h.icon.setImageDrawable(r.icon);
        else h.icon.setImageResource(R.drawable.ic_package);
        h.label.setText(r.label);
        h.pkg.setText(r.packageName);
        h.toggle.setOnCheckedChangeListener(null);
        h.toggle.setChecked(r.anyGranted);
        h.toggle.setEnabled(r.anyModifiable);
        View.OnClickListener click = v -> {
            if (!r.anyModifiable) return;
            if (mToggle != null) mToggle.onToggle(r);
        };
        h.itemView.setOnClickListener(click);
        h.toggle.setOnClickListener(click);
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final MaterialTextView label;
        final MaterialTextView pkg;
        final MaterialSwitch toggle;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.app_icon);
            label = v.findViewById(R.id.app_label);
            pkg = v.findViewById(R.id.app_package);
            toggle = v.findViewById(R.id.permission_switch);
        }
    }
}
