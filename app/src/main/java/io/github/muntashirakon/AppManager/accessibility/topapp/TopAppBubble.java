// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

/**
 * The bubble itself: a small window showing the top app. A tap asks to open that app, a long press shows the last few
 * apps inside the same window, and a drag moves it.
 */
final class TopAppBubble {
    private static final String TAG = TopAppBubble.class.getSimpleName();

    interface Callback {
        void onOpenApp(@NonNull String packageName);
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final WindowManager mWindowManager;
    @NonNull
    private final Callback mCallback;
    @NonNull
    private final View mRoot;
    @NonNull
    private final View mPill;
    @NonNull
    private final ImageView mIcon;
    @NonNull
    private final TextView mPackageName;
    @NonNull
    private final ViewGroup mRecentsContainer;
    @NonNull
    private final WindowManager.LayoutParams mParams;
    private final int mMarginPx;
    private final int mTouchSlopPx;
    @NonNull
    private Point mDisplaySize;
    @NonNull
    private List<String> mRecents = Collections.emptyList();
    @Nullable
    private String mTopApp;
    private boolean mShown;
    private boolean mDragging;
    private float mDownRawX;
    private float mDownRawY;
    private int mDownParamX;
    private int mDownParamY;

    // The bubble is its own window, so its root has no parent to inflate against.
    @SuppressLint("InflateParams")
    TopAppBubble(@NonNull Context context, @NonNull Callback callback) {
        mContext = AppearanceUtils.getThemedContext(context, true);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mCallback = callback;
        mRoot = LayoutInflater.from(mContext).inflate(R.layout.window_top_app_bubble, null);
        mPill = mRoot.findViewById(R.id.top_app_pill);
        mIcon = mRoot.findViewById(android.R.id.icon);
        mPackageName = mRoot.findViewById(R.id.package_name);
        mRecentsContainer = mRoot.findViewById(R.id.top_app_recents);
        float density = context.getResources().getDisplayMetrics().density;
        mMarginPx = TopAppOverlayPolicy.edgeMarginPx(density);
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mDisplaySize = readDisplaySize();
        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                TopAppOverlayPolicy.windowTypeForSdk(Build.VERSION.SDK_INT),
                TopAppOverlayPolicy.windowFlags(),
                PixelFormat.TRANSLUCENT);
        mParams.gravity = TopAppOverlayPolicy.GRAVITY;
        mParams.alpha = TopAppOverlayPolicy.COLLAPSED_ALPHA;
        mParams.x = mMarginPx;
        mParams.y = Math.round(mDisplaySize.y * TopAppOverlayPolicy.INITIAL_VERTICAL_FRACTION);
        mPill.setOnTouchListener(this::onPillTouch);
        mPill.setOnClickListener(v -> {
            if (mTopApp != null) {
                setExpanded(false);
                mCallback.onOpenApp(mTopApp);
            }
        });
        mPill.setOnLongClickListener(v -> {
            setExpanded(mRecentsContainer.getVisibility() != View.VISIBLE);
            return true;
        });
        // A longer package name or the recent rows can push the window past the edge of the screen.
        mRoot.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                moveTo(mParams.x, mParams.y));
        setTopApp(null, Collections.emptyList());
    }

    /**
     * @return {@code false} if Android refused the window
     */
    @MainThread
    boolean show() {
        if (mShown) {
            return true;
        }
        try {
            mWindowManager.addView(mRoot, mParams);
            mShown = true;
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not add the top app bubble.", e);
            return false;
        }
    }

    @MainThread
    void dismiss() {
        if (!mShown) {
            return;
        }
        mShown = false;
        try {
            mWindowManager.removeView(mRoot);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not remove the top app bubble.", e);
        }
    }

    @MainThread
    void setTopApp(@Nullable String packageName, @NonNull List<String> recents) {
        mRecents = recents;
        if (packageName == null) {
            mTopApp = null;
            mPackageName.setText(R.string.top_app_overlay_waiting);
            mPill.setContentDescription(mContext.getString(R.string.top_app_overlay_waiting));
            mIcon.setImageResource(R.drawable.ic_android);
        } else if (!packageName.equals(mTopApp)) {
            mTopApp = packageName;
            mPackageName.setText(packageName);
            mPill.setContentDescription(mContext.getString(R.string.top_app_overlay_bubble_description, packageName));
            bindIcon(packageName, mIcon, null);
        }
        if (mRecentsContainer.getVisibility() == View.VISIBLE) {
            bindRecents();
        }
    }

    /** Called when the display may have changed shape. */
    @MainThread
    void onConfigurationChanged() {
        Point old = mDisplaySize;
        mDisplaySize = readDisplaySize();
        if (old.equals(mDisplaySize)) {
            return;
        }
        moveTo(TopAppOverlayPolicy.remap(mParams.x, mRoot.getWidth(), old.x, mDisplaySize.x, mMarginPx),
                TopAppOverlayPolicy.remap(mParams.y, mRoot.getHeight(), old.y, mDisplaySize.y, mMarginPx));
    }

    @VisibleForTesting
    @NonNull
    WindowManager.LayoutParams getLayoutParams() {
        return mParams;
    }

    @VisibleForTesting
    @NonNull
    View getPill() {
        return mPill;
    }

    @VisibleForTesting
    @NonNull
    ViewGroup getRecentsContainer() {
        return mRecentsContainer;
    }

    private boolean onPillTouch(@NonNull View view, @NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = false;
                mDownRawX = event.getRawX();
                mDownRawY = event.getRawY();
                mDownParamX = mParams.x;
                mDownParamY = mParams.y;
                // Let the view see it too, for its pressed state and its click and long-press timers.
                return false;
            case MotionEvent.ACTION_MOVE: {
                float deltaX = event.getRawX() - mDownRawX;
                float deltaY = event.getRawY() - mDownRawY;
                if (!mDragging && TopAppOverlayPolicy.isDrag(deltaX, deltaY, mTouchSlopPx)) {
                    mDragging = true;
                    // Cancels the pending click and long press: a drag must never also open an app.
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    view.onTouchEvent(cancel);
                    cancel.recycle();
                }
                if (mDragging) {
                    moveTo(mDownParamX + Math.round(deltaX), mDownParamY + Math.round(deltaY));
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean wasDragging = mDragging;
                mDragging = false;
                return wasDragging;
            }
            default:
                return false;
        }
    }

    private void moveTo(int x, int y) {
        int clampedX = TopAppOverlayPolicy.clamp(x, mRoot.getWidth(), mDisplaySize.x, mMarginPx);
        int clampedY = TopAppOverlayPolicy.clamp(y, mRoot.getHeight(), mDisplaySize.y, mMarginPx);
        if (clampedX == mParams.x && clampedY == mParams.y) {
            return;
        }
        mParams.x = clampedX;
        mParams.y = clampedY;
        applyLayout();
    }

    private void applyLayout() {
        if (!mShown) {
            return;
        }
        try {
            mWindowManager.updateViewLayout(mRoot, mParams);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not move the top app bubble.", e);
        }
    }

    private void setExpanded(boolean expanded) {
        if (expanded) {
            bindRecents();
        }
        mRecentsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        mParams.alpha = expanded ? TopAppOverlayPolicy.EXPANDED_ALPHA : TopAppOverlayPolicy.COLLAPSED_ALPHA;
        applyLayout();
    }

    private void bindRecents() {
        mRecentsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (mRecents.isEmpty()) {
            View row = inflater.inflate(R.layout.item_top_app_recent, mRecentsContainer, false);
            row.<TextView>findViewById(android.R.id.title).setText(R.string.top_app_overlay_waiting);
            row.<TextView>findViewById(android.R.id.summary).setVisibility(View.GONE);
            row.<ImageView>findViewById(android.R.id.icon).setImageResource(R.drawable.ic_android);
            mRecentsContainer.addView(row);
            return;
        }
        for (String packageName : mRecents) {
            View row = inflater.inflate(R.layout.item_top_app_recent, mRecentsContainer, false);
            TextView title = row.findViewById(android.R.id.title);
            title.setText(packageName);
            row.<TextView>findViewById(android.R.id.summary).setText(packageName);
            bindIcon(packageName, row.findViewById(android.R.id.icon), title);
            row.setOnClickListener(v -> {
                setExpanded(false);
                mCallback.onOpenApp(packageName);
            });
            mRecentsContainer.addView(row);
        }
    }

    /** Looks the app up off the main thread, since this runs each time another app comes to the front. */
    private void bindIcon(@NonNull String packageName, @NonNull ImageView icon, @Nullable TextView label) {
        icon.setTag(packageName);
        PackageManager pm = mContext.getPackageManager();
        ThreadUtils.postOnBackgroundThread(() -> {
            ApplicationInfo info;
            CharSequence appLabel;
            try {
                info = pm.getApplicationInfo(packageName, 0);
                appLabel = info.loadLabel(pm);
            } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                // It may have been uninstalled already. The package name is still shown.
                info = null;
                appLabel = null;
            }
            ApplicationInfo finalInfo = info;
            CharSequence finalLabel = appLabel;
            ThreadUtils.postOnMainThread(() -> {
                if (!packageName.equals(icon.getTag())) {
                    return;
                }
                ImageLoader.getInstance().displayImage(packageName, finalInfo, icon);
                if (label != null && finalLabel != null) {
                    label.setText(finalLabel);
                }
            });
        });
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private Point readDisplaySize() {
        Point size = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(size);
        return size;
    }
}
