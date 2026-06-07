// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager.OpType;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.util.ParcelUtils;

public class BatchQueueItem implements Parcelable, IJsonSerializer {
    @NonNull
    public static BatchQueueItem getBatchOpQueue(@OpType int op,
                                                 @Nullable ArrayList<String> packages,
                                                 @Nullable ArrayList<Integer> users,
                                                 @Nullable IBatchOpOptions options) {
        return new BatchQueueItem(R.string.batch_ops, op, packages, users, options);
    }

    @NonNull
    public static BatchQueueItem getOneClickQueue(@OpType int op,
                                                  @Nullable ArrayList<String> packages,
                                                  @Nullable ArrayList<Integer> users,
                                                  @Nullable IBatchOpOptions args) {
        return new BatchQueueItem(R.string.one_click_ops, op, packages, users, args);
    }

    @StringRes
    private final int mTitleRes;
    @OpType
    private final int mOp;
    @NonNull
    private ArrayList<String> mPackages;
    @Nullable
    private ArrayList<Integer> mUsers;
    @Nullable
    private final IBatchOpOptions mOptions;

    private BatchQueueItem(@StringRes int titleRes,
                           @OpType int op,
                           @Nullable ArrayList<String> packages,
                           @Nullable ArrayList<Integer> users,
                           @Nullable IBatchOpOptions options) {
        mTitleRes = titleRes;
        mOp = requireValidQueueOp(op);
        mPackages = packages != null ? packages : new ArrayList<>(0);
        mUsers = users;
        mOptions = options;
    }

    @StringRes
    public int getTitleRes() {
        return mTitleRes;
    }

    @Nullable
    public String getTitle() {
        try {
            return ContextUtils.getContext().getString(mTitleRes);
        } catch (Resources.NotFoundException e) {
            // This resource may not always be found
            return null;
        }
    }

    public int getOp() {
        return mOp;
    }

    @NonNull
    public ArrayList<String> getPackages() {
        return mPackages;
    }

    public void setPackages(@NonNull ArrayList<String> packages) {
        mPackages = packages;
    }

    @NonNull
    public ArrayList<Integer> getUsers() {
        if (mUsers == null) {
            int size = mPackages.size();
            int userId = UserHandleHidden.myUserId();
            mUsers = new ArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                mUsers.add(userId);
            }
        } else {
            sanitizeTargets(mPackages, mUsers);
        }
        return mUsers;
    }

    public void setUsers(@Nullable ArrayList<Integer> users) {
        mUsers = users;
    }

    @NonNull
    public BatchQueueItem withTargets(@NonNull List<UserPackagePair> targets) {
        ArrayList<String> packages = new ArrayList<>(targets.size());
        ArrayList<Integer> users = new ArrayList<>(targets.size());
        for (UserPackagePair target : targets) {
            packages.add(target.getPackageName());
            users.add(target.getUserId());
        }
        return new BatchQueueItem(mTitleRes, mOp, packages, users, mOptions);
    }

    @Nullable
    public IBatchOpOptions getOptions() {
        return mOptions;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mTitleRes);
        dest.writeInt(mOp);
        dest.writeStringList(mPackages);
        ParcelUtils.writeArrayList(mUsers, dest);
        dest.writeParcelable(mOptions, flags);
    }

    protected BatchQueueItem(@NonNull JSONObject jsonObject) throws JSONException {
        mTitleRes = jsonObject.getInt("title_res");
        int op = jsonObject.getInt("op");
        if (!BatchOpsManager.isValidQueueOp(op)) {
            throw new JSONException("Invalid batch queue operation: " + op);
        }
        mOp = op;
        mPackages = new ArrayList<>();
        mUsers = new ArrayList<>();
        deserializeTargets(jsonObject.getJSONArray("packages"), jsonObject.getJSONArray("users"), mPackages, mUsers);
        JSONObject options = jsonObject.optJSONObject("options");
        mOptions = options != null ? IBatchOpOptions.DESERIALIZER.deserialize(options) : null;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title_res", mTitleRes);
        jsonObject.put("op", mOp);
        jsonObject.put("packages", JSONUtils.getJSONArray(mPackages));
        jsonObject.put("users", JSONUtils.getJSONArray(mUsers));
        jsonObject.put("options", mOptions != null ? mOptions.serializeToJson() : null);
        return jsonObject;
    }

    protected BatchQueueItem(@NonNull Parcel in) {
        mTitleRes = in.readInt();
        mOp = requireValidQueueOp(in.readInt());
        mPackages = Objects.requireNonNull(in.createStringArrayList());
        mUsers = ParcelUtils.readArrayList(in, Integer.class.getClassLoader());
        mOptions = ParcelCompat.readParcelable(in, IBatchOpOptions.class.getClassLoader(), IBatchOpOptions.class);
    }

    private static int requireValidQueueOp(int op) {
        if (!BatchOpsManager.isValidQueueOp(op)) {
            throw new IllegalArgumentException("Invalid batch queue operation: " + op);
        }
        return op;
    }

    private static void deserializeTargets(@NonNull JSONArray packagesJson,
                                           @NonNull JSONArray usersJson,
                                           @NonNull ArrayList<String> packages,
                                           @NonNull ArrayList<Integer> users) {
        int count = Math.min(packagesJson.length(), usersJson.length());
        for (int i = 0; i < count; ++i) {
            Object packageValue = packagesJson.opt(i);
            Object userValue = usersJson.opt(i);
            if (!(packageValue instanceof String)
                    || !(userValue instanceof Integer || userValue instanceof Long)) {
                continue;
            }
            String packageName = (String) packageValue;
            long userId = ((Number) userValue).longValue();
            if (userId > Integer.MAX_VALUE || !isValidTarget(packageName, userId)) {
                continue;
            }
            packages.add(packageName);
            users.add((int) userId);
        }
    }

    private static void sanitizeTargets(@NonNull ArrayList<String> packages,
                                        @Nullable ArrayList<Integer> users) {
        if (users == null) {
            return;
        }
        int count = Math.min(packages.size(), users.size());
        int writeIndex = 0;
        for (int i = 0; i < count; ++i) {
            String packageName = packages.get(i);
            Integer userId = users.get(i);
            if (packageName == null || userId == null || !isValidTarget(packageName, userId)) {
                continue;
            }
            packages.set(writeIndex, packageName);
            users.set(writeIndex, userId);
            ++writeIndex;
        }
        while (packages.size() > writeIndex) {
            packages.remove(packages.size() - 1);
        }
        while (users.size() > writeIndex) {
            users.remove(users.size() - 1);
        }
    }

    private static boolean isValidTarget(@NonNull String packageName, long userId) {
        return userId >= 0 && PackageUtils.validateName(packageName);
    }

    public static final JsonDeserializer.Creator<BatchQueueItem> DESERIALIZER = BatchQueueItem::new;

    public static final Creator<BatchQueueItem> CREATOR = new Creator<BatchQueueItem>() {
        @NonNull
        @Override
        public BatchQueueItem createFromParcel(@NonNull Parcel in) {
            return new BatchQueueItem(in);
        }

        @NonNull
        @Override
        public BatchQueueItem[] newArray(int size) {
            return new BatchQueueItem[size];
        }
    };
}
