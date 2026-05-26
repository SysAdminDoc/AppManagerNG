// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import androidx.annotation.NonNull;

/**
 * Shared argv / argument validator for the privileged runner used by the
 * T20-A Perfetto exporter and the T20-B simpleperf profile-capture action.
 *
 * <p>Both builders already validate their own inputs, but the privileged
 * runner is invoked by several call paths (root, Shizuku, ADB tcp). When
 * an inadvertent shell-redirection or path-traversal slips through the
 * builder it should still be rejected at the runner boundary. This class
 * is the single source of truth for that gate: callers must run every
 * argv through {@link #validateArgv} before handing it off to the
 * privileged surface.
 *
 * <p>The validator is intentionally narrow:
 * <ul>
 *   <li>{@link #validateArgv(String[])} - reject argv arrays containing
 *       shell metacharacters, control bytes, embedded newlines, or
 *       path-traversal tokens.</li>
 *   <li>{@link #validatePath(String)} - the same predicate, but tighter:
 *       additionally rejects path-traversal segments ({@code ..}) and the
 *       empty string.</li>
 *   <li>{@link #validatePackageName(String)} - Android package-name
 *       format, mirroring {@code PackageManager#validateName}.</li>
 *   <li>{@link #isSafeArgument(String)} / {@link #isSafePath(String)} -
 *       non-throwing predicates for call sites that want to branch
 *       without using exception flow control.</li>
 * </ul>
 *
 * <p>All methods are pure functions and JVM-clean.
 */
public final class PrivilegedRunnerArgValidator {

    /** Hard ceiling per argument; argv blobs longer than this point at abuse. */
    public static final int MAX_ARG_LENGTH = 4096;

    /** Hard ceiling on argv elements; an argv with more than this is rejected. */
    public static final int MAX_ARGV_LENGTH = 64;

    private PrivilegedRunnerArgValidator() {
    }

    /** Outcome of a validation pass. */
    public enum Rejection {
        OK,
        NULL_ARGUMENT,
        EMPTY_ARGUMENT,
        TOO_LONG,
        ARGV_TOO_LONG,
        CONTROL_CHARACTER,
        SHELL_METACHARACTER,
        PATH_TRAVERSAL,
        INVALID_PACKAGE_NAME
    }

    /**
     * Validate an entire argv. Throws if any element is unsafe.
     *
     * @throws IllegalArgumentException with a stable suffix
     *         {@code "[reason=<Rejection>, index=<i>]"} so callers can
     *         show a precise error to the UI without re-parsing the
     *         message.
     */
    public static void validateArgv(@NonNull String[] argv) {
        if (argv.length > MAX_ARGV_LENGTH) {
            throw new IllegalArgumentException(
                    "argv too long: " + argv.length + " > " + MAX_ARGV_LENGTH
                            + " [reason=" + Rejection.ARGV_TOO_LONG + ", index=-1]");
        }
        for (int i = 0; i < argv.length; ++i) {
            Rejection r = classifyArgument(argv[i]);
            if (r != Rejection.OK) {
                throw new IllegalArgumentException(
                        "unsafe argv[" + i + "]: " + describe(argv[i])
                                + " [reason=" + r + ", index=" + i + "]");
            }
        }
    }

    /** Validate a single argument; mirrors {@link #validateArgv} for one element. */
    public static void validateArgument(@NonNull String arg) {
        Rejection r = classifyArgument(arg);
        if (r != Rejection.OK) {
            throw new IllegalArgumentException(
                    "unsafe argument: " + describe(arg) + " [reason=" + r + ", index=0]");
        }
    }

    /** Validate a path argument. Adds {@code ..} traversal rejection on top of {@link #classifyArgument}. */
    public static void validatePath(@NonNull String path) {
        Rejection r = classifyPath(path);
        if (r != Rejection.OK) {
            throw new IllegalArgumentException(
                    "unsafe path: " + describe(path) + " [reason=" + r + ", index=0]");
        }
    }

    public static void validatePackageName(@NonNull String packageName) {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException(
                    "invalid package name: " + describe(packageName)
                            + " [reason=" + Rejection.INVALID_PACKAGE_NAME + ", index=0]");
        }
    }

    public static boolean isSafeArgument(@NonNull String arg) {
        return classifyArgument(arg) == Rejection.OK;
    }

    public static boolean isSafePath(@NonNull String path) {
        return classifyPath(path) == Rejection.OK;
    }

    public static boolean isValidPackageName(@NonNull String name) {
        if (name.isEmpty() || name.length() > 255) return false;
        if (name.indexOf('.') < 0) return false;
        boolean lastWasDot = true;
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '.') {
                if (lastWasDot) return false;
                lastWasDot = true;
                continue;
            }
            boolean valid;
            if (lastWasDot) {
                valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            } else {
                valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '_';
            }
            if (!valid) return false;
            lastWasDot = false;
        }
        return !lastWasDot;
    }

    /** Non-throwing classifier for arguments. Exposed for call sites that branch. */
    public static Rejection classifyArgument(String arg) {
        if (arg == null) return Rejection.NULL_ARGUMENT;
        if (arg.isEmpty()) return Rejection.EMPTY_ARGUMENT;
        if (arg.length() > MAX_ARG_LENGTH) return Rejection.TOO_LONG;
        for (int i = 0; i < arg.length(); ++i) {
            char c = arg.charAt(i);
            if (c < 0x20 || c == 0x7f) return Rejection.CONTROL_CHARACTER;
            switch (c) {
                case '`':
                case '$':
                case '"':
                case '\'':
                case ';':
                case '&':
                case '|':
                case '<':
                case '>':
                case '*':
                case '?':
                case '!':
                case '\\':
                case '\n':
                case '\r':
                    return Rejection.SHELL_METACHARACTER;
                default:
                    break;
            }
        }
        return Rejection.OK;
    }

    /** Non-throwing path classifier. Adds {@code ..} traversal rejection. */
    public static Rejection classifyPath(String path) {
        Rejection base = classifyArgument(path);
        if (base != Rejection.OK) return base;
        // path is non-null/non-empty here; check traversal segments.
        if (containsTraversalSegment(path)) return Rejection.PATH_TRAVERSAL;
        return Rejection.OK;
    }

    static boolean containsTraversalSegment(@NonNull String path) {
        int len = path.length();
        int i = 0;
        while (i < len) {
            int slash = path.indexOf('/', i);
            int end = (slash < 0) ? len : slash;
            if (end - i == 2 && path.charAt(i) == '.' && path.charAt(i + 1) == '.') {
                return true;
            }
            if (slash < 0) break;
            i = slash + 1;
        }
        return false;
    }

    /** Defensive describer that truncates long arguments for error messages. */
    static String describe(String arg) {
        if (arg == null) return "<null>";
        if (arg.isEmpty()) return "<empty>";
        if (arg.length() > 64) {
            return arg.substring(0, 60) + "..." + " (len=" + arg.length() + ")";
        }
        return arg;
    }
}
