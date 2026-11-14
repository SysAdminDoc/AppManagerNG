// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.util.Size;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.simplemagic.ContentType;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ThumbnailUtilsCompat;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.fm.FmItem;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.PathContentInfo;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.AppManager.logs.Log;

public class FmIconFetcher implements ImageLoader.ImageFetcherInterface {
    private static final String TAG = FmIconFetcher.class.getSimpleName();

    private static final Set<String> OPEN_DOCUMENT_FORMAT_MIME_TYPES = new HashSet<String>() {{
        add("application/vnd.oasis.opendocument.text");
        add("application/vnd.oasis.opendocument.spreadsheet");
        add("application/vnd.oasis.opendocument.presentation");
        add("application/vnd.oasis.opendocument.graphics");
        add("application/vnd.oasis.opendocument.chart");
        add("application/vnd.oasis.opendocument.formula");
        add("application/vnd.oasis.opendocument.image");
        add("application/vnd.oasis.opendocument.text-master");
        add("application/vnd.sun.xml.base");
        add("application/vnd.oasis.opendocument.base");
        add("application/vnd.oasis.opendocument.database");
        // Templates
        add("application/vnd.oasis.opendocument.text-template");
        add("application/vnd.oasis.opendocument.spreadsheet-template");
        add("application/vnd.oasis.opendocument.presentation-template");
        add("application/vnd.oasis.opendocument.graphics-template");
        add("application/vnd.oasis.opendocument.chart-template");
        add("application/vnd.oasis.opendocument.formula-template");
        add("application/vnd.oasis.opendocument.text-web");
    }};

    /**
     * Thumbnails can take seconds each, so they get their own threads instead of the shared pool
     * the file list loads on. Two at a time also matches how many the isolated decoder takes at once.
     */
    private static final class ThumbnailThreads {
        static final ThreadPoolExecutor EXECUTOR;

        static {
            AtomicInteger count = new AtomicInteger();
            EXECUTOR = new ThreadPoolExecutor(IsolatedThumbnails.MAX_CONCURRENT_CALLS,
                    IsolatedThumbnails.MAX_CONCURRENT_CALLS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                    runnable -> {
                        Thread thread = new Thread(runnable, "FmThumbnail-" + count.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });
            EXECUTOR.allowCoreThreadTimeOut(true);
        }
    }

    @NonNull
    private final FmItem mFmItem;

    public FmIconFetcher(@NonNull FmItem fmItem) {
        mFmItem = fmItem;
    }

    @NonNull
    @Override
    public Executor getExecutor() {
        return ThumbnailThreads.EXECUTOR;
    }

    @NonNull
    @Override
    public ImageLoader.ImageFetcherResult fetchImage(@NonNull String tag) {
        PathContentInfo contentInfo = mFmItem.getContentInfo();
        if (contentInfo == null) {
            contentInfo = mFmItem.path.getPathContentInfo();
            mFmItem.setContentInfo(contentInfo);
        }
        String mimeType = contentInfo.getMimeType();
        int drawableRes = FmIcons.getDrawableFromType(mimeType);
        int padding = UiUtils.dpToPx(ContextUtils.getContext(), 4);
        int length = UiUtils.dpToPx(ContextUtils.getContext(), 40);
        ImageLoader.DefaultImage defaultImage = new ImageLoader.DefaultImageDrawableRes("drawable_" + drawableRes, drawableRes, padding);
        Size size = new Size(length, length);
        try {
            return fetchImage(tag, mimeType, drawableRes, padding, size, defaultImage);
        } catch (Exception | StackOverflowError | OutOfMemoryError e) {
            // A file that breaks its preview (an APK icon nested deep enough to use up the stack,
            // say) still gets its type's icon
            Log.w(TAG, "Could not make a thumbnail for %s", e, mFmItem.path);
            return new ImageLoader.ImageFetcherResult(tag, null, defaultImage);
        }
    }

