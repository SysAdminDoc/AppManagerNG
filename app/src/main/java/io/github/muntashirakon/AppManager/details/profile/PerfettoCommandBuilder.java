// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;

/**
 * Builds the {@code perfetto -c <config> --txt -o <output>} argv used by the
 * T20-A "Export Perfetto trace" action.
 *
 * <p>The privileged shell consumes both paths as literal arguments. We still
 * apply the same shell-metacharacter guard that {@link CpuProfileCommandBuilder}
 * uses so a runner that routes through {@code sh -c} cannot be coerced into
 * argument injection.
 *
 * <p>{@link #perfettoUiUrl()} returns the deep link used by the post-capture
 * "Open in Perfetto UI" affordance. The Perfetto UI is a JS web app at
 * {@code https://ui.perfetto.dev/}; opening a captured trace is a local
 * drag-and-drop, but we surface the URL so the user can reach the viewer
 * without having to remember it.
 */
public final class PerfettoCommandBuilder {

    public static final String PERFETTO_UI_URL = "https://ui.perfetto.dev/";

    private PerfettoCommandBuilder() {
    }

    /**
     * Argv for {@code perfetto -c <configPath> --txt -o <outputPath>}.
     *
     * @throws IllegalArgumentException if either path contains an unsafe
     *         character (see {@link CpuProfileCommandBuilder#isSafeOutputPath}).
     */
    @NonNull
    public static String[] buildRecord(@NonNull String configPath, @NonNull String outputPath) {
        if (!CpuProfileCommandBuilder.isSafeOutputPath(configPath)) {
            throw new IllegalArgumentException("Unsafe config path: " + configPath);
        }
        if (!CpuProfileCommandBuilder.isSafeOutputPath(outputPath)) {
            throw new IllegalArgumentException("Unsafe output path: " + outputPath);
        }
        return new String[]{
                "perfetto",
                "-c", configPath,
                "--txt",
                "-o", outputPath
        };
    }

    /**
     * The Perfetto UI URL. Stable; callers append no query string because the
     * Perfetto UI accepts the captured trace via local file picker / drag-and-
     * drop only - hosting the trace at a public URL would defeat the privacy
     * posture.
     */
    @NonNull
    public static String perfettoUiUrl() {
        return PERFETTO_UI_URL;
    }
}
