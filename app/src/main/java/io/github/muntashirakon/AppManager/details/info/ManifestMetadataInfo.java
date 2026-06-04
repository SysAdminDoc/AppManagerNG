// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.parser.ManifestComponent;
import io.github.muntashirakon.AppManager.apk.parser.ManifestMetadata;
import io.github.muntashirakon.AppManager.apk.parser.ManifestParser;

/**
 * Generic manifest {@code <meta-data>} inventory for the inspected target APK.
 */
public final class ManifestMetadataInfo {
    @NonNull
    public final List<Owner> owners;
    private final int metadataCount;

    private ManifestMetadataInfo(@NonNull List<Owner> owners, int metadataCount) {
        this.owners = Collections.unmodifiableList(owners);
        this.metadataCount = metadataCount;
    }

    @NonNull
    public static ManifestMetadataInfo from(@NonNull ApplicationInfo applicationInfo) {
        if (applicationInfo.publicSourceDir == null || applicationInfo.publicSourceDir.isEmpty()) {
            return empty();
        }
        try {
            List<ManifestMetadata> metadata = new ManifestParser(ApkUtils.getManifestFromApk(
                    new File(applicationInfo.publicSourceDir))).parseMetadata();
            return fromManifest(metadata);
        } catch (Throwable ignore) {
            return empty();
        }
    }

    @NonNull
    static ManifestMetadataInfo empty() {
        return new ManifestMetadataInfo(Collections.emptyList(), 0);
    }

    public boolean hasMetadata() {
        return metadataCount > 0;
    }

    public int getMetadataCount() {
        return metadataCount;
    }

    @NonNull
    public String toDisplayString() {
        if (!hasMetadata()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Owner owner : owners) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(owner.toDisplayTitle());
            for (Entry entry : owner.entries) {
                builder.append("\n  - ").append(entry.toDisplayString());
            }
        }
        return builder.toString();
    }

    @NonNull
    public String toCopyText() {
        StringBuilder builder = new StringBuilder("Owner\tName\tValue\tType");
        for (Owner owner : owners) {
            for (Entry entry : owner.entries) {
                builder.append('\n')
                        .append(owner.toDisplayTitle()).append('\t')
                        .append(entry.name).append('\t')
                        .append(entry.value != null ? entry.value : "").append('\t')
                        .append(entry.getDisplayType());
            }
        }
        return builder.toString();
    }

    @NonNull
    @VisibleForTesting
    static ManifestMetadataInfo fromManifest(@Nullable List<ManifestMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return empty();
        }
        LinkedHashMap<String, MutableOwner> grouped = new LinkedHashMap<>();
        int count = 0;
        for (ManifestMetadata metadata : metadataList) {
            if (metadata == null || metadata.name.trim().isEmpty()) {
                continue;
            }
            String ownerType = normalizeOwnerType(metadata.ownerType);
            String ownerName = metadata.ownerName != null && !metadata.ownerName.isEmpty()
                    ? metadata.ownerName : null;
            String key = ownerType + "\u0000" + (ownerName != null ? ownerName : "");
            MutableOwner owner = grouped.get(key);
            if (owner == null) {
                owner = new MutableOwner(ownerType, ownerName);
                grouped.put(key, owner);
            }
            owner.entries.add(new Entry(metadata.name, metadata.value, metadata.valueType, metadata.resource));
            ++count;
        }
        if (count == 0) {
            return empty();
        }
        List<Owner> owners = new ArrayList<>(grouped.size());
        for (MutableOwner owner : grouped.values()) {
            owners.add(new Owner(owner.ownerType, owner.ownerName, owner.entries));
        }
        return new ManifestMetadataInfo(owners, count);
    }

    @NonNull
    private static String normalizeOwnerType(@Nullable String ownerType) {
        if (ownerType == null || ownerType.isEmpty()) {
            return ManifestMetadata.OWNER_APPLICATION;
        }
        return ownerType;
    }

    @NonNull
    private static String ownerTypeLabel(@NonNull String ownerType) {
        switch (ownerType) {
            case ManifestMetadata.OWNER_APPLICATION:
                return "Application";
            case ManifestComponent.TYPE_ACTIVITY:
                return "Activity";
            case ManifestComponent.TYPE_ACTIVITY_ALIAS:
                return "Activity alias";
            case ManifestComponent.TYPE_SERVICE:
                return "Service";
            case ManifestComponent.TYPE_RECEIVER:
                return "Receiver";
            case ManifestComponent.TYPE_PROVIDER:
                return "Provider";
            default:
                return ownerType;
        }
    }

    private static final class MutableOwner {
        @NonNull
        final String ownerType;
        @Nullable
        final String ownerName;
        @NonNull
        final List<Entry> entries = new ArrayList<>();

        private MutableOwner(@NonNull String ownerType, @Nullable String ownerName) {
            this.ownerType = ownerType;
            this.ownerName = ownerName;
        }
    }

    public static final class Owner {
        @NonNull
        public final String ownerType;
        @Nullable
        public final String ownerName;
        @NonNull
        public final List<Entry> entries;

        private Owner(@NonNull String ownerType, @Nullable String ownerName, @NonNull List<Entry> entries) {
            this.ownerType = ownerType;
            this.ownerName = ownerName;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }

        @NonNull
        public String toDisplayTitle() {
            String label = ownerTypeLabel(ownerType);
            return ownerName != null ? label + " - " + ownerName : label;
        }
    }

    public static final class Entry {
        @NonNull
        public final String name;
        @Nullable
        public final String value;
        @Nullable
        public final String valueType;
        public final boolean resource;

        private Entry(@NonNull String name, @Nullable String value, @Nullable String valueType, boolean resource) {
            this.name = name;
            this.value = value;
            this.valueType = valueType;
            this.resource = resource;
        }

        @NonNull
        public String toDisplayString() {
            StringBuilder builder = new StringBuilder(name).append(" = ");
            builder.append(value != null ? value : "(no value)");
            builder.append(" (").append(getDisplayType()).append(')');
            return builder.toString();
        }

        @NonNull
        private String getDisplayType() {
            if (resource) {
                return "resource";
            }
            if (valueType == null || valueType.isEmpty()) {
                return "unknown";
            }
            switch (valueType) {
                case "STRING":
                    return "string";
                case "BOOLEAN":
                    return "boolean";
                case "DEC":
                case "HEX":
                    return "integer";
                case "REFERENCE":
                case "DYNAMIC_REFERENCE":
                    return "resource";
                default:
                    return valueType.toLowerCase(Locale.ROOT);
            }
        }
    }
}
