// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ProfileApplierInfo;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ProfileQueueItem implements Parcelable, IJsonSerializer {
    @NonNull
    public static ProfileQueueItem fromProfiledApplierInfo(@NonNull ProfileApplierInfo info) {
        return new ProfileQueueItem(info.profile, info.state);
    }

    @NonNull
    public static ProfileQueueItem fromProfile(@NonNull BaseProfile profile, @Nullable String state) {
        return new ProfileQueueItem(profile, state);
    }

    @NonNull
    public static ProfileQueueItem fromProfile(@NonNull BaseProfile profile, @Nullable String state,
                                               @Nullable JSONObject profileOverrides)
            throws JSONException, IOException {
        if (profileOverrides == null || profileOverrides.length() == 0) {
            return fromProfile(profile, state);
        }
        JSONObject profileObj = profile.serializeToJson();
        mergeProfileOverrides(profileObj, profileOverrides);
        profileObj.put("id", profile.profileId);
        profileObj.put("name", profile.name);
        profileObj.put("type", profile.type);
        BaseProfile overriddenProfile = BaseProfile.DESERIALIZER.deserialize(profileObj);
        File tempProfileFile = FileCache.getGlobalFileCache().getCachedFile(
                profileObj.toString().getBytes(StandardCharsets.UTF_8), ProfileManager.PROFILE_EXT);
        return new ProfileQueueItem(overriddenProfile, state, Paths.get(tempProfileFile));
    }

    @NonNull
    private final String mProfileId;
    @BaseProfile.ProfileType
    private final int mProfileType;
    @NonNull
    private final String mProfileName;
    @Nullable
    private final String mState;
    @Nullable
    private final Path mTempProfilePath;

    private ProfileQueueItem(@NonNull BaseProfile profile, @Nullable String state) {
        this(profile, state, null);
    }

    private ProfileQueueItem(@NonNull BaseProfile profile, @Nullable String state,
                             @Nullable Path tempProfilePath) {
        mProfileId = profile.profileId;
        mProfileType = profile.type;
        mProfileName = profile.name;
        mState = state;
        mTempProfilePath = tempProfilePath;
    }

    protected ProfileQueueItem(@NonNull Parcel in) {
        mProfileId = Objects.requireNonNull(in.readString());
        mProfileType = in.readInt();
        mProfileName = Objects.requireNonNull(in.readString());
        mState = in.readString();
        Uri uri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        mTempProfilePath = uri != null ? Paths.get(uri) : null;
    }

    @NonNull
    public String getProfileId() {
        return mProfileId;
    }

    @BaseProfile.ProfileType
    public int getProfileType() {
        return mProfileType;
    }

    @NonNull
    public String getProfileName() {
        return mProfileName;
    }

    @Nullable
    public String getState() {
        return mState;
    }

    @Nullable
    public Path getTempProfilePath() {
        return mTempProfilePath;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mProfileId);
        dest.writeInt(mProfileType);
        dest.writeString(mProfileName);
        dest.writeString(mState);
        dest.writeParcelable(mTempProfilePath != null ? mTempProfilePath.getUri() : null, flags);
    }

    protected ProfileQueueItem(@NonNull JSONObject jsonObject) throws JSONException {
        String profileId = JSONUtils.getString(jsonObject, "profile_id");
        String profileName = JSONUtils.getString(jsonObject, "profile_name");
        if (profileId == null || profileName == null) {
            throw new JSONException("Missing profile identity.");
        }
        profileId = profileId.trim();
        profileName = profileName.trim();
        if (profileId.isEmpty() || profileName.isEmpty()) {
            throw new JSONException("Missing profile identity.");
        }
        mProfileId = profileId;
        mProfileType = getProfileType(jsonObject);
        mProfileName = profileName;
        mState = JSONUtils.getString(jsonObject, "state");
        JSONObject profile = jsonObject.optJSONObject("profile");
        File profilePath = null;
        if (profile != null) {
            try (InputStream is = new ByteArrayInputStream(profile.toString().getBytes(StandardCharsets.UTF_8))) {
                profilePath = FileCache.getGlobalFileCache().getCachedFile(is, ProfileManager.PROFILE_EXT);
            } catch (IOException e) {
                //noinspection UnnecessaryInitCause
                throw (JSONException) new JSONException(e.getMessage()).initCause(e);
            }
        }
        mTempProfilePath = profilePath != null ? Paths.get(profilePath) : null;
    }

    @BaseProfile.ProfileType
    private static int getProfileType(@NonNull JSONObject jsonObject) throws JSONException {
        Object value = jsonObject.opt("profile_type");
        if (!(value instanceof Integer || value instanceof Long)) {
            throw new JSONException("Invalid profile type.");
        }
        long profileType = ((Number) value).longValue();
        if (profileType == BaseProfile.PROFILE_TYPE_APPS
                || profileType == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
            return (int) profileType;
        }
        throw new JSONException("Invalid profile type.");
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("profile_id", mProfileId);
        jsonObject.put("profile_type", mProfileType);
        jsonObject.put("profile_name", mProfileName);
        jsonObject.put("state", mState);
        // A profile can be altered any time. So, we need to store a snapshot of the profile
        try {
            BaseProfile profile = BaseProfile.fromPath(mTempProfilePath != null
                    ? mTempProfilePath : ProfileManager.findProfilePathById(mProfileId));
            jsonObject.put("profile", profile.serializeToJson());
        } catch (IOException e) {
            //noinspection UnnecessaryInitCause
            throw (JSONException) new JSONException(e.getMessage()).initCause(e);
        }
        return jsonObject;
    }

    private static void mergeProfileOverrides(@NonNull JSONObject base, @NonNull JSONObject overrides)
            throws JSONException {
        JSONArray keys = overrides.names();
        if (keys == null) {
            return;
        }
        for (int i = 0; i < keys.length(); ++i) {
            String key = keys.getString(i);
            Object overrideValue = overrides.get(key);
            if (overrideValue instanceof JSONObject) {
                JSONObject baseValue = base.optJSONObject(key);
                if (baseValue != null) {
                    mergeProfileOverrides(baseValue, (JSONObject) overrideValue);
                    continue;
                }
            }
            base.put(key, overrideValue);
        }
    }

    public static final JsonDeserializer.Creator<ProfileQueueItem> DESERIALIZER = ProfileQueueItem::new;

    public static final Creator<ProfileQueueItem> CREATOR = new Creator<ProfileQueueItem>() {
        @NonNull
        @Override
        public ProfileQueueItem createFromParcel(@NonNull Parcel in) {
            return new ProfileQueueItem(in);
        }

        @NonNull
        @Override
        public ProfileQueueItem[] newArray(int size) {
            return new ProfileQueueItem[size];
        }
    };
}
