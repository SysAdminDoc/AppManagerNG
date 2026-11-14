// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.imagecache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 36})
public class ImageLoaderTest {
    private static final ImageLoader.DefaultImage DEFAULT = () -> Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

    @Test
    public void fetchRunsOnTheExecutorTheFetcherAsksFor() {
        List<Runnable> fetches = new ArrayList<>();
        ImageLoader loader = new ImageLoader(Runnable::run);
        String tag = uniqueTag();
        ImageView view = view(tag);

        loader.displayImage(tag, view, fetcher(fetches::add, () -> {
            throw new AssertionError("Only the fetcher's own executor runs this");
        }));

        assertEquals(1, fetches.size());
    }

    @Test
    public void aFetcherThatThrowsCostsOnlyItsImage() {
        ImageLoader loader = new ImageLoader(Runnable::run);
        String tag = uniqueTag();
        ImageView view = view(tag);

        // An executor's own thread would take the app down with this
        loader.displayImage(tag, view, fetcher(Runnable::run, () -> {
            throw new IllegalArgumentException("width and height must be > 0");
        }));

        shadowOf(Looper.getMainLooper()).idle();
        assertNull(view.getDrawable());
    }

    @Test
    public void aFetcherThatRunsOutOfStackCostsOnlyItsImage() {
        ImageLoader loader = new ImageLoader(Runnable::run);
        String tag = uniqueTag();
        ImageView view = view(tag);

        // A drawable nested deep enough, from an APK's icon, say
        loader.displayImage(tag, view, fetcher(Runnable::run, () -> {
            throw new StackOverflowError();
        }));

        shadowOf(Looper.getMainLooper()).idle();
        assertNull(view.getDrawable());
    }

    @Test
    public void aViewShowingSomethingElseIsNotFetchedFor() {
        List<Runnable> cacheChecks = new ArrayList<>();
        List<Runnable> fetches = new ArrayList<>();
        List<String> fetched = new ArrayList<>();
        ImageLoader loader = new ImageLoader(cacheChecks::add);
        String first = uniqueTag();
        String second = uniqueTag();
        String third = uniqueTag();
        ImageView scrolledPast = view(first);
        ImageView rebound = view(first);
        ImageLoader.ImageFetcherInterface fetcher = new ImageLoader.ImageFetcherInterface() {
            @NonNull
            @Override
            public Executor getExecutor() {
                return fetches::add;
            }

            @NonNull
            @Override
            public ImageLoader.ImageFetcherResult fetchImage(@NonNull String tag) {
                fetched.add(tag);
                return new ImageLoader.ImageFetcherResult(tag, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                        false, false, DEFAULT);
            }
        };

        loader.displayImage(first, scrolledPast, fetcher);
        loader.displayImage(first, rebound, fetcher);
        // Recycled for another row before the disk cache was checked
        scrolledPast.setTag(second);
        loader.displayImage(second, scrolledPast, fetcher);
        runAll(cacheChecks);
        assertEquals("the scrolled-past request stops before the fetcher", 2, fetches.size());

        // Recycled while it waited for the fetcher
        rebound.setTag(third);
        loader.displayImage(third, rebound, fetcher);
        runAll(fetches);
        assertEquals("only what the views show now is fetched", Collections.singletonList(second), fetched);
    }

    @Test
    // The PNG round trip through the disk cache needs real graphics. The cache has no SDK branch,
    // and native graphics on 23 would be a Robolectric sandbox of its own for the whole run.
    @Config(sdk = 36)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void aCachedImageNeverWaitsForTheFetcher() throws Exception {
        List<Runnable> fetches = new ArrayList<>();
        ImageLoader loader = new ImageLoader(Runnable::run);
        String tag = uniqueTag();
        Bitmap cached = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        cached.eraseColor(Color.RED);
        new ImageFileCache().putImage(tag, cached);
        ImageView view = view(tag);

        loader.displayImage(tag, view, fetcher(fetches::add, () -> {
            throw new AssertionError("The cached image is used");
        }));
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue("never queued behind slow fetches", fetches.isEmpty());
        assertNotNull(view.getDrawable());
        Bitmap shown = ((BitmapDrawable) view.getDrawable()).getBitmap();
        assertEquals(3, shown.getWidth());
        assertEquals(Color.RED, shown.getPixel(0, 0));
    }

    // Helpers

    private interface Fetch {
        Bitmap fetch();
    }

    @NonNull
    private static ImageLoader.ImageFetcherInterface fetcher(@NonNull Executor executor, @NonNull Fetch fetch) {
        return new ImageLoader.ImageFetcherInterface() {
            @NonNull
            @Override
            public Executor getExecutor() {
                return executor;
            }

            @NonNull
            @Override
            public ImageLoader.ImageFetcherResult fetchImage(@NonNull String tag) {
                return new ImageLoader.ImageFetcherResult(tag, fetch.fetch(), false, false, DEFAULT);
            }
        };
    }

    @NonNull
    private static ImageView view(@NonNull String tag) {
        ImageView view = new ImageView(RuntimeEnvironment.getApplication());
        view.setTag(tag);
        return view;
    }

    private static void runAll(@NonNull List<Runnable> queue) {
        List<Runnable> copy = new ArrayList<>(queue);
        queue.clear();
        for (Runnable runnable : copy) {
            runnable.run();
        }
    }

    @NonNull
    private static String uniqueTag() {
        return "ImageLoaderTest-" + System.nanoTime();
    }
}
