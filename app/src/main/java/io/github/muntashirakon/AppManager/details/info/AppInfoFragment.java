// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_BLACK;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DEFAULT;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DISABLED;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_ENABLED;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_JUST_WARN;
import static io.github.muntashirakon.AppManager.compat.ManifestCompat.permission.TERMUX_RUN_COMMAND;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayShortToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;
import static io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.system.OsConstants;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeShortcutInfo;
import io.github.muntashirakon.AppManager.apk.installer.AppArchiveManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.AppLocaleManagerCompat;
import io.github.muntashirakon.AppManager.compat.DeveloperVerificationCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.DomainVerificationManagerCompat;
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageInfoCompat2;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.SensorServiceCompat;
import io.github.muntashirakon.AppManager.crypto.auth.ActionAuthGate;
import io.github.muntashirakon.AppManager.debloat.BloatwareDetailsDialog;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.details.manifest.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.details.profile.AppProfileCapture;
import io.github.muntashirakon.AppManager.details.profile.CpuProfileCommandBuilder;
import io.github.muntashirakon.AppManager.details.profile.PerfettoCommandBuilder;
import io.github.muntashirakon.AppManager.details.profile.PerfettoConfigInspector;
import io.github.muntashirakon.AppManager.details.profile.PerfettoTraceConfigBuilder;
import io.github.muntashirakon.AppManager.details.profile.ProfileCaptureOptionCatalog;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.PerAppRollbackManager;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.tags.AppTagStore;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.shortcut.AppActionShortcutInfo;
import io.github.muntashirakon.AppManager.shortcut.ForceStopTileController;
import io.github.muntashirakon.AppManager.shortcut.ForceStopTileService;
import io.github.muntashirakon.AppManager.sharedpref.SharedPrefsActivity;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.ssaid.ChangeSsaidDialog;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.uri.GrantUriUtils;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.IntentUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppInfoFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, MenuProvider {
    public static final String TAG = "AppInfoFragment";

    private static final String PACKAGE_NAME_AURORA_STORE = "com.aurora.store";
    private static final String ACTION_MANAGE_HEALTH_PERMISSIONS =
            "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS";
    private static final String ACTION_CREDENTIAL_PROVIDER_SETTINGS =
            "android.settings.CREDENTIAL_PROVIDER";

    private PackageManager mPackageManager;
    private String mPackageName;
    private int mUserId;
    @Nullable
    private String mInstallerPackageName;
    private PackageInfo mPackageInfo;
    @Nullable
    private PackageInfo mInstalledPackageInfo;
    private AppDetailsActivity mActivity;
    private ApplicationInfo mApplicationInfo;
    private ViewGroup mHorizontalLayout;
    private ViewGroup mTagCloud;
    private SwipeRefreshLayout mSwipeRefresh;
    private CharSequence mAppLabel;
    private LinearProgressIndicator mProgressIndicator;
    private AppDetailsViewModel mMainModel;
    private AppInfoViewModel mAppInfoModel;
    private AppInfoRecyclerAdapter mAdapter;
    // Headers
    private TextView mLabelView;
    private TextView mPackageNameView;
    private TextView mVersionView;
    private ImageView mIconView;
    private MaterialCardView mTrackerCtaCard;
    private TextView mTrackerCtaTitle;
    private TextView mTrackerCtaSubtitle;
    private MaterialButton mTrackerCtaAction;
    private MaterialCardView mPermsCtaCard;
    private TextView mPermsCtaTitle;
    private TextView mPermsCtaSubtitle;
    private MaterialButton mPermsCtaAction;
    private List<MagiskProcess> mMagiskHiddenProcesses;
    private List<MagiskProcess> mMagiskDeniedProcesses;
    private Future<?> mTagCloudFuture;
    private Future<?> mActionsFuture;
    private Future<?> mListFuture;
    private Future<?> mMenuPreparationResult;

    private boolean mIsExternalApk;
    private boolean mIsDataOnlyPackage;
    private int mLoadedItemCount;

    @GuardedBy("mListItems")
    private final List<ListItem> mListItems = new ArrayList<>();
    private final BetterActivityResult<String, Uri> mExport = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.CreateDocument("*/*"));
    private final BetterActivityResult<String, Boolean> mRequestPerm = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.RequestPermission());
    private final BetterActivityResult<Intent, ActivityResult> mActivityLauncher = BetterActivityResult
            .registerActivityForResult(this);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppInfoModel = new ViewModelProvider(this).get(AppInfoViewModel.class);
        mMainModel = new ViewModelProvider(requireActivity()).get(AppDetailsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActivity = (AppDetailsActivity) requireActivity();
        mAppInfoModel.setMainModel(mMainModel);
        mPackageManager = mActivity.getPackageManager();
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        // Recycler view
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        // Horizontal view
        mHorizontalLayout = view.findViewById(R.id.horizontal_layout);
        // Progress indicator
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgressIndicator(true);
        // Header
        mTagCloud = view.findViewById(R.id.tag_cloud);
        mLabelView = view.findViewById(R.id.label);
        mPackageNameView = view.findViewById(R.id.packageName);
        mIconView = view.findViewById(R.id.icon);
        mVersionView = view.findViewById(R.id.version);
        mTrackerCtaCard = view.findViewById(R.id.tracker_cta_card);
        mTrackerCtaTitle = view.findViewById(R.id.tracker_cta_title);
        mTrackerCtaSubtitle = view.findViewById(R.id.tracker_cta_subtitle);
        mTrackerCtaAction = view.findViewById(R.id.tracker_cta_action);
        mPermsCtaCard = view.findViewById(R.id.perms_cta_card);
        mPermsCtaTitle = view.findViewById(R.id.perms_cta_title);
        mPermsCtaSubtitle = view.findViewById(R.id.perms_cta_subtitle);
        mPermsCtaAction = view.findViewById(R.id.perms_cta_action);
        mAdapter = new AppInfoRecyclerAdapter(requireContext());
        recyclerView.setAdapter(mAdapter);
        mActivity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        // Set observer
        mMainModel.get(AppDetailsFragment.APP_INFO).observe(getViewLifecycleOwner(), appDetailsItems -> {
            mLoadedItemCount = 0;
            if (appDetailsItems == null || appDetailsItems.isEmpty() || !mMainModel.isPackageExist()) {
                showProgressIndicator(false);
                return;
            }
            ++mLoadedItemCount;
            AppDetailsItem<?> appDetailsItem = appDetailsItems.get(0);
            mPackageInfo = (PackageInfo) appDetailsItem.item;
            mApplicationInfo = mPackageInfo.applicationInfo;
            mPackageName = appDetailsItem.name;
            mUserId = mMainModel.getUserId();
            mInstalledPackageInfo = mMainModel.getInstalledPackageInfo();
            mIsExternalApk = mMainModel.isExternalApk();
            mIsDataOnlyPackage = mMainModel.isDataOnlyPackage();
            if (!mIsExternalApk) {
                mInstallerPackageName = PackageManagerCompat.getInstallerPackageName(mPackageName, mUserId);
            }
            // Set icon
            ImageLoader.getInstance().displayImage(mPackageName, mApplicationInfo, mIconView);
            // Set package name
            mPackageNameView.setText(mPackageName);
            mPackageNameView.setOnClickListener(v ->
                    Utils.copyToClipboard(ContextUtils.getContext(), "Package name", mPackageName));
            // Set App Version
            CharSequence version = getString(R.string.version_name_with_code, mPackageInfo.versionName, PackageInfoCompat.getLongVersionCode(mPackageInfo));
            mVersionView.setText(version);
            // Load app label
            mAppInfoModel.loadAppLabel(mApplicationInfo);
            // Load tag cloud
            mAppInfoModel.loadTagCloud(mPackageInfo, mIsExternalApk);
            // Load horizontal actions
            setupHorizontalActions();
            // Load other info
            mAppInfoModel.loadAppInfo(mPackageInfo, mIsExternalApk);
        });
        mAppInfoModel.getAppLabel().observe(getViewLifecycleOwner(), appLabel -> {
            ++mLoadedItemCount;
            if (mLoadedItemCount >= 4) {
                showProgressIndicator(false);
            }
            mAppLabel = appLabel;
            // Set Application Name, aka Label
            mLabelView.setText(mAppLabel);
        });
        mMainModel.getFreezeTypeLiveData().observe(getViewLifecycleOwner(), freezeType -> {
            int freezeTypeN = Optional.ofNullable(freezeType)
                    .orElse(Prefs.Blocking.getDefaultFreezingMethod());
            showFreezeDialog(freezeTypeN, freezeType != null);
        });
        mIconView.setOnClickListener(v -> {
            ThreadUtils.postOnBackgroundThread(() -> {
                String data = ClipboardUtils.readHashValueFromClipboard(ContextUtils.getContext());
                if (data != null) {
                    SignerInfo signerInfo = PackageUtils.getSignerInfo(mPackageInfo, mIsExternalApk);
                    if (signerInfo != null) {
                        X509Certificate[] certs = signerInfo.getCurrentSignerCerts();
                        if (certs != null && certs.length == 1) {
                            try {
                                Pair<String, String>[] digests = DigestUtils.getDigests(certs[0].getEncoded());
                                for (Pair<String, String> digest : digests) {
                                    if (digest.second.equals(data)) {
                                        if (digest.first.equals(DigestUtils.MD5) || digest.first.equals(DigestUtils.SHA_1)) {
                                            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.verified_using_unreliable_hash));
                                        } else
                                            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.verified));
                                        return;
                                    }
                                }
                            } catch (CertificateEncodingException ignore) {
                            }
                        }
                    }
                    ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.not_verified));
                }
            });
        });
        mAppInfoModel.getTagCloud().observe(getViewLifecycleOwner(), this::setupTagCloud);
        mAppInfoModel.getAppInfo().observe(getViewLifecycleOwner(), this::setupVerticalView);
        mAppInfoModel.getInstallExistingResult().observe(getViewLifecycleOwner(), statusMessagePair ->
                new MaterialAlertDialogBuilder(requireActivity())
                        .setTitle(mAppLabel)
                        .setIcon(mApplicationInfo.loadIcon(mPackageManager))
                        .setMessage(statusMessagePair.second)
                        .setNegativeButton(R.string.close, null)
                        .show());
        mMainModel.getTagsAlteredLiveData().observe(getViewLifecycleOwner(), altered -> {
            // Reload tag cloud
            mAppInfoModel.loadTagCloud(mPackageInfo, mIsExternalApk);
        });
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mMainModel != null && !mMainModel.isExternalApk()) {
            inflater.inflate(R.menu.fragment_app_info_actions, menu);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        if (mIsExternalApk) return;
        MenuItem magiskHideMenu = menu.findItem(R.id.action_magisk_hide);
        MenuItem magiskDenyListMenu = menu.findItem(R.id.action_magisk_denylist);
        MenuItem openInTermuxMenu = menu.findItem(R.id.action_open_in_termux);
        MenuItem runInTermuxMenu = menu.findItem(R.id.action_run_in_termux);
        MenuItem batteryOptMenu = menu.findItem(R.id.action_battery_opt);
        MenuItem sensorsMenu = menu.findItem(R.id.action_sensor);
        MenuItem netPolicyMenu = menu.findItem(R.id.action_net_policy);
        MenuItem installMenu = menu.findItem(R.id.action_install);
        MenuItem optimizeMenu = menu.findItem(R.id.action_optimize);
        mMenuPreparationResult = ThreadUtils.postOnBackgroundThread(() -> {
            boolean magiskHideAvailable = MagiskHide.available();
            boolean magiskDenyListAvailable = MagiskDenyList.available();
            boolean rootAvailable = RunnerUtils.isRootAvailable();
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            ThreadUtils.postOnMainThread(() -> {
                if (magiskHideMenu != null) {
                    magiskHideMenu.setVisible(magiskHideAvailable);
                }
                if (magiskDenyListMenu != null) {
                    magiskDenyListMenu.setVisible(magiskDenyListAvailable);
                }
                if (openInTermuxMenu != null) {
                    openInTermuxMenu.setVisible(rootAvailable);
                }
            });
        });
        boolean isDebuggable;
        if (mApplicationInfo != null) {
            isDebuggable = (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } else isDebuggable = false;
        if (runInTermuxMenu != null) {
            runInTermuxMenu.setVisible(isDebuggable);
        }
        if (batteryOptMenu != null) {
            batteryOptMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        }
        if (sensorsMenu != null) {
            sensorsMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS));
        }
        if (netPolicyMenu != null) {
            netPolicyMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        }
        if (installMenu != null) {
            installMenu.setVisible(Users.getUsersIds().length > 1 && SelfPermissions.canInstallExistingPackages());
        }
        if (optimizeMenu != null) {
            optimizeMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && (SelfPermissions.isSystemOrRootOrShell() || BuildConfig.APPLICATION_ID.equals(mInstallerPackageName)));
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh_detail) {
            refreshDetails();
        } else if (itemId == R.id.action_customize_action_rail) {
            showActionRailPreferenceDialog();
        } else if (itemId == R.id.action_share_apk) {
            showProgressIndicator(true);
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Path tmpApkSource = ApkUtils.getSharableApkFile(requireContext(), mPackageInfo);
                    ThreadUtils.postOnMainThread(() -> {
                        showProgressIndicator(false);
                        Context ctx = ContextUtils.getContext();
                        Uri apkUri = FmProvider.getContentUri(tmpApkSource);
                        Intent intent = new Intent(Intent.ACTION_SEND)
                                .setType("application/*")
                                .putExtra(Intent.EXTRA_STREAM, apkUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        // ClipData is required for FLAG_GRANT_READ_URI_PERMISSION to
                        // reach the chooser target on Android 18+ (auto-grant removed
                        // for SEND/SEND_MULTIPLE/IMAGE_CAPTURE).
                        intent.setClipData(ClipData.newRawUri("", apkUri));
                        ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share_apk))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    });
                } catch (Exception e) {
                    Log.e(TAG, e);
                    displayLongToast(R.string.failed_to_extract_apk_file);
                }
            });
        } else if (itemId == R.id.action_backup) {
            if (mMainModel == null) return true;
            BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstanceWithPref(
                    Collections.singletonList(new UserPackagePair(mPackageName, mUserId)), mUserId);
            fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
            fragment.setOnActionCompleteListener((mode, failedPackages) -> {
                showProgressIndicator(false);
                mMainModel.getTagsAlteredLiveData().setValue(true);
            });
            fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
        } else if (itemId == R.id.action_per_app_rollback) {
            showPerAppRollbackConfirmation();
        } else if (itemId == R.id.action_view_settings) {
            try {
                ActivityManagerCompat.startActivity(IntentUtils.getAppDetailsSettings(mPackageName), mUserId);
            } catch (Throwable th) {
                UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
            }
        } else if (itemId == R.id.action_edit_tags) {
            showEditTagsDialog();
        } else if (itemId == R.id.action_memory_snapshot) {
            showMemorySnapshot();
        } else if (itemId == R.id.action_export_trace) {
            showPerfettoTraceCapture();
        } else if (itemId == R.id.action_record_cpu_profile) {
            showCpuProfileCapture();
        } else if (itemId == R.id.action_export_blocking_rules) {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(mActivity, System.currentTimeMillis()) + ".am.tsv";
            mExport.launch(fileName, uri -> {
                if (uri == null || mMainModel == null) {
                    // Back button pressed.
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle exportArgs = new Bundle();
                ArrayList<String> packages = new ArrayList<>();
                packages.add(mPackageName);
                exportArgs.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                exportArgs.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                exportArgs.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, packages);
                exportArgs.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, new int[]{mUserId});
                dialogFragment.setArguments(exportArgs);
                dialogFragment.show(mActivity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });
        } else if (itemId == R.id.action_open_in_termux) {
            if (SelfPermissions.checkSelfPermission(TERMUX_RUN_COMMAND)) {
                openInTermux();
            } else {
                mRequestPerm.launch(TERMUX_RUN_COMMAND, granted -> {
                    if (granted) openInTermux();
                });
            }
        } else if (itemId == R.id.action_run_in_termux) {
            if (SelfPermissions.checkSelfPermission(TERMUX_RUN_COMMAND)) {
                runInTermux();
            } else {
                mRequestPerm.launch(TERMUX_RUN_COMMAND, granted -> {
                    if (granted) runInTermux();
                });
            }
        } else if (itemId == R.id.action_magisk_hide) {
            displayMagiskHideDialog();
        } else if (itemId == R.id.action_magisk_denylist) {
            displayMagiskDenyListDialog();
        } else if (itemId == R.id.action_battery_opt) {
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable, (dialog, which) -> {
                            if (DeviceIdleManagerCompat.enableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done);
                                mMainModel.getTagsAlteredLiveData().setValue(true);
                            } else {
                                UIUtils.displayShortToast(R.string.failed);
                            }
                        })
                        .setNegativeButton(R.string.disable, (dialog, which) -> {
                            if (DeviceIdleManagerCompat.disableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done);
                                mMainModel.getTagsAlteredLiveData().setValue(true);
                            } else {
                                UIUtils.displayShortToast(R.string.failed);
                            }
                        })
                        .show();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    startActivity(IntentUtils.getBatteryOptSettings(mPackageName));
                } catch (Throwable th) {
                    UIUtils.displayShortToast("No DEVICE_POWER permission.");
                }
            }
        } else if (itemId == R.id.action_sensor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sensors)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                            try {
                                SensorServiceCompat.enableSensor(mPackageName, mUserId, true);
                                mMainModel.getTagsAlteredLiveData().postValue(true);
                                ThreadUtils.postOnMainThread(() ->
                                        UIUtils.displayShortToast(R.string.done));
                            } catch (IOException e) {
                                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                                        getString(R.string.failed)
                                                + LangUtils.getSeparatorString()
                                                + e.getMessage()));
                            }
                        }))
                        .setNegativeButton(R.string.disable, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                            try {
                                SensorServiceCompat.enableSensor(mPackageName, mUserId, false);
                                mMainModel.getTagsAlteredLiveData().postValue(true);
                                ThreadUtils.postOnMainThread(() ->
                                        UIUtils.displayShortToast(R.string.done));
                            } catch (IOException e) {
                                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                                        getString(R.string.failed)
                                                + LangUtils.getSeparatorString()
                                                + e.getMessage()));
                            }
                        }))
                        .show();
            } else {
                Log.e(TAG, "No sensor permission.");
            }
        } else if (itemId == R.id.action_net_policy) {
            if (!UserHandleHidden.isApp(mApplicationInfo.uid)) {
                UIUtils.displayLongToast(R.string.netpolicy_cannot_be_modified_for_core_apps);
                return true;
            }
            if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        startActivity(IntentUtils.getNetPolicySettings(mPackageName));
                    } catch (Throwable th) {
                        UIUtils.displayShortToast("No MANAGE_NETWORK_POLICY permission.");
                    }
                }
                return true;
            }
            ArrayMap<Integer, String> netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(ContextUtils.getContext());
            Integer[] polices = new Integer[netPolicyMap.size()];
            CharSequence[] policyStrings = new String[netPolicyMap.size()];
            int selectedPolicies = NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo.uid);
            for (int i = 0; i < netPolicyMap.size(); ++i) {
                polices[i] = netPolicyMap.keyAt(i);
                policyStrings[i] = netPolicyMap.valueAt(i);
            }
            new SearchableFlagsDialogBuilder<>(mActivity, polices, policyStrings, selectedPolicies)
                    .setTitle(R.string.net_policy)
                    .showSelectAll(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, selections) -> {
                        int flags = 0;
                        for (int flag : selections) {
                            flags |= flag;
                        }
                        NetworkPolicyManagerCompat.setUidPolicy(mApplicationInfo.uid, flags);
                        mMainModel.getTagsAlteredLiveData().setValue(true);
                    })
                    .show();
        } else if (itemId == R.id.action_extract_icon) {
            String iconName = mAppLabel + "_icon.png";
            mExport.launch(iconName, uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (OutputStream outputStream = Paths.get(uri).openOutputStream()) {
                        if (outputStream == null) {
                            throw new IOException("Unable to open output stream.");
                        }
                        Bitmap bitmap = getBitmapFromDrawable(mApplicationInfo.loadIcon(mPackageManager));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        outputStream.flush();
                        ThreadUtils.postOnMainThread(() -> displayShortToast(R.string.saved_successfully));
                    } catch (IOException e) {
                        Log.e(TAG, e);
                        ThreadUtils.postOnMainThread(() -> displayShortToast(R.string.saving_failed));
                    }
                });
            });
        } else if (itemId == R.id.action_install) {
            List<UserInfo> users = Users.getUsers();
            CharSequence[] userNames = new String[users.size()];
            int i = 0;
            for (UserInfo info : users) {
                userNames[i++] = info.toLocalizedString(requireContext());
            }
            new SearchableItemsDialogBuilder<>(mActivity, userNames)
                    .setTitle(R.string.select_user)
                    .setOnItemClickListener((dialog, which, item1) -> {
                        mAppInfoModel.installExisting(mPackageName, users.get(which).id);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.action_add_to_profile) {
            AddToProfileDialogFragment dialog = AddToProfileDialogFragment.getInstance(new String[]{mPackageName});
            dialog.show(getChildFragmentManager(), AddToProfileDialogFragment.TAG);
        } else if (itemId == R.id.action_optimize) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && (SelfPermissions.isSystemOrRootOrShell() || BuildConfig.APPLICATION_ID.equals(mInstallerPackageName))) {
                DexOptDialog dialog = DexOptDialog.getInstance(new String[]{mPackageName});
                dialog.show(getChildFragmentManager(), DexOptDialog.TAG);
            } else UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
        } else return false;
        return true;
    }

    // NF-08: manage user-authored tags (AppTagStore) for this package. These
    // are the tags the Finder "Tags" filter (TagsOption) matches against.
    private void showEditTagsDialog() {
        if (mPackageName == null) {
            return;
        }
        AppTagStore store = new AppTagStore(requireContext());
        Set<String> current = store.getTags(mPackageName);
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(store.getAllKnownTags());
        all.addAll(current);
        if (all.isEmpty()) {
            // Nothing to choose from yet — jump straight to creating a tag.
            promptNewTag(store);
            return;
        }
        ArrayList<String> items = new ArrayList<>(all);
        List<CharSequence> itemNames = new ArrayList<>(items);
        new SearchableMultiChoiceDialogBuilder<String>(requireActivity(), items, itemNames)
                .setTitle(R.string.edit_tags)
                .addSelections(new ArrayList<>(current))
                .setPositiveButton(R.string.save, (dialog, which, selectedItems) -> {
                    Set<String> selected = new HashSet<>(selectedItems);
                    for (String tag : items) {
                        boolean want = selected.contains(tag);
                        boolean had = current.contains(tag);
                        if (want && !had) {
                            store.addTag(mPackageName, tag);
                        } else if (!want && had) {
                            store.removeTag(mPackageName, tag);
                        }
                    }
                    if (mMainModel != null) {
                        mMainModel.getTagsAlteredLiveData().setValue(true);
                    }
                })
                .setNeutralButton(R.string.new_tag, (dialog, which, selectedItems) -> promptNewTag(store))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showActionRailPreferenceDialog() {
        String[] actionIds = AppInfoActionOrderResolver.getCustomizableActionIds();
        CharSequence[] actionLabels = new CharSequence[actionIds.length];
        boolean[] prioritized = new boolean[actionIds.length];
        Set<String> prioritySet = new HashSet<>(Prefs.AppDetailsPage.getActionRailPriorityIds());
        for (int i = 0; i < actionIds.length; ++i) {
            actionLabels[i] = getString(AppInfoActionOrderResolver.getLabelRes(actionIds[i]));
            prioritized[i] = prioritySet.contains(actionIds[i]);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_info_action_rail_customize)
                .setMessage(R.string.app_info_action_rail_customize_message)
                .setMultiChoiceItems(actionLabels, prioritized,
                        (dialog, which, isChecked) -> prioritized[which] = isChecked)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    ArrayList<String> selectedPriority = new ArrayList<>();
                    for (int i = 0; i < actionIds.length; ++i) {
                        if (prioritized[i]) {
                            selectedPriority.add(actionIds[i]);
                        }
                    }
                    Prefs.AppDetailsPage.setActionRailPriorityIds(selectedPriority);
                    setupHorizontalActions();
                    displayShortToast(R.string.done);
                })
                .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                    Prefs.AppDetailsPage.setActionRailPriorityIds(Collections.emptyList());
                    setupHorizontalActions();
                    displayShortToast(R.string.done);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptNewTag(@NonNull AppTagStore store) {
        if (mPackageName == null) {
            return;
        }
        new TextInputDialogBuilder(requireActivity(), R.string.tag_name)
                .setTitle(R.string.new_tag)
                .setPositiveButton(R.string.add, (dialog, which, inputText, isChecked) -> {
                    if (inputText == null) return;
                    String tag = inputText.toString().trim();
                    if (!AppTagStore.isValidTag(tag)) {
                        UIUtils.displayShortToast(R.string.invalid_tag);
                        return;
                    }
                    store.addTag(mPackageName, tag);
                    if (mMainModel != null) {
                        mMainModel.getTagsAlteredLiveData().setValue(true);
                    }
                    // Reopen the picker so the new tag can be assigned/reviewed.
                    showEditTagsDialog();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // T20-C: capture and show a point-in-time memory snapshot (dumpsys meminfo
    // + gfxinfo + /proc/<pid>/status + /proc/<pid>/maps) for the target package.
    private void showMemorySnapshot() {
        if (!SelfPermissions.isSystemOrRootOrShell()) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setIcon(R.drawable.ic_information_circle)
                    .setTitle(R.string.root_or_adb_required)
                    .setMessage(R.string.memory_snapshot_permission_required)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        if (mPackageName == null) {
            return;
        }
        final String packageName = mPackageName;
        loadAndShowMemorySnapshot(packageName);
    }

    private void loadAndShowMemorySnapshot(@NonNull String packageName) {
        showProgressIndicator(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            MemorySnapshotComposer.AppMemorySnapshot snapshot = AppMemorySnapshotLoader.load(packageName);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) {
                    return;
                }
                showProgressIndicator(false);
                showMemorySnapshotDialog(packageName, snapshot);
            });
        });
    }

    private void showMemorySnapshotDialog(@NonNull String packageName,
                                          @NonNull MemorySnapshotComposer.AppMemorySnapshot snapshot) {
        new ScrollableDialogBuilder(mActivity)
                .setTitle(R.string.action_memory_snapshot)
                .setMessage(AppMemorySnapshotLoader.format(mActivity, snapshot))
                .enableAnchors()
                .setNeutralButton(R.string.refresh, (dialog, which, isChecked) ->
                        loadAndShowMemorySnapshot(packageName))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    // T20-A: capture a duration-bounded Perfetto system trace focused on this
    // app, saved to Downloads, with an "Open Perfetto UI" follow-up.
    private void showPerfettoTraceCapture() {
        if (!SelfPermissions.isSystemOrRootOrShell()) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setIcon(R.drawable.ic_information_circle)
                    .setTitle(R.string.root_or_adb_required)
                    .setMessage(R.string.profile_capture_permission_required)
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.open_developer_options, (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Throwable th) {
                            UIUtils.displayShortToast("Error: " + th.getLocalizedMessage());
                        }
                    })
                    .show();
            return;
        }
        if (mPackageName == null) {
            return;
        }
        final String packageName = mPackageName;
        showPerfettoTraceOptions(packageName);
    }

    private void showPerfettoTraceOptions(@NonNull String packageName) {
        List<String> durationLabels = ProfileCaptureOptionCatalog.durationLabels();
        new TextInputDropdownDialogBuilder(mActivity, R.string.profile_capture_duration)
                .setTitle(R.string.action_export_trace)
                .setDropdownItems(durationLabels,
                        ProfileCaptureOptionCatalog.indexOfDuration(
                                (int) (PerfettoTraceConfigBuilder.DEFAULT_DURATION_MS / 1000L)),
                        false)
                .setHelperText(R.string.profile_capture_duration_helper)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, (dialog, which, inputText, isChecked) -> {
                    int durationSeconds = ProfileCaptureOptionCatalog.durationFromLabel(inputText,
                            (int) (PerfettoTraceConfigBuilder.DEFAULT_DURATION_MS / 1000L));
                    confirmPerfettoTraceCapture(packageName, durationSeconds * 1000L);
                })
                .show();
    }

    private void confirmPerfettoTraceCapture(@NonNull String packageName, long durationMillis) {
        // T20-A: preview the exact trace config the capture will use, parsed
        // back from the generated text-proto via PerfettoConfigInspector.
        String configPreview = PerfettoConfigInspector.oneLineSummary(PerfettoConfigInspector.inspect(
                PerfettoTraceConfigBuilder.buildTextProto(packageName, durationMillis)));
        String durationLabel = ProfileCaptureOptionCatalog.formatDurationSeconds((int) (durationMillis / 1000L));
        CharSequence confirmMessage = getString(R.string.perfetto_trace_confirm_with_duration, durationLabel)
                + "\n\n" + getString(R.string.perfetto_trace_preview, configPreview);
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.action_export_trace)
                .setMessage(confirmMessage)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, (d, w) -> {
                    String outputPath = profileOutputPath(packageName, ".perfetto-trace");
                    showProgressIndicator(true);
                    displayShortToast(R.string.profile_capturing);
                    ThreadUtils.postOnBackgroundThread(() -> {
                        AppProfileCapture.Result result = AppProfileCapture.capturePerfettoTrace(
                                packageName, durationMillis, outputPath);
                        ThreadUtils.postOnMainThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            showProgressIndicator(false);
                            if (result.success) {
                                new MaterialAlertDialogBuilder(mActivity)
                                        .setTitle(R.string.profile_capture_result_title)
                                        .setMessage(getString(R.string.perfetto_trace_saved, result.outputPath))
                                        .setNegativeButton(R.string.close, null)
                                        .setPositiveButton(R.string.open_perfetto_ui, (d2, w2) -> openUrl(
                                                PerfettoCommandBuilder.perfettoUiUrl()))
                                        .show();
                            } else {
                                displayLongToast(getString(R.string.profile_capture_failed,
                                        result.error != null ? result.error : "?"));
                            }
                        });
                    });
                })
                .show();
    }

    // T20-B: record a duration-bounded simpleperf CPU profile for this app,
    // saved as raw perf.data in Downloads.
    private void showCpuProfileCapture() {
        if (!SelfPermissions.isSystemOrRootOrShell()) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setIcon(R.drawable.ic_information_circle)
                    .setTitle(R.string.root_or_adb_required)
                    .setMessage(R.string.profile_capture_permission_required)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        if (mPackageName == null) {
            return;
        }
        final String packageName = mPackageName;
        showCpuProfileOptions(packageName);
    }

    private void showCpuProfileOptions(@NonNull String packageName) {
        List<String> durationLabels = ProfileCaptureOptionCatalog.durationLabels();
        List<String> events = ProfileCaptureOptionCatalog.cpuEventsForDevice(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS);
        String defaultEvent = ProfileCaptureOptionCatalog.eventFromLabel(CpuProfileCommandBuilder.DEFAULT_EVENT, events);
        int defaultEventIndex = Math.max(0, events.indexOf(defaultEvent));
        TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(
                mActivity, R.string.profile_capture_duration);
        builder.setTitle(R.string.action_record_cpu_profile)
                .setDropdownItems(durationLabels,
                        ProfileCaptureOptionCatalog.indexOfDuration(CpuProfileCommandBuilder.DEFAULT_DURATION_SECONDS),
                        false)
                .setHelperText(R.string.profile_capture_duration_helper)
                .setAuxiliaryInput(getString(R.string.profile_capture_event),
                        getString(R.string.cpu_profile_event_helper),
                        events.get(defaultEventIndex),
                        events,
                        false)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, (dialog, which, inputText, isChecked) -> {
                    int durationSeconds = ProfileCaptureOptionCatalog.durationFromLabel(inputText,
                            CpuProfileCommandBuilder.DEFAULT_DURATION_SECONDS);
                    String event = ProfileCaptureOptionCatalog.eventFromLabel(builder.getAuxiliaryInput(), events);
                    confirmCpuProfileCapture(packageName, durationSeconds, event);
                })
                .show();
    }

    private void confirmCpuProfileCapture(@NonNull String packageName, int durationSeconds,
                                          @NonNull String event) {
        String durationLabel = ProfileCaptureOptionCatalog.formatDurationSeconds(durationSeconds);
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.action_record_cpu_profile)
                .setMessage(getString(R.string.cpu_profile_confirm_with_options, durationLabel, event))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, (d, w) -> {
                    String outputPath = profileOutputPath(packageName, ".perf.data");
                    showProgressIndicator(true);
                    displayShortToast(R.string.profile_capturing);
                    ThreadUtils.postOnBackgroundThread(() -> {
                        AppProfileCapture.Result result = AppProfileCapture.captureCpuProfile(
                                packageName, durationSeconds, event, outputPath);
                        ThreadUtils.postOnMainThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            showProgressIndicator(false);
                            if (result.success) {
                                new MaterialAlertDialogBuilder(mActivity)
                                        .setTitle(R.string.profile_capture_result_title)
                                        .setMessage(getString(R.string.cpu_profile_saved, result.outputPath))
                                        .setPositiveButton(R.string.close, null)
                                        .show();
                            } else {
                                displayLongToast(getString(R.string.profile_capture_failed,
                                        result.error != null ? result.error : "?"));
                            }
                        });
                    });
                })
                .show();
    }

    // Downloads path with a digits-only timestamp so the privileged runner sees
    // a metacharacter-free, validator-safe output path.
    @NonNull
    private String profileOutputPath(@NonNull String packageName, @NonNull String extension) {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloads, packageName + "-" + System.currentTimeMillis() + extension).getAbsolutePath();
    }

    private void openUrl(@NonNull String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable th) {
            UIUtils.displayShortToast("Error: " + th.getLocalizedMessage());
        }
    }

    private void showPerAppRollbackConfirmation() {
        if (mPackageName == null) {
            return;
        }
        String packageName = mPackageName;
        int userId = mUserId;
        CharSequence label = mAppLabel != null ? mAppLabel : packageName;
        showProgressIndicator(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            PerAppRollbackManager.RollbackPlan plan = PerAppRollbackManager.buildPlan(packageName, userId);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) {
                    return;
                }
                showProgressIndicator(false);
                if (!plan.hasRunnableActions()) {
                    UIUtils.displayLongToast(R.string.per_app_rollback_none);
                    return;
                }
                showPerAppRollbackPreview(plan, label);
            });
        });
    }

    /**
     * EI-09 dry-run preview. Show a multi-choice dialog listing each inverse
     * BatchOp the rollback planner produced so the user can untick rows before
     * committing. The original single-line summary is preserved as the dialog
     * subtitle so the manual-review count remains visible.
     */
    private void showPerAppRollbackPreview(@NonNull PerAppRollbackManager.RollbackPlan plan,
                                            @NonNull CharSequence label) {
        java.util.List<io.github.muntashirakon.AppManager.batchops.BatchQueueItem> items = plan.getQueueItems();
        CharSequence[] rowLabels = new CharSequence[items.size()];
        boolean[] keep = new boolean[items.size()];
        for (int i = 0; i < items.size(); ++i) {
            rowLabels[i] = io.github.muntashirakon.AppManager.batchops.BatchOpsService
                    .getDesiredOpTitle(requireContext(), items.get(i).getOp());
            keep[i] = true;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.per_app_rollback_confirm_title)
                .setMessage(buildPerAppRollbackMessage(plan, label))
                .setMultiChoiceItems(rowLabels, keep, (dialog, which, isChecked) -> keep[which] = isChecked)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.per_app_rollback, (dialog, which) -> startPerAppRollback(plan, keep))
                .show();
    }

    @NonNull
    private String buildPerAppRollbackMessage(@NonNull PerAppRollbackManager.RollbackPlan plan,
                                              @NonNull CharSequence label) {
        String inverseCount = getResources().getQuantityString(
                R.plurals.per_app_rollback_inverse_count,
                plan.getRunnableCount(),
                plan.getRunnableCount());
        String manualSuffix = "";
        if (plan.getManualReviewCount() > 0) {
            String manualCount = getResources().getQuantityString(
                    R.plurals.per_app_rollback_manual_count,
                    plan.getManualReviewCount(),
                    plan.getManualReviewCount());
            manualSuffix = getString(R.string.per_app_rollback_manual_suffix, manualCount);
        }
        return getString(R.string.per_app_rollback_confirm_message, inverseCount, label, manualSuffix);
    }

    private void startPerAppRollback(@NonNull PerAppRollbackManager.RollbackPlan plan) {
        startPerAppRollback(plan, null);
    }

    private void startPerAppRollback(@NonNull PerAppRollbackManager.RollbackPlan plan,
                                      @Nullable boolean[] keep) {
        int queuedCount = PerAppRollbackManager.start(requireContext(), plan, keep);
        if (queuedCount == 0) {
            UIUtils.displayLongToast(R.string.per_app_rollback_none);
            return;
        }
        String queued = getResources().getQuantityString(
                R.plurals.per_app_rollback_inverse_count,
                queuedCount,
                queuedCount);
        UIUtils.displayShortToast(getString(R.string.per_app_rollback_queued, queued));
        if (mMainModel != null) {
            mMainModel.getTagsAlteredLiveData().setValue(true);
        }
    }

    @Override
    public void onMenuClosed(@NonNull Menu menu) {
        if (mMenuPreparationResult != null) {
            mMenuPreparationResult.cancel(true);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        refreshDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onDetach() {
        if (mTagCloudFuture != null) mTagCloudFuture.cancel(true);
        if (mActionsFuture != null) mActionsFuture.cancel(true);
        if (mListFuture != null) mListFuture.cancel(true);
        super.onDetach();
    }

    private void openInTermux() {
        runWithTermux(new String[]{"su", "-", String.valueOf(mApplicationInfo.uid)});
    }

    private void runInTermux() {
        runWithTermux(new String[]{"su", "-c", "run-as", mPackageName});
    }

    private void runWithTermux(String[] command) {
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", Utils.TERMUX_LOGIN_PATH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", command);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        try {
            ActivityCompat.startForegroundService(mActivity, intent);
        } catch (Exception e) {
            UIUtils.displayLongToast("Error: " + e.getMessage());
        }
    }

    private void install() {
        ApkSource apkSource = mMainModel != null ? mMainModel.getApkSource() : null;
        if (apkSource == null) return;
        try {
            startActivity(PackageInstallerActivity.getLaunchableInstance(requireContext(), apkSource));
        } catch (Exception e) {
            UIUtils.displayLongToast("Error: " + e.getMessage());
        }
    }

    @UiThread
    private void refreshDetails() {
        if (mMainModel == null || isDetached()) return;
        showProgressIndicator(true);
        mMainModel.triggerPackageChange();
    }

    @MainThread
    private void setupTagCloud(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        setupTrackerCtaCard(tagCloud);
        setupPermsCtaCard(tagCloud);
        if (mTagCloudFuture != null) mTagCloudFuture.cancel(true);
        mTagCloudFuture = ThreadUtils.postOnBackgroundThread(() -> {
            List<TagItem> tagItems = getTagCloudItems(tagCloud);
            ThreadUtils.postOnMainThread(() -> {
                if (isDetached()) return;
                ++mLoadedItemCount;
                if (mLoadedItemCount >= 4) {
                    showProgressIndicator(false);
                }
                mTagCloud.removeAllViews();
                for (TagItem tagItem : tagItems) {
                    if (isDetached()) return;
                    mTagCloud.addView(tagItem.toChip(mTagCloud.getContext(), mTagCloud));
                }
            });
        });
    }

    /**
     * Render the prominent "🛡 N trackers detected · BLOCK" / "All N trackers blocked · UNBLOCK"
     * call-to-action card above the tag cloud. The existing tag-cloud chip stays for users who
     * want fine-grained selection; this is the one-tap path for the common case of "block
     * everything known to track me."
     *
     * Hidden when:
     *   - the package has no known tracker components,
     *   - the app is an external APK (we can't apply rules to it),
     *   - the user lacks the privilege to modify component states.
     */
    @MainThread
    private void setupTrackerCtaCard(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        if (mTrackerCtaCard == null) return;
        if (mMainModel == null || tagCloud.trackerComponents == null || tagCloud.trackerComponents.isEmpty()
                || mIsExternalApk
                || !SelfPermissions.canModifyAppComponentStates(mUserId, mPackageName, mMainModel.isTestOnlyApp())) {
            mTrackerCtaCard.setVisibility(View.GONE);
            mTrackerCtaCard.setOnClickListener(null);
            return;
        }
        mTrackerCtaCard.setVisibility(View.VISIBLE);
        int count = tagCloud.trackerComponents.size();
        int trackerColor = ColorCodes.getTrackerRiskIndicatorColor(requireContext(), count);
        int blockedColor = ColorCodes.getComponentTrackerBlockedIndicatorColor(requireContext());
        boolean allBlocked = tagCloud.areAllTrackersBlocked;
        if (allBlocked) {
            mTrackerCtaTitle.setText(getResources().getQuantityString(
                    R.plurals.tracker_cta_title_all_blocked, count, count));
            mTrackerCtaTitle.setTextColor(blockedColor);
            mTrackerCtaSubtitle.setText(R.string.tracker_cta_subtitle_all_blocked);
            mTrackerCtaAction.setText(R.string.unblock);
        } else {
            mTrackerCtaTitle.setText(getResources().getQuantityString(
                    R.plurals.tracker_cta_title_unblocked, count, count));
            mTrackerCtaTitle.setTextColor(trackerColor);
            String breakdown = buildTrackerCategoryBreakdown(tagCloud);
            mTrackerCtaSubtitle.setText(breakdown != null
                    ? breakdown
                    : getString(R.string.tracker_cta_subtitle_unblocked));
            mTrackerCtaAction.setText(R.string.block);
        }
        View.OnClickListener clickHandler = v -> {
            if (mMainModel == null || isDetached()) return;
            showProgressIndicator(true);
            ThreadUtils.postOnBackgroundThread(() -> {
                if (allBlocked) {
                    mMainModel.removeRules(tagCloud.trackerComponents, true);
                } else {
                    mMainModel.addRules(tagCloud.trackerComponents, true);
                }
                ThreadUtils.postOnMainThread(() -> {
                    if (isDetached()) return;
                    showProgressIndicator(false);
                    displayShortToast(R.string.done);
                    refreshDetails();
                });
            });
        };
        mTrackerCtaAction.setOnClickListener(clickHandler);
        mTrackerCtaCard.setOnClickListener(clickHandler);
    }

    /**
     * Render the prominent "N of T dangerous perms granted · REVOKE" CTA card
     * mirroring the tracker CTA pattern. Hidden when:
     *   - the app declares no dangerous permissions,
     *   - the app is an external APK (we can't act on it),
     *   - none of the declared dangerous perms are currently granted (no action
     *     to take; the chip alone surfaces total declared count).
     *
     * Privilege gating: revocation requires root or shell uid. When the user is
     * on no-root, the action button is disabled and the subtitle prompts them
     * to switch modes via Settings -> Mode of operation.
     */
    @MainThread
    private void setupPermsCtaCard(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        if (mPermsCtaCard == null) return;
        if (mIsExternalApk
                || tagCloud.dangerousPermissionGranted == 0
                || tagCloud.dangerousPermissionTotal == 0) {
            mPermsCtaCard.setVisibility(View.GONE);
            mPermsCtaCard.setOnClickListener(null);
            return;
        }
        mPermsCtaCard.setVisibility(View.VISIBLE);
        int permissionColor = ColorCodes.getPermissionRiskIndicatorColor(requireContext(),
                tagCloud.dangerousPermissionGranted, tagCloud.dangerousPermissionTotal);
        mPermsCtaTitle.setText(getString(R.string.perms_cta_title,
                tagCloud.dangerousPermissionGranted, tagCloud.dangerousPermissionTotal));
        mPermsCtaTitle.setTextColor(permissionColor);
        boolean canRevoke = mMainModel != null
                && io.github.muntashirakon.AppManager.self.SelfPermissions
                        .canModifyAppComponentStates(mUserId, mPackageName,
                                mMainModel.isTestOnlyApp());
        if (canRevoke) {
            mPermsCtaSubtitle.setText(R.string.perms_cta_subtitle_can_revoke);
            mPermsCtaAction.setText(R.string.revoke);
            mPermsCtaAction.setEnabled(true);
            View.OnClickListener handler = v -> {
                if (mMainModel == null || isDetached()) return;
                showProgressIndicator(true);
                mPermsCtaAction.setEnabled(false);
                ThreadUtils.postOnBackgroundThread(() -> {
                    boolean ok = mMainModel.revokeDangerousPermissions();
                    ThreadUtils.postOnMainThread(() -> {
                        if (isDetached()) return;
                        showProgressIndicator(false);
                        displayShortToast(ok
                                ? R.string.done
                                : R.string.failed);
                        refreshDetails();
                    });
                });
            };
            mPermsCtaCard.setOnClickListener(handler);
            mPermsCtaAction.setOnClickListener(handler);
        } else {
            // No-root user: keep the CTA visible (the count is informative) but
            // disable the action and explain. Tap the card to jump to the
            // permissions tab so they can still see what's granted.
            mPermsCtaSubtitle.setText(R.string.perms_cta_subtitle_no_privilege);
            mPermsCtaAction.setText(R.string.view);
            mPermsCtaAction.setEnabled(true);
            View.OnClickListener jump = v -> {
                if (mPackageName == null) return;
                startActivity(io.github.muntashirakon.AppManager.details.AppDetailsActivity
                        .getIntentForPermissions(requireContext(), mPackageName, mUserId));
            };
            mPermsCtaCard.setOnClickListener(jump);
            mPermsCtaAction.setOnClickListener(jump);
        }
    }

    /**
     * Build a "12 ad · 8 analytics · 5 crash" breakdown of the app's known tracker
     * components, grouped by {@link io.github.muntashirakon.AppManager.rules
     * .compontents.TrackerCategory}. Each component's vendor name is resolved from
     * the bundled tracker dataset, then categorized by name keyword. Returns
     * {@code null} when categorization yields nothing useful (every component
     * landed in OTHER or the list is empty), so the caller can fall back to the
     * generic subtitle copy.
     */
    @MainThread
    @Nullable
    private String buildTrackerCategoryBreakdown(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        if (tagCloud.trackerComponents == null || tagCloud.trackerComponents.isEmpty()) {
            return null;
        }
        java.util.EnumMap<io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory, Integer> counts =
                new java.util.EnumMap<>(io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory.class);
        java.util.Set<String> uniqueVendors = new java.util.HashSet<>();
        for (io.github.muntashirakon.AppManager.rules.struct.ComponentRule rule : tagCloud.trackerComponents) {
            String vendor = io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
                    .getTrackerLabel(rule.name);
            if (vendor != null && !vendor.isEmpty()) {
                uniqueVendors.add(vendor);
            }
            io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory cat =
                    io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory.categorize(vendor);
            counts.merge(cat, 1, Integer::sum);
        }
        // Drop OTHER from the breakdown if any other category is present — it's only
        // interesting when *everything* lands there, in which case fall through to
        // the generic copy.
        boolean hasNonOther = false;
        for (io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory cat : counts.keySet()) {
            if (cat != io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory.OTHER) {
                hasNonOther = true;
                break;
            }
        }
        if (!hasNonOther) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory, Integer> e : counts.entrySet()) {
            if (e.getKey() == io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory.OTHER) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getValue()).append(' ').append(getString(e.getKey().getLabelRes()).toLowerCase(java.util.Locale.ROOT));
        }
        // Append the unique-organization count when it tells the user something the
        // category breakdown does not: many components but few organizations means
        // the app's tracker payload is dominated by a single vendor stack (the
        // εxodus dataset assigns multiple SDKs to one company — Google's
        // AdMob/Analytics/Crashlytics/Firebase all roll up to "Google").
        // Show the org count only when it differs from the component count so the
        // line stays informative on small apps.
        if (uniqueVendors.size() >= 2 && uniqueVendors.size() != tagCloud.trackerComponents.size()) {
            sb.append(" · ")
                    .append(getResources().getQuantityString(
                            R.plurals.tracker_breakdown_organizations,
                            uniqueVendors.size(),
                            uniqueVendors.size()));
        }
        return sb.toString();
    }

    @WorkerThread
    @NonNull
    private List<TagItem> getTagCloudItems(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        Objects.requireNonNull(mMainModel);
        Context context = mTagCloud.getContext();
        List<TagItem> tagItems = new LinkedList<>();
        // Add tracker chip
        if (!tagCloud.trackerComponents.isEmpty()) {
            CharSequence[] trackerComponentNames = new CharSequence[tagCloud.trackerComponents.size()];
            int blockedColor = ColorCodes.getComponentTrackerBlockedIndicatorColor(context);
            for (int i = 0; i < trackerComponentNames.length; ++i) {
                ComponentRule rule = tagCloud.trackerComponents.get(i);
                trackerComponentNames[i] = rule.isBlocked() ? getColoredText(rule.name, blockedColor) : rule.name;
            }
            TagItem trackerTag = new TagItem();
            tagItems.add(trackerTag);
            trackerTag.setText(getResources().getQuantityString(R.plurals.no_of_trackers,
                            tagCloud.trackerComponents.size(), tagCloud.trackerComponents.size()))
                    .setColor(tagCloud.areAllTrackersBlocked
                            ? ColorCodes.getComponentTrackerBlockedIndicatorColor(context)
                            : ColorCodes.getTrackerRiskIndicatorColor(context, tagCloud.trackerComponents.size()))
                    .setOnClickListener(v -> {
                        if (!mIsExternalApk && SelfPermissions.canModifyAppComponentStates(mUserId, mPackageName, mMainModel.isTestOnlyApp())) {
                            new SearchableMultiChoiceDialogBuilder<>(v.getContext(), tagCloud.trackerComponents, trackerComponentNames)
                                    .setTitle(R.string.trackers)
                                    .addSelections(tagCloud.trackerComponents)
                                    .setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.block, (dialog, which, selectedItems) -> {
                                        showProgressIndicator(true);
                                        ThreadUtils.postOnBackgroundThread(() -> {
                                            mMainModel.addRules(selectedItems, true);
                                            ThreadUtils.postOnMainThread(() -> {
                                                if (!isDetached()) {
                                                    showProgressIndicator(false);
                                                }
                                                displayShortToast(R.string.done);
                                            });
                                        });
                                    })
                                    .setNeutralButton(R.string.unblock, (dialog, which, selectedItems) -> {
                                        showProgressIndicator(true);
                                        ThreadUtils.postOnBackgroundThread(() -> {
                                            mMainModel.removeRules(selectedItems, true);
                                            ThreadUtils.postOnMainThread(() -> {
                                                if (!isDetached()) {
                                                    showProgressIndicator(false);
                                                }
                                                displayShortToast(R.string.done);
                                            });
                                        });
                                    })
                                    .show();
                        } else {
                            new SearchableItemsDialogBuilder<>(v.getContext(), trackerComponentNames)
                                    .setTitle(R.string.trackers)
                                    .setNegativeButton(R.string.close, null)
                                    .show();
                        }
                    });
        }
        // Dangerous-permission overview chip mirrors the main-list badge: the text
        // exposes granted/total, while the color encodes grant ratio severity.
        if (tagCloud.dangerousPermissionTotal > 0) {
            int grantedColor = ColorCodes.getPermissionRiskIndicatorColor(context,
                    tagCloud.dangerousPermissionGranted, tagCloud.dangerousPermissionTotal);
            String label = getString(R.string.tag_dangerous_perms,
                    tagCloud.dangerousPermissionGranted, tagCloud.dangerousPermissionTotal);
            TagItem permTag = new TagItem();
            permTag.setText(label).setColor(grantedColor)
                    .setOnClickListener(v -> {
                        if (mPackageName == null) return;
                        startActivity(io.github.muntashirakon.AppManager.details.AppDetailsActivity
                                .getIntentForPermissions(requireContext(), mPackageName, mUserId));
                    });
            tagItems.add(permTag);
        }
        if (tagCloud.isSystemApp) {
            tagItems.add(new TagItem()
                    .setTextRes(tagCloud.isSystemlessPath ? R.string.systemless_app : R.string.system_app));
            if (tagCloud.isUpdatedSystemApp) {
                tagItems.add(new TagItem().setTextRes(R.string.updated_app));
            }
        } else if (!mIsExternalApk) {
            tagItems.add(new TagItem().setTextRes(R.string.user_app));
        }
        if (tagCloud.splitCount > 0) {
            TagItem splitTag = new TagItem();
            tagItems.add(splitTag);
            splitTag.setText(getResources().getQuantityString(R.plurals.no_of_splits, tagCloud.splitCount,
                            tagCloud.splitCount))
                    .setOnClickListener(v -> {
                        ApkFile apkFile = mMainModel.getApkFile();
                        if (apkFile == null) {
                            return;
                        }
                        // Display a list of apks
                        List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                        CharSequence[] entryNames = new CharSequence[tagCloud.splitCount];
                        for (int i = 0; i < tagCloud.splitCount; ++i) {
                            entryNames[i] = apkEntries.get(i + 1).toLocalizedString(v.getContext());
                        }
                        new SearchableItemsDialogBuilder<>(v.getContext(), entryNames)
                                .setTitle(R.string.splits)
                                .setNegativeButton(R.string.close, null)
                                .show();
                    });
        }
        if (tagCloud.isDebuggable) {
            tagItems.add(new TagItem().setTextRes(R.string.debuggable));
        }
        if (tagCloud.isTestOnly) {
            tagItems.add(new TagItem().setTextRes(R.string.test_only));
        }
        if (tagCloud.isArchived) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.archived_app)
                    .setColor(ColorCodes.getRemovalCautionIndicatorColor(context)));
        }
        if (!tagCloud.hasCode) {
            tagItems.add(new TagItem().setTextRes(R.string.no_code));
        }
        if (tagCloud.isOverlay) {
            TagItem overlayTag = new TagItem();
            tagItems.add(overlayTag);
            overlayTag.setTextRes(R.string.title_overlay)
                    .setOnClickListener(v -> {
                        Context ctx = v.getContext();
                        String target = Objects.requireNonNull(PackageInfoCompat2.getOverlayTarget(mPackageInfo));
                        String targetName = PackageInfoCompat2.getTargetOverlayableName(mPackageInfo);
                        String category = PackageInfoCompat2.getOverlayCategory(mPackageInfo);
                        int priority = PackageInfoCompat2.getOverlayPriority(mPackageInfo);
                        boolean isStatic = PackageInfoCompat2.isStaticOverlayPackage(mPackageInfo);
                        SpannableStringBuilder spannable = new SpannableStringBuilder();
                        if (targetName != null) {
                            spannable.append(getStyledKeyValue(ctx, R.string.overlay_target, targetName))
                                    .append("\n")
                                    .append(getSmallerText(target));
                        } else {
                            spannable.append(getStyledKeyValue(ctx, R.string.overlay_target, target));
                        }
                        if (category != null) {
                            spannable.append("\n")
                                    .append(getSmallerText(getStyledKeyValue(ctx, R.string.overlay_category, category)));
                        }
                        if (!isStatic) {
                            spannable.append("\n")
                                    .append(getSmallerText(getStyledKeyValue(ctx, R.string.priority, String.valueOf(priority))));
                        } // else static overlays have the highest priority
                        new MaterialAlertDialogBuilder(ctx)
                                .setTitle(R.string.title_overlay)
                                .setMessage(spannable)
                                .setNeutralButton(R.string.app_info, (dialog, which) -> {
                                    Intent appDetailsIntent = AppDetailsActivity.getIntent(ctx, target, mUserId);
                                    startActivity(appDetailsIntent);
                                })
                                .setNegativeButton(R.string.close, null)
                                .show();
                    });
        }
        if (tagCloud.hasRequestedLargeHeap) {
            tagItems.add(new TagItem().setTextRes(R.string.requested_large_heap));
        }
        if (tagCloud.hostsToOpen != null) {
            TagItem openLinksTag = new TagItem();
            tagItems.add(openLinksTag);
            openLinksTag.setTextRes(R.string.app_info_tag_open_links)
                    .setColor(!tagCloud.domainLinkConflicts.isEmpty() ? ColorCodes.getRemovalCautionIndicatorColor(context)
                            : tagCloud.canOpenLinks ? ColorCodes.getFailureColor(context)
                            : ColorCodes.getSuccessColor(context))
                    .setOnClickListener(v -> {
                        SearchableItemsDialogBuilder<CharSequence> builder = new SearchableItemsDialogBuilder<>(
                                v.getContext(), buildDomainDialogRows(v.getContext(), tagCloud))
                                .setTitle(R.string.title_domains_supported_by_the_app)
                                .setNegativeButton(R.string.close, null);
                        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION)) {
                            // Enable/disable directly from the app
                            builder.setPositiveButton(tagCloud.canOpenLinks ? R.string.disable : R.string.enable,
                                    (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                DomainVerificationManagerCompat.setDomainVerificationLinkHandlingAllowed(
                                                        mPackageName, !tagCloud.canOpenLinks, mUserId);
                                            }
                                            mMainModel.getTagsAlteredLiveData().postValue(true);
                                            ThreadUtils.postOnMainThread(() ->
                                                    UIUtils.displayShortToast(R.string.done));
                                        } catch (Throwable th) {
                                            th.printStackTrace();
                                            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
                                        }
                                    }));
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            builder.setPositiveButton(R.string.app_settings, (dialog, which) -> {
                                try {
                                    startActivity(IntentUtils.getSettings(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, mPackageName));
                                } catch (Throwable th) {
                                    ExUtils.exceptionAsIgnored(() -> startActivity(IntentUtils.getAppDetailsSettings(mPackageName)));
                                }
                            });
                        }
                        builder.show();
                    });
        }
        if (!tagCloud.runningServices.isEmpty()) {
            TagItem runningTag = new TagItem();
            tagItems.add(runningTag);
            runningTag.setTextRes(R.string.running)
                    .setColor(ColorCodes.getComponentRunningIndicatorColor(context))
                    .setOnClickListener(v ->
                            displayRunningServices(tagCloud.runningServices, v.getContext()));
        } else if (tagCloud.isRunning) {
            TagItem runningTag = new TagItem();
            tagItems.add(runningTag);
            runningTag.setTextRes(R.string.running)
                    .setColor(ColorCodes.getComponentRunningIndicatorColor(context));
        }
        if (tagCloud.isForceStopped) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.stopped)
                    .setColor(ColorCodes.getAppForceStoppedIndicatorColor(context)));
        }
        if (!tagCloud.isAppEnabled) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.disabled_app)
                    .setColor(ColorCodes.getAppDisabledIndicatorColor(context)));
        }
        if (tagCloud.isAppSuspended) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.suspended)
                    .setColor(ColorCodes.getAppSuspendedIndicatorColor(context)));
        }
        if (tagCloud.isAppHidden) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.hidden)
                    .setColor(ColorCodes.getAppHiddenIndicatorColor(context)));
        }
        mMagiskHiddenProcesses = tagCloud.magiskHiddenProcesses;
        if (tagCloud.isMagiskHideEnabled) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.magisk_hide_enabled)
                    .setOnClickListener(v -> displayMagiskHideDialog()));
        }
        mMagiskDeniedProcesses = tagCloud.magiskDeniedProcesses;
        if (tagCloud.isMagiskDenyListEnabled) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.magisk_denylist)
                    .setOnClickListener(v -> displayMagiskDenyListDialog()));
        }
        if (tagCloud.canWriteAndExecute) {
            TagItem wxItem = new TagItem();
            tagItems.add(wxItem);
            wxItem.setText("WX")
                    .setColor(ColorCodes.getAppWriteAndExecuteIndicatorColor(context))
                    .setOnClickListener(v ->
                            new ScrollableDialogBuilder(v.getContext())
                                    .setTitle("WX")
                                    .setMessage(R.string.app_can_write_and_execute_in_same_place)
                                    .enableAnchors()
                                    .setNegativeButton(R.string.close, null)
                                    .show());
        }
        TagItem memoryTaggingTag = new TagItem();
        tagItems.add(memoryTaggingTag);
        memoryTaggingTag.setTextRes(getMemoryTaggingChipTextRes(tagCloud.memoryTaggingInfo))
                .setColor(getMemoryTaggingChipColor(context, tagCloud.memoryTaggingInfo))
                .setOnClickListener(v -> showMemoryTaggingDialog(v.getContext(), tagCloud.memoryTaggingInfo));
        TagItem sdkSandboxTag = new TagItem();
        tagItems.add(sdkSandboxTag);
        if (tagCloud.sdkSandboxInfo.hasDeclaredSdkLibraries()) {
            int sdkLibraryCount = tagCloud.sdkSandboxInfo.declaredSdkLibraries.size();
            sdkSandboxTag.setText(getResources().getQuantityString(R.plurals.sdk_sandbox_chip_count,
                    sdkLibraryCount, sdkLibraryCount));
        } else {
            sdkSandboxTag.setTextRes(tagCloud.sdkSandboxInfo.isSupported()
                    ? R.string.sdk_sandbox_chip_none
                    : R.string.sdk_sandbox_chip_unsupported);
        }
        sdkSandboxTag.setColor(getSdkSandboxChipColor(context, tagCloud.sdkSandboxInfo))
                .setOnClickListener(v -> showSdkSandboxDialog(v.getContext(), tagCloud.sdkSandboxInfo));
        TagItem healthConnectTag = new TagItem();
        tagItems.add(healthConnectTag);
        if (tagCloud.healthConnectInfo.hasRequestedHealthPermissions()) {
            int permissionCount = tagCloud.healthConnectInfo.requestedPermissions.size();
            healthConnectTag.setText(getResources().getQuantityString(R.plurals.health_connect_chip_count,
                    permissionCount, permissionCount));
        } else {
            healthConnectTag.setTextRes(tagCloud.healthConnectInfo.isSupported()
                    ? R.string.health_connect_chip_none
                    : R.string.health_connect_chip_unsupported);
        }
        healthConnectTag.setColor(getHealthConnectChipColor(context, tagCloud.healthConnectInfo))
                .setOnClickListener(v -> showHealthConnectDialog(v.getContext(), tagCloud.healthConnectInfo));
        TagItem credentialProviderTag = new TagItem();
        tagItems.add(credentialProviderTag);
        if (tagCloud.credentialProviderManifestInfo.hasProviderServices()) {
            int serviceCount = tagCloud.credentialProviderManifestInfo.providerServices.size();
            credentialProviderTag.setText(getResources().getQuantityString(R.plurals.credential_provider_chip_count,
                    serviceCount, serviceCount));
        } else {
            credentialProviderTag.setTextRes(tagCloud.credentialProviderManifestInfo.isSupported()
                    ? R.string.credential_provider_chip_none
                    : R.string.credential_provider_chip_unsupported);
        }
        credentialProviderTag.setColor(getCredentialProviderChipColor(context, tagCloud.credentialProviderManifestInfo))
                .setOnClickListener(v -> showCredentialProviderDialog(v.getContext(),
                        tagCloud.credentialProviderManifestInfo));
        if (tagCloud.manifestMetadataInfo.hasMetadata()) {
            int metadataCount = tagCloud.manifestMetadataInfo.getMetadataCount();
            TagItem manifestMetadataTag = new TagItem();
            tagItems.add(manifestMetadataTag);
            manifestMetadataTag.setText(getResources().getQuantityString(R.plurals.manifest_metadata_chip_count,
                            metadataCount, metadataCount))
                    .setOnClickListener(v -> showManifestMetadataDialog(v.getContext(),
                            tagCloud.manifestMetadataInfo));
        }
        if (tagCloud.warnsCleartextDeprecation) {
            TagItem cleartextTag = new TagItem();
            tagItems.add(cleartextTag);
            cleartextTag.setTextRes(R.string.app_info_tag_cleartext_deprecated)
                    .setColor(ColorCodes.getRemovalCautionIndicatorColor(context))
                    .setOnClickListener(v -> new ScrollableDialogBuilder(v.getContext())
                            .setTitle(R.string.cleartext_deprecation_android18_title)
                            .setMessage(R.string.cleartext_deprecation_android18_message)
                            .setNegativeButton(R.string.close, null)
                            .show());
        }
        if (tagCloud.bloatwareRemovalType != 0) {
            TagItem bloatwareTag = new TagItem();
            tagItems.add(bloatwareTag);
            String label = getBloatwareSafetyLabel(context, tagCloud.bloatwareRemovalType);
            bloatwareTag.setText(label)
                    .setColor(ColorCodes.getBloatwareIndicatorColor(context, tagCloud.bloatwareRemovalType))
                    .setOnClickListener(v -> {
                        BloatwareDetailsDialog dialog = BloatwareDetailsDialog.getInstance(mPackageName);
                        dialog.show(getChildFragmentManager(), BloatwareDetailsDialog.TAG);
                    });
        }
        // Cross-verifiable signing-cert fingerprint. The clipboard-paste
        // verify flow on the icon already exists; this surfaces the same
        // digest *prominently* so users running AppVerifier or apksigner
        // verify can copy and compare in one tap.
        if (tagCloud.signingCertSha256 != null) {
            String fp = tagCloud.signingCertSha256;
            String subject = tagCloud.signingCertSubject;
            String issuer = tagCloud.signingCertIssuer;
            TagItem certTag = new TagItem();
            tagItems.add(certTag);
            certTag.setText(getString(R.string.cert_fingerprint_chip_label, shortFingerprint(fp)))
                    .setOnClickListener(v -> showCertFingerprintDialog(v.getContext(), fp, subject, issuer));
        }
        // NF-17 — Runtime activity chip. Always surface so the user can sample
        // last-24h screen-time / network use without opening the global usage
        // surface. Backgrounded by RuntimeTelemetryHelper so the chip render
        // stays on the UI thread.
        if (!mIsExternalApk) {
            TagItem runtimeTag = new TagItem();
            tagItems.add(runtimeTag);
            runtimeTag.setTextRes(R.string.runtime_telemetry_chip_label)
                    .setOnClickListener(v -> showRuntimeTelemetryDialog());
        }
        // NF-11 — package-visibility signal. Surfaces only when the app holds
        // QUERY_ALL_PACKAGES or declares a non-empty <queries> manifest block.
        if (tagCloud.packageVisibility != null && tagCloud.packageVisibility.hasSignal()) {
            PackageVisibilityInfo visibility = tagCloud.packageVisibility;
            TagItem visibilityTag = new TagItem();
            tagItems.add(visibilityTag);
            int summaryRes = visibility.holdsQueryAllPackages
                    ? R.string.package_visibility_query_all_chip
                    : R.string.package_visibility_queries_chip;
            visibilityTag.setTextRes(summaryRes)
                    .setOnClickListener(v -> showPackageVisibilityDialog(visibility));
            if (visibility.holdsQueryAllPackages) {
                visibilityTag.setColor(ColorCodes.getFailureColor(context));
            } else {
                visibilityTag.setColor(ColorCodes.getRemovalCautionIndicatorColor(context));
            }
        }
        if (tagCloud.developerVerificationStatus != DeveloperVerificationCompat.STATUS_UNAVAILABLE) {
            TagItem verifierTag = new TagItem();
            tagItems.add(verifierTag);
            switch (tagCloud.developerVerificationStatus) {
                case DeveloperVerificationCompat.STATUS_VERIFIED:
                    verifierTag.setTextRes(R.string.developer_verification_verified)
                            .setColor(ColorCodes.getSuccessColor(context));
                    break;
                case DeveloperVerificationCompat.STATUS_UNVERIFIED:
                    verifierTag.setTextRes(R.string.developer_verification_unverified)
                            .setColor(ColorCodes.getFailureColor(context));
                    break;
                case DeveloperVerificationCompat.STATUS_UNKNOWN:
                default:
                    verifierTag.setTextRes(R.string.developer_verification_unknown)
                            .setColor(ColorCodes.getRemovalCautionIndicatorColor(context));
                    break;
            }
            verifierTag.setOnClickListener(v -> new ScrollableDialogBuilder(mActivity)
                    .setTitle(R.string.developer_verification)
                    .setMessage(R.string.developer_verification_unknown_description)
                    .setNegativeButton(R.string.close, null)
                    .show());
        }
        if (tagCloud.hasKeyStoreItems) {
            TagItem keyStoreTag = new TagItem();
            tagItems.add(keyStoreTag);
            keyStoreTag.setTextRes(R.string.keystore)
                    .setOnClickListener(view -> new SearchableItemsDialogBuilder<>(view.getContext(), KeyStoreUtils
                            .getKeyStoreFiles(mApplicationInfo.uid, mUserId))
                            .setTitle(R.string.keystore)
                            .setNegativeButton(R.string.close, null)
                            .show());
            if (tagCloud.hasMasterKeyInKeyStore) {
                keyStoreTag.setColor(ColorCodes.getAppKeystoreIndicatorColor(context));
            }
        }
        if (!tagCloud.backups.isEmpty()) {
            TagItem backupTag = new TagItem();
            tagItems.add(backupTag);
            backupTag.setTextRes(R.string.backup)
                    .setOnClickListener(v -> {
                        BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                                Collections.singletonList(new UserPackagePair(mPackageName, mUserId)),
                                BackupRestoreDialogFragment.MODE_RESTORE | BackupRestoreDialogFragment.MODE_DELETE);
                        fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                        fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                        fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
                    });
        }
        if (!tagCloud.isBatteryOptimized) {
            TagItem batteryOptTag = new TagItem();
            tagItems.add(batteryOptTag);
            batteryOptTag.setTextRes(R.string.no_battery_optimization)
                    .setColor(ColorCodes.getAppNoBatteryOptimizationIndicatorColor(context));
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                batteryOptTag.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.enable_battery_optimization)
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            if (DeviceIdleManagerCompat.enableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done);
                                mMainModel.getTagsAlteredLiveData().setValue(true);
                            } else {
                                UIUtils.displayShortToast(R.string.failed);
                            }
                        })
                        .show());
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batteryOptTag.setOnClickListener(v -> ExUtils.exceptionAsIgnored(() ->
                        startActivity(IntentUtils.getBatteryOptSettings(mPackageName))));
            }
        }
        if (!tagCloud.sensorsEnabled) {
            TagItem sensorsTag = new TagItem();
            tagItems.add(sensorsTag);
            sensorsTag.setTextRes(R.string.tag_sensors_disabled);
        }
        if (tagCloud.netPolicies > 0) {
            String[] readablePolicies = NetworkPolicyManagerCompat.getReadablePolicies(context, tagCloud.netPolicies)
                    .values().toArray(new String[0]);
            TagItem netPolicyTag = new TagItem();
            tagItems.add(netPolicyTag);
            netPolicyTag.setTextRes(R.string.has_net_policy)
                    .setOnClickListener(v -> new SearchableItemsDialogBuilder<>(v.getContext(), readablePolicies)
                            .setTitle(R.string.net_policy)
                            .setNegativeButton(R.string.ok, null)
                            .show());
        }
        if (tagCloud.ssaid != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TagItem ssaidTag = new TagItem();
            tagItems.add(ssaidTag);
            ssaidTag.setTextRes(R.string.ssaid)
                    .setColor(ColorCodes.getAppSsaidIndicatorColor(context))
                    .setOnClickListener(v -> {
                        ChangeSsaidDialog changeSsaidDialog = ChangeSsaidDialog.getInstance(mPackageName, mApplicationInfo.uid,
                                tagCloud.ssaid);
                        changeSsaidDialog.setSsaidChangedInterface((newSsaid, isSuccessful) -> {
                            displayLongToast(isSuccessful ? R.string.restart_to_reflect_changes : R.string.failed_to_change_ssaid);
                            if (isSuccessful) tagCloud.ssaid = newSsaid;
                        });
                        changeSsaidDialog.show(getChildFragmentManager(), ChangeSsaidDialog.TAG);
                    });
        }
        if (tagCloud.uriGrants != null) {
            TagItem safTag = new TagItem();
            tagItems.add(safTag);
            safTag.setTextRes(R.string.saf)
                    .setOnClickListener(v -> {
                        CharSequence[] uriGrants = new CharSequence[tagCloud.uriGrants.size()];
                        for (int i = 0; i < tagCloud.uriGrants.size(); ++i) {
                            uriGrants[i] = GrantUriUtils.toLocalisedString(v.getContext(), tagCloud.uriGrants.get(i).uri);
                        }
                        new SearchableItemsDialogBuilder<>(v.getContext(), uriGrants)
                                .setTitle(R.string.saf)
                                .setTextSelectable(true)
                                .setListBackgroundColorOdd(ColorCodes.getListItemColor0(mActivity))
                                .setListBackgroundColorEven(ColorCodes.getListItemColor1(mActivity))
                                .setNegativeButton(R.string.close, null)
                                .show();
                    });
        }
        if (tagCloud.usesPlayAppSigning) {
            TagItem playAppSigningTag = new TagItem();
            tagItems.add(playAppSigningTag);
            playAppSigningTag.setTextRes(R.string.uses_play_app_signing)
                    .setColor(ColorCodes.getAppPlayAppSigningIndicatorColor(context))
                    .setOnClickListener(v ->
                            new ScrollableDialogBuilder(mActivity)
                                    .setTitle(R.string.uses_play_app_signing)
                                    .setMessage(R.string.uses_play_app_signing_description)
                                    .setNegativeButton(R.string.close, null)
                                    .show());
        }
        if (tagCloud.xposedModuleInfo != null) {
            TagItem xposedItem = new TagItem();
            tagItems.add(xposedItem);
            xposedItem.setText("Xposed")
                    .setOnClickListener(v -> new ScrollableDialogBuilder(v.getContext())
                            .setTitle(R.string.xposed_module_info)
                            .setMessage(tagCloud.xposedModuleInfo.toLocalizedString(v.getContext()))
                            .setNegativeButton(R.string.close, null)
                            .show());
        }
        if (tagCloud.staticSharedLibraryNames != null) {
            TagItem staticSharedLibraryTag = new TagItem();
            tagItems.add(staticSharedLibraryTag);
            staticSharedLibraryTag.setTextRes(R.string.static_shared_library)
                    .setOnClickListener(v -> new SearchableMultiChoiceDialogBuilder<>(v.getContext(), tagCloud.staticSharedLibraryNames, tagCloud.staticSharedLibraryNames)
                            .setTitle(R.string.shared_libs)
                            .setPositiveButton(R.string.close, null)
                            .setNeutralButton(R.string.uninstall, (dialog, which, selectedItems) -> {
                                int userId = mUserId;
                                final boolean isSystemApp = ApplicationInfoCompat.isSystemApp(mApplicationInfo);
                                new ScrollableDialogBuilder(mActivity,
                                        isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                                        .setTitle(mAppLabel)
                                        .setPositiveButton(R.string.uninstall, (dialog1, which1, keepData) -> {
                                            if (selectedItems.size() == 1) {
                                                ThreadUtils.postOnBackgroundThread(() -> {
                                                    PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                                    installer.setAppLabel(mAppLabel);
                                                    boolean uninstalled = installer.uninstall(selectedItems.get(0), userId, false);
                                                    ThreadUtils.postOnMainThread(() -> {
                                                        if (uninstalled) {
                                                            displayLongToast(R.string.uninstalled_successfully, mAppLabel);
                                                            mActivity.finish();
                                                        } else {
                                                            displayLongToast(R.string.failed_to_uninstall, mAppLabel);
                                                        }
                                                    });
                                                });
                                            } else {
                                                ArrayList<Integer> userIds = new ArrayList<>(selectedItems.size());
                                                for (int i = 0; i < selectedItems.size(); ++i) {
                                                    userIds.add(userId);
                                                }
                                                BatchQueueItem item = BatchQueueItem.getBatchOpQueue(
                                                        BatchOpsManager.OP_UNINSTALL, selectedItems, userIds, null);
                                                Intent intent = BatchOpsService.getServiceIntent(mActivity, item);
                                                ContextCompat.startForegroundService(mActivity, intent);
                                            }
                                        })
                                        .setNegativeButton(R.string.cancel, (dialog1, which1, keepData) -> {
                                            if (dialog != null) dialog.cancel();
                                        })
                                        .show();
                            })
                            .show());
        }
        // NF-08: surface user-authored tags (AppTagStore) in the cloud so tags
        // assigned via "Edit tags" are visible, not just filterable in Finder.
        // Tapping one opens the editor.
        if (mPackageName != null) {
            for (String userTag : new AppTagStore(requireContext()).getTags(mPackageName)) {
                tagItems.add(new TagItem()
                        .setText(userTag)
                        .setOnClickListener(v -> showEditTagsDialog()));
            }
        }
        return tagItems;
    }

    @NonNull
    private List<CharSequence> buildDomainDialogRows(@NonNull Context context,
                                                     @NonNull AppInfoViewModel.TagCloud tagCloud) {
        List<CharSequence> rows = new ArrayList<>(tagCloud.hostsToOpen.size());
        for (Map.Entry<String, Integer> entry : tagCloud.hostsToOpen.entrySet()) {
            String host = entry.getKey();
            SpannableStringBuilder row = new SpannableStringBuilder(host)
                    .append(getSmallerText("\n" + getString(R.string.domain_verification_state,
                            getDomainStateLabel(context, entry.getValue()))));
            List<DomainLinkConflictDetector.Conflict> conflicts = tagCloud.domainLinkConflicts.get(normalizeDomainHost(host));
            if (conflicts != null && !conflicts.isEmpty()) {
                row.append(getSmallerText("\n" + context.getResources().getQuantityString(
                        R.plurals.domain_link_conflict_count, conflicts.size(), conflicts.size())
                        + ": " + summarizeDomainConflicts(conflicts)));
            }
            rows.add(row);
        }
        return rows;
    }

    @NonNull
    private String normalizeDomainHost(@NonNull String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @NonNull
    private String getDomainStateLabel(@NonNull Context context, int state) {
        switch (state) {
            case DomainVerificationUserState.DOMAIN_STATE_VERIFIED:
                return context.getString(R.string.domain_verification_state_verified);
            case DomainVerificationUserState.DOMAIN_STATE_SELECTED:
                return context.getString(R.string.domain_verification_state_selected);
            case DomainVerificationUserState.DOMAIN_STATE_NONE:
                return context.getString(R.string.domain_verification_state_unverified);
            default:
                return context.getString(R.string.domain_verification_state_unknown, state);
        }
    }

    @NonNull
    private String summarizeDomainConflicts(@NonNull List<DomainLinkConflictDetector.Conflict> conflicts) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(conflicts.size(), 3);
        for (int i = 0; i < limit; ++i) {
            DomainLinkConflictDetector.Conflict conflict = conflicts.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(conflict.label).append(" (").append(conflict.packageName).append(")");
        }
        if (conflicts.size() > limit) {
            sb.append(", +").append(conflicts.size() - limit);
        }
        return sb.toString();
    }

    /**
     * Map a {@link DebloatObject.Removal} flag to a short, human-readable label for
     * the App Info tag cloud. We expose the safety call directly on the chip so
     * users see "Bloatware · Safe" / "Bloatware · Caution" / etc. at a glance,
     * instead of a generic "Bloatware" tag that requires a tap to disambiguate.
     * The colour is still painted by {@link ColorCodes#getBloatwareIndicatorColor}.
     */
    @NonNull
    private String getBloatwareSafetyLabel(@NonNull Context context,
                                           @DebloatObject.Removal int removalType) {
        @StringRes int suffixRes;
        switch (removalType) {
            case DebloatObject.REMOVAL_SAFE:
                suffixRes = R.string.debloat_removal_safe;
                break;
            case DebloatObject.REMOVAL_REPLACE:
                suffixRes = R.string.debloat_removal_replace;
                break;
            case DebloatObject.REMOVAL_CAUTION:
                suffixRes = R.string.debloat_removal_caution;
                break;
            case DebloatObject.REMOVAL_UNSAFE:
                suffixRes = R.string.debloat_removal_unsafe;
                break;
            default:
                return "Bloatware";
        }
        return "Bloatware · " + context.getString(suffixRes);
    }

    /**
     * Compact form of a colon-separated SHA-256 fingerprint for the tag chip:
     * keep the first and last 4 hex digits ("21:5F:B4:70…D0:C2:38:6C") so the
     * chip stays one line wide while still showing enough to spot a mismatch
     * at a glance. Full digest is one tap away in the dialog.
     */
    @NonNull
    private static String shortFingerprint(@NonNull String fingerprint) {
        if (fingerprint.length() <= 11) return fingerprint;
        return fingerprint.substring(0, 5) + "…" + fingerprint.substring(fingerprint.length() - 5);
    }

    @StringRes
    private int getMemoryTaggingChipTextRes(@NonNull MemoryTaggingInfo info) {
        switch (info.status) {
            case MemoryTaggingInfo.STATUS_SYNC:
                return R.string.memory_tagging_chip_sync;
            case MemoryTaggingInfo.STATUS_ASYNC:
                return R.string.memory_tagging_chip_async;
            case MemoryTaggingInfo.STATUS_OFF:
                return R.string.memory_tagging_chip_off;
            case MemoryTaggingInfo.STATUS_DEFAULT:
                return R.string.memory_tagging_chip_default;
            case MemoryTaggingInfo.STATUS_UNSUPPORTED:
            default:
                return R.string.memory_tagging_chip_unsupported;
        }
    }

    @StringRes
    private int getMemoryTaggingStatusTextRes(@NonNull MemoryTaggingInfo info) {
        switch (info.status) {
            case MemoryTaggingInfo.STATUS_SYNC:
                return R.string.memory_tagging_status_sync;
            case MemoryTaggingInfo.STATUS_ASYNC:
                return R.string.memory_tagging_status_async;
            case MemoryTaggingInfo.STATUS_OFF:
                return R.string.memory_tagging_status_off;
            case MemoryTaggingInfo.STATUS_DEFAULT:
                return R.string.memory_tagging_status_default;
            case MemoryTaggingInfo.STATUS_UNSUPPORTED:
            default:
                return R.string.memory_tagging_status_unsupported;
        }
    }

    private int getMemoryTaggingChipColor(@NonNull Context context, @NonNull MemoryTaggingInfo info) {
        switch (info.status) {
            case MemoryTaggingInfo.STATUS_SYNC:
            case MemoryTaggingInfo.STATUS_ASYNC:
                return ColorCodes.getSuccessColor(context);
            case MemoryTaggingInfo.STATUS_OFF:
                return ColorCodes.getFailureColor(context);
            case MemoryTaggingInfo.STATUS_DEFAULT:
            case MemoryTaggingInfo.STATUS_UNSUPPORTED:
            default:
                return ColorCodes.getRemovalCautionIndicatorColor(context);
        }
    }

    private void showMemoryTaggingDialog(@NonNull Context context, @NonNull MemoryTaggingInfo info) {
        String body = getString(R.string.memory_tagging_dialog_body,
                getString(getMemoryTaggingStatusTextRes(info)),
                getString(info.allowsNativeHeapPointerTagging
                        ? R.string.memory_tagging_pointer_tagging_allowed
                        : R.string.memory_tagging_pointer_tagging_disabled),
                info.sdkInt);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.memory_tagging_dialog_title)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private int getSdkSandboxChipColor(@NonNull Context context, @NonNull SdkSandboxInfo info) {
        if (!info.isSupported()) {
            return ColorCodes.getRemovalCautionIndicatorColor(context);
        }
        return info.hasDeclaredSdkLibraries()
                ? ColorCodes.getRemovalCautionIndicatorColor(context)
                : ColorCodes.getSuccessColor(context);
    }

    private void showSdkSandboxDialog(@NonNull Context context, @NonNull SdkSandboxInfo info) {
        String body;
        if (!info.isSupported()) {
            body = getString(R.string.sdk_sandbox_dialog_unsupported, info.sdkInt);
        } else if (!info.hasDeclaredSdkLibraries()) {
            body = getString(R.string.sdk_sandbox_dialog_none);
        } else {
            StringBuilder builder = new StringBuilder(getString(R.string.sdk_sandbox_dialog_header));
            for (SdkSandboxInfo.SdkLibrary sdkLibrary : info.declaredSdkLibraries) {
                builder.append("\n  - ").append(sdkLibrary.toDisplayString());
            }
            builder.append("\n\n").append(getString(R.string.sdk_sandbox_dialog_scope_note));
            body = builder.toString();
        }
        new ScrollableDialogBuilder(context)
                .setTitle(R.string.sdk_sandbox_dialog_title)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private int getHealthConnectChipColor(@NonNull Context context, @NonNull HealthConnectInfo info) {
        if (!info.isSupported()) {
            return ColorCodes.getRemovalCautionIndicatorColor(context);
        }
        return info.hasRequestedHealthPermissions()
                ? ColorCodes.getRemovalCautionIndicatorColor(context)
                : ColorCodes.getSuccessColor(context);
    }

    private void showHealthConnectDialog(@NonNull Context context, @NonNull HealthConnectInfo info) {
        String body;
        if (!info.isSupported()) {
            body = getString(R.string.health_connect_dialog_unsupported, info.sdkInt);
        } else if (!info.hasRequestedHealthPermissions()) {
            body = getString(R.string.health_connect_dialog_none);
        } else {
            StringBuilder builder = new StringBuilder(getString(R.string.health_connect_dialog_header,
                    info.readPermissionCount, info.writePermissionCount));
            for (HealthConnectInfo.HealthPermission permission : info.requestedPermissions) {
                builder.append("\n  - ").append(permission.toDisplayString());
            }
            builder.append("\n\n").append(getString(R.string.health_connect_dialog_scope_note));
            body = builder.toString();
        }
        ScrollableDialogBuilder builder = new ScrollableDialogBuilder(context)
                .setTitle(R.string.health_connect_dialog_title)
                .setMessage(body)
                .setPositiveButton(R.string.close, null);
        if (info.isSupported()) {
            builder.setNeutralButton(R.string.health_connect_open_permissions,
                    (dialog, which, isChecked) -> openHealthConnectPermissions(context));
        }
        builder.show();
    }

    private void openHealthConnectPermissions(@NonNull Context context) {
        try {
            Intent intent = new Intent(ACTION_MANAGE_HEALTH_PERMISSIONS)
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName);
            context.startActivity(intent);
        } catch (Throwable th) {
            displayShortToast(R.string.health_connect_permissions_unavailable);
        }
    }

    private int getCredentialProviderChipColor(@NonNull Context context,
                                               @NonNull CredentialProviderManifestInfo info) {
        if (!info.isSupported()) {
            return ColorCodes.getRemovalCautionIndicatorColor(context);
        }
        if (!info.hasProviderServices()) {
            return ColorCodes.getSuccessColor(context);
        }
        for (CredentialProviderManifestInfo.ServiceDeclaration service : info.providerServices) {
            if (!service.hasRequiredBindPermission) {
                return ColorCodes.getFailureColor(context);
            }
        }
        return ColorCodes.getRemovalCautionIndicatorColor(context);
    }

    private void showCredentialProviderDialog(@NonNull Context context,
                                              @NonNull CredentialProviderManifestInfo info) {
        String body;
        if (!info.isSupported()) {
            body = getString(R.string.credential_provider_dialog_unsupported, info.sdkInt);
        } else if (!info.hasProviderServices()) {
            body = getString(R.string.credential_provider_dialog_none);
        } else {
            StringBuilder builder = new StringBuilder(getString(R.string.credential_provider_dialog_header,
                    info.providerServices.size(), info.getSystemProviderServiceCount()));
            for (CredentialProviderManifestInfo.ServiceDeclaration service : info.providerServices) {
                builder.append("\n  - ").append(service.toDisplayString());
            }
            builder.append("\n\n").append(getString(R.string.credential_provider_dialog_scope_note));
            body = builder.toString();
        }
        ScrollableDialogBuilder builder = new ScrollableDialogBuilder(context)
                .setTitle(R.string.credential_provider_dialog_title)
                .setMessage(body)
                .setPositiveButton(R.string.close, null);
        if (info.isSupported()) {
            builder.setNeutralButton(R.string.credential_provider_open_settings,
                    (dialog, which, isChecked) -> openCredentialProviderSettings(context));
        }
        builder.show();
    }

    private void showManifestMetadataDialog(@NonNull Context context, @NonNull ManifestMetadataInfo info) {
        String body;
        if (!info.hasMetadata()) {
            body = getString(R.string.manifest_metadata_dialog_none);
        } else {
            body = info.toDisplayString() + "\n\n" + getString(R.string.manifest_metadata_dialog_scope_note);
        }
        new ScrollableDialogBuilder(context)
                .setTitle(R.string.manifest_metadata_dialog_title)
                .setMessage(body)
                .setNeutralButton(R.string.copy, (dialog, which, isChecked) ->
                        ClipboardUtils.copyToClipboard(context,
                                getString(R.string.manifest_metadata_dialog_title), info.toCopyText()))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void openCredentialProviderSettings(@NonNull Context context) {
        try {
            context.startActivity(new Intent(ACTION_CREDENTIAL_PROVIDER_SETTINGS));
        } catch (Throwable th) {
            displayShortToast(R.string.credential_provider_settings_unavailable);
        }
    }

    /**
     * Surface the full SHA-256 fingerprint plus the Subject and Issuer DNs
     * with a Copy button (digest-only, matching the existing
     * AppVerifier / {@code apksigner verify --print-certs} cross-check flow).
     * Subject and Issuer are RFC 2253 distinguished names from the X.509
     * certificate; they're informational only — only the fingerprint is
     * cryptographically meaningful, but the DNs let users see who the
     * certificate claims to be issued <em>to</em> at a glance.
     */
    private void showCertFingerprintDialog(@NonNull Context context, @NonNull String fingerprint,
                                           @Nullable String subject, @Nullable String issuer) {
        StringBuilder body = new StringBuilder();
        body.append(getString(R.string.cert_fingerprint_dialog_sha256_header))
                .append('\n').append(fingerprint);
        if (subject != null) {
            body.append("\n\n").append(getString(R.string.cert_fingerprint_dialog_subject_header))
                    .append('\n').append(subject);
        }
        if (issuer != null) {
            body.append("\n\n").append(getString(R.string.cert_fingerprint_dialog_issuer_header))
                    .append('\n').append(issuer);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.cert_fingerprint_dialog_title)
                .setMessage(body.toString())
                .setPositiveButton(R.string.copy, (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("SHA-256", fingerprint));
                        displayShortToast(R.string.copied_to_clipboard);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    /**
     * NF-11 — show the package-visibility signal in a scrollable dialog with
     * three sections: holds-QUERY_ALL_PACKAGES, <queries> packages, and
     * <queries> intent actions. A "Find apps querying this" action kicks off
     * the O(N) inverse lookup on a background thread.
     */
    private void showPackageVisibilityDialog(@NonNull PackageVisibilityInfo visibility) {
        StringBuilder body = new StringBuilder();
        if (visibility.holdsQueryAllPackages) {
            body.append(getString(R.string.package_visibility_section_query_all)).append('\n');
        }
        if (!visibility.queriesPackages.isEmpty()) {
            if (body.length() > 0) body.append('\n');
            body.append(getString(R.string.package_visibility_section_queries_packages, visibility.queriesPackages.size()));
            for (String pkg : visibility.queriesPackages) {
                body.append("\n  • ").append(pkg);
            }
            body.append('\n');
        }
        if (!visibility.queriesIntentActions.isEmpty()) {
            if (body.length() > 0) body.append('\n');
            body.append(getString(R.string.package_visibility_section_queries_actions, visibility.queriesIntentActions.size()));
            for (String action : visibility.queriesIntentActions) {
                body.append("\n  • ").append(action);
            }
            body.append('\n');
        }
        if (body.length() == 0) {
            body.append(getString(R.string.package_visibility_no_signal));
        }
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.package_visibility_dialog_title)
                .setMessage(body.toString())
                .setNeutralButton(R.string.package_visibility_find_callers, (dialog, which) ->
                        findAndShowVisibilityCallers(mPackageName))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    /**
     * NF-17 — Runtime activity dialog. Collects last-24h usage snapshot for
     * the inspected package on a worker thread, then posts a scrollable
     * dialog with screen time, last-use, times-opened, and mobile / Wi-Fi
     * data totals.
     */
    private void showRuntimeTelemetryDialog() {
        if (mPackageName == null) return;
        final String packageName = mPackageName;
        final int userId = mUserId;
        final android.content.Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            RuntimeTelemetryHelper.Snapshot snapshot;
            try {
                snapshot = RuntimeTelemetryHelper.collectLast24h(packageName, userId);
            } catch (Throwable t) {
                ThreadUtils.postOnMainThread(() -> {
                    if (isAdded()) UIUtils.displayShortToast(R.string.runtime_telemetry_unavailable);
                });
                return;
            }
            String body = RuntimeTelemetryHelper.renderSummary(appContext, snapshot);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.runtime_telemetry_dialog_title)
                        .setMessage(body)
                        .setPositiveButton(R.string.close, null)
                        .show();
            });
        });
    }

    /** O(N) inverse lookup: find every installed app whose <queries> lists mPackageName. */
    private void findAndShowVisibilityCallers(@NonNull String targetPackageName) {
        Context appContext = requireContext().getApplicationContext();
        displayShortToast(R.string.package_visibility_searching_callers);
        ThreadUtils.postOnBackgroundThread(() -> {
            List<String> callers = PackageVisibilityInfo.findAppsQueryingPackage(
                    appContext.getPackageManager(), targetPackageName);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                StringBuilder body = new StringBuilder();
                if (callers.isEmpty()) {
                    body.append(getString(R.string.package_visibility_no_callers));
                } else {
                    body.append(getString(R.string.package_visibility_callers_header, callers.size()));
                    for (String pkg : callers) {
                        body.append("\n  • ").append(pkg);
                    }
                }
                new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.package_visibility_callers_title)
                        .setMessage(body.toString())
                        .setPositiveButton(R.string.close, null)
                        .show();
            });
        });
    }

    private void displayRunningServices(
            @NonNull List<ActivityManager.RunningServiceInfo> runningServices,
            @NonNull Context ctx) {
        showProgressIndicator(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            CharSequence[] runningServiceNames = new CharSequence[runningServices.size()];
            for (int i = 0; i < runningServiceNames.length; ++i) {
                ActivityManager.RunningServiceInfo serviceInfo = runningServices.get(i);
                String title = serviceInfo.service.getShortClassName();
                Spannable description = new SpannableStringBuilder()
                        .append(getStyledKeyValue(ctx, R.string.process_name, serviceInfo.process))
                        .append("\n")
                        .append(getStyledKeyValue(ctx, R.string.pid, String.valueOf(serviceInfo.pid)));
                runningServiceNames[i] = new SpannableStringBuilder()
                        .append(title)
                        .append("\n")
                        .append(getSmallerText(description));
            }
            boolean logViewerAvailable = FeatureController.isLogViewerEnabled()
                    && SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DUMP);
            DialogTitleBuilder titleBuilder = new DialogTitleBuilder(ctx)
                    .setTitle(R.string.running_services);
            if (logViewerAvailable) {
                titleBuilder.setSubtitle(R.string.running_services_logcat_hint);
            }
            ThreadUtils.postOnMainThread(() -> {
                if (isDetached()) return;
                showProgressIndicator(false);
                SearchableItemsDialogBuilder<CharSequence> builder = new SearchableItemsDialogBuilder<>(mActivity, runningServiceNames)
                        .setTitle(titleBuilder.build());
                if (logViewerAvailable) {
                    builder.setOnItemClickListener((dialog, which, item) -> {
                        Intent logViewerIntent = new Intent(mActivity.getApplicationContext(), LogViewerActivity.class)
                                .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + runningServices.get(which).pid)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mActivity.startActivity(logViewerIntent);
                    });
                }
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                    builder.setNeutralButton(R.string.force_stop, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            PackageManagerCompat.forceStopPackage(mPackageName, mUserId);
                        } catch (SecurityException e) {
                            Log.e(TAG, e);
                            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_stop, mAppLabel));
                        }
                    }));
                }
                builder.setNegativeButton(R.string.close, null);
                if (isDetached()) return;
                builder.show();
            });
        });
    }

    @UiThread
    private void displayMagiskHideDialog() {
        SearchableMultiChoiceDialogBuilder<MagiskProcess> builder;
        builder = getMagiskProcessDialog(mMagiskHiddenProcesses, (dialog, which, mp, isChecked) ->
                ThreadUtils.postOnBackgroundThread(() -> {
                    mp.setEnabled(isChecked);
                    if (MagiskHide.apply(mp, true)) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mUserId)) {
                            cb.setMagiskHide(mp);
                            mMainModel.getTagsAlteredLiveData().postValue(true);
                        }
                    } else {
                        mp.setEnabled(!isChecked);
                        ThreadUtils.postOnMainThread(() -> displayLongToast(isChecked ? R.string.failed_to_enable_magisk_hide
                                : R.string.failed_to_disable_magisk_hide));
                    }
                }));
        if (builder != null) {
            builder.setTitle(R.string.magisk_hide_enabled).show();
        }
    }

    @UiThread
    private void displayMagiskDenyListDialog() {
        SearchableMultiChoiceDialogBuilder<MagiskProcess> builder;
        builder = getMagiskProcessDialog(mMagiskDeniedProcesses, (dialog, which, mp, isChecked) ->
                ThreadUtils.postOnBackgroundThread(() -> {
                    mp.setEnabled(isChecked);
                    if (MagiskDenyList.apply(mp, true)) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mUserId)) {
                            cb.setMagiskDenyList(mp);
                            mMainModel.getTagsAlteredLiveData().postValue(true);
                        }
                    } else {
                        mp.setEnabled(!isChecked);
                        ThreadUtils.postOnMainThread(() -> displayLongToast(isChecked
                                ? R.string.failed_to_enable_magisk_deny_list
                                : R.string.failed_to_disable_magisk_deny_list));
                    }
                }));
        if (builder != null) {
            builder.setTitle(R.string.magisk_denylist).show();
        }
    }

    @Nullable
    public SearchableMultiChoiceDialogBuilder<MagiskProcess> getMagiskProcessDialog(
            @Nullable List<MagiskProcess> magiskProcesses,
            SearchableMultiChoiceDialogBuilder.OnMultiChoiceClickListener<MagiskProcess> multiChoiceClickListener) {
        if (magiskProcesses == null || magiskProcesses.isEmpty()) {
            return null;
        }
        List<Integer> selectedIndexes = new ArrayList<>();
        CharSequence[] processes = new CharSequence[magiskProcesses.size()];
        int i = 0;
        for (MagiskProcess mp : magiskProcesses) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (mp.isIsolatedProcess()) {
                sb.append("\n").append(UIUtils.getSecondaryText(mActivity, getString(R.string.isolated)));
                if (mp.isRunning()) {
                    sb.append(", ").append(UIUtils.getSecondaryText(mActivity, getString(R.string.running)));
                }
            } else if (mp.isRunning()) {
                sb.append("\n").append(UIUtils.getSecondaryText(mActivity, getString(R.string.running)));
            }
            processes[i] = new SpannableStringBuilder(mp.name).append(UIUtils.getSmallerText(sb));
            if (mp.isEnabled()) {
                selectedIndexes.add(i);
            }
            i++;
        }
        return new SearchableMultiChoiceDialogBuilder<>(mActivity, magiskProcesses, processes)
                .addSelections(ArrayUtils.convertToIntArray(selectedIndexes))
                .setTextSelectable(true)
                .setOnMultiChoiceClickListener(multiChoiceClickListener)
                .setNegativeButton(R.string.close, null);
    }

    @MainThread
    private void setupHorizontalActions() {
        if (mActionsFuture != null) {
            mActionsFuture.cancel(true);
        }
        mActionsFuture = ThreadUtils.postOnBackgroundThread(() -> {
            List<ActionItem> actionItems = getHorizontalActions();
            ThreadUtils.postOnMainThread(() -> {
                if (isDetached()) return;
                ++mLoadedItemCount;
                if (mLoadedItemCount >= 4) {
                    showProgressIndicator(false);
                }
                mHorizontalLayout.removeAllViews();
                for (ActionItem actionItem : actionItems) {
                    if (isDetached()) return;
                    mHorizontalLayout.addView(actionItem.toActionButton(mHorizontalLayout.getContext(), mHorizontalLayout));
                }
                if (isDetached()) return;
                View v = mHorizontalLayout.getChildAt(0);
                if (v != null) v.requestFocus();
            });
        });
    }

    @WorkerThread
    private List<ActionItem> getHorizontalActions() {
        Objects.requireNonNull(mMainModel);
        List<ActionItem> actionItems = new LinkedList<>();
        if (!mIsExternalApk && mIsDataOnlyPackage) {
            ActionItem clearDataAction = new ActionItem(AppInfoActionOrderResolver.ACTION_CLEAR_DATA,
                    R.string.clear_data, R.drawable.ic_clear_data);
            actionItems.add(clearDataAction);
            clearDataAction.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(mAppLabel)
                    .setMessage(getResources().getQuantityString(
                            R.plurals.clear_uninstalled_app_data_confirmation, 1, 1))
                    .setPositiveButton(R.string.clear, (dialog, which) -> ActionAuthGate.authenticate(mActivity,
                            R.string.authenticate_to_clear_data, this::clearDataOnlyPackage))
                    .setNegativeButton(R.string.cancel, null)
                    .show());
            return AppInfoActionOrderResolver.resolve(actionItems, Prefs.AppDetailsPage.getActionRailPriorityIds());
        }
        if (!mIsExternalApk) {
            boolean isStaticSharedLib = ApplicationInfoCompat.isStaticSharedLibrary(mApplicationInfo);
            boolean isFrozen = FreezeUtils.isFrozen(mApplicationInfo);
            boolean isHidden = ApplicationInfoCompat.isHidden(mApplicationInfo);
            boolean isArchived = AppArchiveManager.isArchived(mPackageInfo);
            boolean canFreeze = !isStaticSharedLib && SelfPermissions.canFreezeUnfreezePackages();
            boolean canHide = !isStaticSharedLib
                    && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS);
            // Set open
            Intent launchIntent = PackageManagerCompat.getLaunchIntentForPackage(mPackageName, mUserId);
            if (launchIntent != null && !isFrozen) {
                ActionItem launchAction = new ActionItem(AppInfoActionOrderResolver.ACTION_LAUNCH,
                        R.string.launch_app, R.drawable.ic_open_in_new);
                actionItems.add(launchAction);
                launchAction.setOnClickListener(v -> {
                    try {
                        ActivityManagerCompat.startActivity(launchIntent, mUserId);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
                    }
                });
            }
            // Set freeze/unfreeze
            if (canFreeze && !isFrozen) {
                ActionItem freezeAction = new ActionItem(AppInfoActionOrderResolver.ACTION_FREEZE,
                        R.string.freeze, R.drawable.ic_snowflake);
                actionItems.add(freezeAction);
                freezeAction.setOnClickListener(v -> {
                            if (BuildConfig.APPLICATION_ID.equals(mPackageName)) {
                                new MaterialAlertDialogBuilder(mActivity)
                                        .setMessage(R.string.are_you_sure)
                                        .setPositiveButton(R.string.yes, (d, w) -> freeze(true))
                                        .setNegativeButton(R.string.no, null)
                                        .show();
                            } else freeze(true);
                        })
                        .setOnLongClickListener(v -> {
                            createFreezeShortcut(false);
                            return true;
                        });
            }
            // Set hide/unhide using pm hide, independent of the saved freeze method.
            if (canHide) {
                ActionItem hideAction = new ActionItem(AppInfoActionOrderResolver.ACTION_HIDE,
                        isHidden ? R.string.quick_unhide_app : R.string.quick_hide_app,
                        isHidden ? R.drawable.ic_eye : R.drawable.ic_block);
                actionItems.add(hideAction);
                hideAction.setOnClickListener(v -> {
                    if (!isHidden && BuildConfig.APPLICATION_ID.equals(mPackageName)) {
                        new MaterialAlertDialogBuilder(mActivity)
                                .setMessage(R.string.hide_app_confirmation)
                                .setPositiveButton(R.string.yes, (d, w) -> hide(true))
                                .setNegativeButton(R.string.no, null)
                                .show();
                    } else hide(!isHidden);
                });
            }
            boolean isSystemOrUpdatedSystemApp = (mApplicationInfo.flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (AppArchiveManager.canShowArchiveAction(Build.VERSION.SDK_INT, mUserId, UserHandleHidden.myUserId(),
                    mIsExternalApk, isStaticSharedLib, isSystemOrUpdatedSystemApp, mPackageName)) {
                ActionItem archiveAction = new ActionItem(AppInfoActionOrderResolver.ACTION_ARCHIVE,
                        isArchived ? R.string.unarchive_app : R.string.archive_app,
                        R.drawable.ic_archive);
                actionItems.add(archiveAction);
                archiveAction.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(mAppLabel)
                        .setMessage(isArchived ? R.string.unarchive_app_message : R.string.archive_app_message)
                        .setPositiveButton(isArchived ? R.string.unarchive_app : R.string.archive_app,
                                (dialog, which) -> requestAppArchive(!isArchived))
                        .setNegativeButton(R.string.cancel, null)
                        .show());
            }
            // Set uninstall
            ActionItem uninstallAction = new ActionItem(AppInfoActionOrderResolver.ACTION_UNINSTALL,
                    R.string.uninstall, R.drawable.ic_trash_can);
            actionItems.add(uninstallAction);
            uninstallAction.setOnClickListener(v -> {
                if (mUserId != UserHandleHidden.myUserId() && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
                    // Could be for work profile
                    ActionAuthGate.authenticate(mActivity, R.string.authenticate_to_uninstall, () -> {
                        try {
                            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                            uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                            ActivityManagerCompat.startActivity(uninstallIntent, mUserId);
                            // TODO: 19/8/24 Watch for uninstallation
                        } catch (Throwable th) {
                            UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
                        }
                    });
                    return;
                }
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                ScrollableDialogBuilder builder = new ScrollableDialogBuilder(mActivity,
                        isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                        .setTitle(mAppLabel)
                        // FIXME: 16/6/23 Does it even work without INSTALL_PACKAGES?
                        .setCheckboxLabel(R.string.keep_data_and_app_signing_signatures)
                        .setPositiveButton(R.string.uninstall, (dialog, which, keepData) ->
                                ActionAuthGate.authenticate(mActivity, R.string.authenticate_to_uninstall,
                                        () -> ThreadUtils.postOnBackgroundThread(() -> {
                                            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                            installer.setAppLabel(mAppLabel);
                                            boolean uninstalled = installer.uninstall(mPackageName, mUserId, keepData);
                                            ThreadUtils.postOnMainThread(() -> {
                                                if (uninstalled) {
                                                    displayLongToast(R.string.uninstalled_successfully, mAppLabel);
                                                    mActivity.finish();
                                                } else {
                                                    displayLongToast(R.string.failed_to_uninstall, mAppLabel);
                                                }
                                            });
                                        })))
                        .setNegativeButton(R.string.cancel, (dialog, which, keepData) -> {
                            if (dialog != null) dialog.cancel();
                        });
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    builder.setNeutralButton(R.string.uninstall_updates, (dialog, which, keepData) ->
                            ActionAuthGate.authenticate(mActivity, R.string.authenticate_to_uninstall,
                                    () -> ThreadUtils.postOnBackgroundThread(() -> {
                                        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                        installer.setAppLabel(mAppLabel);
                                        boolean isSuccessful = installer.uninstall(mPackageName, UserHandleHidden.USER_ALL, keepData);
                                        if (isSuccessful) {
                                            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.update_uninstalled_successfully, mAppLabel));
                                        } else {
                                            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_uninstall_updates, mAppLabel));
                                        }
                                    })));
                }
                builder.show();
            });
            // Enable/disable app (root/ADB only)
            if (canFreeze && isFrozen) {
                // Enable app
                ActionItem unfreezeAction = new ActionItem(AppInfoActionOrderResolver.ACTION_FREEZE,
                        R.string.unfreeze, R.drawable.ic_snowflake_off);
                actionItems.add(unfreezeAction);
                unfreezeAction.setOnClickListener(v -> freeze(false))
                        .setOnLongClickListener(v -> {
                            createFreezeShortcut(true);
                            return true;
                        });
            }
            boolean accessibilityServiceRunning = UserHandleHidden.myUserId() == mUserId && ServiceHelper
                    .checkIfServiceIsRunning(mActivity, NoRootAccessibilityService.class);
            if (!isStaticSharedLib && (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
                    || accessibilityServiceRunning)) {
                // Force stop
                if (!ApplicationInfoCompat.isStopped(mApplicationInfo) &&
                        (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
                                || accessibilityServiceRunning)) {
                    ActionItem forceStopAction = new ActionItem(AppInfoActionOrderResolver.ACTION_FORCE_STOP,
                            R.string.force_stop, R.drawable.ic_power_settings);
                    actionItems.add(forceStopAction);
                    forceStopAction.setOnClickListener(v -> {
                        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                            ThreadUtils.postOnBackgroundThread(() -> {
                                try {
                                    PackageManagerCompat.forceStopPackage(mPackageName, mUserId);
                                } catch (SecurityException e) {
                                    Log.e(TAG, e);
                                    displayLongToast(R.string.failed_to_stop, mAppLabel);
                                }
                            });
                        } else {
                            // Use accessibility
                            AccessibilityMultiplexer.getInstance().enableForceStop(true);
                            mActivityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                    result -> {
                                        AccessibilityMultiplexer.getInstance().enableForceStop(false);
                                        refreshDetails();
                                    });
                        }
                    }).setOnLongClickListener(v -> {
                        showForceStopShortcutOptions();
                        return true;
                    });
                }
            }
            if (!isStaticSharedLib && (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)
                    || accessibilityServiceRunning)) {
                // Clear data
                ActionItem clearDataAction = new ActionItem(AppInfoActionOrderResolver.ACTION_CLEAR_DATA,
                        R.string.clear_data, R.drawable.ic_clear_data);
                actionItems.add(clearDataAction);
                clearDataAction.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(mAppLabel)
                        .setMessage(getClearDataConfirmationMessage())
                        .setPositiveButton(R.string.clear, (dialog, which) -> ActionAuthGate.authenticate(mActivity,
                                R.string.authenticate_to_clear_data, () -> {
                            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)) {
                                ThreadUtils.postOnBackgroundThread(() -> {
                                    boolean hadShizukuPermission = ShizukuBridge.hasPermission();
                                    boolean success = PackageManagerCompat
                                            .clearApplicationUserData(mPackageName, mUserId);
                                    boolean shizukuPermissionRevoked = success
                                            && ShizukuBridge.wasPermissionRevokedAfterClearData(hadShizukuPermission);
                                    ThreadUtils.postOnMainThread(() -> {
                                        if (success) {
                                            UIUtils.displayShortToast(R.string.done);
                                        } else UIUtils.displayShortToast(R.string.failed);
                                        if (shizukuPermissionRevoked) {
                                            showShizukuPermissionRevokedDialog();
                                        }
                                    });
                                });
                            } else {
                                // Use accessibility
                                AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                AccessibilityMultiplexer.getInstance().enableClearData(true);
                                mActivityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                        result -> {
                                            AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                            AccessibilityMultiplexer.getInstance().enableClearData(false);
                                            refreshDetails();
                                        });
                            }
                        }))
                        .setNegativeButton(R.string.cancel, null)
                        .show());
            }
            if (!isStaticSharedLib && (SelfPermissions.canClearAppCache() || accessibilityServiceRunning)) {
                // Clear cache
                ActionItem clearCacheAction = new ActionItem(AppInfoActionOrderResolver.ACTION_CLEAR_CACHE,
                        R.string.clear_cache, R.drawable.ic_clear_cache);
                actionItems.add(clearCacheAction);
                clearCacheAction.setOnClickListener(v -> {
                    if (SelfPermissions.canClearAppCache()) {
                        ThreadUtils.postOnBackgroundThread(() -> {
                            boolean success = PackageManagerCompat
                                    .deleteApplicationCacheFilesAsUser(mPackageName, mUserId);
                            ThreadUtils.postOnMainThread(() -> {
                                if (success) {
                                    UIUtils.displayShortToast(R.string.done);
                                } else UIUtils.displayShortToast(R.string.failed);
                            });
                        });
                    } else {
                        // Use accessibility
                        AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                        AccessibilityMultiplexer.getInstance().enableClearCache(true);
                        mActivityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                result -> {
                                    AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(false);
                                    AccessibilityMultiplexer.getInstance().enableClearCache(false);
                                    refreshDetails();
                                });
                    }
                }).setOnLongClickListener(v -> {
                    createAppActionShortcut(AppActionShortcutInfo.ACTION_CLEAR_CACHE,
                            R.string.shortcut_clear_cache_app, R.drawable.ic_clear_cache);
                    return true;
                });
            } else {
                // Display Android settings button
                ActionItem settingAction = new ActionItem(AppInfoActionOrderResolver.ACTION_SETTINGS,
                        R.string.view_in_settings, R.drawable.ic_settings);
                actionItems.add(settingAction);
                settingAction.setOnClickListener(v -> {
                    try {
                        ActivityManagerCompat.startActivity(IntentUtils.getAppDetailsSettings(mPackageName), mUserId);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
                    }
                });
            }
        } else if (FeatureController.isInstallerEnabled()) {
            if (mInstalledPackageInfo == null) {
                // App not installed
                ActionItem installAction = new ActionItem(AppInfoActionOrderResolver.ACTION_INSTALL,
                        R.string.install, R.drawable.ic_get_app);
                actionItems.add(installAction);
                installAction.setOnClickListener(v -> install());
            } else {
                // App is installed
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo);
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
                if (installedVersionCode < thisVersionCode) {
                    // Needs update
                    ActionItem whatsNewAction = new ActionItem(AppInfoActionOrderResolver.ACTION_WHATS_NEW,
                            R.string.whats_new, io.github.muntashirakon.ui.R.drawable.ic_information);
                    actionItems.add(whatsNewAction);
                    whatsNewAction.setOnClickListener(v -> {
                        WhatsNewDialogFragment dialogFragment = WhatsNewDialogFragment
                                .getInstance(mPackageInfo, mInstalledPackageInfo);
                        dialogFragment.show(getChildFragmentManager(), WhatsNewDialogFragment.TAG);
                    });
                    ActionItem updateAction = new ActionItem(AppInfoActionOrderResolver.ACTION_INSTALL,
                            R.string.update, R.drawable.ic_get_app);
                    actionItems.add(updateAction);
                    updateAction.setOnClickListener(v -> install());
                } else if (installedVersionCode == thisVersionCode) {
                    // Needs reinstall
                    ActionItem reinstallAction = new ActionItem(AppInfoActionOrderResolver.ACTION_INSTALL,
                            R.string.reinstall, R.drawable.ic_get_app);
                    actionItems.add(reinstallAction);
                    reinstallAction.setOnClickListener(v -> install());
                } else if (SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
                    // Needs downgrade
                    ActionItem downgradeAction = new ActionItem(AppInfoActionOrderResolver.ACTION_INSTALL,
                            R.string.downgrade, R.drawable.ic_get_app);
                    actionItems.add(downgradeAction);
                    downgradeAction.setOnClickListener(v -> install());
                }
            }
        }
        // Set manifest
        if (FeatureController.isManifestEnabled()) {
            ActionItem manifestAction = new ActionItem(AppInfoActionOrderResolver.ACTION_MANIFEST,
                    R.string.manifest, R.drawable.ic_package);
            actionItems.add(manifestAction);
            manifestAction.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
                startActivityForSplit(intent);
            });
        }
        // Set scanner
        if (FeatureController.isScannerEnabled()) {
            ActionItem scannerAction = new ActionItem(AppInfoActionOrderResolver.ACTION_SCANNER,
                    R.string.scanner, R.drawable.ic_security);
            actionItems.add(scannerAction);
            scannerAction.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ScannerActivity.class);
                intent.putExtra(ScannerActivity.EXTRA_IS_EXTERNAL, mIsExternalApk);
                startActivityForSplit(intent);
            });
        }
        // Root only features
        if (!mIsExternalApk) {
            // Shared prefs (root only)
            final List<Path> sharedPrefs = new ArrayList<>();
            Path[] tmpPaths = getSharedPrefs(mApplicationInfo.dataDir);
            if (tmpPaths != null) sharedPrefs.addAll(Arrays.asList(tmpPaths));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tmpPaths = getSharedPrefs(mApplicationInfo.deviceProtectedDataDir);
                if (tmpPaths != null) sharedPrefs.addAll(Arrays.asList(tmpPaths));
            }
            if (!sharedPrefs.isEmpty()) {
                CharSequence[] sharedPrefNames = new CharSequence[sharedPrefs.size()];
                for (int i = 0; i < sharedPrefs.size(); ++i) {
                    sharedPrefNames[i] = sharedPrefs.get(i).getName();
                }
                ActionItem sharedPrefsAction = new ActionItem(AppInfoActionOrderResolver.ACTION_SHARED_PREFS,
                        R.string.shared_prefs, R.drawable.ic_view_list);
                actionItems.add(sharedPrefsAction);
                sharedPrefsAction.setOnClickListener(v -> new SearchableItemsDialogBuilder<>(mActivity, sharedPrefNames)
                        .setTitle(R.string.shared_prefs)
                        .setOnItemClickListener((dialog, which, item) -> {
                            Intent intent = new Intent(mActivity, SharedPrefsActivity.class);
                            intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which).getUri());
                            intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, mAppLabel);
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.ok, null)
                        .show());
            }
            // Databases (root only)
            final List<Path> databases = new ArrayList<>();
            tmpPaths = getDatabases(mApplicationInfo.dataDir);
            if (tmpPaths != null) databases.addAll(Arrays.asList(tmpPaths));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tmpPaths = getDatabases(mApplicationInfo.deviceProtectedDataDir);
                if (tmpPaths != null) databases.addAll(Arrays.asList(tmpPaths));
            }
            if (!databases.isEmpty()) {
                CharSequence[] databases2 = new CharSequence[databases.size()];
                for (int i = 0; i < databases.size(); ++i) {
                    databases2[i] = databases.get(i).getName();
                }
                ActionItem dbAction = new ActionItem(AppInfoActionOrderResolver.ACTION_DATABASES,
                        R.string.databases, R.drawable.ic_database);
                actionItems.add(dbAction);
                dbAction.setOnClickListener(v -> new SearchableItemsDialogBuilder<>(v.getContext(), databases2)
                        .setTitle(R.string.databases)
                        .setOnItemClickListener((dialog, which, item) -> ThreadUtils.postOnBackgroundThread(() -> {
                            // Vacuum database
                            Runner.runCommand(new String[]{"sqlite3", databases.get(which).getFilePath(), "vacuum"});
                            ThreadUtils.postOnMainThread(() -> {
                                OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(databases.get(which), "application/vnd.sqlite3");
                                if (isDetached()) return;
                                fragment.show(getChildFragmentManager(), OpenWithDialogFragment.TAG);
                            });
                        }))
                        .setNegativeButton(R.string.close, null)
                        .show());
            }
        }  // End root only features
        // Set F-Droid
        Intent fdroidIntent = new Intent(Intent.ACTION_VIEW);
        fdroidIntent.setData(Uri.parse("https://f-droid.org/packages/" + mPackageName));
        List<ResolveInfo> resolvedActivities = mPackageManager.queryIntentActivities(fdroidIntent, 0);
        if (!resolvedActivities.isEmpty()) {
            ActionItem fdroidItem = new ActionItem(AppInfoActionOrderResolver.ACTION_FDROID,
                    R.string.fdroid, R.drawable.ic_frost_fdroid);
            actionItems.add(fdroidItem);
            fdroidItem.setOnClickListener(v -> {
                try {
                    startActivity(fdroidIntent);
                } catch (Exception ignored) {
                }
            });
        }
        // Set Aurora Store
        try {
            PackageInfo auroraInfo = mPackageManager.getPackageInfo(PACKAGE_NAME_AURORA_STORE, 0);
            if (PackageInfoCompat.getLongVersionCode(auroraInfo) == 36L || !auroraInfo.applicationInfo.enabled) {
                // Aurora Store is disabled or the installed version has promotional apps
                throw new PackageManager.NameNotFoundException();
            }
            ActionItem auroraStoreAction = new ActionItem(AppInfoActionOrderResolver.ACTION_AURORA_STORE,
                    R.string.open_in_aurora_store, R.drawable.ic_frost_aurorastore);
            actionItems.add(auroraStoreAction);
            auroraStoreAction.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setPackage(PACKAGE_NAME_AURORA_STORE);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + mPackageName));
                try {
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return AppInfoActionOrderResolver.resolve(actionItems, Prefs.AppDetailsPage.getActionRailPriorityIds());
    }

    private void clearDataOnlyPackage() {
        ThreadUtils.postOnBackgroundThread(() -> {
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            installer.setAppLabel(mAppLabel);
            boolean uninstalled = installer.uninstall(mPackageName, mUserId, false);
            ThreadUtils.postOnMainThread(() -> {
                if (uninstalled) {
                    displayLongToast(R.string.uninstalled_successfully, mAppLabel);
                    mActivity.finish();
                } else {
                    displayLongToast(R.string.failed_to_uninstall, mAppLabel);
                }
            });
        });
    }

    private void requestAppArchive(boolean archive) {
        String packageName = mPackageName;
        CharSequence appLabel = mAppLabel;
        Context appContext = mActivity.getApplicationContext();
        @AppArchiveManager.Operation int operation = archive
                ? AppArchiveManager.OP_ARCHIVE
                : AppArchiveManager.OP_UNARCHIVE;
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    AppArchiveManager.request(appContext, packageName, appLabel, operation);
                }
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(archive
                        ? R.string.archive_app_requested
                        : R.string.unarchive_app_requested, appLabel));
            } catch (Throwable th) {
                Log.e(TAG, "Could not request app archive operation.", th);
                ThreadUtils.postOnMainThread(() -> displayLongToast(archive
                        ? R.string.failed_to_archive_app
                        : R.string.failed_to_unarchive_app, appLabel));
            }
        });
    }

    @NonNull
    private String getClearDataConfirmationMessage() {
        String message = getString(R.string.clear_data_message);
        @StringRes int warning = ShizukuBridge.getClearDataAuthorizationWarning(requireContext(), mPackageName,
                mPackageInfo);
        if (warning == 0) {
            return message;
        }
        return message + "\n\n" + getString(warning);
    }

    private void showShizukuPermissionRevokedDialog() {
        if (isDetached()) {
            return;
        }
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.shizuku_permission_revoked_after_clear_data_title)
                .setMessage(R.string.shizuku_permission_revoked_after_clear_data_message)
                .setPositiveButton(R.string.pref_mode_of_operations, (dialog, which) ->
                        startActivity(SettingsActivity.getSettingsIntent(mActivity, "mode_of_operations")))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @UiThread
    private void startActivityForSplit(Intent intent) {
        if (mMainModel == null) return;
        ApkFile apkFile = mMainModel.getApkFile();
        if (apkFile != null && apkFile.isSplit()) {
            // Display a list of apks
            List<ApkFile.Entry> apkEntries = apkFile.getEntries();
            CharSequence[] entryNames = new CharSequence[apkEntries.size()];
            for (int i = 0; i < apkEntries.size(); ++i) {
                entryNames[i] = apkEntries.get(i).toShortLocalizedString(requireActivity());
            }
            new SearchableItemsDialogBuilder<>(mActivity, entryNames)
                    .setTitle(R.string.select_apk)
                    .setOnItemClickListener((dialog, which, item) -> ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            File file = apkEntries.get(which).getFile(false);
                            intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension("apk"));
                            ThreadUtils.postOnMainThread(() -> {
                                if (isDetached()) return;
                                startActivity(intent);
                            });
                        } catch (IOException e) {
                            UIUtils.displayLongToast("Error: " + e.getMessage());
                        }
                    }))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            // Open directly
            File file = new File(mApplicationInfo.publicSourceDir);
            intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
            startActivity(intent);
        }
    }

    @GuardedBy("mListItems")
    private void setAppIdentity(@NonNull AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            mListItems.add(ListItem.newGroupStart(getString(R.string.app_identity)));
            mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.sdk), getSdkSummary()));

            ListItem sdkRuntimeItem = ListItem.newSelectableRegularItem(getString(R.string.sdk_sandbox_dialog_title),
                    getSdkSandboxSummary(appInfo.sdkSandboxInfo),
                    v -> showSdkSandboxDialog(v.getContext(), appInfo.sdkSandboxInfo));
            sdkRuntimeItem.setActionIcon(R.drawable.ic_information_circle);
            sdkRuntimeItem.setActionContentDescription(R.string.more_info);
            mListItems.add(sdkRuntimeItem);

            String signingCertSha256 = appInfo.signingCertSha256;
            if (signingCertSha256 != null) {
                ListItem certItem = ListItem.newSelectableRegularItem(
                        getString(R.string.app_info_signing_certificate),
                        getSigningCertificateSummary(appInfo),
                        v -> showCertFingerprintDialog(v.getContext(), signingCertSha256,
                                appInfo.signingCertSubject, appInfo.signingCertIssuer));
                certItem.setActionIcon(R.drawable.ic_information_circle);
                certItem.setActionContentDescription(R.string.more_info);
                mListItems.add(certItem);
            }
        }
    }

    @NonNull
    private CharSequence getSdkSummary() {
        StringBuilder sdk = new StringBuilder();
        sdk.append(getString(R.string.sdk_max)).append(LangUtils.getSeparatorString()).append(String.format(Locale.getDefault(), "%d",
                mApplicationInfo.targetSdkVersion));
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            sdk.append(", ").append(getString(R.string.sdk_min)).append(LangUtils.getSeparatorString())
                    .append(String.format(Locale.getDefault(), "%d", mApplicationInfo.minSdkVersion));
        }
        return sdk;
    }

    @NonNull
    private CharSequence getSdkSandboxSummary(@NonNull SdkSandboxInfo info) {
        if (info.hasDeclaredSdkLibraries()) {
            int sdkLibraryCount = info.declaredSdkLibraries.size();
            return getResources().getQuantityString(R.plurals.sdk_sandbox_chip_count,
                    sdkLibraryCount, sdkLibraryCount);
        }
        return getString(info.isSupported()
                ? R.string.sdk_sandbox_chip_none
                : R.string.sdk_sandbox_chip_unsupported);
    }

    @NonNull
    private CharSequence getSigningCertificateSummary(@NonNull AppInfoViewModel.AppInfo appInfo) {
        String fingerprint = Objects.requireNonNull(appInfo.signingCertSha256);
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.cert_fingerprint_dialog_sha256_header))
                .append(LangUtils.getSeparatorString())
                .append(shortFingerprint(fingerprint));
        if (appInfo.signingCertSubject != null) {
            builder.append('\n')
                    .append(getString(R.string.cert_fingerprint_dialog_subject_header))
                    .append(LangUtils.getSeparatorString())
                    .append(appInfo.signingCertSubject);
        }
        if (appInfo.signingCertIssuer != null) {
            builder.append('\n')
                    .append(getString(R.string.cert_fingerprint_dialog_issuer_header))
                    .append(LangUtils.getSeparatorString())
                    .append(appInfo.signingCertIssuer);
        }
        return builder;
    }

    @GuardedBy("mListItems")
    private void setPathsAndDirectories(@NonNull AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Paths and directories
            mListItems.add(ListItem.newGroupStart(getString(R.string.paths_and_directories)));
            // Source directory (apk path)
            if (appInfo.sourceDir != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.source_dir),
                        appInfo.sourceDir, openAsFolderInFM(requireContext(), appInfo.sourceDir));
                listItem.setActionContentDescription(R.string.open);
                mListItems.add(listItem);
            }
            // Data dir
            if (appInfo.dataDir != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.data_dir),
                        appInfo.dataDir, openAsFolderInFM(requireContext(), appInfo.dataDir));
                listItem.setActionContentDescription(R.string.open);
                mListItems.add(listItem);
            }
            // Device-protected data dir
            if (appInfo.dataDeDir != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.dev_protected_data_dir),
                        appInfo.dataDeDir, openAsFolderInFM(requireContext(), appInfo.dataDeDir));
                listItem.setActionContentDescription(R.string.open);
                mListItems.add(listItem);
            }
            // External data dirs
            if (appInfo.extDataDirs.size() == 1) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.external_data_dir),
                        appInfo.extDataDirs.get(0), openAsFolderInFM(requireContext(),
                                appInfo.extDataDirs.get(0)));
                listItem.setActionContentDescription(R.string.open);
                mListItems.add(listItem);
            } else {
                for (int i = 0; i < appInfo.extDataDirs.size(); ++i) {
                    ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.external_multiple_data_dir, i),
                            appInfo.extDataDirs.get(i), openAsFolderInFM(requireContext(),
                                    appInfo.extDataDirs.get(i)));
                    listItem.setActionContentDescription(R.string.open);
                    mListItems.add(listItem);
                }
            }
            // Native JNI library dir
            if (appInfo.jniDir != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.native_library_dir), appInfo.jniDir,
                        openAsFolderInFM(requireContext(), appInfo.jniDir));
                listItem.setActionContentDescription(R.string.open);
                mListItems.add(listItem);
            }
        }
    }

    @GuardedBy("mListItems")
    private void setMoreInfo(AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Set more info
            mListItems.add(ListItem.newGroupStart(getString(R.string.more_info)));

            // Set installed version info
            if (mIsExternalApk && mInstalledPackageInfo != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.installed_version),
                        getString(R.string.version_name_with_code, mInstalledPackageInfo.versionName,
                                PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo)), v -> {
                            Intent intent = AppDetailsActivity.getIntent(mActivity, mPackageName,
                                    UserHandleHidden.myUserId());
                            mActivity.startActivity(intent);
                        });
                listItem.setActionIcon(io.github.muntashirakon.ui.R.drawable.ic_information);
                listItem.setActionContentDescription(R.string.app_info);
                mListItems.add(listItem);
            }

            // Set Flags
            final StringBuilder flags = new StringBuilder();
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                flags.append("FLAG_DEBUGGABLE");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_TEST_ONLY");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_MULTIARCH) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_MULTIARCH");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_HARDWARE_ACCELERATED");

            if (flags.length() != 0) {
                ListItem flagsItem = ListItem.newSelectableRegularItem(getString(R.string.sdk_flags), flags.toString());
                flagsItem.setMonospace(true);
                mListItems.add(flagsItem);
            }
            if (mIsExternalApk) return;

            mListItems.add(ListItem.newRegularItem(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime)));
            mListItems.add(ListItem.newRegularItem(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime)));
            if (!mPackageName.equals(mApplicationInfo.processName)) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.process_name), mApplicationInfo.processName));
            }
            if (appInfo.installerApp != null) {
                ListItem installerItem = ListItem.newSelectableRegularItem(
                        getString(R.string.installer_app), appInfo.installerApp,
                        v -> displayInstallerDialog(Objects.requireNonNull(appInfo.installSource)));
                installerItem.setActionIcon(R.drawable.ic_information_circle);
                installerItem.setActionContentDescription(R.string.more_info);
                mListItems.add(installerItem);
            }
            mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.user_id), String.format(Locale.getDefault(), "%d",
                    mApplicationInfo.uid)));
            if (mPackageInfo.sharedUserId != null)
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.shared_user_id), mPackageInfo.sharedUserId));
            addApplicationLocaleInfo(appInfo);
            if (appInfo.primaryCpuAbi != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.primary_abi),
                        appInfo.primaryCpuAbi));
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.device_page_size),
                        getDevicePageSizeLabel()));
            }
            if (appInfo.zygotePreloadName != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.zygote_preload_name),
                        appInfo.zygotePreloadName));
            }
            if (!mIsExternalApk) {
                mListItems.add(ListItem.newRegularItem(getString(R.string.hidden_api_enforcement_policy),
                        getHiddenApiEnforcementPolicy(appInfo.hiddenApiEnforcementPolicy)));
            }
            addSelinuxInfo(appInfo);
            // Main activity
            if (appInfo.mainActivity != null) {
                final ComponentName launchComponentName = appInfo.mainActivity.getComponent();
                if (launchComponentName != null) {
                    final String mainActivity = launchComponentName.getClassName();
                    ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.main_activity),
                            mainActivity, view -> startActivity(appInfo.mainActivity));
                    listItem.setActionContentDescription(R.string.open);
                    mListItems.add(listItem);
                }
            }
        }
    }

    @GuardedBy("mListItems")
    private void addApplicationLocaleInfo(@NonNull AppInfoViewModel.AppInfo appInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !AppLocaleManagerCompat.canReadApplicationLocales()) {
            return;
        }
        CharSequence localeSummary = AppLocaleOptions.describeLanguageTags(appInfo.applicationLocaleTags,
                getString(R.string.system_default), Locale.getDefault());
        ListItem localeItem;
        if (AppLocaleManagerCompat.canSetApplicationLocales()) {
            localeItem = ListItem.newSelectableRegularItem(getString(R.string.app_language), localeSummary,
                    v -> showAppLocalePicker(appInfo.applicationLocaleTags));
            localeItem.setActionIcon(R.drawable.ic_translate);
            localeItem.setActionContentDescription(R.string.change_app_language);
        } else {
            localeItem = ListItem.newSelectableRegularItem(getString(R.string.app_language), localeSummary);
        }
        mListItems.add(localeItem);
    }

    @MainThread
    private void showAppLocalePicker(@Nullable String currentLocaleTags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !AppLocaleManagerCompat.canSetApplicationLocales()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
            return;
        }
        List<AppLocaleOptions.Option> options = AppLocaleOptions.buildOptions(Locale.getAvailableLocales(),
                Locale.getDefault(), getString(R.string.system_default));
        CharSequence[] languageLabels = new CharSequence[options.size()];
        for (int i = 0; i < options.size(); ++i) {
            languageLabels[i] = options.get(i).label;
        }
        new SearchableItemsDialogBuilder<>(mActivity, languageLabels)
                .setTitle(R.string.change_app_language)
                .setOnItemClickListener((dialog, which, item) -> {
                    dialog.dismiss();
                    String languageTag = options.get(which).languageTag;
                    String currentLanguageTags = currentLocaleTags != null ? currentLocaleTags.trim() : "";
                    if (!languageTag.equals(currentLanguageTags)) {
                        setAppLocale(languageTag);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @MainThread
    private void setAppLocale(@Nullable String languageTags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                AppLocaleManagerCompat.setApplicationLocaleTags(mPackageName, mUserId, languageTags);
                ThreadUtils.postOnMainThread(() -> {
                    UIUtils.displayShortToast(R.string.done);
                    refreshDetails();
                });
            } catch (Throwable th) {
                Log.e(TAG, th);
                ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_set_app_language, mAppLabel));
            }
        });
    }

    @GuardedBy("mListItems")
    private void addSelinuxInfo(@NonNull AppInfoViewModel.AppInfo appInfo) {
        if (appInfo.seInfo != null) {
            mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.selinux_policy_info),
                    appInfo.seInfo));
        }
        CharSequence fileContexts = getSelinuxFileContexts(appInfo);
        if (fileContexts != null) {
            ListItem fileContextsItem = ListItem.newSelectableRegularItem(getString(R.string.selinux_file_contexts),
                    fileContexts);
            fileContextsItem.setMonospace(true);
            mListItems.add(fileContextsItem);
        }
        CharSequence processContexts = getSelinuxProcessContexts(appInfo);
        if (processContexts != null) {
            ListItem processContextsItem = ListItem.newSelectableRegularItem(getString(R.string.selinux_process_contexts),
                    processContexts);
            processContextsItem.setMonospace(true);
            mListItems.add(processContextsItem);
        }
    }

    @Nullable
    private CharSequence getSelinuxFileContexts(@NonNull AppInfoViewModel.AppInfo appInfo) {
        StringBuilder fileContexts = new StringBuilder();
        appendSelinuxContext(fileContexts, getString(R.string.data_dir), appInfo.dataDirSelinuxContext);
        appendSelinuxContext(fileContexts, getString(R.string.source_dir), appInfo.sourceFileSelinuxContext);
        return fileContexts.length() == 0 ? null : fileContexts;
    }

    @Nullable
    private CharSequence getSelinuxProcessContexts(@NonNull AppInfoViewModel.AppInfo appInfo) {
        if (appInfo.processSelinuxContexts.isEmpty()) {
            return null;
        }
        StringBuilder processContexts = new StringBuilder();
        for (AppSelinuxContexts.ProcessContext processContext : appInfo.processSelinuxContexts) {
            if (processContexts.length() > 0) {
                processContexts.append('\n');
            }
            processContexts.append(processContext.processName)
                    .append(" (")
                    .append(processContext.pid)
                    .append("): ")
                    .append(processContext.context);
        }
        return processContexts;
    }

    private static void appendSelinuxContext(@NonNull StringBuilder builder, @NonNull CharSequence label,
                                             @Nullable String context) {
        if (context == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(context);
    }

    @NonNull
    private String getDevicePageSizeLabel() {
        long pageSize;
        try {
            pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
        } catch (Throwable t) {
            return getString(R.string.device_page_size_unknown);
        }
        if (pageSize == 16384L) {
            return getString(R.string.device_page_size_16k);
        }
        if (pageSize == 4096L) {
            return getString(R.string.device_page_size_4k);
        }
        return getString(R.string.device_page_size_other, pageSize);
    }

    @NonNull
    private String getHiddenApiEnforcementPolicy(int policy) {
        switch (policy) {
            case HIDDEN_API_ENFORCEMENT_DEFAULT:
                return getString(R.string.hidden_api_enf_default_policy);
            default:
            case HIDDEN_API_ENFORCEMENT_DISABLED:
                return getString(R.string.hidden_api_enf_policy_none);
            case HIDDEN_API_ENFORCEMENT_JUST_WARN:
                return getString(R.string.hidden_api_enf_policy_warn);
            case HIDDEN_API_ENFORCEMENT_ENABLED:
                return getString(R.string.hidden_api_enf_policy_dark_grey_and_black);
            case HIDDEN_API_ENFORCEMENT_BLACK:
                return getString(R.string.hidden_api_enf_policy_black);
        }
    }

    private void setDataUsage(@NonNull AppInfoViewModel.AppInfo appInfo) {
        AppUsageStatsManager.DataUsage dataUsage = appInfo.dataUsage;
        if (dataUsage == null) {
            // No permission
            return;
        }
        // Hide data usage if:
        // 1. OS is Android 6.0 onwards, AND
        // 2. The user is not the current user, AND
        // 3. Remote UID is not system UID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && mUserId != UserHandleHidden.myUserId()
                && !SelfPermissions.isSystem()) {
            return;
        }
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.newGroupStart(getString(R.string.data_usage_msg)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_transmitted), getReadableSize(dataUsage.getTx())));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_received), getReadableSize(dataUsage.getRx())));
        }
    }

    @MainThread
    @GuardedBy("mListItems")
    private void setupVerticalView(AppInfoViewModel.AppInfo appInfo) {
        if (mListFuture != null) mListFuture.cancel(true);
        mListFuture = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mListItems) {
                mListItems.clear();
                setAppIdentity(appInfo);
                if (!mIsExternalApk) {
                    setPathsAndDirectories(appInfo);
                    setDataUsage(appInfo);
                    // Storage and Cache
                    if (FeatureController.isUsageAccessEnabled()) {
                        setStorageAndCache(appInfo);
                    }
                }
                setMoreInfo(appInfo);
                ThreadUtils.postOnMainThread(() -> {
                    if (isDetached()) return;
                    ++mLoadedItemCount;
                    if (mLoadedItemCount >= 4) {
                        showProgressIndicator(false);
                    }
                    if (isDetached()) return;
                    mAdapter.setAdapterList(mListItems);
                });
            }
        });
    }

    @Nullable
    private Path[] getSharedPrefs(@Nullable String sourceDir) {
        if (sourceDir == null) return null;
        try {
            Path sharedPath = Paths.get(sourceDir).findFile("shared_prefs");
            return sharedPath.listFiles();
        } catch (FileNotFoundException e) {
            return null;
        }

    }

    @Nullable
    private Path[] getDatabases(@Nullable String sourceDir) {
        if (sourceDir == null) return null;
        try {
            Path sharedPath = Paths.get(sourceDir).findFile("databases");
            return sharedPath.listFiles((dir, name) -> !(name.endsWith("-journal")
                    || name.endsWith("-wal") || name.endsWith("-shm")));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @GuardedBy("mListItems")
    private void setStorageAndCache(AppInfoViewModel.AppInfo appInfo) {
        if (FeatureController.isUsageAccessEnabled()) {
            // Grant optional READ_PHONE_STATE permission
            if (AppUsageStatsManager.requireReadPhoneStatePermission()) {
                ThreadUtils.postOnMainThread(() -> mRequestPerm.launch(Manifest.permission.READ_PHONE_STATE, granted -> {
                    if (granted) {
                        mAppInfoModel.loadAppInfo(mPackageInfo, mIsExternalApk);
                    }
                }));
            }
        }
        if (!SelfPermissions.checkUsageStatsPermission()) {
            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.grant_usage_access)
                    .setMessage(R.string.grant_usage_acess_message)
                    .setPositiveButton(R.string.go, (dialog, which) -> {
                        try {
                            mActivityLauncher.launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), result -> {
                                if (SelfPermissions.checkUsageStatsPermission()) {
                                    FeatureController.getInstance().modifyState(FeatureController
                                            .FEAT_USAGE_ACCESS, true);
                                    // Reload app info
                                    mAppInfoModel.loadAppInfo(mPackageInfo, mIsExternalApk);
                                }
                            });
                        } catch (SecurityException ignore) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.never_ask, (dialog, which) -> FeatureController.getInstance().modifyState(
                            FeatureController.FEAT_USAGE_ACCESS, false))
                    .setCancelable(false)
                    .show());
            return;
        }
        PackageSizeInfo sizeInfo = appInfo.sizeInfo;
        if (sizeInfo == null) return;
        synchronized (mListItems) {
            mListItems.add(ListItem.newGroupStart(getString(R.string.storage_and_cache)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.app_size), getReadableSize(sizeInfo.codeSize)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_size), getReadableSize(sizeInfo.dataSize)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.cache_size), getReadableSize(sizeInfo.cacheSize)));
            if (sizeInfo.obbSize != 0) {
                mListItems.add(ListItem.newInlineItem(getString(R.string.obb_size), getReadableSize(sizeInfo.obbSize)));
            }
            if (sizeInfo.mediaSize != 0) {
                mListItems.add(ListItem.newInlineItem(getString(R.string.media_size), getReadableSize(sizeInfo.mediaSize)));
            }
            mListItems.add(ListItem.newInlineItem(getString(R.string.total_size), getReadableSize(sizeInfo.getTotalSize())));
        }
    }

    @MainThread
    private void freeze(boolean freeze) {
        if (mMainModel == null) return;
        if (freeze) {
            mMainModel.loadFreezeType();
        } else {
            // Unfreeze
            ThreadUtils.postOnBackgroundThread(this::doUnfreeze);
        }
    }

    private void showFreezeDialog(int freezeType, boolean isCustom) {
        View view = View.inflate(mActivity, R.layout.item_checkbox, null);
        MaterialCheckBox checkBox = view.findViewById(R.id.checkbox);
        checkBox.setText(R.string.remember_option_for_this_app);
        checkBox.setChecked(isCustom);
        FreezeUnfreeze.getFreezeDialog(mActivity, freezeType)
                .setIcon(R.drawable.ic_snowflake)
                .setTitle(R.string.freeze)
                .setView(view)
                .setPositiveButton(R.string.freeze, (dialog, which, selectedItem) -> {
                    if (selectedItem == null) {
                        return;
                    }
                    ThreadUtils.postOnBackgroundThread(() -> doFreeze(selectedItem, checkBox.isChecked()));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @WorkerThread
    private void recordFreezeHistory(boolean freeze, boolean success, @Nullable Throwable failure) {
        try {
            Context context = mActivity != null ? mActivity : ContextUtils.getContext();
            String operationLabel = context.getString(freeze ? R.string.freeze : R.string.unfreeze);
            SingleAppActionHistoryItem item = new SingleAppActionHistoryItem(
                    freeze ? SingleAppActionHistoryItem.ACTION_FREEZE : SingleAppActionHistoryItem.ACTION_UNFREEZE,
                    operationLabel,
                    mPackageName,
                    mUserId,
                    operationLabel,
                    null);
            OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, item, success,
                    OperationJournalMetadata.forSingleAppAction(context, item, success,
                            OperationJournalMetadata.RISK_MEDIUM, true, failure));
        } catch (Throwable th) {
            Log.e(TAG, "Could not record single-app operation history.", th);
        }
    }

    @WorkerThread
    private void doFreeze(@FreezeUtils.FreezeMethod int freezeType, boolean remember) {
        try {
            if (remember) {
                FreezeUtils.storeFreezeMethod(mPackageName, freezeType);
            } else {
                FreezeUtils.deleteFreezeMethod(mPackageName);
            }
            FreezeUtils.freeze(mPackageName, mUserId, freezeType);
            recordFreezeHistory(true, true, null);
        } catch (Throwable th) {
            Log.e(TAG, th);
            recordFreezeHistory(true, false, th);
            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_freeze, mAppLabel));
        }
    }

    @WorkerThread
    private void doUnfreeze() {
        try {
            FreezeUtils.unfreeze(mPackageName, mUserId);
            recordFreezeHistory(false, true, null);
        } catch (Throwable th) {
            Log.e(TAG, th);
            recordFreezeHistory(false, false, th);
            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_unfreeze, mAppLabel));
        }
    }

    @MainThread
    private void hide(boolean hide) {
        ThreadUtils.postOnBackgroundThread(() -> doHide(hide));
    }

    @WorkerThread
    private void doHide(boolean hide) {
        try {
            PackageManagerCompat.hidePackage(mPackageName, mUserId, hide);
            ThreadUtils.postOnMainThread(() -> {
                UIUtils.displayShortToast(R.string.done);
                refreshDetails();
            });
        } catch (Throwable th) {
            Log.e(TAG, th);
            ThreadUtils.postOnMainThread(() -> displayLongToast(hide
                    ? R.string.failed_to_hide
                    : R.string.failed_to_unhide, mAppLabel));
        }
    }

    private void createFreezeShortcut(boolean isFrozen) {
        if (mMainModel == null) return;
        List<Integer> allFlags = new ArrayList<>(3);
        for (int i = 0; i < 3; ++i) {
            allFlags.add(1 << i);
        }
        new SearchableMultiChoiceDialogBuilder<>(mActivity, allFlags, R.array.freeze_unfreeze_flags)
                .setTitle(R.string.freeze_unfreeze)
                .setPositiveButton(R.string.create_shortcut, (dialog, which, selections) -> {
                    int flags = 0;
                    for (int flag : selections) {
                        flags |= flag;
                    }
                    Bitmap icon = getBitmapFromDrawable(mIconView.getDrawable());
                    FreezeUnfreezeShortcutInfo shortcutInfo = new FreezeUnfreezeShortcutInfo(mPackageName, mUserId, flags);
                    shortcutInfo.setName(getString(isFrozen ? R.string.shortcut_unfreeze_app
                            : R.string.shortcut_freeze_app, mAppLabel));
                    shortcutInfo.setIcon(isFrozen ? getDimmedBitmap(icon) : icon);
                    CreateShortcutDialogFragment dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                    dialog1.show(getChildFragmentManager(), CreateShortcutDialogFragment.TAG);
                })
                .show();
    }

    private void createAppActionShortcut(@NonNull @AppActionShortcutInfo.ShortcutAction String action,
                                         @StringRes int labelRes,
                                         @DrawableRes int iconRes) {
        AppActionShortcutInfo shortcutInfo = new AppActionShortcutInfo(mPackageName, mUserId, action);
        shortcutInfo.setName(getString(labelRes, mAppLabel));
        Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
        shortcutInfo.setIcon(icon != null ? getBitmapFromDrawable(icon) : getBitmapFromDrawable(mIconView.getDrawable()));
        CreateShortcutDialogFragment dialog = CreateShortcutDialogFragment.getInstance(shortcutInfo);
        dialog.show(getChildFragmentManager(), CreateShortcutDialogFragment.TAG);
    }

    private void showForceStopShortcutOptions() {
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(getString(R.string.shortcut_force_stop_app, mAppLabel))
                .setItems(new CharSequence[]{
                        getString(R.string.create_shortcut),
                        getString(R.string.force_stop_tile_set_app)
                }, (dialog, which) -> {
                    if (which == 0) {
                        createAppActionShortcut(AppActionShortcutInfo.ACTION_FORCE_STOP,
                                R.string.shortcut_force_stop_app, R.drawable.ic_stop);
                    } else {
                        ForceStopTileController.setSelectedTarget(mPackageName, mUserId);
                        ForceStopTileService.requestAddTile(mActivity);
                        UIUtils.displayShortToast(R.string.force_stop_tile_app_set, mAppLabel);
                    }
                })
                .show();
    }

    private void displayInstallerDialog(@NonNull InstallSourceInfoCompat installSource) {
        List<CharSequence> installerInfoList = new ArrayList<>(3);
        List<String> packageNames = new ArrayList<>(3);
        if (installSource.getInstallingPackageLabel() != null) {
            CharSequence info = new SpannableStringBuilder(getSmallerText(getString(R.string.installer)))
                    .append("\n")
                    .append(getTitleText(requireContext(), installSource.getInstallingPackageLabel()))
                    .append("\n")
                    .append(installSource.getInstallingPackageName());
            installerInfoList.add(info);
            packageNames.add(installSource.getInstallingPackageName());
        }
        if (installSource.getInitiatingPackageLabel() != null) {
            SpannableStringBuilder info = new SpannableStringBuilder(getSmallerText(getString(R.string.actual_installer)))
                    .append("\n")
                    .append(getTitleText(requireContext(), installSource.getInitiatingPackageLabel()))
                    .append("\n")
                    .append(installSource.getInitiatingPackageName());
            // Append the initiating-package signing-cert SHA-256 (API 30+) so the
            // user can match it against a known-good installer fingerprint without
            // navigating to App Details for the installer itself. Defends the
            // case where a malicious installer spoofs the label/name of a real
            // store but signs with its own key.
            String certDigest = computeInitiatingCertSha256(installSource);
            if (certDigest != null) {
                info.append("\n")
                        .append(getSmallerText(getString(R.string.install_source_initiator_cert_sha256,
                                certDigest)));
            }
            installerInfoList.add(info);
            packageNames.add(installSource.getInitiatingPackageName());
        }
        if (installSource.getOriginatingPackageLabel() != null) {
            CharSequence info = new SpannableStringBuilder(getSmallerText(getString(R.string.apk_source)))
                    .append("\n")
                    .append(getTitleText(requireContext(), installSource.getOriginatingPackageLabel()))
                    .append("\n")
                    .append(installSource.getOriginatingPackageName());
            installerInfoList.add(info);
            packageNames.add(installSource.getOriginatingPackageName());
        }
        new SearchableItemsDialogBuilder<>(requireContext(), installerInfoList)
                .setTitle(R.string.installer)
                .setOnItemClickListener((dialog, which, item) -> {
                    String packageName = packageNames.get(which);
                    Intent intent = AppDetailsActivity.getIntent(requireContext(), packageName, mUserId);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    /**
     * Compute a colon-separated SHA-256 hex digest of the initiating installer's
     * first signing certificate, when available. Returns {@code null} when the
     * signing info is absent (pre-API 30, side-loaded with no install session,
     * or runtime failure) so the caller can omit the line entirely.
     *
     * <p>Match against a publisher's known-good fingerprint to detect a spoofed
     * installer that wears a real store's label but signs with a different key.
     */
    @Nullable
    private static String computeInitiatingCertSha256(@NonNull InstallSourceInfoCompat installSource) {
        android.content.pm.SigningInfo signingInfo = installSource.getInitiatingPackageSigningInfo();
        if (signingInfo == null) return null;
        try {
            android.content.pm.Signature[] sigs = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
            if (sigs == null || sigs.length == 0) return null;
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 3);
            for (int i = 0; i < digest.length; ++i) {
                if (i > 0) sb.append(':');
                int v = digest[i] & 0xff;
                sb.append(Character.forDigit(v >>> 4, 16));
                sb.append(Character.forDigit(v & 0x0f, 16));
            }
            return sb.toString().toUpperCase(java.util.Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Get Unix time to formatted time.
     *
     * @param time Unix time
     * @return Formatted time
     */
    @NonNull
    private String getTime(long time) {
        return DateUtils.formatLongDateTime(requireContext(), time);
    }

    /**
     * Format sizes (bytes to B, KB, MB etc.).
     *
     * @param size Size in Bytes
     * @return Formatted size
     */
    private String getReadableSize(long size) {
        return Formatter.formatFileSize(mActivity, size);
    }

    private void showProgressIndicator(boolean show) {
        if (mProgressIndicator == null) return;
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }
}
