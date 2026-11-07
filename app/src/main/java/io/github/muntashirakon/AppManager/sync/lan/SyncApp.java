// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One installed app as Local sync describes it: what a host lists and what the client compares
 * against its own install. Signer digests are lowercase hex SHA-256 of each certificate, the same
 * form the App details screen shows.
 */
public final class SyncApp {
    public static final class ApkFileInfo {
        @NonNull
        public final String name;
        public final long size;

        public ApkFileInfo(@NonNull String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    @NonNull
    public final String packageName;
    @NonNull
    public final String label;
    public final long versionCode;
    @NonNull
    public final String versionName;
    public final boolean system;
    /**
     * Current signers, sorted.
     */
    @NonNull
    public final List<String> signers;
    /**
     * Earlier signers from a key rotation lineage, possibly including the current one.
     */
    @NonNull
    public final List<String> pastSigners;
    /**
     * The base APK first, then the splits.
     */
    @NonNull
    public final List<ApkFileInfo> files;

    public SyncApp(@NonNull String packageName, @NonNull String label, long versionCode,
                   @NonNull String versionName, boolean system, @NonNull List<String> signers,
                   @NonNull List<String> pastSigners, @NonNull List<ApkFileInfo> files) {
        this.packageName = packageName;
        this.label = label;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.system = system;
        List<String> sorted = new ArrayList<>(signers);
        Collections.sort(sorted);
        this.signers = Collections.unmodifiableList(sorted);
        this.pastSigners = Collections.unmodifiableList(new ArrayList<>(pastSigners));
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
    }

    public long totalSize() {
        long total = 0;
        for (ApkFileInfo file : files) {
            total += file.size;
        }
        return total;
    }
}
