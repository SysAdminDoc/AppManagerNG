// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.Context;
import android.os.UserHandleHidden;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;

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

    /**
     * The details line for the list's sort order: the value the list is sorted by comes right after the UID, where
     * the ellipsis can't cut it, and a target SDK sort moves the SDK there. A sort with no value to show (label,
     * package, user or system, frozen, backup, UID) and {@code followSort} off keep the usual line.
     */
    @NonNull
    static CharSequence metadataLine(@NonNull Context context, @NonNull ApplicationItem item,
                                     @NonNull CharSequence appType, @ColorInt int highlightColor,
                                     @MainListOptions.SortOrder int sortBy, boolean followSort) {
        if (!followSort) {
            return metadataLine(context, item, appType, highlightColor);
        }
        String version = TextUtils.isEmpty(item.versionTag) ? "-" : item.versionTag;
        String sdk = item.sdkString != null ? item.sdkString : "-";
        if (sortBy == MainListOptions.SORT_BY_TARGET_SDK) {
            return withUid(context, item, sdk + SEPARATOR + version + SEPARATOR + appType, highlightColor);
        }
        String value = sortedValue(context, item, sortBy);
        if (value.isEmpty()) {
            return metadataLine(context, item, appType, highlightColor);
        }
        return withUid(context, item, value + SEPARATOR + version + SEPARATOR + appType + SEPARATOR + sdk,
                highlightColor);
    }

    /**
     * The value the list is sorted by, as the row shows it, or an empty string for a sort that has none or a value
     * that isn't known (a date or size of zero means it was never read, for example without usage access).
     */
    @NonNull
    static String sortedValue(@NonNull Context context, @NonNull ApplicationItem item,
                              @MainListOptions.SortOrder int sortBy) {
        switch (sortBy) {
            case MainListOptions.SORT_BY_INSTALLATION_DATE:
                return date(context, R.string.main_list_sort_installed, item.firstInstallTime);
            case MainListOptions.SORT_BY_LAST_UPDATE:
                return date(context, R.string.main_list_sort_updated, orZero(item.lastUpdateTime));
            case MainListOptions.SORT_BY_LAST_USAGE_TIME:
                return date(context, R.string.main_list_sort_last_used, orZero(item.lastUsageTime));
            case MainListOptions.SORT_BY_LAST_ACTION:
                return date(context, R.string.main_list_sort_last_action, orZero(item.lastActionTime));
            case MainListOptions.SORT_BY_TOTAL_SIZE:
                return size(context, R.string.main_list_sort_size, orZero(item.totalSize));
            case MainListOptions.SORT_BY_DATA_USAGE:
                return size(context, R.string.main_list_sort_data, orZero(item.dataUsage));
            case MainListOptions.SORT_BY_OPEN_COUNT:
                return item.openCount <= 0 ? "" : context.getResources().getQuantityString(
                        R.plurals.main_list_sort_opened, item.openCount, item.openCount);
            case MainListOptions.SORT_BY_SCREEN_TIME: {
                long screenTime = orZero(item.screenTime);
                return screenTime <= 0 ? "" : context.getString(R.string.main_list_sort_screen_time,
                        DateUtils.getFormattedDuration(context, screenTime));
            }
            case MainListOptions.SORT_BY_TRACKERS: {
                int trackers = orZero(item.trackerCount);
                int blocked = Math.min(trackers, orZero(item.trackerBlockedCount));
                String count = context.getResources().getQuantityString(R.plurals.main_list_sort_trackers,
                        trackers, trackers);
                return blocked <= 0 ? count : count + ", " + context.getResources().getQuantityString(
                        R.plurals.main_list_sort_blocked, blocked, blocked);
            }
            case MainListOptions.SORT_BY_BLOCKED_COMPONENTS: {
                int blocked = orZero(item.blockedCount);
                return context.getResources().getQuantityString(R.plurals.main_list_sort_blocked, blocked, blocked);
            }
            case MainListOptions.SORT_BY_DANGEROUS_PERMS: {
                int total = orZero(item.dangerousPermTotal);
                return total <= 0 ? "" : context.getString(R.string.main_list_sort_dangerous,
                        orZero(item.dangerousPermGranted), total);
            }
            case MainListOptions.SORT_BY_SHA:
                return TextUtils.isEmpty(item.issuerShortName) ? ""
                        : context.getString(R.string.main_list_sort_signer, item.issuerShortName);
            default:
                return "";
        }
    }

    /**
     * What a screen reader says for the sorted value, or an empty string when the row shows none.
     */
    @NonNull
    static String sortedValueDescription(@NonNull Context context, @NonNull ApplicationItem item,
                                         @MainListOptions.SortOrder int sortBy, boolean followSort) {
        if (!followSort) {
            return "";
        }
        String value = sortedValue(context, item, sortBy);
        return value.isEmpty() ? "" : context.getString(R.string.main_list_sorted_value_a11y, value);
    }

    @NonNull
    static CharSequence metadataLine(@NonNull Context context, @NonNull ApplicationItem item,
                                     @NonNull CharSequence appType, @ColorInt int highlightColor) {
        String version = TextUtils.isEmpty(item.versionTag) ? "-" : item.versionTag;
        String sdk = item.sdkString != null ? item.sdkString : "-";
        return withUid(context, item, version + SEPARATOR + appType + SEPARATOR + sdk, highlightColor);
    }

    @NonNull
    private static CharSequence withUid(@NonNull Context context, @NonNull ApplicationItem item,
                                        @NonNull String details, @ColorInt int highlightColor) {
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

    @NonNull
    private static String date(@NonNull Context context, @StringRes int format, long millis) {
        return millis <= 0 ? "" : context.getString(format, DateUtils.formatDateTime(context, millis));
    }

    @NonNull
    private static String size(@NonNull Context context, @StringRes int format, long bytes) {
        return bytes <= 0 ? "" : context.getString(format, Formatter.formatShortFileSize(context, bytes));
    }

    private static long orZero(@Nullable Long value) {
        return value != null ? value : 0L;
    }

    private static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
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
