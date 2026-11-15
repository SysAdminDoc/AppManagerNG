// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.internet;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.internet.InternetFirewall.Unavailable;
import io.github.muntashirakon.AppManager.rules.struct.InternetRule;
import io.github.muntashirakon.AppManager.self.PackageEventSync;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Keeps each app's saved internet rule and the device's firewall in step. The saved rule is the wish; the firewall
 * forgets it on every restart and whenever the app gets a new UID, so it's put back after a restart, after the app is
 * installed, updated or removed, and whenever the privileged mode comes up.
 */
public final class InternetAccess {
    private static final String TAG = "InternetAccess";

    public static final String WORK_TAG = "internet_rules";
    @VisibleForTesting
    static final String ALL_RULES_WORK_NAME = "internet_rules_all";
    @VisibleForTesting
    static final String PACKAGE_WORK_PREFIX = "internet_rules_package:";
    /** A file in the app's files folder that exists while an internet rule may be saved. */
    @VisibleForTesting
    public static final String RULES_MARKER = "internet_rules";

    /** Firewall changes and the rule writes that go with them happen one at a time. */
    private static final Object LOCK = new Object();

    /** Finds an app's UID in a user, or {@link InternetRule#NO_UID} when it isn't installed there. */
    @VisibleForTesting
    interface UidResolver {
        int getUid(@NonNull String packageName, @UserIdInt int userId) throws RemoteException;
    }

    /** Where the requests go. Tests swap it so nothing reaches a real WorkManager. */
    @VisibleForTesting
    interface WorkSink {
        void enqueue(@NonNull Context context, @NonNull String uniqueName, @NonNull OneTimeWorkRequest request);
    }

    private static final UidResolver PACKAGE_MANAGER_UIDS = (packageName, userId) -> {
        try {
            // A disabled or hidden (frozen) app keeps its UID, so its rule stays on. Without
            // MATCH_UNINSTALLED_PACKAGES a hidden app reads as gone and its block would be let go.
            return PackageManagerCompat.getApplicationInfo(packageName, PackageManagerCompat.MATCH_DISABLED_COMPONENTS
                    | PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return InternetRule.NO_UID;
        }
    };

    private static final WorkSink WORK_MANAGER_SINK = (context, uniqueName, request) -> {
        try {
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request);
        } catch (IllegalStateException e) {
            Log.w(TAG, "WorkManager is unavailable, so internet rules wait for the next start.", e);
        }
    };

    @NonNull
    private static volatile InternetFirewall sFirewall = new PlatformInternetFirewall();
    @NonNull
    private static volatile UidResolver sUidResolver = PACKAGE_MANAGER_UIDS;
    @NonNull
    private static volatile WorkSink sWorkSink = WORK_MANAGER_SINK;

    private InternetAccess() {
    }

    @VisibleForTesting
    static void setFirewallForTesting(@Nullable InternetFirewall firewall) {
        sFirewall = firewall != null ? firewall : new PlatformInternetFirewall();
    }

    @VisibleForTesting
    static void setUidResolverForTesting(@Nullable UidResolver resolver) {
        sUidResolver = resolver != null ? resolver : PACKAGE_MANAGER_UIDS;
    }

    @VisibleForTesting
    static void setWorkSinkForTesting(@Nullable WorkSink sink) {
        sWorkSink = sink != null ? sink : WORK_MANAGER_SINK;
    }

    /**
     * Where an app's internet access stands in one user.
     */
    public static final class Status {
        /** A rule is saved for the app in this user. */
        public final boolean saved;
        /** The saved wish. Without a rule the app is allowed. */
        public final boolean blocked;
        /** The device does what the wish says right now. */
        public final boolean inForce;
        /** The saved rule names a UID, so a deny may be on it in the kernel. */
        public final boolean holdsUid;
        /** Why the wish isn't in force, or why a new one can't be made. */
        @Nullable
        public final Unavailable unavailable;

