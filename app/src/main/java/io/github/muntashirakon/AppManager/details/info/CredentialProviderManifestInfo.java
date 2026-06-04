// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.parser.ManifestComponent;
import io.github.muntashirakon.AppManager.apk.parser.ManifestIntentFilter;
import io.github.muntashirakon.AppManager.apk.parser.ManifestParser;

/**
 * Target manifest Credential Manager provider declarations.
 *
 * <p>CredentialManager can only report provider-enabled state for the caller or
 * privileged callers. For inspected packages, this class limits itself to the
 * manifest service declarations that make an app eligible to be a provider.</p>
 */
public final class CredentialProviderManifestInfo {
    public static final int MIN_SDK_INT = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    @VisibleForTesting
    static final String SERVICE_INTERFACE = "android.service.credentials.CredentialProviderService";
    @VisibleForTesting
    static final String SYSTEM_SERVICE_INTERFACE = "android.service.credentials.system.CredentialProviderService";
    @VisibleForTesting
    static final String BIND_PERMISSION = "android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE";

    public final int sdkInt;
    @NonNull
    public final List<ServiceDeclaration> providerServices;

    private CredentialProviderManifestInfo(int sdkInt, @NonNull List<ServiceDeclaration> providerServices) {
        this.sdkInt = sdkInt;
        this.providerServices = Collections.unmodifiableList(providerServices);
    }

    @NonNull
    public static CredentialProviderManifestInfo from(@NonNull ApplicationInfo applicationInfo,
                                                      @Nullable ServiceInfo[] services) {
        if (Build.VERSION.SDK_INT < MIN_SDK_INT || applicationInfo.publicSourceDir == null
                || applicationInfo.publicSourceDir.isEmpty()) {
            return unsupported(Build.VERSION.SDK_INT);
        }
        try {
            List<ManifestComponent> components = new ManifestParser(ApkUtils.getManifestFromApk(
                    new File(applicationInfo.publicSourceDir))).parseComponents();
            return fromRaw(Build.VERSION.SDK_INT, components, services);
        } catch (Throwable ignore) {
            return fromRaw(Build.VERSION.SDK_INT, Collections.emptyList(), services);
        }
    }

    @NonNull
    static CredentialProviderManifestInfo unsupported(int sdkInt) {
        return new CredentialProviderManifestInfo(sdkInt, Collections.emptyList());
    }

    public boolean isSupported() {
        return sdkInt >= MIN_SDK_INT;
    }

    public boolean hasProviderServices() {
        return !providerServices.isEmpty();
    }

    public int getSystemProviderServiceCount() {
        int count = 0;
        for (ServiceDeclaration service : providerServices) {
            if (service.isSystemProvider) {
                ++count;
            }
        }
        return count;
    }

    @NonNull
    @VisibleForTesting
    static CredentialProviderManifestInfo fromRaw(int sdkInt, @Nullable List<ManifestComponent> components,
                                                  @Nullable ServiceInfo[] services) {
        if (sdkInt < MIN_SDK_INT || components == null || components.isEmpty()) {
            return new CredentialProviderManifestInfo(sdkInt, Collections.emptyList());
        }
        Map<String, String> servicePermissions = buildServicePermissionMap(services);
        List<ServiceRecord> records = new ArrayList<>();
        for (ManifestComponent component : components) {
            if (component == null || component.cn == null) {
                continue;
            }
            String packageName = component.cn.getPackageName();
            String className = component.cn.getClassName();
            String normalizedClassName = normalizeClassName(packageName, className);
            if (normalizedClassName == null) {
                continue;
            }
            List<String> actions = new ArrayList<>();
            for (ManifestIntentFilter filter : component.intentFilters) {
                actions.addAll(filter.actions);
            }
            records.add(ServiceRecord.fromRaw(packageName, className, component.type, actions,
                    servicePermissions.get(componentKey(packageName, normalizedClassName))));
        }
        return fromRawRecords(sdkInt, records);
    }

