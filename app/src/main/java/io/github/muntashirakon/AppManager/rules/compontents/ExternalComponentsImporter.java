// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Import components from external apps like Blocker, Watt and MyAndroidTools.
 */
public class ExternalComponentsImporter {
    private static final String MY_ANDROID_TOOLS_IFW_EXTENSION = ".ifw";
    private static final String MY_ANDROID_TOOLS_IFW_ENTRY_SUFFIX = "$.xml";

    public static void setModeToFilteredAppOps(@NonNull AppOpsManagerCompat appOpsManager,
                                               @NonNull UserPackagePair pair,
                                               int[] appOps,
                                               @AppOpsManagerCompat.Mode int mode) throws RemoteException {
        Collection<Integer> appOpList;
        appOpList = PackageUtils.getFilteredAppOps(pair.getPackageName(), pair.getUserId(), appOps, mode);
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
            for (int appOp : appOpList) {
                appOpsManager.setMode(appOp, PackageUtils.getAppUid(pair), pair.getPackageName(), mode);
                cb.setAppOp(appOp, mode);
            }
            cb.applyRules(true);
        }
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromExistingBlockList(@NonNull List<String> packageNames, int userHandle) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RuleType> components;
        Path rulesPath = Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH);
        for (String packageName : packageNames) {
            components = PackageUtils.getUserDisabledComponentsForPackage(packageName, userHandle);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                for (String componentName : components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                // Remove IFW blocking rules if exists
                Path[] rulesFiles = rulesPath.listFiles((dir, name) -> name.startsWith(packageName) && name.endsWith("xml"));
                for (Path rulesFile : rulesFiles) {
                    rulesFile.delete();
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromBlocker(@NonNull List<Uri> uriList, int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            String filename = Paths.get(uri).getName();
            try {
                for (int userHandle : userHandles) {
                    applyFromBlocker(uri, userHandle);
                }
            } catch (Exception e) {
                failedFiles.add(filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromWatt(@NonNull List<Uri> uriList, int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            Path path = Paths.get(uri);
            String filename = path.getName();
            try {
                for (int userHandle : userHandles) {
                    applyFromWatt(Paths.trimPathExtension(filename), path, userHandle);
                }
            } catch (IOException e) {
                failedFiles.add(filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromMyAndroidTools(@NonNull List<Uri> uriList, int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            Path path = Paths.get(uri);
            String filename = path.getName();
            try {
                for (int userHandle : userHandles) {
                    applyFromMyAndroidTools(path, filename, userHandle);
                }
            } catch (Exception e) {
                failedFiles.add(filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    /**
     * Watt only supports IFW, so copy them directly
     */
    @WorkerThread
    private static void applyFromWatt(String packageName, Path path, int userHandle)
            throws IOException {
        try (InputStream rulesStream = path.openInputStream()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                HashMap<String, RuleType> components = ComponentUtils.readIFWRules(rulesStream,
                        packageName);
                for (String componentName : components.keySet()) {
                    // Overwrite rules if exists
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            }
        }
    }

    @WorkerThread
    private static void applyFromMyAndroidTools(@NonNull Path path, @NonNull String filename, int userHandle)
            throws Exception {
        HashMap<String, HashMap<String, RuleType>> packageComponents;
        if (filename.toLowerCase(Locale.ROOT).endsWith(MY_ANDROID_TOOLS_IFW_EXTENSION)) {
            try (InputStream rulesStream = path.openInputStream()) {
                packageComponents = parseMyAndroidToolsIfwRules(rulesStream);
            }
        } else {
            packageComponents = parseMyAndroidToolsPlainRules(path.getContentAsString());
        }
        applyParsedComponents(packageComponents, userHandle);
    }

    @VisibleForTesting
    @NonNull
    static HashMap<String, HashMap<String, RuleType>> parseMyAndroidToolsPlainRules(@NonNull String rules)
            throws IOException {
        HashMap<String, HashMap<String, RuleType>> packageComponents = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(rules))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmedLine.split("\\s+", 2);
                String componentSpec = parts[0];
                int separator = componentSpec.indexOf('/');
                if (separator <= 0 || separator == componentSpec.length() - 1) {
                    continue;
                }
                String packageName = componentSpec.substring(0, separator);
                String componentName = componentSpec.substring(separator + 1);
                if (componentName.startsWith(".")) {
                    componentName = packageName + componentName;
                } else if (!componentName.contains(".")) {
                    componentName = packageName + "." + componentName;
                }
                RuleType type = parts.length > 1 ? parseMyAndroidToolsType(parts[1]) : null;
                addParsedComponent(packageComponents, packageName, componentName, type);
            }
        }
        return packageComponents;
    }

    @VisibleForTesting
    @NonNull
    static HashMap<String, HashMap<String, RuleType>> parseMyAndroidToolsIfwRules(@NonNull InputStream rulesStream)
            throws IOException {
        HashMap<String, HashMap<String, RuleType>> packageComponents = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(rulesStream))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String filename = getFilenameFromZipEntry(entry);
                if (!filename.endsWith(MY_ANDROID_TOOLS_IFW_ENTRY_SUFFIX)) {
                    continue;
                }
                String packageName = filename.substring(0,
                        filename.length() - MY_ANDROID_TOOLS_IFW_ENTRY_SUFFIX.length());
                HashMap<String, RuleType> ifwRules = ComponentUtils.readIFWRules(zipInputStream, packageName);
                for (Map.Entry<String, RuleType> rule : ifwRules.entrySet()) {
                    addParsedComponent(packageComponents, packageName, rule.getKey(), rule.getValue());
                }
                zipInputStream.closeEntry();
            }
        }
        return packageComponents;
    }

    @NonNull
    private static String getFilenameFromZipEntry(@NonNull ZipEntry entry) {
        String name = entry.getName();
        int index = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return index >= 0 ? name.substring(index + 1) : name;
    }

    @Nullable
    private static RuleType parseMyAndroidToolsType(@NonNull String typeSpec) {
        String type = typeSpec.trim().toLowerCase(Locale.ROOT);
        if (type.length() < 2) {
            return null;
        }
        switch (type.charAt(1)) {
            case 'a':
                return RuleType.ACTIVITY;
            case 'p':
                return RuleType.PROVIDER;
            case 'r':
                return RuleType.RECEIVER;
            case 's':
                return RuleType.SERVICE;
            default:
                return null;
        }
    }

    private static void addParsedComponent(@NonNull HashMap<String, HashMap<String, RuleType>> packageComponents,
                                           @NonNull String packageName, @NonNull String componentName,
                                           @Nullable RuleType type) throws IOException {
        HashMap<String, RuleType> components = packageComponents.get(packageName);
        if (components == null) {
            components = new HashMap<>();
            packageComponents.put(packageName, components);
        }
        if (!components.containsKey(componentName) || components.get(componentName) == null) {
            components.put(componentName, type);
            return;
        }
        RuleType existingType = components.get(componentName);
        if (type != null && existingType != type) {
            throw new IOException("Conflicting MyAndroidTools component type for " + packageName + "/" + componentName);
        }
    }

    private static void applyParsedComponents(@NonNull HashMap<String, HashMap<String, RuleType>> packageComponents,
                                              int userHandle) throws Exception {
        HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
        List<String> uninstalledApps = new ArrayList<>();
        for (String packageName : packageComponents.keySet()) {
            if (uninstalledApps.contains(packageName)) continue;
            HashMap<String, RuleType> components = packageComponents.get(packageName);
            if (components == null || components.isEmpty()) continue;
            HashMap<String, RuleType> resolvedComponents = new HashMap<>();
            for (Map.Entry<String, RuleType> component : components.entrySet()) {
                RuleType type = component.getValue();
                if (type == null) {
                    if (!packageInfoList.containsKey(packageName)) {
                        try {
                            packageInfoList.put(packageName, PackageManagerCompat.getPackageInfo(packageName,
                                    PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                            | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                            | MATCH_DISABLED_COMPONENTS
                                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                                    userHandle));
                        } catch (Exception e) {
                            uninstalledApps.add(packageName);
                            break;
                        }
                    }
                    type = getType(component.getKey(), packageInfoList.get(packageName));
                }
                if (type != null) {
                    resolvedComponents.put(component.getKey(), type);
                }
            }
            if (resolvedComponents.isEmpty()) continue;
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                for (Map.Entry<String, RuleType> component : resolvedComponents.entrySet()) {
                    cb.addComponent(component.getKey(), component.getValue());
                }
                cb.applyRules(true);
                if (!cb.isRulesApplied()) {
                    throw new Exception("Rules not applied for package " + packageName);
                }
            }
        }
    }

    /**
     * Apply from blocker
     *
     * @param uri File URI
     */
    @WorkerThread
    @SuppressLint("WrongConstant")
    private static void applyFromBlocker(Uri uri, int userHandle) throws Exception {
        String jsonString = Paths.get(uri).getContentAsString();
        HashMap<String, HashMap<String, RuleType>> packageComponents = new HashMap<>();
        HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray components = jsonObject.getJSONArray("components");
        List<String> uninstalledApps = new ArrayList<>();
        for (int i = 0; i < components.length(); ++i) {
            JSONObject component = (JSONObject) components.get(i);
            String packageName = component.getString("packageName");
            if (uninstalledApps.contains(packageName)) continue;
            if (!packageInfoList.containsKey(packageName)) {
                try {
                    packageInfoList.put(packageName, PackageManagerCompat.getPackageInfo(packageName,
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                    | MATCH_DISABLED_COMPONENTS
                                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle));
                } catch (Exception e) {
                    // App not installed
                    uninstalledApps.add(packageName);
                    continue;
                }
            }
            String componentName = component.getString("name");
            if (!packageComponents.containsKey(packageName)) {
                packageComponents.put(packageName, new HashMap<>());
            }
            // Fetch package components using PackageInfo since the type used in Blocker can be wrong
            //noinspection ConstantConditions
            packageComponents.get(packageName).put(componentName, getType(componentName,
                    packageInfoList.get(packageName)));
        }
        if (packageComponents.size() > 0) {
            for (String packageName : packageComponents.keySet()) {
                HashMap<String, RuleType> disabledComponents = packageComponents.get(packageName);
                //noinspection ConstantConditions
                if (disabledComponents.size() > 0) {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                        for (String component : disabledComponents.keySet()) {
                            cb.addComponent(component, disabledComponents.get(component));
                        }
                        cb.applyRules(true);
                        if (!cb.isRulesApplied())
                            throw new Exception("Rules not applied for package " + packageName);
                    }
                }
            }
        }
    }

    @Nullable
    private static RuleType getType(@NonNull String name, @NonNull PackageInfo packageInfo) {
        if (packageInfo.activities != null)
            for (ActivityInfo activityInfo : packageInfo.activities)
                if (activityInfo.name.equals(name)) return RuleType.ACTIVITY;
        if (packageInfo.providers != null)
            for (ProviderInfo providerInfo : packageInfo.providers)
                if (providerInfo.name.equals(name)) return RuleType.PROVIDER;
        if (packageInfo.receivers != null)
            for (ActivityInfo receiverInfo : packageInfo.receivers)
                if (receiverInfo.name.equals(name)) return RuleType.RECEIVER;
        if (packageInfo.services != null)
            for (ServiceInfo serviceInfo : packageInfo.services)
                if (serviceInfo.name.equals(name)) return RuleType.SERVICE;
        return null;
    }
}
