// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import org.json.JSONException;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.io.Path;

public class ProfileApplierActivity extends BaseActivity {
    private static final String TAG = "ProfileApplierActivity";
    private static final String EXTRA_SHORTCUT_TYPE = "shortcut";

    public static final String EXTRA_PROFILE_ID = "prof";
    public static final String EXTRA_STATE = "state";
    private static final String EXTRA_NOTIFY = "notify";

    @StringDef({ST_SIMPLE, ST_ADVANCED})
    public @interface ShortcutType {
    }

    public static final String ST_SIMPLE = "simple";
    public static final String ST_ADVANCED = "advanced";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context,
                                           @NonNull String profileId,
                                           @ShortcutType @Nullable String shortcutType,
                                           @Nullable String state) {
        // Compatibility: Old shortcuts still store profile name instead of profile ID.
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, realProfileId);
        if (shortcutType == null) {
            if (state != null) { // State => It's a simple shortcut
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE);
                intent.putExtra(EXTRA_STATE, state);
            } else { // Otherwise it's an advance shortcut
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
            }
        } else {
            intent.putExtra(EXTRA_SHORTCUT_TYPE, shortcutType);
            if (state != null) {
                intent.putExtra(EXTRA_STATE, state);
            }
        }
        return intent;
    }

    @NonNull
    public static Intent getAutomationIntent(@NonNull Context context,
                                             @NonNull String profileId,
                                             @Nullable String state) {
        // Compatibility: Old shortcuts still store profile name instead of profile ID.
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, realProfileId);
        if (state != null) { // State => Automatic trigger
            intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE);
            intent.putExtra(EXTRA_STATE, state);
            // Avoid issuing completion notification
            intent.putExtra(EXTRA_NOTIFY, false);
        } else { // Manual trigger
            intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
        }
        return intent;
    }

    @NonNull
    public static Intent getApplierIntent(@NonNull Context context, @NonNull String profileId) {
        // Compatibility: Old shortcuts still store profile name instead of profile ID.
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, realProfileId);
        intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
        return intent;
    }

    public static class ProfileApplierInfo {
        @Nullable
        public BaseProfile profile;
        public String profileId;
        @ShortcutType
        public String shortcutType;
        @Nullable
        public String state;
        public boolean notify;
        public boolean loadFailed;
    }

    private final Queue<Intent> mQueue = new LinkedList<>();
    private ProfileApplierViewModel mViewModel;

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(ProfileApplierViewModel.class);
        synchronized (mQueue) {
            mQueue.add(getIntent());
        }
        mViewModel.mProfileLiveData.observe(this, this::handleShortcut);
        next();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        synchronized (mQueue) {
            mQueue.add(intent);
        }
        super.onNewIntent(intent);
    }

    private void next() {
        Intent intent;
        synchronized (mQueue) {
            intent = mQueue.poll();
        }
        if (intent == null) {
            finish();
            return;
        }
        @ShortcutType
        String shortcutType = intent.getStringExtra(EXTRA_SHORTCUT_TYPE);
        String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
        String profileState = intent.getStringExtra(EXTRA_STATE);
        boolean notify = intent.getBooleanExtra(EXTRA_NOTIFY, true);
        if (shortcutType == null || profileId == null) {
            // Invalid shortcut
            UIUtils.displayShortToast(R.string.profile_apply_invalid_shortcut);
            next();
            return;
        }
        ProfileApplierInfo info = new ProfileApplierInfo();
        info.profileId = profileId;
        info.shortcutType = shortcutType;
        info.state = profileState;
        info.notify = notify;
        mViewModel.loadProfile(info);
    }

    private void handleShortcut(@Nullable ProfileApplierInfo info) {
        if (info == null) {
            next();
            return;
        }
        if (info.loadFailed || info.profile == null) {
            UIUtils.displayShortToast(R.string.profile_apply_load_failed);
            next();
            return;
        }
        info.state = info.state != null ? info.state : info.profile.state;
        switch (info.shortcutType) {
            case ST_SIMPLE:
                Intent intent = ProfileApplierService.getIntent(this,
                        ProfileQueueItem.fromProfiledApplierInfo(info), info.notify);
                ContextCompat.startForegroundService(this, intent);
                next();
                break;
            case ST_ADVANCED:
                final String[] statesL = new String[]{
                        getString(R.string.on),
                        getString(R.string.off)
                };
                @BaseProfile.ProfileState final List<String> states = Arrays.asList(BaseProfile.STATE_ON, BaseProfile.STATE_OFF);
                DialogTitleBuilder titleBuilder = new DialogTitleBuilder(this)
                        .setTitle(getString(R.string.apply_profile, info.profile.name))
                        .setSubtitle(R.string.choose_a_profile_state);
                new SearchableSingleChoiceDialogBuilder<>(this, states, statesL)
                        .setTitle(titleBuilder.build())
                        .setView(createApplyReviewView(info))
                        .setSelection(info.state)
                        .setPositiveButton(R.string.apply_now, (dialog, which, selectedState) -> {
                            info.state = selectedState;
                            Intent aIntent = ProfileApplierService.getIntent(this,
                                    ProfileQueueItem.fromProfiledApplierInfo(info), info.notify);
                            ContextCompat.startForegroundService(this, aIntent);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .setOnDismissListener(dialog -> next())
                        .show();
                break;
            default:
                next();
        }
    }

    @NonNull
    private View createApplyReviewView(@NonNull ProfileApplierInfo info) {
        View view = LayoutInflater.from(this).inflate(R.layout.view_profile_apply_review, null, false);
        ((TextView) view.findViewById(R.id.profile_apply_review_summary))
                .setText(getProfileApplyReviewSummary(info.profile.type));
        ((TextView) view.findViewById(R.id.profile_apply_review_meta)).setText(getString(
                R.string.profile_apply_review_meta,
                getProfileTypeLabel(info.profile.type),
                getProfileStateLabel(info.state)));
        ((TextView) view.findViewById(R.id.profile_apply_review_id)).setText(getString(
                R.string.profile_apply_review_id, info.profile.profileId));
        return view;
    }

    private int getProfileApplyReviewSummary(int profileType) {
        if (profileType == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
            return R.string.profile_apply_review_filters_summary;
        }
        return R.string.profile_apply_review_apps_summary;
    }

    @NonNull
    private String getProfileTypeLabel(int profileType) {
        if (profileType == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
            return getString(R.string.filters);
        }
        return getString(R.string.apps);
    }

    @NonNull
    private String getProfileStateLabel(@Nullable String profileState) {
        if (BaseProfile.STATE_OFF.equals(profileState)) {
            return getString(R.string.off);
        }
        return getString(R.string.on);
    }

    public static class ProfileApplierViewModel extends AndroidViewModel {
        final MutableLiveData<ProfileApplierInfo> mProfileLiveData = new MutableLiveData<>();

        public ProfileApplierViewModel(@NonNull Application application) {
            super(application);
        }

        public void loadProfile(ProfileApplierInfo info) {
            ThreadUtils.postOnBackgroundThread(() -> {
                Path profilePath = ProfileManager.findProfilePathById(info.profileId);
                try {
                    info.profile = BaseProfile.fromPath(profilePath);
                    mProfileLiveData.postValue(info);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Failed to load profile: ", e);
                    info.loadFailed = true;
                    mProfileLiveData.postValue(info);
                }
            });
        }
    }
}
