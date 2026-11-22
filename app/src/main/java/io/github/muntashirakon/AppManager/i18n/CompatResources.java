// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * The app's resources with a language file's text in front, for Android 10 and older. Text the
 * file has comes from it; everything else, and every other kind of resource, comes from the
 * resources it wraps.
 */
final class CompatResources extends Resources {
    @NonNull
    private final Resources mBase;
    @NonNull
    private final CompatPack mPack;
    /** The wrapped resources' configuration when this one last took it. */
    @Nullable
    private volatile Configuration mFollowed;

    @SuppressWarnings("deprecation")
    CompatResources(@NonNull Resources base, @NonNull CompatPack pack) {
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        mBase = base;
        mPack = pack;
        mFollowed = new Configuration(base.getConfiguration());
    }

    @NonNull
    Resources base() {
        return mBase;
    }

    @NonNull
    CompatPack pack() {
        return mPack;
    }

    /**
     * Takes the wrapped resources' configuration when it changed without going through this one,
     * so themes made here resolve for the same screen.
     */
    void followBase() {
        Configuration now = mBase.getConfiguration();
        if (now.equals(mFollowed)) {
            return;
        }
        synchronized (this) {
            if (!now.equals(mFollowed)) {
                Configuration copy = new Configuration(now);
                super.updateConfiguration(copy, mBase.getDisplayMetrics());
                mFollowed = copy;
            }
        }
    }

    private boolean applies() {
        return mPack.appliesTo(mBase.getConfiguration());
    }

    @Nullable
    private CharSequence translated(int id) {
        return applies() ? mPack.text(id) : null;
    }

