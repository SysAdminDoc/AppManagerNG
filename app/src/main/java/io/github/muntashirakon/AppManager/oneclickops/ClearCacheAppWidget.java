// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.utils.appearance.AppWidgetThemeUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

public class ClearCacheAppWidget extends AppWidgetProvider {

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        context = AppearanceUtils.getThemedWidgetContext(context, false);
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget_clear_cache);
        AppWidgetThemeUtils.Palette palette = AppWidgetThemeUtils.getPalette(context);
        AppWidgetThemeUtils.applyWidgetSurface(views, android.R.id.background, palette);
        AppWidgetThemeUtils.setImageTint(views, palette.primary, R.id.appwidget_icon);
        Intent intent = new Intent(context, OneClickOpsActivity.class);
        intent.putExtra(OneClickOpsActivity.EXTRA_OP, BatchOpsManager.OP_CLEAR_CACHE);
        views.setOnClickPendingIntent(android.R.id.background, PendingIntentCompat.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false));
        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }
}
