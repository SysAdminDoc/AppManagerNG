// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.crypto.ECCCrypto;
import io.github.muntashirakon.AppManager.crypto.OpenPGPCrypto;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

import org.openintents.openpgp.util.OpenPgpApi;

public class CryptoUtils {
    @StringDef(value = {
            MODE_NO_ENCRYPTION,
            MODE_AES,
            MODE_RSA,
            MODE_ECC,
            MODE_OPEN_PGP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final String MODE_NO_ENCRYPTION = "none";
    public static final String MODE_AES = "aes";
    public static final String MODE_RSA = "rsa";
    public static final String MODE_ECC = "ecc";
    public static final String MODE_OPEN_PGP = "pgp";
    @Mode
    public static String getMode() {
        String currentMode = Prefs.Encryption.getEncryptionMode();
        if (isAvailable(currentMode)) return currentMode;
        // Fallback to no encryption if none of the modes are available.
        return MODE_NO_ENCRYPTION;
    }

    public static String getExtension(@NonNull @Mode String mode) {
        switch (mode) {
            case MODE_OPEN_PGP:
                return OpenPGPCrypto.GPG_EXT;
            case MODE_AES:
                return AESCrypto.AES_EXT;
            case MODE_RSA:
                return RSACrypto.RSA_EXT;
            case MODE_ECC:
                return ECCCrypto.ECC_EXT;
            case MODE_NO_ENCRYPTION:
            default:
                return "";
        }
    }

    /**
     * Get file name with appropriate extension
     */
    @NonNull
    public static String getAppropriateFilename(String filename, @NonNull @Mode String mode) {
        return filename + getExtension(mode);
    }

    @WorkerThread
    public static Crypto setupCrypto(@NonNull BackupMetadataV2 metadata) throws CryptoException {
        BackupCryptSetupHelper cryptoHelper = new BackupCryptSetupHelper(metadata.crypto, metadata.version);
        metadata.keyIds = cryptoHelper.getKeyIds();
        metadata.aes = cryptoHelper.getAes();
        metadata.iv = cryptoHelper.getIv();
        return cryptoHelper.crypto;
    }

    @WorkerThread
    public static boolean isAvailable(@NonNull @Mode String mode) {
        switch (mode) {
            case MODE_OPEN_PGP:
                String keyIds = Prefs.Encryption.getOpenPgpKeyIds();
                if (TextUtils.isEmpty(keyIds)) {
                    return false;
                }
                return isOpenPgpProviderAvailable(ContextUtils.getContext(),
                        Prefs.Encryption.getOpenPgpProvider());
            case MODE_AES:
                try {
                    return KeyStoreManager.getInstance().containsKey(AESCrypto.AES_KEY_ALIAS);
                } catch (Exception e) {
                    return false;
                }
            case MODE_RSA:
                try {
                    return KeyStoreManager.getInstance().containsKey(RSACrypto.RSA_KEY_ALIAS);
                } catch (Exception e) {
                    return false;
                }
            case MODE_ECC:
                try {
                    return KeyStoreManager.getInstance().containsKey(ECCCrypto.ECC_KEY_ALIAS);
                } catch (Exception e) {
                    return false;
                }
            case MODE_NO_ENCRYPTION:
                return true;
            default:
                return false;
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    static boolean isOpenPgpProviderAvailable(@NonNull Context context, @NonNull String providerPackage) {
        if (TextUtils.isEmpty(providerPackage)) {
            return false;
        }
        Intent intent = new Intent(OpenPgpApi.SERVICE_INTENT_2);
        intent.setPackage(providerPackage);
        List<ResolveInfo> resolveInfoList = context.getPackageManager().queryIntentServices(intent, 0);
        for (ResolveInfo resolveInfo : resolveInfoList) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo != null && providerPackage.equals(serviceInfo.packageName)) {
                return true;
            }
        }
        return false;
    }
}
