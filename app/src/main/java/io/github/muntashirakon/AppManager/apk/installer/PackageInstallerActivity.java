// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_ABORTED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_BLOCKED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_CONFLICT;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INVALID;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SECURITY;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_ABANDON;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_COMMIT;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_CREATE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_WRITE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_STORAGE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;

import android.Manifest;
import android.annotation.UserIdInt;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.CachedApkSource;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewFragment;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.DeveloperVerificationCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Activity that manages installing and confirming package installation. Actual installation is done by
 * {@link PackageInstallerService}.
 * <p>
 * How the installer works:
 * <ol>
 * <li>When the installation of a package is requested, it is either stored in queue or loaded directly if the queue is
 * empty.
 * <li>Then, it is checked whether there's an already installed package by the same name. If there exists any, the user
 * is offered to reinstall, upgrade or downgrade the package depending on the features supported by the present mode of
 * operation. Otherwise, the user is asked to confirm installation. Before doing so, however, a changelog may be
 * listed if it is enabled in settings.
 * <li>Next, if it is a split app, the user is asked to choose the splits to be installed. Otherwise, the installer
 * proceeds to the next phase directly.
 * <li>If display options is enabled, the options are displayed so that the user can tweak the present installer.
 * Otherwise, the installed proceeds to the next phase.
 * <li>Installer takes necessary steps to launch a installer service to initiate the installation.
 * </ol>
 */
