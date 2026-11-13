// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * The body of the SHA-256 prompt shown before Android's install confirmation: which app and file
 * this is, the checksum, and what the install changes compared with the installed copy.
 *
 * <p>Installs queued in the background reach this prompt one after another, often with nothing
 * else on screen, so the app and file come first. The comparison can be turned off from the
 * prompt itself.
 */
final class InstallSessionPrompt {
    /** Reads the comparison. Called once, off the main thread. */
    interface ChangesLoader {
        @WorkerThread
        @Nullable
        InstallerManifestSummary.Changes load();
    }

    private InstallSessionPrompt() {
    }

    @UiThread
    @NonNull
    static View inflate(@NonNull Context context, @Nullable InstallSessionDetails details,
                        @NonNull String packageName, @NonNull String sha256) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_install_session, null, false);
        CharSequence label = details != null ? details.appLabel : null;
        TextView labelView = root.findViewById(R.id.app_label);
        TextView packageView = root.findViewById(R.id.package_name);
        if (TextUtils.isEmpty(label)) {
            labelView.setText(packageName);
            packageView.setVisibility(View.GONE);
        } else {
            labelView.setText(label);
            packageView.setText(packageName);
        }
        TextView apkView = root.findViewById(R.id.apk_name);
        String apkName = details != null ? details.apkName : null;
        if (TextUtils.isEmpty(apkName)) {
            apkView.setVisibility(View.GONE);
        } else {
            apkView.setText(context.getString(R.string.installer_session_file, apkName));
        }
        ((TextView) root.findViewById(R.id.sha256)).setText(context.getString(
                R.string.installer_session_sha256_message, PackageInstallerActivity.formatSha256ForDisplay(sha256)));
        return root;
    }

    /**
     * Sets up the switch and, while it's on, the comparison.
     *
     * @param loader     {@code null} when there's no base APK to read, which hides the switch.
     * @param background Where the loader runs.
     */
    @UiThread
    static void bindChanges(@NonNull View root, @NonNull String packageName, @Nullable ChangesLoader loader,
                            @NonNull Executor background) {
        MaterialSwitch toggle = root.findViewById(R.id.show_changes);
        View section = root.findViewById(R.id.changes);
        if (loader == null) {
            toggle.setVisibility(View.GONE);
            section.setVisibility(View.GONE);
            return;
        }
        boolean[] started = {false};
        Runnable start = () -> {
            if (started[0]) {
                return;
            }
            started[0] = true;
            TextView status = root.findViewById(R.id.changes_status);
            status.setText(R.string.installer_session_changes_reading);
            status.setVisibility(View.VISIBLE);
            background.execute(() -> {
                InstallerManifestSummary.Changes changes = loader.load();
                ThreadUtils.postOnMainThread(() -> showChanges(root, packageName, changes));
            });
        };
        boolean show = Prefs.Installer.showSessionChanges();
        toggle.setChecked(show);
        section.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            start.run();
        }
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.Installer.setShowSessionChanges(isChecked);
            section.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                start.run();
            }
        });
    }

    /** Fills the comparison, or says it couldn't be read when {@code changes} is {@code null}. */
    @UiThread
    static void showChanges(@NonNull View root, @NonNull String packageName,
                            @Nullable InstallerManifestSummary.Changes changes) {
        Context context = root.getContext();
        TextView status = root.findViewById(R.id.changes_status);
        ViewGroup rows = root.findViewById(R.id.change_rows);
        rows.removeAllViews();
        if (changes == null) {
            status.setText(R.string.installer_session_changes_unreadable);
            status.setVisibility(View.VISIBLE);
            return;
        }
        String newVersion = versionOf(changes.newVersionName, changes.newVersionCode);
        status.setText(changes.newInstall
                ? context.getString(R.string.installer_session_version_new, newVersion)
                : context.getString(R.string.installer_session_version_update,
                versionOf(changes.oldVersionName, changes.oldVersionCode), newVersion));
        status.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (InstallerManifestSummary.KindChange kind : changes.kinds) {
            View row = inflater.inflate(R.layout.item_install_session_change, rows, false);
            bindRow(row, kind, changes.newInstall, packageName);
            rows.addView(row);
        }
    }

    private static void bindRow(@NonNull View row, @NonNull InstallerManifestSummary.KindChange kind,
                                boolean newInstall, @NonNull String packageName) {
        Context context = row.getContext();
        TextView header = row.findViewById(R.id.header);
        TextView names = row.findViewById(R.id.names);
        String kindName = context.getString(kindLabel(kind.kind));
        if (newInstall) {
            header.setText(context.getString(R.string.installer_session_kind_total, kindName, kind.total));
            return;
        }
        if (!kind.hasChanges()) {
            header.setText(context.getString(R.string.installer_session_kind_unchanged, kindName));
            return;
        }
        header.setText(context.getString(R.string.installer_session_kind_changed, kindName,
                kind.added.size(), kind.removed.size()));
        names.setText(namesText(context, kind, packageName));
        setExpanded(header, names, false);
        header.setOnClickListener(v -> setExpanded(header, names, names.getVisibility() != View.VISIBLE));
    }

    private static void setExpanded(@NonNull TextView header, @NonNull TextView names, boolean expanded) {
        names.setVisibility(expanded ? View.VISIBLE : View.GONE);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(header, 0, 0,
                expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more, 0);
        ViewCompat.setStateDescription(header, header.getContext().getString(
                expanded ? R.string.installer_session_expanded : R.string.installer_session_collapsed));
    }

    @NonNull
    static String namesText(@NonNull Context context, @NonNull InstallerManifestSummary.KindChange kind,
                            @NonNull String packageName) {
        StringBuilder out = new StringBuilder();
        appendNames(out, context.getString(R.string.installer_session_added), kind.added, packageName);
        appendNames(out, context.getString(R.string.installer_session_removed), kind.removed, packageName);
        return out.toString();
    }

    private static void appendNames(@NonNull StringBuilder out, @NonNull String heading,
                                    @NonNull List<String> names, @NonNull String packageName) {
        if (names.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(heading);
        for (String name : names) {
            out.append('\n').append(InstallerManifestSummary.displayName(packageName, name));
        }
    }

    @NonNull
    static String versionOf(@Nullable String versionName, long versionCode) {
        return versionName != null
                ? String.format(Locale.ROOT, "%s (%d)", versionName, versionCode)
                : String.valueOf(versionCode);
    }

    @StringRes
    private static int kindLabel(@NonNull InstallerManifestSummary.Kind kind) {
        switch (kind) {
            case PERMISSIONS:
                return R.string.installer_session_kind_permissions;
            case ACTIVITIES:
                return R.string.installer_session_kind_activities;
            case SERVICES:
                return R.string.installer_session_kind_services;
            case RECEIVERS:
                return R.string.installer_session_kind_receivers;
            case PROVIDERS:
            default:
                return R.string.installer_session_kind_providers;
        }
    }
}
