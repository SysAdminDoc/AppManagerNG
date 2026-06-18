// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import static io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager.AM_KEYSTORE_FILE;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.ScopedKeyStoreImporter;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
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
                        Log.e(TAG, "Could not export AppManagerNG keystore.", e);
                        ThreadUtils.postOnMainThread(() -> {
                            UIUtils.displayShortToast(R.string.keystore_export_failed);
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
                new TextInputDialogBuilder(mActivity, R.string.keystore_password_field_hint)
                        .setTitle(R.string.import_keystore)
                        .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                            if (TextUtils.isEmpty(inputText)) {
                                UIUtils.displayShortToast(R.string.keystore_pass_cannot_be_empty);
                                return;
                            }
                            char[] importPassword = new char[inputText.length()];
                            inputText.getChars(0, inputText.length(), importPassword, 0);
                            confirmAndImportKeyStore(uri, importPassword);
                        })
                        .setNegativeButton(R.string.cancel, null)
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

    private void confirmAndImportKeyStore(@NonNull Uri uri, @NonNull char[] importPassword) {
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.import_keystore)
                .setMessage(R.string.confirm_import_keystore)
                .setPositiveButton(R.string.ok, (dialog, which) -> importKeyStoreAsync(uri, importPassword))
                .setNegativeButton(R.string.cancel, (dialog, which) -> Utils.clearChars(importPassword))
                .setOnCancelListener(dialog -> Utils.clearChars(importPassword))
                .show();
    }

    private void importKeyStoreAsync(@NonNull Uri uri, @NonNull char[] importPassword) {
        ThreadUtils.postOnBackgroundThread(() -> {
            File backupFile = null;
            boolean backupPrepared = false;
            boolean importSucceeded = false;
            try {
                KeyStore importKs = KeyStore.getInstance("BKS");
                try (InputStream is = mActivity.getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new IOException("Unable to open URI");
                    importKs.load(is, importPassword);
                }
                KeyStoreManager liveKsm = KeyStoreManager.getInstance();
                ScopedKeyStoreImporter.ImportPreview preview =
                        ScopedKeyStoreImporter.preview(importKs, liveKsm);
                List<String> amFound = preview.amAliasesFound;
                if (amFound.isEmpty()) {
                    ThreadUtils.postOnMainThread(() -> {
                        UIUtils.displayShortToast(R.string.keystore_import_no_am_aliases);
                        ExUtils.exceptionAsIgnored(this::dismiss);
                    });
                    return;
                }
                backupFile = backupExistingKeyStore(AM_KEYSTORE_FILE);
                backupPrepared = true;
                ScopedKeyStoreImporter.ImportResult result =
                        ScopedKeyStoreImporter.importScoped(importKs, importPassword, liveKsm);
                if (result.allFailed()) {
                    restoreFailedImport(backupFile);
                    ThreadUtils.postOnMainThread(() -> {
                        UIUtils.displayShortToast(R.string.keystore_import_failed);
                        ExUtils.exceptionAsIgnored(this::dismiss);
                    });
                    return;
                }
                importSucceeded = true;
                int successCount = result.succeeded.size();
                int failCount = result.failed.size();
                ThreadUtils.postOnMainThread(() -> {
                    if (failCount > 0) {
                        UIUtils.displayLongToast(R.string.keystore_import_partial,
                                successCount, failCount);
                    } else {
                        UIUtils.displayShortToast(R.string.keystore_import_success,
                                successCount);
                    }
                    ExUtils.exceptionAsIgnored(this::dismiss);
                });
            } catch (Exception e) {
                Log.e(TAG, "Could not import AppManagerNG keystore.", e);
                if (backupPrepared) {
                    restoreFailedImport(backupFile);
                }
                ThreadUtils.postOnMainThread(() -> {
                    UIUtils.displayShortToast(R.string.keystore_import_failed);
                    ExUtils.exceptionAsIgnored(this::dismiss);
                });
            } finally {
                Utils.clearChars(importPassword);
                if (importSucceeded) {
                    deleteBackup(backupFile);
                }
            }
        });
    }

    private static void restoreFailedImport(@Nullable File backupFile) {
        try {
            restoreKeyStoreBackup(AM_KEYSTORE_FILE, backupFile);
            KeyStoreManager.reloadKeyStore();
        } catch (Exception e) {
            Log.e(TAG, "Could not restore AppManagerNG keystore backup after failed import.", e);
        }
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
