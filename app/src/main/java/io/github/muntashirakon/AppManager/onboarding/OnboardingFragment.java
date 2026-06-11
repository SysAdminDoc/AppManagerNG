// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.onboarding;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbTcpipProbe;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuBridge;
import io.github.muntashirakon.AppManager.runner.RootManagerInfo;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * First-run picker for the privilege level AppManagerNG should use (Auto / Root /
 * Wireless ADB / ADB over TCP / No-root). Shown once after the user has accepted
 * the disclaimer; tracked via {@link AppPref.PrefKey#PREF_ONBOARDING_SHOWN_BOOL}.
 *
 * <p>The sheet primarily writes the user's choice to {@link Ops#setMode(String)}.
 * Wireless ADB also exposes a first-run setup affordance because pairing is an
 * explicit user-guided flow; other privilege bring-up still happens from the
 * normal mode initialisation path.
 *
 * <p>For Root, the card surfaces a "✓ Detected" / "Not detected" status by calling
 * {@link Ops#hasRoot()} on bind. Wireless ADB / ADB-TCP / No-root are always
 * pickable because their availability depends on user-configurable state we can't
 * cheaply check before the user has actually opted in (e.g. ADB pairing requires
 * permission).
 */
public class OnboardingFragment extends BottomSheetDialogFragment {
    public static final String TAG = "OnboardingFragment";
    private static final String MIN_RECOMMENDED_ADB_SECURITY_PATCH = "2026-05-01";
    private static final int STATUS_BADGE_BACKGROUND_ALPHA = 0x22;
    private static final int STATUS_BADGE_STROKE_ALPHA = 0x66;

    /** True when this fragment has not yet been dismissed for this user. */
    public static boolean shouldShow() {
        return !AppPref.getBoolean(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL);
    }

    /** Optional callback fired after the user picks a mode or cancels. */
    @Nullable
    private Runnable mOnDismissCallback;
    @Nullable
    private Ops.AdbConnectionInterface mWirelessSetupCallback;
    private boolean mAdbTcpipSessionDetected;

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

    /**
     * The fragment's bound root view, retained between onViewCreated and onResume so cached
     * capability badges can be rebound after focus changes without re-running root/ADB probes.
     * Cleared in onDestroyView to avoid retaining a detached view.
     */
    @Nullable
    private View mBoundView;
    @Nullable
    private CapabilitySnapshot mCapabilitySnapshot;
    private int mCapabilityProbeGeneration;
    private boolean mNotificationPermissionPromptShown;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBoundView = view;
        bindAdbPatchLevelWarning(view);
        refreshCapabilityStatuses(view, false);
        bindCardActions(view, R.id.card_mode_auto, R.id.info_mode_auto, View.NO_ID, Ops.MODE_AUTO,
                R.string.onboarding_mode_auto_title, R.string.onboarding_mode_auto_summary,
                R.string.onboarding_mode_auto_explainer);
        bindCardActions(view, R.id.card_mode_root, R.id.info_mode_root, R.id.status_root, Ops.MODE_ROOT,
                R.string.onboarding_mode_root_title, R.string.onboarding_mode_root_summary,
                R.string.onboarding_mode_root_explainer);
        bindCardActions(view, R.id.card_mode_shizuku, R.id.info_mode_shizuku, R.id.status_shizuku,
                Ops.MODE_SHIZUKU, R.string.onboarding_mode_shizuku_title,
                R.string.onboarding_mode_shizuku_summary, R.string.onboarding_mode_shizuku_explainer);
        bindCardActions(view, R.id.card_mode_adb_wifi, R.id.info_mode_adb_wifi, R.id.status_adb_wifi,
                Ops.MODE_ADB_WIFI, R.string.onboarding_mode_adb_wifi_title,
                R.string.onboarding_mode_adb_wifi_summary, R.string.onboarding_mode_adb_wifi_explainer);
        bindCardActions(view, R.id.card_mode_adb_tcp, R.id.info_mode_adb_tcp, R.id.status_adb_tcp,
                Ops.MODE_ADB_OVER_TCP, R.string.onboarding_mode_adb_tcp_title,
                R.string.onboarding_mode_adb_tcp_summary, R.string.onboarding_mode_adb_tcp_explainer);
        bindCardActions(view, R.id.card_mode_no_root, R.id.info_mode_no_root, View.NO_ID, Ops.MODE_NO_ROOT,
                R.string.onboarding_mode_no_root_title, R.string.onboarding_mode_no_root_summary,
                R.string.onboarding_mode_no_root_explainer);
        View setupWirelessAdb = view.findViewById(R.id.action_setup_adb_wifi);
        if (setupWirelessAdb != null) {
            setupWirelessAdb.setOnClickListener(v -> startWirelessAdbSetup());
        }
        View useAdbTcpip = view.findViewById(R.id.action_use_adb_tcpip);
        if (useAdbTcpip != null) {
            useAdbTcpip.setVisibility(View.GONE);
            useAdbTcpip.setOnClickListener(v -> startExistingAdbTcpipSession());
        }
        // Highlight the currently active mode card so a user replaying the wizard
        // from Settings can see at a glance which mode is in effect. Nothing to
        // highlight on a true first-run — the saved mode will already be the
        // default (MODE_AUTO) and that's still meaningful information.
        highlightActiveMode(view, Ops.getMode());
        bindNextStepTiles(view);
        maybePromptForNotificationPermission(view);
        // "Re-check capabilities" — for users who toggle Wireless debugging or
        // grant root from another app while the sheet is open. Re-bind the
        // capability badges and the active-mode highlight without dismissing
        // the sheet, then confirm with a Snackbar so the action is visible
        // even when the badges already showed the new state.
        View recheck = view.findViewById(R.id.btn_recheck);
        if (recheck != null) {
            recheck.setOnClickListener(v -> {
                refreshCapabilityStatuses(view, true);
                highlightActiveMode(view, Ops.getMode());
                com.google.android.material.snackbar.Snackbar.make(view,
                        R.string.onboarding_recheck_done,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            });
        }
    }

    private void bindAdbPatchLevelWarning(@NonNull View view) {
        TextView warning = view.findViewById(R.id.warning_adb_patch_level);
        if (warning == null) return;
        String securityPatch = getSecurityPatchLevel();
        if (!isSecurityPatchBefore(securityPatch, MIN_RECOMMENDED_ADB_SECURITY_PATCH)) {
            warning.setVisibility(View.GONE);
            return;
        }
        warning.setText(getString(R.string.onboarding_adb_patch_level_warning, securityPatch));
        applyWarningTextStyle(warning);
        warning.setVisibility(View.VISIBLE);
    }

    private void bindShizukuWarning(@Nullable TextView warning, @NonNull CapabilitySnapshot snapshot) {
        if (warning == null) return;
        if (snapshot.shizukuRootBacked) {
            warning.setText(R.string.onboarding_mode_shizuku_root_backed_warning);
            applyWarningTextStyle(warning);
            warning.setOnClickListener(v -> startWirelessAdbSetup());
            warning.setVisibility(View.VISIBLE);
            return;
        }
        if (snapshot.shizukuOemWarning != null) {
            warning.setText(getString(snapshot.shizukuOemWarning.bannerTextRes,
                    snapshot.shizukuOemWarning.fallbackVersion));
            applyWarningTextStyle(warning);
            warning.setOnClickListener(v -> openShizukuPinnedArchive());
            warning.setVisibility(View.VISIBLE);
            return;
        }
        if (!snapshot.shizukuAndroid17Risk) {
            warning.setOnClickListener(null);
            warning.setVisibility(View.GONE);
            return;
        }
        warning.setText(R.string.onboarding_mode_shizuku_android17_warning);
        applyWarningTextStyle(warning);
        warning.setOnClickListener(v -> startWirelessAdbSetup());
        warning.setVisibility(View.VISIBLE);
    }

    private void applyWarningTextStyle(@NonNull TextView warning) {
        // Resolve from the theme attribute (not the raw color) so AMOLED keeps the dark pair.
        int warningColor = MaterialColors.getColor(warning, R.attr.premiumWarningContent,
                ContextCompat.getColor(requireContext(), R.color.premium_warning_content));
        warning.setTextColor(warningColor);
        warning.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.premium_space_8));
        Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_security_network);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, warningColor);
        }
        warning.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        warning.setContentDescription(warning.getText());
    }

    @Nullable
    private static String getSecurityPatchLevel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        return Build.VERSION.SECURITY_PATCH;
    }

    @VisibleForTesting
    static boolean isSecurityPatchBefore(@Nullable String securityPatch, @NonNull String minimumPatch) {
        if (!isIsoDate(securityPatch) || !isIsoDate(minimumPatch)) {
            return false;
        }
        return securityPatch.compareTo(minimumPatch) < 0;
    }

    private static boolean isIsoDate(@Nullable String value) {
        if (value == null || value.length() != 10) {
            return false;
        }
        return Character.isDigit(value.charAt(0))
                && Character.isDigit(value.charAt(1))
                && Character.isDigit(value.charAt(2))
                && Character.isDigit(value.charAt(3))
                && value.charAt(4) == '-'
                && Character.isDigit(value.charAt(5))
                && Character.isDigit(value.charAt(6))
                && value.charAt(7) == '-'
                && Character.isDigit(value.charAt(8))
                && Character.isDigit(value.charAt(9));
    }

    /**
     * Re-evaluate every capability badge against the current device state. The first bind
     * captures a snapshot; later lifecycle resumes only re-bind that snapshot. The explicit
     * "Re-check" action is the only post-bind path that refreshes root/Shizuku/Dhizuku/ADB
     * probes.
     */
    private void refreshCapabilityStatuses(@NonNull View view, boolean forceProbe) {
        CapabilitySnapshot snapshot = mCapabilitySnapshot;
        boolean runProbes = shouldRunCapabilityProbe(forceProbe, snapshot);
        if (runProbes) {
            snapshot = captureCapabilitySnapshot();
            mCapabilitySnapshot = snapshot;
            mCapabilityProbeGeneration++;
        }
        bindCapabilityStatuses(view, snapshot, mCapabilityProbeGeneration, runProbes);
    }

    @VisibleForTesting
    static boolean shouldRunCapabilityProbe(boolean forceProbe, @Nullable Object snapshot) {
        return forceProbe || snapshot == null;
    }

    @VisibleForTesting
    static boolean shouldPromptForNotificationPermission(int sdk, boolean permissionGranted,
                                                         boolean promptAlreadyShown) {
        return sdk >= Build.VERSION_CODES.TIRAMISU && !permissionGranted && !promptAlreadyShown;
    }

    @NonNull
    private CapabilitySnapshot captureCapabilitySnapshot() {
        CapabilitySnapshot snapshot = new CapabilitySnapshot();
        snapshot.rootAvailable = Ops.hasRoot();
        snapshot.shizukuRecommendedVersion = ShizukuBridge.isRecommendedManagerVersion(requireContext());
        snapshot.shizukuVersionName = ShizukuBridge.getInstalledVersionName(requireContext());
        snapshot.shizukuHasPermission = ShizukuBridge.hasPermission();
        snapshot.shizukuRootBacked = ShizukuBridge.isRootBacked();
        snapshot.shizukuSupportsUserService = ShizukuBridge.supportsUserService();
        snapshot.shizukuOemWarning = ShizukuBridge.getOemCompatibilityWarning(requireContext());
        snapshot.shizukuAndroid17Risk = ShizukuBridge.hasAndroid17CompatibilityRisk(requireContext());
        snapshot.shizukuOfferAutoStart = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ShizukuBridge.shouldOfferTrustedWlanAutoStart(requireContext());
        snapshot.dhizukuResult = DhizukuBridge.probe(requireContext());
        snapshot.wirelessDebuggingActive = isWirelessDebuggingActive();
        snapshot.hasPairedAdbDevice = ServerConfig.hasPairedAdbDevice();
        snapshot.usbDebuggingEnabled = isUsbDebuggingEnabled();
        snapshot.adbTcpipSessionDetected = mAdbTcpipSessionDetected;
        return snapshot;
    }

    private void bindCapabilityStatuses(@NonNull View view, @NonNull CapabilitySnapshot snapshot,
                                        int generation, boolean runAsyncProbes) {
        TextView rootStatus = view.findViewById(R.id.status_root);
        bindCapabilityStatus(rootStatus, snapshot.rootAvailable,
                R.string.onboarding_mode_root_status_detected,
                R.string.onboarding_mode_root_status_missing);
        bindRootManagerStatus(rootStatus, snapshot.rootManagerInfo);
        TextView shizukuStatus = view.findViewById(R.id.status_shizuku);
        TextView shizukuAutoStartHint = view.findViewById(R.id.hint_shizuku_autostart);
        View shizukuAutoStartAction = view.findViewById(R.id.action_shizuku_autostart);
        bindShizukuStatus(shizukuStatus, shizukuAutoStartHint, shizukuAutoStartAction, snapshot);
        TextView dhizukuStatus = view.findViewById(R.id.status_dhizuku);
        bindDhizukuStatus(dhizukuStatus, snapshot);
        TextView shizukuAndroid17Warning = view.findViewById(R.id.warning_shizuku_android17);
        bindShizukuWarning(shizukuAndroid17Warning, snapshot);
        TextView adbWifiStatus = view.findViewById(R.id.status_adb_wifi);
        bindAdbWifiStatus(adbWifiStatus, snapshot);
        TextView adbTcpStatus = view.findViewById(R.id.status_adb_tcp);
        View adbTcpipAction = view.findViewById(R.id.action_use_adb_tcpip);
        bindAdbTcpStatus(adbTcpStatus, adbTcpipAction, snapshot);
        // Run the root-manager probe off the UI thread — marker checks shell out
        // when root is granted (~50–150ms). Without root, falls back to a fast
        // PackageManager lookup. Either way, we don't want to block bind().
        if (runAsyncProbes) {
            refreshRootManagerStatus(rootStatus, snapshot, generation);
            refreshAdbTcpipSessionStatus(adbTcpStatus, adbTcpipAction, snapshot, generation);
        }
    }

    /**
     * Async probe for which root manager (Magisk / KernelSU / APatch) owns
     * {@code /data/adb}, plus whether ZygiskNext is layered on top. When
     * resolved, appends e.g. " · Magisk" or " · KernelSU + ZygiskNext" to the
     * already-bound root status line so users replaying the wizard see exactly
     * which root provider is live.
     */
    private void refreshRootManagerStatus(@Nullable TextView rootStatus, @NonNull CapabilitySnapshot snapshot,
                                          int generation) {
        if (rootStatus == null) return;
        // Capture the Application context now, on the main thread, so a fragment that's been
        // detached by the time the background task starts can't trigger an
        // IllegalStateException out of requireContext() — the runnable lives on a worker thread
        // pool and any throw there is uncaught.
        final android.content.Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            RootManagerInfo info = RootManagerInfo.detect(appContext);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || generation != mCapabilityProbeGeneration || snapshot != mCapabilitySnapshot) {
                    return;
                }
                snapshot.rootManagerInfo = info;
                bindRootManagerStatus(rootStatus, info);
            });
        });
    }

    private void bindRootManagerStatus(@Nullable TextView rootStatus, @Nullable RootManagerInfo info) {
        if (rootStatus == null || info == null) return;
        CharSequence base = rootStatus.getText();
        if (base == null) return;
        String suffix = buildRootManagerSuffix(info);
        if (suffix.isEmpty()) return;
        // Idempotent: avoid stacking suffixes if cached status is rebound repeatedly.
        String baseStr = base.toString();
        int sepIdx = baseStr.indexOf(" · ");
        if (sepIdx >= 0) baseStr = baseStr.substring(0, sepIdx);
        rootStatus.setText(baseStr + " · " + suffix);
    }

    @NonNull
    private static String buildRootManagerSuffix(@NonNull RootManagerInfo info) {
        String name = info.displayName();
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name);
        if (info.suiPresent) {
            sb.append(" + Sui");
        }
        if (info.zygiskNextPresent) {
            sb.append(" + ZygiskNext");
        }
        return sb.toString();
    }

    /**
     * Ring the currently-active mode card with the M3 primary stroke so the
     * picker doubles as a "current state" view when replayed. Other cards keep
     * the default outlined style from {@code Widget.AppTheme.CardView.OnboardingMode}.
     */
    private void highlightActiveMode(@NonNull View root, @NonNull String activeMode) {
        int activeCardId = cardIdFor(activeMode);
        int[] allCards = {R.id.card_mode_auto, R.id.card_mode_root, R.id.card_mode_shizuku, R.id.card_mode_adb_wifi,
                R.id.card_mode_adb_tcp, R.id.card_mode_no_root};
        int active = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary);
        int inactive = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutlineVariant);
        int strokeWidthActive = getResources().getDimensionPixelSize(R.dimen.premium_stroke_focus);
        int strokeWidthInactive = getResources().getDimensionPixelSize(R.dimen.premium_stroke_hairline);
        for (int id : allCards) {
            View v = root.findViewById(id);
            if (!(v instanceof com.google.android.material.card.MaterialCardView)) continue;
            com.google.android.material.card.MaterialCardView card =
                    (com.google.android.material.card.MaterialCardView) v;
            Object tag = card.getTag();
            CharSequence baseDescription = tag instanceof CharSequence
                    ? (CharSequence) tag
                    : card.getContentDescription();
            if (id == activeCardId) {
                card.setStrokeColor(active);
                card.setStrokeWidth(strokeWidthActive);
                card.setContentDescription(getString(R.string.onboarding_mode_card_active_a11y,
                        baseDescription != null ? baseDescription : ""));
            } else {
                card.setStrokeColor(inactive);
                card.setStrokeWidth(strokeWidthInactive);
                card.setContentDescription(baseDescription);
            }
        }
    }

    private int cardIdFor(@NonNull String mode) {
        switch (mode) {
            case Ops.MODE_ROOT:        return R.id.card_mode_root;
            case Ops.MODE_SHIZUKU:     return R.id.card_mode_shizuku;
            case Ops.MODE_ADB_WIFI:    return R.id.card_mode_adb_wifi;
            case Ops.MODE_ADB_OVER_TCP:return R.id.card_mode_adb_tcp;
            case Ops.MODE_NO_ROOT:     return R.id.card_mode_no_root;
            case Ops.MODE_AUTO:
            default:                   return R.id.card_mode_auto;
        }
    }

    private void bindCapabilityStatus(@Nullable TextView statusView, boolean available,
                                      int availableTextRes, int unavailableTextRes) {
        bindCapabilityStatus(statusView, available, availableTextRes, unavailableTextRes, null);
    }

    private void bindCapabilityStatus(@Nullable TextView statusView, boolean available,
                                      int availableTextRes, int unavailableTextRes,
                                      @Nullable Object[] formatArgs) {
        if (statusView == null) return;
        int color = MaterialColors.getColor(statusView, available
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        int textRes = available ? availableTextRes : unavailableTextRes;
        statusView.setText(formatArgs != null ? getString(textRes, formatArgs) : getString(textRes));
        statusView.setTextColor(color);
        applyStatusBadgeStyle(statusView, color);
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

    private void applyStatusBadgeStyle(@NonNull TextView statusView, int contentColor) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(ColorUtils.setAlphaComponent(contentColor, STATUS_BADGE_BACKGROUND_ALPHA));
        background.setStroke(
                getResources().getDimensionPixelSize(R.dimen.premium_stroke_hairline),
                ColorUtils.setAlphaComponent(contentColor, STATUS_BADGE_STROKE_ALPHA));
        background.setCornerRadius(getResources().getDimensionPixelSize(R.dimen.premium_radius_control));
        statusView.setBackground(background);
    }

    private void bindShizukuStatus(@Nullable TextView statusView, @Nullable TextView autoStartHint,
                                   @Nullable View autoStartAction, @NonNull CapabilitySnapshot snapshot) {
        if (statusView == null) return;
        int statusRes;
        Object[] args = null;
        boolean available;
        if (!snapshot.shizukuRecommendedVersion && snapshot.shizukuSupportsUserService
                && snapshot.shizukuVersionName != null) {
            statusRes = R.string.onboarding_mode_shizuku_status_update_recommended;
            args = new Object[]{snapshot.shizukuVersionName};
            available = false;
        } else if (snapshot.shizukuHasPermission) {
            if (snapshot.shizukuRootBacked) {
                statusRes = R.string.onboarding_mode_shizuku_status_root_backed;
                available = false;
            } else {
                statusRes = R.string.onboarding_mode_shizuku_status_ready;
                available = true;
            }
        } else if (snapshot.shizukuSupportsUserService) {
            statusRes = R.string.onboarding_mode_shizuku_status_needs_permission;
            available = true;
        } else {
            statusRes = R.string.onboarding_mode_shizuku_status_missing;
            available = false;
        }
        bindCapabilityStatus(statusView, available, statusRes, statusRes, args);
        bindShizukuAutoStartHint(autoStartHint, autoStartAction, snapshot);
    }

    private void bindDhizukuStatus(@Nullable TextView statusView, @NonNull CapabilitySnapshot snapshot) {
        if (statusView == null) return;
        DhizukuBridge.Result result = snapshot.dhizukuResult;
        if (!result.isInstalled() && !result.isOfficialOwner()) {
            statusView.setVisibility(View.GONE);
            return;
        }
        statusView.setVisibility(View.VISIBLE);
        int statusRes;
        boolean available;
        if (DhizukuBridge.isBelowMinimumSupportedAndroidVersion(result.sdk)) {
            statusRes = R.string.onboarding_mode_dhizuku_status_unsupported;
            available = false;
        } else if (result.isOfficialOwner()) {
            statusRes = result.apiPermissionGranted
                    ? R.string.onboarding_mode_dhizuku_status_ready
                    : R.string.onboarding_mode_dhizuku_status_needs_permission;
            available = result.apiPermissionGranted;
        } else {
            statusRes = R.string.onboarding_mode_dhizuku_status_inactive;
            available = false;
        }
        int color = MaterialColors.getColor(statusView, available
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        statusView.setText(statusRes);
        statusView.setTextColor(color);
        statusView.setBackground(null);
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

    private void bindShizukuAutoStartHint(@Nullable TextView autoStartHint, @Nullable View autoStartAction,
                                          @NonNull CapabilitySnapshot snapshot) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (autoStartHint != null) {
                autoStartHint.setVisibility(View.GONE);
            }
            if (autoStartAction != null) {
                autoStartAction.setVisibility(View.GONE);
            }
            return;
        }
        if (autoStartHint != null) {
            autoStartHint.setVisibility(View.VISIBLE);
            autoStartHint.setText(snapshot.shizukuRecommendedVersion
                    ? R.string.onboarding_mode_shizuku_autostart_tip
                    : R.string.onboarding_mode_shizuku_update_for_autostart);
        }
        if (autoStartAction != null) {
            autoStartAction.setVisibility(snapshot.shizukuOfferAutoStart ? View.VISIBLE : View.GONE);
            autoStartAction.setOnClickListener(v -> openShizukuAutoStartSettings());
        }
    }

    private void openShizukuAutoStartSettings() {
        Intent intent = ShizukuBridge.getTrustedWlanAutoStartIntent(requireContext());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            UIUtils.displayShortToast(R.string.shizuku_autostart_open_failed);
        }
    }

    private void openShizukuPinnedArchive() {
        try {
            startActivity(ShizukuBridge.getPinnedSafeManagerArchiveIntent());
        } catch (ActivityNotFoundException | SecurityException e) {
            UIUtils.displayShortToast(R.string.shizuku_oem_archive_open_failed);
        }
    }

    private void bindAdbWifiStatus(@Nullable TextView statusView, @NonNull CapabilitySnapshot snapshot) {
        if (statusView == null) return;
        if (snapshot.wirelessDebuggingActive) {
            bindCapabilityStatus(statusView, true,
                    R.string.onboarding_mode_adb_wifi_status_active,
                    R.string.onboarding_mode_adb_wifi_status_active);
        } else if (snapshot.hasPairedAdbDevice) {
            bindCapabilityStatus(statusView, false,
                    R.string.onboarding_mode_adb_wifi_status_paired,
                    R.string.onboarding_mode_adb_wifi_status_paired);
        } else {
            bindCapabilityStatus(statusView, false,
                    R.string.onboarding_mode_adb_wifi_status_inactive,
                    R.string.onboarding_mode_adb_wifi_status_inactive);
        }
    }

    private void bindAdbTcpStatus(@Nullable TextView statusView, @Nullable View useTcpipAction,
                                  @NonNull CapabilitySnapshot snapshot) {
        if (snapshot.adbTcpipSessionDetected) {
            bindCapabilityStatus(statusView, true,
                    R.string.onboarding_mode_adb_tcp_status_tcpip_detected,
                    R.string.onboarding_mode_adb_tcp_status_tcpip_detected,
                    new Object[]{AdbTcpipProbe.DEFAULT_TCPIP_PORT});
        } else {
            bindCapabilityStatus(statusView, snapshot.usbDebuggingEnabled,
                    R.string.onboarding_mode_adb_tcp_status_active,
                    R.string.onboarding_mode_adb_tcp_status_inactive);
        }
        if (useTcpipAction != null) {
            useTcpipAction.setVisibility(snapshot.adbTcpipSessionDetected ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshAdbTcpipSessionStatus(@Nullable TextView statusView, @Nullable View useTcpipAction,
                                              @NonNull CapabilitySnapshot snapshot, int generation) {
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean reachable = AdbTcpipProbe.isDefaultTcpipSessionReachable();
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || generation != mCapabilityProbeGeneration || snapshot != mCapabilitySnapshot) {
                    return;
                }
                mAdbTcpipSessionDetected = reachable;
                snapshot.adbTcpipSessionDetected = reachable;
                bindAdbTcpStatus(statusView, useTcpipAction, snapshot);
            });
        });
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
        card.setTag(card.getContentDescription());
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

    private void startWirelessAdbSetup() {
        startWirelessAdbSetup(false, false);
    }

    private void startWirelessAdbSetup(boolean skipUsbDebuggingPreflight) {
        startWirelessAdbSetup(skipUsbDebuggingPreflight, true);
    }

    private void startWirelessAdbSetup(boolean skipUsbDebuggingPreflight, boolean skipTcpipPrompt) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.onboarding_mode_adb_wifi_title)
                    .setMessage(R.string.onboarding_mode_adb_wifi_requires_android_11)
                    .setPositiveButton(R.string.onboarding_mode_adb_tcp_title, (d, w) -> commit(Ops.MODE_ADB_OVER_TCP))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (!skipTcpipPrompt && mAdbTcpipSessionDetected) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.onboarding_adb_tcpip_detected_title)
                    .setMessage(R.string.onboarding_adb_tcpip_detected_message)
                    .setPositiveButton(R.string.onboarding_adb_tcpip_use_existing,
                            (d, w) -> startExistingAdbTcpipSession())
                    .setNeutralButton(R.string.onboarding_mode_adb_wifi_setup,
                            (d, w) -> startWirelessAdbSetup(false, true))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (!skipUsbDebuggingPreflight && !isUsbDebuggingEnabled()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.onboarding_adb_wifi_enable_usb_debugging_title)
                    .setMessage(R.string.onboarding_adb_wifi_enable_usb_debugging_message)
                    .setPositiveButton(R.string.open, (d, w) -> openDeveloperOptions())
                    .setNeutralButton(R.string.action_continue, (d, w) -> startWirelessAdbSetup(true, true))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        FragmentActivity activity = requireActivity();
        Ops.AdbConnectionInterface callback = getWirelessSetupCallback(activity);
        Ops.setMode(Ops.MODE_ADB_WIFI);
        AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, true);
        mOnDismissCallback = null;
        dismissAllowingStateLoss();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (ServerConfig.hasPairedAdbDevice()) {
                Ops.connectWirelessDebugging(activity, callback);
            } else {
                Ops.pairAdbInput(activity, callback);
            }
        });
    }

    private void startExistingAdbTcpipSession() {
        FragmentActivity activity = requireActivity();
        Ops.setMode(Ops.MODE_ADB_OVER_TCP);
        ServerConfig.setAdbPort(AdbTcpipProbe.DEFAULT_TCPIP_PORT);
        AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, true);
        mOnDismissCallback = null;
        dismissAllowingStateLoss();
        ThreadUtils.postOnBackgroundThread(() -> {
            int status = Ops.connectAdb(activity.getApplicationContext(),
                    AdbTcpipProbe.DEFAULT_TCPIP_PORT, Ops.STATUS_FAILURE);
            ThreadUtils.postOnMainThread(() -> handleAdbTcpipSetupStatus(activity, status));
        });
    }

    @NonNull
    private Ops.AdbConnectionInterface getWirelessSetupCallback(@NonNull FragmentActivity activity) {
        if (mWirelessSetupCallback != null) return mWirelessSetupCallback;
        mWirelessSetupCallback = new Ops.AdbConnectionInterface() {
            @Override
            public void connectAdb(int port) {
                ThreadUtils.postOnBackgroundThread(() -> {
                    int status = Ops.connectAdb(activity.getApplicationContext(), port, Ops.STATUS_FAILURE);
                    ThreadUtils.postOnMainThread(() -> handleWirelessSetupStatus(activity, status));
                });
            }

            @Override
            public void pairAdb() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    onStatusReceived(Ops.STATUS_FAILURE);
                    return;
                }
                ThreadUtils.postOnBackgroundThread(() -> {
                    int status = Ops.pairAdb(activity.getApplicationContext());
                    ThreadUtils.postOnMainThread(() -> handleWirelessSetupStatus(activity, status));
                });
            }

            @Override
            public void onStatusReceived(int status) {
                ThreadUtils.postOnMainThread(() -> handleWirelessSetupStatus(activity, status));
            }
        };
        return mWirelessSetupCallback;
    }

    private void handleWirelessSetupStatus(@NonNull FragmentActivity activity, @Ops.Status int status) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Ops.AdbConnectionInterface callback = getWirelessSetupCallback(activity);
        switch (status) {
            case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ThreadUtils.postOnBackgroundThread(() -> {
                        int next = Ops.autoConnectWirelessDebugging(activity.getApplicationContext());
                        ThreadUtils.postOnMainThread(() -> handleWirelessSetupStatus(activity, next));
                    });
                }
                return;
            case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Ops.connectWirelessDebugging(activity, callback);
                }
                return;
            case Ops.STATUS_ADB_CONNECT_REQUIRED:
                Ops.connectAdbInput(activity, callback);
                return;
            case Ops.STATUS_ADB_PAIRING_REQUIRED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Ops.pairAdbInput(activity, callback);
                }
                return;
            case Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED:
                Ops.displayLocalNetworkPermissionMessage(activity, callback);
                return;
            case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                Ops.displayIncompleteUsbDebuggingMessage(activity);
                return;
            case Ops.STATUS_SUCCESS:
                UIUtils.displayShortToast(R.string.adb_pairing_connected);
                return;
            case Ops.STATUS_FAILURE:
            default:
                UIUtils.displayShortToast(R.string.adb_pairing_not_finished);
        }
    }

    private void handleAdbTcpipSetupStatus(@NonNull FragmentActivity activity, @Ops.Status int status) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        switch (status) {
            case Ops.STATUS_SUCCESS:
                UIUtils.displayShortToast(R.string.adb_tcpip_connected);
                return;
            case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                Ops.displayIncompleteUsbDebuggingMessage(activity);
                return;
            case Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED:
                Ops.displayLocalNetworkPermissionMessage(activity, getWirelessSetupCallback(activity));
                return;
            case Ops.STATUS_FAILURE:
            default:
                UIUtils.displayShortToast(R.string.adb_tcpip_not_finished);
        }
    }

    /**
     * Wire the three "Next steps" tiles on the final onboarding card: jump to
     * Settings -> Privileges (Mode Doctor home), Permission Inspector, and
     * Settings -> Backup/Restore. Each tile dismisses the sheet on tap so the
     * destination screen is the user's next focus.
     */
    private void bindNextStepTiles(@NonNull View root) {
        bindNotificationPermissionTile(root);
        View privileges = root.findViewById(R.id.onboarding_next_step_privileges);
        if (privileges != null) {
            privileges.setOnClickListener(v -> launchNextStep(
                    io.github.muntashirakon.AppManager.settings.SettingsActivity
                            .getSettingsIntent(requireContext(), "privilege_health")));
        }
        View permInspector = root.findViewById(R.id.onboarding_next_step_permission_inspector);
        if (permInspector != null) {
            permInspector.setOnClickListener(v -> launchNextStep(new android.content.Intent(requireContext(),
                    io.github.muntashirakon.AppManager.permissions.PermissionInspectorActivity.class)));
        }
        View backup = root.findViewById(R.id.onboarding_next_step_backup);
        if (backup != null) {
            backup.setOnClickListener(v -> launchNextStep(
                    io.github.muntashirakon.AppManager.settings.SettingsActivity
                            .getSettingsIntent(requireContext(), "backup_restore_prefs")));
        }
    }

    private void launchNextStep(@NonNull android.content.Intent intent) {
        try {
            startActivity(intent);
        } catch (Throwable ignored) {
            // Fall through; sheet stays open so the user can pick another tile.
            return;
        }
        // Mark onboarding as shown so a fresh-install user who explores the next
        // step does not get the wizard again on the next launch.
        AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, true);
        dismissAllowingStateLoss();
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

    private void openDeveloperOptions() {
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignore) {
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
        if (Ops.MODE_ADB_WIFI.equals(mode)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.onboarding_mode_adb_wifi_title)
                        .setMessage(R.string.onboarding_mode_adb_wifi_requires_android_11)
                        .setPositiveButton(R.string.onboarding_mode_adb_tcp_title, (d, w) -> commit(Ops.MODE_ADB_OVER_TCP))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return;
            }
            if (!isWirelessDebuggingActive() || !ServerConfig.hasPairedAdbDevice()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.onboarding_mode_adb_wifi_title)
                        .setMessage(R.string.onboarding_mode_adb_wifi_setup_prompt)
                        .setPositiveButton(R.string.onboarding_mode_adb_wifi_setup, (d, w) -> startWirelessAdbSetup())
                        .setNeutralButton(R.string.onboarding_mode_adb_wifi_pick_only, (d, w) -> commit(mode))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return;
            }
        }
        commit(mode);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-bind cached capability badges when the wizard regains focus without
        // shelling out or probing ADB again. Users can tap Re-check for a fresh read.
        if (mBoundView != null) {
            refreshCapabilityStatuses(mBoundView, false);
        }
    }

    private void bindNotificationPermissionTile(@NonNull View root) {
        View notifications = root.findViewById(R.id.onboarding_next_step_notifications);
        if (notifications == null) return;
        boolean granted = SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted) {
            notifications.setVisibility(View.GONE);
            notifications.setOnClickListener(null);
            return;
        }
        notifications.setVisibility(View.VISIBLE);
        notifications.setOnClickListener(v -> requestNotificationPermission(root));
    }

    private void maybePromptForNotificationPermission(@NonNull View root) {
        boolean granted = SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
        if (!shouldPromptForNotificationPermission(Build.VERSION.SDK_INT, granted,
                mNotificationPermissionPromptShown)) {
            return;
        }
        mNotificationPermissionPromptShown = true;
        root.post(() -> {
            if (isAdded()) {
                requestNotificationPermission(root);
            }
        });
    }

    private void requestNotificationPermission(@NonNull View root) {
        FragmentActivity activity = requireActivity();
        NotificationUtils.requestPostNotificationsForWorkflow(activity,
                R.string.onboarding_notification_permission_title,
                R.string.onboarding_notification_permission_message,
                () -> {
                    if (isAdded()) {
                        bindNotificationPermissionTile(root);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        mWirelessSetupCallback = null;
        mBoundView = null;
        super.onDestroyView();
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

    private static final class CapabilitySnapshot {
        boolean rootAvailable;
        boolean shizukuRecommendedVersion;
        @Nullable
        String shizukuVersionName;
        boolean shizukuHasPermission;
        boolean shizukuRootBacked;
        boolean shizukuSupportsUserService;
        @Nullable
        ShizukuBridge.OemCompatibilityWarning shizukuOemWarning;
        boolean shizukuAndroid17Risk;
        boolean shizukuOfferAutoStart;
        @NonNull
        DhizukuBridge.Result dhizukuResult;
        boolean wirelessDebuggingActive;
        boolean hasPairedAdbDevice;
        boolean usbDebuggingEnabled;
        boolean adbTcpipSessionDetected;
        @Nullable
        RootManagerInfo rootManagerInfo;
    }
}
