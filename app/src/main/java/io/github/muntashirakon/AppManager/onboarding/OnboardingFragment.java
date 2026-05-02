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
        refreshCapabilityStatuses(view);
        bindCardActions(view, R.id.card_mode_auto, R.id.info_mode_auto, View.NO_ID, Ops.MODE_AUTO,
                R.string.onboarding_mode_auto_title, R.string.onboarding_mode_auto_summary,
                R.string.onboarding_mode_auto_explainer);
        bindCardActions(view, R.id.card_mode_root, R.id.info_mode_root, R.id.status_root, Ops.MODE_ROOT,
                R.string.onboarding_mode_root_title, R.string.onboarding_mode_root_summary,
                R.string.onboarding_mode_root_explainer);
        bindCardActions(view, R.id.card_mode_adb_wifi, R.id.info_mode_adb_wifi, R.id.status_adb_wifi,
                Ops.MODE_ADB_WIFI, R.string.onboarding_mode_adb_wifi_title,
                R.string.onboarding_mode_adb_wifi_summary, R.string.onboarding_mode_adb_wifi_explainer);
        bindCardActions(view, R.id.card_mode_adb_tcp, R.id.info_mode_adb_tcp, R.id.status_adb_tcp,
                Ops.MODE_ADB_OVER_TCP, R.string.onboarding_mode_adb_tcp_title,
                R.string.onboarding_mode_adb_tcp_summary, R.string.onboarding_mode_adb_tcp_explainer);
        bindCardActions(view, R.id.card_mode_no_root, R.id.info_mode_no_root, View.NO_ID, Ops.MODE_NO_ROOT,
                R.string.onboarding_mode_no_root_title, R.string.onboarding_mode_no_root_summary,
                R.string.onboarding_mode_no_root_explainer);
        // Highlight the currently active mode card so a user replaying the wizard
        // from Settings can see at a glance which mode is in effect. Nothing to
        // highlight on a true first-run — the saved mode will already be the
        // default (MODE_AUTO) and that's still meaningful information.
        highlightActiveMode(view, Ops.getMode());
        // "Re-check capabilities" — for users who toggle Wireless debugging or
        // grant root from another app while the sheet is open. Re-bind the
        // capability badges and the active-mode highlight without dismissing
        // the sheet, then confirm with a Snackbar so the action is visible
        // even when the badges already showed the new state.
        View recheck = view.findViewById(R.id.btn_recheck);
        if (recheck != null) {
            recheck.setOnClickListener(v -> {
                refreshCapabilityStatuses(view);
                highlightActiveMode(view, Ops.getMode());
                com.google.android.material.snackbar.Snackbar.make(view,
                        R.string.onboarding_recheck_done,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * Re-evaluate every capability badge against the current device state. Called
     * on bind and from the "Re-check" button so a user who toggles Wireless
     * debugging from quick-settings or grants root from another app can see the
     * new state without dismissing the sheet.
     */
    private void refreshCapabilityStatuses(@NonNull View view) {
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
    }

    /**
     * Ring the currently-active mode card with the M3 primary stroke so the
     * picker doubles as a "current state" view when replayed. Other cards keep
     * the default outlined style from {@code Widget.AppTheme.CardView.OnboardingMode}.
     */
    private void highlightActiveMode(@NonNull View root, @NonNull String activeMode) {
        int activeCardId = cardIdFor(activeMode);
        int[] allCards = {R.id.card_mode_auto, R.id.card_mode_root, R.id.card_mode_adb_wifi,
                R.id.card_mode_adb_tcp, R.id.card_mode_no_root};
        int active = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary);
        int strokeWidthActive = (int) (2 * getResources().getDisplayMetrics().density);
        for (int id : allCards) {
            View v = root.findViewById(id);
            if (!(v instanceof com.google.android.material.card.MaterialCardView)) continue;
            com.google.android.material.card.MaterialCardView card =
                    (com.google.android.material.card.MaterialCardView) v;
            if (id == activeCardId) {
                card.setStrokeColor(active);
                card.setStrokeWidth(strokeWidthActive);
                card.setContentDescription(getString(R.string.onboarding_mode_card_active_a11y,
                        card.getContentDescription() != null ? card.getContentDescription() : ""));
            }
        }
    }

    private int cardIdFor(@NonNull String mode) {
        switch (mode) {
            case Ops.MODE_ROOT:        return R.id.card_mode_root;
            case Ops.MODE_ADB_WIFI:    return R.id.card_mode_adb_wifi;
            case Ops.MODE_ADB_OVER_TCP:return R.id.card_mode_adb_tcp;
            case Ops.MODE_NO_ROOT:     return R.id.card_mode_no_root;
            case Ops.MODE_AUTO:
            default:                   return R.id.card_mode_auto;
        }
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
     * Wire tap-to-pick plus a visible details affordance on a mode card. Tap commits
     * the choice via {@link #pick}; details/long-press surfaces an extended explainer
     * dialog so users can read more before picking without committing first.
     */
    private void bindCardActions(@NonNull View root, int cardId, int infoId, int statusId,
                                 @NonNull @Ops.Mode String mode,
                                 int titleRes, int summaryRes, int explainerRes) {
        View card = root.findViewById(cardId);
        if (card == null) return;
        String title = getString(titleRes);
        String summary = getString(summaryRes);
        TextView status = statusId != View.NO_ID ? root.findViewById(statusId) : null;
        CharSequence statusText = status != null ? status.getText() : null;
        card.setContentDescription(statusText != null && statusText.length() > 0
                ? getString(R.string.onboarding_mode_card_status_a11y, title, summary, statusText)
                : getString(R.string.onboarding_mode_card_a11y, title, summary));
        card.setOnClickListener(v -> pick(mode));
        card.setOnLongClickListener(v -> {
            showModeExplainer(mode, titleRes, explainerRes);
            return true;
        });
        View info = root.findViewById(infoId);
        if (info != null) {
            info.setContentDescription(getString(R.string.onboarding_mode_info_a11y, title));
            info.setOnClickListener(v -> showModeExplainer(mode, titleRes, explainerRes));
        }
    }

    private void showModeExplainer(@NonNull @Ops.Mode String mode, int titleRes, int explainerRes) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setMessage(explainerRes)
                .setPositiveButton(R.string.onboarding_explainer_pick_this, (d, w) -> pick(mode))
                .setNegativeButton(R.string.close, null)
                .show();
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
        // Warn the user before committing to Root mode if su isn't detected on this
        // device — picking it anyway is fine (some setups hide su until first
        // request) but the explainer dialog gives them a chance to bail without
        // burning the onboarding-shown flag.
        if (Ops.MODE_ROOT.equals(mode) && !Ops.hasRoot()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.onboarding_mode_root_title)
                    .setMessage(R.string.onboarding_mode_root_pick_without_detection)
                    .setPositiveButton(R.string.onboarding_explainer_pick_this, (d, w) -> commit(mode))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        commit(mode);
    }

    private void commit(@NonNull @Ops.Mode String mode) {
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
