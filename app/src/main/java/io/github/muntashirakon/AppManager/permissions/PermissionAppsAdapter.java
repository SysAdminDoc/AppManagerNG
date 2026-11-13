// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

class PermissionAppsAdapter extends RecyclerView.Adapter<PermissionAppsAdapter.VH> {
    interface OnAppToggle {
        void onToggle(@NonNull PermissionAppsViewModel.AppRow row);
    }

    interface OnReferenceAction {
        void onReferenceAction(@NonNull PermissionAppsViewModel.AppRow row);
    }

    interface OnOpenSettings {
        void onOpenSettings(@NonNull PermissionAppsViewModel.AppRow row);
    }

    private final List<PermissionAppsViewModel.AppRow> mRows = new ArrayList<>();
    private final OnAppToggle mToggle;
    private final OnReferenceAction mReferenceAction;
    private final OnOpenSettings mOpenSettings;
    private boolean mInteractionsEnabled = true;

    PermissionAppsAdapter(OnAppToggle toggle, OnReferenceAction referenceAction, OnOpenSettings openSettings) {
        mToggle = toggle;
        mReferenceAction = referenceAction;
        mOpenSettings = openSettings;
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
        // The badge always says what the app has. A pinned reference shows on the flag, and a
        // permission App Manager can't change gets a Settings button instead of the switch.
        String status = h.itemView.getResources().getString(r.referenceDrifted
                ? R.string.perm_app_status_reference_drift
                : r.anyGranted
                        ? R.string.perm_app_status_granted
                        : R.string.perm_app_status_not_granted);
        h.status.setText(status);
        h.itemView.setContentDescription(h.itemView.getResources().getString(
                R.string.perm_app_row_a11y, r.label, r.packageName, status));
        boolean modifiable = r.anyModifiable;
        h.itemView.setEnabled(mInteractionsEnabled);
        h.toggle.setVisibility(modifiable ? View.VISIBLE : View.GONE);
        h.toggle.setEnabled(mInteractionsEnabled && modifiable);
        h.settingsAction.setVisibility(modifiable ? View.GONE : View.VISIBLE);
        h.settingsAction.setEnabled(mInteractionsEnabled);
        TooltipCompat.setTooltipText(h.settingsAction, h.settingsAction.getContentDescription());
        ReferenceAction.bind(h.referenceAction,
                ReferenceAction.next(r.hasReference, r.referenceDrifted, r.anyModifiable), r.hasReference);
        h.referenceAction.setEnabled(mInteractionsEnabled);
        h.referenceAction.setOnClickListener(v -> {
            if (!mInteractionsEnabled || mReferenceAction == null) return;
            mReferenceAction.onReferenceAction(r);
        });
        View.OnClickListener click = v -> {
            if (!mInteractionsEnabled) return;
            if (r.anyModifiable) {
                if (mToggle != null) mToggle.onToggle(r);
            } else if (mOpenSettings != null) {
                mOpenSettings.onOpenSettings(r);
            }
        };
        h.itemView.setOnClickListener(click);
        h.toggle.setOnClickListener(modifiable ? click : null);
        h.settingsAction.setOnClickListener(click);
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
        final MaterialButton referenceAction;
        final MaterialSwitch toggle;
        final MaterialButton settingsAction;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.app_icon);
            label = v.findViewById(R.id.app_label);
            pkg = v.findViewById(R.id.app_package);
            status = v.findViewById(R.id.permission_status);
            referenceAction = v.findViewById(R.id.permission_reference_action);
            toggle = v.findViewById(R.id.permission_switch);
            settingsAction = v.findViewById(R.id.permission_settings_action);
        }
    }
}
