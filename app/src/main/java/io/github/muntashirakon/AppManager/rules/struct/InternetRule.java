// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.rules.RuleType;

/**
 * Whether an app may use the network in one user. The firewall works on UIDs, which differ between users, so the
 * rule's name is the user id. {@link #getUid()} is the UID the rule was last put on: an update or a reinstall can
 * change it, and the old UID then has to be let go.
 */
public class InternetRule extends RuleEntry {
    /** The rule isn't on any UID. */
    public static final int NO_UID = -1;

    private final boolean mBlocked;
    private final int mUid;

    public InternetRule(@NonNull String packageName, int userId, boolean blocked, int uid) {
        super(packageName, String.valueOf(requireNonNegativeInt(userId, "userId")), RuleType.INTERNET);
        mBlocked = blocked;
        mUid = uid == NO_UID ? NO_UID : requireNonNegativeInt(uid, "uid");
    }

    public InternetRule(@NonNull String packageName, @NonNull String name, @NonNull RuleValueReader tokenizer) {
        super(packageName, String.valueOf(parseNonNegativeInt(name, "userId")), RuleType.INTERNET);
        if (!tokenizer.hasNext()) {
            throw new IllegalArgumentException("Invalid format: blocked not found");
        }
        mBlocked = parseBoolean(tokenizer.next(), "blocked");
        if (!tokenizer.hasNext()) {
            throw new IllegalArgumentException("Invalid format: uid not found");
        }
        String uid = tokenizer.next().trim();
        mUid = String.valueOf(NO_UID).equals(uid) ? NO_UID : parseNonNegativeInt(uid, "uid");
    }

    /**
     * The entries without other users' internet rules. Every user's rule sits in the app's one rules file, so an
     * export or a backup made for one user would otherwise carry them all.
     */
    @NonNull
    public static List<RuleEntry> withoutOtherUsers(@NonNull List<RuleEntry> entries, int userId) {
        List<RuleEntry> result = new ArrayList<>(entries.size());
        for (RuleEntry entry : entries) {
            if (!(entry instanceof InternetRule) || ((InternetRule) entry).getUserId() == userId) {
                result.add(entry);
            }
        }
        return result;
    }

    public int getUserId() {
        return Integer.parseInt(name);
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public int getUid() {
        return mUid;
    }

    @NonNull
    public InternetRule withUid(int uid) {
        return new InternetRule(packageName, getUserId(), mBlocked, uid);
    }

    /** The same wish for another user. The UID belongs to the old user, so it's dropped. */
    @NonNull
    public InternetRule forUser(int userId) {
        return new InternetRule(packageName, userId, mBlocked, NO_UID);
    }

    @NonNull
    @Override
    public String toString() {
        return "InternetRule{" +
                "packageName='" + packageName + '\'' +
                ", userId=" + name +
                ", blocked=" + mBlocked +
                ", uid=" + mUid +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mBlocked + "\t" + mUid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InternetRule)) return false;
        if (!super.equals(o)) return false;
        InternetRule that = (InternetRule) o;
        return mBlocked == that.mBlocked && mUid == that.mUid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mBlocked, mUid);
    }
}
