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
    private boolean mInteractionsEnabled = true;

    PermissionAppsAdapter(OnAppToggle toggle) {
        mToggle = toggle;
    }

    void submit(List<PermissionAppsViewModel.AppRow> rows) {
        mRows.clear();
        if (rows != null) mRows.addAll(rows);
        notifyDataSetChanged();
    }

    void setInteractionsEnabled(boolean enabled) {
        if (mInteractionsEnabled == enabled) return;
        mInteractionsEnabled = enabled;
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
        String status = h.itemView.getResources().getString(!r.anyModifiable
                ? R.string.perm_app_status_read_only
                : r.anyGranted
                        ? R.string.perm_app_status_granted
                        : R.string.perm_app_status_not_granted);
        h.status.setText(status);
        h.itemView.setContentDescription(h.itemView.getResources().getString(
                R.string.perm_app_row_a11y, r.label, r.packageName, status));
        boolean enabled = mInteractionsEnabled && r.anyModifiable;
        h.itemView.setEnabled(enabled);
        h.toggle.setEnabled(enabled);
        h.itemView.setAlpha(enabled ? 1f : 0.62f);
        View.OnClickListener click = v -> {
            if (!mInteractionsEnabled || !r.anyModifiable) return;
            if (mToggle != null) mToggle.onToggle(r);
        };
        h.itemView.setOnClickListener(enabled ? click : null);
        h.toggle.setOnClickListener(enabled ? click : null);
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final MaterialTextView label;
        final MaterialTextView pkg;
        final MaterialTextView status;
        final MaterialSwitch toggle;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.app_icon);
            label = v.findViewById(R.id.app_label);
            pkg = v.findViewById(R.id.app_package);
            status = v.findViewById(R.id.permission_status);
            toggle = v.findViewById(R.id.permission_switch);
        }
    }
}
