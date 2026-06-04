// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;

/**
 * Per-package memory-tagging posture exposed by {@link ApplicationInfo}.
 *
 * <p>This is a manifest/runtime signal, not proof that the current device can
 * enforce hardware MTE for the package. Actual enforcement still depends on
 * platform runtime policy and hardware support.</p>
 */
public final class MemoryTaggingInfo {
    public static final int STATUS_UNSUPPORTED = 0;
    public static final int STATUS_DEFAULT = 1;
    public static final int STATUS_OFF = 2;
    public static final int STATUS_ASYNC = 3;
    public static final int STATUS_SYNC = 4;

    @IntDef({
            STATUS_UNSUPPORTED,
            STATUS_DEFAULT,
            STATUS_OFF,
            STATUS_ASYNC,
            STATUS_SYNC
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    public final int sdkInt;
    public final boolean allowsNativeHeapPointerTagging;
    public final int memtagMode;
    @Status
    public final int status;

    private MemoryTaggingInfo(int sdkInt, boolean allowsNativeHeapPointerTagging, int memtagMode,
                              @Status int status) {
        this.sdkInt = sdkInt;
        this.allowsNativeHeapPointerTagging = allowsNativeHeapPointerTagging;
        this.memtagMode = memtagMode;
        this.status = status;
    }

    @NonNull
    public static MemoryTaggingInfo from(@NonNull ApplicationInfo info) {
        int memtagMode = ApplicationInfo.MEMTAG_DEFAULT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            memtagMode = info.getMemtagMode();
        }
        return from(Build.VERSION.SDK_INT, ApplicationInfoCompat.getPrivateFlags(info), memtagMode);
    }

    @NonNull
    static MemoryTaggingInfo unsupported(int sdkInt) {
        return new MemoryTaggingInfo(sdkInt, false, ApplicationInfo.MEMTAG_DEFAULT, STATUS_UNSUPPORTED);
    }

    @NonNull
    @VisibleForTesting
    static MemoryTaggingInfo from(int sdkInt, int privateFlags, int memtagMode) {
        boolean allowsPointerTagging = (privateFlags
                & ApplicationInfoCompat.PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING) != 0;
        if (sdkInt < Build.VERSION_CODES.R) {
            return new MemoryTaggingInfo(sdkInt, allowsPointerTagging, memtagMode, STATUS_UNSUPPORTED);
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            switch (memtagMode) {
                case ApplicationInfo.MEMTAG_SYNC:
                    return new MemoryTaggingInfo(sdkInt, allowsPointerTagging, memtagMode, STATUS_SYNC);
                case ApplicationInfo.MEMTAG_ASYNC:
                    return new MemoryTaggingInfo(sdkInt, allowsPointerTagging, memtagMode, STATUS_ASYNC);
                case ApplicationInfo.MEMTAG_OFF:
                    return new MemoryTaggingInfo(sdkInt, allowsPointerTagging, memtagMode, STATUS_OFF);
                case ApplicationInfo.MEMTAG_DEFAULT:
                default:
                    return new MemoryTaggingInfo(sdkInt, allowsPointerTagging, memtagMode, STATUS_DEFAULT);
            }
        }
        return new MemoryTaggingInfo(sdkInt, allowsPointerTagging, memtagMode,
                allowsPointerTagging ? STATUS_DEFAULT : STATUS_OFF);
    }
}
