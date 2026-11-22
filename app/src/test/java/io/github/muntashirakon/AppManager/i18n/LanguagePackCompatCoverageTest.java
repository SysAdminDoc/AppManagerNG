// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.PreferenceFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LangUtils;

/**
 * #31: how much of the app a language file reaches on Android 10 and older. A file with every
 * string translated is used, then every layout, menu and settings screen is built and each text
 * that came from a string is counted as shown from the file or left in English.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class LanguagePackCompatCoverageTest {
    /** A language the app has no built-in translation for, so whatever the file misses is English. */
    private static final String CODE = "fi";
    private static final String MARK = "[fi] ";

    private Context mContext;
    private Locale mLocale;
    private final Set<String> mEnglish = new HashSet<>();

    private static final class Count {
        int translated;
        int missed;
        final List<String> examples = new ArrayList<>();

        void add(@Nullable CharSequence text, @NonNull String where) {
            if (text == null || text.length() == 0) {
                return;
            }
            String s = text.toString();
            if (s.startsWith(MARK)) {
                ++translated;
            } else if (s.length() > 1 && sEnglish.contains(s)) {
                ++missed;
                if (examples.size() < 40) {
                    examples.add(where + ": " + s);
                }
            }
        }

        @NonNull
        String report(@NonNull String what) {
            int total = translated + missed;
            return String.format(Locale.ROOT, "%s: %d of %d texts from the file (%.1f%%)", what, translated, total,
                    total == 0 ? 100.0 : 100.0 * translated / total);
        }

        double share() {
            int total = translated + missed;
            return total == 0 ? 1 : (double) translated / total;
        }
    }

    private static Set<String> sEnglish;

    @Before
    public void setUp() {
        mLocale = Locale.getDefault();
        LanguagePackCompatTest.forgetStatics();
        mContext = ApplicationProvider.getApplicationContext();
        LanguageFileArchiveTest.deleteAll(new File(mContext.getFilesDir(), "language_packs"));
        LanguageFileArchiveTest.deleteAll(new File(mContext.getNoBackupFilesDir(), "language_packs"));
        sEnglish = mEnglish;
    }

    @After
    public void tearDown() {
        LanguagePackManager.getInstance(mContext).stop();
        LanguagePackManager.setInstanceForTesting(null);
        mContext.getSharedPreferences("language_packs", Context.MODE_PRIVATE).edit().clear().commit();
        AppPref.getNewInstance(mContext).setPref(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, LangUtils.LANG_AUTO);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        Locale.setDefault(mLocale);
        LanguagePackCompatTest.forgetStatics();
        sEnglish = null;
    }

    @Test
    public void layoutsMenusAndSettingsShowTheLanguageFile() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        LanguagePackManager.setInstanceForTesting(manager);
        manager.importPack(everyString(manager.source()));
        manager.use(CODE, manager.prepare(CODE));

        Count layouts = new Count();
        Count menus = new Count();
        Count settings = new Count();
        List<String> notBuilt = new ArrayList<>();
        ActivityController<LanguagePackCompatTest.HostActivity> controller =
                Robolectric.buildActivity(LanguagePackCompatTest.HostActivity.class).setup();
        try {
            LanguagePackCompatTest.HostActivity activity = controller.get();
            LayoutInflater inflater = LayoutInflater.from(activity);
            for (Map.Entry<String, Integer> layout : ids(R.layout.class).entrySet()) {
                View root;
                try {
                    root = inflater.inflate(layout.getValue(), new FrameLayout(activity), false);
                } catch (Throwable th) {
                    try {
                        // A <merge> layout
                        FrameLayout parent = new FrameLayout(activity);
                        inflater.inflate(layout.getValue(), parent, true);
                        root = parent;
                    } catch (Throwable th2) {
                        notBuilt.add("layout/" + layout.getKey() + " (" + th2.getClass().getSimpleName() + ")");
                        continue;
                    }
                }
                countViews(root, layouts, "layout/" + layout.getKey());
            }
            for (Map.Entry<String, Integer> menuRes : ids(R.menu.class).entrySet()) {
                MenuBuilder menu = new MenuBuilder(activity);
                try {
                    activity.getMenuInflater().inflate(menuRes.getValue(), menu);
                } catch (Throwable th) {
                    notBuilt.add("menu/" + menuRes.getKey() + " (" + th.getClass().getSimpleName() + ")");
                    continue;
                }
                countMenu(menu, menus, "menu/" + menuRes.getKey());
            }
            FragmentManager fm = activity.getSupportFragmentManager();
            for (Map.Entry<String, Integer> xml : ids(R.xml.class).entrySet()) {
                if (!xml.getKey().startsWith("preferences_")) {
                    continue;
                }
                XmlHost host = new XmlHost();
                try {
                    fm.beginTransaction().add(android.R.id.content, host).commitNow();
                    host.load(xml.getValue());
                    countPreferences(host.getPreferenceScreen(), settings, "xml/" + xml.getKey());
                } catch (Throwable th) {
                    notBuilt.add("xml/" + xml.getKey() + " (" + th.getClass().getSimpleName() + ")");
                } finally {
                    try {
                        fm.beginTransaction().remove(host).commitNow();
                    } catch (Throwable ignore) {
                    }
                }
            }
        } finally {
            controller.pause().stop().destroy();
        }

        System.out.println("[coverage] " + layouts.report("Layouts"));
        System.out.println("[coverage] " + menus.report("Menus"));
        System.out.println("[coverage] " + settings.report("Settings screens"));
        System.out.println("[coverage] Not built in the test: " + notBuilt);
        for (String example : layouts.examples) {
            System.out.println("[coverage] English: " + example);
        }
        for (String example : menus.examples) {
            System.out.println("[coverage] English: " + example);
        }
        for (String example : settings.examples) {
            System.out.println("[coverage] English: " + example);
        }
        assertTrue(layouts.report("Layouts"), layouts.translated > 500 && layouts.share() >= 0.9);
        assertTrue(menus.report("Menus"), menus.translated > 100 && menus.share() >= 0.9);
        assertTrue(settings.report("Settings screens"), settings.translated > 100 && settings.share() >= 0.9);
    }

    /** A language file with every string the app has, each one marked so it can be told apart. */
    private byte[] everyString(@NonNull AndroidSourceStrings source) throws Exception {
        JSONObject translations = new JSONObject();
        for (String key : source.keys()) {
            SourceString src = source.get(key);
            TextValue english = src == null ? null : src.toMarkupValue();
            if (english == null) {
                continue;
            }
            JSONObject entry = new JSONObject();
            if (english.isForms()) {
                JSONObject forms = new JSONObject();
                for (Map.Entry<String, String> form : english.forms().entrySet()) {
                    forms.put(form.getKey(), MARK + form.getValue());
                }
                entry.put("translation", forms);
            } else {
                entry.put("translation", MARK + english.string());
                mEnglish.add(src.text().text);
            }
            translations.put(key, entry);
        }
        JSONObject pack = new JSONObject();
        pack.put("schema_version", 1);
        pack.put("language_code", CODE);
        pack.put("language_name", "Suomi");
        pack.put("translations", translations);
        return pack.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void countViews(@NonNull View view, @NonNull Count count, @NonNull String where) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            count.add(text.getText(), where);
            count.add(text.getHint(), where);
        }
        if (view instanceof TextInputLayout) {
            TextInputLayout input = (TextInputLayout) view;
            count.add(input.getHint(), where);
            count.add(input.getHelperText(), where);
        }
        count.add(view.getContentDescription(), where);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                countViews(group.getChildAt(i), count, where);
            }
        }
    }

    private static void countMenu(@NonNull Menu menu, @NonNull Count count, @NonNull String where) {
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            count.add(item.getTitle(), where);
            if (item.hasSubMenu()) {
                countMenu(item.getSubMenu(), count, where);
            }
        }
    }

    private static void countPreferences(@Nullable PreferenceGroup group, @NonNull Count count, @NonNull String where) {
        if (group == null) {
            return;
        }
        for (int i = 0; i < group.getPreferenceCount(); ++i) {
            Preference preference = group.getPreference(i);
            count.add(preference.getTitle(), where);
            count.add(preference.getSummary(), where);
            if (preference instanceof PreferenceGroup) {
                countPreferences((PreferenceGroup) preference, count, where);
            }
        }
    }

    @NonNull
    private static Map<String, Integer> ids(@NonNull Class<?> rClass) throws IllegalAccessException {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Field field : rClass.getFields()) {
            if (field.getType() == int.class) {
                out.put(field.getName(), field.getInt(null));
            }
        }
        return out;
    }

    public static class XmlHost extends PreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        }

        void load(int xml) {
            setPreferencesFromResource(xml, null);
        }

        @Override
        public int getTitle() {
            return R.string.settings;
        }
    }
}
