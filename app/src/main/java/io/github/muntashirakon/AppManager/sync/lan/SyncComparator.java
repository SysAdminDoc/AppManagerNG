// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sorts a host's app into the bucket the client shows, and decides whether pulling it is allowed.
 * An APK may only replace an installed app signed by the same key, so a signer mismatch wins over
 * any version difference.
 */
public final class SyncComparator {
    public enum Status {
        NEWER,
        SAME,
        OLDER,
        NOT_INSTALLED,
        SIGNATURE_DIFFERS
    }

    private SyncComparator() {
    }

    @NonNull
    public static Status compare(@NonNull SyncApp remote, @Nullable SyncApp local) {
        if (local == null) {
            return Status.NOT_INSTALLED;
        }
        if (!signersMatch(remote.signers, remote.pastSigners, local.signers)) {
            return Status.SIGNATURE_DIFFERS;
        }
        if (remote.versionCode > local.versionCode) {
            return Status.NEWER;
        }
        if (remote.versionCode == local.versionCode) {
            return Status.SAME;
        }
        return Status.OLDER;
    }

    public static boolean canPull(@NonNull Status status) {
        return status == Status.NEWER || status == Status.NOT_INSTALLED;
    }

    /**
     * True when an APK signed by {@code incoming} (with rotation lineage {@code incomingLineage})
     * can update an app currently signed by {@code installed}: the same signer set, or a single
     * signer whose lineage includes the installed key. Unsigned on either side never matches.
     */
    public static boolean signersMatch(@NonNull List<String> incoming, @NonNull List<String> incomingLineage,
                                       @NonNull List<String> installed) {
        if (incoming.isEmpty() || installed.isEmpty()) {
            return false;
        }
        Set<String> incomingSet = lower(incoming);
        Set<String> installedSet = lower(installed);
        if (incomingSet.equals(installedSet)) {
            return true;
        }
        if (incomingSet.size() == 1 && installedSet.size() == 1) {
            return lower(incomingLineage).containsAll(installedSet);
        }
        return false;
    }

    @NonNull
    private static Set<String> lower(@NonNull List<String> digests) {
        Set<String> out = new HashSet<>(digests.size());
        for (String digest : digests) {
            out.add(digest.toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }
}
