// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ManifestMetadata {
    public static final String OWNER_APPLICATION = "application";
    public static final String ATTRIBUTE_VALUE = "value";
    public static final String ATTRIBUTE_RESOURCE = "resource";

    @NonNull
    public final String ownerType;
    @Nullable
    public final String ownerName;
    @NonNull
    public final String name;
    @Nullable
    public final String value;
    @Nullable
    public final String valueType;
    public final boolean resource;

    public ManifestMetadata(@NonNull String ownerType, @Nullable String ownerName,
                            @NonNull String name, @Nullable String value,
                            @Nullable String valueType, boolean resource) {
        this.ownerType = ownerType;
        this.ownerName = ownerName;
        this.name = name;
        this.value = value;
        this.valueType = valueType;
        this.resource = resource;
    }
}
