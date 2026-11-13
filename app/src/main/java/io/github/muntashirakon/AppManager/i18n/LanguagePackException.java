// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;

/** A language file that can't be imported at all, with the reason the UI shows. */
public final class LanguagePackException extends Exception {
    public enum Reason {
        NOT_A_PACK,
        TOO_LARGE,
        NEWER_SCHEMA,
        BAD_CODE,
        BAD_NAME,
        ENGLISH,
        NOTHING_USABLE,
    }

    @NonNull
    public final Reason reason;

    public LanguagePackException(@NonNull Reason reason, @NonNull String message) {
        super(message);
        this.reason = reason;
    }

    public LanguagePackException(@NonNull Reason reason, @NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
