// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethodImplementation;
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedFieldReference;
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What an APK's code does about screen capture, audio capture and DRM, read from its dex files without running it.
 * Code can set these on paths that never run, so a finding means "likely", never "certain".
 */
public final class MediaCaptureScanner {
    /** A window flag write that includes FLAG_SECURE, which blocks screenshots and screen recording. */
    public static final int FLAG_SECURE_WINDOW = 1;
    /** SurfaceView.setSecure, which keeps a video surface out of screenshots. */
    public static final int FLAG_SECURE_SURFACE = 1 << 1;
    /** Activity.setRecentsScreenshotEnabled, which hides the app's preview in Recents. */
    public static final int FLAG_HIDES_RECENTS = 1 << 2;
    /** MediaDrm, MediaCrypto or ExoPlayer's DRM, or a Widevine reference. */
    public static final int FLAG_DRM = 1 << 3;
    /** setAllowedCapturePolicy, which limits audio capture at run time. */
    public static final int FLAG_AUDIO_CAPTURE_POLICY = 1 << 4;
    /** The manifest doesn't let other apps capture the app's audio. Set from the app's flags, not the dex. */
    public static final int FLAG_AUDIO_CAPTURE_BLOCKED = 1 << 5;

    /** A dex bigger than this is skipped, so one hostile file can't use up the memory. */
    public static final int MAX_DEX_BYTES = 48 << 20;
    /** No more than this much dex is read from one APK. */
    public static final long MAX_TOTAL_DEX_BYTES = 384L << 20;
    public static final int MAX_DEX_FILES = 64;

    /** ApplicationInfo.PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE */
    private static final int PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE = 1 << 27;
    /** WindowManager.LayoutParams.FLAG_SECURE */
    private static final int WINDOW_FLAG_SECURE = 0x2000;

    private static final String WINDOW = "Landroid/view/Window;";
    private static final String LAYOUT_PARAMS = "Landroid/view/WindowManager$LayoutParams;";
    private static final Pattern DEX_NAME = Pattern.compile("classes\\d*\\.dex");

    private MediaCaptureScanner() {
    }

    /**
     * Scans one dex. A file that isn't a readable dex finds nothing.
     */
    @WorkerThread
    public static int scanDex(@NonNull byte[] dex) {
        DexBackedDexFile dexFile;
        try {
            dexFile = new DexBackedDexFile(Opcodes.getDefault(), dex);
        } catch (RuntimeException e) {
            return 0;
        }
        try {
            return scan(dexFile);
        } catch (RuntimeException e) {
            // A malformed section ends the scan with what it found so far
            return 0;
        }
    }

