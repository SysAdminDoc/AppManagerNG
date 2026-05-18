// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.snapshot;

import androidx.annotation.NonNull;

/**
 * Thrown when a snapshot bundle is malformed, written by a future schema, or
 * contains entries that would escape the import target directory. Callers must
 * treat this as user-facing input validation, not an internal error.
 */
public class SnapshotImportException extends Exception {
    public SnapshotImportException(@NonNull String message) {
        super(message);
    }
}