        /** A saved rule here is taken to name a UID. */
        @VisibleForTesting
        Status(boolean saved, boolean blocked, boolean inForce, @Nullable Unavailable unavailable) {
            this(saved, blocked, inForce, saved, unavailable);
        }

        Status(boolean saved, boolean blocked, boolean inForce, boolean holdsUid, @Nullable Unavailable unavailable) {
            this.saved = saved;
            this.blocked = blocked;
            this.inForce = inForce;
            this.holdsUid = holdsUid;
            this.unavailable = unavailable;
        }

        /**
         * Whether the switch can be used. A new wish needs a firewall that works. A saved wish that names a UID
         * stays put while no privileged mode can reach the firewall, since a deny on that UID could be neither read
         * nor lifted, and the switch would show the app allowed while it's still blocked. Any other saved wish can
         * be changed: one without a UID is dropped without the firewall, and the rest is saved until it can be made.
         */
        public boolean canChange() {
            if (unavailable == null) {
                return true;
            }
            if (!saved) {
                return false;
            }
            if (unavailable.reason == Unavailable.NO_PRIVILEGED_MODE
                    || unavailable.reason == Unavailable.NO_PERMISSION) {
                return !holdsUid;
            }
            return true;
        }

        /**
         * The line shown under the app's INTERNET row, or {@code null} when the app simply has its access.
         */
        @Nullable
        public String describe(@NonNull Context context) {
            if (unavailable != null) {
                String reason = unavailable.describe(context);
                if (!saved) {
                    return context.getString(R.string.internet_access_unavailable, reason);
                }
                if (holdsUid && (unavailable.reason == Unavailable.NO_PRIVILEGED_MODE
                        || unavailable.reason == Unavailable.NO_PERMISSION)) {
                    // The kernel may still hold a block made before the privileged mode went away
                    return context.getString(R.string.internet_access_unknown, reason);
                }
                return context.getString(R.string.internet_access_not_in_force, reason);
            }
            if (!saved) {
                return null;
            }
            if (!inForce) {
                return context.getString(R.string.internet_access_waiting_for_install);
            }
            // A restart clears the firewall, and the app is online until the rule is back
            return blocked ? context.getString(R.string.internet_access_blocked) : null;
        }

        @NonNull
        @Override
        public String toString() {
            return "Status{saved=" + saved + ", blocked=" + blocked + ", inForce=" + inForce
                    + ", holdsUid=" + holdsUid + ", unavailable=" + unavailable + '}';
        }
    }

    /**
     * Saves the wish and puts it into force if it can.
     */
    @WorkerThread
    @NonNull
    public static Status setBlocked(@NonNull String packageName, @UserIdInt int userId, boolean blocked) {
        synchronized (LOCK) {
            try (RulesFile file = RulesFile.edit(packageName, userId)) {
                InternetRule old = file.getInternetRule(userId);
                if (!blocked && old == null) {
                    // Nothing to save
                    file.setReadOnly();
                } else if (!blocked && old.getUid() == InternetRule.NO_UID) {
                    // Nothing was ever put on a UID, so there's nothing to undo
                    file.deleteInternetRule(userId);
                } else {
                    // The old UID stays on the rule until the firewall lets it go
                    int uid = old != null ? old.getUid() : InternetRule.NO_UID;
                    file.setInternetRule(new InternetRule(packageName, userId, blocked, uid));
                }
            }
            Status status = apply(packageName, userId);
            if (!status.saved) {
                forgetMarkerIfNoRules();
            }
            return status;
        }
    }

