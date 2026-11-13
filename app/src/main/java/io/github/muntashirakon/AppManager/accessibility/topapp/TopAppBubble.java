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
 * The bubble itself: a small window showing the top app. When another app draws an overlay, that app gets its own row
 * on top in the error colors and the app underneath stays below it. A tap on a row asks to open that app, a long press
 * shows the last few apps inside the same window, and a drag moves it.
 */
final class TopAppBubble {
    private static final String TAG = TopAppBubble.class.getSimpleName();
    /** A gone overlay fades so a live one stands out, but stays readable. */
    @VisibleForTesting
    static final float GONE_OVERLAY_ALPHA = 0.6f;

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
    private final TextView mUnderlyingLayer;
    @NonNull
    private final View mOverlayRow;
    @NonNull
    private final ImageView mOverlayIcon;
    @NonNull
    private final TextView mOverlayLayer;
    @NonNull
    private final TextView mOverlayPackageName;
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
    @Nullable
    private String mOverlayApp;
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
        mUnderlyingLayer = mRoot.findViewById(R.id.underlying_layer);
        mOverlayRow = mRoot.findViewById(R.id.top_app_overlay_row);
        mOverlayIcon = mRoot.findViewById(R.id.overlay_icon);
        mOverlayLayer = mRoot.findViewById(R.id.overlay_layer);
        mOverlayPackageName = mRoot.findViewById(R.id.overlay_package_name);
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
        // Either row drags the whole bubble.
        mPill.setOnTouchListener(this::onRowTouch);
        mOverlayRow.setOnTouchListener(this::onRowTouch);
        mPill.setOnClickListener(v -> openApp(mTopApp));
        mOverlayRow.setOnClickListener(v -> openApp(mOverlayApp));
        View.OnLongClickListener toggleRecents = v -> {
            setExpanded(mRecentsContainer.getVisibility() != View.VISIBLE);
            return true;
        };
        mPill.setOnLongClickListener(toggleRecents);
        mOverlayRow.setOnLongClickListener(toggleRecents);
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
        setLayers(packageName, null, false, recents);
    }

    /**
     * @param underlying      The app under any overlay, or {@code null} when it isn't known, such as over the home
     *                        screen
     * @param overlay         Another app's overlay, or {@code null}
     * @param overlayOnScreen Whether that overlay is still up
     */
    @MainThread
    void setLayers(@Nullable String underlying, @Nullable String overlay, boolean overlayOnScreen,
                   @NonNull List<String> recents) {
        mRecents = recents;
        bindOverlay(overlay, overlayOnScreen);
        // Over the home screen there's nothing underneath worth a row.
        mPill.setVisibility(underlying == null && overlay != null ? View.GONE : View.VISIBLE);
        mUnderlyingLayer.setVisibility(overlay != null ? View.VISIBLE : View.GONE);
        if (underlying == null) {
            mTopApp = null;
            mPackageName.setText(R.string.top_app_overlay_waiting);
            mPill.setContentDescription(mContext.getString(R.string.top_app_overlay_waiting));
            mIcon.setImageResource(R.drawable.ic_android);
        } else {
            if (!underlying.equals(mTopApp)) {
                mTopApp = underlying;
                mPackageName.setText(underlying);
                bindIcon(underlying, mIcon, null);
            }
            mPill.setContentDescription(mContext.getString(overlay != null
                    ? R.string.top_app_overlay_underneath_description
                    : R.string.top_app_overlay_bubble_description, underlying));
        }
        if (mRecentsContainer.getVisibility() == View.VISIBLE) {
            bindRecents();
        }
    }

    private void bindOverlay(@Nullable String overlay, boolean onScreen) {
        if (overlay == null) {
            mOverlayApp = null;
            mOverlayRow.setVisibility(View.GONE);
            return;
        }
        mOverlayRow.setVisibility(View.VISIBLE);
        mOverlayRow.setAlpha(onScreen ? 1f : GONE_OVERLAY_ALPHA);
        mOverlayLayer.setText(onScreen ? R.string.top_app_overlay_layer_overlay
                : R.string.top_app_overlay_layer_overlay_gone);
        mOverlayRow.setContentDescription(mContext.getString(onScreen ? R.string.top_app_overlay_overlay_description
                : R.string.top_app_overlay_overlay_gone_description, overlay));
        if (!overlay.equals(mOverlayApp)) {
            mOverlayApp = overlay;
            mOverlayPackageName.setText(overlay);
            bindIcon(overlay, mOverlayIcon, null);
        }
    }

    private void openApp(@Nullable String packageName) {
        if (packageName != null) {
            setExpanded(false);
            mCallback.onOpenApp(packageName);
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
    View getOverlayRow() {
        return mOverlayRow;
    }

    @VisibleForTesting
    @NonNull
    TextView getOverlayLayer() {
        return mOverlayLayer;
    }

    @VisibleForTesting
    @NonNull
    TextView getUnderlyingLayer() {
        return mUnderlyingLayer;
    }

    @VisibleForTesting
    @NonNull
    ViewGroup getRecentsContainer() {
        return mRecentsContainer;
    }

    private boolean onRowTouch(@NonNull View view, @NonNull MotionEvent event) {
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
