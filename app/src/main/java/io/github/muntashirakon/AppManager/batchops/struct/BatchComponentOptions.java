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

public class BatchComponentOptions implements IBatchOpOptions {
    public static final String TAG = BatchComponentOptions.class.getSimpleName();

    @NonNull
    private String[] mSignatures;

    public BatchComponentOptions(@NonNull String[] signatures) {
        mSignatures = requireValidSignatures(signatures);
    }

    @NonNull
    public String[] getSignatures() {
        return mSignatures;
    }

    protected BatchComponentOptions(@NonNull Parcel in) {
        mSignatures = requireValidSignatures(Objects.requireNonNull(in.createStringArray()));
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(mSignatures);
    }

    protected BatchComponentOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mSignatures = deserializeSignatures(jsonObject.getJSONArray("signatures"));
    }

    public static final JsonDeserializer.Creator<BatchComponentOptions> DESERIALIZER
            = BatchComponentOptions::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("signatures", JSONUtils.getJSONArray(mSignatures));
        return jsonObject;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BatchComponentOptions> CREATOR = new Creator<BatchComponentOptions>() {
        @Override
        @NonNull
        public BatchComponentOptions createFromParcel(@NonNull Parcel in) {
            return new BatchComponentOptions(in);
        }

        @Override
        @NonNull
        public BatchComponentOptions[] newArray(int size) {
            return new BatchComponentOptions[size];
        }
    };

    @NonNull
    private static String[] deserializeSignatures(@NonNull JSONArray signaturesJson) throws JSONException {
        String[] signatures = new String[signaturesJson.length()];
        for (int i = 0; i < signaturesJson.length(); ++i) {
            Object value = signaturesJson.get(i);
            if (!(value instanceof String)) {
                throw new JSONException("Invalid component signature.");
            }
            signatures[i] = (String) value;
        }
        try {
            return requireValidSignatures(signatures);
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    @NonNull
    private static String[] requireValidSignatures(@NonNull String[] signatures) {
        if (signatures.length == 0) {
            throw new IllegalArgumentException("Component options must include at least one signature.");
        }
        ArrayList<String> validSignatures = new ArrayList<>(signatures.length);
        for (String signature : signatures) {
            if (signature == null) {
                throw new IllegalArgumentException("Invalid component signature.");
            }
            String normalizedSignature = signature.trim();
            if (normalizedSignature.isEmpty()) {
                throw new IllegalArgumentException("Invalid component signature.");
            }
            validSignatures.add(normalizedSignature);
        }
        return validSignatures.toArray(new String[0]);
    }
}