    @NonNull
    private ImageLoader.ImageFetcherResult fetchImage(@NonNull String tag, @Nullable String mimeType,
                                                      @DrawableRes int drawableRes, int padding,
                                                      @NonNull Size size,
                                                      @NonNull ImageLoader.DefaultImage defaultImage) {
        if (OPEN_DOCUMENT_FORMAT_MIME_TYPES.contains(mimeType)) {
            // Open document format
            Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_OPEN_DOCUMENT, null);
            if (bitmap != null) {
                return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                        false, true, defaultImage);
            }
        }
        // Others
        if (FmIcons.isApk(drawableRes)) {
            if (ContentType.APK.getMimeType().equals(mimeType)) {
                Bitmap bitmap = FmIcons.generateApkIcon(mFmItem.path);
                if (bitmap != null) {
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                            false, true, defaultImage);
                }
            } else if (ContentType2.APKM.getMimeType().equals(mimeType)) {
                Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_APK_BUNDLE, null);
                if (bitmap != null) {
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                            false, true, defaultImage);
                }
            } else {
                Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_APK_BUNDLE, null);
                if (bitmap == null) {
                    // No icon.png: the base APK's icon, read like a plain APK's
                    bitmap = FmIcons.getApksBaseIcon(mFmItem.path);
                }
                if (bitmap != null) {
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                            false, true, defaultImage);
                }
            }
        } else if (FmIcons.isArchive(drawableRes)) {
            if (ContentType.JAVA_ARCHIVE.getMimeType().equals(mimeType)) {
                Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_J2ME, null);
                if (bitmap != null) {
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                            false, true, defaultImage);
                }
            }
        } else if (FmIcons.isAudio(drawableRes)) {
            try {
                Bitmap bitmap = ThumbnailUtilsCompat.createAudioThumbnail(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path), size, null);
                return new ImageLoader.ImageFetcherResult(tag, bitmap, false, true, defaultImage);
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        } else if (FmIcons.isVideo(drawableRes)) {
            try {
                Bitmap bitmap = ThumbnailUtilsCompat.createVideoThumbnail(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path), size, null);
                return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage);
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        } else if (FmIcons.isImage(drawableRes)) {
            boolean svg = ContentType.SVG.getMimeType().equals(mimeType);
            Bitmap bitmap = decodeIsolated(svg ? ThumbnailDecoder.KIND_SVG : ThumbnailDecoder.KIND_IMAGE, null);
            if (bitmap != null) {
                return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                        false, true, defaultImage);
            }
        } else if (FmIcons.isEbook(drawableRes)) {
            if (ContentType.EPUB.getMimeType().equals(mimeType)) {
                Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_EPUB, null);
                if (bitmap != null) {
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                            false, true, defaultImage);
                }
            }
        } else if (FmIcons.isFont(drawableRes)) {
            String extension = mFmItem.path.getExtension();
            String text = extension != null ? extension.substring(0, Math.min(extension.length(), 4))
                    .toUpperCase(Locale.ROOT) : "FONT";
            Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_FONT, text);
            if (bitmap != null) {
                return new ImageLoader.ImageFetcherResult(tag, bitmap,
                        false, true, defaultImage);
            }
        } else if (FmIcons.isPdf(drawableRes)) {
            Bitmap bitmap = decodeIsolated(ThumbnailDecoder.KIND_PDF, null);
            if (bitmap != null) {
                return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                        false, true, defaultImage);
            }
        } else if (FmIcons.isGeneric(drawableRes)) {
            String extension = mFmItem.path.getExtension();
            if (extension != null) {
                // Generate icon from extension (at most 4 characters)
                int len = Math.min(extension.length(), 4);
                String shortExt = extension.substring(0, len).toUpperCase(Locale.ROOT);
                String extTag = "fm_ext_" + shortExt;
                return new ImageLoader.ImageFetcherResult(tag, null,
                        new ImageLoader.DefaultImageString(extTag, shortExt));
            }
            if (mFmItem.path.canExecute()) {
                // Generate executable string
                drawableRes = R.drawable.ic_frost_termux;
                return new ImageLoader.ImageFetcherResult(tag, null,
                        new ImageLoader.DefaultImageDrawableRes("drawable_" + drawableRes, drawableRes, padding));
            }
        }
        return new ImageLoader.ImageFetcherResult(tag, null, defaultImage);
    }

    /**
     * Decodes the file in the isolated thumbnail process, never in this one: the file can come
     * from anywhere.
     */
    @Nullable
    private Bitmap decodeIsolated(int kind, @Nullable String label) {
        return IsolatedThumbnails.getInstance().decode(mFmItem.path, kind, label);
    }

    private Bitmap getThumbnail(@NonNull Bitmap bitmap, @NonNull Size size, boolean recycle) {
        return ThumbnailUtils.extractThumbnail(bitmap, size.getWidth(), size.getHeight(), recycle ? ThumbnailUtils.OPTIONS_RECYCLE_INPUT : 0);
    }
}