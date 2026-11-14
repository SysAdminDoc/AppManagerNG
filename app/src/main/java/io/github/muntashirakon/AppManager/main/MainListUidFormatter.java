// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.Context;
import android.os.UserHandleHidden;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.R;

/**
 * The UID on a main list row. It leads the row's one metadata line: that line ends in an ellipsis
 * when the row is narrow or the font is big, and a token at the front is the one part of it that
 * can't be cut, however long the version name is.
 */
final class MainListUidFormatter {
    private static final String SEPARATOR = " " + (char) 0x00B7 + " ";

    private MainListUidFormatter() {
    }

    /**
     * The UID as the row shows it: the UID for one user, or the user count and the app ID (the UID
     * differs per user) for several. Read from the item at bind time. {@link
     * ApplicationItem#uidOrAppIds} isn't recomputed when another user's copy of the package joins
     * the row. An app with no installed copy has no UID, whatever a backup or a stale row says.
     */
    @NonNull
    static String valueFor(@NonNull ApplicationItem item) {
        if (!item.isInstalled || item.userIds.length == 0) {
            return "";
        }
        if (item.userIds.length == 1) {
            return String.valueOf(item.uid);
        }
        return item.userIds.length + "+" + UserHandleHidden.getAppId(item.uid);
    }

    /**
     * The same rule that tints the full UID in the details: a shared UID or a package whose state
     * differs between users is worth a second look.
     */
    static boolean isHighlighted(@NonNull ApplicationItem item) {
        return item.sharedUserId != null || item.disabledUserIds.length > 0 || item.uninstalledUserIds.length > 0;
    }

    @NonNull
    static CharSequence metadataLine(@NonNull Context context, @NonNull ApplicationItem item,
                                     @NonNull CharSequence appType, @ColorInt int highlightColor) {
        String version = TextUtils.isEmpty(item.versionTag) ? "-" : item.versionTag;
        String sdk = item.sdkString != null ? item.sdkString : "-";
        String details = version + SEPARATOR + appType + SEPARATOR + sdk;
        String uid = valueFor(item);
        if (uid.isEmpty()) {
            // An archived app has no UID, so the mark takes its place at the front, where the ellipsis
            // can't cut it. The lookup ran on the loader thread, so this makes no call.
            return item.isKnownArchived() ? context.getString(R.string.archived_app) + SEPARATOR + details : details;
        }
        String token = context.getString(R.string.main_list_uid_label, uid);
        SpannableString line = new SpannableString(token + SEPARATOR + details);
        if (isHighlighted(item)) {
            line.setSpan(new ForegroundColorSpan(highlightColor), 0, token.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return line;
    }

    /**
     * What a screen reader says for the token, which spells out what "2+10123" means. Empty when
     * the row shows no UID.
     */
    @NonNull
    static String contentDescription(@NonNull Context context, @NonNull ApplicationItem item) {
        if (valueFor(item).isEmpty()) {
            return "";
        }
        String description;
        if (item.userIds.length > 1) {
            description = context.getResources().getQuantityString(R.plurals.main_list_app_id_multi_user_a11y,
                    item.userIds.length, String.valueOf(UserHandleHidden.getAppId(item.uid)), item.userIds.length);
        } else {
            description = context.getString(R.string.main_list_uid_a11y, String.valueOf(item.uid));
        }
        if (item.sharedUserId != null) {
            description += " " + context.getString(R.string.main_list_shared_user_a11y, item.sharedUserId);
        }
        return description;
    }
}
