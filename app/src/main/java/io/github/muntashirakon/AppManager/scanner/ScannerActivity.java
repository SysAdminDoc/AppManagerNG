// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2015 Google, Inc.
public class ScannerActivity extends BaseActivity {
    public static final String EXTRA_IS_EXTERNAL = "is_external";

    @Nullable
    private ActionBar mActionBar;
    @Nullable
    private LinearProgressIndicator mProgressIndicator;
    @Nullable
    private ParcelFileDescriptor mFd;
    @Nullable
    private Uri mApkUri;
    @Nullable
    private ScannerViewModel mModel;
    private boolean mIsExternalApk;

    private final ActivityResultLauncher<String> mExportScanReport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    exportScanReport(uri);
                }
            });

    @Override
    protected void onDestroy() {
        FileUtils.deleteSilently(getCodeCacheDir());
        IoUtils.closeQuietly(mFd);
        super.onDestroy();
    }

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_fm);
        setSupportActionBar(findViewById(R.id.toolbar));
        ScannerViewModel model = new ViewModelProvider(this).get(ScannerViewModel.class);
        mModel = model;
        mActionBar = getSupportActionBar();
        Intent intent = getIntent();
        mIsExternalApk = intent.getBooleanExtra(EXTRA_IS_EXTERNAL, true);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgress(true);

        mApkUri = IntentCompat.getDataUri(intent);
        if (mApkUri == null) {
            UIUtils.displayShortToast(R.string.error);
            finish();
            return;
        }

        File apkFile = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (!FmProvider.AUTHORITY.equals(mApkUri.getAuthority())) {
                try {
                    mFd = FileUtils.getFdFromUri(this, mApkUri, "r");
                    apkFile = FileUtils.getFileFromFd(mFd);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } else {
            String path = mApkUri.getPath();
            if (path != null) apkFile = new File(path);
        }

        model.setApkFile(apkFile);
        model.setApkUri(mApkUri);

        FragmentTransaction initialTransaction = MotionUtils.maybeSetDefaultFragmentAnimations(this,
                getSupportFragmentManager().beginTransaction());
        initialTransaction.replace(R.id.main_layout, new ScannerFragment())
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_scanner, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_install).setVisible(mIsExternalApk && FeatureController.isInstallerEnabled());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (id == R.id.action_export_report) {
            ScannerViewModel model = mModel;
            if (model != null) {
                mExportScanReport.launch(model.getDefaultReportFileName());
                return true;
            }
        } else if (id == R.id.action_install) {
            if (mApkUri != null) {
                startActivity(PackageInstallerActivity.getLaunchableInstance(getApplicationContext(), mApkUri));
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportScanReport(@NonNull Uri uri) {
        ScannerViewModel model = mModel;
        if (model == null) {
            UIUtils.displayShortToast(R.string.export_failed);
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) {
                    throw new IOException("Cannot open export target.");
                }
                os.write(model.buildScanReportJson().getBytes(StandardCharsets.UTF_8));
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.scanner_export_success));
            } catch (Exception e) {
                e.printStackTrace();
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.export_failed));
            }
        });
    }

    public void setSubtitle(CharSequence subtitle) {
        if (mActionBar != null) {
            mActionBar.setSubtitle(subtitle);
        }
    }

    public void setSubtitle(@StringRes int subtitle) {
        if (mActionBar != null) {
            mActionBar.setSubtitle(subtitle);
        }
    }

    void showProgress(boolean willShow) {
        if (mProgressIndicator == null) {
            return;
        }
        if (willShow) {
            mProgressIndicator.show();
        } else {
            mProgressIndicator.hide();
        }
    }

    public void loadNewFragment(Fragment fragment) {
        FragmentTransaction transaction = MotionUtils.maybeSetDefaultFragmentAnimations(this,
                getSupportFragmentManager().beginTransaction());
        transaction.replace(R.id.main_layout, fragment)
                .addToBackStack(null)
                .commit();
    }
}
