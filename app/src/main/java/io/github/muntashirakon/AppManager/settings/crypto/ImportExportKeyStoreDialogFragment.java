// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import static io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager.AM_KEYSTORE_FILE;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.IoUtils;

public class ImportExportKeyStoreDialogFragment extends DialogFragment {
    public static final String TAG = "IEKeyStoreDialogFragment";

    private FragmentActivity mActivity;
    private final ActivityResultLauncher<String> mExportKeyStore = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                if (uri == null) {
                    dismiss();
                    return;
                }
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (InputStream is = new FileInputStream(AM_KEYSTORE_FILE);
                         OutputStream os = mActivity.getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new IOException("Unable to open URI");
                        IoUtils.copy(is, os);
                        ThreadUtils.postOnMainThread(() -> {
                            UIUtils.displayShortToast(R.string.done);
                            ExUtils.exceptionAsIgnored(this::dismiss);
                        });
                    } catch (IOException e) {
                        ThreadUtils.postOnMainThread(() -> {
                            UIUtils.displayShortToast(R.string.failed);
                            ExUtils.exceptionAsIgnored(this::dismiss);
                        });
                    }
                });
            });
    private final ActivityResultLauncher<String> mImportKeyStore = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    dismiss();
                    return;
                }
                new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.import_keystore)
                        .setMessage(R.string.confirm_import_keystore)
                        .setPositiveButton(R.string.yes, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                            File backupFile = null;
                            boolean importSucceeded = false;
                            try (InputStream is = mActivity.getContentResolver().openInputStream(uri)) {
                                if (is == null) throw new IOException("Unable to open URI");
                                backupFile = backupExistingKeyStore(AM_KEYSTORE_FILE);
                                try (OutputStream os = new FileOutputStream(AM_KEYSTORE_FILE)) {
                                    IoUtils.copy(is, os);
                                }
                                if (KeyStoreManager.hasKeyStorePassword()) {
                                    CountDownLatch waitForKs = new CountDownLatch(1);
                                    KeyStoreManager.inputKeyStorePassword(mActivity, waitForKs::countDown);
                                    waitForKs.await(2, TimeUnit.MINUTES);
                                    if (waitForKs.getCount() == 1) {
                                        throw new Exception();
                                    }
                                }
                                KeyStoreManager.reloadKeyStore();
                                importSucceeded = true;
                                // TODO: 21/4/21 Only import the keys that we use instead of replacing the entire keystore
                                ThreadUtils.postOnMainThread(() -> {
                                    UIUtils.displayShortToast(R.string.done);
                                    ExUtils.exceptionAsIgnored(this::dismiss);
                                });
                            } catch (Exception e) {
                                try {
                                    restoreKeyStoreBackup(AM_KEYSTORE_FILE, backupFile);
                                    try {
                                        KeyStoreManager.reloadKeyStore();
                                    } catch (Exception ignore) {
                                    }
                                } catch (IOException restoreException) {
                                    e.addSuppressed(restoreException);
                                }
                                ThreadUtils.postOnMainThread(() -> {
                                    UIUtils.displayShortToast(R.string.failed);
                                    ExUtils.exceptionAsIgnored(this::dismiss);
                                });
                            } finally {
                                if (importSucceeded) {
                                    deleteBackup(backupFile);
                                }
                            }
                        }))
                        .setNegativeButton(R.string.close, (dialog, which) -> dismiss())
                        .setCancelable(false)
                        .show();
            });

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mActivity = requireActivity();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.pref_import_export_keystore)
                .setMessage(R.string.choose_what_to_do)
                .setPositiveButton(R.string.pref_export, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.pref_import, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button exportButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            Button importButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (AM_KEYSTORE_FILE.exists()) {
                exportButton.setOnClickListener(v -> mExportKeyStore.launch(KeyStoreManager.AM_KEYSTORE_FILE_NAME));
            }
            importButton.setOnClickListener(v -> mImportKeyStore.launch("application/*"));
        });
        return alertDialog;
    }

    @Nullable
    @VisibleForTesting
    static File backupExistingKeyStore(@NonNull File keyStoreFile) throws IOException {
        if (!keyStoreFile.exists()) {
            return null;
        }
        File parent = keyStoreFile.getAbsoluteFile().getParentFile();
        File backupFile = File.createTempFile(keyStoreFile.getName(), ".bak", parent);
        if (!backupFile.delete()) {
            throw new IOException("Unable to prepare keystore backup.");
        }
        if (!keyStoreFile.renameTo(backupFile)) {
            throw new IOException("Unable to back up existing keystore.");
        }
        return backupFile;
    }

    @VisibleForTesting
    static void restoreKeyStoreBackup(@NonNull File keyStoreFile, @Nullable File backupFile) throws IOException {
        if (keyStoreFile.exists() && !keyStoreFile.delete()) {
            throw new IOException("Unable to remove failed keystore import.");
        }
        if (backupFile != null && backupFile.exists() && !backupFile.renameTo(keyStoreFile)) {
            throw new IOException("Unable to restore previous keystore.");
        }
    }

    private static void deleteBackup(@Nullable File backupFile) {
        if (backupFile != null && backupFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backupFile.delete();
        }
    }
}
