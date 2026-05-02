// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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
    private Preference mBatteryOptPref;

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
        // Battery optimization — surfaces current exemption state and routes the
        // user to either the per-app request prompt (if currently optimized) or
        // the system-wide settings list (if already exempt, so they can revoke).
        // No background work is performed here; the OS owns the toggle. Pre-M
        // devices have no whitelist concept — we leave the entry visible but
        // disabled with an explanatory summary.
        mBatteryOptPref = (Preference) Objects.requireNonNull(findPreference("battery_optimization"));
        mBatteryOptPref.setOnPreferenceClickListener(preference -> {
            launchBatteryOptimizationFlow();
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
    public void onResume() {
        super.onResume();
        // The user may have toggled exemption from the system settings page we
        // launched; refresh the summary so it reflects reality without forcing
        // a full screen reload.
        refreshBatteryOptimizationSummary();
    }

    @Override
    public int getTitle() {
        return R.string.troubleshooting;
    }

    private void refreshBatteryOptimizationSummary() {
        if (mBatteryOptPref == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mBatteryOptPref.setSummary(R.string.pref_battery_optimization_unsupported);
            mBatteryOptPref.setEnabled(false);
            return;
        }
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        mBatteryOptPref.setSummary(exempt
                ? R.string.pref_battery_optimization_state_exempt
                : R.string.pref_battery_optimization_state_optimized);
        mBatteryOptPref.setEnabled(true);
    }

    private void launchBatteryOptimizationFlow() {
        Context ctx = getContext();
        if (ctx == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(ctx, R.string.pref_battery_optimization_unsupported,
                    Toast.LENGTH_LONG).show();
            return;
        }
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        Intent intent;
        if (exempt) {
            // Already exempt — drop the user into the system-wide list so they
            // can revoke if desired (REQUEST_IGNORE_... is request-only).
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        } else {
            intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(ctx, R.string.pref_battery_optimization_unsupported,
                    Toast.LENGTH_LONG).show();
        }
    }
}