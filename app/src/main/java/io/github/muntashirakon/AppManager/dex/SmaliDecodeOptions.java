// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dex;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.tools.smali.baksmali.BaksmaliOptions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;

public final class SmaliDecodeOptions {
    public static final String COMMENT_LEVEL_NONE = "none";
    public static final String COMMENT_LEVEL_BASIC = "basic";
    public static final String COMMENT_LEVEL_VERBOSE = "verbose";
    public static final String DEFAULT_COMMENT_LEVEL = COMMENT_LEVEL_BASIC;

    private static final Set<String> REMOVABLE_ANNOTATION_TYPES = new HashSet<>(Arrays.asList(
            "Landroid/annotation/NonNull;",
            "Landroid/annotation/Nullable;",
            "Landroid/annotation/RequiresApi;",
            "Landroid/support/annotation/NonNull;",
            "Landroid/support/annotation/Nullable;",
            "Landroid/support/annotation/RequiresApi;",
            "Landroidx/annotation/NonNull;",
            "Landroidx/annotation/Nullable;",
            "Landroidx/annotation/RequiresApi;",
            "Lorg/jetbrains/annotations/NotNull;",
            "Lorg/jetbrains/annotations/Nullable;"
    ));

    @NonNull
    public final String commentLevel;
    public final boolean removeAnnotations;

    public SmaliDecodeOptions(@NonNull String commentLevel, boolean removeAnnotations) {
        this.commentLevel = normalizeCommentLevel(commentLevel);
        this.removeAnnotations = removeAnnotations;
    }

    @NonNull
    public static SmaliDecodeOptions defaults() {
        return new SmaliDecodeOptions(DEFAULT_COMMENT_LEVEL, false);
    }

    public void applyTo(@NonNull BaksmaliOptions options) {
        switch (commentLevel) {
            case COMMENT_LEVEL_NONE:
                options.debugInfo = false;
                options.codeOffsets = false;
                options.accessorComments = false;
                break;
            case COMMENT_LEVEL_VERBOSE:
                options.debugInfo = true;
                options.codeOffsets = true;
                options.accessorComments = true;
                break;
            case COMMENT_LEVEL_BASIC:
            default:
                options.debugInfo = BuildConfig.DEBUG;
                options.codeOffsets = false;
                options.accessorComments = false;
                break;
        }
    }

    @NonNull
    public String postProcess(@NonNull String smali) {
        if (!removeAnnotations) {
            return smali;
        }
        return stripCommonAnnotations(smali);
    }

    @NonNull
    public static String normalizeCommentLevel(@NonNull String commentLevel) {
        switch (commentLevel) {
            case COMMENT_LEVEL_NONE:
            case COMMENT_LEVEL_BASIC:
            case COMMENT_LEVEL_VERBOSE:
                return commentLevel;
            default:
                return DEFAULT_COMMENT_LEVEL;
        }
    }

    @VisibleForTesting
    @NonNull
    static String stripCommonAnnotations(@NonNull String smali) {
        StringBuilder out = new StringBuilder(smali.length());
        String[] lines = smali.split("\\n", -1);
        boolean stripping = false;
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            boolean hasLineBreak = i < lines.length - 1;
            String trimmed = line.trim();
            if (!stripping && trimmed.startsWith(".annotation ") && isRemovableAnnotation(trimmed)) {
                stripping = true;
                continue;
            }
            if (stripping) {
                if (".end annotation".equals(trimmed)) {
                    stripping = false;
                }
                continue;
            }
            out.append(line);
            if (hasLineBreak) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static boolean isRemovableAnnotation(@NonNull String annotationLine) {
        for (String type : REMOVABLE_ANNOTATION_TYPES) {
            if (annotationLine.endsWith(type)) {
                return true;
            }
        }
        return false;
    }
}