    /**
     * Puts the app's saved rule into force in one user and reports where it stands.
     */
    @WorkerThread
    @NonNull
    public static Status apply(@NonNull String packageName, @UserIdInt int userId) {
        synchronized (LOCK) {
            InternetRule rule = readRule(packageName, userId);
            InternetFirewall firewall = sFirewall;
            Unavailable unavailable = firewall.check();
            if (rule == null) {
                return new Status(false, false, true, false, unavailable);
            }
            boolean blocked = rule.isBlocked();
            boolean holdsUid = rule.getUid() != InternetRule.NO_UID;
            if (!blocked && !holdsUid) {
                // Nothing was put on a UID for this rule, as with an import that only says allowed. There's nothing
                // to lift, and a deny that something else put on the app's UID is left alone.
                replaceIfUnchanged(rule, null);
                return new Status(false, false, true, false, unavailable);
            }
            if (unavailable != null && unavailable.reason == Unavailable.ANDROID_VERSION) {
                // Kept for a later Android, but there's nothing to put back after app changes here
                return new Status(true, blocked, false, holdsUid, unavailable);
            }
            noteRuleSaved();
            if (unavailable != null) {
                return new Status(true, blocked, false, holdsUid, unavailable);
            }
            try {
                int uid = sUidResolver.getUid(packageName, userId);
                int oldUid = rule.getUid();
                if (oldUid != InternetRule.NO_UID && oldUid != uid
                        && findOtherBlockedRule(oldUid, packageName, userId) == null) {
                    // A reinstall moved the app to a new UID, which may now belong to some other app
                    firewall.setDenied(oldUid, false);
                }
                if (uid == InternetRule.NO_UID) {
                    if (blocked) {
                        // Kept for when the app comes back
                        replaceIfUnchanged(rule, rule.withUid(InternetRule.NO_UID));
                        return new Status(true, true, false, false, null);
                    }
                    replaceIfUnchanged(rule, null);
                    return new Status(false, false, true, false, null);
                }
                if (blocked) {
                    // Saved first, so a crash between the two still leaves a record of the UID to let go
                    InternetRule held = rule.withUid(uid);
                    if (!replaceIfUnchanged(rule, held)) {
                        // A new wish was saved meanwhile, as by an import, and that one goes into force instead
                        return apply(packageName, userId);
                    }
                    try {
                        firewall.setDenied(uid, true);
                    } catch (RuntimeException | LinkageError e) {
                        // Refused, failed, or the call doesn't exist, so nothing went on a new UID and the rule
                        // mustn't claim it. A different old UID was either let go above or is held by another
                        // blocked rule, so this rule drops it too, and allowing the app again just drops the rule.
                        // A dead privileged mode (RemoteException) leaves the UID on, as the deny may have gone in.
                        replaceIfUnchanged(held, rule.withUid(oldUid == uid ? uid : InternetRule.NO_UID));
                        throw e;
                    }
                } else {
                    String sharer = findOtherBlockedRule(uid, packageName, userId);
                    if (sharer != null) {
                        // The firewall works on UIDs, so allowing this app would also allow the other one
                        replaceIfUnchanged(rule, rule.withUid(uid));
                        return new Status(true, false, false, true, new Unavailable(Unavailable.SHARED_UID, sharer));
                    }
                    firewall.setDenied(uid, false);
                }
                boolean denied = firewall.isDenied(uid);
                if (denied != blocked) {
                    Log.w(TAG, "The firewall reads %s as %s after it was asked for %s", packageName,
                            denied ? "blocked" : "allowed", blocked ? "blocked" : "allowed");
                    return new Status(true, blocked, false, true, new Unavailable(Unavailable.PLATFORM, null));
                }
                if (!blocked) {
                    // Allowed and nothing left on any UID, so the rule has done its job
                    replaceIfUnchanged(rule, null);
                    return new Status(false, false, true, false, null);
                }
                return new Status(true, true, true, true, null);
            } catch (SecurityException e) {
                Log.w(TAG, "The firewall refused a change for %s", e, packageName);
                Unavailable now = firewall.check();
                return failedStatus(packageName, userId, now != null ? now
                        : new Unavailable(Unavailable.NO_PERMISSION, e.getMessage()));
            } catch (RemoteException e) {
                Log.w(TAG, "The privileged mode went away while changing %s", e, packageName);
                return failedStatus(packageName, userId,
                        new Unavailable(Unavailable.NO_PRIVILEGED_MODE, e.getMessage()));
            } catch (RuntimeException | LinkageError e) {
                // LinkageError: a ROM without the hidden firewall calls
                Log.w(TAG, "The firewall failed for %s", e, packageName);
                return failedStatus(packageName, userId, new Unavailable(Unavailable.PLATFORM, e.getMessage()));
            }
        }
    }

