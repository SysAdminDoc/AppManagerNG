// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static io.github.muntashirakon.AppManager.BaseActivity.ASKED_PERMISSIONS;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbPairingService;
import io.github.muntashirakon.AppManager.compat.BiometricAuthenticatorsCompat;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreActivity;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.SupportInfoBundle;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModel;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.settings.StartupInitState;
import io.github.muntashirakon.AppManager.settings.StartupInitUiState;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    public static final String TAG = SplashActivity.class.getSimpleName();

    @Nullable
    private TextView mStateNameView;
    @Nullable
    private CircularProgressIndicator mStartupProgressView;
    @Nullable
    private LinearLayoutCompat mRecoveryActionsView;
    private SecurityAndOpsViewModel mViewModel;
    private BiometricPrompt mBiometricPrompt;
    private final Handler mStartupInitHandler = new Handler(Looper.getMainLooper());
    private long mStartupInitTimeoutAttemptId;
    private long mDisplayedStartupAttemptId;
    private final Runnable mStartupInitTimeoutRunnable = () -> {
        if (mViewModel != null && mStartupInitTimeoutAttemptId > 0) {
            mViewModel.timeoutStartupInitAttempt(mStartupInitTimeoutAttemptId,
                    getString(R.string.startup_init_timeout_detail));
        }
    };

    private final ActivityResultLauncher<Intent> mKeyStoreActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // Need authentication and/or verify mode of operation
                ensureSecurityAndModeOfOp();
            });
    private final ActivityResultLauncher<String[]> mPermissionCheckActivity = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissionStatusMap -> {
                // Run authentication
                doAuthenticate();
            });

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(Prefs.Appearance.isPureBlackTheme() ? R.style.AppTheme_Splash_Black : R.style.AppTheme_Splash);
        SplashScreen.installSplashScreen(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_authentication);
        ((TextView) findViewById(R.id.version)).setText(String.format(Locale.ROOT, "%s (%d)",
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        mStateNameView = findViewById(R.id.state_name);
        mStartupProgressView = findViewById(R.id.startup_progress);
        mRecoveryActionsView = findViewById(R.id.startup_recovery_actions);
        if (Ops.isAuthenticated()) {
            Log.d(TAG, "Already authenticated.");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (Boolean.TRUE.equals(BuildExpiryChecker.buildExpired())) {
            // Build has expired
            BuildExpiryChecker.getBuildExpiredDialog(this, (dialog, which) -> doAuthenticate()).show();
            return;
        }
        // Init permission checks
        if (!initPermissionChecks()) {
            // Run authentication
            doAuthenticate();
        }
    }

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @CallSuper
    @Override
    protected void onDestroy() {
        mStartupInitHandler.removeCallbacks(mStartupInitTimeoutRunnable);
        super.onDestroy();
    }

    private void doAuthenticate() {
        mViewModel = new ViewModelProvider(this).get(SecurityAndOpsViewModel.class);
        mBiometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        finishAndRemoveTask();
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        handleMigrationAndModeOfOp();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });
        Log.d(TAG, "Waiting to be authenticated.");
        mViewModel.startupInitState().observe(this, this::bindStartupInitState);
        mViewModel.authenticationStatus().observe(this, status -> {
            switch (status) {
                case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                    Log.d(TAG, "Try auto-connecting to wireless debugging.");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mViewModel.autoConnectWirelessDebugging();
                        return;
                    } // fall-through
                case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                    Log.d(TAG, "Display wireless debugging chooser (pair or connect)");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.connectWirelessDebugging(this, mViewModel);
                        return;
                    } // fall-through
                case Ops.STATUS_ADB_CONNECT_REQUIRED:
                    Log.d(TAG, "Display connect dialog.");
                    Ops.connectAdbInput(this, mViewModel);
                    return;
                case Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED:
                    Log.d(TAG, "Request Shizuku permission.");
                    Ops.requestShizukuPermission(this, mViewModel);
                    return;
                case Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED:
                    Log.d(TAG, "Local network permission required for wireless debugging.");
                    Ops.displayLocalNetworkPermissionMessage(this, mViewModel);
                    return;
                case Ops.STATUS_ADB_PAIRING_REQUIRED:
                    Log.d(TAG, "Display pairing dialog.");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.pairAdbInput(this, mViewModel);
                        return;
                    } // fall-through
                case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                    Ops.displayIncompleteUsbDebuggingMessage(this);
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILURE:
                    Log.d(TAG, "Authentication completed.");
                    mViewModel.setAuthenticating(false);
                    Ops.setAuthenticated(this, true);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
            }
        });
        if (!mViewModel.isAuthenticating()) {
            mViewModel.setAuthenticating(true);
            // Check KeyStore
            if (KeyStoreManager.hasKeyStorePassword()) {
                // We already have a working keystore password.
                // Only need authentication and/or verify mode of operation.
                ensureSecurityAndModeOfOp();
                return;
            }
            Intent keyStoreIntent = new Intent(this, KeyStoreActivity.class)
                    .putExtra(KeyStoreActivity.EXTRA_KS, true);
            mKeyStoreActivity.launch(keyStoreIntent);
        }
    }

    private void ensureSecurityAndModeOfOp() {
        if (!Prefs.Privacy.isScreenLockEnabled()) {
            // No security enabled
            handleMigrationAndModeOfOp();
            return;
        }
        Log.d(TAG, "Security enabled.");
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            // Screen lock enabled
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.unlock_app_manager))
                    .setAllowedAuthenticators(new BiometricAuthenticatorsCompat.Builder().allowEverything(true).build())
                    .build();
            mBiometricPrompt.authenticate(promptInfo);
        } else {
            // Screen lock disabled
            UIUtils.displayLongToast(R.string.screen_lock_not_enabled);
            finishAndRemoveTask();
        }
    }

    private void handleMigrationAndModeOfOp() {
        // Authentication was successful
        Log.d(TAG, "Authenticated");
        if (mStateNameView != null) {
            mStateNameView.setText(R.string.initializing);
        }
        // Set mode of operation
        if (mViewModel != null) {
            mViewModel.setModeOfOps();
        }
    }

    private boolean initPermissionChecks() {
        List<String> permissionsToBeAsked = new ArrayList<>(ASKED_PERMISSIONS.size());
        for (String permission : ASKED_PERMISSIONS.keySet()) {
            boolean required = Boolean.TRUE.equals(ASKED_PERMISSIONS.get(permission));
            if (required && !SelfPermissions.checkSelfPermission(permission)) {
                permissionsToBeAsked.add(permission);
            }
        }
        if (!permissionsToBeAsked.isEmpty()) {
            // Ask required permissions
            mPermissionCheckActivity.launch(permissionsToBeAsked.toArray(new String[0]));
            return true;
        }
        return false;
    }

    private void bindStartupInitState(@Nullable StartupInitState state) {
        if (state == null) {
            return;
        }
        mDisplayedStartupAttemptId = state.getAttemptId();
        StartupInitUiState uiState = StartupInitUiState.from(state);
        if (mStateNameView != null) {
            mStateNameView.setText(uiState.getMessageRes());
        }
        if (mStartupProgressView != null) {
            mStartupProgressView.setVisibility(uiState.isProgressVisible() ? View.VISIBLE : View.GONE);
        }
        bindRecoveryActions(uiState.getActions());
        scheduleStartupInitTimeout(state);
    }

    private void bindRecoveryActions(@NonNull List<StartupInitUiState.Action> actions) {
        if (mRecoveryActionsView == null) {
            return;
        }
        mRecoveryActionsView.removeAllViews();
        if (actions.isEmpty()) {
            mRecoveryActionsView.setVisibility(View.GONE);
            return;
        }
        int marginTop = getResources().getDimensionPixelSize(R.dimen.premium_space_8);
        int iconPadding = getResources().getDimensionPixelSize(R.dimen.premium_space_8);
        mRecoveryActionsView.setVisibility(View.VISIBLE);
        for (StartupInitUiState.Action action : actions) {
            MaterialButton button = new MaterialButton(this);
            button.setText(action.getLabelRes());
            button.setIconResource(action.getIconRes());
            button.setIconPadding(iconPadding);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setCornerRadiusResource(R.dimen.premium_radius_control);
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            button.setMaxLines(2);
            button.setOnClickListener(v -> handleStartupRecoveryAction(action.getAction()));
            LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (mRecoveryActionsView.getChildCount() > 0) {
                params.topMargin = marginTop;
            }
            mRecoveryActionsView.addView(button, params);
        }
    }

    private void scheduleStartupInitTimeout(@NonNull StartupInitState state) {
        mStartupInitHandler.removeCallbacks(mStartupInitTimeoutRunnable);
        mStartupInitTimeoutAttemptId = 0;
        if (mViewModel == null || state.getStatus() != StartupInitState.Status.RUNNING
                || state.getDeadlineAtMillis() <= 0) {
            return;
        }
        mStartupInitTimeoutAttemptId = state.getAttemptId();
        long delayMillis = state.getDeadlineAtMillis() - System.currentTimeMillis();
        if (delayMillis <= 0) {
            mStartupInitTimeoutRunnable.run();
            return;
        }
        mStartupInitHandler.postDelayed(mStartupInitTimeoutRunnable, delayMillis);
    }

    private void handleStartupRecoveryAction(@NonNull StartupInitState.RecoveryAction action) {
        switch (action) {
            case REQUEST_LOCAL_NETWORK_PERMISSION:
                if (mViewModel != null) {
                    Ops.displayLocalNetworkPermissionMessage(this, mViewModel);
                }
                return;
            case REQUEST_SHIZUKU_PERMISSION:
                if (mViewModel != null) {
                    Ops.requestShizukuPermission(this, mViewModel);
                }
                return;
            case CANCEL_PAIRING:
                cancelStartupPairing();
                return;
            case CHOOSE_MODE:
                openStartupSettings("mode_of_operations");
                return;
            case MODE_DOCTOR:
                openStartupSettings("privilege_health", "privilege_health_mode_doctor");
                return;
            case SUPPORT_BUNDLE:
                shareSupportInfoBundle();
                return;
            case RETRY:
            default:
                handleMigrationAndModeOfOp();
        }
    }

    private void cancelStartupPairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startService(AdbPairingService.getStopSearchingIntent(this));
        }
        if (mViewModel != null && mDisplayedStartupAttemptId > 0) {
            mViewModel.cancelStartupInitAttempt(mDisplayedStartupAttemptId,
                    getString(R.string.startup_init_pairing_cancelled_detail));
        }
    }

    private void openStartupSettings(@NonNull String... paths) {
        if (mViewModel != null) {
            mViewModel.setAuthenticating(false);
        }
        Ops.setAuthenticated(this, true);
        startActivity(SettingsActivity.getSettingsIntent(this, paths));
        finish();
    }

    private void shareSupportInfoBundle() {
        Context appContext = getApplicationContext();
        Toast.makeText(this, R.string.support_info_bundle_preparing, Toast.LENGTH_SHORT).show();
        ThreadUtils.postOnBackgroundThread(() -> {
            Path bundlePath = null;
            try {
                bundlePath = SupportInfoBundle.writeTextBundle(appContext);
            } catch (Throwable ignored) {
            }
            Path finalBundlePath = bundlePath;
            ThreadUtils.postOnMainThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalBundlePath == null) {
                    Toast.makeText(this, R.string.support_info_bundle_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent shareIntent = SupportInfoBundle.buildShareIntent(this, finalBundlePath);
                try {
                    startActivity(Intent.createChooser(shareIntent,
                            getString(R.string.support_info_bundle_share_title)));
                } catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(this, R.string.no_apps_to_handle, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
