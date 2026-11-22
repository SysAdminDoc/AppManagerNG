// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.MainThread;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.widget.MaterialAlertView;

/**
 * Shows a language file on Android 10 and older, which have no {@code ResourcesLoader}. The app's
 * and each activity's resources answer from the file ({@link CompatResources}), and text that
 * layouts, menus and settings screens read straight from their XML is put in after they're built,
 * since that text never goes through the resources. Dialog and notification text from code goes
 * through the resources and needs nothing more.
 */
public final class LanguagePackCompat {
    private static final String TAG = LanguagePackManager.TAG;

    @Nullable
    private static volatile CompatPack sActive;

    private LanguagePackCompat() {
    }

    static void setActive(@Nullable CompatPack pack) {
        sActive = pack;
    }

    /** Whether a language file is shown through compatibility mode right now. */
    public static boolean isActive() {
        return sActive != null;
    }

    /**
     * Hands out an app component's resources: the component's own, or with the language file in
     * front while one is shown through compatibility mode. Each component keeps one.
     */
    public static final class Host {
        @Nullable
        private CompatResources mResources;
        @Nullable
        private MenuInflater mMenuBase;
        @Nullable
        private MenuInflater mMenuInflater;

        @NonNull
        public Resources resources(@NonNull Resources base) {
            CompatPack pack = sActive;
            if (pack == null) {
                mResources = null;
                return base;
            }
            CompatResources wrapped = mResources;
            if (wrapped == null || wrapped.base() != base || wrapped.pack() != pack) {
                wrapped = new CompatResources(base, pack);
                mResources = wrapped;
            } else {
                wrapped.followBase();
            }
            return wrapped;
        }

        @NonNull
        public MenuInflater menuInflater(@NonNull Context context, @NonNull MenuInflater base) {
            if (sActive == null) {
                return base;
            }
            if (mMenuInflater == null || mMenuBase != base) {
                mMenuBase = base;
                mMenuInflater = new TranslatingMenuInflater(context, base);
            }
            return mMenuInflater;
        }
    }

    /**
     * Lets an activity's views show the language file. Called before {@code super.onCreate()},
     * so the activity's inflater is set up before AppCompat's, which this one builds views with.
     */
    @MainThread
    public static void attach(@NonNull AppCompatActivity activity) {
        if (sActive == null) {
            return;
        }
        LayoutInflater inflater = activity.getLayoutInflater();
        if (inflater.getFactory() == null) {
            inflater.setFactory2(new CompatInflaterFactory(activity));
        }
    }

