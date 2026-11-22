// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2012 Nolan Lawson
public class RecordingWidgetProvider extends AppWidgetProvider {
    public static final String TAG = RecordingWidgetProvider.class.getSimpleName();

    public static final String URI_SCHEME = "log_viewer_widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        Log.d(TAG, "onUpdate() for appWidgetIds %s", Arrays.toString(appWidgetIds));

        // track which widgets were created, since there's a bug in the android system that lets
        // stale app widget ids stick around
        PreferenceHelper.setWidgetExistsPreference(context, appWidgetIds);

        WidgetHelper.updateWidgets(context, appWidgetIds);
    }

    // Taps go to RecordingWidgetActionReceiver, which isn't exported. This provider is, for the
    // launcher's widget broadcasts, so it must not act on anything else.
}
