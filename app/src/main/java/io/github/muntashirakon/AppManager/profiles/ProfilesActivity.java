// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ST_ADVANCED;
import static io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ST_SIMPLE;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class ProfilesActivity extends BaseActivity implements NewProfileDialogFragment.OnCreateNewProfileInterface {
    private static final String TAG = "ProfilesActivity";

    private ProfilesAdapter mAdapter;
    private ProfilesViewModel mModel;
    private LinearProgressIndicator mProgressIndicator;
    private TextView mProfilesSummaryView;
    @Nullable
    private String mProfileId;

    private final ActivityResultLauncher<String> mExportProfile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                if (mProfileId != null) {
                    // Export profile
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) {
                            return;
                        }
                        Path profilePath = ProfileManager.findProfilePathById(mProfileId);
                        BaseProfile profile = BaseProfile.fromPath(profilePath);
                        profile.write(os);
                        UIUtils.displayShortToast(R.string.the_export_was_successful);
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Error: ", e);
                        UIUtils.displayShortToast(R.string.export_failed);
                    }
                }
            });
    private final ActivityResultLauncher<String> mImportProfile = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    // Verify
                    Path profilePath = Paths.get(uri);
                    BaseProfile profile = BaseProfile.fromPath(profilePath);
                    BaseProfile newProfile = BaseProfile.newProfile(profile.name, profile.type, profile);
                    Path innerProfilePath = ProfileManager.requireProfilePathById(newProfile.profileId);
                    // Save
                    try (OutputStream os = innerProfilePath.openOutputStream()) {
                        newProfile.write(os);
                    }
                    UIUtils.displayShortToast(R.string.the_import_was_successful);
                    // Load imported profile
                    startActivity(ProfileManager.getProfileIntent(this, newProfile.type, newProfile.profileId));
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Error: ", e);
                    UIUtils.displayShortToast(R.string.import_failed);
                }
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_profiles);
        setSupportActionBar(findViewById(R.id.toolbar));
        mModel = new ViewModelProvider(this).get(ProfilesViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProfilesSummaryView = findViewById(R.id.profiles_summary);
        showProfilesLoadingState();
        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        View emptyView = findViewById(android.R.id.empty);
        setupEmptyState(emptyView);
        listView.setEmptyView(emptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new ProfilesAdapter(this);
        mAdapter.setHasStableIds(true);
        listView.setAdapter(mAdapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.setOnClickListener(v -> showNewProfileDialog());
        mModel.getProfilesLiveData().observe(this, profiles -> {
            mProgressIndicator.hide();
            mAdapter.setDefaultList(profiles);
        });
        mProgressIndicator.show();
        mModel.loadProfiles();
    }

    private void showProfilesLoadingState() {
        mProfilesSummaryView.setText(R.string.profiles_status_loading);
        mProfilesSummaryView.setVisibility(View.VISIBLE);
    }

    private void updateProfilesSummary(int shownCount, int totalCount) {
        if (totalCount == 0) {
            mProfilesSummaryView.setVisibility(View.GONE);
            return;
        }
        mProfilesSummaryView.setVisibility(View.VISIBLE);
        if (shownCount == totalCount) {
            mProfilesSummaryView.setText(getResources().getQuantityString(
                    R.plurals.profiles_status_all, totalCount, totalCount));
        } else {
            mProfilesSummaryView.setText(getResources().getQuantityString(
                    R.plurals.profiles_status_filtered, totalCount, shownCount, totalCount));
        }
    }

    private void setupEmptyState(@NonNull View emptyView) {
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_view_agenda);
        ((TextView) emptyView.findViewById(R.id.empty_state_title)).setText(R.string.profiles_empty_title);
        ((TextView) emptyView.findViewById(R.id.empty_state_summary)).setText(R.string.profiles_empty_message);
        MaterialButton action = emptyView.findViewById(R.id.empty_state_action);
        action.setText(R.string.new_profile);
        action.setIconResource(R.drawable.ic_add);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> showNewProfileDialog());
    }

    private void showNewProfileDialog() {
        NewProfileDialogFragment dialog = NewProfileDialogFragment.getInstance(this);
        dialog.show(getSupportFragmentManager(), NewProfileDialogFragment.TAG);
    }

    /**
     * Share a profile's JSON definition via {@link Intent#ACTION_SEND}. Mirrors
     * the existing Export-to-file action but skips the SAF round-trip when the
     * user just wants to ping the profile to another device, a chat, or email
     * for cross-device sync. The receiving NG instance can re-import via the
     * Import action; share targets that need a file should use Export instead.
     */
    private void shareProfileAsJson(@NonNull BaseProfile profile) {
        try {
            Path profilePath = ProfileManager.findProfilePathById(profile.profileId);
            BaseProfile loaded = BaseProfile.fromPath(profilePath);
            String json = loaded.serializeToJson().toString(2);
            String subject = getString(R.string.share_profile_subject, profile.name);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TITLE, profile.name + ".am.json")
                    .putExtra(Intent.EXTRA_TEXT, json);
            startActivity(Intent.createChooser(send, getString(R.string.share_profile_chooser_title)));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.e(TAG, "Share failed: ", e);
            UIUtils.displayShortToast(R.string.share_failed);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_profiles_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_import) {
            mImportProfile.launch("application/json");
        } else if (id == R.id.action_refresh) {
            mProgressIndicator.show();
            showProfilesLoadingState();
            mModel.loadProfiles();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onCreateNewProfile(@NonNull String newProfileName, int type) {
        Intent intent = ProfileManager.getNewProfileIntent(this, type, newProfileName);
        startActivity(intent);
    }

    static class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.ViewHolder> implements Filterable {
        private Filter mFilter;
        private String mConstraint;
        private BaseProfile[] mDefaultList;
        private BaseProfile[] mAdapterList;
        private HashMap<BaseProfile, CharSequence> mAdapterMap;
        private final ProfilesActivity mActivity;
        private final int mQueryStringHighlightColor;

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView summary;
            TextView profileType;
            TextView profileState;
            MaterialButton applyButton;
            MaterialButton moreButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.title);
                summary = itemView.findViewById(android.R.id.summary);
                profileType = itemView.findViewById(R.id.profile_type);
                profileState = itemView.findViewById(R.id.profile_state);
                applyButton = itemView.findViewById(R.id.profile_apply);
                moreButton = itemView.findViewById(R.id.profile_more);
            }
        }

        ProfilesAdapter(@NonNull ProfilesActivity activity) {
            mActivity = activity;
            mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        }

        void setDefaultList(@NonNull HashMap<BaseProfile, CharSequence> list) {
            mDefaultList = list.keySet().toArray(new BaseProfile[0]);
            Arrays.sort(mDefaultList, (first, second) -> {
                int result = first.name.compareToIgnoreCase(second.name);
                if (result != 0) {
                    return result;
                }
                return first.profileId.compareTo(second.profileId);
            });
            int previousCount = getItemCount();
            mAdapterList = mDefaultList;
            mAdapterMap = list;
            AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList.length);
            mActivity.updateProfilesSummary(mAdapterList.length, mDefaultList.length);
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public long getItemId(int position) {
            return mAdapterList[position].profileId.hashCode();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BaseProfile profile = mAdapterList[position];
            if (!TextUtils.isEmpty(mConstraint) && profile.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.title.setText(UIUtils.getHighlightedText(profile.name, mConstraint, mQueryStringHighlightColor));
            } else {
                holder.title.setText(profile.name);
            }
            CharSequence value = mAdapterMap.get(profile);
            CharSequence summary = value != null ? value : "";
            holder.summary.setText(summary);
            holder.summary.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
            CharSequence profileType = getProfileTypeLabel(profile.type);
            CharSequence profileState = getProfileStateLabel(profile.state);
            holder.profileType.setText(profileType);
            holder.profileState.setText(mActivity.getString(R.string.profile_state_badge, profileState));
            holder.itemView.setContentDescription(mActivity.getString(
                    R.string.profile_item_content_description, profile.name, profileType, profileState, summary));
            holder.applyButton.setContentDescription(mActivity.getString(
                    R.string.profile_apply_content_description, profile.name));
            holder.moreButton.setContentDescription(mActivity.getString(
                    R.string.profile_more_actions_content_description, profile.name));
            holder.itemView.setOnClickListener(v -> openProfile(profile));
            holder.applyButton.setOnClickListener(v -> applyProfile(profile));
            holder.moreButton.setOnClickListener(v -> showProfileActions(v, profile));
            holder.itemView.setOnLongClickListener(v -> {
                showProfileActions(v, profile);
                return true;
            });
        }

        private void openProfile(@NonNull BaseProfile profile) {
            Intent intent = ProfileManager.getProfileIntent(mActivity, profile.type, profile.profileId);
            mActivity.startActivity(intent);
        }

        private void applyProfile(@NonNull BaseProfile profile) {
            Intent intent = ProfileApplierActivity.getApplierIntent(mActivity, profile.profileId);
            mActivity.startActivity(intent);
        }

        private void showProfileActions(@NonNull View anchor, @NonNull BaseProfile profile) {
            PopupMenu popupMenu = new PopupMenu(mActivity, anchor);
            popupMenu.setForceShowIcon(true);
            popupMenu.inflate(R.menu.activity_profiles_popup_actions);
            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_apply) {
                    applyProfile(profile);
                } else if (id == R.id.action_delete) {
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(mActivity.getString(R.string.delete_filename, profile.name))
                            .setMessage(R.string.profile_delete_confirmation)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete, (dialog, which) -> {
                                if (ProfileManager.deleteProfile(profile.profileId)) {
                                    UIUtils.displayShortToast(R.string.deleted_successfully);
                                    mActivity.mProgressIndicator.show();
                                    mActivity.showProfilesLoadingState();
                                    mActivity.mModel.loadProfiles();
                                } else {
                                    UIUtils.displayShortToast(R.string.deletion_failed);
                                }
                            })
                            .show();
                } else if (id == R.id.action_duplicate) {
                    new TextInputDialogBuilder(mActivity, R.string.input_profile_name)
                            .setTitle(R.string.new_profile)
                            .setHelperText(R.string.input_profile_name_description)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.go, (dialog, which, newProfName, isChecked) -> {
                                if (!TextUtils.isEmpty(newProfName)) {
                                    Intent intent = ProfileManager.getCloneProfileIntent(
                                            mActivity, profile.type, profile.profileId,
                                            newProfName.toString());
                                    mActivity.startActivity(intent);
                                }
                            })
                            .show();
                } else if (id == R.id.action_export) {
                    mActivity.mProfileId = profile.profileId;
                    mActivity.mExportProfile.launch(profile.name + ".am.json");
                } else if (id == R.id.action_share) {
                    mActivity.shareProfileAsJson(profile);
                } else if (id == R.id.action_copy) {
                    Utils.copyToClipboard(mActivity, profile.name, profile.profileId);
                } else if (id == R.id.action_shortcut) {
                    final String[] shortcutTypesL = new String[]{
                            mActivity.getString(R.string.simple),
                            mActivity.getString(R.string.advanced)
                    };
                    final String[] shortcutTypes = new String[]{ST_SIMPLE, ST_ADVANCED};
                    new SearchableSingleChoiceDialogBuilder<>(mActivity, shortcutTypes, shortcutTypesL)
                            .setTitle(R.string.create_shortcut)
                            .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                                if (!isChecked) {
                                    return;
                                }
                                Drawable icon = Objects.requireNonNull(ContextCompat.getDrawable(mActivity, R.drawable.ic_launcher_foreground));
                                ProfileShortcutInfo shortcutInfo = new ProfileShortcutInfo(profile.profileId,
                                        profile.name, shortcutTypes[which], shortcutTypesL[which]);
                                shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(icon));
                                CreateShortcutDialogFragment dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                                dialog1.show(mActivity.getSupportFragmentManager(), CreateShortcutDialogFragment.TAG);
                                dialog.dismiss();
                            })
                            .show();
                } else return false;
                return true;
            });
            popupMenu.show();
        }

        @NonNull
        private CharSequence getProfileTypeLabel(int profileType) {
            if (profileType == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
                return mActivity.getString(R.string.filters);
            }
            return mActivity.getString(R.string.apps);
        }

        @NonNull
        private CharSequence getProfileStateLabel(@Nullable String profileState) {
            if (BaseProfile.STATE_OFF.equals(profileState)) {
                return mActivity.getString(R.string.off);
            }
            return mActivity.getString(R.string.on);
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint.isEmpty() ? null : constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.isEmpty()) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<BaseProfile> list = new ArrayList<>(mDefaultList.length);
                        for (BaseProfile item : mDefaultList) {
                            if (item.name.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list.toArray(new BaseProfile[0]);
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        int previousCount = mAdapterList != null ? mAdapterList.length : 0;
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            mAdapterList = (BaseProfile[]) filterResults.values;
                        }
                        AdapterUtils.notifyDataSetChanged(ProfilesAdapter.this, previousCount, mAdapterList.length);
                        mActivity.updateProfilesSummary(mAdapterList.length, mDefaultList.length);
                    }
                };
            return mFilter;
        }
    }
}
