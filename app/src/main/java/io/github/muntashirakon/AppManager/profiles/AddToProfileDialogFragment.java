// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.AppManager.logs.Log;

public class AddToProfileDialogFragment extends DialogFragment {
    public static final String TAG = AddToProfileDialogFragment.class.getSimpleName();

    private static final String ARG_PKGS = "pkgs";

    public static AddToProfileDialogFragment getInstance(@NonNull String[] packages) {
        AddToProfileDialogFragment fragment = new AddToProfileDialogFragment();
        Bundle args = new Bundle();
        args.putStringArray(ARG_PKGS, packages);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String[] packages = requireArguments().getStringArray(ARG_PKGS);
        AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(requireContext())
                .setTitle(R.string.add_to_profile)
                .setEndIconContentDescription(R.string.new_profile)
                .setEndIcon(R.drawable.ic_add, v -> new TextInputDialogBuilder(requireContext(), R.string.input_profile_name)
                        .setTitle(R.string.new_profile)
                        .setHelperText(R.string.input_profile_name_description)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                            if (!TextUtils.isEmpty(profName)) {
                                startActivity(AppsProfileActivity.getNewProfileIntent(requireContext(),
                                        profName.toString(), packages));
                                if (dialogRef.get() != null) {
                                    dialogRef.get().dismiss();
                                }
                            }
                        })
                        .show());
        // Profiles are read from disk, so the list fills in once they load
        SearchableMultiChoiceDialogBuilder<AppsProfile> builder = new SearchableMultiChoiceDialogBuilder<>(
                requireContext(), new ArrayList<AppsProfile>(), new ArrayList<CharSequence>());
        AlertDialog alertDialog = builder
                .setTitle(titleBuilder.build())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> ThreadUtils.postOnBackgroundThread(() -> {
                    boolean isSuccess = true;
                    for (AppsProfile profile : selectedItems) {
                        Path profilePath = ProfileManager.findProfilePathById(profile.profileId);
                        if (profilePath == null) {
                            isSuccess = false;
                            continue;
                        }
                        try (OutputStream os = profilePath.openOutputStream()) {
                            profile.appendPackages(packages);
                            profile.write(os);
                        } catch (Exception e) {
                            isSuccess = false;
                            Log.w(TAG, e);
                        }
                    }
                    if (isSuccess) {
                        ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.done));
                    } else {
                        ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
                    }
                }))
                .create();
        dialogRef.set(alertDialog);
        ThreadUtils.postOnBackgroundThread(() -> {
            List<AppsProfile> profiles = ExUtils.requireNonNullElse(() ->
                    ProfileManager.getProfiles(AppsProfile.PROFILE_TYPE_APPS), Collections.emptyList());
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) {
                    return;
                }
                builder.setItems(profiles, getProfileNames(requireContext(), profiles));
            });
        });
        return alertDialog;
    }

    @NonNull
    private static List<CharSequence> getProfileNames(@NonNull Context context, @NonNull List<AppsProfile> profiles) {
        List<CharSequence> profileNames = new ArrayList<>(profiles.size());
        for (AppsProfile profile : profiles) {
            profileNames.add(new SpannableStringBuilder(profile.name).append("\n")
                    .append(getSecondaryText(context, getSmallerText(profile.toLocalizedString(context)))));
        }
        return profileNames;
    }
}
