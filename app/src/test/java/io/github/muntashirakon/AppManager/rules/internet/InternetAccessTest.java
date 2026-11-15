// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.internet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.OneTimeWorkRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesExporter;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.internet.InternetFirewall.Unavailable;
import io.github.muntashirakon.AppManager.rules.struct.InternetRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Saved internet rules against a fake firewall: blocking, allowing again, a new UID after a reinstall, a restart
 * that clears the kernel, a privileged mode that comes and goes, and devices that refuse the rule.
 */
@RunWith(RobolectricTestRunner.class)
public class InternetAccessTest {
    private static final String APP = "com.example.internet";
    private static final String OTHER_APP = "com.example.internet.other";
    private static final int APP_UID = 10100;

    private FakeFirewall mFirewall;
    private final Map<String, Integer> mUids = new HashMap<>();
    private final List<String> mEnqueued = new ArrayList<>();

    @Before
    public void setUp() {
        deleteRulesFiles();
        mFirewall = new FakeFirewall();
        InternetAccess.setFirewallForTesting(mFirewall);
        InternetAccess.setUidResolverForTesting((packageName, userId) -> {
            Integer uid = mUids.get(packageName + ":" + userId);
            return uid != null ? uid : InternetRule.NO_UID;
        });
        InternetAccess.setWorkSinkForTesting((context, uniqueName, request) -> mEnqueued.add(uniqueName));
        mUids.put(APP + ":0", APP_UID);
    }

    @After
    public void tearDown() {
        InternetAccess.setFirewallForTesting(null);
        InternetAccess.setUidResolverForTesting(null);
        InternetAccess.setWorkSinkForTesting(null);
        deleteRulesFiles();
    }

    /**
     * The rules files and the saved-rule marker of the context the code uses, which stays the first test's across
     * tests.
     */
    private static void deleteRulesFiles() {
        for (String packageName : Arrays.asList(APP, OTHER_APP)) {
            Path rulesFile = RulesStorageManager.getConfDir(ContextUtils.getContext())
                    .findFileOrNull(packageName + ".tsv");
            if (rulesFile != null) {
                assertTrue(rulesFile.delete());
            }
        }
        File marker = new File(ContextUtils.getContext().getFilesDir(), InternetAccess.RULES_MARKER);
        if (marker.exists()) {
            assertTrue(marker.delete());
        }
    }

    @Test
    public void blockingDeniesTheAppsUidAndSavesTheRule() {
        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, true);

