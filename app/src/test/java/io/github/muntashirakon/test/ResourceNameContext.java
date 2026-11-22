// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import androidx.annotation.NonNull;

/**
 * A context whose strings come back as their resource names, {@code {{name}}} or
 * {@code {{name|arg1|arg2}}}. Text built from it with every marker removed is exactly the text the
 * code wrote itself, so a test can tell a translatable label from an English word typed into Java.
 */
public final class ResourceNameContext extends ContextWrapper {
    @NonNull
    private final Resources mResources;

    public ResourceNameContext(@NonNull Context base) {
        super(base);
        mResources = new NamedResources(base.getResources());
    }

    @Override
    public Resources getResources() {
        return mResources;
    }

    /** Removes every resource marker, leaving what the code added on its own. */
    @NonNull
    public static String withoutResourceText(@NonNull CharSequence text) {
        return text.toString().replaceAll("\\{\\{[^}]*\\}\\}", "");
    }

    @SuppressWarnings("deprecation")
    private static final class NamedResources extends Resources {
        @NonNull
        private final Resources mReal;

        NamedResources(@NonNull Resources real) {
            super(real.getAssets(), real.getDisplayMetrics(), real.getConfiguration());
            mReal = real;
        }

        @NonNull
        @Override
        public CharSequence getText(int id) {
            return mark(id);
        }

        @NonNull
        @Override
        public String getString(int id) {
            return mark(id);
        }

        @NonNull
        @Override
        public String getString(int id, Object... formatArgs) {
            return mark(id, formatArgs);
        }

        @NonNull
        @Override
        public CharSequence getQuantityText(int id, int quantity) {
            return mark(id);
        }

        @NonNull
        @Override
        public String getQuantityString(int id, int quantity) {
            return mark(id);
        }

        @NonNull
        @Override
        public String getQuantityString(int id, int quantity, Object... formatArgs) {
            return mark(id, formatArgs);
        }

        @NonNull
        private String mark(int id, Object... args) {
            StringBuilder sb = new StringBuilder("{{").append(mReal.getResourceEntryName(id));
            for (Object arg : args) {
                sb.append('|').append(arg);
            }
            return sb.append("}}").toString();
        }
    }
}
