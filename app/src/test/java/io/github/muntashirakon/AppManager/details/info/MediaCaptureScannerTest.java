// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.dex.DexUtils;

/**
 * Issue 41: App Info's Media capture section reads what the APK's code does about screen and audio capture and DRM.
 * The screen findings come from the dex alone, so they read "Likely".
 */
@RunWith(RobolectricTestRunner.class)
public class MediaCaptureScannerTest {
    private static final String SECURE_ACTIVITY = ".class public Lcom/example/SecureActivity;\n"
            + ".super Landroid/app/Activity;\n"
            + ".method protected onCreate(Landroid/os/Bundle;)V\n"
            + "    .registers 4\n"
            + "    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n"
            + "    invoke-virtual {p0}, Lcom/example/SecureActivity;->getWindow()Landroid/view/Window;\n"
            + "    move-result-object v0\n"
            + "    const/16 v1, 0x2000\n"
            + "    invoke-virtual {v0, v1, v1}, Landroid/view/Window;->setFlags(II)V\n"
            + "    return-void\n"
            + ".end method\n";
    private static final String SECURE_PARAMS = ".class public Lcom/example/Dialogs;\n"
            + ".super Ljava/lang/Object;\n"
            + ".method public static secure(Landroid/view/WindowManager$LayoutParams;)V\n"
            + "    .registers 2\n"
            + "    iget v0, p0, Landroid/view/WindowManager$LayoutParams;->flags:I\n"
            + "    or-int/lit16 v0, v0, 0x2000\n"
            + "    iput v0, p0, Landroid/view/WindowManager$LayoutParams;->flags:I\n"
            + "    return-void\n"
            + ".end method\n";
    // Uses the same number and calls Window.addFlags, but in different methods
    private static final String UNRELATED_FLAGS = ".class public Lcom/example/Plain;\n"
            + ".super Ljava/lang/Object;\n"
            + ".method public static keepOn(Landroid/view/Window;)V\n"
            + "    .registers 2\n"
            + "    const/16 v0, 0x80\n"
            + "    invoke-virtual {p0, v0}, Landroid/view/Window;->addFlags(I)V\n"
            + "    return-void\n"
            + ".end method\n"
            + ".method public static size()I\n"
            + "    .registers 1\n"
            + "    const/16 v0, 0x2000\n"
            + "    return v0\n"
            + ".end method\n";
    private static final String SECURE_SURFACE = ".class public Lcom/example/Player;\n"
            + ".super Ljava/lang/Object;\n"
            + ".method public static hide(Landroid/view/SurfaceView;)V\n"
            + "    .registers 2\n"
            + "    const/4 v0, 0x1\n"
            + "    invoke-virtual {p0, v0}, Landroid/view/SurfaceView;->setSecure(Z)V\n"
            + "    return-void\n"
            + ".end method\n";
    private static final String COOKIE = ".class public Lcom/example/Cookies;\n"
            + ".super Ljava/lang/Object;\n"
            + ".method public static secure(Ljava/net/HttpCookie;)V\n"
            + "    .registers 2\n"
            + "    const/4 v0, 0x1\n"
            + "    invoke-virtual {p0, v0}, Ljava/net/HttpCookie;->setSecure(Z)V\n"
            + "    return-void\n"
            + ".end method\n";
    private static final String RECENTS = ".class public Lcom/example/Bank;\n"
            + ".super Landroid/app/Activity;\n"
            + ".method public hide()V\n"
            + "    .registers 2\n"
            + "    const/4 v0, 0x0\n"
            + "    invoke-virtual {p0, v0}, Lcom/example/Bank;->setRecentsScreenshotEnabled(Z)V\n"
            + "    return-void\n"
            + ".end method\n";
    private static final String MEDIA_DRM = ".class public Lcom/example/Drm;\n"
            + ".super Ljava/lang/Object;\n"
            + ".method public static open()Landroid/media/MediaDrm;\n"
            + "    .registers 1\n"
            + "    const/4 v0, 0x0\n"
            + "    return-object v0\n"
            + ".end method\n";
    private static final String EXO_DRM = ".class public Lcom/example/Exo;\n"
            + ".super Ljava/lang/Object;\n"
            + ".field public manager:Lcom/google/android/exoplayer2/drm/DefaultDrmSessionManager;\n";
    private static final String AUDIO_POLICY = ".class public Lcom/example/Audio;\n"
            + ".super Ljava/lang/Object;\n"
            + ".method public static policy(Landroid/media/AudioAttributes$Builder;)V\n"
            + "    .registers 2\n"
            + "    const/4 v0, 0x3\n"
            + "    invoke-virtual {p0, v0}, Landroid/media/AudioAttributes$Builder;->setAllowedCapturePolicy(I)"
            + "Landroid/media/AudioAttributes$Builder;\n"
            + "    return-void\n"
            + ".end method\n";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void flagSecureOnTheWindowIsFound() throws Exception {
        int flags = MediaCaptureScanner.scanDex(dex(SECURE_ACTIVITY));

        assertTrue((flags & MediaCaptureScanner.FLAG_SECURE_WINDOW) != 0);
        assertTrue(MediaCaptureScanner.blocksScreenCapture(flags));
    }

