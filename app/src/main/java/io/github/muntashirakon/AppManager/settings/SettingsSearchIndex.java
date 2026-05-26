// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.annotation.WorkerThread;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;

/**
 * In-memory index of every searchable {@code app:title} / {@code app:summary}
 * declared in {@code res/xml/preferences_*.xml}, built by walking the static
 * XML at runtime (no PreferenceManager inflation, no fragment instantiation).
 *
 * <p>The index is consumed by {@link SettingsActivity}'s toolbar SearchView.
 * On result tap, the activity is restarted with a deep link
 * {@code am://settings/<parentKey>/<targetKey>} so the user lands on the
 * matching preference row through the existing key-stack navigation.</p>
 *
 * <p>Sub-screens that load XML via {@link io.github.muntashirakon.AppManager.settings.PreferenceFragment#setPreferencesFromResource}
 * are enumerated here by their parent-preference key in {@code preferences_main.xml}.
 * Dynamic / runtime-built preferences (e.g. {@link ComponentRulesPreferences},
 * {@link ModeOfOpsPreference}) are not indexed because their rows do not exist
 * until the fragment is created.</p>
 */
public final class SettingsSearchIndex {
    private static final String TAG = SettingsSearchIndex.class.getSimpleName();

    /** A row that can appear in a search-result list. */
    public static final class Entry {
        @NonNull public final CharSequence title;
        @Nullable public final CharSequence summary;
        /** The {@code app:key} of the matching preference (used for scrollToPreference). */
        @Nullable public final String targetKey;
        /** The parent preference key under {@code preferences_main.xml} that owns this row. */
        @NonNull public final String parentKey;
        /** Human-readable parent label (e.g. "Appearance"), used as breadcrumb. */
        @NonNull public final CharSequence parentLabel;

        @VisibleForTesting
        public Entry(@NonNull CharSequence title, @Nullable CharSequence summary,
                     @Nullable String targetKey, @NonNull String parentKey,
                     @NonNull CharSequence parentLabel) {
            this.title = title;
            this.summary = summary;
            this.targetKey = targetKey;
            this.parentKey = parentKey;
            this.parentLabel = parentLabel;
        }
    }

    private static final class Source {
        @NonNull final String parentKey;
        @XmlRes final int xmlRes;
        @StringRes final int parentLabelRes;

        Source(@NonNull String parentKey, @XmlRes int xmlRes, @StringRes int parentLabelRes) {
            this.parentKey = parentKey;
            this.xmlRes = xmlRes;
            this.parentLabelRes = parentLabelRes;
        }
    }

    // Mirrors the android:fragment entries in preferences_main.xml that load a static
    // preferences_*.xml file. ComponentRulesPreferences, ModeOfOpsPreference, and
    // ChangeLanguageFragment build their rows at runtime, so they are intentionally
    // omitted.
    private static final Source[] SOURCES = new Source[] {
            new Source("appearance_prefs", R.xml.preferences_appearance, R.string.pref_cat_appearance),
            new Source("privacy_prefs", R.xml.preferences_privacy, R.string.pref_privacy),
            new Source("privilege_health", R.xml.preferences_privilege_health, R.string.privilege_health_title),
            new Source("apk_signing_prefs", R.xml.preferences_signature, R.string.apk_signing),
            new Source("installer", R.xml.preferences_installer, R.string.installer),
            new Source("backup_restore_prefs", R.xml.preferences_backup_restore, R.string.backup_restore),
            new Source("vt", R.xml.preferences_virus_total, R.string.virus_total),
            new Source("log_viewer_prefs", R.xml.preferences_log_viewer, R.string.log_viewer),
            new Source("files_prefs", R.xml.preferences_file_manager, R.string.files),
            new Source("rules_prefs", R.xml.preferences_rules, R.string.rules),
            new Source("advanced_prefs", R.xml.preferences_advanced, R.string.pref_cat_advanced),
            new Source("about", R.xml.preferences_about, R.string.about),
            new Source("troubleshooting_prefs", R.xml.preferences_troubleshooting, R.string.troubleshooting),
            // Glossary topics: each row is its own searchable explainer. The parent
            // is the "Glossary & how-to" preference inside Settings -> About.
            new Source("glossary", R.xml.preferences_glossary, R.string.pref_glossary_title),
    };

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";

    @Nullable
    private static volatile SettingsSearchIndex sInstance;

    @NonNull
    private final List<Entry> mEntries;

    private SettingsSearchIndex(@NonNull List<Entry> entries) {
        mEntries = Collections.unmodifiableList(entries);
    }

