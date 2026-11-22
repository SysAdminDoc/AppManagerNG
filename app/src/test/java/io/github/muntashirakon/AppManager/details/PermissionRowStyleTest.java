// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.R;

/**
 * Issue 43: a dangerous permission only differed by a border the row style draws zero wide, so it looked like any
 * other row. It now reads bold on the theme's error container, in the light, dark and pure black themes.
 */
@RunWith(RobolectricTestRunner.class)
public class PermissionRowStyleTest {
    private static final int[] THEMES = {R.style.AppTheme_V2, R.style.AppTheme_V2_Dark, R.style.AppTheme_V2_Amoled};

    @Test
    public void aDangerousRowIsBoldOnTheErrorContainer() {
        for (int theme : THEMES) {
            MaterialCardView card = row(theme);
            TextView title = card.findViewById(R.id.perm_name);
            TextView protection = card.findViewById(R.id.perm_protection_level);

            PermissionRowStyle.apply(card, title, protection, true);

            int errorContainer = MaterialColors.getColor(card, com.google.android.material.R.attr.colorErrorContainer);
            int surface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface);
            assertTrue(isBold(title));
            assertTrue(isBold(protection));
            assertEquals(errorContainer, card.getCardBackgroundColor().getDefaultColor());
            assertNotEquals("the tint has to show against the page", surface, errorContainer);
        }
    }

    @Test
    public void aNormalRowKeepsThePlainLook() {
        for (int theme : THEMES) {
            MaterialCardView card = row(theme);
            TextView title = card.findViewById(R.id.perm_name);
            TextView protection = card.findViewById(R.id.perm_protection_level);

            PermissionRowStyle.apply(card, title, protection, false);

            assertFalse(isBold(title));
            assertFalse(isBold(protection));
            assertEquals(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface),
                    card.getCardBackgroundColor().getDefaultColor());
        }
    }

    @Test
    public void aRecycledRowLosesTheDangerLook() {
        MaterialCardView card = row(R.style.AppTheme_V2);
        TextView title = card.findViewById(R.id.perm_name);
        TextView protection = card.findViewById(R.id.perm_protection_level);
        PermissionRowStyle.apply(card, title, protection, true);

        // The adapter binds the next permission's text, then styles it
        title.setText("android.permission.INTERNET");
        protection.setText("normal|granted");
        PermissionRowStyle.apply(card, title, protection, false);

        assertFalse(isBold(title));
        assertFalse(isBold(protection));
        assertEquals(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface),
                card.getCardBackgroundColor().getDefaultColor());
    }

    private static boolean isBold(@NonNull TextView view) {
        CharSequence text = view.getText();
        if (!(text instanceof Spanned)) return false;
        for (StyleSpan span : ((Spanned) text).getSpans(0, text.length(), StyleSpan.class)) {
            if (span.getStyle() == Typeface.BOLD && ((Spanned) text).getSpanStart(span) == 0
                    && ((Spanned) text).getSpanEnd(span) == text.length()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static MaterialCardView row(@StyleRes int theme) {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(theme);
        FrameLayout parent = new FrameLayout(activity);
        activity.setContentView(parent);
        MaterialCardView card = (MaterialCardView) activity.getLayoutInflater()
                .inflate(R.layout.item_app_details_perm, parent, false);
        parent.addView(card);
        ((TextView) card.findViewById(R.id.perm_name)).setText("android.permission.CAMERA");
        ((TextView) card.findViewById(R.id.perm_protection_level)).setText("dangerous|granted");
        return card;
    }
}
