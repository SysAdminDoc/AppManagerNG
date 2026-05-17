// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.profiles.struct.AppsBaseProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.Path;

public final class QuickFreezeTileController {
    private QuickFreezeTileController() {
    }

    public static boolean isSelectedProfile(@NonNull String profileId) {
        return Objects.equals(profileId, Prefs.Profiles.getQuickFreezeProfileId());
    }

    public static void setSelectedProfile(@NonNull String profileId) {
        Prefs.Profiles.setQuickFreezeProfileId(profileId);
    }

    public static void clearSelectedProfile() {
        Prefs.Profiles.setQuickFreezeProfileId(null);
    }

    @Nullable
    public static String getSelectedProfileId() {
        return Prefs.Profiles.getQuickFreezeProfileId();
    }

    public static boolean isProfileEligible(@Nullable BaseProfile profile) {
        return profile instanceof AppsBaseProfile && ((AppsBaseProfile) profile).freeze;
    }

    @Nullable
    public static BaseProfile loadSelectedProfile() throws IOException, JSONException {
        String profileId = getSelectedProfileId();
        if (profileId == null) {
            return null;
        }
        Path profilePath = ProfileManager.findProfilePathById(profileId);
        return BaseProfile.fromPath(profilePath);
    }
}