    @NonNull
    @VisibleForTesting
    static CredentialProviderManifestInfo fromRawRecords(int sdkInt, @Nullable List<ServiceRecord> records) {
        if (sdkInt < MIN_SDK_INT || records == null || records.isEmpty()) {
            return new CredentialProviderManifestInfo(sdkInt, Collections.emptyList());
        }
        List<ServiceDeclaration> providerServices = new ArrayList<>();
        for (ServiceRecord record : records) {
            if (record == null || !ManifestComponent.TYPE_SERVICE.equals(record.type)
                    || record.actions.isEmpty()) {
                continue;
            }
            boolean provider = record.actions.contains(SERVICE_INTERFACE);
            boolean systemProvider = record.actions.contains(SYSTEM_SERVICE_INTERFACE);
            if (!provider && !systemProvider) {
                continue;
            }
            providerServices.add(new ServiceDeclaration(shortComponentName(record.packageName,
                    record.normalizedClassName), systemProvider, BIND_PERMISSION.equals(record.permission)));
        }
        Collections.sort(providerServices, (left, right) ->
                left.componentName.compareTo(right.componentName));
        return new CredentialProviderManifestInfo(sdkInt, providerServices);
    }

    @NonNull
    private static Map<String, String> buildServicePermissionMap(@Nullable ServiceInfo[] services) {
        if (services == null || services.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> servicePermissions = new HashMap<>();
        for (ServiceInfo service : services) {
            if (service == null || service.packageName == null || service.name == null) {
                continue;
            }
            String normalizedClassName = normalizeClassName(service.packageName, service.name);
            if (normalizedClassName != null) {
                servicePermissions.put(componentKey(service.packageName, normalizedClassName), service.permission);
            }
        }
        return servicePermissions;
    }

    @Nullable
    private static String normalizeClassName(@Nullable String packageName, @Nullable String className) {
        if (packageName == null || packageName.isEmpty() || className == null || className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        } else if (className.indexOf('.') < 0) {
            return packageName + "." + className;
        }
        return className;
    }

    @NonNull
    private static String componentKey(@NonNull String packageName, @NonNull String className) {
        return packageName + "/" + className;
    }

    @NonNull
    private static String shortComponentName(@NonNull String packageName, @NonNull String className) {
        String packagePrefix = packageName + ".";
        if (className.startsWith(packagePrefix)) {
            return packageName + "/." + className.substring(packagePrefix.length());
        }
        return packageName + "/" + className;
    }

    @VisibleForTesting
    static final class ServiceRecord {
        @NonNull
        final String packageName;
        @NonNull
        final String normalizedClassName;
        @Nullable
        final String type;
        @NonNull
        final List<String> actions;
        @Nullable
        final String permission;

        private ServiceRecord(@NonNull String packageName, @NonNull String normalizedClassName,
                              @Nullable String type, @NonNull List<String> actions,
                              @Nullable String permission) {
            this.packageName = packageName;
            this.normalizedClassName = normalizedClassName;
            this.type = type;
            this.actions = Collections.unmodifiableList(actions);
            this.permission = permission;
        }

        @Nullable
        @VisibleForTesting
        static ServiceRecord fromRaw(@Nullable String packageName, @Nullable String className,
                                     @Nullable String type, @Nullable List<String> actions,
                                     @Nullable String permission) {
            String normalizedClassName = normalizeClassName(packageName, className);
            if (packageName == null || normalizedClassName == null) {
                return null;
            }
            List<String> actionList = actions != null ? new ArrayList<>(actions) : Collections.emptyList();
            return new ServiceRecord(packageName, normalizedClassName, type, actionList, permission);
        }
    }

    public static final class ServiceDeclaration {
        @NonNull
        public final String componentName;
        public final boolean isSystemProvider;
        public final boolean hasRequiredBindPermission;

        private ServiceDeclaration(@NonNull String componentName, boolean isSystemProvider,
                                   boolean hasRequiredBindPermission) {
            this.componentName = componentName;
            this.isSystemProvider = isSystemProvider;
            this.hasRequiredBindPermission = hasRequiredBindPermission;
        }

        @NonNull
        public String toDisplayString() {
            StringBuilder builder = new StringBuilder(componentName);
            if (isSystemProvider) {
                builder.append(" (system)");
            }
            if (!hasRequiredBindPermission) {
                builder.append(" (missing bind permission)");
            }
            return builder.toString();
        }
    }
}
