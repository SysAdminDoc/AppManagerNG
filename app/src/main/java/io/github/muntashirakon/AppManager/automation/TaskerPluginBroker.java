// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_BACKUP_FLAGS;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_BACKUP_NAME;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_DRY_RUN;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PACKAGES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PACKAGE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_ID;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_OVERRIDES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_STATE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_URI;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_USERS;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_USER;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.muntashirakon.AppManager.profiles.ProfileApplierReceiver;
import io.github.muntashirakon.AppManager.settings.Prefs;

final class TaskerPluginBroker {
    static final String ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING";
    static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";
    static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB";

    private static final String BUNDLE_EXTRA_URI = "io.github.sysadmindoc.AppManagerNG.tasker.URI";
    private static final String BUNDLE_EXTRA_SIGNATURE = "io.github.sysadmindoc.AppManagerNG.tasker.SIGNATURE";
    private static final int MAX_URI_LENGTH = 8192;

    private TaskerPluginBroker() {
    }

    @NonNull
    static Intent buildEditResultIntent(@NonNull String uri) throws JSONException {
        return buildEditResultIntent(uri, getOrCreateSigningSecret());
    }

    @NonNull
    @VisibleForTesting
    static Intent buildEditResultIntent(@NonNull String uri, @NonNull String secret) throws JSONException {
        return new Intent()
                .putExtra(EXTRA_STRING_BLURB, buildBlurb(uri))
                .putExtra(EXTRA_BUNDLE, buildPluginBundle(uri, secret));
    }

    @Nullable
    static String getConfiguredUri(@Nullable Intent editIntent) {
        if (editIntent == null) {
            return null;
        }
        String secret = Prefs.AppActions.getTaskerPluginSigningSecret();
        Bundle bundle = editIntent.getBundleExtra(EXTRA_BUNDLE);
        return secret != null && isBundleSigned(bundle, secret) ? getUri(bundle) : null;
    }

    @Nullable
    static Intent getSignedAutomationIntent(@NonNull Context context, @Nullable Intent fireIntent) {
        if (fireIntent == null) {
            return null;
        }
        String secret = Prefs.AppActions.getTaskerPluginSigningSecret();
        if (secret == null) {
            return null;
        }
        return getSignedAutomationIntent(context, fireIntent, secret);
    }

    @Nullable
    @VisibleForTesting
    static Intent getSignedAutomationIntent(@NonNull Context context,
                                            @Nullable Intent fireIntent,
                                            @NonNull String secret) {
        if (fireIntent == null) {
            return null;
        }
        Intent automationIntent = getSignedAutomationIntent(context, fireIntent.getBundleExtra(EXTRA_BUNDLE), secret);
        if (automationIntent == null) {
            return null;
        }
        try {
            ProfileApplierReceiver.applyRuntimePackageOverride(automationIntent,
                    fireIntent.getStringExtra(ProfileApplierReceiver.EXTRA_RUNTIME_PACKAGE));
        } catch (JSONException | RuntimeException e) {
            return null;
        }
        return automationIntent;
    }