    /**
     * Scans every classes*.dex at the root of the APK, within {@link #MAX_DEX_FILES}, {@link #MAX_DEX_BYTES} each
     * and {@link #MAX_TOTAL_DEX_BYTES} in all, whatever sizes the zip claims.
     */
    @WorkerThread
    public static int scanApk(@NonNull File apk) throws IOException {
        int flags = 0;
        long total = 0;
        int files = 0;
        try (ZipFile zipFile = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements() && files < MAX_DEX_FILES && total < MAX_TOTAL_DEX_BYTES) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Interrupted");
                }
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !DEX_NAME.matcher(entry.getName()).matches()) {
                    continue;
                }
                ++files;
                byte[] dex = readBounded(zipFile, entry, (int) Math.min(MAX_DEX_BYTES, MAX_TOTAL_DEX_BYTES - total));
                if (dex == null) {
                    continue;
                }
                total += dex.length;
                flags |= scanDex(dex);
            }
        }
        return flags;
    }

    public static boolean blocksScreenCapture(int flags) {
        return (flags & (FLAG_SECURE_WINDOW | FLAG_SECURE_SURFACE)) != 0;
    }

    /**
     * Whether other apps may capture the app's audio playback, from android:allowAudioPlaybackCapture (true by default
     * for apps targeting Android 10 or later). Android has no audio capture before 10.
     */
    public static boolean allowsAudioCapture(int privateFlags, int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.Q && (privateFlags & PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE) != 0;
    }

    /**
     * Whether "dumpsys window windows" lists a window of this package with FLAG_SECURE, written by name ("SECURE")
     * or, on older versions, inside the hex flags ("fl=#2000").
     */
    public static boolean hasSecureWindow(@NonNull List<String> dumpsysLines, @NonNull String packageName) {
        String current = null;
        for (String raw : dumpsysLines) {
            String line = raw.trim();
            if (line.startsWith("Window #")) {
                current = packageFromTitle(line);
                continue;
            }
            String owner = valueAfter(line, "package=");
            if (owner != null) {
                current = owner;
            }
            if (!packageName.equals(current)) {
                continue;
            }
            String flags = line.startsWith("fl=") ? line.substring(3) : null;
            if (flags == null) {
                int at = line.indexOf(" fl=");
                if (at >= 0) flags = line.substring(at + 4);
            }
            if (flags != null && hasSecureFlag(flags)) {
                return true;
            }
        }
        return false;
    }

    private static int scan(@NonNull DexBackedDexFile dexFile) {
        int flags = 0;
        boolean writesWindowFlags = false;
        for (DexBackedMethodReference method : dexFile.getMethodSection()) {
            String owner = method.getDefiningClass();
            String name = method.getName();
            if (WINDOW.equals(owner) && (name.equals("addFlags") || name.equals("setFlags"))) {
                writesWindowFlags = true;
            } else if (name.equals("setSecure") && owner.endsWith("SurfaceView;")) {
                flags |= FLAG_SECURE_SURFACE;
            } else if (name.equals("setRecentsScreenshotEnabled")) {
                flags |= FLAG_HIDES_RECENTS;
            } else if (name.equals("setAllowedCapturePolicy")) {
                flags |= FLAG_AUDIO_CAPTURE_POLICY;
            }
        }
        for (DexBackedFieldReference field : dexFile.getFieldSection()) {
            if (isWindowFlagsField(field)) {
                writesWindowFlags = true;
                break;
            }
        }
        for (String type : dexFile.getTypeSection()) {
            if (isDrmType(type)) {
                flags |= FLAG_DRM;
                break;
            }
        }
        if ((flags & FLAG_DRM) == 0) {
            for (String string : dexFile.getStringSection()) {
                if (string.length() < 64 && string.toLowerCase(Locale.ROOT).contains("widevine")) {
                    flags |= FLAG_DRM;
                    break;
                }
            }
        }
        if (writesWindowFlags && setsSecureFlagSomewhere(dexFile)) {
            flags |= FLAG_SECURE_WINDOW;
        }
        return flags;
    }

    /**
     * A method that both writes window flags and holds a number with FLAG_SECURE in it. Checking only within one
     * method keeps a flag write in one place and an unrelated 0x2000 in another from adding up.
     */
    private static boolean setsSecureFlagSomewhere(@NonNull DexBackedDexFile dexFile) {
        for (DexBackedClassDef classDef : dexFile.getClasses()) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            for (DexBackedMethod method : classDef.getMethods()) {
                DexBackedMethodImplementation impl = method.getImplementation();
                if (impl == null) continue;
                boolean writesFlags = false;
                boolean hasSecureLiteral = false;
                for (Instruction instruction : impl.getInstructions()) {
                    if (instruction instanceof ReferenceInstruction) {
                        Reference reference = ((ReferenceInstruction) instruction).getReference();
                        if (reference instanceof MethodReference) {
                            MethodReference mr = (MethodReference) reference;
                            if (WINDOW.equals(mr.getDefiningClass()) && (mr.getName().equals("addFlags")
                                    || mr.getName().equals("setFlags"))) {
                                writesFlags = true;
                            }
                        } else if (reference instanceof FieldReference && isWindowFlagsField((FieldReference) reference)) {
                            writesFlags = true;
                        }
                    } else if (instruction instanceof NarrowLiteralInstruction
                            && isSecureLiteral(((NarrowLiteralInstruction) instruction).getNarrowLiteral())) {
                        hasSecureLiteral = true;
                    }
                    if (writesFlags && hasSecureLiteral) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isWindowFlagsField(@NonNull FieldReference field) {
        return LAYOUT_PARAMS.equals(field.getDefiningClass()) && "flags".equals(field.getName());
    }

    /**
     * FLAG_SECURE alone or among a few other window flags. A value with most bits set, like -1, is a mask, not a
     * flag set.
     */
    private static boolean isSecureLiteral(int value) {
        return (value & WINDOW_FLAG_SECURE) != 0 && Integer.bitCount(value) <= 8;
    }

    private static boolean isDrmType(@NonNull String type) {
        return type.startsWith("Landroid/media/MediaDrm") || type.equals("Landroid/media/MediaCrypto;")
                || type.startsWith("Lcom/google/android/exoplayer2/drm/")
                || type.startsWith("Lcom/google/android/exoplayer/drm/")
                || type.startsWith("Landroidx/media3/exoplayer/drm/");
    }

    private static boolean hasSecureFlag(@NonNull String flags) {
        String[] tokens = flags.trim().split(" ");
        if (tokens.length > 0 && tokens[0].startsWith("#")) {
            String hex = tokens[0].substring(1);
            int end = 0;
            while (end < hex.length() && Character.digit(hex.charAt(end), 16) >= 0) ++end;
            try {
                return end > 0 && (Long.parseLong(hex.substring(0, end), 16) & WINDOW_FLAG_SECURE) != 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        for (String token : tokens) {
            if (token.contains("=")) {
                // The next attribute starts here
                break;
            }
            if (token.equals("SECURE")) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String packageFromTitle(@NonNull String line) {
        int start = line.indexOf("Window{");
        if (start < 0) return null;
        int end = line.indexOf('}', start);
        String[] parts = line.substring(start + "Window{".length(), end < 0 ? line.length() : end).split(" ");
        if (parts.length < 3) return null;
        String title = parts[2];
        int slash = title.indexOf('/');
        return slash >= 0 ? title.substring(0, slash) : title;
    }

    @Nullable
    private static String valueAfter(@NonNull String line, @NonNull String key) {
        int at = line.startsWith(key) ? 0 : line.indexOf(" " + key);
        if (at < 0) return null;
        int start = line.indexOf(key, at) + key.length();
        int end = line.indexOf(' ', start);
        return line.substring(start, end < 0 ? line.length() : end);
    }

    @Nullable
    private static byte[] readBounded(@NonNull ZipFile zipFile, @NonNull ZipEntry entry, int limit)
            throws IOException {
        try (InputStream is = zipFile.getInputStream(entry)) {
            ByteArrayOutputStream os = new ByteArrayOutputStream(Math.min(limit, 1 << 20));
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = is.read(buffer)) > 0) {
                if (os.size() + read > limit) {
                    return null;
                }
                os.write(buffer, 0, read);
            }
            return os.toByteArray();
        }
    }
}