        assertTrue(status.saved);
        assertTrue(status.blocked);
        assertTrue(status.inForce);
        assertNull(status.unavailable);
        assertEquals(Collections.singleton(APP_UID), mFirewall.denied);
        InternetRule saved = savedRule(APP, 0);
        assertNotNull(saved);
        assertTrue(saved.isBlocked());
        assertEquals(APP_UID, saved.getUid());
    }

    @Test
    public void allowingAgainLiftsTheBlockAndDropsTheRule() {
        InternetAccess.setBlocked(APP, 0, true);

        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, false);

        assertFalse(status.saved);
        assertFalse(status.blocked);
        assertTrue(status.inForce);
        assertTrue(mFirewall.denied.isEmpty());
        assertNull(savedRule(APP, 0));
    }

    @Test
    public void anAppWithoutARuleIsLeftAlone() {
        InternetAccess.Status status = InternetAccess.apply(APP, 0);

        assertFalse(status.saved);
        assertTrue(status.inForce);
        assertTrue(status.canChange());
        assertEquals(0, mFirewall.changes);
    }

    @Test
    public void aReinstallMovesTheBlockToTheNewUid() {
        InternetAccess.setBlocked(APP, 0, true);
        // The kernel still holds the old UID, which the next app installed could get
        mUids.put(APP + ":0", 10200);

        InternetAccess.Status status = InternetAccess.apply(APP, 0);

        assertTrue(status.inForce);
        assertEquals(Collections.singleton(10200), mFirewall.denied);
        assertEquals(10200, savedRule(APP, 0).getUid());
    }

    @Test
    public void anUninstallLetsTheUidGoAndTheInstallPutsTheBlockBack() {
        InternetAccess.setBlocked(APP, 0, true);
        mUids.remove(APP + ":0");

        InternetAccess.Status removed = InternetAccess.apply(APP, 0);

        assertTrue(removed.saved);
        assertTrue(removed.blocked);
        assertFalse(removed.inForce);
        assertNull(removed.unavailable);
        assertTrue(mFirewall.denied.isEmpty());
        assertEquals(InternetRule.NO_UID, savedRule(APP, 0).getUid());

        mUids.put(APP + ":0", 10300);
        InternetAccess.Status installed = InternetAccess.apply(APP, 0);

        assertTrue(installed.inForce);
        assertEquals(Collections.singleton(10300), mFirewall.denied);
    }

    @Test
    public void afterARestartEveryRuleIsPutBack() {
        mUids.put(OTHER_APP + ":10", 1010100);
        InternetAccess.setBlocked(APP, 0, true);
        InternetAccess.setBlocked(OTHER_APP, 10, true);
        // An app with other rules but no internet rule
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(OTHER_APP, 0)) {
            cb.setAppOp(100, 1);
        }
        mFirewall.denied.clear();

        InternetAccess.applyAll();

        assertEquals(new HashSet<>(Arrays.asList(APP_UID, 1010100)), mFirewall.denied);
        assertTrue(InternetAccess.hasAnyRule());
    }

    @Test
    public void withoutAPrivilegedModeTheRuleWaitsAndGoesBackInWhenItReturns() {
        InternetAccess.setBlocked(APP, 0, true);
        // A restart, and root or ADB isn't up yet
        mFirewall.denied.clear();
        mFirewall.unavailable = new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null);

        InternetAccess.Status waiting = InternetAccess.apply(APP, 0);

        assertTrue(waiting.saved);
        assertTrue(waiting.blocked);
        assertFalse(waiting.inForce);
        assertEquals(Unavailable.NO_PRIVILEGED_MODE, waiting.unavailable.reason);
        assertFalse(waiting.canChange());
        assertTrue(mFirewall.denied.isEmpty());
        assertNotNull(savedRule(APP, 0));

        mFirewall.unavailable = null;
        InternetAccess.applyAll();

        assertEquals(Collections.singleton(APP_UID), mFirewall.denied);
    }

    @Test
    public void withoutAPrivilegedModeANewRuleCantBeMade() {
        mFirewall.unavailable = new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null);

        InternetAccess.Status status = InternetAccess.apply(APP, 0);

        assertFalse(status.saved);
        assertFalse(status.canChange());
        assertEquals(Unavailable.NO_PRIVILEGED_MODE, status.unavailable.reason);
    }

    @Test
    public void allowingWithoutAPrivilegedModeIsSavedUntilItCanBeLifted() {
        InternetAccess.setBlocked(APP, 0, true);
        mFirewall.unavailable = new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null);

        InternetAccess.Status waiting = InternetAccess.setBlocked(APP, 0, false);

        assertFalse(waiting.blocked);
        assertFalse(waiting.inForce);
        // The kernel still blocks the app, so the rule has to stay to lift it later
        assertEquals(Collections.singleton(APP_UID), mFirewall.denied);
        assertFalse(savedRule(APP, 0).isBlocked());

        mFirewall.unavailable = null;
        InternetAccess.applyAll();

        assertTrue(mFirewall.denied.isEmpty());
        assertNull(savedRule(APP, 0));
    }

    @Test
    public void aFirewallThatDropsTheRuleIsReported() {
        mFirewall.dropRules = true;

        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, true);

        assertFalse(status.inForce);
        assertEquals(Unavailable.PLATFORM, status.unavailable.reason);
        assertNull(status.unavailable.detail);
        assertTrue(savedRule(APP, 0).isBlocked());
    }

    @Test
    public void aFirewallErrorIsReportedWithItsMessage() {
        mFirewall.failure = new IllegalStateException("No such chain");

        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, true);

        assertFalse(status.inForce);
        assertEquals(new Unavailable(Unavailable.PLATFORM, "No such chain"), status.unavailable);
    }

    @Test
    public void aRefusedCallReadsAsMissingPermission() {
        mFirewall.failure = new SecurityException("NETWORK_SETTINGS");

        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, true);

        assertEquals(Unavailable.NO_PERMISSION, status.unavailable.reason);
    }

    @Test
    public void aDeadPrivilegedModeReadsAsMissing() {
        mFirewall.remoteFailure = true;

        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, true);

        assertEquals(Unavailable.NO_PRIVILEGED_MODE, status.unavailable.reason);
        assertTrue(savedRule(APP, 0).isBlocked());
    }

    @Test
    public void anAppSharingABlockedUidStaysBlocked() {
        mUids.put(OTHER_APP + ":0", APP_UID);
        InternetAccess.setBlocked(APP, 0, true);
        InternetAccess.setBlocked(OTHER_APP, 0, true);

        InternetAccess.Status shared = InternetAccess.setBlocked(APP, 0, false);

        assertFalse(shared.inForce);
        assertEquals(new Unavailable(Unavailable.SHARED_UID, OTHER_APP), shared.unavailable);
        assertEquals(Collections.singleton(APP_UID), mFirewall.denied);

        InternetAccess.setBlocked(OTHER_APP, 0, false);
        InternetAccess.Status allowed = InternetAccess.apply(APP, 0);

        assertTrue(allowed.inForce);
        assertTrue(mFirewall.denied.isEmpty());
        assertNull(savedRule(APP, 0));
    }

    @Test
    public void onlyAppsWithARuleArePutBackAfterAnInstall() {
        Context context = ApplicationProvider.getApplicationContext();

        InternetAccess.applySoon(context, APP, 0);
        assertTrue(mEnqueued.isEmpty());

        InternetAccess.setBlocked(APP, 0, true);
        InternetAccess.applySoon(context, APP, 0);
        InternetAccess.applySoon(context, null, 0);

        assertEquals(Arrays.asList("internet_rules_package:" + APP + ":0", "internet_rules_all"), mEnqueued);
    }

    @Test
    public void aRuleSurvivesItsTextForm() {
        InternetRule blocked = new InternetRule(APP, 10, true, 1010100);
        InternetRule waiting = new InternetRule(APP, 0, true, InternetRule.NO_UID);

        assertEquals(blocked, RuleEntry.unflattenFromString(null, blocked.flattenToString(true), true));
        assertEquals(waiting, RuleEntry.unflattenFromString(null, waiting.flattenToString(true), true));
    }

    @Test
    public void anExportKeepsOnlyTheExportedUsersRule() throws IOException {
        mUids.put(APP + ":10", 1010100);
        InternetAccess.setBlocked(APP, 0, true);
        InternetAccess.setBlocked(APP, 10, true);

        String exported = export(APP, 0);

        assertEquals(1, exported.split("\n").length);
        assertTrue(exported, exported.startsWith(APP + "\t0\tINTERNET\ttrue\t"));
    }

    @Test
    public void anImportedBlockGoesIntoForce() throws IOException {
        InternetAccess.setBlocked(APP, 0, true);
        File exported = writeExport(export(APP, 0));
        InternetAccess.setBlocked(APP, 0, false);
        assertTrue(mFirewall.denied.isEmpty());

        importRules(exported, 0);

        assertEquals(Collections.singleton(APP_UID), mFirewall.denied);
        assertTrue(savedRule(APP, 0).isBlocked());
    }

    @Test
    public void anImportIntoAnotherUserUsesThatUsersUid() throws IOException {
        InternetAccess.setBlocked(APP, 0, true);
        File exported = writeExport(export(APP, 0));
        mUids.put(APP + ":10", 1010100);

        importRules(exported, 10);

        InternetRule imported = savedRule(APP, 10);
        assertNotNull(imported);
        assertTrue(imported.isBlocked());
        assertEquals(1010100, imported.getUid());
        assertEquals(new HashSet<>(Arrays.asList(APP_UID, 1010100)), mFirewall.denied);
    }

    @Test
    public void anImportKeepsTheUidTheSavedRuleHolds() throws IOException {
        // A backup made before a reinstall says allowed, while the kernel still blocks the old UID
        InternetAccess.setBlocked(APP, 0, true);
        File exported = writeExport(APP + "\t0\tINTERNET\tfalse\t-1\n");
        mUids.put(APP + ":0", 10200);

        importRules(exported, 0);

        assertTrue(mFirewall.denied.isEmpty());
        assertNull(savedRule(APP, 0));
    }

    @Test
    public void anImportThatOnlySaysAllowedLeavesOtherBlocksAlone() throws IOException {
        // Something else, such as the platform's own networking switch, put a deny on the app's UID
        mFirewall.denied.add(APP_UID);
        File exported = writeExport(APP + "\t0\tINTERNET\tfalse\t-1\n");

        List<InternetAccess.Status> notInForce = importRulesNotInForce(exported, 0);

        assertTrue(notInForce.isEmpty());
        assertEquals(Collections.singleton(APP_UID), mFirewall.denied);
        assertEquals(0, mFirewall.changes);
        assertNull(savedRule(APP, 0));
        assertFalse(InternetAccess.mightHaveRules(ContextUtils.getContext()));
    }

    @Test
    public void anImportedBlockThatCantBePutIntoForceIsReported() throws IOException {
        File exported = writeExport(APP + "\t0\tINTERNET\ttrue\t-1\n");
        mFirewall.unavailable = new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null);

        List<InternetAccess.Status> notInForce = importRulesNotInForce(exported, 0);

        assertEquals(1, notInForce.size());
        InternetAccess.Status status = notInForce.get(0);
        assertTrue(status.saved);
        assertTrue(status.blocked);
        assertEquals(Unavailable.NO_PRIVILEGED_MODE, status.unavailable.reason);
        assertTrue(savedRule(APP, 0).isBlocked());
        assertTrue(mFirewall.denied.isEmpty());
    }

    @Test
    public void aSavedRuleKeepsTheAppChangeSyncUntilTheLastOneGoes() {
        Context context = ContextUtils.getContext();
        mUids.put(APP + ":10", 1010100);
        assertFalse(InternetAccess.mightHaveRules(context));

        InternetAccess.setBlocked(APP, 0, true);
        assertTrue(InternetAccess.mightHaveRules(context));
        InternetAccess.setBlocked(APP, 10, true);

        InternetAccess.setBlocked(APP, 0, false);
        assertTrue("user 10 still has a rule", InternetAccess.mightHaveRules(context));

        InternetAccess.setBlocked(APP, 10, false);
        assertFalse(InternetAccess.mightHaveRules(context));
    }

    @Test
    public void anAppChangeIsPutBackInEveryUserWithARule() {
        Context context = ApplicationProvider.getApplicationContext();
        mUids.put(APP + ":10", 1010100);
        InternetAccess.setBlocked(APP, 0, true);
        InternetAccess.setBlocked(APP, 10, true);

        InternetAccess.applySoonInEveryUser(context, APP);
        InternetAccess.applySoonInEveryUser(context, OTHER_APP);

        assertEquals(2, mEnqueued.size());
        assertEquals(new HashSet<>(Arrays.asList("internet_rules_package:" + APP + ":0",
                "internet_rules_package:" + APP + ":10")), new HashSet<>(mEnqueued));
    }

    @Test
    public void aRomWithoutTheFirewallCallsReadsAsAPlatformError() {
        mFirewall.linkageError = new NoSuchMethodError("setUidFirewallRule");

        InternetAccess.Status status = InternetAccess.setBlocked(APP, 0, true);

        assertFalse(status.inForce);
        assertEquals(new Unavailable(Unavailable.PLATFORM, "setUidFirewallRule"), status.unavailable);
        assertTrue(status.canChange());
        assertTrue(savedRule(APP, 0).isBlocked());
    }

    @Test
    public void aSavedRuleCanOnlyBeChangedWhileTheFirewallAnswers() {
        assertFalse(waitingFor(Unavailable.NO_PRIVILEGED_MODE).canChange());
        assertFalse(waitingFor(Unavailable.NO_PERMISSION).canChange());
        assertFalse(waitingFor(Unavailable.ANDROID_VERSION).canChange());
        assertTrue(waitingFor(Unavailable.PLATFORM).canChange());
        assertTrue(waitingFor(Unavailable.SHARED_UID).canChange());
        assertFalse("a new rule needs a firewall that works", new InternetAccess.Status(false, false, true,
                new Unavailable(Unavailable.PLATFORM, null)).canChange());
    }

    @Test
    public void eachStatusSaysWhereTheBlockStands() {
        Context context = ContextUtils.getContext();
        String noPrivilegedMode = new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null).describe(context);
        String platform = new Unavailable(Unavailable.PLATFORM, null).describe(context);

        assertNull(new InternetAccess.Status(false, false, true, null).describe(context));
        assertEquals(context.getString(R.string.internet_access_unavailable, noPrivilegedMode),
                new InternetAccess.Status(false, false, true,
                        new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null)).describe(context));
        assertEquals(context.getString(R.string.internet_access_unknown, noPrivilegedMode),
                waitingFor(Unavailable.NO_PRIVILEGED_MODE).describe(context));
        assertEquals(context.getString(R.string.internet_access_not_in_force, platform),
                waitingFor(Unavailable.PLATFORM).describe(context));
        assertEquals(context.getString(R.string.internet_access_waiting_for_install),
                new InternetAccess.Status(true, true, false, null).describe(context));
        assertEquals(context.getString(R.string.internet_access_blocked),
                new InternetAccess.Status(true, true, true, null).describe(context));
    }

    @NonNull
    private static InternetAccess.Status waitingFor(int reason) {
        return new InternetAccess.Status(true, true, false, new Unavailable(reason, null));
    }

    /** What the rules exporter writes for one package and user. */
    @NonNull
    private static String export(@NonNull String packageName, int userId) throws IOException {
        Uri uri = Uri.parse("content://internet-access-test/export");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Robolectric hands back a stream that keeps nothing for a URI nobody registered
        shadowOf(ContextUtils.getContext().getContentResolver()).registerOutputStream(uri, out);
        new RulesExporter(Collections.singletonList(RuleType.INTERNET), Collections.singletonList(packageName),
                new int[]{userId}).saveRules(uri);
        return out.toString("UTF-8");
    }

    @NonNull
    private static List<InternetAccess.Status> importRulesNotInForce(@NonNull File file, int userId)
            throws IOException {
        try (RulesImporter importer = new RulesImporter(Collections.singletonList(RuleType.INTERNET),
                new int[]{userId})) {
            importer.addRulesFromPath(Paths.get(file));
            importer.applyRules(true);
            return importer.getInternetRulesNotInForce();
        }
    }

    @NonNull
    private static File writeExport(@NonNull String rules) throws IOException {
        File file = File.createTempFile("internet-rules", ".tsv",
                ApplicationProvider.<Context>getApplicationContext().getCacheDir());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(rules.getBytes("UTF-8"));
        }
        return file;
    }

    private static void importRules(@NonNull File file, int userId) throws IOException {
        try (RulesImporter importer = new RulesImporter(Collections.singletonList(RuleType.INTERNET),
                new int[]{userId})) {
            importer.addRulesFromPath(Paths.get(file));
            importer.applyRules(true);
        }
    }

    @Nullable
    private static InternetRule savedRule(@NonNull String packageName, int userId) {
        try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userId, false)) {
            return cb.getInternetRule(userId);
        }
    }

    private static final class FakeFirewall implements InternetFirewall {
        final Set<Integer> denied = new HashSet<>();
        @Nullable
        Unavailable unavailable;
        /** Some kernels take the call and keep nothing. */
        boolean dropRules;
        boolean remoteFailure;
        @Nullable
        RuntimeException failure;
        /** A ROM without the hidden calls fails when they're linked. */
        @Nullable
        LinkageError linkageError;
        int changes;

        @Nullable
        @Override
        public Unavailable check() {
            return unavailable;
        }

        @Override
        public boolean isDenied(int uid) {
            return denied.contains(uid);
        }

        @Override
        public void setDenied(int uid, boolean deny) throws RemoteException {
            ++changes;
            if (remoteFailure) throw new RemoteException("Binder died");
            if (failure != null) throw failure;
            if (linkageError != null) throw linkageError;
            if (dropRules) return;
            if (deny) denied.add(uid);
            else denied.remove(uid);
        }
    }
}
