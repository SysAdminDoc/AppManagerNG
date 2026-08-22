// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public abstract class BaseAccessibilityService extends AccessibilityService {
    private static final long ACTION_DELAY_MILLIS = 500;

    private Context mContext;
    @Nullable
    private AccessibilityActionSequencer mActionSequencer;

    @Override
    public void onCreate() {
        super.onCreate();
        Handler handler = new Handler(Looper.getMainLooper());
        mActionSequencer = AccessibilityActionSequencer.create(handler::post);
    }

    @Override
    public void onDestroy() {
        if (mActionSequencer != null) {
            mActionSequencer.close();
            mActionSequencer = null;
        }
        super.onDestroy();
    }

    public void init(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Check if the accessibility service is enabled
     */
    public static boolean isAccessibilityEnabled(@NonNull Context context) {
        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (!TextUtils.isEmpty(enabledServices)) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabledServices);
            while (splitter.hasNext()) {
                ComponentName componentName = ComponentName.unflattenFromString(splitter.next());
                if (componentName != null && componentName.getPackageName().equals(context.getPackageName())) {
                    return true;
                }
            }
        }
        AccessibilityManager accessibilityManager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> accessibilityServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : accessibilityServices) {
            ComponentName componentName = ComponentName.unflattenFromString(info.getId());
            if (componentName != null && componentName.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    public void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * Simulate click
     *
     * @param nodeInfo nodeInfo
     */
    public void performViewClick(@Nullable AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        while (nodeInfo != null) {
            if (nodeInfo.isClickable() && nodeInfo.isEnabled()
                    && nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return;
            }
            nodeInfo = nodeInfo.getParent();
        }
    }

    /**
     * Simulate back/return operation
     */
    public void performBackClick() {
        performGlobalActions(GLOBAL_ACTION_BACK, 1);
    }

    protected void performBackClicks(int count) {
        performGlobalActions(GLOBAL_ACTION_BACK, count);
    }

    /**
     * Simulate scroll down
     */
    public void performScrollBackward() {
        performGlobalActions(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, 1);
    }

    /**
     * Simulate scroll up
     */
    public void performScrollForward() {
        performGlobalActions(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, 1);
    }

    /**
     * Find a view by its text
     *
     * @param text text
     * @return View
     */
    public AccessibilityNodeInfo findViewByText(String text) {
        return findViewByText(text, false);
    }

    /**
     * Find a view by its text
     *
     * @param text      text
     * @param clickable Whether the view can be clicked
     * @return View
     */
    @Nullable
    public AccessibilityNodeInfo findViewByText(String text, boolean clickable) {
        AccessibilityNodeInfo accessibilityNodeInfo = getRootInActiveWindow();
        if (accessibilityNodeInfo == null) {
            return null;
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByText(text);
        if (nodeInfoList != null) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                if (nodeInfo.isClickable() == clickable) {
                    return nodeInfo;
                }
                nodeInfo.recycle();
            }
        }
        return null;
    }

    /**
     * Find a view by its ID
     *
     * @param id ID in resource ID format i.e. package:id/id_name
     * @return View
     */
    @Nullable
    public AccessibilityNodeInfo findViewById(String id) {
        AccessibilityNodeInfo accessibilityNodeInfo = getRootInActiveWindow();
        if (accessibilityNodeInfo == null) {
            return null;
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByViewId(id);
        if (nodeInfoList != null) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                return nodeInfo;
            }
        }
        return null;
    }

    public void clickTextViewByText(String text) {
        AccessibilityNodeInfo accessibilityNodeInfo = getRootInActiveWindow();
        if (accessibilityNodeInfo == null) {
            return;
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByText(text);
        if (nodeInfoList != null && !nodeInfoList.isEmpty()) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                performViewClick(nodeInfo);
                nodeInfo.recycle();
                break;
            }
        }
    }

    public void clickTextViewByID(String id) {
        AccessibilityNodeInfo accessibilityNodeInfo = getRootInActiveWindow();
        if (accessibilityNodeInfo == null) {
            return;
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByViewId(id);
        if (nodeInfoList != null) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                performViewClick(nodeInfo);
                nodeInfo.recycle();
                break;
            }
        }
    }

    /**
     * Set input text
     *
     * @param nodeInfo nodeInfo
     * @param text     text
     */
    public void inputText(AccessibilityNodeInfo nodeInfo, String text) {
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    @Nullable
    protected static AccessibilityNodeInfo findViewByText(@Nullable AccessibilityNodeInfo accessibilityNodeInfo,
                                                          @NonNull String text, boolean clickable) {
        if (accessibilityNodeInfo == null) return null;
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByText(text);
        if (nodeInfoList != null) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                if (nodeInfo.isClickable() == clickable) {
                    return nodeInfo;
                }
                nodeInfo.recycle();
            }
        }
        return null;
    }

    @Nullable
    protected static AccessibilityNodeInfo findViewByTextRecursive(@Nullable AccessibilityNodeInfo accessibilityNodeInfo,
                                                                   @NonNull String text) {
        if (accessibilityNodeInfo == null) return null;
        CharSequence nodeText = accessibilityNodeInfo.getText();
        CharSequence nodeDescription = accessibilityNodeInfo.getContentDescription();
        if ((nodeText != null && text.contentEquals(nodeText))
                || (nodeDescription != null && text.contentEquals(nodeDescription))) {
            return AccessibilityNodeInfo.obtain(accessibilityNodeInfo);
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByText(text);
        if (nodeInfoList != null) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                return nodeInfo;
            }
        }
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); ++i) {
            AccessibilityNodeInfo nodeInfo = findViewByTextRecursive(accessibilityNodeInfo.getChild(i), text);
            if (nodeInfo != null) {
                return nodeInfo;
            }
        }
        return null;
    }

    @Nullable
    protected static AccessibilityNodeInfo findViewByViewIdRecursive(@Nullable AccessibilityNodeInfo accessibilityNodeInfo,
                                                                     @NonNull String viewId) {
        if (accessibilityNodeInfo == null) return null;
        if (viewId.equals(accessibilityNodeInfo.getViewIdResourceName())) {
            return AccessibilityNodeInfo.obtain(accessibilityNodeInfo);
        }
        List<AccessibilityNodeInfo> nodeInfoList = accessibilityNodeInfo.findAccessibilityNodeInfosByViewId(viewId);
        if (nodeInfoList != null) {
            for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
                return nodeInfo;
            }
        }
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); ++i) {
            AccessibilityNodeInfo nodeInfo = findViewByViewIdRecursive(accessibilityNodeInfo.getChild(i), viewId);
            if (nodeInfo != null) {
                return nodeInfo;
            }
        }
        return null;
    }

    @Nullable
    protected static AccessibilityNodeInfo findViewByClassName(@Nullable AccessibilityNodeInfo nodeInfo,
                                                               @NonNull CharSequence className) {
        if (nodeInfo == null) return null;
        for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
            AccessibilityNodeInfo child = nodeInfo.getChild(i);
            if (className.equals(child.getClassName())) {
                return child;
            }
            child.recycle();
        }
        return null;
    }

    protected final void runAfterAccessibilityDelay(long delayMillis, @NonNull Runnable action) {
        if (mActionSequencer != null) {
            mActionSequencer.runAfter(delayMillis, action);
        }
    }

    protected final void runAccessibilityActionWhenReady(@NonNull AccessibilityActionSequencer.ReadyAction action,
                                                         int maxChecks, long retryDelayMillis,
                                                         @NonNull Runnable onTimeout) {
        if (mActionSequencer != null) {
            mActionSequencer.runWhenReady(action, maxChecks, retryDelayMillis, onTimeout);
        }
    }

    protected final void cancelPendingAccessibilityActions() {
        if (mActionSequencer != null) {
            mActionSequencer.cancelPending();
        }
    }

    private void performGlobalActions(int action, int count) {
        if (count <= 0) {
            return;
        }
        runAfterAccessibilityDelay(ACTION_DELAY_MILLIS, () -> {
            performGlobalAction(action);
            performGlobalActions(action, count - 1);
        });
    }
}