    @Test
    public void flagSecureSetOnLayoutParamsIsFound() throws Exception {
        assertTrue((MediaCaptureScanner.scanDex(dex(SECURE_PARAMS)) & MediaCaptureScanner.FLAG_SECURE_WINDOW) != 0);
    }

    @Test
    public void theNumberAndTheCallInDifferentPlacesAreNoEvidence() throws Exception {
        int flags = MediaCaptureScanner.scanDex(dex(UNRELATED_FLAGS));

        assertEquals(0, flags);
        assertFalse(MediaCaptureScanner.blocksScreenCapture(flags));
    }

    @Test
    public void aSecureSurfaceIsFoundButACookieIsNot() throws Exception {
        assertEquals(MediaCaptureScanner.FLAG_SECURE_SURFACE, MediaCaptureScanner.scanDex(dex(SECURE_SURFACE)));
        assertEquals(0, MediaCaptureScanner.scanDex(dex(COOKIE)));
    }

    @Test
    public void hidingFromRecentsIsFound() throws Exception {
        int flags = MediaCaptureScanner.scanDex(dex(RECENTS));

        assertEquals(MediaCaptureScanner.FLAG_HIDES_RECENTS, flags);
        assertFalse("recents alone doesn't stop a screenshot", MediaCaptureScanner.blocksScreenCapture(flags));
    }

    @Test
    public void drmIsFoundFromTheFrameworkOrExoPlayer() throws Exception {
        assertEquals(MediaCaptureScanner.FLAG_DRM, MediaCaptureScanner.scanDex(dex(MEDIA_DRM)));
        assertEquals(MediaCaptureScanner.FLAG_DRM, MediaCaptureScanner.scanDex(dex(EXO_DRM)));
    }

    @Test
    public void anAudioCapturePolicyIsFound() throws Exception {
        assertEquals(MediaCaptureScanner.FLAG_AUDIO_CAPTURE_POLICY, MediaCaptureScanner.scanDex(dex(AUDIO_POLICY)));
    }

    @Test
    public void everyDexInTheApkIsRead() throws Exception {
        File apk = mTemp.newFile("app.apk");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apk))) {
            put(zos, "classes.dex", dex(UNRELATED_FLAGS));
            put(zos, "classes2.dex", dex(SECURE_ACTIVITY));
            put(zos, "classes3.dex", dex(MEDIA_DRM));
            put(zos, "assets/classes9.dex", dex(RECENTS));
            put(zos, "classes4.dex", "not a dex".getBytes());
        }

        int flags = MediaCaptureScanner.scanApk(apk);

        assertEquals(MediaCaptureScanner.FLAG_SECURE_WINDOW | MediaCaptureScanner.FLAG_DRM, flags);
    }

    @Test
    public void audioCaptureFollowsTheManifestFlag() {
        int allowFlag = 1 << 27;
        assertTrue(MediaCaptureScanner.allowsAudioCapture(allowFlag, 29));
        assertFalse(MediaCaptureScanner.allowsAudioCapture(0, 29));
    }

    @Test
    public void aSecureWindowIsFoundInDumpsysForThatPackageOnly() {
        List<String> modern = Arrays.asList(
                "WINDOW MANAGER WINDOWS (dumpsys window windows)",
                "  Window #2 Window{a1b2c3 u0 com.example.bank/com.example.bank.Main}:",
                "    mOwnerUid=10123 showForAllUsers=false package=com.example.bank appop=NONE",
                "    mAttrs={(0,0)(fillxfill) ty=BASE_APPLICATION fmt=TRANSPARENT",
                "      fl=LAYOUT_IN_SCREEN LAYOUT_INSET_DECOR SECURE SPLIT_TOUCH HARDWARE_ACCELERATED",
                "  Window #3 Window{d4e5f6 u0 com.example.other/com.example.other.Main}:",
                "    mOwnerUid=10124 showForAllUsers=false package=com.example.other appop=NONE",
                "      fl=LAYOUT_IN_SCREEN SPLIT_TOUCH");
        assertTrue(MediaCaptureScanner.hasSecureWindow(modern, "com.example.bank"));
        assertFalse(MediaCaptureScanner.hasSecureWindow(modern, "com.example.other"));
        assertFalse(MediaCaptureScanner.hasSecureWindow(modern, "com.example"));

        List<String> hex = Arrays.asList(
                "  Window #5 Window{1234 u0 com.example.bank/com.example.bank.Main}:",
                "    mAttrs=WM.LayoutParams{(0,0)(fillxfill) sim=#120 ty=1 fl=#81812100 wanim=0x1030466}");
        assertTrue(MediaCaptureScanner.hasSecureWindow(hex, "com.example.bank"));
    }

    private static void put(@NonNull ZipOutputStream zos, @NonNull String name, @NonNull byte[] bytes)
            throws java.io.IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    @NonNull
    static byte[] dex(@NonNull String... smali) throws Exception {
        List<ClassDef> classes = new ArrayList<>();
        for (String s : smali) {
            classes.add(DexUtils.toClassDef(s, 26));
        }
        MemoryDataStore store = new MemoryDataStore();
        DexUtils.storeDex(classes, store, 26);
        return store.getData();
    }
}
