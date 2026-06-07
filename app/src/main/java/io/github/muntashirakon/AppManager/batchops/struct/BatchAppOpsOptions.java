// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchAppOpsOptions implements IBatchOpOptions {
    public static final String TAG = BatchAppOpsOptions.class.getSimpleName();

    @NonNull
    private int[] mAppOps;
    private int mMode;

    public BatchAppOpsOptions(@NonNull int[] appOps, int mode) {
        mAppOps = requireValidAppOps(appOps);
        mMode = requireValidMode(mode);
    }

    @NonNull
    public int[] getAppOps() {
        return mAppOps;
    }

    public int getMode() {
        return mMode;
    }

    protected BatchAppOpsOptions(@NonNull Parcel in) {
        mAppOps = requireValidAppOps(Objects.requireNonNull(in.createIntArray()));
        mMode = requireValidMode(in.readInt());
    }

    public static final Creator<BatchAppOpsOptions> CREATOR = new Creator<BatchAppOpsOptions>() {
        @Override
        @NonNull
        public BatchAppOpsOptions createFromParcel(@NonNull Parcel in) {
            return new BatchAppOpsOptions(in);
        }

        @Override
        @NonNull
        public BatchAppOpsOptions[] newArray(int size) {
            return new BatchAppOpsOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(mAppOps);
        dest.writeInt(mMode);
    }

    protected BatchAppOpsOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        try {
            int[] appOps = Objects.requireNonNull(JSONUtils.getIntArray(jsonObject.getJSONArray("app_ops")));
            mAppOps = requireValidAppOps(appOps);
            mMode = requireValidMode(jsonObject.getInt("mode"));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    public static final JsonDeserializer.Creator<BatchAppOpsOptions> DESERIALIZER
            = BatchAppOpsOptions::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("app_ops", JSONUtils.getJSONArray(mAppOps));
        jsonObject.put("mode", mMode);
        return jsonObject;
    }

    @NonNull
    private static int[] requireValidAppOps(@NonNull int[] appOps) {
        if (appOps.length == 0) {
            throw new IllegalArgumentException("AppOps options must include at least one operation.");
        }
        if (appOps.length == 1 && appOps[0] == AppOpsManagerCompat.OP_NONE) {
            return appOps;
        }
        for (int appOp : appOps) {
            if (!AppOpsManagerCompat.isValidOp(appOp)) {
                throw new IllegalArgumentException("Invalid app op: " + appOp);
            }
        }
        return appOps;
    }

    private static int requireValidMode(int mode) {
        if (!AppOpsManagerCompat.isValidMode(mode)) {
            throw new IllegalArgumentException("Invalid app op mode: " + mode);
        }
        return mode;
    }
}
