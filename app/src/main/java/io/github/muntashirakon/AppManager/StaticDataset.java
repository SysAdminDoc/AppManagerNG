// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.debloat.DebloatDefinitionsUpdater;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.debloat.SuggestionObject;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.algo.AhoCorasick;
import io.github.muntashirakon.AppManager.logs.Log;

public class StaticDataset {
    private static final String TAG = StaticDataset.class.getSimpleName();

    @Nullable
    private static AhoCorasick sAhoCorasickTrackerCache;
    private static String[] sTrackerNames;
    private static List<DebloatObject> sDebloatObjects;
    // Guarded by StaticDataset.class
    private static final Map<String, Drawable> sDebloatIcons = new HashMap<>();

    public static final String ARMEABI_V7A = "armeabi_v7a";
    public static final String ARM64_V8A = "arm64_v8a";
    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";

    public static Map<String, String> ALL_ABIS = new HashMap<>();

    static {
        ALL_ABIS.put(ARMEABI_V7A, VMRuntime.ABI_ARMEABI_V7A);
        ALL_ABIS.put(ARM64_V8A, VMRuntime.ABI_ARM64_V8A);
        ALL_ABIS.put(X86, VMRuntime.ABI_X86);
        ALL_ABIS.put(X86_64, VMRuntime.ABI_X86_64);
    }

    public static final String LDPI = "ldpi";
    public static final String MDPI = "mdpi";
    public static final String TVDPI = "tvdpi";
    public static final String HDPI = "hdpi";
    public static final String XHDPI = "xhdpi";
    public static final String XXHDPI = "xxhdpi";
    public static final String XXXHDPI = "xxxhdpi";

    public static final ArrayMap<String, Integer> DENSITY_NAME_TO_DENSITY = new ArrayMap<String, Integer>(8) {
        {
            put(LDPI, DisplayMetrics.DENSITY_LOW);
            put(MDPI, DisplayMetrics.DENSITY_MEDIUM);
            put(TVDPI, DisplayMetrics.DENSITY_TV);
            put(HDPI, DisplayMetrics.DENSITY_HIGH);
            put(XHDPI, DisplayMetrics.DENSITY_XHIGH);
            put(XXHDPI, DisplayMetrics.DENSITY_XXHIGH);
            put(XXXHDPI, DisplayMetrics.DENSITY_XXXHIGH);
        }
    };
    public static final int DEVICE_DENSITY;

    static {
        DEVICE_DENSITY = Resources.getSystem().getDisplayMetrics().densityDpi;
    }

    public static final Map<String, Integer> LOCALE_RANKING = new HashMap<>();

    static {
        LocaleListCompat localeList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
        for (int i = 0; i < localeList.size(); i++) {
            LOCALE_RANKING.put(Objects.requireNonNull(localeList.get(i)).getLanguage(), i);
        }
    }

    public static String[] getTrackerCodeSignatures() {
        return ContextUtils.getContext().getResources().getStringArray(R.array.tracker_signatures);
    }

    @NonNull
    public static String getTrackerDatabaseVersion() {
        return ContextUtils.getContext().getString(R.string.tracker_database_version);
    }

    public static int getTrackerDatabaseSignatureCount() {
        return ContextUtils.getContext().getResources().getStringArray(R.array.tracker_signatures).length;
    }

    public static synchronized AhoCorasick getSearchableTrackerSignatures() {
        // Synchronized: AhoCorasick owns native memory released via cleanup(); a race
        // between two first-callers (or a first-caller and cleanup()) could leak the
        // unreferenced instance or close the one another thread is still scanning.
        if (sAhoCorasickTrackerCache == null) {
            sAhoCorasickTrackerCache = new AhoCorasick(getTrackerCodeSignatures());
        }
        return sAhoCorasickTrackerCache;
    }

    public static synchronized void cleanup() {
        if (sAhoCorasickTrackerCache != null) {
            sAhoCorasickTrackerCache.close();
            sAhoCorasickTrackerCache = null;
        }
    }

    public static synchronized String[] getTrackerNames() {
        if (sTrackerNames == null) {
            sTrackerNames = ContextUtils.getContext().getResources().getStringArray(R.array.tracker_names);
        }
        return sTrackerNames;
    }

