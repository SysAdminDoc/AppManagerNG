// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

/**
 * The confirmation intent reaches us through a mutable {@code PendingIntent}, so whoever can make
 * the broadcast fire also chooses this payload. It must be rejected rather than re-forwarded
 * unless it names a foreign target, and it must never carry the caller's URI grants along.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallerConfirmIntentGuardTest {
    private static final String SELF = "io.github.sysadmindoc.AppManagerNG";
    private static final String INSTALLER = "com.google.android.packageinstaller";

    @Test
    public void aNullPayloadIsRejected() {
        assertNull(InstallerConfirmIntentGuard.sanitize(null, SELF));
    }

    @Test
    public void aFullyImplicitPayloadIsRejected() {
        Intent implicit = new Intent(Intent.ACTION_VIEW);
        assertNull(InstallerConfirmIntentGuard.sanitize(implicit, SELF));
    }

    @Test
    public void aPayloadPointingBackAtUsIsRejected() {
        Intent selfRedirect = new Intent()
                .setComponent(new ComponentName(SELF, SELF + ".settings.SettingsActivity"));
        assertNull(InstallerConfirmIntentGuard.sanitize(selfRedirect, SELF));
    }

    @Test
    public void aPackageScopedPayloadPointingBackAtUsIsRejected() {
        Intent selfRedirect = new Intent(Intent.ACTION_VIEW).setPackage(SELF);
        assertNull(InstallerConfirmIntentGuard.sanitize(selfRedirect, SELF));
    }

    @Test
    public void aComponentTargetedPayloadIsForwarded() {
        Intent confirm = new Intent()
                .setComponent(new ComponentName(INSTALLER, INSTALLER + ".InstallStart"));
        Intent sanitized = InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertNotNull(sanitized);
        assertEquals(confirm.getComponent(), sanitized.getComponent());
    }

    @Test
    public void aPackageScopedPayloadIsForwarded() {
        Intent confirm = new Intent(Intent.ACTION_VIEW).setPackage(INSTALLER);
        assertNotNull(InstallerConfirmIntentGuard.sanitize(confirm, SELF));
    }

    @Test
    public void uriGrantsAreStrippedFromTheForwardedPayload() {
        Intent confirm = new Intent(Intent.ACTION_VIEW)
                .setPackage(INSTALLER)
                .setData(Uri.parse("content://io.github.sysadmindoc.AppManagerNG.fm/secret"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        assertTrue(InstallerConfirmIntentGuard.carriesUriGrants(confirm));

        Intent sanitized = InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertNotNull(sanitized);
        assertFalse(InstallerConfirmIntentGuard.carriesUriGrants(sanitized));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, sanitized.getFlags());
        // The payload itself is preserved: only the caller's flags are dropped.
        assertEquals(confirm.getData(), sanitized.getData());
    }

    @Test
    public void everyCallerChosenFlagIsDropped() {
        Intent confirm = new Intent(Intent.ACTION_VIEW)
                .setPackage(INSTALLER)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                        | Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        Intent sanitized = InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertNotNull(sanitized);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, sanitized.getFlags());
    }

    @Test
    public void theOriginalIntentIsNotMutated() {
        Intent confirm = new Intent(Intent.ACTION_VIEW)
                .setPackage(INSTALLER)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertTrue(InstallerConfirmIntentGuard.carriesUriGrants(confirm));
    }

    // The cases below are the payloads the platform actually hands back. Every case above builds
    // its own intent, so none of them proved the guard passes a real confirmation through.

    /**
     * AOSP builds this one in {@code PackageInstallerService}: action, a {@code package:} data URI,
     * a callback binder, and nothing else. Verified against android-10.0.0_r47 and
     * android-13.0.0_r83, which are byte-for-byte identical in this respect.
     */
    @NonNull
    private static Intent aospUninstallConfirmation() {
        return new Intent(Intent.ACTION_UNINSTALL_PACKAGE)
                .setData(Uri.fromParts("package", "com.example.victim", null));
    }

    /**
     * {@code PackageInstaller.ACTION_CONFIRM_INSTALL} is hidden, so the literal is used here. AOSP
     * sets a package on this one, but a ROM whose {@code getPackageInstallerPackageName()} comes
     * back null leaves it implicit, which is the shape reported in fork issue #14.
     */
    @NonNull
    private static Intent implicitInstallConfirmation() {
        return new Intent("android.content.pm.action.CONFIRM_INSTALL")
                .putExtra("android.content.pm.extra.SESSION_ID", 42);
    }

    @NonNull
    private PackageManager resolverFor(@NonNull Intent query, @Nullable String targetPackage,
                                       boolean system) {
        PackageManager packageManager = RuntimeEnvironment.getApplication().getPackageManager();
        if (targetPackage == null) {
            return packageManager;
        }
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = targetPackage;
        applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = targetPackage;
        activityInfo.name = targetPackage + ".InstallStart";
        activityInfo.applicationInfo = applicationInfo;
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        Shadows.shadowOf(packageManager).addResolveInfoForIntent(query, resolveInfo);
        return packageManager;
    }

    @Test
    public void theAospUninstallConfirmationIsForwardedToTheResolvedSystemActivity() {
        Intent confirm = aospUninstallConfirmation();
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                confirm, SELF, resolverFor(confirm, INSTALLER, true));
        assertTrue(decision.isForwarded());
        assertEquals(InstallerConfirmIntentGuard.RULE_RESOLVED_SYSTEM, decision.rule);
        assertNotNull(decision.intent);
        assertEquals(new ComponentName(INSTALLER, INSTALLER + ".InstallStart"),
                decision.intent.getComponent());
        assertEquals(confirm.getData(), decision.intent.getData());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, decision.intent.getFlags());
    }

    @Test
    public void anImplicitInstallConfirmationIsForwardedToTheResolvedSystemActivity() {
        Intent confirm = implicitInstallConfirmation();
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                confirm, SELF, resolverFor(confirm, INSTALLER, true));
        assertTrue(decision.isForwarded());
        assertEquals(InstallerConfirmIntentGuard.RULE_RESOLVED_SYSTEM, decision.rule);
        assertNotNull(decision.intent);
        assertEquals(INSTALLER, decision.intent.getComponent().getPackageName());
        assertEquals(42, decision.intent.getIntExtra("android.content.pm.extra.SESSION_ID", -1));
    }

    @Test
    public void anImplicitPayloadResolvingToANonSystemPackageIsRejected() {
        Intent confirm = aospUninstallConfirmation();
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                confirm, SELF, resolverFor(confirm, "com.attacker.app", false));
        assertNull(decision.intent);
        assertEquals(InstallerConfirmIntentGuard.RULE_NOT_SYSTEM, decision.rule);
        assertEquals("com.attacker.app/.InstallStart", decision.target);
    }

    @Test
    public void anImplicitPayloadResolvingBackToUsIsRejected() {
        Intent confirm = aospUninstallConfirmation();
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                confirm, SELF, resolverFor(confirm, SELF, true));
        assertNull(decision.intent);
        assertEquals(InstallerConfirmIntentGuard.RULE_SELF_REDIRECT, decision.rule);
    }

    @Test
    public void anImplicitPayloadThatResolvesToNothingIsRejected() {
        Intent confirm = aospUninstallConfirmation();
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                confirm, SELF, resolverFor(confirm, null, false));
        assertNull(decision.intent);
        assertEquals(InstallerConfirmIntentGuard.RULE_UNRESOLVABLE, decision.rule);
    }

    @Test
    public void anImplicitPayloadIsStillRejectedWithoutAResolver() {
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                aospUninstallConfirmation(), SELF, null);
        assertNull(decision.intent);
        assertEquals(InstallerConfirmIntentGuard.RULE_NO_RESOLVER, decision.rule);
    }

    @Test
    public void uriGrantsAreStrippedFromAResolvedPayload() {
        Intent confirm = aospUninstallConfirmation()
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        assertTrue(InstallerConfirmIntentGuard.carriesUriGrants(confirm));
        InstallerConfirmIntentGuard.Decision decision = InstallerConfirmIntentGuard.decide(
                confirm, SELF, resolverFor(confirm, INSTALLER, true));
        assertNotNull(decision.intent);
        assertFalse(InstallerConfirmIntentGuard.carriesUriGrants(decision.intent));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, decision.intent.getFlags());
        assertTrue(InstallerConfirmIntentGuard.carriesUriGrants(confirm));
    }

    @Test
    public void anExplicitlyTargetedPayloadStillReportsItsRule() {
        Intent confirm = new Intent(Intent.ACTION_VIEW).setPackage(INSTALLER);
        InstallerConfirmIntentGuard.Decision decision =
                InstallerConfirmIntentGuard.decide(confirm, SELF, null);
        assertTrue(decision.isForwarded());
        assertEquals(InstallerConfirmIntentGuard.RULE_EXPLICIT_TARGET, decision.rule);
        assertEquals(INSTALLER, decision.target);
    }

    @Test
    public void anAbsentPayloadReportsItsRule() {
        InstallerConfirmIntentGuard.Decision decision =
                InstallerConfirmIntentGuard.decide(null, SELF, null);
        assertNull(decision.intent);
        assertEquals(InstallerConfirmIntentGuard.RULE_ABSENT, decision.rule);
    }
}