    /** The activity's title from its manifest label, which the system read without the file. */
    @MainThread
    public static void applyActivityTitle(@NonNull Activity activity) {
        CompatPack pack = sActive;
        if (pack == null || !pack.appliesTo(activity.getResources().getConfiguration())) {
            return;
        }
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0);
            CharSequence title = info.labelRes != 0 ? pack.text(info.labelRes) : null;
            if (title != null) {
                activity.setTitle(title);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, "No label for " + activity.getComponentName(), e);
        }
    }

    /**
     * A context whose resources show the language file while one is shown through compatibility
     * mode. For contexts the app gets without going through an activity or the application: the
     * one {@code ContextUtils} hands out, services' and widgets'.
     */
    @NonNull
    public static Context wrap(@NonNull Context base) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || base instanceof CompatContext) {
            // Android 11 and later lay the file over the app's own resources
            return base;
        }
        return new CompatContext(base);
    }

    private static final class CompatContext extends ContextWrapper {
        private final Host mHost = new Host();

        CompatContext(@NonNull Context base) {
            super(base);
        }

        @Override
        public Resources getResources() {
            return mHost.resources(super.getResources());
        }
    }

    /** Puts the file's text in a menu built from {@code menuRes} somewhere other than an activity. */
    public static void translateMenu(@NonNull Context context, @MenuRes int menuRes, @NonNull Menu menu) {
        CompatPack pack = sActive;
        if (pack == null || !pack.appliesTo(context.getResources().getConfiguration())) {
            return;
        }
        Resources english = untranslated(context.getResources());
        // The menu each open <menu> element was built into: the one given, then the submenus of
        // the items around it, or null where that item wasn't found
        List<Menu> open = new ArrayList<>();
        MenuItem last = null;
        try (XmlResourceParser parser = context.getResources().getLayout(menuRes)) {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("menu".equals(tag)) {
                        open.add(open.isEmpty() ? menu : last != null && last.hasSubMenu() ? last.getSubMenu() : null);
                    } else if ("item".equals(tag)) {
                        last = translateItem(pack, english, parser, menu,
                                open.isEmpty() ? null : open.get(open.size() - 1));
                    }
                } else if (event == XmlPullParser.END_TAG && "menu".equals(parser.getName()) && !open.isEmpty()) {
                    open.remove(open.size() - 1);
                }
            }
        } catch (XmlPullParserException | IOException | Resources.NotFoundException e) {
            Log.w(TAG, "Couldn't read menu " + menuRes, e);
        }
    }

    /**
     * Finds the menu item an {@code <item>} element made and puts the file's text in it. One with
     * an id is looked up anywhere in {@code root}; one without, such as an entry that opens a
     * submenu, by the English title it still has in {@code parent}.
     */
    @Nullable
    private static MenuItem translateItem(@NonNull CompatPack pack, @NonNull Resources english,
                                          @NonNull XmlResourceParser parser, @NonNull Menu root,
                                          @Nullable Menu parent) {
        int id = 0;
        int titleRes = 0;
        Map<String, CharSequence> texts = new HashMap<>(4);
        for (int i = 0, n = parser.getAttributeCount(); i < n; ++i) {
            String name = parser.getAttributeName(i);
            int res = parser.getAttributeResourceValue(i, 0);
            if ("id".equals(name)) {
                id = res;
            } else if (res != 0) {
                if ("title".equals(name)) {
                    titleRes = res;
                }
                CharSequence text = pack.text(res);
                if (text != null) {
                    texts.put(name, text);
                }
            }
        }
        MenuItem item;
        if (id != 0) {
            item = root.findItem(id);
        } else {
            item = parent != null && titleRes != 0 ? findByTitle(parent, english, titleRes) : null;
        }
        if (item == null) {
            return null;
        }
        CharSequence title = texts.get("title");
        if (title != null) {
            item.setTitle(title);
            if (item.hasSubMenu()) {
                // The header a submenu shows is the title its item had when it was built
                item.getSubMenu().setHeaderTitle(title);
            }
        }
        CharSequence condensed = texts.get("titleCondensed");
        if (condensed != null) {
            item.setTitleCondensed(condensed);
        }
        CharSequence description = texts.get("contentDescription");
        if (description != null) {
            MenuItemCompat.setContentDescription(item, description);
        }
        CharSequence tooltip = texts.get("tooltipText");
        if (tooltip != null) {
            MenuItemCompat.setTooltipText(item, tooltip);
        }
        return item;
    }

    @Nullable
    private static MenuItem findByTitle(@NonNull Menu menu, @NonNull Resources english, int titleRes) {
        CharSequence wanted;
        try {
            wanted = english.getText(titleRes);
        } catch (Resources.NotFoundException e) {
            return null;
        }
        for (int i = 0, n = menu.size(); i < n; ++i) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == Menu.NONE && TextUtils.equals(String.valueOf(item.getTitle()), wanted.toString())) {
                return item;
            }
        }
        return null;
    }

    /** The speed dial built from {@code menuRes} gets the file's labels. */
    public static void translateSpeedDial(@NonNull Context context, @MenuRes int menuRes, @NonNull SpeedDialView view) {
        if (sActive == null) {
            return;
        }
        // The same way the speed dial reads the menu
        PopupMenu popup = new PopupMenu(context, new View(context));
        popup.inflate(menuRes);
        Menu menu = popup.getMenu();
        translateMenu(context, menuRes, menu);
        for (SpeedDialActionItem item : new ArrayList<>(view.getActionItems())) {
            MenuItem menuItem = menu.findItem(item.getId());
            CharSequence title = menuItem != null ? menuItem.getTitle() : null;
            if (title != null && !TextUtils.equals(title, item.getLabel(context))) {
                view.replaceActionItem(item, new SpeedDialActionItem.Builder(item).setLabel(title.toString()).create());
            }
        }
    }

    /**
     * Puts the file's text in preferences just made from {@code xmlRes}. {@code root} is what the
     * XML element with {@code rootKey} became, or with {@code rootKey} {@code null} the XML's
     * root; with {@code merged} the XML's preferences were added to an existing screen, so only
     * those with a key can be found.
     */
    public static void translatePreferences(@NonNull Context context, @XmlRes int xmlRes,
                                            @Nullable PreferenceGroup root, @Nullable String rootKey,
                                            boolean merged) {
        CompatPack pack = sActive;
        if (pack == null || root == null || !pack.appliesTo(context.getResources().getConfiguration())) {
            return;
        }
        XmlPreference xml;
        try {
            xml = XmlPreference.parse(context.getResources(), xmlRes);
        } catch (XmlPullParserException | IOException | Resources.NotFoundException e) {
            Log.w(TAG, "Couldn't read preferences " + xmlRes, e);
            return;
        }
        if (xml == null) {
            return;
        }
        Resources english = untranslated(context.getResources());
        if (merged) {
            for (XmlPreference child : xml.children) {
                translate(pack, english, child, null, root);
            }
            return;
        }
        XmlPreference start = rootKey != null ? xml.find(rootKey) : xml;
        if (start != null) {
            translate(pack, english, start, root, root);
        }
    }

    private static void translate(@NonNull CompatPack pack, @NonNull Resources english, @NonNull XmlPreference node,
                                  @Nullable Preference preference, @NonNull PreferenceGroup screen) {
        if (node.key != null) {
            Preference byKey = screen.findPreference(node.key);
            if (byKey != null) {
                preference = byKey;
            }
        }
        if (preference != null) {
            node.applyTo(preference, pack);
        }
        boolean positional = preference instanceof PreferenceGroup
                && ((PreferenceGroup) preference).getPreferenceCount() == node.children.size();
        for (int i = 0; i < node.children.size(); ++i) {
            XmlPreference child = node.children.get(i);
            Preference candidate = null;
            if (positional) {
                candidate = ((PreferenceGroup) preference).getPreference(i);
                if (!child.describes(candidate, english)) {
                    candidate = null;
                }
            }
            translate(pack, english, child, candidate, screen);
        }
    }

    @NonNull
    private static Resources untranslated(@NonNull Resources res) {
        return res instanceof CompatResources ? ((CompatResources) res).base() : res;
    }

    /** One element of a preferences XML: its key, its text and its children. */
    private static final class XmlPreference {
        @Nullable
        final String key;
        final Map<String, Integer> texts = new HashMap<>(4);
        final List<XmlPreference> children = new ArrayList<>();

        XmlPreference(@Nullable String key) {
            this.key = key;
        }

        @Nullable
        static XmlPreference parse(@NonNull Resources res, @XmlRes int xmlRes)
                throws XmlPullParserException, IOException {
            XmlPreference root = null;
            Deque<XmlPreference> open = new ArrayDeque<>();
            // Elements inside <intent> and <extra> aren't preferences
            int skipDepth = 0;
            try (XmlResourceParser parser = res.getXml(xmlRes)) {
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if (skipDepth > 0 || "intent".equals(tag) || "extra".equals(tag)) {
                            ++skipDepth;
                            continue;
                        }
                        String key = null;
                        Map<String, Integer> texts = new HashMap<>(4);
                        for (int i = 0, n = parser.getAttributeCount(); i < n; ++i) {
                            String name = parser.getAttributeName(i);
                            if ("key".equals(name)) {
                                key = parser.getAttributeValue(i);
                            } else {
                                int id = parser.getAttributeResourceValue(i, 0);
                                if (id != 0) {
                                    texts.put(name, id);
                                }
                            }
                        }
                        XmlPreference node = new XmlPreference(key);
                        node.texts.putAll(texts);
                        if (open.isEmpty()) {
                            root = node;
                        } else {
                            open.peek().children.add(node);
                        }
                        open.push(node);
                    } else if (event == XmlPullParser.END_TAG) {
                        if (skipDepth > 0) {
                            --skipDepth;
                        } else if (!open.isEmpty()) {
                            open.pop();
                        }
                    }
                }
            }
            return root;
        }

        @Nullable
        XmlPreference find(@NonNull String wanted) {
            if (wanted.equals(key)) {
                return this;
            }
            for (XmlPreference child : children) {
                XmlPreference found = child.find(wanted);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        /**
         * Whether a preference at this element's place was made from it: it still shows the text
         * the XML gave it, so a preference the code added or moved isn't touched.
         */
        boolean describes(@Nullable Preference preference, @NonNull Resources english) {
            if (preference == null) {
                return false;
            }
            Integer title = texts.get("title");
            if (title != null) {
                return TextUtils.equals(String.valueOf(preference.getTitle()), english.getString(title));
            }
            Integer summary = texts.get("summary");
            return summary != null && preference.getSummaryProvider() == null
                    && TextUtils.equals(String.valueOf(preference.getSummary()), english.getString(summary));
        }

        void applyTo(@NonNull Preference preference, @NonNull CompatPack pack) {
            for (Map.Entry<String, Integer> e : texts.entrySet()) {
                CharSequence text = pack.text(e.getValue());
                if (text == null) {
                    continue;
                }
                switch (e.getKey()) {
                    case "title":
                        preference.setTitle(text);
                        break;
                    case "summary":
                        if (preference.getSummaryProvider() == null) {
                            preference.setSummary(text);
                        }
                        break;
                    case "summaryOn":
                        if (preference instanceof TwoStatePreference) {
                            ((TwoStatePreference) preference).setSummaryOn(text);
                        }
                        break;
                    case "summaryOff":
                        if (preference instanceof TwoStatePreference) {
                            ((TwoStatePreference) preference).setSummaryOff(text);
                        }
                        break;
                    case "dialogTitle":
                        if (preference instanceof DialogPreference) {
                            ((DialogPreference) preference).setDialogTitle(text);
                        }
                        break;
                    case "dialogMessage":
                        if (preference instanceof DialogPreference) {
                            ((DialogPreference) preference).setDialogMessage(text);
                        }
                        break;
                    case "positiveButtonText":
                        if (preference instanceof DialogPreference) {
                            ((DialogPreference) preference).setPositiveButtonText(text);
                        }
                        break;
                    case "negativeButtonText":
                        if (preference instanceof DialogPreference) {
                            ((DialogPreference) preference).setNegativeButtonText(text);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** Called by {@link CompatInflaterFactory} for each view it builds. */
    static void applyText(@NonNull View view, @NonNull AttributeSet attrs) {
        CompatPack pack = sActive;
        if (pack == null) {
            return;
        }
        boolean checked = false;
        int menuRes = 0;
        for (int i = 0, n = attrs.getAttributeCount(); i < n; ++i) {
            int res = attrs.getAttributeResourceValue(i, 0);
            if (res == 0) {
                continue;
            }
            String name = attrs.getAttributeName(i);
            if ("menu".equals(name)) {
                menuRes = res;
                continue;
            }
            CharSequence text = pack.text(res);
            if (text == null) {
                continue;
            }
            if (!checked) {
                if (!pack.appliesTo(view.getContext().getResources().getConfiguration())) {
                    return;
                }
                checked = true;
            }
            setText(view, name, text);
        }
        if (menuRes != 0) {
            Menu menu = menuOf(view);
            if (menu != null) {
                translateMenu(view.getContext(), menuRes, menu);
            }
        }
    }

    private static void setText(@NonNull View view, @NonNull String attr, @NonNull CharSequence text) {
        switch (attr) {
            case "text":
                if (view instanceof TextView) {
                    ((TextView) view).setText(text);
                } else if (view instanceof MaterialAlertView) {
                    ((MaterialAlertView) view).setText(text);
                }
                break;
            case "hint":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setHint(text);
                } else if (view instanceof TextView) {
                    ((TextView) view).setHint(text);
                }
                break;
            case "contentDescription":
                view.setContentDescription(text);
                break;
            case "tooltipText":
                // Older Android has no tooltips, and AppCompat only reads them for menus
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view.getTooltipText() != null) {
                    view.setTooltipText(text);
                }
                break;
            case "title":
                if (view instanceof Toolbar) {
                    ((Toolbar) view).setTitle(text);
                } else if (view instanceof CollapsingToolbarLayout) {
                    ((CollapsingToolbarLayout) view).setTitle(text);
                } else if (view instanceof android.widget.Toolbar) {
                    ((android.widget.Toolbar) view).setTitle(text);
                }
                break;
            case "subtitle":
                if (view instanceof Toolbar) {
                    ((Toolbar) view).setSubtitle(text);
                } else if (view instanceof android.widget.Toolbar) {
                    ((android.widget.Toolbar) view).setSubtitle(text);
                }
                break;
            case "helperText":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setHelperText(text);
                }
                break;
            case "endIconContentDescription":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setEndIconContentDescription(text);
                }
                break;
            case "startIconContentDescription":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setStartIconContentDescription(text);
                }
                break;
            case "placeholderText":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setPlaceholderText(text);
                }
                break;
            case "prefixText":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setPrefixText(text);
                }
                break;
            case "suffixText":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setSuffixText(text);
                }
                break;
            case "queryHint":
                if (view instanceof androidx.appcompat.widget.SearchView) {
                    ((androidx.appcompat.widget.SearchView) view).setQueryHint(text);
                } else if (view instanceof android.widget.SearchView) {
                    ((android.widget.SearchView) view).setQueryHint(text);
                }
                break;
            case "prompt":
                if (view instanceof Spinner) {
                    ((Spinner) view).setPrompt(text);
                }
                break;
            case "textOn":
                if (view instanceof SwitchCompat) {
                    ((SwitchCompat) view).setTextOn(text);
                } else if (view instanceof ToggleButton) {
                    ((ToggleButton) view).setTextOn(text);
                }
                break;
            case "textOff":
                if (view instanceof SwitchCompat) {
                    ((SwitchCompat) view).setTextOff(text);
                } else if (view instanceof ToggleButton) {
                    ((ToggleButton) view).setTextOff(text);
                }
                break;
            default:
                break;
        }
    }

    /** The menu a view built from its {@code menu} attribute: toolbars, navigation bars and the like. */
    @Nullable
    private static Menu menuOf(@NonNull View view) {
        if (view instanceof Toolbar) {
            return ((Toolbar) view).getMenu();
        }
        try {
            Method getMenu = view.getClass().getMethod("getMenu");
            Object menu = getMenu.invoke(view);
            return menu instanceof Menu ? (Menu) menu : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** An activity's menu inflater that puts the file's text in each menu it builds. */
    private static final class TranslatingMenuInflater extends MenuInflater {
        @NonNull
        private final Context mContext;
        @NonNull
        private final MenuInflater mBase;

        TranslatingMenuInflater(@NonNull Context context, @NonNull MenuInflater base) {
            super(context);
            mContext = context;
            mBase = base;
        }

        @Override
        public void inflate(int menuRes, Menu menu) {
            mBase.inflate(menuRes, menu);
            translateMenu(mContext, menuRes, menu);
        }
    }
}
