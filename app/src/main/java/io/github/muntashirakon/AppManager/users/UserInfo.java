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
    /** The name the system gave the user, {@code null} when it has none. Shown through {@link #getDisplayName}. */
    @Nullable
    public final String name;
    @NonNull
    public final ProfileVisibilityDiagnostics.ProfileFacts profileFacts;

    UserInfo(@NonNull android.content.pm.UserInfo userInfo) {
        userHandle = userInfo.getUserHandle();
        id = userInfo.id;
        name = userInfo.name;
        profileFacts = ProfileVisibilityDiagnostics.buildFacts(userInfo);
    }

    /** The user's name, or "This user" or "Other user" in the app's language when the system has none (#31). */
    @NonNull
    public String getDisplayName(@NonNull Context context) {
        if (name != null) {
            return name;
        }
        return context.getString(id == UserHandleHidden.myUserId() ? R.string.user_unnamed_this
                : R.string.user_unnamed_other);
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        String label = getDisplayName(context) + " (" + id + ")";
        CharSequence details = ProfileVisibilityDiagnostics.getUserProfileDetails(context, profileFacts);
        return details.length() == 0
                ? label
                : context.getString(R.string.user_profile_label_with_diagnostics, label, details);
    }
}
