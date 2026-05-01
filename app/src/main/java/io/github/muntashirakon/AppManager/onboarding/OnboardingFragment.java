// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.onboarding;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;

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

    /** Optional callback fired after the user picks a mode or cancels. */
    @Nullable
    private Runnable mOnDismissCallback;

    /**
     * Wires a continuation to run on the host activity once the user has finished
     * with the sheet (picked a mode or cancelled). Used by MainActivity to chain
     * the main-list tour onto the same first-launch flow without forcing the
     * user to navigate away and back. The callback fires from the dialog's
     * {@link androidx.fragment.app.DialogFragment#onDismiss} hook so it runs for
     * both pick-a-card and cancel paths.
     */
    public void setOnDismissCallback(@Nullable Runnable callback) {
        mOnDismissCallback = callback;
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mOnDismissCallback != null) {
            // Defer to the next main-thread tick so the BottomSheet's exit animation
            // finishes before the follow-up dialog covers the same screen region.
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(mOnDismissCallback);
        }
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
        // Live capability surfacing — re-evaluate every time the sheet binds rather
        // than caching at fragment construction so a user who toggles ADB and
        // returns to the sheet sees the new state.
        TextView rootStatus = view.findViewById(R.id.status_root);
        bindCapabilityStatus(rootStatus, Ops.hasRoot(),
                R.string.onboarding_mode_root_status_detected,
                R.string.onboarding_mode_root_status_missing);
        TextView adbWifiStatus = view.findViewById(R.id.status_adb_wifi);
        bindCapabilityStatus(adbWifiStatus, isWirelessDebuggingActive(),
                R.string.onboarding_mode_adb_wifi_status_active,
                R.string.onboarding_mode_adb_wifi_status_inactive);
        TextView adbTcpStatus = view.findViewById(R.id.status_adb_tcp);
        bindCapabilityStatus(adbTcpStatus, isUsbDebuggingEnabled(),
                R.string.onboarding_mode_adb_tcp_status_active,
                R.string.onboarding_mode_adb_tcp_status_inactive);
        bindCardActions(view, R.id.card_mode_auto, Ops.MODE_AUTO,
                R.string.onboarding_mode_auto_title, R.string.onboarding_mode_auto_explainer);
        bindCardActions(view, R.id.card_mode_root, Ops.MODE_ROOT,
                R.string.onboarding_mode_root_title, R.string.onboarding_mode_root_explainer);
        bindCardActions(view, R.id.card_mode_adb_wifi, Ops.MODE_ADB_WIFI,
                R.string.onboarding_mode_adb_wifi_title, R.string.onboarding_mode_adb_wifi_explainer);
        bindCardActions(view, R.id.card_mode_adb_tcp, Ops.MODE_ADB_OVER_TCP,
                R.string.onboarding_mode_adb_tcp_title, R.string.onboarding_mode_adb_tcp_explainer);
        bindCardActions(view, R.id.card_mode_no_root, Ops.MODE_NO_ROOT,
                R.string.onboarding_mode_no_root_title, R.string.onboarding_mode_no_root_explainer);
    }

    private void bindCapabilityStatus(@Nullable TextView statusView, boolean available,
                                      int availableTextRes, int unavailableTextRes) {
        if (statusView == null) return;
        int color = MaterialColors.getColor(statusView, available
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        statusView.setText(available ? availableTextRes : unavailableTextRes);
        statusView.setTextColor(color);
        statusView.setCompoundDrawablePadding(getResources()
                .getDimensionPixelSize(io.github.muntashirakon.ui.R.dimen.padding_very_small));
        Drawable icon = ContextCompat.getDrawable(requireContext(), available
                ? R.drawable.ic_check_circle
                : io.github.muntashirakon.ui.R.drawable.ic_caution);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, color);
        }
        statusView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    /**
     * Wire tap-to-pick + long-press-to-explain on a mode card. Tap commits the choice
     * via {@link #pick}; long-press surfaces an extended explainer dialog so users
     * unfamiliar with root / ADB / Shizuku terminology can read more before picking
     * without committing first. The footer hint advertises the long-press affordance.
     */
    private void bindCardActions(@NonNull View root, int cardId,
                                 @NonNull @Ops.Mode String mode,
                                 int titleRes, int explainerRes) {
        View card = root.findViewById(cardId);
        if (card == null) return;
        card.setOnClickListener(v -> pick(mode));
        card.setOnLongClickListener(v -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(titleRes)
                    .setMessage(explainerRes)
                    .setPositiveButton(R.string.onboarding_explainer_pick_this, (d, w) -> pick(mode))
                    .setNegativeButton(R.string.close, null)
                    .show();
            return true;
        });
    }

    /** True when USB debugging (ADB over USB) is enabled in Developer options. */
    private boolean isUsbDebuggingEnabled() {
        try {
            return android.provider.Settings.Global.getInt(
                    requireContext().getContentResolver(), "adb_enabled", 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when Android 11+ Wireless Debugging is currently active. Returns false on
     * Android &lt; 11 (the setting key didn't exist) and on any read failure (we'd
     * rather show "not active" conservatively than mislead the user).
     */
    private boolean isWirelessDebuggingActive() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return false;
        }
        try {
            return android.provider.Settings.Global.getInt(
                    requireContext().getContentResolver(), "adb_wifi_enabled", 0) != 0;
        } catch (Throwable t) {
            return false;
        }
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
