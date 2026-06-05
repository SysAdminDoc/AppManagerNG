// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class InstallerIconSanitizer {
    private static final int MAX_ICON_EDGE_DP = 96;
    private static final int MAX_ICON_BYTES = 1024 * 1024;

    private InstallerIconSanitizer() {
    }

    @NonNull
    static Drawable sanitizeForDialog(@NonNull Context context, @Nullable Drawable icon) {
        Drawable fallbackIcon = context.getPackageManager().getDefaultActivityIcon();
        if (icon == null) {
            return fallbackIcon;
        }
        int maxEdge = Math.max(1, Math.round(MAX_ICON_EDGE_DP * context.getResources().getDisplayMetrics().density));
        try {
            if (icon instanceof BitmapDrawable) {
                return sanitizeBitmapDrawable(context, (BitmapDrawable) icon, fallbackIcon, maxEdge);
            }
            int width = icon.getIntrinsicWidth();
            int height = icon.getIntrinsicHeight();
            if (isBounded(width, height, maxEdge)) {
                return icon;
            }
            return renderDrawable(context, icon, fallbackIcon, maxEdge);
        } catch (Throwable ignore) {
            return fallbackIcon;
        }
    }

    @NonNull
    private static Drawable sanitizeBitmapDrawable(@NonNull Context context, @NonNull BitmapDrawable icon,
                                                   @NonNull Drawable fallbackIcon, int maxEdge) {
        Bitmap bitmap = icon.getBitmap();
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return fallbackIcon;
        }
        if (isBounded(bitmap.getWidth(), bitmap.getHeight(), maxEdge)
                && bitmap.getAllocationByteCount() <= MAX_ICON_BYTES) {
            return icon;
        }
        int[] scaledSize = scaledSize(bitmap.getWidth(), bitmap.getHeight(), maxEdge);
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledSize[0], scaledSize[1], true);
        return new BitmapDrawable(context.getResources(), scaled);
    }

    @NonNull
    private static Drawable renderDrawable(@NonNull Context context, @NonNull Drawable icon,
                                           @NonNull Drawable fallbackIcon, int maxEdge) {
        int width = icon.getIntrinsicWidth();
        int height = icon.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return icon;
        }
        int[] scaledSize = scaledSize(width, height, maxEdge);
        Bitmap bitmap = Bitmap.createBitmap(scaledSize[0], scaledSize[1], Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int oldLeft = icon.getBounds().left;
        int oldTop = icon.getBounds().top;
        int oldRight = icon.getBounds().right;
        int oldBottom = icon.getBounds().bottom;
        try {
            icon.setBounds(0, 0, scaledSize[0], scaledSize[1]);
            icon.draw(canvas);
        } catch (Throwable ignore) {
            return fallbackIcon;
        } finally {
            icon.setBounds(oldLeft, oldTop, oldRight, oldBottom);
        }
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private static boolean isBounded(int width, int height, int maxEdge) {
        long pixels = (long) width * (long) height;
        return width > 0 && height > 0 && width <= maxEdge && height <= maxEdge
                && pixels * 4L <= MAX_ICON_BYTES;
    }

    @NonNull
    private static int[] scaledSize(int width, int height, int maxEdge) {
        float scale = Math.min((float) maxEdge / width, (float) maxEdge / height);
        return new int[]{
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale))
        };
    }
}
