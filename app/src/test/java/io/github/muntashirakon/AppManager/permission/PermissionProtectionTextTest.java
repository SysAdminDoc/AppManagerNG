// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;

/**
 * Issue 31: the protection level and the grant state under each permission were English words built in code, so a
 * translated app still read "dangerous|instant|revoked".
 */
@RunWith(RobolectricTestRunner.class)
public class PermissionProtectionTextTest {
    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void englishReadsAsBefore() {
        assertEquals("dangerous|instant|revoked", PermissionProtectionText.of(mContext,
                permission(PermissionInfo.PROTECTION_DANGEROUS | PermissionInfo.PROTECTION_FLAG_INSTANT), false));
        assertEquals("normal|instant|granted", PermissionProtectionText.of(mContext,
                permission(PermissionInfo.PROTECTION_NORMAL | PermissionInfo.PROTECTION_FLAG_INSTANT), true));
        assertEquals("signature|privileged|development|appop", PermissionProtectionText.of(mContext,
                permission(PermissionInfo.PROTECTION_SIGNATURE | PermissionInfo.PROTECTION_FLAG_PRIVILEGED
                        | PermissionInfo.PROTECTION_FLAG_DEVELOPMENT | PermissionInfo.PROTECTION_FLAG_APPOP)));
        assertEquals("signature|pre23|installer|verifier|preinstalled|setup|runtime",
                PermissionProtectionText.of(mContext, permission(PermissionInfo.PROTECTION_SIGNATURE
                        | PermissionInfo.PROTECTION_FLAG_INSTALLER | PermissionInfo.PROTECTION_FLAG_VERIFIER
                        | PermissionInfo.PROTECTION_FLAG_PREINSTALLED | PermissionInfo.PROTECTION_FLAG_SETUP
                        | PermissionInfo.PROTECTION_FLAG_PRE23 | PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY)));
    }

    @Test
    @Config(qualifiers = "ru")
    public void russianGetsTranslatedWordsBetweenTheSameSeparators() {
        String text = PermissionProtectionText.of(mContext,
                permission(PermissionInfo.PROTECTION_DANGEROUS | PermissionInfo.PROTECTION_FLAG_INSTANT), false);

        String[] tokens = text.split("\\|");
        assertEquals(3, tokens.length);
        assertEquals(mContext.getString(R.string.perm_protection_dangerous), tokens[0]);
        assertEquals(mContext.getString(R.string.perm_protection_instant), tokens[1]);
        assertEquals(mContext.getString(R.string.perm_status_revoked), tokens[2]);
        assertNotEquals("dangerous", tokens[0]);
        assertNotEquals("instant", tokens[1]);
        assertNotEquals("revoked", tokens[2]);

        String granted = PermissionProtectionText.of(mContext, permission(PermissionInfo.PROTECTION_SIGNATURE), true);
        Locale ru = new Locale("ru");
        assertEquals(mContext.getString(R.string.app_signing_signature).toLowerCase(ru) + "|"
                + mContext.getString(R.string.perm_app_status_granted).toLowerCase(ru), granted);
        assertNotEquals("signature|granted", granted);
    }

    @NonNull
    private static PermissionInfo permission(int protectionLevel) {
        PermissionInfo info = new PermissionInfo();
        info.name = "android.permission.EXAMPLE";
        info.protectionLevel = protectionLevel;
        return info;
    }
}
