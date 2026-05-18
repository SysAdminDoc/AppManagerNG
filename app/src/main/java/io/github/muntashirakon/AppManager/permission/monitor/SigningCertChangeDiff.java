// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure-function diff between two {@link SigningCertSnapshot}s. A signing-cert
 * change is a serious security signal — either the publisher rotated the key
 * (V3 scheme, expected) or the package was replaced by an attacker with a
 * different key (uncommon but high-impact).
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>{@link Kind#UNCHANGED}: every cert in {@code before} is still present
 *       in {@code after}.</li>
 *   <li>{@link Kind#ROTATED_ADDITIVE}: {@code after} is a strict superset of
 *       {@code before} — the package added new signers without removing any
 *       known ones. Treated as a normal rotation but worth surfacing.</li>
 *   <li>{@link Kind#REPLACED}: {@code after} drops at least one signer that
 *       was present in {@code before}. The most alarming case — neither side
 *       can be reconciled with the other, and the user should be alerted.</li>
 * </ul>
 *
 * <p>The {@link Result#isInteresting()} signal fires for ROTATED_ADDITIVE and
 * REPLACED. Callers can branch on {@link Result#kind} for tone.
 */
public final class SigningCertChangeDiff {

    public enum Kind {
        UNCHANGED,
        ROTATED_ADDITIVE,
        REPLACED
    }

    public static final class Result {
        @NonNull
        public final String packageName;
        public final long beforeVersionCode;
        public final long afterVersionCode;
        @NonNull
        public final Kind kind;
        @NonNull
        public final Set<String> added;
        @NonNull
        public final Set<String> removed;

        Result(@NonNull String packageName, long beforeVersionCode, long afterVersionCode,
               @NonNull Kind kind, @NonNull Set<String> added, @NonNull Set<String> removed) {
            this.packageName = packageName;
            this.beforeVersionCode = beforeVersionCode;
            this.afterVersionCode = afterVersionCode;
            this.kind = kind;
            this.added = new TreeSet<>(added);
            this.removed = new TreeSet<>(removed);
        }

        public boolean isInteresting() {
            return kind != Kind.UNCHANGED;
        }
    }

    private SigningCertChangeDiff() {
    }

    @VisibleForTesting
    @NonNull
    public static Result compute(@NonNull String packageName,
                                 @NonNull SigningCertSnapshot before,
                                 @NonNull SigningCertSnapshot after) {
        Set<String> added = new LinkedHashSet<>(after.certShas256);
        added.removeAll(before.certShas256);
        Set<String> removed = new LinkedHashSet<>(before.certShas256);
        removed.removeAll(after.certShas256);
        Kind kind;
        if (added.isEmpty() && removed.isEmpty()) {
            kind = Kind.UNCHANGED;
        } else if (removed.isEmpty()) {
            kind = Kind.ROTATED_ADDITIVE;
        } else {
            kind = Kind.REPLACED;
        }
        return new Result(packageName, before.versionCode, after.versionCode, kind, added, removed);
    }
}
