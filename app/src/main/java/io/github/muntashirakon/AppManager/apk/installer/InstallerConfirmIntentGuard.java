// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Gate for the confirmation {@link Intent} the platform hands back through
 * {@link Intent#EXTRA_INTENT} on {@code STATUS_PENDING_USER_ACTION}.
 *
 * <p>That intent arrives inside a broadcast delivered through a <em>mutable</em>
 * {@code PendingIntent} — the platform installer has to be able to fill it in. Anything that can
 * make us deliver such a broadcast can therefore also choose the payload, and we launch it. An
 * unvalidated forward is a textbook intent-redirection primitive: the payload runs with our
 * identity and can carry our URI grants along with it.
 *
 * <p>The policy is that we, not the caller, decide where the payload goes. A payload that already
 * names a foreign target keeps it. A payload that names nothing is not trusted to resolve itself
 * at launch time: we resolve it here and forward it only when it lands on an activity inside a
 * system package, bound explicitly to that component.
 *
 * <p>The second form is not an edge case. AOSP builds the uninstall confirmation as a bare
 * {@code ACTION_UNINSTALL_PACKAGE} carrying only a {@code package:} data URI, with no component
 * and no package, and some ROMs leave the install confirmation implicit too. Rejecting those
 * outright takes the system prompt away from the user and leaves the session hanging.
 *
 * <p>Whatever the outcome, the caller's flags are dropped — least of all URI-permission grants.
 */
public final class InstallerConfirmIntentGuard {
    private static final int URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    /** No payload was supplied at all. */
    public static final String RULE_ABSENT = "absent";
    /** The payload named a foreign target itself and was forwarded unchanged. */
    public static final String RULE_EXPLICIT_TARGET = "explicit-target";
    /** The payload named nothing and was resolved to a system activity. */
    public static final String RULE_RESOLVED_SYSTEM = "resolved-system";
    /** The payload named nothing and there was no resolver available to check it. */
    public static final String RULE_NO_RESOLVER = "no-resolver";
    /** The payload named nothing and resolved to nothing. */
    public static final String RULE_UNRESOLVABLE = "unresolvable";
    /** The payload resolved to a package that is not part of the system image. */
    public static final String RULE_NOT_SYSTEM = "not-system";
    /** The payload pointed back into this application. */
    public static final String RULE_SELF_REDIRECT = "self-redirect";

    private InstallerConfirmIntentGuard() {
    }

    /**
     * The outcome of a single guard evaluation, including why it went that way. A rejection is
     * worth logging: the rule and the target it resolved to are what identify a ROM whose
     * confirmation payload does not look like AOSP's.
     */
    public static final class Decision {
        /** The sanitized payload, or {@code null} when it must not be forwarded. */
        @Nullable
        public final Intent intent;
        /** One of the {@code RULE_*} constants. */
        @NonNull
        public final String rule;
        /** The target the payload named or resolved to, when there was one. */
        @Nullable
        public final String target;

        private Decision(@Nullable Intent intent, @NonNull String rule, @Nullable String target) {
            this.intent = intent;
            this.rule = rule;
            this.target = target;
        }

        public boolean isForwarded() {
            return intent != null;
        }

        @NonNull
        @Override
        public String toString() {
            return "rule=" + rule + ", target=" + target + ", forwarded=" + isForwarded();
        }
    }

    /**
     * Evaluate a payload without a resolver. Equivalent to {@link #decide} with a {@code null}
     * {@link PackageManager}: a payload that names no target cannot be checked, so it is rejected.
     *
     * @param confirmIntent the intent supplied in {@link Intent#EXTRA_INTENT}.
     * @param selfPackage   this application's package name.
     * @return a sanitized copy safe to launch, or {@code null} when it must not be forwarded.
     */
    @Nullable
    public static Intent sanitize(@Nullable Intent confirmIntent, @NonNull String selfPackage) {
        return decide(confirmIntent, selfPackage, null).intent;
    }

    /**
     * @param confirmIntent  the intent supplied in {@link Intent#EXTRA_INTENT}.
     * @param selfPackage    this application's package name.
     * @param packageManager used to resolve a payload that names no target. When {@code null},
     *                       such a payload is rejected instead of resolved.
     * @return the decision, never {@code null}.
     */
    @NonNull
    public static Decision decide(@Nullable Intent confirmIntent, @NonNull String selfPackage,
                                  @Nullable PackageManager packageManager) {
        if (confirmIntent == null) {
            return new Decision(null, RULE_ABSENT, null);
        }
        ComponentName component = confirmIntent.getComponent();
        String targetPackage = component != null ? component.getPackageName() : confirmIntent.getPackage();
        if (targetPackage != null && !targetPackage.isEmpty()) {
            if (selfPackage.equals(targetPackage)) {
                // Redirecting back into our own components is the confused-deputy case itself.
                return new Decision(null, RULE_SELF_REDIRECT, targetPackage);
            }
            return new Decision(forwardable(confirmIntent), RULE_EXPLICIT_TARGET, targetPackage);
        }
        // The payload names nothing. The platform's own uninstall confirmation looks exactly like
        // this, so resolve it rather than assuming it is hostile.
        if (packageManager == null) {
            return new Decision(null, RULE_NO_RESOLVER, null);
        }
        ActivityInfo activityInfo = resolveActivity(confirmIntent, packageManager);
        if (activityInfo == null || activityInfo.packageName == null || activityInfo.name == null) {
            return new Decision(null, RULE_UNRESOLVABLE, null);
        }
        ComponentName resolved = new ComponentName(activityInfo.packageName, activityInfo.name);
        String resolvedTarget = resolved.flattenToShortString();
        if (selfPackage.equals(activityInfo.packageName)) {
            return new Decision(null, RULE_SELF_REDIRECT, resolvedTarget);
        }
        if (!isSystem(activityInfo.applicationInfo)) {
            return new Decision(null, RULE_NOT_SYSTEM, resolvedTarget);
        }
        Intent sanitized = forwardable(confirmIntent);
        // Bind it to the component we resolved, so the choice of target is ours and cannot change
        // between this check and the launch.
        sanitized.setComponent(resolved);
        return new Decision(sanitized, RULE_RESOLVED_SYSTEM, resolvedTarget);
    }

    /**
     * @return whether {@code confirmIntent} carries URI-permission grants, i.e. whether the guard
     * had to strip any.
     */
    public static boolean carriesUriGrants(@Nullable Intent confirmIntent) {
        return confirmIntent != null && (confirmIntent.getFlags() & URI_GRANT_FLAGS) != 0;
    }

    @NonNull
    private static Intent forwardable(@NonNull Intent confirmIntent) {
        Intent sanitized = new Intent(confirmIntent);
        // Drop every caller-chosen flag, then re-add only what launching from a receiver needs.
        // In particular this strips URI grants, which would otherwise be passed on with our
        // permission to read them.
        sanitized.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return sanitized;
    }

    @Nullable
    private static ActivityInfo resolveActivity(@NonNull Intent confirmIntent,
                                                @NonNull PackageManager packageManager) {
        try {
            ResolveInfo resolveInfo = packageManager.resolveActivity(confirmIntent, 0);
            return resolveInfo != null ? resolveInfo.activityInfo : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean isSystem(@Nullable ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            return false;
        }
        int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return (applicationInfo.flags & systemFlags) != 0;
    }
}
