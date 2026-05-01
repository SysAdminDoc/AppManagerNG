// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * First-run picker for the privilege level AppManagerNG should use (Auto / Root /
 * Wireless ADB / ADB over TCP / No-root). Shown once after the user has accepted
 * the disclaimer; tracked via {@link AppPref.PrefKey#PREF_ONBOARDING_SHOWN_BOOL}.
 *
 * <p>The sheet does not perform any privilege bring-up — it only writes the user's
 * choice to {@link Ops#setMode(String)} so subsequent launches honor it. Bring-up
 * (Su, ADB pairing, Shizuku connection) is owned by {@code BaseActivity} via
 * {@code Ops.init(...)} and handles its own dialogs there.
 *
 * <p>For Root, the card surfaces a "✓ Detected" / "Not detected" status by calling
 * {@link Ops#hasRoot()} on bind. Wireless ADB / ADB-TCP / No-root are always
 * pickable because their availability depends on user-configurable state we can't
 * cheaply check before the user has actually opted in (e.g. ADB pairing requires
 * permission).
 */
public class OnboardingFragment extends BottomSheetDialogFragment {
    public static final String TAG = "OnboardingFragment";

    /** True when this fragment has not yet been dismissed for this user. */
    public static boolean shouldShow() {
        return !AppPref.getBoolean(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Root status surfacing — picked here rather than in onCreate so we re-evaluate
        // if the user lands on this sheet from elsewhere.
        TextView rootStatus = view.findViewById(R.id.status_root);
        if (rootStatus != null) {
            rootStatus.setText(Ops.hasRoot()
                    ? R.string.onboarding_mode_root_status_detected
                    : R.string.onboarding_mode_root_status_missing);
        }
        view.findViewById(R.id.card_mode_auto).setOnClickListener(v -> pick(Ops.MODE_AUTO));
        view.findViewById(R.id.card_mode_root).setOnClickListener(v -> pick(Ops.MODE_ROOT));
        view.findViewById(R.id.card_mode_adb_wifi).setOnClickListener(v -> pick(Ops.MODE_ADB_WIFI));
        view.findViewById(R.id.card_mode_adb_tcp).setOnClickListener(v -> pick(Ops.MODE_ADB_OVER_TCP));
        view.findViewById(R.id.card_mode_no_root).setOnClickListener(v -> pick(Ops.MODE_NO_ROOT));
    }

    private void pick(@NonNull @Ops.Mode String mode) {
        Ops.setMode(mode);
        AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, true);
        dismissAllowingStateLoss();
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        // Cancelling without picking still counts as "shown" — don't bother the user
        // again. They land on Auto by default which auto-detects at runtime anyway.
        AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, true);
        super.onCancel(dialog);
    }
}
