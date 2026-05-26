// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;

/**
 * Contextual help glossary. Each row is a searchable preference entry whose
 * summary is the elevator-pitch explainer; tapping the row opens a scrollable
 * dialog with the full body copy from {@code R.string.help_<topic>_body}.
 *
 * <p>By living in a {@link PreferenceFragment}-backed XML file, every glossary
 * entry is automatically picked up by {@link SettingsSearchIndex} — searching
 * "Shizuku", "Freezing", or "AES" lands the user here without any extra
 * registry plumbing.</p>
 */
public class GlossaryPreferences extends PreferenceFragment {

    private static final String[] ENTRY_KEYS = new String[] {
            "glossary_mode_of_operations",
            "glossary_shizuku",
            "glossary_root",
            "glossary_adb",
            "glossary_appops",
            "glossary_freezing",
            "glossary_component_blocking",
            "glossary_trackers",
            "glossary_debloater",
            "glossary_backup_encryption",
            "glossary_scheduled_backup",
            "glossary_pro_mode",
            "glossary_finder",
            "glossary_intent_interceptor",
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_glossary, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        for (String key : ENTRY_KEYS) {
            Preference pref = findPreference(key);
            if (pref == null) continue;
            pref.setOnPreferenceClickListener(p -> {
                showHelpDialog(p.getTitle(), p.getKey());
                return true;
            });
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MotionUtils.applySharedAxisZTransition(this);
    }

    @Override
    public int getTitle() {
        return R.string.pref_glossary_title;
    }

    private void showHelpDialog(@Nullable CharSequence title, @NonNull String prefKey) {
        @StringRes int bodyRes = resolveBodyRes(prefKey);
        if (bodyRes == 0) return;
        new ScrollableDialogBuilder(requireActivity())
                .setTitle(title != null ? title : getText(R.string.pref_glossary_title))
                .setMessage(bodyRes)
                .enableAnchors()
                .setNegativeButton(R.string.close, null)
                .show();
    }

    @StringRes
    static int resolveBodyRes(@NonNull String prefKey) {
        // Map glossary_<topic> -> R.string.help_<topic>_body. Explicit switch keeps
        // ProGuard from stripping any string resource because they are referenced.
        switch (prefKey) {
            case "glossary_mode_of_operations": return R.string.help_mode_of_operations_body;
            case "glossary_shizuku": return R.string.help_shizuku_body;
            case "glossary_root": return R.string.help_root_body;
            case "glossary_adb": return R.string.help_adb_body;
            case "glossary_appops": return R.string.help_appops_body;
            case "glossary_freezing": return R.string.help_freezing_body;
            case "glossary_component_blocking": return R.string.help_component_blocking_body;
            case "glossary_trackers": return R.string.help_trackers_body;
            case "glossary_debloater": return R.string.help_debloater_body;
            case "glossary_backup_encryption": return R.string.help_backup_encryption_body;
            case "glossary_scheduled_backup": return R.string.help_scheduled_backup_body;
            case "glossary_pro_mode": return R.string.help_pro_mode_body;
            case "glossary_finder": return R.string.help_finder_body;
            case "glossary_intent_interceptor": return R.string.help_intent_interceptor_body;
            default: return 0;
        }
    }
}