    @NonNull
    private Locale locale() {
        Configuration config = mBase.getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return config.getLocales().isEmpty() ? Locale.getDefault() : config.getLocales().get(0);
        }
        //noinspection deprecation
        return config.locale != null ? config.locale : Locale.getDefault();
    }

    @NonNull
    @Override
    public CharSequence getText(int id) throws NotFoundException {
        CharSequence text = translated(id);
        return text != null ? text : mBase.getText(id);
    }

    @Override
    public CharSequence getText(int id, CharSequence def) {
        CharSequence text = id != 0 ? translated(id) : null;
        return text != null ? text : mBase.getText(id, def);
    }

    @NonNull
    @Override
    public String getString(int id) throws NotFoundException {
        CharSequence text = translated(id);
        return text != null ? text.toString() : mBase.getString(id);
    }

    @NonNull
    @Override
    public String getString(int id, Object... formatArgs) throws NotFoundException {
        CharSequence text = translated(id);
        return text != null ? String.format(locale(), text.toString(), formatArgs) : mBase.getString(id, formatArgs);
    }

    @NonNull
    @Override
    public CharSequence getQuantityText(int id, int quantity) throws NotFoundException {
        CharSequence text = applies() ? mPack.quantityText(id, quantity) : null;
        return text != null ? text : mBase.getQuantityText(id, quantity);
    }

    @NonNull
    @Override
    public String getQuantityString(int id, int quantity) throws NotFoundException {
        return getQuantityText(id, quantity).toString();
    }

    @NonNull
    @Override
    public String getQuantityString(int id, int quantity, Object... formatArgs) throws NotFoundException {
        CharSequence text = applies() ? mPack.quantityText(id, quantity) : null;
        return text != null ? String.format(locale(), text.toString(), formatArgs)
                : mBase.getQuantityString(id, quantity, formatArgs);
    }

    /** Array items that point at a string the file translates show the translation. */
    @NonNull
    @Override
    public CharSequence[] getTextArray(int id) throws NotFoundException {
        CharSequence[] items = mBase.getTextArray(id);
        if (applies()) {
            TypedArray refs = mBase.obtainTypedArray(id);
            try {
                for (int i = 0; i < items.length && i < refs.length(); ++i) {
                    CharSequence text = mPack.text(refs.getResourceId(i, 0));
                    if (text != null) {
                        items[i] = text;
                    }
                }
            } finally {
                refs.recycle();
            }
        }
        return items;
    }

    @NonNull
    @Override
    public String[] getStringArray(int id) throws NotFoundException {
        String[] items = mBase.getStringArray(id);
        if (applies()) {
            TypedArray refs = mBase.obtainTypedArray(id);
            try {
                for (int i = 0; i < items.length && i < refs.length(); ++i) {
                    CharSequence text = mPack.text(refs.getResourceId(i, 0));
                    if (text != null) {
                        items[i] = text.toString();
                    }
                }
            } finally {
                refs.recycle();
            }
        }
        return items;
    }

    @Override
    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {
        super.updateConfiguration(config, metrics);
        // Null while the super constructor runs
        //noinspection ConstantValue
        if (mBase != null) {
            mBase.updateConfiguration(config, metrics);
            mFollowed = new Configuration(mBase.getConfiguration());
        }
    }

    // Everything else comes from the wrapped resources, so caches and configuration changes
    // stay theirs.

    @NonNull
    @Override
    public int[] getIntArray(int id) throws NotFoundException {
        return mBase.getIntArray(id);
    }

    @NonNull
    @Override
    public TypedArray obtainTypedArray(int id) throws NotFoundException {
        return mBase.obtainTypedArray(id);
    }

    @Override
    public float getDimension(int id) throws NotFoundException {
        return mBase.getDimension(id);
    }

    @Override
    public int getDimensionPixelOffset(int id) throws NotFoundException {
        return mBase.getDimensionPixelOffset(id);
    }

    @Override
    public int getDimensionPixelSize(int id) throws NotFoundException {
        return mBase.getDimensionPixelSize(id);
    }

    @Override
    public float getFraction(int id, int base, int pbase) {
        return mBase.getFraction(id, base, pbase);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return mBase.getDrawable(id);
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        return mBase.getDrawable(id, theme);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return mBase.getDrawableForDensity(id, density);
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        return mBase.getDrawableForDensity(id, density, theme);
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getColor(int id) throws NotFoundException {
        return mBase.getColor(id);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Override
    public int getColor(int id, @Nullable Theme theme) throws NotFoundException {
        return mBase.getColor(id, theme);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public ColorStateList getColorStateList(int id) throws NotFoundException {
        return mBase.getColorStateList(id);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    @Override
    public ColorStateList getColorStateList(int id, @Nullable Theme theme) throws NotFoundException {
        return mBase.getColorStateList(id, theme);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @NonNull
    @Override
    public Typeface getFont(int id) throws NotFoundException {
        return mBase.getFont(id);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Override
    public float getFloat(int id) {
        return mBase.getFloat(id);
    }

    @Override
    public boolean getBoolean(int id) throws NotFoundException {
        return mBase.getBoolean(id);
    }

    @Override
    public int getInteger(int id) throws NotFoundException {
        return mBase.getInteger(id);
    }

    @NonNull
    @Override
    public XmlResourceParser getLayout(int id) throws NotFoundException {
        return mBase.getLayout(id);
    }

    @NonNull
    @Override
    public XmlResourceParser getAnimation(int id) throws NotFoundException {
        return mBase.getAnimation(id);
    }

    @NonNull
    @Override
    public XmlResourceParser getXml(int id) throws NotFoundException {
        return mBase.getXml(id);
    }

    @NonNull
    @Override
    public InputStream openRawResource(int id) throws NotFoundException {
        return mBase.openRawResource(id);
    }

    @NonNull
    @Override
    public InputStream openRawResource(int id, TypedValue value) throws NotFoundException {
        return mBase.openRawResource(id, value);
    }

    @Override
    public AssetFileDescriptor openRawResourceFd(int id) throws NotFoundException {
        return mBase.openRawResourceFd(id);
    }

    @Override
    public void getValue(int id, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        mBase.getValue(id, outValue, resolveRefs);
    }

    @Override
    public void getValueForDensity(int id, int density, TypedValue outValue, boolean resolveRefs)
            throws NotFoundException {
        mBase.getValueForDensity(id, density, outValue, resolveRefs);
    }

    @Override
    public void getValue(String name, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        mBase.getValue(name, outValue, resolveRefs);
    }

    @Override
    public TypedArray obtainAttributes(AttributeSet set, int[] attrs) {
        return mBase.obtainAttributes(set, attrs);
    }

    @Override
    public DisplayMetrics getDisplayMetrics() {
        return mBase.getDisplayMetrics();
    }

    @Override
    public Configuration getConfiguration() {
        return mBase.getConfiguration();
    }

    @Override
    public int getIdentifier(String name, String defType, String defPackage) {
        return mBase.getIdentifier(name, defType, defPackage);
    }

    @Override
    public String getResourceName(int resid) throws NotFoundException {
        return mBase.getResourceName(resid);
    }

    @Override
    public String getResourcePackageName(int resid) throws NotFoundException {
        return mBase.getResourcePackageName(resid);
    }

    @Override
    public String getResourceTypeName(int resid) throws NotFoundException {
        return mBase.getResourceTypeName(resid);
    }

    @Override
    public String getResourceEntryName(int resid) throws NotFoundException {
        return mBase.getResourceEntryName(resid);
    }

    @Override
    public void parseBundleExtras(XmlResourceParser parser, Bundle outBundle)
            throws XmlPullParserException, IOException {
        mBase.parseBundleExtras(parser, outBundle);
    }

    @Override
    public void parseBundleExtra(String tagName, AttributeSet attrs, Bundle outBundle) throws XmlPullParserException {
        mBase.parseBundleExtra(tagName, attrs, outBundle);
    }
}
