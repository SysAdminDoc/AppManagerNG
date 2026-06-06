// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.io.fs.VirtualFileSystem;

@RunWith(RobolectricTestRunner.class)
public class FmActivityOptionsTest {
    private static final Uri ARCHIVE_URI = Uri.parse("file:///tmp/archive.zip");

    @Test
    public void readOnlyFlagReflectsConstructor() {
        assertTrue(new FmActivity.Options(ARCHIVE_URI, true, true, false).isReadOnly());
        assertFalse(new FmActivity.Options(ARCHIVE_URI, true, false, false).isReadOnly());
    }

    @Test
    public void parcelPreservesReadOnlyFlag() {
        FmActivity.Options options = new FmActivity.Options(ARCHIVE_URI, true, true, true);
        options.setInitUriForVfs(Uri.parse("vfs://1/classes.dex"));

        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        FmActivity.Options restored = FmActivity.Options.CREATOR.createFromParcel(parcel);
        assertEquals(ARCHIVE_URI, restored.uri);
        assertEquals(Uri.parse("vfs://1/classes.dex"), restored.getInitUriForVfs());
        assertTrue(restored.isVfs());
        assertTrue(restored.isReadOnly());
        assertTrue(restored.isMountDex());
    }

    @Test
    public void vfsMountOptionsFollowReadOnlyFlag() {
        VirtualFileSystem.MountOptions readOnly = FmViewModel.getVfsMountOptions(
                new FmActivity.Options(ARCHIVE_URI, true, true, false));
        VirtualFileSystem.MountOptions readWrite = FmViewModel.getVfsMountOptions(
                new FmActivity.Options(ARCHIVE_URI, true, false, false));

        assertFalse(readOnly.readWrite);
        assertTrue(readWrite.readWrite);
    }
}
