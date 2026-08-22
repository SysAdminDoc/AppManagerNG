// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.accessibility.activity.TrackerWindow;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

public class NoRootAccessibilityService extends BaseAccessibilityService {
    private static final CharSequence SETTING_PACKAGE = "com.android.settings";
    private static final CharSequence INSTALLER_PACKAGE = "com.android.packageinstaller";
    private static final int MAX_SETTINGS_CHECKS = 18;
    private static final long SETTINGS_CHECK_DELAY_MILLIS = 500;

    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private PackageManager mPm;
    private int mTries = 1;
    private boolean mSettingsNavigationPending;
    private boolean mSettingsButtonPending;
    private boolean mForceStopBackPending;
    @Nullable
    private TrackerWindow mTrackerWindow;

    @Override
    public void onCreate() {
        super.onCreate();
        mPm = AppearanceUtils.getSystemContext(this).getPackageManager();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mMultiplexer.isLeadingActivityTracker()) {
            if (mTrackerWindow == null) {
                mTrackerWindow = new TrackerWindow(this);
            }
            mTrackerWindow.showOrUpdate(AccessibilityEvent.obtain(event));
        } else if (mTrackerWindow != null) {
            mTrackerWindow.dismiss();
            mTrackerWindow = null;
        }
        CharSequence packageName = event.getPackageName();
        boolean isWindowStateChanged = event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        boolean isSettingsContentChanged = SETTING_PACKAGE.equals(packageName)
                && mMultiplexer.hasPendingSettingsOperation()
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        if (!isWindowStateChanged && !isSettingsContentChanged) {
            return;
        }
        if (INSTALLER_PACKAGE.equals(packageName)) {
            automateInstallationUninstallation(event);
            return;
        }
        if (SETTING_PACKAGE.equals(packageName)) {
            automateSettings(event);
        }
    }

    @Override
    public void onInterrupt() {
        cancelPendingAccessibilityActions();
        mSettingsNavigationPending = false;
        mSettingsButtonPending = false;
        mForceStopBackPending = false;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cancelPendingAccessibilityActions();
        mSettingsNavigationPending = false;
        mSettingsButtonPending = false;
        mForceStopBackPending = false;
        if (mTrackerWindow != null) {
            mTrackerWindow.dismiss();
            mTrackerWindow = null;
        }
        return super.onUnbind(intent);
    }

    private void automateInstallationUninstallation(@NonNull AccessibilityEvent event) {
        if (event.getClassName().equals("android.app.Dialog")) {
            if (mMultiplexer.isInstallEnabled()) {
                // Install
                performViewClick(findViewByText(getString(event, "install"), true)); // install_text
            }
        } else if (event.getClassName().equals("com.android.packageinstaller.UninstallerActivity")) {
            if (mMultiplexer.isUninstallEnabled()) {
                // uninstall
                performViewClick(findViewByText(getString(event, "ok"), true)); // dlg_ok
            }
        }
    }

    private void automateSettings(@NonNull AccessibilityEvent event) {
        if (mMultiplexer.isForceStopEnabled()
                && clickConfirmationIfPresent(event, "force_stop_dlg_title", () -> {
            mForceStopBackPending = false;
            mMultiplexer.enableForceStop(false);
            mTries = 1;
            performBackClick();
        })) {
            return;
        }
        if (mMultiplexer.isClearDataEnabled()
                && clickConfirmationIfPresent(event, "clear_data_dlg_title", () -> {
            mMultiplexer.enableClearData(false);
            performBackClicks(2);
        })) {
            return;
        }
        if (mMultiplexer.isNavigateToStorageAndCache()) {
            navigateToStorageAndCache(event, null);
            return;
        }
        if (mMultiplexer.isForceStopEnabled()) {
            clickForceStopWhenReady(event);
            return;
        }
        if (mMultiplexer.isClearDataEnabled()) {
            clickSettingsButtonWhenReady(getString(event, "clear_user_data_text"),
                    () -> mSettingsButtonPending = true,
                    () -> {
                        mMultiplexer.enableClearData(false);
                        performBackClick();
                    });
            return;
        }
        if (mMultiplexer.isClearCacheEnabled()) {
            mMultiplexer.enableClearCache(false);
            clickSettingsButtonWhenReady(getString(event, "clear_cache_btn_text"),
                    () -> performBackClicks(2), this::performBackClick);
        }
    }

    private boolean clickConfirmationIfPresent(@NonNull AccessibilityEvent event,
                                               @NonNull String titleResource,
                                               @NonNull Runnable onConfirmed) {
        String titleText = getString(event, titleResource);
        String confirmText = getString(event, "dlg_ok");
        AccessibilityNodeInfo titleNode = findViewByTextRecursive(getRootInActiveWindow(), titleText);
        if (titleNode == null) {
            return false;
        }
        titleNode.recycle();
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        AccessibilityNodeInfo confirmNode = findViewByTextRecursive(rootNode, confirmText);
        if (confirmNode == null) {
            confirmNode = findViewByViewIdRecursive(rootNode, "android:id/button1");
        }
        if (confirmNode == null) {
            return false;
        }
        mSettingsButtonPending = false;
        performViewClick(confirmNode);
        confirmNode.recycle();
        onConfirmed.run();
        return true;
    }

    private void clickForceStopWhenReady(@NonNull AccessibilityEvent event) {
        if (mSettingsButtonPending) {
            return;
        }
        String forceStopText = getString(event, "force_stop");
        String clearDataText = getString(event, "clear_user_data_text");
        String storageSettings;
        try {
            storageSettings = getStorageSettingsText(event);
        } catch (Resources.NotFoundException e) {
            storageSettings = null;
        }
        String finalStorageSettings = storageSettings;
        String forceStopDialogTitle = getString(event, "force_stop_dlg_title");
        mSettingsButtonPending = true;
        runAccessibilityActionWhenReady(() -> {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            AccessibilityNodeInfo forceStopNode = findViewByTextRecursive(rootNode, forceStopText);
            if (forceStopNode == null) {
                AccessibilityNodeInfo clearDataNode = findViewByTextRecursive(getRootInActiveWindow(), clearDataText);
                if (clearDataNode != null) {
                    clearDataNode.recycle();
                    mSettingsButtonPending = false;
                    mMultiplexer.enableForceStop(false);
                    performBackClick();
                    return true;
                }
                return false;
            }
            boolean isEnabled = forceStopNode.isEnabled();
            if (isEnabled) {
                mTries = 0;
                performViewClick(forceStopNode);
                mForceStopBackPending = true;
                runAfterAccessibilityDelay(SETTINGS_CHECK_DELAY_MILLIS,
                        () -> finishForceStopIfNoConfirmation(forceStopDialogTitle));
            }
            forceStopNode.recycle();
            mSettingsButtonPending = false;
            if (!isEnabled) {
                if (mTries > 0) {
                    // Work around Android occasionally disabling force-stop until storage is opened.
                    if (finalStorageSettings != null) {
                        navigateToStorageAndCache(finalStorageSettings, () -> {
                            --mTries;
                            performBackClick();
                        });
                    } else {
                        mMultiplexer.enableForceStop(false);
                        performBackClick();
                    }
                } else {
                    performBackClick();
                }
            }
            return true;
        }, MAX_SETTINGS_CHECKS, SETTINGS_CHECK_DELAY_MILLIS, () -> {
            mSettingsButtonPending = false;
            mForceStopBackPending = false;
            mMultiplexer.enableForceStop(false);
            performBackClick();
        });
    }

    private void finishForceStopIfNoConfirmation(@NonNull String dialogTitle) {
        if (!mForceStopBackPending) {
            return;
        }
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null || !SETTING_PACKAGE.equals(rootNode.getPackageName())) {
            return;
        }
        AccessibilityNodeInfo titleNode = findViewByTextRecursive(rootNode, dialogTitle);
        if (titleNode != null) {
            titleNode.recycle();
            return;
        }
        mForceStopBackPending = false;
        mMultiplexer.enableForceStop(false);
        performBackClick();
    }

    private void navigateToStorageAndCache(@NonNull AccessibilityEvent event, @Nullable Runnable onOpened) {
        if (mSettingsNavigationPending) {
            return;
        }
        String storageSettings;
        try {
            storageSettings = getStorageSettingsText(event);
        } catch (Resources.NotFoundException e) {
            // Failed: non-AOSP device
            mMultiplexer.enableNavigateToStorageAndCache(false);
            performBackClick();
            return;
        }
        navigateToStorageAndCache(storageSettings, onOpened);
    }

    private void navigateToStorageAndCache(@NonNull String storageSettings, @Nullable Runnable onOpened) {
        if (mSettingsNavigationPending) {
            return;
        }
        mSettingsNavigationPending = true;
        runAfterAccessibilityDelay(SETTINGS_CHECK_DELAY_MILLIS, () -> runAccessibilityActionWhenReady(() -> {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            AccessibilityNodeInfo storageNode = findViewByTextRecursive(rootNode, storageSettings);
            if (storageNode == null) {
                return false;
            }
            mSettingsNavigationPending = false;
            mMultiplexer.enableNavigateToStorageAndCache(false);  // prevent infinite loop
            performViewClick(storageNode);
            storageNode.recycle();
            if (onOpened != null) {
                onOpened.run();
            }
            return true;
        }, MAX_SETTINGS_CHECKS, SETTINGS_CHECK_DELAY_MILLIS, () -> {
            mSettingsNavigationPending = false;
            mMultiplexer.enableNavigateToStorageAndCache(false);
            performBackClick();
        }));
    }

    @NonNull
    private String getStorageSettingsText(@NonNull AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return getString(event, "storage_settings_for_app");
        }
        return getString(event, "storage_label");
    }

    private void clickSettingsButtonWhenReady(@NonNull String buttonText, @Nullable Runnable onClicked,
                                              @NonNull Runnable onTimeout) {
        if (mSettingsButtonPending) {
            return;
        }
        mSettingsButtonPending = true;
        runAccessibilityActionWhenReady(() -> {
            AccessibilityNodeInfo node = findViewByTextRecursive(getRootInActiveWindow(), buttonText);
            if (node == null) {
                return false;
            }
            boolean isEnabled = node.isEnabled();
            if (isEnabled) {
                performViewClick(node);
            }
            node.recycle();
            if (!isEnabled) {
                return false;
            }
            mSettingsButtonPending = false;
            if (onClicked != null) {
                onClicked.run();
            }
            return true;
        }, MAX_SETTINGS_CHECKS, SETTINGS_CHECK_DELAY_MILLIS, () -> {
            mSettingsButtonPending = false;
            onTimeout.run();
        });
    }

    /**
     * Return the string value associated with a particular resource ID. It will be stripped of any styled text information.
     *
     * @param stringRes The desired resource identifier.
     * @return String The string data associated with the resource, stripped of styled text information.
     * @throws Resources.NotFoundException Throws NotFoundException if the given ID or package does not exist.
     */
    private String getString(@NonNull AccessibilityEvent event, @NonNull String stringRes)
            throws Resources.NotFoundException {
        CharSequence packageName = event.getPackageName();
        CharSequence className = event.getClassName();
        if (TextUtils.isEmpty(packageName)) {
            throw new Resources.NotFoundException("Empty package name");
        }
        ResourceUtil resUtil = new ResourceUtil();
        if (!TextUtils.isEmpty(className)) {
            if (!resUtil.loadResources(mPm, packageName.toString(), className.toString())
                    && !resUtil.loadResources(mPm, packageName.toString())
                    && !resUtil.loadAndroidResources()) {
                throw new Resources.NotFoundException("Couldn't load resources for package: " + packageName
                        + ", class: " + className);
            }
        } else if (!resUtil.loadResources(mPm, packageName.toString()) && !resUtil.loadAndroidResources()) {
            throw new Resources.NotFoundException("Couldn't load resources for package: " + packageName);
        }
        return resUtil.getString(stringRes);
    }
}
