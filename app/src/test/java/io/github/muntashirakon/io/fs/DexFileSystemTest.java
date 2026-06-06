// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.convert.OABConverter;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class DexFileSystemTest {
    private File mDexFile;

    @Before
    public void setUp() {
        ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
        mDexFile = new File(new File(Objects.requireNonNull(classLoader.getResource(OABConverter.PATH_SUFFIX))
                .getFile(), "ademar.textlauncher"), "classes.dex");
    }

    @Test
    public void defaultMountOptionsUseFallbackApiLevel() throws Exception {
        int fsId = VirtualFileSystem.mount(Uri.fromFile(new File("/tmp/dex_api_default")), Paths.get(mDexFile),
                ContentType2.DEX.getMimeType());
        try {
            DexFileSystem fs = (DexFileSystem) Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));

            assertEquals(-1, fs.getApiLevel());
        } finally {
            VirtualFileSystem.unmount(fsId);
        }
    }

    @Test
    public void dexApiLevelFollowsMountOptions() throws Exception {
        VirtualFileSystem.MountOptions options = new VirtualFileSystem.MountOptions.Builder()
                .setDexApiLevel(23)
                .build();
        int fsId = VirtualFileSystem.mount(Uri.fromFile(new File("/tmp/dex_api_23")), Paths.get(mDexFile),
                ContentType2.DEX.getMimeType(), options);
        try {
            DexFileSystem fs = (DexFileSystem) Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));

            assertEquals(23, fs.getApiLevel());
        } finally {
            VirtualFileSystem.unmount(fsId);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void dexApiLevelRejectsInvalidValues() {
        new VirtualFileSystem.MountOptions.Builder().setDexApiLevel(-2);
    }
}