    /**
     * Puts every saved rule into force, as after a restart, when the kernel has forgotten all of them.
     */
    @WorkerThread
    public static void applyAll() {
        synchronized (LOCK) {
            for (String packageName : ComponentUtils.getAllPackagesWithRules(ContextUtils.getContext())) {
                for (InternetRule rule : readRules(packageName)) {
                    Status status = apply(packageName, rule.getUserId());
                    if (!status.inForce) {
                        Log.w(TAG, "Internet rule for %s in user %d isn't in force: %s", packageName,
                                rule.getUserId(), status);
                    }
                }
            }
        }
    }

    /**
     * Asks for one app's rule to be put back in the background, or every rule when {@code packageName} is
     * {@code null}. An app without a rule is skipped.
     */
    @WorkerThread
    public static void applySoon(@NonNull Context context, @Nullable String packageName, @UserIdInt int userId) {
        if (packageName != null && readRule(packageName, userId) == null) {
            return;
        }
        sWorkSink.enqueue(context, uniqueWorkName(packageName, userId), buildRequest(packageName, userId));
    }

    /**
     * Asks for the app's rule to be put back in every user that has one. An install or removal seen in one user can
     * move the app to a new app ID, and with it the UID it has in every other user.
     */
    @WorkerThread
    public static void applySoonInEveryUser(@NonNull Context context, @NonNull String packageName) {
        for (InternetRule rule : readRules(packageName)) {
            int userId = rule.getUserId();
            sWorkSink.enqueue(context, uniqueWorkName(packageName, userId), buildRequest(packageName, userId));
        }
    }

    /**
     * Whether an internet rule may be saved, read without opening every rules file, so it's fine on the main
     * thread. The app change sync stays scheduled while this is true.
     */
    @AnyThread
    public static boolean mightHaveRules(@NonNull Context context) {
        return new File(context.getFilesDir(), RULES_MARKER).exists();
    }

    /**
     * Whether this device can hold per-app internet rules at all. Below Android 14 a saved rule is only kept for a
     * later version, so nothing needs a privileged mode for it.
     */
    @WorkerThread
    static boolean canHoldRules() {
        Unavailable unavailable = sFirewall.check();
        return unavailable == null || unavailable.reason != Unavailable.ANDROID_VERSION;
    }

    /**
     * Drops the marker once no rule is left, so the app change sync can stop if nothing else needs it.
     */
    @WorkerThread
    static void forgetMarkerIfNoRules() {
        // Under the lock, so a rule saved meanwhile can't find the marker still there and then lose it
        synchronized (LOCK) {
            Context context = ContextUtils.getContext();
            File marker = new File(context.getFilesDir(), RULES_MARKER);
            if (marker.exists() && !hasAnyRule() && marker.delete()) {
                rescheduleAppChangeSync(context);
            }
        }
    }

