// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.preference.WarningAlertPreference;

public class MainPreferences extends PreferenceFragment implements SearchView.OnQueryTextListener {
    @NonNull
    public static MainPreferences getInstance(@Nullable String key, boolean dualPane) {
        MainPreferences preferences = new MainPreferences();
        Bundle args = new Bundle();
        args.putString(PREF_KEY, key);
        args.putBoolean(PREF_SECONDARY, dualPane);
        preferences.setArguments(args);
        return preferences;
    }

    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private FragmentActivity mActivity;
    private Preference mModePref;
    private Preference mLocalePref;
    private Preference mNoResultsPref;
    private String[] mModes;
    @Nullable
    private SearchView mSearchView;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        MainPreferencesViewModel model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = requireActivity();
        // Expiry notice
        WarningAlertPreference buildExpiringNotice = requirePreference("app_manager_expiring_notice");
        buildExpiringNotice.setVisible(!Boolean.FALSE.equals(BuildExpiryChecker.buildExpired()));
        // Custom locale
        mLocalePref = requirePreference("custom_locale");
        // Mode of operation
        mModePref = requirePreference("mode_of_operations");
        mModes = getResources().getStringArray(R.array.modes);
        mNoResultsPref = new Preference(requireContext());
        mNoResultsPref.setKey("settings_search_no_results");
        mNoResultsPref.setIcon(R.drawable.ic_information_circle);
        mNoResultsPref.setTitle(R.string.settings_search_empty_title);
        mNoResultsPref.setSummary(R.string.settings_search_empty_message);
        mNoResultsPref.setSelectable(false);
        mNoResultsPref.setVisible(false);
        getPreferenceScreen().addPreference(mNoResultsPref);

        model.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            UIUtils.displayShortToast(R.string.the_operation_was_successful);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mActivity instanceof SettingsActivity) {
            mSearchView = ((SettingsActivity) mActivity).searchView;
            if (mSearchView != null) {
                mSearchView.setVisibility(View.VISIBLE);
                mSearchView.setQueryHint(getString(R.string.search_settings));
                mSearchView.setOnQueryTextListener(this);
                filterPreferences(mSearchView.getQuery());
            }
        }
        if (mModePref != null) {
            mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                    mModes[MODE_NAMES.indexOf(Ops.getMode())], Ops.getInferredMode(mActivity)));
        }
        if (mLocalePref != null) {
            mLocalePref.setSummary(getLanguageName());
        }
    }

    @Override
    public int getTitle() {
        return R.string.settings;
    }

    public CharSequence getLanguageName() {
        String langTag = Prefs.Appearance.getLanguage();
        if (LangUtils.LANG_AUTO.equals(langTag)) {
            return getString(R.string.auto);
        }
        Locale locale = Locale.forLanguageTag(langTag);
        return locale.getDisplayName(locale);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filterPreferences(newText);
        return true;
    }

    private void filterPreferences(@Nullable CharSequence query) {
        PreferenceGroup screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        String normalizedQuery = query == null ? "" : query.toString().trim().toLowerCase(Locale.ROOT);
        boolean hasMatches = filterPreferenceGroup(screen, normalizedQuery, false);
        if (mNoResultsPref != null) {
            mNoResultsPref.setVisible(!TextUtils.isEmpty(normalizedQuery) && !hasMatches);
        }
    }

    private boolean filterPreferenceGroup(@NonNull PreferenceGroup group, @NonNull String query,
                                          boolean ancestorMatched) {
        boolean emptyQuery = TextUtils.isEmpty(query);
        boolean groupMatched = ancestorMatched || preferenceMatches(group, query);
        boolean anyChildVisible = false;
        for (int i = 0; i < group.getPreferenceCount(); ++i) {
            Preference preference = group.getPreference(i);
            if (preference == mNoResultsPref) {
                preference.setVisible(false);
                continue;
            }
            boolean childVisible;
            if (preference instanceof PreferenceGroup) {
                childVisible = filterPreferenceGroup((PreferenceGroup) preference, query, groupMatched);
            } else {
                childVisible = emptyQuery || groupMatched || preferenceMatches(preference, query);
                preference.setVisible(childVisible);
            }
            anyChildVisible |= childVisible;
        }
        boolean groupVisible = emptyQuery || groupMatched || anyChildVisible;
        group.setVisible(groupVisible);
        return groupVisible;
    }

    private boolean preferenceMatches(@NonNull Preference preference, @NonNull String query) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        return contains(preference.getTitle(), query)
                || contains(preference.getSummary(), query)
                || contains(preference.getKey(), query);
    }

    private boolean contains(@Nullable CharSequence value, @NonNull String query) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(query);
    }
}
