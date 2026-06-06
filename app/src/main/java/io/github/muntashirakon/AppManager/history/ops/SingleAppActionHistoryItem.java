// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;

public final class SingleAppActionHistoryItem implements IJsonSerializer {
    private static final String KEY_ACTION = "action";
    private static final String KEY_OPERATION_LABEL = "operation_label";
    private static final String KEY_PACKAGE_NAME = "package_name";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_TARGET_LABEL = "target_label";
    private static final String KEY_DETAIL = "detail";

    public static final String ACTION_FREEZE = "freeze";
    public static final String ACTION_UNFREEZE = "unfreeze";
    public static final String ACTION_PERMISSION_GRANT = "permission_grant";
    public static final String ACTION_PERMISSION_REVOKE = "permission_revoke";
    public static final String ACTION_APP_OP_SET = "app_op_set";
    public static final String ACTION_COMPONENT_RULE = "component_rule";
    public static final String ACTION_COMPONENT_ACTION = "component_action";

    @NonNull
    private final String mAction;
    @NonNull
    private final String mOperationLabel;
    @NonNull
    private final String mPackageName;
    private final int mUserId;
    @NonNull
    private final String mTargetLabel;
    @Nullable
    private final String mDetail;

    public SingleAppActionHistoryItem(@NonNull String action,
                                      @NonNull String operationLabel,
                                      @NonNull String packageName,
                                      int userId,
                                      @NonNull String targetLabel,
                                      @Nullable String detail) {
        mAction = action;
        mOperationLabel = operationLabel;
        mPackageName = packageName;
        mUserId = userId;
        mTargetLabel = targetLabel;
        mDetail = detail;
    }

    @NonNull
    public String getAction() {
        return mAction;
    }

    @NonNull
    public String getOperationLabel() {
        return mOperationLabel;
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    public int getUserId() {
        return mUserId;
    }

    @NonNull
    public String getTargetLabel() {
        return mTargetLabel;
    }

    @NonNull
    public String getTargetPreviewLabel() {
        return mTargetLabel.isEmpty() ? mPackageName : mPackageName + " - " + mTargetLabel;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject()
                .put(KEY_ACTION, mAction)
                .put(KEY_OPERATION_LABEL, mOperationLabel)
                .put(KEY_PACKAGE_NAME, mPackageName)
                .put(KEY_USER_ID, mUserId)
                .put(KEY_TARGET_LABEL, mTargetLabel);
        if (mDetail != null && !mDetail.isEmpty()) {
            jsonObject.put(KEY_DETAIL, mDetail);
        }
        return jsonObject;
    }
}
