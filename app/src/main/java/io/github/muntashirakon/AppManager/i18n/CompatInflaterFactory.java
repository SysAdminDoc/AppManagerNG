// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds an activity's views the way it would anyway (AppCompat and Material first, then the
 * activity, then the platform), and then puts a language file's text in place of the text the
 * view read from its XML. That text comes straight from the string pool, so the resources a view
 * is built with never see it.
 */
final class CompatInflaterFactory implements LayoutInflater.Factory2 {
    /** The packages {@code LayoutInflater} looks in for a tag without one, in its order. */
    private static final String[] PREFIXES = {"android.widget.", "android.webkit.", "android.app.", "android.view."};
    private static final Class<?>[] SIGNATURE = {Context.class, AttributeSet.class};
    private static final Map<String, Constructor<? extends View>> sConstructors = new ConcurrentHashMap<>();
    private static final Set<String> sMissing = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @NonNull
    private final AppCompatActivity mActivity;

    CompatInflaterFactory(@NonNull AppCompatActivity activity) {
        mActivity = activity;
    }

    @Nullable
    @Override
    public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context,
                             @NonNull AttributeSet attrs) {
        View view = mActivity.getDelegate().createView(parent, name, context, attrs);
        if (view == null) {
            // Fragments and anything else the activity builds itself
            view = mActivity.onCreateView(parent, name, context, attrs);
        }
        if (view == null) {
            view = construct(name, context, attrs);
        }
        if (view != null) {
            LanguagePackCompat.applyText(view, attrs);
        }
        return view;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    /**
     * Builds the view with its {@code (Context, AttributeSet)} constructor, or returns {@code null}
     * to leave it to {@code LayoutInflater}, which then reports any error the usual way.
     */
    @Nullable
    private static View construct(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        if ("ViewStub".equals(name)) {
            // LayoutInflater gives a stub its own inflater for later
            return null;
        }
        if (name.indexOf('.') >= 0) {
            return newView(name, context, attrs);
        }
        for (String prefix : PREFIXES) {
            View view = newView(prefix + name, context, attrs);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Nullable
    private static View newView(@NonNull String className, @NonNull Context context, @NonNull AttributeSet attrs) {
        if (sMissing.contains(className)) {
            return null;
        }
        try {
            Constructor<? extends View> constructor = sConstructors.get(className);
            if (constructor == null) {
                Class<?> cls = Class.forName(className, false, context.getClassLoader());
                if (!View.class.isAssignableFrom(cls)) {
                    sMissing.add(className);
                    return null;
                }
                constructor = cls.asSubclass(View.class).getConstructor(SIGNATURE);
                constructor.setAccessible(true);
                sConstructors.put(className, constructor);
            }
            return constructor.newInstance(context, attrs);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            sMissing.add(className);
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
