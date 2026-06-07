// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PackageInstallerActivityTest {
    @Test
    public void getBatchInstallInstanceCopiesUrisBeforeBuildingIntent() {
        Uri first = Uri.parse("content://example.test/apk/one.apk");
        Uri second = Uri.parse("content://example.test/apk/two.apks");
        Uri replacement = Uri.parse("content://example.test/apk/replacement.apk");
        ArrayList<Uri> input = new ArrayList<>(Arrays.asList(first, second));

        Intent intent = PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(), input);

        input.clear();
        input.add(replacement);
        List<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertNotNull(streams);
        assertEquals(Arrays.asList(first, second), streams);
        assertNotNull(intent.getClipData());
        assertEquals(2, intent.getClipData().getItemCount());
        assertEquals(first, intent.getClipData().getItemAt(0).getUri());
        assertEquals(second, intent.getClipData().getItemAt(1).getUri());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue(intent.getBooleanExtra(PackageInstallerActivity.EXTRA_BATCH_INSTALL, false));
    }

    @Test
    public void getBatchInstallInstanceRejectsNullUri() {
        ArrayList<Uri> input = new ArrayList<>();
        input.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> PackageInstallerActivity.getBatchInstallInstance(RuntimeEnvironment.getApplication(), input));
    }
}
