// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Build;
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
        assertEquals(Build.VERSION.SDK_INT, readOnly.dexApiLevel);
        assertEquals(Build.VERSION.SDK_INT, readWrite.dexApiLevel);
    }

    @Test
    public void drawerDisplayNameFlattensControlsDefusesFormulaAndFallsBack() {
        FmActivity.Options options = new FmActivity.Options(Uri.parse("file:///sdcard/fallback"), false, false, false);
        FmDrawerItem formulaName = new FmDrawerItem(1, "\t=payload\nfolder", options,
                FmDrawerItem.ITEM_TYPE_FAVORITE);
        FmDrawerItem blankName = new FmDrawerItem(2, "\r\n\t", options, FmDrawerItem.ITEM_TYPE_FAVORITE);

        assertEquals("' =payload folder", FmActivity.DrawerRecyclerViewAdapter.getDrawerDisplayName(formulaName));
        assertEquals("/sdcard/fallback", FmActivity.DrawerRecyclerViewAdapter.getDrawerDisplayName(blankName));
    }

    @Test
    public void drawerShortcutActionAcceptsHomeAndBookmarksOnly() {
        assertTrue(FmActivity.isDrawerShortcutAction(android.R.id.home));
        assertTrue(FmActivity.isDrawerShortcutAction(io.github.muntashirakon.AppManager.R.id.action_bookmarks));
        assertFalse(FmActivity.isDrawerShortcutAction(io.github.muntashirakon.AppManager.R.id.action_refresh));
    }
}