    @Nullable
    @VisibleForTesting
    static Intent getSignedAutomationIntent(@NonNull Context context,
                                            @Nullable Bundle bundle,
                                            @NonNull String secret) {
        if (!isBundleSigned(bundle, secret)) {
            return null;
        }
        try {
            AutomationRequest request = parseUri(getUri(bundle));
            if (request == null) {
                return null;
            }
            return toAutomationIntent(request).setClass(context, AutomationReceiver.class);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    static boolean isSupportedAutomationUri(@Nullable String uri) {
        try {
            return parseUri(uri) != null;
        } catch (JSONException | RuntimeException e) {
            return false;
        }
    }

    @NonNull
    @VisibleForTesting
    static Bundle buildPluginBundle(@NonNull String uri, @NonNull String secret) throws JSONException {
        AutomationRequest request = parseUri(uri);
        if (request == null) {
            throw new IllegalArgumentException("Unsupported automation URI.");
        }
        Bundle bundle = new Bundle(2);
        bundle.putString(BUNDLE_EXTRA_URI, uri.trim());
        bundle.putString(BUNDLE_EXTRA_SIGNATURE, sign(uri.trim(), secret));
        return bundle;
    }

    @Nullable
    @VisibleForTesting
    static String getUri(@Nullable Bundle bundle) {
        return bundle != null ? bundle.getString(BUNDLE_EXTRA_URI) : null;
    }

    @VisibleForTesting
    static boolean isBundleSigned(@Nullable Bundle bundle, @NonNull String secret) {
        String uri = getUri(bundle);
        String signature = bundle != null ? bundle.getString(BUNDLE_EXTRA_SIGNATURE) : null;
        if (uri == null || signature == null) {
            return false;
        }
        return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                sign(uri, secret).getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    @VisibleForTesting
    static Intent toAutomationIntent(@NonNull AutomationRequest request) {
        Intent intent = new Intent(request.action);
        if (request.packages.size() == 1) {
            intent.putExtra(EXTRA_PACKAGE, request.packages.get(0));
        } else if (!request.packages.isEmpty()) {
            intent.putStringArrayListExtra(EXTRA_PACKAGES, request.packages);
        }
        if (request.users.size() == 1) {
            intent.putExtra(EXTRA_USER, request.users.get(0));
        } else if (!request.users.isEmpty()) {
            intent.putIntegerArrayListExtra(EXTRA_USERS, request.users);
        }
        if (request.component != null) {
            intent.putExtra(EXTRA_COMPONENT, request.component);
        }
        if (request.profileId != null) {
            intent.putExtra(EXTRA_PROFILE_ID, request.profileId);
        }
        if (request.profileState != null) {
            intent.putExtra(EXTRA_PROFILE_STATE, request.profileState);
        }
        if (request.profileOverrides != null) {
            intent.putExtra(EXTRA_PROFILE_OVERRIDES, request.profileOverrides.toString());
        }
        if (request.backupName != null) {
            intent.putExtra(EXTRA_BACKUP_NAME, request.backupName);
        }
        if (request.hasBackupFlags) {
            intent.putExtra(EXTRA_BACKUP_FLAGS, request.backupFlags);
        }
        if (request.uri != null) {
            intent.putExtra(EXTRA_URI, request.uri);
        }
        if (request.dryRun) {
            intent.putExtra(EXTRA_DRY_RUN, true);
        }
        return intent;
    }

    @Nullable
    private static AutomationRequest parseUri(@Nullable String uri) throws JSONException {
        if (uri == null) {
            return null;
        }
        String trimmed = uri.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_URI_LENGTH) {
            return null;
        }
        return AutomationRequest.fromUri(Uri.parse(trimmed));
    }

    @NonNull
    private static String buildBlurb(@NonNull String uri) throws JSONException {
        AutomationRequest request = parseUri(uri);
        if (request == null) {
            throw new IllegalArgumentException("Unsupported automation URI.");
        }
        if (request.profileId != null) {
            return "Profile: " + request.profileId;
        }
        if (!request.packages.isEmpty()) {
            return request.packages.size() == 1 ? request.packages.get(0) : request.packages.size() + " packages";
        }
        if (request.uri != null) {
            return "Install: " + trimBlurb(request.uri);
        }
        return trimBlurb(uri.trim());
    }

    @NonNull
    private static String trimBlurb(@NonNull String value) {
        return value.length() <= 96 ? value : value.substring(0, 95) + "...";
    }

    @NonNull
    private static String getOrCreateSigningSecret() {
        String secret = Prefs.AppActions.getTaskerPluginSigningSecret();
        if (secret != null) {
            return secret;
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        secret = Base64.encodeToString(bytes, Base64.NO_WRAP);
        Prefs.AppActions.setTaskerPluginSigningSecret(secret);
        return secret;
    }

    @NonNull
    private static String sign(@NonNull String uri, @NonNull String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.encodeToString(mac.doFinal(uri.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign Tasker plug-in bundle.", e);
        }
    }
}
