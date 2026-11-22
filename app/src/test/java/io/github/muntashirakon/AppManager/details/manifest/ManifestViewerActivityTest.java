// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.os.BundleCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.editor.CodeEditorFragment;
import io.github.muntashirakon.AppManager.editor.CodeEditorWidget;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.io.IoUtils;

/**
 * The manifest viewer read the APK again and swapped in a new viewer every time the screen was
 * made again, so a turn of the phone lost the place in the file and a Save As answer on its way.
 */
@RunWith(RobolectricTestRunner.class)
public class ManifestViewerActivityTest {
    private static final String APK_MIME = "application/vnd.android.package-archive";

    @Rule
    public final TemporaryFolder mTemp = new TemporaryFolder();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ActivityController<ManifestViewerActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(mContext, true);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        Ops.setAuthenticated(mContext, false);
    }

    @Test
    public void aSaveAsAnswerThatComesBackAfterTurningThePhoneSavesTheManifest() throws Exception {
        ManifestViewerActivity activity = open();
        String manifest = waitForManifest(activity);
        assertTrue(fragment(activity).onMenuItemSelected(new RoboMenuItem(R.id.action_save_as)));
        idle();
        ShadowActivity.IntentForResult picker = shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(picker);
        File copy = mTemp.newFile("AndroidManifest.xml");

        ManifestViewerActivity turned = mController.recreate().get();
        idle();
        turned.getActivityResultRegistry().dispatchResult(picker.requestCode, Activity.RESULT_OK,
                new Intent().setData(Uri.fromFile(copy)));

        waitForFile(copy, manifest);
        assertEquals(manifest, waitForManifest(turned));
    }

    @Test
    public void turningThePhoneAfterTheCacheWasClearedShowsTheManifestAgain() throws Exception {
        ManifestViewerActivity activity = open();
        String manifest = waitForManifest(activity);
        CodeEditorFragment.Options options = BundleCompat.getParcelable(fragment(activity).requireArguments(),
                CodeEditorFragment.ARG_OPTIONS, CodeEditorFragment.Options.class);
        assertNotNull(options);
        assertNotNull(options.uri);
        File cached = new File(options.uri.getPath());
        // Android, or the app's own cache sweep, can clear the cache while the screen is open
        assertTrue(cached.delete());

        ManifestViewerActivity turned = mController.recreate().get();

        assertEquals(manifest, waitForManifest(turned));
    }

    @NonNull
    private ManifestViewerActivity open() throws IOException {
        File apk = mTemp.newFile("base.apk");
        ClassLoader classLoader = ManifestViewerActivityTest.class.getClassLoader();
        assertNotNull(classLoader);
        try (ZipFile bundle = new ZipFile(new File(classLoader.getResource("AppManager_v2.5.22.apks").getFile()));
             InputStream in = bundle.getInputStream(bundle.getEntry("base.apk"));
             OutputStream os = new FileOutputStream(apk)) {
            IoUtils.copy(in, os);
        }
        Intent intent = new Intent(mContext, ManifestViewerActivity.class)
                .setDataAndType(Uri.fromFile(apk), APK_MIME);
        mController = Robolectric.buildActivity(ManifestViewerActivity.class, intent);
        return mController.setup().get();
    }

    @NonNull
    private static CodeEditorFragment fragment(@NonNull ManifestViewerActivity activity) {
        return (CodeEditorFragment) activity.getSupportFragmentManager().findFragmentById(R.id.container);
    }

    @NonNull
    private static String waitForManifest(@NonNull ManifestViewerActivity activity) throws InterruptedException {
        // The APK is read and the manifest decoded on background threads
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            idle();
            CodeEditorWidget editor = activity.findViewById(R.id.editor);
            String text = editor == null ? "" : editor.getText().toString();
            if (text.contains("<manifest")) {
                return text;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("no manifest shown, editor has: " + text);
            }
            Thread.sleep(20);
        }
    }

    private static void waitForFile(@NonNull File file, @NonNull String text) throws Exception {
        // Saves are written on a background thread
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            idle();
            String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (text.equals(saved)) {
                return;
            }
            if (System.nanoTime() > deadline) {
                assertEquals(text, saved);
            }
            Thread.sleep(20);
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
