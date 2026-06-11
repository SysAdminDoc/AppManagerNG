// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;

public class BatchSafetyOptions implements IBatchOpOptions {
    public static final String TAG = BatchSafetyOptions.class.getSimpleName();

    private final boolean mAllowCriticalPackages;

    public BatchSafetyOptions(boolean allowCriticalPackages) {
        mAllowCriticalPackages = allowCriticalPackages;
    }

    protected BatchSafetyOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mAllowCriticalPackages = jsonObject.optBoolean("allow_critical_packages", false);
    }

    public boolean isAllowCriticalPackages() {
        return mAllowCriticalPackages;
    }

    public static final JsonDeserializer.Creator<BatchSafetyOptions> DESERIALIZER
            = BatchSafetyOptions::new;

    protected BatchSafetyOptions(@NonNull Parcel in) {
        mAllowCriticalPackages = in.readByte() != 0;
    }

    public static final Creator<BatchSafetyOptions> CREATOR = new Creator<BatchSafetyOptions>() {
        @NonNull
        @Override
        public BatchSafetyOptions createFromParcel(@NonNull Parcel in) {
            return new BatchSafetyOptions(in);
        }

        @NonNull
        @Override
        public BatchSafetyOptions[] newArray(int size) {
            return new BatchSafetyOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByte((byte) (mAllowCriticalPackages ? 1 : 0));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return new JSONObject()
                .put("tag", TAG)
                .put("allow_critical_packages", mAllowCriticalPackages);
    }
}
