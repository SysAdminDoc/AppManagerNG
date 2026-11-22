// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;

import android.content.pm.ApplicationInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Issue 42: App Info groups an app's split APKs by what each one carries, read from the config.* names bundletool
 * gives them.
 */
@RunWith(RobolectricTestRunner.class)
public class ApkSplitsTest {
    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void languageSplitsAreLanguages() {
        for (String name : new String[]{"config.en", "config.fr", "config.fil", "config.pt_BR", "feature.config.de"}) {
            assertEquals(name, ApkSplits.TYPE_LANGUAGE, ApkSplits.classify(name));
        }
    }

    @Test
    public void densitySplitsAreDensities() {
        for (String name : new String[]{"config.ldpi", "config.mdpi", "config.tvdpi", "config.hdpi", "config.xhdpi",
                "config.xxhdpi", "config.xxxhdpi", "config.nodpi", "config.anydpi", "config.560dpi"}) {
            assertEquals(name, ApkSplits.TYPE_DENSITY, ApkSplits.classify(name));
        }
    }

    @Test
    public void abiSplitsAreAbis() {
        for (String name : new String[]{"config.arm64_v8a", "config.armeabi_v7a", "config.armeabi", "config.x86",
                "config.x86_64", "config.riscv64", "camera.config.arm64_v8a"}) {
            assertEquals(name, ApkSplits.TYPE_ABI, ApkSplits.classify(name));
        }
    }

    @Test
    public void aSplitWithoutAConfigPartIsAFeature() {
        assertEquals(ApkSplits.TYPE_FEATURE, ApkSplits.classify("camera"));
        assertEquals(ApkSplits.TYPE_FEATURE, ApkSplits.classify("dynamic_feature_ar"));
        assertEquals(ApkSplits.TYPE_OTHER, ApkSplits.classify("config.etc2"));
    }

    @Test
    public void theSplitsComeWithTheirSizes() throws IOException {
        ApplicationInfo info = new ApplicationInfo();
        info.splitNames = new String[]{"config.arm64_v8a", "config.en", "camera"};
        info.splitSourceDirs = new String[]{file(3000), file(20), file(500)};

        List<ApkSplits.Split> splits = ApkSplits.from(info);

        assertEquals(3, splits.size());
        assertEquals("config.arm64_v8a", splits.get(0).name);
        assertEquals(ApkSplits.TYPE_ABI, splits.get(0).type);
        assertEquals(3000, splits.get(0).size);
        assertEquals(ApkSplits.TYPE_LANGUAGE, splits.get(1).type);
        assertEquals(20, splits.get(1).size);
        assertEquals(ApkSplits.TYPE_FEATURE, splits.get(2).type);
    }

    @Test
    public void anAppWithoutSplitsHasNone() {
        assertEquals(0, ApkSplits.from(new ApplicationInfo()).size());
    }

    private String file(int size) throws IOException {
        File file = mTemp.newFile();
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(new byte[size]);
        }
        return file.getAbsolutePath();
    }
}
