// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.IoUtils;

/**
 * Versioned on-disk storage for downloaded debloat definitions.
 *
 * <p>Every downloaded generation lands in its own directory and becomes visible only when a single
 * pointer file is atomically replaced, so readers can never observe a half-written generation or a
 * mixture of files from two of them. The previous generation is kept as the known-good fallback;
 * anything older is pruned.
 */
public class DebloatDefinitionStore {
    private static final String TAG = DebloatDefinitionStore.class.getSimpleName();

    @VisibleForTesting
    static final String POINTER_FILE = "current";
    private static final String GENERATION_PREFIX = "g";
    private static final int GENERATIONS_RETAINED = 2;

    @NonNull
    private final File mRoot;

    public DebloatDefinitionStore(@NonNull File root) {
        mRoot = root;
    }

    /**
     * @return the generation number that is currently published, or {@code 0} if none is.
     */
    public long getActiveGeneration() {
        File dir = getActiveDirectory();
        return dir != null ? parseGeneration(dir.getName()) : 0;
    }

    /**
     * @return the content of {@code fileName} in the published generation, or {@code null} when no
     * complete generation is published.
     */
    @Nullable
    @WorkerThread
    public String read(@NonNull String fileName) {
        File dir = getActiveDirectory();
        if (dir == null) {
            return null;
        }
        File file = new File(dir, fileName);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return IoUtils.getInputStreamContent(inputStream);
        } catch (IOException e) {
            Log.w(TAG, "Could not read the published debloat definition file: %s", e, file);
            return null;
        }
    }

    /**
     * Writes a complete generation and publishes it. Nothing is visible to readers until every file
     * has been written and synced, so an interruption at any point leaves the previously published
     * generation active.
     *
     * @throws IOException if the generation could not be written, synced, or published. The
     *                     previously published generation stays active in that case.
     */
    @WorkerThread
    public void publish(long generation, @NonNull List<GenerationFile> files) throws IOException {
        if (generation <= 0) {
            throw new IOException("Invalid debloat definition generation: " + generation);
        }
        if (files.isEmpty()) {
            throw new IOException("Refusing to publish an empty debloat definition generation.");
        }
        if (!mRoot.isDirectory() && !mRoot.mkdirs()) {
            throw new IOException("Could not create " + mRoot);
        }
        File target = new File(mRoot, GENERATION_PREFIX + generation);
        deleteRecursively(target);
        if (!target.mkdirs()) {
            throw new IOException("Could not create " + target);
        }
        try {
            for (GenerationFile file : files) {
                writeSynced(new File(target, file.name), file.bytes);
            }
            syncDirectory(target);
            writePointer(target.getName());
        } catch (IOException | RuntimeException e) {
            deleteRecursively(target);
            throw e;
        }
        prune(generation);
    }

    /**
     * Removes every stored generation and the pointer.
     */
    public void clear() {
        deleteRecursively(mRoot);
    }

    @Nullable
    private File getActiveDirectory() {
        File pointer = new File(mRoot, POINTER_FILE);
        if (!pointer.isFile()) {
            return null;
        }
        String name;
        try (InputStream inputStream = new FileInputStream(pointer)) {
            name = IoUtils.getInputStreamContent(inputStream).trim();
        } catch (IOException e) {
            Log.w(TAG, "Could not read the debloat definition pointer.", e);
            return null;
        }
        if (parseGeneration(name) <= 0) {
            return null;
        }
        File dir = new File(mRoot, name);
        return dir.isDirectory() ? dir : null;
    }

    private void writePointer(@NonNull String generationName) throws IOException {
        File pointer = new File(mRoot, POINTER_FILE);
        File temp = new File(mRoot, POINTER_FILE + ".new");
        writeSynced(temp, generationName.getBytes(StandardCharsets.UTF_8));
        if (!temp.renameTo(pointer)) {
            // A rename over an existing file is not atomic on every filesystem Android exposes.
            if (!pointer.delete() || !temp.renameTo(pointer)) {
                deleteRecursively(temp);
                throw new IOException("Could not publish the debloat definition generation.");
            }
        }
        syncDirectory(mRoot);
    }

    private void prune(long activeGeneration) {
        File[] children = mRoot.listFiles();
        if (children == null) {
            return;
        }
        List<Long> generations = new ArrayList<>();
        for (File child : children) {
            long generation = child.isDirectory() ? parseGeneration(child.getName()) : 0;
            if (generation > 0 && generation != activeGeneration) {
                generations.add(generation);
            }
        }
        Collections.sort(generations, Collections.reverseOrder());
        for (int i = GENERATIONS_RETAINED - 1; i < generations.size(); ++i) {
            deleteRecursively(new File(mRoot, GENERATION_PREFIX + generations.get(i)));
        }
    }

    private static void writeSynced(@NonNull File target, @NonNull byte[] bytes) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(target)) {
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.getFD().sync();
        }
    }

    private static void syncDirectory(@NonNull File dir) {
        // Directory fsync is best-effort: it is unsupported on some Android filesystems and the
        // pointer swap is still ordered correctly without it.
        try (FileInputStream stream = new FileInputStream(dir)) {
            stream.getFD().sync();
        } catch (IOException e) {
            Log.d(TAG, "Could not sync %s", dir);
        }
    }

    private static long parseGeneration(@NonNull String name) {
        if (!name.startsWith(GENERATION_PREFIX)) {
            return 0;
        }
        try {
            long generation = Long.parseLong(name.substring(GENERATION_PREFIX.length()));
            return generation > 0 ? generation : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void deleteRecursively(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static final class GenerationFile {
        @NonNull
        final String name;
        @NonNull
        final byte[] bytes;

        public GenerationFile(@NonNull String name, @NonNull byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    @NonNull
    public static List<GenerationFile> generationFiles(@NonNull GenerationFile... files) {
        return Collections.unmodifiableList(Arrays.asList(files));
    }
}
