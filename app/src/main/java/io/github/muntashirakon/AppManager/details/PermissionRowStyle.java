// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/**
 * How a permission or app op row tells a dangerous permission apart: a bold title and protection line on the
 * theme's error container, with the protection line in the matching on-container color. Both colors come from the
 * theme, so the row reads in the light, dark and pure black themes.
 * <p>
 * The emphasis is spans on the text the adapter just set, so a recycled row loses it with its next text. Only the
 * card's background needs putting back, which a normal row does.
 */
public final class PermissionRowStyle {
    private PermissionRowStyle() {
    }

    public static void apply(@NonNull MaterialCardView card, @NonNull TextView title, @NonNull TextView protection,
                             boolean dangerous) {
        if (!dangerous) {
            card.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface));
            return;
        }
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorErrorContainer));
        title.setText(bold(title.getText(), null));
        protection.setText(bold(protection.getText(), MaterialColors.getColor(protection,
                com.google.android.material.R.attr.colorOnErrorContainer)));
    }

    @NonNull
    private static CharSequence bold(@NonNull CharSequence text, @Nullable Integer color) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (color != null) {
            sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
}
