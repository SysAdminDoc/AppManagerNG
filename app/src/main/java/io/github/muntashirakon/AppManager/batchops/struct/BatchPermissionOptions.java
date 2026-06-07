// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchPermissionOptions implements IBatchOpOptions {
    public static final String TAG = BatchPermissionOptions.class.getSimpleName();
    @NonNull
    private String[] mPermissions;

    public BatchPermissionOptions(@NonNull String[] permissions) {
        mPermissions = requireValidPermissions(permissions);
    }

    @NonNull
    public String[] getPermissions() {
        return mPermissions;
    }

    protected BatchPermissionOptions(@NonNull Parcel in) {
        mPermissions = requireValidPermissions(Objects.requireNonNull(in.createStringArray()));
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(mPermissions);
    }

    protected BatchPermissionOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mPermissions = deserializePermissions(jsonObject.getJSONArray("permissions"));
    }

    public static final JsonDeserializer.Creator<BatchPermissionOptions> DESERIALIZER
            = BatchPermissionOptions::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("permissions", JSONUtils.getJSONArray(mPermissions));
        return jsonObject;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BatchPermissionOptions> CREATOR = new Creator<BatchPermissionOptions>() {
        @Override
        @NonNull
        public BatchPermissionOptions createFromParcel(@NonNull Parcel in) {
            return new BatchPermissionOptions(in);
        }

        @Override
        @NonNull
        public BatchPermissionOptions[] newArray(int size) {
            return new BatchPermissionOptions[size];
        }
    };

    @NonNull
    private static String[] deserializePermissions(@NonNull JSONArray permissionsJson) throws JSONException {
        String[] permissions = new String[permissionsJson.length()];
        for (int i = 0; i < permissionsJson.length(); ++i) {
            Object value = permissionsJson.get(i);
            if (!(value instanceof String)) {
                throw new JSONException("Invalid permission name.");
            }
            permissions[i] = (String) value;
        }
        try {
            return requireValidPermissions(permissions);
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    @NonNull
    private static String[] requireValidPermissions(@NonNull String[] permissions) {
        if (permissions.length == 0) {
            throw new IllegalArgumentException("Permission options must include at least one permission.");
        }
        ArrayList<String> validPermissions = new ArrayList<>(permissions.length);
        for (String permission : permissions) {
            if (permission == null) {
                throw new IllegalArgumentException("Invalid permission name.");
            }
            String normalizedPermission = permission.trim();
            if (normalizedPermission.isEmpty()) {
                throw new IllegalArgumentException("Invalid permission name.");
            }
            validPermissions.add(normalizedPermission);
        }
        if (validPermissions.contains("*") && validPermissions.size() != 1) {
            throw new IllegalArgumentException("Permission wildcard cannot be mixed with explicit permissions.");
        }
        return validPermissions.toArray(new String[0]);
    }
}
