// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.muntashirakon.svg.SVG;
import io.github.muntashirakon.svg.SVGParser;

/**
 * Turns a file into a small bitmap. This runs in the isolated thumbnail process only, since every
 * decoder here parses files anyone could have put on the device. Each one reads from the
 * descriptor it's handed and never allocates more than {@link #MAX_DECODE_PIXELS} pixels or
 * {@link #MAX_ENTRY_BYTES} bytes for one file.
 */
final class ThumbnailDecoder {
    static final int KIND_IMAGE = 1;
    static final int KIND_SVG = 2;
    static final int KIND_PDF = 3;
    static final int KIND_FONT = 4;
    static final int KIND_EPUB = 5;
    static final int KIND_J2ME = 6;
    static final int KIND_OPEN_DOCUMENT = 7;
    static final int KIND_APK_BUNDLE = 8;

    /**
     * Largest side of a thumbnail, in pixels. Keeps a thumbnail's pixels well under the binder's
     * transaction limit.
     */
    static final int MAX_SIDE = 256;
    /**
     * Largest file read out of an archive to show it: an eBook cover or an icon.
     */
    static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    /**
     * Largest XML or manifest file read out of an archive to find that file.
     */
    static final int MAX_METADATA_BYTES = 1024 * 1024;
    /**
     * Largest image decoded before it's scaled down, in pixels (16 MB at four bytes each).
     */
    static final long MAX_DECODE_PIXELS = 4L * 1024 * 1024;
    /**
     * Longest label a font thumbnail draws.
     */
    static final int MAX_LABEL_LENGTH = 8;

    private static final Object PDF_LOCK = new Object();

    private ThumbnailDecoder() {
    }

    /**
     * @return the thumbnail, which fits in {@code maxSide} by {@code maxSide}, or {@code null} if
     * the file has nothing to show
     */
    @Nullable
    static Bitmap decode(@NonNull ParcelFileDescriptor fd, int kind, @Nullable String label, int maxSide)
            throws IOException {
        switch (kind) {
            case KIND_IMAGE:
                return decodeImage(fd, maxSide);
            case KIND_SVG:
                return decodeSvg(fd, maxSide);
            case KIND_PDF:
                return decodePdf(fd, maxSide);
            case KIND_FONT:
                return decodeFont(fd, label, maxSide);
            case KIND_EPUB:
                try (ZipSource zip = new ZipSource(fd)) {
                    return decodeImage(EpubCoverGenerator.readCover(zip.reader), maxSide);
                }
            case KIND_J2ME:
                try (ZipSource zip = new ZipSource(fd)) {
                    return decodeImage(J2meIconExtractor.readIcon(zip.reader), maxSide);
                }
            case KIND_OPEN_DOCUMENT:
                try (ZipSource zip = new ZipSource(fd)) {
                    return decodeImage(zip.reader.read("Thumbnails/thumbnail.png", MAX_ENTRY_BYTES), maxSide);
                }
            case KIND_APK_BUNDLE:
                try (ZipSource zip = new ZipSource(fd)) {
                    return decodeImage(zip.reader.read("icon.png", MAX_ENTRY_BYTES), maxSide);
                }
            default:
                return null;
        }
    }

    /**
     * Width, height, then the pixels row by row, scaled to fit in {@code maxSide} if needed.
     */
    @NonNull
    static int[] toPixels(@NonNull Bitmap bitmap, int maxSide) {
        Bitmap fitted = scaleToFit(bitmap, maxSide);
        if (fitted.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = fitted.copy(Bitmap.Config.ARGB_8888, false);
            fitted.recycle();
            fitted = copy;
        }
        int width = fitted.getWidth();
        int height = fitted.getHeight();
        int[] pixels = new int[2 + width * height];
        pixels[0] = width;
        pixels[1] = height;
        fitted.getPixels(pixels, 2, width, 0, 0, width, height);
        fitted.recycle();
        return pixels;
    }

    /**
     * The size an image takes when scaled to fit in {@code maxSide} by {@code maxSide}, keeping
     * its shape. Never smaller than one pixel a side, and never scaled up.
     */
    @VisibleForTesting
    @NonNull
    static int[] fitWithin(int width, int height, int maxSide) {
        float scale = Math.min(1f, (float) maxSide / Math.max(width, height));
        return new int[]{
                Math.max(1, Math.min(maxSide, Math.round(width * scale))),
                Math.max(1, Math.min(maxSide, Math.round(height * scale)))
        };
    }

    /**
     * The power of two an image is subsampled by so that it's decoded no larger than needed and
     * never above {@link #MAX_DECODE_PIXELS}.
     */
    @VisibleForTesting
    static int sampleSizeFor(int width, int height, int maxSide) {
        int sampleSize = 1;
        while (sampleSize < (1 << 30) && Math.max(width, height) / (sampleSize * 2) >= maxSide) {
            sampleSize *= 2;
        }
        while (sampleSize < (1 << 30) && (long) (width / sampleSize) * (height / sampleSize) > MAX_DECODE_PIXELS) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    @Nullable
    private static Bitmap decodeImage(@NonNull ParcelFileDescriptor fd, int maxSide) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        // Decoding from a descriptor leaves its offset where it was, so the second pass starts over
        BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSizeFor(options.outWidth, options.outHeight, maxSide);
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
        return bitmap != null ? scaleToFit(bitmap, maxSide) : null;
    }

