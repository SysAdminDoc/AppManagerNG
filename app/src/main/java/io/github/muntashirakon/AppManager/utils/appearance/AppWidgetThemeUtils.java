// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.material.color.MaterialColors;

public final class AppWidgetThemeUtils {
    private AppWidgetThemeUtils() {
    }

    @NonNull
    public static Palette getPalette(@NonNull Context context) {
        int surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0);
        int onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0);
        int onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant,
                onSurface);
        return new Palette(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, surface),
                onSurface,
                onSurfaceVariant,
                MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, onSurface),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, surface),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, onSurface),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, surface),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer, onSurface),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiaryContainer, surface),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnTertiaryContainer, onSurface));
    }

    public static void applyWidgetSurface(@NonNull RemoteViews views, @IdRes int viewId, @NonNull Palette palette) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setBackgroundTint(views, viewId, palette.surfaceContainer);
        }
    }

    public static void setTextColor(@NonNull RemoteViews views, @ColorInt int color, @IdRes int... viewIds) {
        for (int viewId : viewIds) {
            views.setTextColor(viewId, color);
        }
    }

    public static void setImageTint(@NonNull RemoteViews views, @ColorInt int color, @IdRes int... viewIds) {
        for (int viewId : viewIds) {
            views.setInt(viewId, "setColorFilter", color);
        }
    }

    public static void setUsageBubbleColors(@NonNull RemoteViews views, @IdRes int timeViewId, @IdRes int dotViewId,
            @ColorInt int backgroundColor, @ColorInt int textColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setBackgroundTint(views, timeViewId, backgroundColor);
            setBackgroundTint(views, dotViewId, backgroundColor);
        }
        views.setTextColor(timeViewId, textColor);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static void setBackgroundTint(@NonNull RemoteViews views, @IdRes int viewId, @ColorInt int color) {
        views.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color));
    }

    public static final class Palette {
        @ColorInt
        public final int surfaceContainer;
        @ColorInt
        public final int onSurface;
        @ColorInt
        public final int onSurfaceVariant;
        @ColorInt
        public final int primary;
        @ColorInt
        public final int primaryContainer;
        @ColorInt
        public final int onPrimaryContainer;
        @ColorInt
        public final int secondaryContainer;
        @ColorInt
        public final int onSecondaryContainer;
        @ColorInt
        public final int tertiaryContainer;
        @ColorInt
        public final int onTertiaryContainer;

        private Palette(@ColorInt int surfaceContainer, @ColorInt int onSurface, @ColorInt int onSurfaceVariant,
                @ColorInt int primary, @ColorInt int primaryContainer, @ColorInt int onPrimaryContainer,
                @ColorInt int secondaryContainer, @ColorInt int onSecondaryContainer, @ColorInt int tertiaryContainer,
                @ColorInt int onTertiaryContainer) {
            this.surfaceContainer = surfaceContainer;
            this.onSurface = onSurface;
            this.onSurfaceVariant = onSurfaceVariant;
            this.primary = primary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
            this.secondaryContainer = secondaryContainer;
            this.onSecondaryContainer = onSecondaryContainer;
            this.tertiaryContainer = tertiaryContainer;
            this.onTertiaryContainer = onTertiaryContainer;
        }
    }
}
