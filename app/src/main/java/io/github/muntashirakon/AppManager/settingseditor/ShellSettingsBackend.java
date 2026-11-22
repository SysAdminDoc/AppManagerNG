// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.List;

import io.github.muntashirakon.AppManager.runner.Runner;

/** Runs the {@code settings} command through the root, ADB or Shizuku shell. */
public class ShellSettingsBackend implements SettingsBackend {
    /** What the shell printed and how it ended. */
    public static final class Output {
        public final int exitCode;
        @NonNull
        public final List<String> stdout;
        @NonNull
        public final List<String> stderr;

        public Output(int exitCode, @NonNull List<String> stdout, @NonNull List<String> stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public interface Shell {
        @NonNull
        Output run(@NonNull String command);
    }

    @NonNull
    public static Shell privilegedShell() {
        return command -> {
            Runner.Result result = Runner.runCommand(command);
            return new Output(result.getExitCode(), result.getOutputAsList(), result.getStderr());
        };
    }

    @NonNull
    private final Shell mShell;
    private final int mUserId;
    /** Android 5 has no {@code settings list}, so listing falls back to the provider there. */
    @Nullable
    private final SettingsBackend mListFallback;

    public ShellSettingsBackend(@NonNull Shell shell, int userId, @Nullable SettingsBackend listFallback) {
        mShell = shell;
        mUserId = userId;
        mListFallback = listFallback;
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public boolean canWrite(@NonNull SettingsTable table) {
        return true;
    }

    @NonNull
    @Override
    public List<SettingEntry> list(@NonNull SettingsTable table) throws IOException {
        Output output = mShell.run(SettingsCommands.list(table, mUserId));
        if (output.exitCode == 0) {
            return SettingsListParser.parse(output.stdout);
        }
        if (mListFallback != null) {
            return mListFallback.list(table);
        }
        throw failure(output);
    }

    @Nullable
    @Override
    public String get(@NonNull SettingsTable table, @NonNull String key) throws IOException {
        Output output = run(SettingsCommands.get(table, key, mUserId));
        StringBuilder value = new StringBuilder();
        for (String line : output.stdout) {
            if (value.length() > 0) {
                value.append('\n');
            }
            value.append(line);
        }
        return "null".contentEquals(value) ? null : value.toString();
    }

    @Override
    public void put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value) throws IOException {
        run(SettingsCommands.put(table, key, value, mUserId));
    }

    @Override
    public void delete(@NonNull SettingsTable table, @NonNull String key) throws IOException {
        run(SettingsCommands.delete(table, key, mUserId));
    }

    @NonNull
    private Output run(@NonNull String command) throws IOException {
        Output output = mShell.run(command);
        if (output.exitCode != 0) {
            throw failure(output);
        }
        return output;
    }

    @VisibleForTesting
    @NonNull
    static IOException failure(@NonNull Output output) {
        // The first line of the error says what went wrong; the rest is a stack trace.
        String reason = output.stderr.isEmpty() ? "exit code " + output.exitCode : output.stderr.get(0);
        return new IOException(reason);
    }
}