    @WorkerThread
    public static synchronized List<DebloatObject> getDebloatObjects() {
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(ContextUtils.getContext(), new Gson());
        }
        return sDebloatObjects;
    }

    public static synchronized void clearDebloatObjectsCache() {
        sDebloatObjects = null;
        sDebloatIcons.clear();
    }

    @VisibleForTesting
    public static synchronized void setDebloatObjectsForTesting(@NonNull List<DebloatObject> definitions) {
        sDebloatObjects = definitions;
        sDebloatIcons.clear();
    }

    /**
     * Fills in a new copy of every entry on each call. A list or dialog can keep showing the
     * rows an earlier call returned, so they're never changed afterwards. The entries from
     * {@link #getDebloatObjects()} aren't filled in at all.
     */
    @WorkerThread
    @NonNull
    public static synchronized List<DebloatObject> getDebloatObjectsWithInstalledInfo(@NonNull Context context) {
        AppDb appDb = new AppDb();
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(context, new Gson());
        }
        List<DebloatObject> filled = new ArrayList<>(sDebloatObjects.size());
        for (DebloatObject definition : sDebloatObjects) {
            filled.add(fillCopy(context, definition, appDb));
        }
        return filled;
    }

    /**
     * One package's entry, filled in on a new copy like
     * {@link #getDebloatObjectsWithInstalledInfo(Context)} does.
     *
     * @return {@code null} if the list has no entry for the package
     */
    @WorkerThread
    @Nullable
    public static synchronized DebloatObject getDebloatObjectWithInstalledInfo(@NonNull Context context,
                                                                               @NonNull String packageName) {
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(context, new Gson());
        }
        for (DebloatObject definition : sDebloatObjects) {
            if (packageName.equals(definition.packageName)) {
                return fillCopy(context, definition, new AppDb());
            }
        }
        return null;
    }

    // Called with the StaticDataset.class lock held
    @WorkerThread
    @NonNull
    private static DebloatObject fillCopy(@NonNull Context context, @NonNull DebloatObject definition,
                                          @NonNull AppDb appDb) {
        DebloatObject debloatObject = definition.copyDefinition(sDebloatIcons.get(definition.packageName));
        debloatObject.fillInstallInfo(context, appDb);
        Drawable icon = debloatObject.getIcon();
        if (icon != null) {
            sDebloatIcons.put(definition.packageName, icon);
        }
        return debloatObject;
    }

    @NonNull
    @WorkerThread
    private static List<DebloatObject> loadDebloatObjects(@NonNull Context context, @NonNull Gson gson) {
        HashMap<String, List<SuggestionObject>> idSuggestionObjectsMap = loadSuggestions(context, gson);
        String jsonContent = getDefinitionJson(context, "debloat.json");
        try {
            List<DebloatObject> debloatObjects = Arrays.asList(gson.fromJson(jsonContent, DebloatObject[].class));
            int id = 0;
            for (DebloatObject debloatObject : debloatObjects) {
                List<SuggestionObject> suggestionObjects = idSuggestionObjectsMap.get(debloatObject.getSuggestionId());
                debloatObject.setSuggestions(suggestionObjects);
                debloatObject.setId(id++);
            }
            return debloatObjects;
        } catch (Exception e) {
            Log.w(TAG, e);
            return Collections.emptyList();
        }
    }

    @NonNull
    @WorkerThread
    private static HashMap<String, List<SuggestionObject>> loadSuggestions(@NonNull Context context, @NonNull Gson gson) {
        String jsonContent = getDefinitionJson(context, "suggestions.json");
        HashMap<String, List<SuggestionObject>> idSuggestionObjectsMap = new HashMap<>();
        try {
            SuggestionObject[] suggestionObjects = gson.fromJson(jsonContent, SuggestionObject[].class);
            if (suggestionObjects != null) {
                for (SuggestionObject suggestionObject : suggestionObjects) {
                    List<SuggestionObject> objects = idSuggestionObjectsMap.get(suggestionObject.suggestionId);
                    if (objects == null) {
                        objects = new ArrayList<>();
                        idSuggestionObjectsMap.put(suggestionObject.suggestionId, objects);
                    }
                    objects.add(suggestionObject);
                }
            }
        } catch (Exception th) {
            Log.w(TAG, th);
        }
        return idSuggestionObjectsMap;
    }

    @NonNull
    @WorkerThread
    private static String getDefinitionJson(@NonNull Context context, @NonNull String fileName) {
        String cachedDefinition = DebloatDefinitionsUpdater.readCachedDefinition(context, fileName);
        if (cachedDefinition != null) {
            return cachedDefinition;
        }
        return FileUtils.getContentFromAssets(context, fileName);
    }
}
