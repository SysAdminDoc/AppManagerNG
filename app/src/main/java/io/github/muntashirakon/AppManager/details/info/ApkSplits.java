// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;

/**
 * An app's split APKs, sorted by what each carries. Bundletool names a configuration split {@code config.<value>},
 * or {@code <module>.config.<value>} for a feature module's, and a split without a config part is a feature module.
 */
public final class ApkSplits {
    @IntDef({TYPE_FEATURE, TYPE_LANGUAGE, TYPE_DENSITY, TYPE_ABI, TYPE_OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    public static final int TYPE_FEATURE = 0;
    public static final int TYPE_LANGUAGE = 1;
    public static final int TYPE_DENSITY = 2;
    public static final int TYPE_ABI = 3;
    public static final int TYPE_OTHER = 4;

    /**
     * The order App Info lists the groups in.
     */
    private static final int[] DISPLAY_ORDER = {TYPE_ABI, TYPE_DENSITY, TYPE_LANGUAGE, TYPE_FEATURE, TYPE_OTHER};

    private static final Pattern DENSITY = Pattern.compile("(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi|anydpi"
            + "|\\d+dpi)");
    private static final Pattern ABI = Pattern.compile("(armeabi|armeabi[_-]v7a|arm64[_-]v8a|x86|x86[_-]64|mips"
            + "|mips64|riscv64)");
    // A language, optionally with a region: en, fil, pt_BR, zh-rTW, b+sr+Latn
    private static final Pattern LANGUAGE = Pattern.compile("([a-z]{2,3}([_-]r?[A-Z]{2})?|b\\+[A-Za-z0-9+]+)");

    public static final class Split {
        @NonNull
        public final String name;
        @Type
        public final int type;
        /**
         * Size of the split's file in bytes, 0 when it can't be read.
         */
        public final long size;

        Split(@NonNull String name, @Type int type, long size) {
            this.name = name;
            this.type = type;
            this.size = size;
        }
    }

    private ApkSplits() {
    }

    @Type
    public static int classify(@NonNull String splitName) {
        int configAt = splitName.startsWith("config.") ? 0 : splitName.indexOf(".config.");
        if (configAt < 0) {
            return TYPE_FEATURE;
        }
        String value = splitName.substring(splitName.indexOf("config.", configAt) + "config.".length());
        String lower = value.toLowerCase(Locale.ROOT);
        if (ABI.matcher(lower).matches()) {
            return TYPE_ABI;
        }
        if (DENSITY.matcher(lower).matches()) {
            return TYPE_DENSITY;
        }
        if (LANGUAGE.matcher(value).matches()) {
            return TYPE_LANGUAGE;
        }
        return TYPE_OTHER;
    }

    /**
     * The installed splits with their sizes, read from the split files. Call off the main thread.
     */
    @NonNull
    public static List<Split> from(@NonNull ApplicationInfo info) {
        if (info.splitNames == null || info.splitNames.length == 0) {
            return Collections.emptyList();
        }
        List<Split> splits = new ArrayList<>(info.splitNames.length);
        for (int i = 0; i < info.splitNames.length; ++i) {
            String name = info.splitNames[i];
            String path = info.splitSourceDirs != null && i < info.splitSourceDirs.length
                    ? info.splitSourceDirs[i] : null;
            long size = path != null ? new File(path).length() : 0;
            splits.add(new Split(name, classify(name), size));
        }
        return splits;
    }

    /**
     * One line per kind of split, like "Languages: config.en, config.fr (24 kB)".
     */
    @NonNull
    public static CharSequence summarize(@NonNull Context context, @NonNull List<Split> splits) {
        List<String> lines = new ArrayList<>();
        for (int type : DISPLAY_ORDER) {
            List<String> names = new ArrayList<>();
            long size = 0;
            for (Split split : splits) {
                if (split.type == type) {
                    names.add(split.name);
                    size += split.size;
                }
            }
            if (!names.isEmpty()) {
                lines.add(context.getString(R.string.split_group_summary, context.getString(labelRes(type)),
                        TextUtils.join(", ", names), Formatter.formatShortFileSize(context, size)));
            }
        }
        return TextUtils.join("\n", lines);
    }

    @StringRes
    private static int labelRes(@Type int type) {
        switch (type) {
            case TYPE_LANGUAGE:
                return R.string.languages;
            case TYPE_DENSITY:
                return R.string.split_type_density;
            case TYPE_ABI:
                return R.string.split_type_abi;
            case TYPE_FEATURE:
                return R.string.split_type_feature;
            case TYPE_OTHER:
            default:
                return R.string.split_type_other;
        }
    }
}
