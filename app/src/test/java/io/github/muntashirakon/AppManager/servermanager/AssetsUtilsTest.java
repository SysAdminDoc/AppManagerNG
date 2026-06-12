// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(RobolectricTestRunner.class)
public class AssetsUtilsTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void copyFileReplacesSameLengthContentMismatch() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        byte[] assetBytes = readAsset(context, ServerConfig.SERVER_RUNNER_EXEC_NAME);
        byte[] corruptedBytes = assetBytes.clone();
        corruptedBytes[0] = (byte) (corruptedBytes[0] == '#' ? '!' : '#');
        File destFile = tmp.newFile(ServerConfig.SERVER_RUNNER_EXEC_NAME);
        writeFile(destFile, corruptedBytes);
        assertEquals(assetBytes.length, destFile.length());

        AssetsUtils.copyFile(context, ServerConfig.SERVER_RUNNER_EXEC_NAME, destFile, false);

        assertArrayEquals(assetBytes, readFile(destFile));
    }

    private static byte[] readAsset(Context context, String name) throws IOException {
        try (InputStream inputStream = context.getAssets().open(name)) {
            return readAllBytes(inputStream);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return readAllBytes(inputStream);
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return outputStream.toByteArray();
        }
    }

    private static void writeFile(File file, byte[] bytes) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(bytes);
        }
    }
}
