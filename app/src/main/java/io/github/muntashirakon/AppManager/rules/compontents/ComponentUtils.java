// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.DONT_KILL_APP;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
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
    private static final String TAG = ComponentUtils.class.getSimpleName();

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
        ComponentBlockingBatch.Result result = blockTrackingComponents(
                Collections.singletonList(pair), intensity);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Could not block tracker components for " + pair,
                    result.getFailures().get(0).getError());
        }
    }

    @WorkerThread
    @NonNull
    public static List<UserPackagePair> blockTrackingComponents(@NonNull Collection<UserPackagePair> userPackagePairs) {
        return toFailedPairs(blockTrackingComponents(userPackagePairs, TrackerBlockingIntensity.STRICT));
    }

    @WorkerThread
    @NonNull
    public static ComponentBlockingBatch.Result blockTrackingComponents(
            @NonNull Collection<UserPackagePair> userPackagePairs,
            @NonNull TrackerBlockingIntensity intensity) {
        if (intensity == TrackerBlockingIntensity.DETECT_ONLY) {
            return ComponentBlockingBatch.execute(Collections.emptyList(), pair ->
                    Collections.emptyMap(), (pair, components) -> { });
        }
        return ComponentBlockingBatch.execute(userPackagePairs,
                pair -> {
                    Map<String, RuleType> detected = getTrackerComponentsForPackage(
                            pair.getPackageName(), pair.getUserId());
                    if (intensity == TrackerBlockingIntensity.STRICT) {
                        return detected;
                    }
                    Map<String, RuleType> filtered = new HashMap<>();
                    for (Map.Entry<String, RuleType> entry : detected.entrySet()) {
                        TrackerCategory category = TrackerCategory.categorize(getTrackerLabel(entry.getKey()));
                        if (intensity.shouldBlock(category)) {
                            filtered.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return filtered;
                },
                (pair, components) -> {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(
                            pair.getPackageName(), pair.getUserId())) {
                        for (Map.Entry<String, RuleType> entry : components.entrySet()) {
                            cb.addComponent(entry.getKey(), entry.getValue());
                        }
                        cb.applyRules(true);
                    }
                });
    }

    public static void unblockTrackingComponents(@NonNull UserPackagePair pair) {
        ComponentBlockingBatch.Result result = unblockTrackingComponentsBatch(
                Collections.singletonList(pair));
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Could not unblock tracker components for " + pair,
                    result.getFailures().get(0).getError());
        }
    }

    @WorkerThread
    @NonNull
    public static List<UserPackagePair> unblockTrackingComponents(@NonNull Collection<UserPackagePair> userPackagePairs) {
        return toFailedPairs(unblockTrackingComponentsBatch(userPackagePairs));
    }

    @WorkerThread
    @NonNull
    public static ComponentBlockingBatch.Result unblockTrackingComponentsBatch(
            @NonNull Collection<UserPackagePair> userPackagePairs) {
        return ComponentBlockingBatch.execute(userPackagePairs,
                pair -> getTrackerComponentsForPackage(pair.getPackageName(), pair.getUserId()),
                (pair, components) -> {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(
                            pair.getPackageName(), pair.getUserId())) {
                        for (String componentName : components.keySet()) {
                            cb.removeComponent(componentName);
                        }
                        cb.applyRules(true);
                    }
                });
    }

    public static void blockFilteredComponents(@NonNull UserPackagePair pair, String[] signatures) {
        ComponentBlockingBatch.Result result = blockFilteredComponents(Collections.singletonList(pair), signatures);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Could not block components for " + pair,
                    result.getFailures().get(0).getError());
        }
    }

    @WorkerThread
    @NonNull
    public static ComponentBlockingBatch.Result blockFilteredComponents(
            @NonNull Collection<UserPackagePair> userPackagePairs, String[] signatures) {
        return ComponentBlockingBatch.execute(userPackagePairs,
                pair -> PackageUtils.getFilteredComponents(pair.getPackageName(), pair.getUserId(), signatures),
                (pair, components) -> {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(
                            pair.getPackageName(), pair.getUserId())) {
                        for (Map.Entry<String, RuleType> entry : components.entrySet()) {
                            cb.addComponent(entry.getKey(), entry.getValue());
                        }
                        cb.applyRules(true);
                    }
                });
    }

    public static void unblockFilteredComponents(@NonNull UserPackagePair pair, String[] signatures) {
        ComponentBlockingBatch.Result result = unblockFilteredComponents(
                Collections.singletonList(pair), signatures);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Could not unblock components for " + pair,
                    result.getFailures().get(0).getError());
        }
    }

    @WorkerThread
    @NonNull
    public static ComponentBlockingBatch.Result unblockFilteredComponents(
            @NonNull Collection<UserPackagePair> userPackagePairs, String[] signatures) {
        return ComponentBlockingBatch.execute(userPackagePairs,
                pair -> PackageUtils.getFilteredComponents(pair.getPackageName(), pair.getUserId(), signatures),
                (pair, components) -> {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(
                            pair.getPackageName(), pair.getUserId())) {
                        for (String componentName : components.keySet()) {
                            cb.removeComponent(componentName);
                        }
                        cb.applyRules(true);
                    }
                });
    }

    @NonNull
    private static List<UserPackagePair> toFailedPairs(@NonNull ComponentBlockingBatch.Result result) {
        List<UserPackagePair> failedPairs = new ArrayList<>();
        for (ComponentBlockingBatch.Failure failure : result.getFailures()) {
            Log.w(TAG, "Component batch failed for " + failure.getPair(), failure.getError());
            failedPairs.add(failure.getPair());
        }
        return failedPairs;
    }

    public static void storeRules(@NonNull OutputStream os, @NonNull List<RuleEntry> rules, boolean isExternal)
            throws IOException {
        for (RuleEntry entry : rules) {
            os.write((entry.flattenToString(isExternal) + "\n").getBytes(StandardCharsets.UTF_8));
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
        } catch (Exception ignored) {
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
    @NonNull
    public static List<ComponentRuleResetPlan> snapshotAllRules(@NonNull Context context,
                                                                 @NonNull int[] userIds) {
        if (userIds.length == 0) {
            return Collections.emptyList();
        }
        List<ComponentRuleResetPlan> plans = new ArrayList<>();
        for (String packageName : getAllPackagesWithComponentRuleFiles(context)) {
            try (ComponentsBlocker blocker = ComponentsBlocker.getInstance(packageName, userIds[0], true)) {
                ComponentRuleResetPlan plan = ComponentRuleResetPlan.fromRules(packageName, userIds,
                        new ArrayList<>(blocker.getAll()));
                if (plan.size() > 0) {
                    plans.add(plan);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Could not snapshot rules for package %s.", e, packageName);
            }
        }
        return plans;
    }

    @WorkerThread
    @NonNull
    public static ComponentRuleResetResult resetRules(@NonNull List<ComponentRuleResetPlan> plans,
                                                       @NonNull ComponentRuleResetRunner.CancellationChecker cancellationChecker,
                                                       @NonNull ComponentRuleResetRunner.ProgressListener progressListener) {
        Map<String, Boolean> ifwClearStatus = new HashMap<>();
        ComponentRuleResetResult result = ComponentRuleResetRunner.run(plans, cancellationChecker, target -> {
            ComponentRuleResetPlan.RuleSpec rule = target.getRule();
            try {
                boolean ifwCleared = true;
                if (rule.isIfw()) {
                    Boolean cachedStatus = ifwClearStatus.get(target.getPackageName());
                    if (cachedStatus == null) {
                        try (ComponentsBlocker blocker = ComponentsBlocker.getMutableInstance(
                                target.getPackageName(), target.getUserId())) {
                            cachedStatus = blocker.clearIntentFirewallRules();
                            blocker.setReadOnly();
                        }
                        ifwClearStatus.put(target.getPackageName(), cachedStatus);
                    }
                    ifwCleared = cachedStatus;
                }
                if (rule.isComponent()) {
                    PackageManagerCompat.setComponentEnabledSetting(
                            new ComponentName(target.getPackageName(), rule.getLabel()),
                            COMPONENT_ENABLED_STATE_DEFAULT, DONT_KILL_APP, target.getUserId());
                    if (!ifwCleared) {
                        return ComponentRuleResetResult.Outcome.failure(target,
                                "The Intent Firewall rule file could not be cleared.");
                    }
                } else if (rule.isAppOp()) {
                    int uid = PackageUtils.getAppUid(new UserPackagePair(target.getPackageName(),
                            target.getUserId()));
                    new AppOpsManagerCompat().setMode(rule.getAppOp(), uid, target.getPackageName(),
                            AppOpsManager.MODE_DEFAULT);
                } else if (rule.isPermission()) {
                    PermissionCompat.grantPermission(target.getPackageName(), rule.getPermissionName(),
                            target.getUserId());
                }
                return ComponentRuleResetResult.Outcome.success(target);
            } catch (Throwable e) {
                Log.e(TAG, "Could not reset %s for package %s and user %d.", e, target.getLabel(),
                        target.getPackageName(), target.getUserId());
                String message = e.getMessage();
                return ComponentRuleResetResult.Outcome.failure(target,
                        e.getClass().getSimpleName() + (message != null ? ": " + message : ""));
            }
        }, progressListener);
        persistRetryRules(plans, result.getSuccessfulTargetIds());
        return result;
    }

    @NonNull
    public static List<ComponentRuleResetPlan> getRetryPlans(@NonNull List<ComponentRuleResetPlan> plans,
                                                              @NonNull ComponentRuleResetResult result) {
        Set<String> retryTargetIds = new LinkedHashSet<>();
        Set<String> successfulTargetIds = result.getSuccessfulTargetIds();
        for (ComponentRuleResetPlan plan : plans) {
            for (ComponentRuleResetPlan.Target target : plan.getTargets()) {
                if (!successfulTargetIds.contains(target.getId())) {
                    retryTargetIds.add(target.getId());
                }
            }
        }
        List<ComponentRuleResetPlan> retryPlans = new ArrayList<>();
        for (ComponentRuleResetPlan plan : plans) {
            ComponentRuleResetPlan retryPlan = plan.retainTargets(retryTargetIds);
            if (retryPlan.size() > 0) {
                retryPlans.add(retryPlan);
            }
        }
        return retryPlans;
    }

    @WorkerThread
    private static void persistRetryRules(@NonNull List<ComponentRuleResetPlan> plans,
                                          @NonNull Set<String> successfulTargetIds) {
        for (ComponentRuleResetPlan plan : plans) {
            Set<ComponentRuleResetPlan.RuleSpec> retryRules = new LinkedHashSet<>();
            for (ComponentRuleResetPlan.Target target : plan.getTargets()) {
                if (!successfulTargetIds.contains(target.getId())) {
                    retryRules.add(target.getRule());
                }
            }
            int userId = plan.getTargets().isEmpty() ? 0 : plan.getTargets().get(0).getUserId();
            try (ComponentsBlocker blocker = ComponentsBlocker.getMutableInstance(plan.getPackageName(), userId)) {
                for (RuleEntry entry : new ArrayList<>(blocker.getAll())) {
                    if (entry instanceof ComponentRule || entry instanceof AppOpRule
                            || entry instanceof PermissionRule) {
                        blocker.removeEntry(entry);
                    }
                }
                for (ComponentRuleResetPlan.RuleSpec retryRule : retryRules) {
                    retryRule.restoreTo(blocker);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Could not persist the retry ledger for package %s.", e,
                        plan.getPackageName());
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
                Log.w(TAG, e);
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
                            // bubble to the outer catch and truncate the rest of the file. A
                            // component-filter with no recognized enclosing tag leaves componentType
                            // null; storing that would NPE later (ComponentRule.flattenToString).
                            if (cn != null && componentType != null && packageName.equals(cn.getPackageName())) {
                                rules.put(cn.getClassName(), componentType);
                            }
                        }
                }
                event = parser.nextTag();
            }
        } catch (Exception e) {
            // A malformed/truncated IFW file: keep whatever parsed so far, but log it — silently
            // under-reporting blocked components is a security-relevant downgrade.
            Log.w(TAG, "Failed to fully parse IFW rules for " + packageName, e);
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
