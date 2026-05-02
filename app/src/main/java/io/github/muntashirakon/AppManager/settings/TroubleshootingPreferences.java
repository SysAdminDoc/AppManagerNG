// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.onboarding.OnboardingFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class TroubleshootingPreferences extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_troubleshooting, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        MainPreferencesViewModel model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        // Reload apps
        ((Preference) Objects.requireNonNull(findPreference("reload_apps")))
                .setOnPreferenceClickListener(preference -> {
                    model.reloadApps();
                    return true;
                });
        // Replay onboarding wizard — clears the shown flag and surfaces the picker
        // immediately so power users / testers can revisit explainers without a
        // fresh install. The picker writes back PREF_ONBOARDING_SHOWN_BOOL on
        // pick/cancel, so the flow self-heals after the replay.
        ((Preference) Objects.requireNonNull(findPreference("replay_onboarding")))
                .setOnPreferenceClickListener(preference -> {
                    AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, false);
                    if (getActivity() == null) return true;
                    if (getActivity().getSupportFragmentManager()
                            .findFragmentByTag(OnboardingFragment.TAG) != null) return true;
                    new OnboardingFragment().show(getActivity().getSupportFragmentManager(),
                            OnboardingFragment.TAG);
                    return true;
                });
        // Replay quick tour — flips the shown flag so MainActivity surfaces the
        // tour on its next maybeShowMainListTour() pass. Cheaper than building a
        // synchronous re-trigger path through the activity backstack.
        ((Preference) Objects.requireNonNull(findPreference("replay_tour")))
                .setOnPreferenceClickListener(preference -> {
                    AppPref.set(AppPref.PrefKey.PREF_MAIN_TOUR_SHOWN_BOOL, false);
                    Toast.makeText(requireContext(), R.string.replay_tour_armed,
                            Toast.LENGTH_LONG).show();
                    return true;
                });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public int getTitle() {
        return R.string.troubleshooting;
    }
}