// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/** The app's translatable English strings, looked up by resource name. */
public interface SourceStrings {
    /** Every translatable key, in {@code strings.xml} order. */
    @NonNull
    List<String> keys();

    /** The English source for {@code key}, or {@code null} when this app version has no such string. */
    @Nullable
    SourceString get(@NonNull String key);
}
