// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.SingleLineTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.TextMarquee;

/**
 * #31: Russian and other long translations were cut with "..." in rows that have room to grow.
 * Rows that can grow wrap now, and text in places with a fixed height scrolls instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "ru")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LongTranslationLayoutTest {
    /** Older and smaller phones are 320dp wide, and the tests run at mdpi. */
    private static final int NARROW_PHONE_PIXELS = 320;

    private static Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
    }

    @Test
    public void aLongSettingsTitleAndSummaryWrapInsteadOfBeingCut() {
        Context context = themed();
        View row = LayoutInflater.from(context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference,
                new FrameLayout(context), false);
        // Bound by the real Preference, the way the settings list binds it
        Preference preference = new Preference(context);
        preference.setTitle(R.string.pref_backup_pause_apps);
        preference.setSummary(R.string.pref_backup_pause_apps_msg);
        preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));
        TextView title = row.findViewById(android.R.id.title);
        TextView summary = row.findViewById(android.R.id.summary);
        // Russian text, not the English
        assertTrue(title.getText().toString(), title.getText().toString().startsWith("Приостанавливать"));

        layOut(row, title, 3);
        assertShownWhole(title, 3);
        assertShownWhole(summary, 3);
    }

    @Test
    public void aLongAppNameAndInfoLineWrapInTheMainList() {
        Context context = themed();
        View row = LayoutInflater.from(context).inflate(R.layout.item_main_v2, new FrameLayout(context), false);
        TextView label = row.findViewById(R.id.label);
        TextView metadata = row.findViewById(R.id.metadata);
        label.setText(context.getString(R.string.pref_backup_pause_apps));
        String dot = " " + (char) 0x00B7 + " ";
        // The line the list shows: UID, version, app type, SDK
        metadata.setText("UID 10123" + dot + "v4.7.3-beta" + dot + context.getString(R.string.user_app) + dot
                + "SDK 35");

        layOut(row, metadata, 2);
        assertShownWhole(metadata, 2);
        // A name too long for two lines on a narrow phone still gets both, and the first isn't cut
        layOut(row, label, 2);
        Layout name = label.getLayout();
        assertEquals(label.getText().toString(), 2, name.getLineCount());
        assertEquals(label.getText().toString(), 0, name.getEllipsisCount(0));
    }

    @Test
    public void tabsOfAFixedWidthScrollTheirTextInsteadOfCuttingIt() {
        Context context = themed();
        View dialog = LayoutInflater.from(context).inflate(R.layout.dialog_backup_restore, new FrameLayout(context), false);
        TabLayout tabs = dialog.findViewById(R.id.tab_layout);
        tabs.removeAllTabs();
        TextMarquee.applyToTabs(tabs);
        tabs.addTab(tabs.newTab().setText(R.string.backup));
        tabs.addTab(tabs.newTab().setText(R.string.restore));
        tabs.addTab(tabs.newTab().setText(R.string.delete_backup));
        dialog.findViewById(R.id.container).setVisibility(View.VISIBLE);
        dialog.measure(View.MeasureSpec.makeMeasureSpec(NARROW_PHONE_PIXELS, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        dialog.layout(0, 0, dialog.getMeasuredWidth(), dialog.getMeasuredHeight());

        List<TextView> texts = new ArrayList<>();
        collectTextViews(tabs, texts);
        assertEquals(3, texts.size());
        for (TextView text : texts) {
            assertMarquee(text, false);
        }
        // The tab that's picked scrolls its text, and picking another moves that along
        assertTrue(texts.get(0).isSelected());
        tabs.selectTab(tabs.getTabAt(2));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(texts.get(2).isSelected());
        assertFalse(texts.get(0).isSelected());
    }

    @Test
    public void aLongToolbarTitleScrolls() {
        ActivityController<ToolbarActivity> controller = Robolectric.buildActivity(ToolbarActivity.class).setup();
        try {
            ToolbarActivity activity = controller.get();
            activity.setTitle(R.string.pref_backup_pause_apps);
            shadowOf(Looper.getMainLooper()).idle();
            TextView title = findText(activity.toolbar, activity.getString(R.string.pref_backup_pause_apps));
            assertNotNull(title);
            assertMarquee(title, true);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    public static class ToolbarActivity extends PerProcessActivity {
        MaterialToolbar toolbar;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            toolbar = new MaterialToolbar(this);
            setContentView(toolbar);
            setSupportActionBar(toolbar);
        }
    }

    private static void assertMarquee(@NonNull TextView text, boolean selected) {
        assertEquals(text.getText().toString(), TextUtils.TruncateAt.MARQUEE, text.getEllipsize());
        if (selected) {
            assertTrue("a marquee only runs while selected", text.isSelected());
        }
        assertEquals(-1, text.getMarqueeRepeatLimit());
        assertTrue("a marquee needs a single line", text.getTransformationMethod() instanceof SingleLineTransformationMethod);
    }

    /**
     * Lays the row out at a narrow phone's width, or narrower when {@code text} would still fit
     * in fewer than {@code lines} lines there, so the check always has something to prove.
     */
    private static void layOut(@NonNull View row, @NonNull TextView text, int lines) {
        int wide = 4000;
        row.measure(View.MeasureSpec.makeMeasureSpec(wide, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int around = wide - (text.getMeasuredWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight());
        float needed = text.getPaint().measureText(text.getText().toString());
        int width = Math.min(NARROW_PHONE_PIXELS, around + (int) (needed / (lines - 0.5f)));
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
    }

    private static void assertShownWhole(@NonNull TextView text, int minLines) {
        Layout layout = text.getLayout();
        assertNotNull(layout);
        String shown = text.getText().toString();
        assertTrue(shown + ": " + layout.getLineCount() + " lines", layout.getLineCount() >= minLines);
        for (int line = 0; line < layout.getLineCount(); ++line) {
            assertEquals(shown + ": cut on line " + line, 0, layout.getEllipsisCount(line));
        }
        assertEquals(shown, text.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
        int room = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        assertTrue(shown + ": " + layout.getHeight() + "px of text in " + room + "px", room >= layout.getHeight());
    }

    private static void collectTextViews(@NonNull View view, @NonNull List<TextView> out) {
        if (view instanceof TextView && ((TextView) view).getText().length() > 0) {
            out.add((TextView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                collectTextViews(group.getChildAt(i), out);
            }
        }
    }

    @Nullable
    private static TextView findText(@NonNull View view, @NonNull String text) {
        List<TextView> all = new ArrayList<>();
        collectTextViews(view, all);
        for (TextView tv : all) {
            if (text.contentEquals(tv.getText())) {
                return tv;
            }
        }
        return null;
    }
}