    private static void noteRuleSaved() {
        Context context = ContextUtils.getContext();
        File marker = new File(context.getFilesDir(), RULES_MARKER);
        if (marker.exists()) {
            return;
        }
        try {
            if (marker.createNewFile()) {
                // A reinstall has to be seen while AppManagerNG isn't running, or the new UID goes unblocked
                rescheduleAppChangeSync(context);
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not note that an internet rule is saved.", e);
        }
    }

    private static void rescheduleAppChangeSync(@NonNull Context context) {
        try {
            PackageEventSync.scheduleOrCancel(context);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not update the app change sync.", e);
        }
    }

    @WorkerThread
    public static boolean hasAnyRule() {
        for (String packageName : ComponentUtils.getAllPackagesWithRules(ContextUtils.getContext())) {
            if (!readRules(packageName).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    @NonNull
    static String uniqueWorkName(@Nullable String packageName, @UserIdInt int userId) {
        return packageName == null ? ALL_RULES_WORK_NAME : PACKAGE_WORK_PREFIX + packageName + ":" + userId;
    }

    @NonNull
    private static OneTimeWorkRequest buildRequest(@Nullable String packageName, @UserIdInt int userId) {
        Data.Builder input = new Data.Builder().putInt(InternetRuleWorker.INPUT_USER, userId);
        if (packageName != null) {
            input.putString(InternetRuleWorker.INPUT_PACKAGE, packageName);
        }
        return new OneTimeWorkRequest.Builder(InternetRuleWorker.class)
                .setInputData(input.build())
                .addTag(WORK_TAG)
                .build();
    }

    /**
     * Where the app stands after a failed change, read from the rule saved now. A failed block may already have taken
     * its UID back off the rule.
     */
    @NonNull
    private static Status failedStatus(@NonNull String packageName, @UserIdInt int userId,
                                       @NonNull Unavailable unavailable) {
        InternetRule rule = readRule(packageName, userId);
        if (rule == null) {
            return new Status(false, false, true, false, unavailable);
        }
        return new Status(true, rule.isBlocked(), false, rule.getUid() != InternetRule.NO_UID, unavailable);
    }

    @Nullable
    private static InternetRule readRule(@NonNull String packageName, @UserIdInt int userId) {
        try (RulesFile file = RulesFile.read(packageName, userId)) {
            return file.getInternetRule(userId);
        }
    }

    @NonNull
    private static List<InternetRule> readRules(@NonNull String packageName) {
        try (RulesFile file = RulesFile.read(packageName, 0)) {
            return file.getAll(InternetRule.class);
        }
    }

    /**
     * The package of another app whose saved rule blocks this UID, as apps sharing a UID do.
     */
    @Nullable
    private static String findOtherBlockedRule(int uid, @NonNull String packageName, @UserIdInt int userId) {
        for (String otherPackage : ComponentUtils.getAllPackagesWithRules(ContextUtils.getContext())) {
            for (InternetRule rule : readRules(otherPackage)) {
                if (rule.isBlocked() && rule.getUid() == uid
                        && !(otherPackage.equals(packageName) && rule.getUserId() == userId)) {
                    return otherPackage;
                }
            }
        }
        return null;
    }

    /**
     * Replaces the saved rule only if it's still {@code expected}, so a wish saved meanwhile isn't overwritten.
     *
     * @param replacement {@code null} to delete it
     * @return {@code false} when another wish was saved meanwhile
     */
    private static boolean replaceIfUnchanged(@NonNull InternetRule expected, @Nullable InternetRule replacement) {
        int userId = expected.getUserId();
        boolean deleted = false;
        try (RulesFile file = RulesFile.edit(expected.packageName, userId)) {
            InternetRule current = file.getInternetRule(userId);
            if (!expected.equals(current)) {
                file.setReadOnly();
                return false;
            }
            if (expected.equals(replacement)) {
                // Nothing to write
                file.setReadOnly();
            } else if (replacement == null) {
                file.deleteInternetRule(userId);
                deleted = true;
            } else {
                file.setInternetRule(replacement);
            }
        }
        if (deleted) {
            // Closing saved the file, so the last rule may be gone now
            forgetMarkerIfNoRules();
        }
        return true;
    }

    /**
     * The app's rules file. Reading doesn't wait for a writer; editing holds the app's write lock until closed,
     * and closing saves.
     */
    private static final class RulesFile extends RulesStorageManager {
        @NonNull
        static RulesFile read(@NonNull String packageName, @UserIdInt int userId) {
            return new RulesFile(packageName, userId, false);
        }

        @NonNull
        static RulesFile edit(@NonNull String packageName, @UserIdInt int userId) {
            RulesFile file = new RulesFile(packageName, userId, true);
            file.setMutable();
            return file;
        }

        private RulesFile(@NonNull String packageName, @UserIdInt int userId, boolean lockBeforeLoad) {
            super(packageName, userId, lockBeforeLoad);
        }
    }
}
