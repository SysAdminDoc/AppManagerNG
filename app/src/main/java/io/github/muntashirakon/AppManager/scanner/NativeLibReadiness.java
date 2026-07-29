// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Whether an app's bundled native code will still work, and work well, on current Android.
 *
 * <p>Three properties matter and they fail in different ways:
 * <ul>
 *   <li><b>16 KB page alignment</b> — Android 15+ devices may use 16 KB memory pages. A shared
 *       library whose load segments are aligned to less than that cannot be mapped and the app
 *       will not run on such a device.</li>
 *   <li><b>32-bit only</b> — a package shipping only 32-bit libraries cannot run at all on a
 *       64-bit-only device, and those are now shipping.</li>
 *   <li><b>Compressed libraries</b> — libraries extracted at install time are stored twice, once
 *       in the APK and once unpacked, which is wasted space rather than a failure.</li>
 * </ul>
 *
 * <p>Every field distinguishes "not ready" from "not known". A package whose libraries could not
 * be parsed is never reported as ready, and never reported as broken either — a filter that
 * silently treated unreadable as passing would quietly under-report the problem it exists to find.
 */
public final class NativeLibReadiness {
    private static final String TAG = "NativeLibReadiness";

    /** The readiness of a package whose native libraries have not been examined. */
    public static final NativeLibReadiness UNKNOWN = new NativeLibReadiness(
            false, false, false, false, false, Collections.emptySet());

    /** {@code true} when the package ships at least one native library. */
    public final boolean hasNativeLibraries;
    /** {@code true} when every library with a readable alignment is 16 KB-aligned. */
    public final boolean sixteenKbReady;
    /** {@code true} when at least one library's alignment could not be determined. */
    public final boolean alignmentUnknown;
    /** {@code true} when the package ships 32-bit libraries and no 64-bit ones. */
    public final boolean thirtyTwoBitOnly;
    /** {@code true} when the libraries are stored compressed and unpacked at install time. */
    public final boolean compressed;
    /** ABI directory names the package ships libraries for. */
    @NonNull
    public final Set<String> abis;

    private NativeLibReadiness(boolean hasNativeLibraries, boolean sixteenKbReady, boolean alignmentUnknown,
                               boolean thirtyTwoBitOnly, boolean compressed, @NonNull Set<String> abis) {
        this.hasNativeLibraries = hasNativeLibraries;
        this.sixteenKbReady = sixteenKbReady;
        this.alignmentUnknown = alignmentUnknown;
        this.thirtyTwoBitOnly = thirtyTwoBitOnly;
        this.compressed = compressed;
        this.abis = Collections.unmodifiableSet(abis);
    }

    /**
     * @param libraries        Parsed native libraries; entries that are not ELF images are ignored,
     *                         since an unreadable file says nothing about alignment.
     * @param abiDirectoryNames The {@code lib/<abi>} directory names present in the package.
     * @param compressed       Whether the package asks the platform to extract its libraries.
     */
    @NonNull
    public static NativeLibReadiness from(@Nullable Collection<? extends NativeLibraries.NativeLib> libraries,
                                          @Nullable Collection<String> abiDirectoryNames,
                                          boolean compressed) {
        Set<String> abis = new LinkedHashSet<>();
        if (abiDirectoryNames != null) {
            for (String abi : abiDirectoryNames) {
                if (abi != null && !abi.trim().isEmpty()) {
                    abis.add(abi.trim());
                }
            }
        }
        if (libraries == null || libraries.isEmpty()) {
            // No native code is not a readiness problem; it is the absence of one.
            return new NativeLibReadiness(false, true, false, false, false, abis);
        }
        boolean sawElf = false;
        boolean sawUnalignedOrUnknown = false;
        boolean alignmentUnknown = false;
        boolean saw32Bit = false;
        boolean saw64Bit = false;
        for (NativeLibraries.NativeLib library : libraries) {
            if (!(library instanceof NativeLibraries.ElfLib)) {
                continue;
            }
            NativeLibraries.ElfLib elf = (NativeLibraries.ElfLib) library;
            sawElf = true;
            if (!elf.hasKnownLoadSegmentAlignment()) {
                alignmentUnknown = true;
                sawUnalignedOrUnknown = true;
            } else if (!elf.has16KbLoadSegmentAlignment()) {
                sawUnalignedOrUnknown = true;
            }
            if (elf.getArch() == NativeLibraries.ElfLib.ARCH_64BIT) {
                saw64Bit = true;
            } else if (elf.getArch() == NativeLibraries.ElfLib.ARCH_32BIT) {
                saw32Bit = true;
            }
        }
        if (!sawElf) {
            // Files were present but none could be read as ELF: nothing can be concluded.
            return new NativeLibReadiness(true, false, true, false, compressed, abis);
        }
        return new NativeLibReadiness(true, !sawUnalignedOrUnknown, alignmentUnknown,
                saw32Bit && !saw64Bit, compressed, abis);
    }

    /**
     * Reads the {@code lib/} entries of a packaged APK.
     *
     * <p>A package that cannot be opened stays {@link #UNKNOWN} rather than being reported as
     * having no native code — a sweep for broken libraries must not quietly clear apps it failed
     * to inspect.
     */
    @NonNull
    public static NativeLibReadiness fromApk(@Nullable String sourceDir) {
        if (sourceDir == null || sourceDir.isEmpty()) {
            return UNKNOWN;
        }
        List<NativeLibraries.NativeLib> libraries = new ArrayList<>();
        Set<String> abis = new LinkedHashSet<>();
        boolean compressed = false;
        try (ZipFile zipFile = new ZipFile(new File(sourceDir))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("lib/") || !name.endsWith(".so")) {
                    continue;
                }
                String[] parts = name.split("/");
                if (parts.length >= 3) {
                    abis.add(parts[1]);
                }
                // A stored entry can be mapped straight out of the APK; a deflated one has to be
                // extracted at install time and then occupies disk twice.
                if (entry.getMethod() != ZipEntry.STORED) {
                    compressed = true;
                }
                try (InputStream stream = zipFile.getInputStream(entry)) {
                    libraries.add(NativeLibraries.NativeLib.parse(name, entry.getSize(), stream));
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, "Could not parse native library %s", e, name);
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not read native libraries from %s", e, sourceDir);
            return UNKNOWN;
        }
        return from(libraries, abis, compressed);
    }

    /** {@code true} when at least one library is known not to be 16 KB-aligned or cannot be read. */
    public boolean needsSixteenKbAttention() {
        return hasNativeLibraries && !sixteenKbReady;
    }

    /** {@code true} when nothing about this package's native code has been established. */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * The chip labels this readiness warrants, most important first, or an empty list when there
     * is nothing worth saying. A package with no native code gets a single reassuring chip; a
     * package we could not inspect says so rather than staying silent.
     */
    @NonNull
    public List<Integer> getChipLabelResources() {
        List<Integer> chips = new ArrayList<>(3);
        if (isUnknown()) {
            chips.add(R.string.native_lib_readiness_unknown);
            return chips;
        }
        if (!hasNativeLibraries) {
            chips.add(R.string.native_lib_readiness_none);
            return chips;
        }
        chips.add(sixteenKbReady
                ? R.string.native_lib_readiness_ready
                : R.string.native_lib_readiness_not_ready);
        if (thirtyTwoBitOnly) {
            chips.add(R.string.native_lib_readiness_32bit_only);
        }
        if (compressed) {
            chips.add(R.string.native_lib_readiness_compressed);
        }
        return chips;
    }
}
