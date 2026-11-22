// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.SubMenuBuilder;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.widget.MaterialAlertView;

/**
 * #31: places compatibility mode missed on Android 10 and older, found on an API 29 emulator.
 * Finnish has no built-in translation, so anything the file doesn't reach shows English.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class LanguagePackCompatReachTest {
    private static final String ENTRIES = "\"cancel\": {\"translation\": \"Peruuta\"},"
            + "\"sort\": {\"translation\": \"Lajittele\"},"
            + "\"refresh_rate\": {\"translation\": \"Päivitystiheys\"},"
            + "\"sort_by_process_name\": {\"translation\": \"Prosessin nimi\"},"
            + "\"one_click_ops_notice\": {\"translation\": \"Nämä toiminnot koskevat monia sovelluksia.\"},"
            + "\"change_owner_uid\": {\"translation\": \"Vaihda omistaja (UID)\"}";

    private Context mContext;
    private Locale mLocale;
    private LanguagePackManager mManager;

    @Before
    public void setUp() {
        mLocale = Locale.getDefault();
        LanguagePackCompatTest.forgetStatics();
        forgetContextUtils();
        mContext = ApplicationProvider.getApplicationContext();
        LanguageFileArchiveTest.deleteAll(new File(mContext.getFilesDir(), "language_packs"));
        LanguageFileArchiveTest.deleteAll(new File(mContext.getNoBackupFilesDir(), "language_packs"));
        mManager = new LanguagePackManager(mContext);
        LanguagePackManager.setInstanceForTesting(mManager);
    }

    @After
    public void tearDown() {
        mManager.stop();
        LanguagePackManager.setInstanceForTesting(null);
        mContext.getSharedPreferences("language_packs", Context.MODE_PRIVATE).edit().clear().commit();
        AppPref.getNewInstance(mContext).setPref(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, LangUtils.LANG_AUTO);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        Locale.setDefault(mLocale);
        LanguagePackCompatTest.forgetStatics();
        forgetContextUtils();
    }

    private void useFinnish() throws Exception {
        mManager.importPack(LanguagePackCompatTest.pack("fi", ENTRIES));
        mManager.use("fi", mManager.prepare("fi"));
    }

    @Test
    public void textBuiltFromTheSharedContextShowsTheFile() throws Exception {
        useFinnish();
        // Process states, activity flags and much more are worded through this context
        assertEquals("Peruuta", ContextUtils.getContext().getString(R.string.cancel));
    }

    @Test
    public void servicesShowTheFile() throws Exception {
        useFinnish();
        // Notifications are worded with the service's own context
        ServiceController<Probe> controller = Robolectric.buildService(Probe.class).create();
        try {
            assertEquals("Peruuta", controller.get().getString(R.string.cancel));
        } finally {
            controller.destroy();
        }
    }

    @SuppressLint("RestrictedApi")
    @Test
    public void submenuEntriesWithoutAnIdShowTheFile() throws Exception {
        useFinnish();
        ActivityController<LanguagePackCompatTest.HostActivity> controller =
                Robolectric.buildActivity(LanguagePackCompatTest.HostActivity.class).setup();
        try {
            LanguagePackCompatTest.HostActivity activity = controller.get();
            MenuBuilder menu = new MenuBuilder(activity);
            activity.getMenuInflater().inflate(R.menu.activity_running_apps_actions, menu);
            MenuItem sort = itemWithSubmenu(menu, "Lajittele");
            assertNotNull(titles(menu), sort);
            assertEquals("Lajittele", String.valueOf(((SubMenuBuilder) sort.getSubMenu()).getHeaderTitle()));
            assertNotNull(itemWithSubmenu(menu, "Päivitystiheys"));
            // Entries with an id inside a submenu still get theirs
            assertEquals("Prosessin nimi", String.valueOf(menu.findItem(R.id.action_sort_by_process_name).getTitle()));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void noticesShowTheFile() throws Exception {
        useFinnish();
        ActivityController<LanguagePackCompatTest.HostActivity> controller =
                Robolectric.buildActivity(LanguagePackCompatTest.HostActivity.class).setup();
        try {
            LanguagePackCompatTest.HostActivity activity = controller.get();
            View root = LayoutInflater.from(activity).inflate(R.layout.activity_one_click_ops,
                    new FrameLayout(activity), false);
            MaterialAlertView notice = root.findViewById(R.id.one_click_ops_notice);
            assertNotNull(notice.getEditText());
            assertEquals("Nämä toiminnot koskevat monia sovelluksia.", notice.getEditText().getText().toString());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void iconButtonsInTextFieldsAreNamedFromTheFile() throws Exception {
        useFinnish();
        ActivityController<LanguagePackCompatTest.HostActivity> controller =
                Robolectric.buildActivity(LanguagePackCompatTest.HostActivity.class).setup();
        try {
            // Screen readers name file Properties' change buttons this way
            View properties = LayoutInflater.from(controller.get()).inflate(R.layout.dialog_file_properties,
                    new FrameLayout(controller.get()), false);
            assertTrue(hasEndIconDescription(properties, "Vaihda omistaja (UID)"));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Nullable
    private static MenuItem itemWithSubmenu(@NonNull MenuBuilder menu, @NonNull String title) {
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            if (item.hasSubMenu() && title.equals(String.valueOf(item.getTitle()))) {
                return item;
            }
        }
        return null;
    }

    private static boolean hasEndIconDescription(@NonNull View view, @NonNull String wanted) {
        if (view instanceof TextInputLayout
                && wanted.equals(String.valueOf(((TextInputLayout) view).getEndIconContentDescription()))) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                if (hasEndIconDescription(group.getChildAt(i), wanted)) {
                    return true;
                }
            }
        }
        return false;
    }

    @NonNull
    private static String titles(@NonNull MenuBuilder menu) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < menu.size(); ++i) {
            sb.append(menu.getItem(i).getTitle()).append(" | ");
        }
        return sb.toString();
    }

    private static void forgetContextUtils() {
        try {
            Field field = ContextUtils.class.getDeclaredField("sContext");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static class Probe extends ForegroundService {
        public Probe() {
            super("probe");
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
        }
    }
}
