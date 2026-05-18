// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import android.app.KeyguardManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.BiometricAuthenticatorsCompat;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public final class ActionAuthGate {
    private ActionAuthGate() {
    }

    public static boolean canAuthenticate(@NonNull Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isKeyguardSecure();
    }

    public static void authenticate(@NonNull FragmentActivity activity, @StringRes int titleRes,
                                    @NonNull Runnable onAuthenticated) {
        if (!Prefs.Privacy.isActionAuthGateEnabled()) {
            onAuthenticated.run();
            return;
        }
        if (!canAuthenticate(activity)) {
            UIUtils.displayLongToast(R.string.screen_lock_not_enabled);
            return;
        }
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_CANCELED
                                && errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            UIUtils.displayLongToast(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        onAuthenticated.run();
                    }
                });
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(titleRes))
                .setAllowedAuthenticators(new BiometricAuthenticatorsCompat.Builder().allowEverything(true).build())
                .build();
        biometricPrompt.authenticate(promptInfo);
    }
}
