// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ManifestSdkLibrary {
    @NonNull
    public final String name;
    public final long versionMajor;
    @Nullable
    public final String certDigest;

    public ManifestSdkLibrary(@NonNull String name, long versionMajor, @Nullable String certDigest) {
        this.name = name;
        this.versionMajor = versionMajor;
        this.certDigest = certDigest;
    }
}
