// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Crash reports, the support bundle, the diagnostic ZIP and the install transcript go to the maintainer, so the
 * strings they borrow from resources are read in English whatever language the app shows (issue #31).
 */
public final class ReportLocale {
    private ReportLocale() {
    }

    /** A context that reads this app's resources in English. The app's own language is left alone. */
    @AnyThread
    @NonNull
    public static Context english(@NonNull Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.ENGLISH);
        return context.createConfigurationContext(configuration);
    }
}
