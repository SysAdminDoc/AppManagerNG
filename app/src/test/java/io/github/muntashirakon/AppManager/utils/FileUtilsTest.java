// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.progress.ProgressHandler;

@RunWith(RobolectricTestRunner.class)
public class FileUtilsTest {
    @Test
    public void calculateProgress_returnsBaseForInvalidSizes() {
        assertEquals(25f, FileUtils.calculateProgress(25f, 10, 0), 0f);
        assertEquals(25f, FileUtils.calculateProgress(25f, 10, -1), 0f);
        assertEquals(25f, FileUtils.calculateProgress(25f, 0, 100), 0f);
    }

    @Test
    public void calculateProgress_clampsToComplete() {
        assertEquals(100f, FileUtils.calculateProgress(90f, 20, 100), 0f);
    }

    @Test
    public void copyKnownSizeReportsFinalProgressForSmallStream() throws Exception {
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingProgressHandler progressHandler = new CapturingProgressHandler();

        long copied = FileUtils.copy(new ByteArrayInputStream(input), output, 6, progressHandler);

        assertEquals(3, copied);
        assertArrayEquals(input, output.toByteArray());
        assertEquals(100, progressHandler.max);
        assertEquals(50f, progressHandler.current, 0f);
        assertFalse(Float.isNaN(progressHandler.current));
        assertFalse(Float.isInfinite(progressHandler.current));
    }

    @Test
    public void copyZeroSizeReportsCompletion() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingProgressHandler progressHandler = new CapturingProgressHandler();

        long copied = FileUtils.copy(new ByteArrayInputStream(new byte[0]), output, 0, progressHandler);

        assertEquals(0, copied);
        assertEquals(100, progressHandler.max);
        assertEquals(100f, progressHandler.current, 0f);
    }

    @Test
    public void copyUnknownSizeUsesIndeterminateProgress() throws Exception {
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingProgressHandler progressHandler = new CapturingProgressHandler();

        long copied = FileUtils.copy(new ByteArrayInputStream(input), output, -1, progressHandler);

        assertEquals(3, copied);
        assertArrayEquals(input, output.toByteArray());
        assertEquals(-1, progressHandler.max);
        assertEquals(0f, progressHandler.current, 0f);
    }

    private static class CapturingProgressHandler extends ProgressHandler {
        int max;
        float current;
        @Nullable
        Object message;

        @Override
        public void onAttach(@Nullable Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
            onProgressUpdate(max, current, message);
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
            this.max = max;
            this.current = current;
            this.message = message;
        }

        @Override
        public void onResult(@Nullable Object message) {
            this.message = message;
        }

        @Override
        public void onDetach(@Nullable Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return new CapturingProgressHandler();
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return message;
        }

        @Override
        public int getLastMax() {
            return max;
        }

        @Override
        public float getLastProgress() {
            return current;
        }
    }
}
