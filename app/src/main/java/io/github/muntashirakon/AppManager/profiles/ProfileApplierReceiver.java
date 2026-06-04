// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RUN_PROFILE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_ID;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_OVERRIDES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_STATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;

import io.github.muntashirakon.AppManager.automation.AutomationReceiver;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class ProfileApplierReceiver extends BroadcastReceiver {
    private static final String TAG = ProfileApplierReceiver.class.getSimpleName();

    public static final String EXTRA_RUNTIME_PACKAGE = "extra_pkg";

    @NonNull
    public static Intent getAutomationIntent(@NonNull Context context,
                                             @NonNull String profileId,
                                             @Nullable String state) {
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierReceiver.class);
        intent.putExtra(ProfileApplierActivity.EXTRA_PROFILE_ID, realProfileId);
        if (state != null) {
            intent.putExtra(ProfileApplierActivity.EXTRA_STATE, state);
        }
        return intent;
    }

    public static void applyRuntimePackageOverride(@NonNull Intent intent, @Nullable String packageName)
            throws JSONException {
        if (!ACTION_RUN_PROFILE.equals(intent.getAction())) {
            return;
        }
        JSONObject overrides = mergeRuntimePackageOverride(
                getProfileOverrides(intent.getStringExtra(EXTRA_PROFILE_OVERRIDES)), packageName);
        if (overrides != null) {
            intent.putExtra(EXTRA_PROFILE_OVERRIDES, overrides.toString());
        }
    }

    @Nullable
    public static JSONObject mergeRuntimePackageOverride(@Nullable JSONObject overrides,
                                                         @Nullable String packageName)
            throws JSONException {
        String normalizedPackageName = normalizeRuntimePackage(packageName);
        if (normalizedPackageName == null) {
            return overrides;
        }
        JSONObject merged = overrides != null ? new JSONObject(overrides.toString()) : new JSONObject();
        merged.put("packages", new JSONArray(Collections.singletonList(normalizedPackageName)));
        return merged;
    }

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String profileId = firstNonEmpty(intent.getStringExtra(ProfileApplierActivity.EXTRA_PROFILE_ID),
                intent.getStringExtra(EXTRA_PROFILE_ID));
        if (profileId == null) {
            Log.w(TAG, "Ignoring profile receiver request with no profile id.");
            return;
        }
        Intent automationIntent = new Intent(ACTION_RUN_PROFILE)
                .setClass(context, AutomationReceiver.class)
                .putExtra(EXTRA_PROFILE_ID, ProfileManager.getProfileIdCompat(profileId));
        String state = firstNonEmpty(intent.getStringExtra(ProfileApplierActivity.EXTRA_STATE),
                intent.getStringExtra(EXTRA_PROFILE_STATE));
        if (state != null) {
            automationIntent.putExtra(EXTRA_PROFILE_STATE, state);
        }
        try {
            applyRuntimePackageOverride(automationIntent, intent.getStringExtra(EXTRA_RUNTIME_PACKAGE));
        } catch (JSONException | IllegalArgumentException e) {
            Log.w(TAG, "Ignoring invalid runtime package override.", e);
            return;
        }
        context.sendBroadcast(automationIntent);
    }

    @Nullable
    private static JSONObject getProfileOverrides(@Nullable String value) throws JSONException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new JSONObject(value.trim());
    }

    @Nullable
    private static String normalizeRuntimePackage(@Nullable String packageName) {
        String trimmedPackage = packageName != null ? packageName.trim() : null;
        if (trimmedPackage == null || trimmedPackage.isEmpty()) {
            return null;
        }
        if (!PackageUtils.validateName(trimmedPackage)) {
            throw new IllegalArgumentException("Invalid " + EXTRA_RUNTIME_PACKAGE + ": " + trimmedPackage);
        }
        return trimmedPackage;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        String firstValue = trimToNull(first);
        return firstValue != null ? firstValue : trimToNull(second);
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