    /**
     * Lazily build (or return) the singleton index for {@code context}. The
     * harvest walks ~15 small XML files and resolves their string references,
     * which is cheap enough to run on a background thread on first use.
     */
    @AnyThread
    @NonNull
    public static SettingsSearchIndex get(@NonNull Context context) {
        SettingsSearchIndex local = sInstance;
        if (local != null) {
            return local;
        }
        synchronized (SettingsSearchIndex.class) {
            if (sInstance == null) {
                sInstance = new SettingsSearchIndex(buildEntries(context.getApplicationContext()));
            }
            return sInstance;
        }
    }

    /** Clear the cache. Call after a locale change so titles re-resolve. */
    public static void invalidate() {
        synchronized (SettingsSearchIndex.class) {
            sInstance = null;
        }
    }

    @NonNull
    public List<Entry> all() {
        return mEntries;
    }

    /**
     * Case-insensitive substring match against title, summary, or parent
     * label. Empty or whitespace queries return an empty list. Caller is
     * responsible for deciding the result-limit; this method returns all
     * matches.
     *
     * <p>Parent-label matching is what lets a user type a section name
     * ("About", "Backup", "Privileges") and surface every row inside that
     * section, instead of returning empty just because no individual
     * preference title repeated the section name.
     */
    @AnyThread
    @NonNull
    public List<Entry> search(@Nullable CharSequence query) {
        if (query == null) return Collections.emptyList();
        String needle = query.toString().trim().toLowerCase(Locale.getDefault());
        if (needle.isEmpty()) return Collections.emptyList();
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : mEntries) {
            if (matches(entry.title, needle)
                    || matches(entry.summary, needle)
                    || matches(entry.parentLabel, needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private static boolean matches(@Nullable CharSequence haystack, @NonNull String needle) {
        if (haystack == null) return false;
        return haystack.toString().toLowerCase(Locale.getDefault()).contains(needle);
    }

    @WorkerThread
    @NonNull
    private static List<Entry> buildEntries(@NonNull Context appContext) {
        Resources res = appContext.getResources();
        List<Entry> entries = new ArrayList<>();
        for (Source source : SOURCES) {
            String parentLabel = appContext.getString(source.parentLabelRes);
            try (XmlResourceParser parser = res.getXml(source.xmlRes)) {
                harvest(parser, appContext, source.parentKey, parentLabel, entries);
            } catch (XmlPullParserException | IOException e) {
                Log.w(TAG, "Failed to harvest " + appContext.getResources().getResourceEntryName(source.xmlRes), e);
            }
        }
        return entries;
    }

    @VisibleForTesting
    static void harvest(@NonNull XmlPullParser parser, @NonNull Context context,
                         @NonNull String parentKey, @NonNull CharSequence parentLabel,
                         @NonNull List<Entry> out)
            throws XmlPullParserException, IOException {
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if (tag == null || "PreferenceScreen".equals(tag) || "PreferenceCategory".equals(tag)) {
                    event = parser.next();
                    continue;
                }
                CharSequence title = readStringAttr(parser, context, "title");
                if (title == null || title.length() == 0) {
                    event = parser.next();
                    continue;
                }
                CharSequence summary = readStringAttr(parser, context, "summary");
                String key = readRawAttr(parser, "key");
                out.add(new Entry(title, summary, key, parentKey, parentLabel));
            }
            event = parser.next();
        }
    }

    @Nullable
    private static CharSequence readStringAttr(@NonNull XmlPullParser parser, @NonNull Context context,
                                                @NonNull String attribute) {
        int resId = getResourceAttr(parser, attribute);
        if (resId != 0) {
            try {
                return context.getText(resId);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
        // Inline string fallback (rare in this project but allowed by AndroidX preference XML)
        return readRawAttr(parser, attribute);
    }

    private static int getResourceAttr(@NonNull XmlPullParser parser, @NonNull String attribute) {
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            if (!attribute.equals(parser.getAttributeName(i))) continue;
            String ns = parser.getAttributeNamespace(i);
            if (!APP_NS.equals(ns) && !ANDROID_NS.equals(ns)) continue;
            String raw = parser.getAttributeValue(i);
            if (raw != null && raw.startsWith("@") && raw.length() > 1) {
                try {
                    return Integer.parseInt(raw.substring(1));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    @Nullable
    private static String readRawAttr(@NonNull XmlPullParser parser, @NonNull String attribute) {
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            if (!attribute.equals(parser.getAttributeName(i))) continue;
            String ns = parser.getAttributeNamespace(i);
            if (!APP_NS.equals(ns) && !ANDROID_NS.equals(ns) && ns != null && !ns.isEmpty()) continue;
            String raw = parser.getAttributeValue(i);
            if (raw != null && raw.startsWith("@")) {
                // Resource reference; the typed reader handles it.
                return null;
            }
            return raw;
        }
        return null;
    }
}