public class PackageInstallerActivity extends BaseActivity implements InstallerDialogHelper.OnClickButtonsListener {
    public static final String TAG = PackageInstallerActivity.class.getSimpleName();

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.setData(uri);
        return intent;
    }

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, ApkSource apkSource) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        IntentCompat.putWrappedParcelableExtra(intent, EXTRA_APK_FILE_LINK, apkSource);
        return intent;
    }

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, @NonNull String packageName) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.setData(Uri.parse("package:" + packageName));
        return intent;
    }

    private static final String EXTRA_APK_FILE_LINK = "link";
    static final String EXTRA_BATCH_INSTALL = BuildConfig.APPLICATION_ID + ".extra.BATCH_INSTALL";
    public static final String ACTION_PACKAGE_INSTALLED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_INSTALLED";

    @NonNull
    public static Intent getBatchInstallInstance(@NonNull Context context, @NonNull ArrayList<Uri> uris) {
        if (uris.isEmpty()) {
            throw new IllegalArgumentException("At least one APK URI is required.");
        }
        Intent intent = new Intent(context, PackageInstallerActivity.class)
                .setAction(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .putExtra(EXTRA_BATCH_INSTALL, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clipData = ClipData.newRawUri("", uris.get(0));
        for (int i = 1; i < uris.size(); ++i) {
            clipData.addItem(new ClipData.Item(uris.get(i)));
        }
        intent.setClipData(clipData);
        return intent;
    }

    private int mSessionId = -1;
    @Nullable
    private ApkQueueItem mCurrentItem;
    private String mPackageName;
    /**
     * Whether this activity is currently dealing with an apk
     */
    private boolean mIsDealingWithApk = false;
    @UserIdInt
    private int mLastUserId;
    private InstallerDialogHelper mDialogHelper;
    private PackageInstallerViewModel mModel;
    @Nullable
    private PackageInstallerService mService;
    private InstallerDialogFragment mInstallerDialogFragment;
    private boolean initiated = false;
    private boolean mBatchInstall;
    @NonNull
    private List<InstallDependencyChecker.Issue> mPendingDependencyIssues = Collections.emptyList();
    private boolean mDeveloperVerificationWarningShown;
    private final View.OnClickListener mAppInfoClickListener = v -> {
        assert mCurrentItem != null;
        try {
            ApkSource apkSource = mCurrentItem.getApkSource();
            if (apkSource == null) {
                apkSource = mModel.getApkSource();
            }
            Intent appDetailsIntent = AppDetailsActivity.getIntent(this, apkSource, true);
            appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(appDetailsIntent);
        } finally {
            // We cannot trigger cancel here because the cached file will be deleted
            goToNext();
        }
    };
    private final InstallerOptions mInstallerOptions = InstallerOptions.getDefault();
    private final Queue<ApkQueueItem> mApkQueue = new LinkedList<>();
    private final ActivityResultLauncher<Intent> mConfirmIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // User did some interaction and the installer screen is closed now
                Intent broadcastIntent = new Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_END);
                broadcastIntent.setPackage(getPackageName());
                broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, mSessionId);
                getApplicationContext().sendBroadcast(broadcastIntent);
                if (!hasNext() && !mIsDealingWithApk) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            });

    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private final StoragePermission mStoragePermission = StoragePermission.init(this);
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((ForegroundService.Binder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        final Intent intent = getIntent();
        if (intent == null) {
            triggerCancel();
            return;
        }
        Log.d(TAG, "On create, intent: %s", intent);
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            onNewIntent(intent);
            return;
        }
        mBatchInstall = intent.getBooleanExtra(EXTRA_BATCH_INSTALL, false);
        mModel = new ViewModelProvider(this).get(PackageInstallerViewModel.class);
        if (!bindService(
                new Intent(this, PackageInstallerService.class), mServiceConnection, BIND_AUTO_CREATE)) {
            throw new RuntimeException("Unable to bind PackageInstallerService");
        }
        synchronized (mApkQueue) {
            mApkQueue.addAll(ApkQueueItem.fromIntent(intent, Utils.getRealReferrer(this)));

        }
        ApkSource apkSource = IntentCompat.getUnwrappedParcelableExtra(intent, EXTRA_APK_FILE_LINK, ApkSource.class);
        if (apkSource != null) {
            synchronized (mApkQueue) {
                mApkQueue.add(ApkQueueItem.fromApkSource(apkSource));
            }
        }
        mModel.packageInfoLiveData().observe(this, newPackageInfo -> {
            if (newPackageInfo == null) {
                if (mBatchInstall) {
                    UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
                    goToNext();
                    return;
                }
                mDialogHelper.showParseFailedDialog(v -> triggerCancel());
                return;
            }
            mPendingDependencyIssues = collectDependencyIssues(newPackageInfo);
            mDialogHelper.onParseSuccess(mModel.getAppLabel(), getVersionInfoWithTrackers(newPackageInfo),
                    mModel.getAppIcon(), v -> displayInstallerOptions((dialog1, which, options) -> {
                        if (options != null) {
                            mInstallerOptions.copy(options);
                        }
                    }));
            if (mBatchInstall) {
                triggerBatchInstall();
                return;
            }
            displayChangesOrInstallationPrompt();
        });
        mModel.packageUninstalledLiveData().observe(this, success -> {
            if (success) {
                install();
            } else {
                // Uninstall failures live on the same dialog but have no PackageInstaller status
                // code; pass STATUS_SUCCESS so the install-specific "Copy diagnostic info" button
                // stays hidden (App Info is also hidden because displayOpenAndAppInfo = false).
                showInstallationFinishedDialog(mModel.getPackageName(), getString(R.string.failed_to_uninstall_app),
                        null, false, STATUS_SUCCESS);
            }
        });
        // Init fragment
        mInstallerDialogFragment = new InstallerDialogFragment();
        mInstallerDialogFragment.setCancelable(false);
        mInstallerDialogFragment.setFragmentStartedCallback(this::init);
        mInstallerDialogFragment.showNow(getSupportFragmentManager(), InstallerDialogFragment.TAG);
    }

    @Override
    protected void onDestroy() {
        if (mService != null) {
            unbindService(mServiceConnection);
        }
        unsetInstallFinishedListener();
        // Delete remaining cached file
        if (mCurrentItem != null && (mCurrentItem.getApkSource() instanceof CachedApkSource)) {
            ((CachedApkSource) mCurrentItem.getApkSource()).cleanup();
        }
        super.onDestroy();
    }

    private void init(@NonNull InstallerDialogFragment fragment, @NonNull AlertDialog dialog) {
        // Make sure that it's only initiated once
        if (initiated) {
            return;
        }
        initiated = true;
        mDialogHelper = new InstallerDialogHelper(fragment, dialog);
        mDialogHelper.initProgress(v -> triggerCancel());
        goToNext();
    }

    @UiThread
    private void displayChangesOrInstallationPrompt() {
        // This dialog either calls triggerInstall() or triggerCancel()
        boolean displayChanges;
        PackageInfo installedPackageInfo = mModel.getInstalledPackageInfo();
        int actionRes;
        if (installedPackageInfo == null) {
            // App not installed or data not cleared
            displayChanges = false;
            actionRes = R.string.install;
        } else {
            // App is installed or the app is uninstalled without clearing data, or the app is uninstalled,
            // but it's a system app
            long installedVersionCode = PackageInfoCompat.getLongVersionCode(installedPackageInfo);
            long thisVersionCode = PackageInfoCompat.getLongVersionCode(mModel.getNewPackageInfo());
            displayChanges = Prefs.Installer.displayChanges();
            if (installedVersionCode < thisVersionCode) {
                // Needs update
                actionRes = R.string.update;
            } else if (installedVersionCode == thisVersionCode) {
                // Issue reinstall
                actionRes = R.string.reinstall;
            } else {
                // Downgrade
                actionRes = R.string.downgrade;
            }
        }
        if (displayChanges) {
            WhatsNewFragment dialogFragment = WhatsNewFragment.getInstance(mModel.getNewPackageInfo(),
                    mModel.getInstalledPackageInfo());
            mDialogHelper.showWhatsNewDialog(actionRes, dialogFragment, new InstallerDialogHelper.OnClickButtonsListener() {
                @Override
                public void triggerInstall() {
                    displayInstallationPrompt(actionRes, true);
                }

                @Override
                public void triggerCancel() {
                    PackageInstallerActivity.this.triggerCancel();
                }
            }, mAppInfoClickListener);
            return;
        }
        displayInstallationPrompt(actionRes, false);
    }

    private void displayInstallationPrompt(int actionRes, boolean splitOnly) {
        if (mModel.getApkFile().isSplit()) {
            SplitApkChooser fragment = SplitApkChooser.getNewInstance(getVersionInfoWithTrackers(
                    mModel.getNewPackageInfo()), getString(actionRes));
            mDialogHelper.showApkChooserDialog(actionRes, fragment, this, mAppInfoClickListener);
            return;
        }
        if (!splitOnly) {
            // In unprivileged mode, a dialog is generated by the system. But we need to display it nonetheless in order
            // to provide additional features.
            int trackers = mModel.getTrackerCount();
            CharSequence callout = composeInstallCallout(trackers, mPendingDependencyIssues);
            mDialogHelper.showInstallConfirmationDialog(actionRes, this, mAppInfoClickListener, callout);
        } else triggerInstall();
    }

    private void displayInstallerOptions(InstallerOptionsFragment.OnClickListener clickListener) {
        PackageInfo packageInfo = mModel.getNewPackageInfo();
        InstallerOptionsFragment dialog = InstallerOptionsFragment.getInstance(packageInfo.packageName,
                ApplicationInfoCompat.isTestOnly(packageInfo.applicationInfo), mInstallerOptions, clickListener);
        dialog.show(getSupportFragmentManager(), InstallerOptionsFragment.TAG);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.clear();
        super.onSaveInstanceState(outState);
    }

    @UiThread
    private void install() {
        if (mModel.getApkFile().hasObb() && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            // Need to request permissions if not given
            mStoragePermission.request(granted -> {
                if (granted) requestInstallerNotificationsAndLaunch();
            });
        } else requestInstallerNotificationsAndLaunch();
    }

    @UiThread
    private void requestInstallerNotificationsAndLaunch() {
        if (!Utils.canDisplayNotification(this)) {
            launchInstallerService();
            return;
        }
        NotificationUtils.requestPostNotificationsForWorkflow(this,
                R.string.installer_notification_permission_title,
                R.string.installer_notification_permission_message,
                this::launchInstallerService);
    }

    @UiThread
    private void launchInstallerService() {
        assert mCurrentItem != null;
        int userId = mInstallerOptions.getUserId();
        mCurrentItem.setInstallerOptions(mInstallerOptions);
        mCurrentItem.setSelectedSplits(mModel.getSelectedSplitsForInstallation());
        mLastUserId = userId == UserHandleHidden.USER_ALL ? UserHandleHidden.myUserId() : userId;
        boolean canDisplayNotification = Utils.canDisplayNotification(this);
        boolean alwaysOnBackground = canDisplayNotification && Prefs.Installer.installInBackground();
        Intent intent = new Intent(this, PackageInstallerService.class);
        IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, mCurrentItem);
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            // For unprivileged mode, use accessibility service if enabled
            mMultiplexer.enableInstall(true);
        }
        ContextCompat.startForegroundService(this, intent);
        if (!alwaysOnBackground && mService != null) {
            setInstallFinishedListener();
            mDialogHelper.showInstallProgressDialog(canDisplayNotification ? v -> {
                unsetInstallFinishedListener();
                goToNext();
            } : null);
        } else {
            unsetInstallFinishedListener();
            // For some reason, the service is empty
            // Install next app instead
            goToNext();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "New intent called: %s", intent);
        setIntent(intent);
        // Check for action first
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            mSessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            mPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
            Intent confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
            String sessionSha256 = intent.getStringExtra(PackageInstallerBroadcastReceiver.EXTRA_SESSION_SHA256);
            try {
                if (mPackageName == null || confirmIntent == null) throw new Exception("Empty confirmation intent.");
                Log.d(TAG, "Requesting user confirmation for package %s", mPackageName);
                if (sessionSha256 != null) {
                    showInstallChecksumDialog(confirmIntent, sessionSha256);
                } else {
                    launchInstallConfirmation(confirmIntent);
                }
            } catch (Exception e) {
                e.printStackTrace();
                String packageName = mPackageName != null ? mPackageName : "";
                PackageInstallerCompat.sendCompletedBroadcast(this, packageName,
                        PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM, mSessionId);
                if (!hasNext() && !mIsDealingWithApk) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            }
            return;
        }
        // New APK files added
        mBatchInstall = mBatchInstall || intent.getBooleanExtra(EXTRA_BATCH_INSTALL, false);
        synchronized (mApkQueue) {
            mApkQueue.addAll(ApkQueueItem.fromIntent(intent, Utils.getRealReferrer(this)));
        }
        UIUtils.displayShortToast(R.string.added_to_queue);
    }

    private void showInstallChecksumDialog(@NonNull Intent confirmIntent, @NonNull String sessionSha256) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.installer_session_sha256_title)
                .setMessage(getString(R.string.installer_session_sha256_message,
                        formatSha256ForDisplay(sessionSha256)))
                .setCancelable(false)
                .setPositiveButton(R.string.action_continue, (dialog, which) -> launchInstallConfirmation(confirmIntent))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    String packageName = mPackageName != null ? mPackageName : "";
                    PackageInstallerCompat.sendCompletedBroadcast(this, packageName,
                            PackageInstallerCompat.STATUS_FAILURE_ABORTED, mSessionId);
                    if (!hasNext() && !mIsDealingWithApk) {
                        finish();
                    }
                })
                .show();
    }

    private void launchInstallConfirmation(@NonNull Intent confirmIntent) {
        mConfirmIntentLauncher.launch(confirmIntent);
    }

    @NonNull
    static String formatSha256ForDisplay(@NonNull String sha256) {
        String compact = sha256.replace(" ", "");
        StringBuilder out = new StringBuilder(compact.length() + compact.length() / 8);
        for (int i = 0; i < compact.length(); ++i) {
            if (i > 0 && i % 8 == 0) {
                out.append(' ');
            }
            out.append(compact.charAt(i));
        }
        return out.toString();
    }

    @UiThread
    @Override
    public void triggerInstall() {
        if (!mDeveloperVerificationWarningShown
                && DeveloperVerificationCompat.isVerifierServiceAvailable(this)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.developer_verification)
                    .setMessage(R.string.installer_developer_verification_warning)
                    .setCancelable(false)
                    .setNegativeButton(R.string.cancel, (dialog, which) -> triggerCancel())
                    .setPositiveButton(R.string.action_continue, (dialog, which) -> {
                        mDeveloperVerificationWarningShown = true;
                        triggerInstall();
                    })
                    .show();
            return;
        }
        // Calls install(), reinstall() (which in terms called install()) and triggerCancel()
        if (mModel.getInstalledPackageInfo() == null) {
            // App not installed
            install();
            return;
        }
        InstallerDialogHelper.OnClickButtonsListener reinstallListener = new InstallerDialogHelper.OnClickButtonsListener() {
            @Override
            public void triggerInstall() {
                // Uninstall and then install again
                reinstall();
            }

            @Override
            public void triggerCancel() {
                PackageInstallerActivity.this.triggerCancel();
            }
        };
        long installedVersionCode = PackageInfoCompat.getLongVersionCode(mModel.getInstalledPackageInfo());
        long thisVersionCode = PackageInfoCompat.getLongVersionCode(mModel.getNewPackageInfo());
        if (installedVersionCode > thisVersionCode && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            // Need to uninstall and install again
            SpannableStringBuilder builder = new SpannableStringBuilder()
                    .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                    .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                    .append("\n\n");
            mDialogHelper.showDowngradeReinstallWarning(builder, reinstallListener, mAppInfoClickListener);
            return;
        }
        if (!mModel.isSignatureDifferent()) {
            // Signature is either matched or the app isn't installed
            install();
            return;
        }
        // Signature is different
        ApplicationInfo info = mModel.getInstalledPackageInfo().applicationInfo;  // Installed package info is never null here.
        boolean isSystem = ApplicationInfoCompat.isSystemApp(info);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (isSystem) {
            // Cannot reinstall a system app with a different signature
            builder.append(getString(R.string.app_signing_signature_mismatch_for_system_apps));
        } else {
            // Offer user to uninstall and then install the app again
            builder.append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                    .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)));
        }
        builder.append("\n\n");
        int start = builder.length();
        builder.append(getText(R.string.app_signing_install_without_data_loss));
        builder.setSpan(new RelativeSizeSpan(0.8f), start, builder.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        mDialogHelper.showSignatureMismatchReinstallWarning(builder, reinstallListener, v -> install(), isSystem);
    }

    private void triggerBatchInstall() {
        try {
            mModel.selectDefaultSplitsForInstallation();
            mDeveloperVerificationWarningShown = true;
            triggerInstall();
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not prepare batch install item.", e);
            UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
            goToNext();
        }
    }

    @Override
    public void triggerCancel() {
        // Run cleanup
        if (mCurrentItem != null && mCurrentItem.getApkSource() instanceof CachedApkSource) {
            ((CachedApkSource) mCurrentItem.getApkSource()).cleanup();
        }
        goToNext();
    }

    private void reinstall() {
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            mMultiplexer.enableUninstall(true);
        }
        mModel.uninstallPackage();
    }

    /**
     * Closes the current APK and start the next
     */
    private void goToNext() {
        mCurrentItem = null;
        mMultiplexer.enableInstall(false);
        mMultiplexer.enableUninstall(false);
        if (hasNext()) {
            mIsDealingWithApk = true;
            mDeveloperVerificationWarningShown = false;
            mDialogHelper.initProgress(v -> goToNext());
            synchronized (mApkQueue) {
                mCurrentItem = Objects.requireNonNull(mApkQueue.poll());
                mModel.getPackageInfo(mCurrentItem);
            }
        } else {
            mIsDealingWithApk = false;
            mDialogHelper.dismiss();
            finish();
        }
    }

    private boolean hasNext() {
        synchronized (mApkQueue) {
            return !mApkQueue.isEmpty();
        }
    }

    @NonNull
    private String getVersionInfoWithTrackers(@NonNull final PackageInfo newPackageInfo) {
        Resources res = getApplication().getResources();
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo);
        String newVersionName = newPackageInfo.versionName;
        int trackers = mModel.getTrackerCount();
        StringBuilder sb = new StringBuilder(res.getString(R.string.version_name_with_code, newVersionName, newVersionCode));
        if (trackers > 0) {
            sb.append(", ").append(res.getQuantityString(R.plurals.no_of_trackers, trackers, trackers));
        }
        return sb.toString();
    }

    public void showInstallationFinishedDialog(String packageName, int result, @Nullable String blockingPackage,
                                               @Nullable String statusMessage) {
        showInstallationFinishedDialog(packageName, getStringFromStatus(result, blockingPackage), statusMessage,
                result == STATUS_SUCCESS, result);
    }

    public void showInstallationFinishedDialog(String packageName, CharSequence message,
                                               @Nullable String statusMessage, boolean displayOpenAndAppInfo,
                                               int statusCode) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(message);
        if (statusMessage != null) {
            ssb.append("\n\n").append(UIUtils.getItalicString(statusMessage));
        }
        Intent intent = PackageManagerCompat.getLaunchIntentForPackage(packageName, UserHandleHidden.myUserId());
        boolean isFailure = statusCode != STATUS_SUCCESS;
        View.OnClickListener neutralListener;
        int neutralLabelRes;
        if (displayOpenAndAppInfo) {
            // Success: keep the historical "App info" affordance.
            neutralListener = v -> {
                try {
                    Intent appDetailsIntent = AppDetailsActivity.getIntent(this, packageName, mLastUserId, true);
                    appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(appDetailsIntent);
                } finally {
                    goToNext();
                }
            };
            neutralLabelRes = R.string.app_info;
        } else if (isFailure) {
            // Failure: surface a copyable install transcript so users can paste the relevant
            // diagnostic context into a support request without re-typing the install path.
            InstallTranscript transcript = buildInstallTranscript(packageName, statusCode, statusMessage);
            neutralListener = v -> {
                ClipboardUtils.copyToClipboard(this, getString(R.string.installer_copy_diagnostic_info),
                        transcript.toShareableText());
                UIUtils.displayShortToast(R.string.installer_diagnostic_info_copied);
            };
            neutralLabelRes = R.string.installer_copy_diagnostic_info;
        } else {
            neutralListener = null;
            neutralLabelRes = 0;
        }
        mDialogHelper.showInstallFinishedDialog(ssb, hasNext() ? R.string.next : R.string.close, v -> goToNext(),
                displayOpenAndAppInfo && intent != null ? v -> {
                    try {
                        startActivity(intent);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast(th.getMessage());
                    } finally {
                        goToNext();
                    }
                } : null, neutralListener, neutralLabelRes);
    }

    @NonNull
    private List<InstallDependencyChecker.Issue> collectDependencyIssues(@NonNull PackageInfo newPackageInfo) {
        ApplicationInfo applicationInfo = newPackageInfo.applicationInfo;
        int apkMinSdk = 0;
        if (applicationInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkMinSdk = applicationInfo.minSdkVersion;
        }
        List<String> requiredLibraries = null;
        if (newPackageInfo.reqFeatures != null || newPackageInfo.applicationInfo != null) {
            // <uses-library> declarations from the APK manifest; only available when the apk
            // was parsed with PackageManager.GET_SHARED_LIBRARY_FILES (or via NG's ApkFile).
            if (newPackageInfo.applicationInfo != null
                    && newPackageInfo.applicationInfo.sharedLibraryFiles != null) {
                requiredLibraries = new ArrayList<>(java.util.Arrays.asList(
                        newPackageInfo.applicationInfo.sharedLibraryFiles));
            }
        }
        List<String> installedLibraries = null;
        try {
            String[] systemLibs = getPackageManager().getSystemSharedLibraryNames();
            if (systemLibs != null) {
                installedLibraries = new ArrayList<>(java.util.Arrays.asList(systemLibs));
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not fetch system shared library names.", th);
        }
        return InstallDependencyChecker.check(apkMinSdk, Build.VERSION.SDK_INT,
                requiredLibraries, installedLibraries);
    }

    @Nullable
    private CharSequence composeInstallCallout(int trackers,
                                               @NonNull List<InstallDependencyChecker.Issue> issues) {
        List<CharSequence> lines = new ArrayList<>(issues.size() + 2);
        if (DeveloperVerificationCompat.isVerifierServiceAvailable(this)) {
            lines.add(getText(R.string.installer_developer_verification_warning));
            mDeveloperVerificationWarningShown = true;
        }
        if (trackers > 0) {
            lines.add(getResources().getQuantityString(
                    R.plurals.installer_tracker_callout, trackers, trackers));
        }
        for (InstallDependencyChecker.Issue issue : issues) {
            CharSequence line = formatDependencyIssue(issue);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines.get(i));
        }
        return out;
    }

    @Nullable
    private CharSequence formatDependencyIssue(@NonNull InstallDependencyChecker.Issue issue) {
        switch (issue.kind) {
            case MIN_SDK_TOO_HIGH:
                return getString(R.string.installer_dependency_min_sdk_too_high,
                        issue.requiredVersion, issue.actualVersion);
            case MISSING_SHARED_LIBRARY:
                return getString(R.string.installer_dependency_missing_shared_library,
                        InstallDependencyChecker.joinMissingNames(issue.missingNames));
            default:
                return null;
        }
    }

    @NonNull
    private InstallTranscript buildInstallTranscript(@NonNull String packageName, int statusCode,
                                                     @Nullable String statusMessage) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = fmt.format(new Date());
        String version = String.format(Locale.ROOT, "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        String device = String.format(Locale.ROOT, "%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.DEVICE);
        String patch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Build.VERSION.SECURITY_PATCH : "";
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        String mode = Ops.getMode();
        String sourceUri = null;
        ApkQueueItem currentItem = mCurrentItem;
        if (currentItem != null && currentItem.getApkSource() != null) {
            Uri rawUri = currentItem.getApkSource().getUri();
            if (rawUri != null) {
                sourceUri = rawUri.toString();
            }
        }
        return new InstallTranscript(timestamp, version, device, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                patch, abi, mode, packageName, statusCode, InstallTranscript.statusName(statusCode), statusMessage,
                sourceUri, true);
    }

    @NonNull
    private String getStringFromStatus(@PackageInstallerCompat.Status int status,
                                       @Nullable String blockingPackage) {
        switch (status) {
            case STATUS_SUCCESS:
                return getString(R.string.installer_app_installed);
            case STATUS_FAILURE_ABORTED:
                return getString(R.string.installer_error_aborted);
            case STATUS_FAILURE_BLOCKED:
                String blocker = getString(R.string.installer_error_blocked_device);
                if (blockingPackage != null) {
                    blocker = PackageUtils.getPackageLabel(getPackageManager(), blockingPackage);
                }
                return getString(R.string.installer_error_blocked, blocker);
            case STATUS_FAILURE_CONFLICT:
                return getString(R.string.installer_error_conflict);
            case STATUS_FAILURE_INCOMPATIBLE:
                return getString(R.string.installer_error_incompatible);
            case STATUS_FAILURE_INVALID:
                return getString(R.string.installer_error_bad_apks);
            case STATUS_FAILURE_STORAGE:
                return getString(R.string.installer_error_storage);
            case STATUS_FAILURE_SECURITY:
                return getString(R.string.installer_error_security);
            case STATUS_FAILURE_SESSION_CREATE:
                return getString(R.string.installer_error_session_create);
            case STATUS_FAILURE_SESSION_WRITE:
                return getString(R.string.installer_error_session_write);
            case STATUS_FAILURE_SESSION_COMMIT:
                return getString(R.string.installer_error_session_commit);
            case STATUS_FAILURE_SESSION_ABANDON:
                return getString(R.string.installer_error_session_abandon);
            case STATUS_FAILURE_INCOMPATIBLE_ROM:
                return getString(R.string.installer_error_lidl_rom);
        }
        return getString(R.string.installer_error_generic);
    }

    public void setInstallFinishedListener() {
        if (mService != null) {
            mService.setOnInstallFinished((packageName, status, blockingPackage, statusMessage) -> {
                if (isFinishing()) return;
                showInstallationFinishedDialog(packageName, status, blockingPackage, statusMessage);
            });
        }
    }

    public void unsetInstallFinishedListener() {
        if (mService != null) {
            mService.setOnInstallFinished(null);
        }
    }
}
