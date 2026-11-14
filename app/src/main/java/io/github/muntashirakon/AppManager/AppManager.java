// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.StrictMode;
import android.sun.security.provider.JavaKeyStoreProvider;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.window.embedding.RuleController;

import com.topjohnwu.superuser.Shell;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.security.Security;

import dalvik.system.ZipPathValidator;
import io.github.muntashirakon.AppManager.apk.behavior.AutoFreezeOnLockReceiver;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.compat.ProcessCompat;
import io.github.muntashirakon.AppManager.debloat.DebloatDefinitionsUpdater;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryPruneScheduler;
import io.github.muntashirakon.AppManager.i18n.LanguagePackManager;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.misc.ProfilingTriggerHelper;
import io.github.muntashirakon.AppManager.oneclickops.ClearCacheAppWidget;
import io.github.muntashirakon.AppManager.scanner.TrackerDatabaseFreshnessChecker;
import io.github.muntashirakon.AppManager.self.DeferredLogAccess;
import io.github.muntashirakon.AppManager.self.PackageEventSync;
import io.github.muntashirakon.AppManager.usage.DataUsageAppWidget;
import io.github.muntashirakon.AppManager.usage.ScreenTimeAppWidget;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

public class AppManager extends Application {
    private AutoFreezeOnLockReceiver mAutoFreezeOnLockReceiver;
    private int mNightModeMask;

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // We don't rely on the system to detect a zip slip attack
            ZipPathValidator.clearCallback();
        }
    }

    @Keep
    @Override
    public void onCreate() {
        super.onCreate();
        if (ProcessCompat.isIsolatedProcess()) {
            // The thumbnail decoder's process: no permissions, no app data and no receivers, so
            // none of the setup below can run there, and the decoder needs none of it.
            return;
        }
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
        if (!Utils.isRoboUnitTest()) {
            // Every Robolectric test makes a new Application in the same JVM, and each handler
            // keeps the one before it, so the chain would hold every test's app and resources.
            Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        }
        mNightModeMask = getNightModeMask(getResources().getConfiguration());
        configureActivityEmbeddingSplits();
        // Before the locale is applied, so an imported language file is already in place
        LanguagePackManager.installOnStartup(this);
        AppearanceUtils.init(this);
        DeferredLogAccess.register(this);
        registerAutoFreezeOnLockReceiver();
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new JavaKeyStoreProvider());
        Security.addProvider(new BouncyCastleProvider());
        ProfilingTriggerHelper.registerTriggersIfSupported(this);
        DebloatDefinitionsUpdater.scheduleUpdateIfAllowed(this);
        TrackerDatabaseFreshnessChecker.scheduleCheckIfAllowed(this);
        OpHistoryPruneScheduler.scheduleOrCancel(this);
        ThreadUtils.postOnBackgroundThread(() -> PackageInstallerService.cleanupStaleInstallSessions(this));
        if (!Utils.isRoboUnitTest()) {
            PackageEventSync.start(this);
        }
    }

    private void configureActivityEmbeddingSplits() {
        RuleController.getInstance(this)
                .setRules(RuleController.parseRules(this, R.xml.main_activity_splits));
    }

    private void registerAutoFreezeOnLockReceiver() {
        mAutoFreezeOnLockReceiver = new AutoFreezeOnLockReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(this, mAutoFreezeOnLockReceiver, filter,
                ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ProcessCompat.isIsolatedProcess()) {
            return;
        }
        int nightModeMask = getNightModeMask(newConfig);
        if (nightModeMask == mNightModeMask) {
            return;
        }
        mNightModeMask = nightModeMask;
        ThreadUtils.postOnBackgroundThread(() -> refreshWidgetsForTheme(this));
    }

    static int getNightModeMask(@NonNull Configuration configuration) {
        return configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    private static void refreshWidgetsForTheme(@NonNull Context context) {
        ScreenTimeAppWidget.updateWidgets(context);
        DataUsageAppWidget.updateWidgets(context);
        ClearCacheAppWidget.updateWidgets(context);
        WidgetHelper.updateWidgets(context);
    }

    @Keep
    @Override
    @SuppressWarnings("deprecation") // addHiddenApiExemptions deprecated in HiddenApiBypass 6.2+; replace when that ships
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !Utils.isRoboUnitTest()
                && !ProcessCompat.isIsolatedProcess()) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            StaticDataset.cleanup();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        StaticDataset.cleanup();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mAutoFreezeOnLockReceiver != null) {
            unregisterReceiver(mAutoFreezeOnLockReceiver);
            mAutoFreezeOnLockReceiver = null;
        }
        StaticDataset.cleanup();
    }
}
