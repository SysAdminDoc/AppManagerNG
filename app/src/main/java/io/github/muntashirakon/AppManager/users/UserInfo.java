// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.LocalizedString;

public class UserInfo implements LocalizedString {
    @NonNull
    public final UserHandle userHandle;
    public final int id;
    @Nullable
    public final String name;
    @NonNull
    public final ProfileVisibilityDiagnostics.ProfileFacts profileFacts;

    UserInfo(@NonNull android.content.pm.UserInfo userInfo) {
        userHandle = userInfo.getUserHandle();
        id = userInfo.id;
        String username = userInfo.name;
        if (username == null) {
            this.name = id == UserHandleHidden.myUserId() ? "This" : "Other";
        } else this.name = username;
        profileFacts = ProfileVisibilityDiagnostics.buildFacts(userInfo);
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        String label = name == null ? String.valueOf(id) : (name + " (" + id + ")");
        CharSequence details = ProfileVisibilityDiagnostics.getUserProfileDetails(context, profileFacts);
        return details.length() == 0
                ? label
                : context.getString(R.string.user_profile_label_with_diagnostics, label, details);
    }
}
