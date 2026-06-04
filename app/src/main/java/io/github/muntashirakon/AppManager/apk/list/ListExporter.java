// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Xml;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.csv.CsvWriter;

public final class ListExporter {
    public static final int EXPORT_TYPE_CSV = 0;
    public static final int EXPORT_TYPE_JSON = 1;
    public static final int EXPORT_TYPE_XML = 2;
    public static final int EXPORT_TYPE_MARKDOWN = 3;

    @IntDef({EXPORT_TYPE_CSV, EXPORT_TYPE_JSON, EXPORT_TYPE_XML, EXPORT_TYPE_MARKDOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExportType {
    }

    private static final String[] BASE_CSV_HEADERS_N = {
            "name", "label", "versionCode", "versionName", "minSdk", "targetSdk", "signature",
            "firstInstallTime", "lastUpdateTime", "installerPackageName", "installerPackageLabel"
    };
    private static final String[] BASE_CSV_HEADERS_LEGACY = {
            "name", "label", "versionCode", "versionName", "targetSdk", "signature",
            "firstInstallTime", "lastUpdateTime", "installerPackageName", "installerPackageLabel"
    };
    private static final String[] EXTENDED_CSV_HEADERS = {
            "userId", "system", "disabled", "hidden", "suspended", "stopped",
            "requestedPermissionCount", "grantedPermissionCount", "splitCount",
            "sourceDir", "publicSourceDir"
    };

    public static void export(@NonNull Context context,
                              @NonNull Writer writer,
                              @ExportType int exportType,
                              @NonNull List<PackageInfo> packageInfoList) throws IOException {
        export(context, writer, exportType, packageInfoList, false);
    }

    public static void export(@NonNull Context context,
                              @NonNull Writer writer,
                              @ExportType int exportType,
                              @NonNull List<PackageInfo> packageInfoList,
                              boolean includeExtendedMetadata) throws IOException {
        List<AppListItem> appListItems = getAppListItems(context, packageInfoList);
        exportItems(writer, exportType, appListItems, includeExtendedMetadata, context);
    }

    @VisibleForTesting
    static void exportItems(@NonNull Writer writer,
                            @ExportType int exportType,
                            @NonNull List<AppListItem> appListItems,
                            boolean includeExtendedMetadata,
                            @NonNull Context context) throws IOException {
        switch (exportType) {
            case EXPORT_TYPE_CSV:
                exportCsv(writer, appListItems, includeExtendedMetadata);
                return;
            case EXPORT_TYPE_JSON:
                try {
                    exportJson(writer, appListItems, includeExtendedMetadata);
                } catch (JSONException e) {
                    ExUtils.rethrowAsIOException(e);
                }
                return;
            case EXPORT_TYPE_XML:
                exportXml(writer, appListItems, includeExtendedMetadata);
                return;
            case EXPORT_TYPE_MARKDOWN:
                exportMarkdown(context, writer, appListItems, includeExtendedMetadata);
                return;
        }
        throw new IllegalArgumentException("Invalid export type: " + exportType);
    }

    private static void exportXml(@NonNull Writer writer,
                                  @NonNull List<AppListItem> appListItems,
                                  boolean includeExtendedMetadata) throws IOException {
        XmlSerializer xmlSerializer = Xml.newSerializer();
        xmlSerializer.setOutput(writer);
        xmlSerializer.startDocument("UTF-8", true);
        xmlSerializer.docdecl("packages SYSTEM \"https://raw.githubusercontent.com/MuntashirAkon/AppManager/master/schema/packages.dtd\"");
        xmlSerializer.startTag("", "packages");
        xmlSerializer.attribute("", "version", String.valueOf(1));
        if (includeExtendedMetadata) {
            xmlSerializer.attribute("", "extended", String.valueOf(true));
        }
        for (AppListItem appListItem : appListItems) {
            xmlSerializer.startTag("", "package");
            xmlSerializer.attribute("", "name", appListItem.packageName);
            xmlSerializer.attribute("", "label", appListItem.getPackageLabel());
            xmlSerializer.attribute("", "versionCode", String.valueOf(appListItem.getVersionCode()));
            xmlSerializer.attribute("", "versionName", appListItem.getVersionName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                xmlSerializer.attribute("", "minSdk", String.valueOf(appListItem.getMinSdk()));
            }
            xmlSerializer.attribute("", "targetSdk", String.valueOf(appListItem.getTargetSdk()));
            xmlSerializer.attribute("", "signature", appListItem.getSignatureSha256());
            xmlSerializer.attribute("", "firstInstallTime", String.valueOf(appListItem.getFirstInstallTime()));
            xmlSerializer.attribute("", "lastUpdateTime", String.valueOf(appListItem.getLastUpdateTime()));
            if (appListItem.getInstallerPackageName() != null) {
                xmlSerializer.attribute("", "installerPackageName", appListItem.getInstallerPackageName());
                if (appListItem.getInstallerPackageLabel() != null) {
                    xmlSerializer.attribute("", "installerPackageLabel", appListItem.getInstallerPackageLabel());
                }
            }
            if (includeExtendedMetadata) {
                appendExtendedXmlAttributes(xmlSerializer, appListItem);
            }
            xmlSerializer.endTag("", "package");
        }
        xmlSerializer.endTag("", "packages");
        xmlSerializer.endDocument();
        xmlSerializer.flush();
    }

    private static void exportCsv(@NonNull Writer writer,
                                  @NonNull List<AppListItem> appListItems,
                                  boolean includeExtendedMetadata) throws IOException {
        CsvWriter csvWriter = new CsvWriter(writer);
        csvWriter.addLine(buildCsvHeader(includeExtendedMetadata));
        for (AppListItem item : appListItems) {
            csvWriter.addLine(buildCsvRow(item, includeExtendedMetadata));
        }
    }

    private static void exportJson(@NonNull Writer writer,
                                   @NonNull List<AppListItem> appListItems,
                                   boolean includeExtendedMetadata)
            throws JSONException, IOException {
        // Should reflect packages.dtd
        JSONArray array = new JSONArray();
        for (AppListItem item : appListItems) {
            JSONObject object = new JSONObject();
            object.put("name", item.packageName);
            object.put("label", item.getPackageLabel());
            object.put("versionCode", item.getVersionCode());
            object.put("versionName", item.getVersionName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                object.put("minSdk", item.getMinSdk());
            }
            object.put("targetSdk", item.getTargetSdk());
            object.put("signature", item.getSignatureSha256());
            object.put("firstInstallTime", item.getFirstInstallTime());
            object.put("lastUpdateTime", item.getLastUpdateTime());
            if (item.getInstallerPackageName() != null) {
                object.put("installerPackageName", item.getInstallerPackageName());
                if (item.getInstallerPackageLabel() != null) {
                    object.put("installerPackageLabel", item.getInstallerPackageLabel());
                }
            }
            if (includeExtendedMetadata) {
                appendExtendedJson(object, item);
            }
            array.put(object);
        }
        writer.write(array.toString(4));
    }

    private static void exportMarkdown(@NonNull Context context, @NonNull Writer writer,
                                       @NonNull List<AppListItem> appListItems,
                                       boolean includeExtendedMetadata) throws IOException {
        writer.write("# Package Info\n\n");
        for (AppListItem appListItem : appListItems) {
            writer.append("## ").append(appListItem.getPackageLabel()).append("\n\n")
                    .append("**Package name:** ").append(appListItem.packageName).append("\n")
                    .append("**Version:** ").append(appListItem.getVersionName()).append(" (")
                    .append(String.valueOf(appListItem.getVersionCode())).append(")\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                writer.append("**Min SDK:** ").append(String.valueOf(appListItem.getMinSdk()))
                        .append(", ");
            }
            writer.append("**Target SDK:** ").append(String.valueOf(appListItem.getTargetSdk()))
                    .append("\n")
                    .append("**Date installed:** ")
                    .append(DateUtils.formatDateTime(context, appListItem.getFirstInstallTime()))
                    .append(", **Date updated:** ")
                    .append(DateUtils.formatDateTime(context, appListItem.getLastUpdateTime()))
                    .append("\n");
            if (appListItem.getInstallerPackageName() != null) {
                writer.append("**Installer:** ");
                if (appListItem.getInstallerPackageLabel() != null) {
                    writer.append(appListItem.getInstallerPackageLabel()).append(" (");
                }
                writer.append(appListItem.getInstallerPackageName());
                if (appListItem.getInstallerPackageLabel() != null) {
                    writer.append(")");
                }
            }
            if (includeExtendedMetadata) {
                writer.append("\n")
                        .append("**Extended metadata:** user ")
                        .append(String.valueOf(appListItem.getUserId()))
                        .append(", ")
                        .append(appListItem.isSystemApp() ? "system" : "user")
                        .append(", ")
                        .append(appListItem.isEnabled() ? "enabled" : "disabled")
                        .append(", permissions ")
                        .append(String.valueOf(appListItem.getGrantedPermissionCount()))
                        .append("/")
                        .append(String.valueOf(appListItem.getRequestedPermissionCount()))
                        .append(", splits ")
                        .append(String.valueOf(appListItem.getSplitCount()))
                        .append("\n")
                        .append("**State:** hidden=")
                        .append(String.valueOf(appListItem.isHidden()))
                        .append(", suspended=")
                        .append(String.valueOf(appListItem.isSuspended()))
                        .append(", stopped=")
                        .append(String.valueOf(appListItem.isStopped()))
                        .append("\n");
                if (appListItem.getPublicSourceDir() != null) {
                    writer.append("**Source:** ").append(appListItem.getPublicSourceDir()).append("\n");
                }
            }
            writer.append("\n\n");
        }
    }

    @NonNull
    private static String[] buildCsvHeader(boolean includeExtendedMetadata) {
        String[] baseHeader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? BASE_CSV_HEADERS_N : BASE_CSV_HEADERS_LEGACY;
        return includeExtendedMetadata ? append(baseHeader, EXTENDED_CSV_HEADERS) : baseHeader;
    }

    @NonNull
    private static String[] buildCsvRow(@NonNull AppListItem item, boolean includeExtendedMetadata) {
        String installerPackage = item.getInstallerPackageName() != null ? item.getInstallerPackageName() : "";
        String installerLabel = item.getInstallerPackageLabel() != null ? item.getInstallerPackageLabel() : "";
        String[] baseRow;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseRow = new String[]{item.packageName, item.getPackageLabel(),
                    String.valueOf(item.getVersionCode()), item.getVersionName(),
                    String.valueOf(item.getMinSdk()), String.valueOf(item.getTargetSdk()),
                    item.getSignatureSha256(), String.valueOf(item.getFirstInstallTime()),
                    String.valueOf(item.getLastUpdateTime()), installerPackage, installerLabel};
        } else {
            baseRow = new String[]{item.packageName, item.getPackageLabel(),
                    String.valueOf(item.getVersionCode()), item.getVersionName(),
                    String.valueOf(item.getTargetSdk()), item.getSignatureSha256(),
                    String.valueOf(item.getFirstInstallTime()), String.valueOf(item.getLastUpdateTime()),
                    installerPackage, installerLabel};
        }
        return includeExtendedMetadata ? append(baseRow, buildExtendedCsvRow(item)) : baseRow;
    }

    @NonNull
    private static String[] buildExtendedCsvRow(@NonNull AppListItem item) {
        return new String[]{
                String.valueOf(item.getUserId()),
                String.valueOf(item.isSystemApp()),
                String.valueOf(!item.isEnabled()),
                String.valueOf(item.isHidden()),
                String.valueOf(item.isSuspended()),
                String.valueOf(item.isStopped()),
                String.valueOf(item.getRequestedPermissionCount()),
                String.valueOf(item.getGrantedPermissionCount()),
                String.valueOf(item.getSplitCount()),
                item.getSourceDir() != null ? item.getSourceDir() : "",
                item.getPublicSourceDir() != null ? item.getPublicSourceDir() : ""
        };
    }

    @NonNull
    private static String[] append(@NonNull String[] first, @NonNull String[] second) {
        List<String> out = new ArrayList<>(first.length + second.length);
        Collections.addAll(out, first);
        Collections.addAll(out, second);
        return out.toArray(new String[0]);
    }

    private static void appendExtendedJson(@NonNull JSONObject object, @NonNull AppListItem item)
            throws JSONException {
        object.put("userId", item.getUserId());
        object.put("system", item.isSystemApp());
        object.put("disabled", !item.isEnabled());
        object.put("hidden", item.isHidden());
        object.put("suspended", item.isSuspended());
        object.put("stopped", item.isStopped());
        object.put("requestedPermissionCount", item.getRequestedPermissionCount());
        object.put("grantedPermissionCount", item.getGrantedPermissionCount());
        object.put("splitCount", item.getSplitCount());
        if (item.getSourceDir() != null) {
            object.put("sourceDir", item.getSourceDir());
        }
        if (item.getPublicSourceDir() != null) {
            object.put("publicSourceDir", item.getPublicSourceDir());
        }
    }

    private static void appendExtendedXmlAttributes(@NonNull XmlSerializer xmlSerializer,
                                                    @NonNull AppListItem item) throws IOException {
        xmlSerializer.attribute("", "userId", String.valueOf(item.getUserId()));
        xmlSerializer.attribute("", "system", String.valueOf(item.isSystemApp()));
        xmlSerializer.attribute("", "disabled", String.valueOf(!item.isEnabled()));
        xmlSerializer.attribute("", "hidden", String.valueOf(item.isHidden()));
        xmlSerializer.attribute("", "suspended", String.valueOf(item.isSuspended()));
        xmlSerializer.attribute("", "stopped", String.valueOf(item.isStopped()));
        xmlSerializer.attribute("", "requestedPermissionCount", String.valueOf(item.getRequestedPermissionCount()));
        xmlSerializer.attribute("", "grantedPermissionCount", String.valueOf(item.getGrantedPermissionCount()));
        xmlSerializer.attribute("", "splitCount", String.valueOf(item.getSplitCount()));
        if (item.getSourceDir() != null) {
            xmlSerializer.attribute("", "sourceDir", item.getSourceDir());
        }
        if (item.getPublicSourceDir() != null) {
            xmlSerializer.attribute("", "publicSourceDir", item.getPublicSourceDir());
        }
    }

    @NonNull
    private static List<AppListItem> getAppListItems(@NonNull Context context,
                                                     @NonNull List<PackageInfo> packageInfoList) {
        List<AppListItem> appListItems = new ArrayList<>(packageInfoList.size());
        PackageManager pm = context.getPackageManager();
        for (PackageInfo packageInfo : packageInfoList) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            AppListItem item = new AppListItem(packageInfo.packageName);
            appListItems.add(item);
            item.setIcon(UIUtils.getBitmapFromDrawable(applicationInfo.loadIcon(pm)));
            item.setPackageLabel(applicationInfo.loadLabel(pm).toString());
            item.setVersionCode(PackageInfoCompat.getLongVersionCode(packageInfo));
            item.setVersionName(packageInfo.versionName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                item.setMinSdk(applicationInfo.minSdkVersion);
            }
            item.setTargetSdk(applicationInfo.targetSdkVersion);
            String[] signatureSha256 = PackageUtils.getSigningCertSha256Checksum(packageInfo, false);
            item.setSignatureSha256(TextUtils.join(",", signatureSha256));
            item.setFirstInstallTime(packageInfo.firstInstallTime);
            item.setLastUpdateTime(packageInfo.lastUpdateTime);
            item.setUserId(UserHandleHidden.getUserId(applicationInfo.uid));
            item.setSystemApp(ApplicationInfoCompat.isSystemApp(applicationInfo));
            item.setEnabled(applicationInfo.enabled);
            item.setHidden(ApplicationInfoCompat.isHidden(applicationInfo));
            item.setSuspended(ApplicationInfoCompat.isSuspended(applicationInfo));
            item.setStopped(ApplicationInfoCompat.isStopped(applicationInfo));
            item.setRequestedPermissionCount(packageInfo.requestedPermissions != null
                    ? packageInfo.requestedPermissions.length : 0);
            item.setGrantedPermissionCount(countGrantedPermissions(packageInfo));
            item.setSplitCount(packageInfo.splitNames != null ? packageInfo.splitNames.length : 0);
            item.setSourceDir(applicationInfo.sourceDir);
            item.setPublicSourceDir(applicationInfo.publicSourceDir);
            String installerPackageName = PackageManagerCompat.getInstallerPackageName(
                    packageInfo.packageName, item.getUserId());
            if (installerPackageName != null) {
                item.setInstallerPackageName(installerPackageName);
                String installerPackageLabel;
                try {
                    installerPackageLabel = pm.getApplicationInfo(installerPackageName, 0)
                            .loadLabel(pm).toString();
                    if (!installerPackageLabel.equals(installerPackageName)) {
                        item.setInstallerPackageLabel(installerPackageLabel);
                    }
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }
        }
        return appListItems;
    }

    private static int countGrantedPermissions(@NonNull PackageInfo packageInfo) {
        if (packageInfo.requestedPermissions == null || packageInfo.requestedPermissionsFlags == null) {
            return 0;
        }
        int count = 0;
        int limit = Math.min(packageInfo.requestedPermissions.length, packageInfo.requestedPermissionsFlags.length);
        for (int i = 0; i < limit; ++i) {
            if ((packageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                ++count;
            }
        }
        return count;
    }
}
