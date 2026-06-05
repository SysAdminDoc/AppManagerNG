// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class InstallerIconSanitizerTest {
    @Test
    public void smallBitmapIconIsPreserved() {
        Context context = RuntimeEnvironment.getApplication();
        BitmapDrawable icon = new BitmapDrawable(context.getResources(),
                Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888));

        Drawable sanitized = InstallerIconSanitizer.sanitizeForDialog(context, icon);

        assertSame(icon, sanitized);
    }

    @Test
    public void oversizedBitmapIconIsScaledDown() {
        Context context = RuntimeEnvironment.getApplication();
        BitmapDrawable icon = new BitmapDrawable(context.getResources(),
                Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888));

        Drawable sanitized = InstallerIconSanitizer.sanitizeForDialog(context, icon);

        assertNotSame(icon, sanitized);
        Bitmap scaled = ((BitmapDrawable) sanitized).getBitmap();
        assertEquals(96, scaled.getWidth());
        assertEquals(48, scaled.getHeight());
    }

    @Test
    public void oversizedNonBitmapIconIsRenderedToBoundedBitmap() {
        Context context = RuntimeEnvironment.getApplication();
        Drawable icon = new LargeDrawable(480, 240);

        Drawable sanitized = InstallerIconSanitizer.sanitizeForDialog(context, icon);

        assertTrue(sanitized instanceof BitmapDrawable);
        Bitmap scaled = ((BitmapDrawable) sanitized).getBitmap();
        assertEquals(96, scaled.getWidth());
        assertEquals(48, scaled.getHeight());
    }

    @Test
    public void safeNonBitmapIconIsPreserved() {
        Context context = RuntimeEnvironment.getApplication();
        Drawable icon = new ColorDrawable(Color.RED);
        icon.setBounds(0, 0, 48, 48);

        Drawable sanitized = InstallerIconSanitizer.sanitizeForDialog(context, icon);

        assertSame(icon, sanitized);
    }

    @Test
    public void failingIconFallsBackToDefaultActivityIcon() {
        Context context = RuntimeEnvironment.getApplication();
        FailingDrawable icon = new FailingDrawable();

        Drawable sanitized = InstallerIconSanitizer.sanitizeForDialog(context, icon);

        assertNotNull(sanitized);
        assertNotSame(icon, sanitized);
    }

    private static class LargeDrawable extends Drawable {
        private final int mWidth;
        private final int mHeight;

        LargeDrawable(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(Color.BLUE);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return mWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mHeight;
        }
    }

    private static class FailingDrawable extends LargeDrawable {
        FailingDrawable() {
            super(480, 480);
        }

        @Override
        public void draw(Canvas canvas) {
            throw new RuntimeException("boom");
        }
    }
}
