// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import android.annotation.SuppressLint;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.SystemClock;
import android.os.UserHandleHidden;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.widget.TextInputTextView;

public class TrackerWindow implements View.OnTouchListener {
    public final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mWindowLayoutParams;
    private final View mView;
    private final ShapeableImageView mIconView;
    private final MaterialCardView mContentView;
    private final TextInputTextView mPackageNameView;
    private final TextInputTextView mActivityNameView;
    private final TextInputTextView mClassNameView;
    private final TextInputTextView mClassHierarchyView;
    private final MaterialButton mPlayPauseButton;
    private final Point mWindowSize = new Point(0, 0);
    private final Point mWindowPosition = new Point(0, 0);
    private final Point mPressPosition = new Point(0, 0);
    private final float mDensity;
    private final int mEdgeMargin;
    private final int mFallbackWindowSize;
    private int mMaxWidth;
    private boolean mPaused = false;
    private boolean mIconified = false;
    private boolean mViewAttached = false;
    private int mWindowManagerFailures = 0;
    private long mLastLayoutUpdateUptime = -1L;
    @Nullable
    private Future<?> mClassHierarchyResult;

    @SuppressLint("ClickableViewAccessibility")
    public TrackerWindow(@NonNull Context context) {
        Context themedContext = AppearanceUtils.getThemedContext(context, true);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDensity = context.getResources().getDisplayMetrics().density;
        mEdgeMargin = TrackerOverlayPolicy.edgeMarginPx(mDensity);
        mFallbackWindowSize = TrackerOverlayPolicy.fallbackWindowSizePx(mDensity);
        refreshWindowMetrics();
        int type = TrackerOverlayPolicy.windowTypeForSdk(Build.VERSION.SDK_INT);
        int flags = TrackerOverlayPolicy.windowFlags();
        mWindowLayoutParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type, flags, PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.gravity = Gravity.CENTER;
        mWindowLayoutParams.width = mMaxWidth;
        MotionUtils.setWindowAnimations(context, mWindowLayoutParams, android.R.style.Animation_Toast);

        mView = View.inflate(themedContext, R.layout.window_activity_tracker, null);
        mIconView = mView.findViewById(R.id.icon);
        mContentView = mView.findViewById(R.id.content);
        mPackageNameView = mView.findViewById(R.id.package_name);
        mActivityNameView = mView.findViewById(R.id.activity_name);
        mClassNameView = mView.findViewById(R.id.class_name);
        mClassHierarchyView = mView.findViewById(R.id.class_hierarchy);
        mPlayPauseButton = mView.findViewById(R.id.action_play_pause);
        mPackageNameView.setOnLongClickListener(v -> {
            Editable packageName = mPackageNameView.getText();
            if (TextUtils.isEmpty(packageName)) {
                return false;
            }
            copyText("Package name", packageName);
            return true;
        });
        mActivityNameView.setOnLongClickListener(v -> {
            Editable activityName = mActivityNameView.getText();
            if (TextUtils.isEmpty(activityName)) {
                return false;
            }
            copyText("Activity name", activityName);
            return true;
        });
        mClassNameView.setOnLongClickListener(v -> {
            Editable className = mClassNameView.getText();
            if (TextUtils.isEmpty(className)) {
                return false;
            }
            copyText("Class name", className);
            return true;
        });
        mClassHierarchyView.setOnLongClickListener(v -> {
            Editable hierarchy = mClassHierarchyView.getText();
            if (TextUtils.isEmpty(hierarchy)) {
                return false;
            }
            copyText("Class hierarchy", hierarchy);
            return true;
        });
        mView.findViewById(R.id.info).setOnClickListener(v -> {
            Editable packageName = mPackageNameView.getText();
            if (TextUtils.isEmpty(packageName)) {
                return;
            }
            Intent appInfoIntent = AppDetailsActivity.getIntent(context, packageName.toString(), UserHandleHidden.myUserId(), true);
            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(appInfoIntent);
            } catch (Throwable th) {
                UIUtils.displayLongToast("Error: " + th.getMessage());
            }
        });
        mView.findViewById(R.id.mini).setOnClickListener(v -> iconify());
        mPlayPauseButton.setOnClickListener(v -> {
            mPaused = !mPaused;
            updatePlayPauseButton();
        });
        mView.findViewById(android.R.id.closeButton).setOnClickListener(v -> dismiss());
        mIconView.setVisibility(View.GONE);
        mIconView.setOnClickListener(v -> expand());
        mView.findViewById(R.id.drag).setOnTouchListener(this);
        mIconView.setOnTouchListener(this);
        updatePlayPauseButton();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Point point;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            point = new Point((int) event.getRawX(), (int) event.getRawY());
            mPressPosition.set(point.x, point.y);
            mWindowPosition.set(mWindowLayoutParams.x, mWindowLayoutParams.y);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            point = new Point((int) event.getRawX(), (int) event.getRawY());
            int delX = point.x - mPressPosition.x;
            int delY = point.y - mPressPosition.y;
            mWindowLayoutParams.x = mWindowPosition.x + delX;
            mWindowLayoutParams.y = mWindowPosition.y + delY;
            updateLayout();
            return true;
        }
        if (v == mIconView && action == MotionEvent.ACTION_UP) {
            point = new Point((int) event.getRawX(), (int) event.getRawY());
            int delX = Math.abs(point.x - mPressPosition.x);
            int delY = Math.abs(point.y - mPressPosition.y);
            if (delX < 1 && delY < 1) {
                v.performClick();
                return true;
            }
        }
        return false;
    }

    public void showOrUpdate(AccessibilityEvent event) {
        if (!mViewAttached) {
            if (!attachView()) {
                return;
            }
        }
        if (!mPaused) {
            @Nullable
            CharSequence packageName = event.getPackageName();
            if (packageName != null && BuildConfig.APPLICATION_ID.contentEquals(packageName)) {
                // On some devices, this window always gets the focus
                CharSequence className = event.getClassName();
                if (className != null && "android.widget.EditText".contentEquals(className)) {
                    // For some reason, only this class is focused
                    if (event.getSource() == null) {
                        // No class hierarchy. This is the intended event
                        return;
                    }
                }
            }
            if (mClassHierarchyResult != null) {
                mClassHierarchyResult.cancel(true);
            }
            mPackageNameView.setText(packageName);
            mClassNameView.setText(event.getClassName());
            mClassHierarchyResult = ThreadUtils.postOnBackgroundThread(() -> {
                CharSequence classHierarchy = TextUtils.join("\n", getClassHierarchy(event));
                String activityName = getActivityName(event);
                ThreadUtils.postOnMainThread(() -> {
                    mActivityNameView.setText(activityName);
                    mClassHierarchyView.setText(classHierarchy);
                });
            });
        }
    }

    public void dismiss() {
        AccessibilityMultiplexer.getInstance().enableLeadingActivityTracker(false);
        mViewAttached = false;
        mWindowManagerFailures = 0;
        if (mClassHierarchyResult != null) {
            mClassHierarchyResult.cancel(true);
        }
        try {
            mWindowManager.removeView(mView);
        } catch (Exception ignore) {
        }
    }

    private void iconify() {
        mPaused = true;
        mIconified = true;
        mIconView.setVisibility(View.VISIBLE);
        mContentView.setVisibility(View.GONE);
        updatePlayPauseButton();
        updateLayout(true);
    }

    private void expand() {
        mContentView.setVisibility(View.VISIBLE);
        mIconView.setVisibility(View.GONE);
        mPaused = false;
        mIconified = false;
        updatePlayPauseButton();
        updateLayout(true);
    }

    private void updatePlayPauseButton() {
        mPlayPauseButton.setIconResource(mPaused ? R.drawable.ic_play_arrow : R.drawable.ic_pause);
        mPlayPauseButton.setContentDescription(mView.getContext().getString(mPaused
                ? R.string.tracker_window_resume
                : R.string.tracker_window_pause));
    }

    private void updateLayout() {
        updateLayout(false);
    }

    private void updateLayout(boolean force) {
        int displayedWidth = TrackerOverlayPolicy.displayedWidth(mIconified, mMaxWidth);
        applyWindowBounds(displayedWidth);
        long now = SystemClock.uptimeMillis();
        if (!force && !TrackerOverlayPolicy.shouldApplyLayoutUpdate(mLastLayoutUpdateUptime, now)) {
            return;
        }
        mLastLayoutUpdateUptime = now;
        try {
            mWindowManager.updateViewLayout(mView, mWindowLayoutParams);
            mWindowManagerFailures = 0;
        } catch (RuntimeException e) {
            handleWindowManagerFailure(e);
        }
    }

    private boolean attachView() {
        applyWindowBounds(mMaxWidth);
        try {
            mWindowManager.addView(mView, mWindowLayoutParams);
            mViewAttached = true;
            mWindowManagerFailures = 0;
            return true;
        } catch (RuntimeException e) {
            mViewAttached = false;
            handleWindowManagerFailure(e);
            return false;
        }
    }

    private void applyWindowBounds(int displayedWidth) {
        refreshWindowMetrics();
        int windowWidth = displayedWidth == WindowManager.LayoutParams.WRAP_CONTENT
                ? measuredWidthOrFallback(mIconified ? mIconView : mView)
                : displayedWidth;
        int windowHeight = measuredHeightOrFallback(mIconified ? mIconView : mView);
        mWindowLayoutParams.width = displayedWidth;
        mWindowLayoutParams.x = TrackerOverlayPolicy.clampHorizontalOffset(
                mWindowLayoutParams.x, mWindowSize.x, windowWidth, mEdgeMargin);
        mWindowLayoutParams.y = TrackerOverlayPolicy.clampVerticalOffset(
                mWindowLayoutParams.y, mWindowSize.y, windowHeight, mEdgeMargin);
    }

    private int measuredWidthOrFallback(@NonNull View view) {
        int measured = view.getWidth() > 0 ? view.getWidth() : view.getMeasuredWidth();
        if (measured > 0) {
            return measured;
        }
        return mFallbackWindowSize;
    }

    private int measuredHeightOrFallback(@NonNull View view) {
        int measured = view.getHeight() > 0 ? view.getHeight() : view.getMeasuredHeight();
        if (measured > 0) {
            return measured;
        }
        return mFallbackWindowSize;
    }

    @SuppressWarnings("deprecation")
    private void refreshWindowMetrics() {
        Display display = mWindowManager.getDefaultDisplay();
        display.getRealSize(mWindowSize);
        if (mWindowSize.x <= 0) {
            mWindowSize.x = Math.max(1, display.getWidth());
        }
        if (mWindowSize.y <= 0) {
            mWindowSize.y = Math.max(1, display.getHeight());
        }
        mMaxWidth = TrackerOverlayPolicy.expandedWidthPx(mWindowSize.x, mDensity);
    }

    private void handleWindowManagerFailure(@NonNull RuntimeException error) {
        ++mWindowManagerFailures;
        if (!TrackerOverlayPolicy.shouldDisableAfterFailure(mWindowManagerFailures)) {
            return;
        }
        mViewAttached = false;
        AccessibilityMultiplexer.getInstance().enableLeadingActivityTracker(false);
        try {
            mWindowManager.removeView(mView);
        } catch (Exception ignore) {
        }
        UIUtils.displayLongToast("Tracker overlay disabled: " + error.getMessage());
    }

    private void copyText(CharSequence label, CharSequence content) {
        Utils.copyToClipboard(mView.getContext(), label, content);
    }

    @Nullable
    public String getActivityName(@NonNull AccessibilityEvent event) {
        if (event.getPackageName() == null) {
            return null;
        }
        String packageName = event.getPackageName().toString();
        UsageEvents.Event usageEvent = new UsageEvents.Event();
        long currentTimeMillis = System.currentTimeMillis();
        long timeDiff = 5_000;
        int tries = 0;
        do {
            UsageEvents queryEvents = UsageStatsManagerCompat.queryEvents(currentTimeMillis - timeDiff,
                    currentTimeMillis, UserHandleHidden.myUserId());
            if (queryEvents == null) {
                return null;
            }
            long lastTime = 0L;
            String activityName = null;
            while (queryEvents.hasNextEvent()) {
                queryEvents.getNextEvent(usageEvent);
                if (usageEvent.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                        && Objects.equals(packageName, usageEvent.getPackageName())
                        && lastTime < usageEvent.getTimeStamp()) {
                    lastTime = usageEvent.getTimeStamp();
                    activityName = usageEvent.getClassName();
                }
            }
            if (activityName != null) {
                return activityName;
            }
            timeDiff *= 60;
        } while ((++tries) != 3);
        return null;
    }

    @NonNull
    private static List<CharSequence> getClassHierarchy(@NonNull AccessibilityEvent event) {
        List<CharSequence> classHierarchies = new ArrayList<>();
        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo != null) {
            classHierarchies.add(nodeInfo.getClassName());
            int depth = 0;
            while (depth < 20) { // Limit depth to avoid running forever
                AccessibilityNodeInfo tmpNodeInfo = nodeInfo.getParent();
                if (tmpNodeInfo != null) {
                    nodeInfo.recycle();
                    nodeInfo = tmpNodeInfo;
                    classHierarchies.add(nodeInfo.getClassName());
                } else {
                    // Max depth reached
                    break;
                }
                ++depth;
                if (ThreadUtils.isInterrupted()) {
                    return Collections.emptyList();
                }
            }
            try {
                if (depth == 20) {
                    classHierarchies.add("...");
                }
            } finally {
                nodeInfo.recycle();
            }
        }
        Collections.reverse(classHierarchies);
        if (ThreadUtils.isInterrupted()) {
            return Collections.emptyList();
        }
        int size = classHierarchies.size();
        if (size <= 1) {
            return classHierarchies;
        }
        classHierarchies.set(0, "┬ " + classHierarchies.get(0));
        for (int i = 1; i < size; ++i) {
            StringBuilder sb = new StringBuilder();
            for (int j = 1; j < i; ++j) {
                sb.append(' ');
            }
            if (i != (size - 1)) {
                sb.append("└┬ ");
            } else sb.append("└─ ");
            sb.append(classHierarchies.get(i));
            classHierarchies.set(i, sb.toString());
            if (ThreadUtils.isInterrupted()) {
                return Collections.emptyList();
            }
        }
        return classHierarchies;
    }
}