    @Nullable
    private static Bitmap decodeImage(@Nullable byte[] bytes, int maxSide) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSizeFor(options.outWidth, options.outHeight, maxSide);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return bitmap != null ? scaleToFit(bitmap, maxSide) : null;
    }

    @Nullable
    private static Bitmap decodeSvg(@NonNull ParcelFileDescriptor fd, int maxSide) throws IOException {
        Picture picture;
        try (ParcelFileDescriptor copy = fd.dup();
             InputStream is = new BufferedInputStream(new FileInputStream(copy.getFileDescriptor()))) {
            SVG svg = SVGParser.getSVGFromInputStream(is);
            picture = svg.getPicture();
        }
        int width = picture.getWidth();
        int height = picture.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        int[] size = fitWithin(width, height, maxSide);
        Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale((float) size[0] / width, (float) size[1] / height);
        canvas.drawPicture(picture);
        return bitmap;
    }

    @Nullable
    private static Bitmap decodePdf(@NonNull ParcelFileDescriptor fd, int maxSide) throws IOException {
        // Binder threads decode side by side, and pdfium isn't thread safe on every version
        synchronized (PDF_LOCK) {
            return decodePdfLocked(fd, maxSide);
        }
    }

    @Nullable
    private static Bitmap decodePdfLocked(@NonNull ParcelFileDescriptor fd, int maxSide) throws IOException {
        ParcelFileDescriptor copy = fd.dup();
        PdfRenderer pdfRenderer;
        try {
            // The renderer closes the descriptor it's given once it opens
            pdfRenderer = new PdfRenderer(copy);
        } catch (IOException | RuntimeException e) {
            copy.close();
            throw e;
        }
        try (PdfRenderer renderer = pdfRenderer) {
            if (renderer.getPageCount() < 1) {
                return null;
            }
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                int width = page.getWidth();
                int height = page.getHeight();
                if (width <= 0 || height <= 0) {
                    return null;
                }
                int[] size = fitWithin(width, height, maxSide);
                Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                Matrix matrix = new Matrix();
                matrix.postScale((float) size[0] / width, (float) size[1] / height);
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            }
        }
    }

    @Nullable
    private static Bitmap decodeFont(@NonNull ParcelFileDescriptor fd, @Nullable String label, int maxSide)
            throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Before Android 8, a font loads only from a path, and this process can't open paths
            return null;
        }
        Typeface typeface;
        try (ParcelFileDescriptor copy = fd.dup()) {
            // Falls back to the default typeface if the file isn't a font it can read
            typeface = new Typeface.Builder(copy.getFileDescriptor()).build();
        }
        String text = label == null || label.trim().isEmpty() ? "FONT" : label.trim();
        if (text.length() > MAX_LABEL_LENGTH) {
            text = text.substring(0, MAX_LABEL_LENGTH);
        }
        return drawText(text, typeface, maxSide);
    }

    @NonNull
    private static Bitmap drawText(@NonNull String text, @NonNull Typeface typeface, int maxSide) {
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(100);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(typeface);
        Rect textRect = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), textRect);
        // A font decides its own glyph bounds, so the drawing is scaled into the bitmap rather
        // than the bitmap sized to the drawing
        int length = (int) Math.max(1, Math.min(Integer.MAX_VALUE,
                Math.max((long) textRect.width(), textRect.height()) + 80L));
        int side = Math.min(maxSide, length);
        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float scale = (float) side / length;
        canvas.scale(scale, scale);
        float x = length / 2f - textRect.width() / 2f - textRect.left;
        float y = length / 2f + textRect.height() / 2f - textRect.bottom;
        canvas.drawText(text, x, y, textPaint);
        return bitmap;
    }

    @NonNull
    private static Bitmap scaleToFit(@NonNull Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxSide && height <= maxSide) {
            return bitmap;
        }
        int[] size = fitWithin(width, height, maxSide);
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size[0], size[1], true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static final class ZipSource implements AutoCloseable {
        @NonNull
        private final ParcelFileDescriptor mCopy;
        @NonNull
        private final FileInputStream mStream;
        @NonNull
        final BoundedZipReader reader;

        ZipSource(@NonNull ParcelFileDescriptor fd) throws IOException {
            mCopy = fd.dup();
            try {
                mStream = new FileInputStream(mCopy.getFileDescriptor());
                reader = new BoundedZipReader(mStream.getChannel());
            } catch (IOException | RuntimeException e) {
                mCopy.close();
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                mStream.close();
            } finally {
                mCopy.close();
            }
        }
    }
}
