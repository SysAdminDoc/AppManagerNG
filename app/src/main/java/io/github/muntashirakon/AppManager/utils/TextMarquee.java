// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.tabs.TabLayout;

/**
 * Text in a place with a fixed size, such as a toolbar or a tab of a fixed-width tab bar, scrolls
 * sideways when a translation doesn't fit, instead of being cut with "...". Rows that can grow
 * wrap instead.
 */
public final class TextMarquee {
    /** The text is selected only so it scrolls, so screen readers aren't told it's selected. */
    private static final AccessibilityDelegateCompat NOT_ANNOUNCED_AS_SELECTED = new AccessibilityDelegateCompat() {
        @Override
        public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setSelected(false);
        }
    };

    private TextMarquee() {
    }

    /** One line that scrolls while the view is selected, with faded edges. */
    public static void apply(@NonNull TextView text) {
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        text.setMarqueeRepeatLimit(-1);
        text.setHorizontalFadingEdgeEnabled(true);
    }

    /** The toolbar's title and subtitle scroll, now and whenever the toolbar makes them. */
    public static void applyToToolbar(@NonNull Toolbar toolbar) {
        CharSequence title = toolbar.getTitle();
        CharSequence subtitle = toolbar.getSubtitle();
        for (int i = 0; i < toolbar.getChildCount(); ++i) {
            View child = toolbar.getChildAt(i);
            if (isPlainText(child)) {
                CharSequence text = ((TextView) child).getText();
                // Leave text views a layout put in the toolbar alone
                if (text.length() > 0 && (TextUtils.equals(text, title) || TextUtils.equals(text, subtitle))) {
                    scrollAlways((TextView) child);
                }
            }
        }
        // The toolbar makes its title view the first time it gets a title
        toolbar.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                if (isPlainText(child)) {
                    scrollAlways((TextView) child);
                }
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
    }

    /**
     * Each tab's text scrolls while that tab is picked, for tabs now and tabs added later. Meant
     * for fixed tabs, which share the width; scrollable tabs grow to fit their text.
     */
    public static void applyToTabs(@NonNull TabLayout tabs) {
        if (tabs.getChildCount() == 0 || !(tabs.getChildAt(0) instanceof ViewGroup)) {
            return;
        }
        ViewGroup strip = (ViewGroup) tabs.getChildAt(0);
        for (int i = 0; i < strip.getChildCount(); ++i) {
            applyToTab(strip.getChildAt(i));
        }
        strip.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                applyToTab(child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
    }

    private static void applyToTab(@NonNull View view) {
        // A tab selects its text along with itself, which runs the marquee only on the picked tab
        if (isPlainText(view)) {
            apply((TextView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                applyToTab(group.getChildAt(i));
            }
        }
    }

    private static void scrollAlways(@NonNull TextView text) {
        apply(text);
        text.setSelected(true);
        ViewCompat.setAccessibilityDelegate(text, NOT_ANNOUNCED_AS_SELECTED);
    }

    private static boolean isPlainText(@NonNull View view) {
        return view instanceof TextView && !(view instanceof EditText) && !(view instanceof Button);
    }
}
