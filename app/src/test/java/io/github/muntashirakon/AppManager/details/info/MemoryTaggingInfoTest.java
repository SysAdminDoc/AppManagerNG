// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import org.junit.Test;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;

public class MemoryTaggingInfoTest {
    @Test
    public void belowAndroid11IsUnsupported() {
        MemoryTaggingInfo info = MemoryTaggingInfo.from(
                Build.VERSION_CODES.Q,
                ApplicationInfoCompat.PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING,
                ApplicationInfo.MEMTAG_SYNC);

        assertEquals(MemoryTaggingInfo.STATUS_UNSUPPORTED, info.status);
        assertTrue(info.allowsNativeHeapPointerTagging);
    }

    @Test
    public void android11UsesPointerTaggingPrivateFlag() {
        MemoryTaggingInfo allowed = MemoryTaggingInfo.from(
                Build.VERSION_CODES.R,
                ApplicationInfoCompat.PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING,
                ApplicationInfo.MEMTAG_DEFAULT);
        MemoryTaggingInfo disabled = MemoryTaggingInfo.from(
                Build.VERSION_CODES.R,
                0,
                ApplicationInfo.MEMTAG_DEFAULT);

        assertEquals(MemoryTaggingInfo.STATUS_DEFAULT, allowed.status);
        assertTrue(allowed.allowsNativeHeapPointerTagging);
        assertEquals(MemoryTaggingInfo.STATUS_OFF, disabled.status);
        assertFalse(disabled.allowsNativeHeapPointerTagging);
    }

    @Test
    public void android12PlusUsesMemtagMode() {
        assertEquals(MemoryTaggingInfo.STATUS_SYNC, MemoryTaggingInfo.from(
                Build.VERSION_CODES.S,
                0,
                ApplicationInfo.MEMTAG_SYNC).status);
        assertEquals(MemoryTaggingInfo.STATUS_ASYNC, MemoryTaggingInfo.from(
                Build.VERSION_CODES.S,
                0,
                ApplicationInfo.MEMTAG_ASYNC).status);
        assertEquals(MemoryTaggingInfo.STATUS_OFF, MemoryTaggingInfo.from(
                Build.VERSION_CODES.S,
                ApplicationInfoCompat.PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING,
                ApplicationInfo.MEMTAG_OFF).status);
        assertEquals(MemoryTaggingInfo.STATUS_DEFAULT, MemoryTaggingInfo.from(
                Build.VERSION_CODES.S,
                0,
                ApplicationInfo.MEMTAG_DEFAULT).status);
    }
}
