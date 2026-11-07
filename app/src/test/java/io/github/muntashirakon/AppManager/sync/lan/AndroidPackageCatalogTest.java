// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class AndroidPackageCatalogTest {
    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void offersTheBaseThenTheSplitsByName() throws IOException {
        ApplicationInfo info = new ApplicationInfo();
        info.sourceDir = file("base.apk").getPath();
        info.splitSourceDirs = new String[]{file("split_config.arm64_v8a.apk").getPath(),
                file("split_config.en.apk").getPath()};

        Map<String, File> files = AndroidPackageCatalog.filesOf(info);

        assertNotNull(files);
        assertEquals(Arrays.asList("base.apk", "split_config.arm64_v8a.apk", "split_config.en.apk"),
                new ArrayList<>(files.keySet()));
        assertEquals(new File(info.sourceDir), files.get("base.apk"));
    }

    @Test
    public void incompleteOrOddInstallsAreNotOffered() throws IOException {
        ApplicationInfo missing = new ApplicationInfo();
        missing.sourceDir = file("base.apk").getPath();
        missing.splitSourceDirs = new String[]{new File(mTemp.getRoot(), "gone.apk").getPath()};
        assertNull(AndroidPackageCatalog.filesOf(missing));

        ApplicationInfo noBase = new ApplicationInfo();
        assertNull(AndroidPackageCatalog.filesOf(noBase));

        ApplicationInfo wrongSuffix = new ApplicationInfo();
        wrongSuffix.sourceDir = file("base.zip").getPath();
        assertNull(AndroidPackageCatalog.filesOf(wrongSuffix));

        File otherDir = mTemp.newFolder("other");
        File duplicate = new File(otherDir, "base.apk");
        assertEquals(true, duplicate.createNewFile());
        ApplicationInfo duplicated = new ApplicationInfo();
        duplicated.sourceDir = file("base.apk").getPath();
        duplicated.splitSourceDirs = new String[]{duplicate.getPath()};
        assertNull(AndroidPackageCatalog.filesOf(duplicated));

        ApplicationInfo directory = new ApplicationInfo();
        directory.sourceDir = mTemp.newFolder("dir.apk").getPath();
        assertNull(AndroidPackageCatalog.filesOf(directory));

        ApplicationInfo tooMany = new ApplicationInfo();
        tooMany.sourceDir = file("base.apk").getPath();
        String[] splits = new String[SyncMessages.MAX_FILES];
        for (int i = 0; i < splits.length; ++i) {
            splits[i] = file("split_" + i + ".apk").getPath();
        }
        tooMany.splitSourceDirs = splits;
        assertNull(AndroidPackageCatalog.filesOf(tooMany));
    }

    @NonNull
    private File file(@NonNull String name) throws IOException {
        File file = new File(mTemp.getRoot(), name);
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Could not create " + file);
        }
        return file;
    }
}
