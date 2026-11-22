// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.os.LocaleListCompat;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;

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
import java.util.Locale;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.PreferenceFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.test.CachedAppContext;

/**
 * #31: Android 5 to 10 have no {@code ResourcesLoader}, so a language file is shown there through
 * a compatibility mode: the app's resources answer from the file, and text that layouts, menus
 * and settings screens read straight from their XML is put in after they're built.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 29})
public class LanguagePackCompatTest {
    private static final String ENTRIES = "\"cancel\": {\"original\": \"Cancel\", \"translation\": \"Abbrechen\"},"
            + "\"no_of_trackers\": {\"translation\": {\"one\": \"%1$d Tracker\", \"other\": \"%1$d Tracker gesamt\"}},"
            + "\"text_include_device_info\": {\"translation\": \"Geraeteinfos beilegen\"},"
            + "\"user_manual\": {\"translation\": \"Handbuch\"},"
            + "\"list_options\": {\"translation\": \"Listenoptionen\"},"
            + "\"pref_pure_black_theme\": {\"translation\": \"Tiefschwarz\"},"
            + "\"pref_pure_black_theme_msg\": {\"translation\": \"Ganz schwarzer Hintergrund im Dunkelmodus.\"}";

    private Context mContext;
    private Locale mLocale;

    @Before
    public void setUp() {
        mLocale = Locale.getDefault();
        forgetStatics();
        mContext = ApplicationProvider.getApplicationContext();
        LanguageFileArchiveTest.deleteAll(new File(mContext.getFilesDir(), "language_packs"));
        LanguageFileArchiveTest.deleteAll(new File(mContext.getNoBackupFilesDir(), "language_packs"));
    }

    @After
    public void tearDown() {
        LanguagePackManager instance = LanguagePackManager.getInstance(mContext);
        instance.stop();
        LanguagePackManager.setInstanceForTesting(null);
        mContext.getSharedPreferences("language_packs", Context.MODE_PRIVATE).edit().clear().commit();
        AppPref.getNewInstance(mContext).setPref(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, LangUtils.LANG_AUTO);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        Locale.setDefault(mLocale);
        forgetStatics();
    }

    @Test
    public void aLanguageFileWorksOnOlderAndroid() throws Exception {
        LanguagePackManager manager = new LanguagePackManager(mContext);
        LanguagePackManager.setInstanceForTesting(manager);
        manager.importPack(pack("de", ENTRIES));
        manager.use("de", manager.prepare("de"));

        // Text the code asks for
        assertEquals("Abbrechen", mContext.getString(R.string.cancel));
        assertEquals("3 Tracker gesamt", mContext.getResources().getQuantityString(R.plurals.no_of_trackers, 3, 3));
        assertEquals("1 Tracker", mContext.getResources().getQuantityString(R.plurals.no_of_trackers, 1, 1));

        ActivityController<HostActivity> controller = Robolectric.buildActivity(HostActivity.class).setup();
        try {
            HostActivity activity = controller.get();
            assertEquals("Abbrechen", activity.getString(R.string.cancel));
            // A layout's text from its XML
            TextView checkbox = inflateSendLog(activity);
            assertEquals("Geraeteinfos beilegen", checkbox.getText().toString());
            // A menu's titles
            MenuBuilder menu = new MenuBuilder(activity);
            activity.getMenuInflater().inflate(R.menu.activity_main_actions, menu);
            assertEquals("Handbuch", String.valueOf(menu.findItem(R.id.action_instructions).getTitle()));
            assertEquals("Listenoptionen", String.valueOf(menu.findItem(R.id.action_list_options).getTitle()));
            // A settings screen
            AppearanceHost fragment = new AppearanceHost();
            activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment).commitNow();
            shadowOf(Looper.getMainLooper()).idle();
            Preference pureBlack = fragment.findPreference("app_theme_pure_black");
            assertNotNull(pureBlack);
            assertEquals("Tiefschwarz", String.valueOf(pureBlack.getTitle()));
            assertEquals("Ganz schwarzer Hintergrund im Dunkelmodus.", String.valueOf(pureBlack.getSummary()));
        } finally {
            controller.pause().stop().destroy();
        }

        // Stopping brings back the app's own text everywhere
        manager.stop();
        assertEquals("Cancel", mContext.getString(R.string.cancel));
        ActivityController<HostActivity> english = Robolectric.buildActivity(HostActivity.class).setup();
        try {
            HostActivity activity = english.get();
            assertEquals("Include device information", inflateSendLog(activity).getText().toString());
            MenuBuilder menu = new MenuBuilder(activity);
            activity.getMenuInflater().inflate(R.menu.activity_main_actions, menu);
            assertEquals("User manual", String.valueOf(menu.findItem(R.id.action_instructions).getTitle()));
        } finally {
            english.pause().stop().destroy();
        }
    }

    private static TextView inflateSendLog(HostActivity activity) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_send_log, null, false);
        return root.findViewById(android.R.id.checkbox);
    }

    public static class HostActivity extends PerProcessActivity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
    }

    public static class AppearanceHost extends PreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.preferences_appearance, rootKey);
        }

        @Override
        public int getTitle() {
            return R.string.pref_cat_appearance;
        }
    }

    static void forgetStatics() {
        CachedAppContext.forget();
        try {
            Field field = AppPref.class.getDeclaredField("sAppPref");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static byte[] pack(String code, String entries) {
        return ("{\"schema_version\": 1, \"language_code\": \"" + code + "\", \"language_name\": \"Deutsch\","
                + " \"translations\": {" + entries + "}}").getBytes(StandardCharsets.UTF_8);
    }
}
