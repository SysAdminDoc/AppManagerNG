// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.RemoteException;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class ComponentUtils {
    /**
     * Escape the five XML predefined entities in a value destined for an
     * attribute or text node. Component and package names that reach the IFW
     * rule writer can originate from imported/restored rule files (a trust
     * boundary), so they must be escaped before being concatenated into the
     * privileged {@code /data/system/ifw/*.xml} rules — otherwise a crafted
     * name can inject extra XML elements or corrupt the document (fail-open).
     */
    @NonNull
    public static String escapeXml(@NonNull String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static boolean isTracker(String componentName) {
        return StaticDataset.getSearchableTrackerSignatures().search(componentName).length > 0;
    }

    /**
     * Resolve a human-readable tracker name for a component class, or {@code null} if the
     * component does not match any known tracker signature. When multiple signatures match
     * (rare; usually different SDK versions of the same vendor), the first match wins.
     */
    @Nullable
    public static String getTrackerLabel(String componentName) {
        int[] matches = StaticDataset.getSearchableTrackerSignatures().search(componentName);
        if (matches.length == 0) {
            return null;
        }
        String[] names = StaticDataset.getTrackerNames();
        int idx = matches[0];
        if (idx < 0 || idx >= names.length) {
            return null;
        }
        return names[idx];
    }

    public static int getTrackerComponentsCountForPackage(PackageInfo packageInfo) {
        HashMap<String, RuleType> components = PackageUtils.collectComponentClassNames(packageInfo);
        return (int) components.keySet().stream()
                .filter(ComponentUtils::isTracker)
                .count();
    }

    @NonNull
    public static Map<String, RuleType> getTrackerComponentsForPackage(PackageInfo packageInfo) {
        HashMap<String, RuleType> components = PackageUtils.collectComponentClassNames(packageInfo);
        return components.entrySet().stream()
                .filter(entry -> isTracker(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @NonNull
    public static Map<String, RuleType> getTrackerComponentsForPackage(String packageName, @UserIdInt int userHandle) {
        HashMap<String, RuleType> components = PackageUtils.collectComponentClassNames(packageName, userHandle);
        return components.entrySet().stream()
                .filter(entry -> isTracker(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static void blockTrackingComponents(@NonNull UserPackagePair pair) {
        blockTrackingComponents(pair, TrackerBlockingIntensity.STRICT);
    }

    /**
     * Block tracker components for {@code pair}, filtering by category according
     * to {@code intensity}. STRICT keeps the pre-NF-07 behaviour of blocking
     * every detected tracker; STANDARD blocks only categories likely to be
     * hostile (ad / analytics / identification); DETECT_ONLY blocks nothing.
     */
    public static void blockTrackingComponents(@NonNull UserPackagePair pair,
                                                @NonNull TrackerBlockingIntensity intensity) {
        if (intensity == TrackerBlockingIntensity.DETECT_ONLY) {
            return;
        }
        Map<String, RuleType> components = ComponentUtils.getTrackerComponentsForPackage(pair.getPackageName(), pair.getUserId());
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
            for (Map.Entry<String, RuleType> entry : components.entrySet()) {
                String componentName = entry.getKey();
                if (intensity != TrackerBlockingIntensity.STRICT) {
                    TrackerCategory category = TrackerCategory.categorize(getTrackerLabel(componentName));
                    if (!intensity.shouldBlock(category)) {
                        continue;
                    }
                }
                cb.addComponent(componentName, Objects.requireNonNull(entry.getValue()));
            }
            cb.applyRules(true);
        }
    }

    @WorkerThread
    @NonNull
    public static List<UserPackagePair> blockTrackingComponents(@NonNull Collection<UserPackagePair> userPackagePairs) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                blockTrackingComponents(pair);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(pair);
            }
        }
        return failedPkgList;
    }

    public static void unblockTrackingComponents(@NonNull UserPackagePair pair) {
        Map<String, RuleType> components = getTrackerComponentsForPackage(pair.getPackageName(), pair.getUserId());
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
            for (String componentName : components.keySet()) {
                cb.removeComponent(componentName);
            }
            cb.applyRules(true);
        }
    }

    @WorkerThread
    @NonNull
    public static List<UserPackagePair> unblockTrackingComponents(@NonNull Collection<UserPackagePair> userPackagePairs) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        for (UserPackagePair pair : userPackagePairs) {
            try {
                unblockTrackingComponents(pair);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(pair);
            }
        }
        return failedPkgList;
    }

    public static void blockFilteredComponents(@NonNull UserPackagePair pair, String[] signatures) {
        HashMap<String, RuleType> components = PackageUtils.getFilteredComponents(pair.getPackageName(), pair.getUserId(), signatures);
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
            for (String componentName : components.keySet()) {
                cb.addComponent(componentName, Objects.requireNonNull(components.get(componentName)));
            }
            cb.applyRules(true);
        }
    }

    public static void unblockFilteredComponents(@NonNull UserPackagePair pair, String[] signatures) {
        HashMap<String, RuleType> components = PackageUtils.getFilteredComponents(pair.getPackageName(), pair.getUserId(), signatures);
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
            for (String componentName : components.keySet()) {
                cb.removeComponent(componentName);
            }
            cb.applyRules(true);
        }
    }

    public static void storeRules(@NonNull OutputStream os, @NonNull List<RuleEntry> rules, boolean isExternal)
            throws IOException {
        for (RuleEntry entry : rules) {
            os.write((entry.flattenToString(isExternal) + "\n").getBytes());
        }
    }

    @NonNull
    public static List<String> getAllPackagesWithRules(@NonNull Context context) {
        List<String> packages = new ArrayList<>();
        Path confDir = RulesStorageManager.getConfDir(context);
        Path[] paths = confDir.listFiles((dir, name) -> name.endsWith(".tsv"));
        for (Path path : paths) {
            packages.add(Paths.trimPathExtension(path.getUri().getLastPathSegment()));
        }
        return packages;
    }

    @NonNull
    public static List<String> getAllPackagesWithComponentRuleFiles(@NonNull Context context) {
        Set<String> packages = new LinkedHashSet<>(getAllPackagesWithRules(context));
        packages.addAll(getAllPackagesWithIfwRuleFiles(Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH)));
        List<String> sortedPackages = new ArrayList<>(packages);
        Collections.sort(sortedPackages);
        return sortedPackages;
    }

    @VisibleForTesting
    @NonNull
    static List<String> getAllPackagesWithIfwRuleFiles(@NonNull Path ifwDir) {
        List<String> packages = new ArrayList<>();
        Path[] paths;
        try {
            paths = ifwDir.listFiles((dir, name) -> name.endsWith(".xml"));
        } catch (Throwable ignored) {
            return packages;
        }
        for (Path path : paths) {
            String fileName = path.getUri().getLastPathSegment();
            if (fileName != null) {
                packages.add(Paths.trimPathExtension(fileName));
            }
        }
        return packages;
    }

    @WorkerThread
    public static void removeAllRules(@NonNull String packageName, int userHandle) {
        int uid = PackageUtils.getAppUid(new UserPackagePair(packageName, userHandle));
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
            // Remove all blocking rules
            for (ComponentRule entry : cb.getAllComponents()) {
                cb.removeComponent(entry.name);
            }
            cb.applyRules(true);
            // Reset configured app ops
            AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
            try {
                appOpsManager.resetAllModes(userHandle, packageName);
                for (AppOpRule entry : cb.getAll(AppOpRule.class)) {
                    try {
                        appOpsManager.setMode(entry.getOp(), uid, packageName, AppOpsManager.MODE_DEFAULT);
                        cb.removeEntry(entry);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Grant configured permissions
            for (PermissionRule entry : cb.getAll(PermissionRule.class)) {
                try {
                    PermissionCompat.grantPermission(packageName, entry.name, userHandle);
                    cb.removeEntry(entry);
                } catch (RemoteException e) {
                    Log.e("ComponentUtils", "Cannot revoke permission %s for package %s", e, entry.name,
                            packageName);
                }
            }
        }
    }

    @NonNull
    public static HashMap<String, RuleType> getIFWRulesForPackage(@NonNull String packageName) {
        return getIFWRulesForPackage(packageName, Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH));
    }

    @VisibleForTesting
    @NonNull
    public static HashMap<String, RuleType> getIFWRulesForPackage(@NonNull String packageName, @NonNull Path path) {
        HashMap<String, RuleType> rules = new HashMap<>();
        Path[] files = path.listFiles((dir, name) -> {
            // For our case, name must start with package name to support apps like Watt, Blocker and MyAndroidTools,
            // and to prevent unwanted situation, such as when the contains unsupported tags such as intent-filter.
            return name.startsWith(packageName) && name.endsWith(".xml");
        });
        for (Path ifwRulesFile : files) {
            // Get file contents
            try (InputStream inputStream = ifwRulesFile.openInputStream()) {
                // Read rules
                rules.putAll(readIFWRules(inputStream, packageName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return rules;
    }

    public static final String TAG_RULES = "rules";

    public static final String TAG_ACTIVITY = "activity";
    public static final String TAG_BROADCAST = "broadcast";
    public static final String TAG_SERVICE = "service";

    @NonNull
    public static HashMap<String, RuleType> readIFWRules(@NonNull InputStream inputStream, @NonNull String packageName) {
        HashMap<String, RuleType> rules = new HashMap<>();
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, TAG_RULES);
            int event = parser.nextTag();
            RuleType componentType = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals(TAG_ACTIVITY) || name.equals(TAG_BROADCAST) || name.equals(TAG_SERVICE)) {
                            componentType = getComponentType(name);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("component-filter")) {
                            String fullKey = parser.getAttributeValue(null, "name");
                            ComponentName cn = ComponentName.unflattenFromString(fullKey);
                            // Skip malformed entries individually instead of letting the NPE
                            // bubble to the outer catch and truncate the rest of the file.
                            if (cn != null && packageName.equals(cn.getPackageName())) {
                                rules.put(cn.getClassName(), componentType);
                            }
                        }
                }
                event = parser.nextTag();
            }
        } catch (Throwable ignore) {
            // The file contains errors, simply ignore
        }
        return rules;
    }

    /**
     * Get component type from TAG_* constants
     *
     * @param componentTag Name of the constant: one of the TAG_*
     * @return One of the {@link RuleType}
     */
    @Nullable
    static RuleType getComponentType(@NonNull String componentTag) {
        switch (componentTag) {
            case TAG_ACTIVITY:
                return RuleType.ACTIVITY;
            case TAG_BROADCAST:
                return RuleType.RECEIVER;
            case TAG_SERVICE:
                return RuleType.SERVICE;
            default:
                return null;
        }
    }
}
