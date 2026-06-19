// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Static manifest audit for Android 16 strict-intent compliance.
 * Exported components and their intent filters must have specific action/data/scheme/host
 * constraints — broad filters without parser-side validation are rejected.
 */
public class IntentFilterManifestAuditTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String SOURCE_PKG = "io.github.muntashirakon.AppManager";

    private static Document sManifest;
    private static Map<String, ExportedComponent> sComponents;

    @BeforeClass
    public static void parseManifest() throws Exception {
        Path manifestPath = findAppProjectDir().resolve("src/main/AndroidManifest.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        sManifest = factory.newDocumentBuilder().parse(manifestPath.toFile());
        sComponents = new HashMap<>();
        collectComponents("activity");
        collectComponents("activity-alias");
        collectComponents("service");
        collectComponents("receiver");
        collectComponents("provider");
    }

    @Test
    public void automationUriAcceptsOnlyKnownHosts() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".automation.AutomationUriActivity");
        assertNotNull("AutomationUriActivity must be exported", comp);

        Set<String> allowedAmHosts = new HashSet<>(Arrays.asList(
                "freeze", "unfreeze", "force-stop", "clear-cache", "clear-data",
                "uninstall", "backup", "restore", "disable-component", "enable-component",
                "scan-trackers", "profile", "run-profile", "install"));

        for (IntentFilter filter : comp.filters) {
            for (String scheme : filter.dataSchemes) {
                if ("am".equals(scheme)) {
                    for (String host : filter.dataHosts) {
                        assertTrue("am:// host " + host + " is not in the known automation set",
                                allowedAmHosts.contains(host));
                    }
                }
                if ("appmanager".equals(scheme)) {
                    for (String host : filter.dataHosts) {
                        assertEquals("appmanager:// deep link host must be 'run-profile'",
                                "run-profile", host);
                    }
                }
            }
        }
    }

    @Test
    public void automationReceiverIsSignatureProtected() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".automation.AutomationReceiver");
        assertNotNull("AutomationReceiver must be exported", comp);
        assertNotNull("AutomationReceiver must have a permission attribute", comp.permission);
        assertTrue("AutomationReceiver must use signature AUTOMATION permission",
                comp.permission.endsWith(".permission.AUTOMATION"));
    }

    @Test
    public void automationBroadcastActionsAreNamespaced() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".automation.AutomationReceiver");
        assertNotNull(comp);

        for (IntentFilter filter : comp.filters) {
            for (String action : filter.actions) {
                assertTrue("Automation broadcast action must be application-namespaced: " + action,
                        action.startsWith("${applicationId}.action.") || action.startsWith("io.github."));
            }
        }
    }

    @Test
    public void appDetailDeepLinksUseKnownSchemesAndHosts() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".details.AppInfoActivity");
        assertNotNull("AppInfoActivity alias must be exported", comp);

        for (IntentFilter filter : comp.filters) {
            for (String scheme : filter.dataSchemes) {
                if ("app-manager".equals(scheme)) {
                    assertTrue("app-manager:// must target 'details' host",
                            filter.dataHosts.contains("details"));
                }
                if ("am".equals(scheme) && !filter.dataMimeTypes.contains("*/*")
                        && !filter.dataMimeTypes.contains("application/vnd.android.package-archive")) {
                    assertTrue("am:// deep link on AppInfoActivity must target 'app' host",
                            filter.dataHosts.contains("app"));
                }
            }
        }
    }

    @Test
    public void settingsDeepLinkUsesKnownSchemeAndHost() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".settings.SettingsActivity");
        assertNotNull("SettingsActivity must be exported", comp);

        boolean hasAppManagerScheme = false;
        for (IntentFilter filter : comp.filters) {
            for (String scheme : filter.dataSchemes) {
                if ("app-manager".equals(scheme)) {
                    hasAppManagerScheme = true;
                    assertTrue("app-manager:// on SettingsActivity must target 'settings' host",
                            filter.dataHosts.contains("settings"));
                }
            }
        }
        assertTrue("SettingsActivity must have app-manager:// deep link", hasAppManagerScheme);
    }

    @Test
    public void quickSettingsTilesArePermissionProtected() {
        String[] tileNames = {
                SOURCE_PKG + ".profiles.QuickFreezeTileService",
                SOURCE_PKG + ".shortcut.ForceStopTileService"
        };
        for (String name : tileNames) {
            ExportedComponent comp = sComponents.get(name);
            if (comp == null) continue;
            assertEquals("QS tile " + name + " must require BIND_QUICK_SETTINGS_TILE",
                    "android.permission.BIND_QUICK_SETTINGS_TILE", comp.permission);
        }
    }

    @Test
    public void documentsProviderRequiresManageDocumentsPermission() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".fm.AppManagerDocumentsProvider");
        assertNotNull("AppManagerDocumentsProvider must be exported", comp);
        assertEquals("android.permission.MANAGE_DOCUMENTS", comp.permission);
    }

    @Test
    public void shizukuProviderRequiresCrossUsersPermission() {
        ExportedComponent comp = sComponents.get("rikka.shizuku.ShizukuProvider");
        assertNotNull("ShizukuProvider must be exported", comp);
        assertEquals("android.permission.INTERACT_ACROSS_USERS_FULL", comp.permission);
    }

    @Test
    public void exportedActivitiesWithFiltersHaveDefaultCategory() {
        for (ExportedComponent comp : sComponents.values()) {
            if (!comp.exported || comp.filters.isEmpty()) continue;
            if (!"activity".equals(comp.tag) && !"activity-alias".equals(comp.tag)) continue;
            if (comp.name.contains("SplashActivity") || comp.name.contains("SplashAlias")) continue;

            for (IntentFilter filter : comp.filters) {
                if (filter.actions.contains("android.intent.action.MAIN")) continue;
                assertTrue("Exported activity " + comp.name
                                + " has intent-filter without DEFAULT category: " + filter.actions,
                        filter.categories.contains("android.intent.category.DEFAULT"));
            }
        }
    }

    @Test
    public void shortcutDispatchDeclaresOnlyKnownActions() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".shortcut.ShortcutDispatchActivity");
        assertNotNull(comp);

        Set<String> allowed = new HashSet<>(Arrays.asList(
                SOURCE_PKG + ".shortcut.action.OPEN_ONE_CLICK_OPS",
                SOURCE_PKG + ".shortcut.action.OPEN_FINDER",
                SOURCE_PKG + ".shortcut.action.RUN_SCHEDULED_BACKUP"));

        for (IntentFilter filter : comp.filters) {
            for (String action : filter.actions) {
                assertTrue("ShortcutDispatchActivity declares unexpected action: " + action,
                        allowed.contains(action));
            }
        }
    }

    @Test
    public void taskerPluginUsesStandardAction() {
        ExportedComponent comp = sComponents.get(SOURCE_PKG + ".automation.TaskerPluginEditActivity");
        if (comp == null) return;

        boolean hasEditSetting = false;
        for (IntentFilter filter : comp.filters) {
            if (filter.actions.contains("com.twofortyfouram.locale.intent.action.EDIT_SETTING")) {
                hasEditSetting = true;
            }
        }
        assertTrue("TaskerPluginEditActivity must declare EDIT_SETTING action", hasEditSetting);
    }

    /**
     * Pinned allowlist of components that are intentionally exported without
     * intent-filters or permission guards (gated by BaseActivity auth instead).
     * Adding a new unguarded export without updating this set forces a review.
     */
    private static final Set<String> KNOWN_UNGUARDED_EXPORTS = new HashSet<>(Arrays.asList(
            SOURCE_PKG + ".runningapps.RunningAppsActivity",
            SOURCE_PKG + ".misc.LabsActivity",
            SOURCE_PKG + ".misc.HelpActivity",
            SOURCE_PKG + ".crypto.auth.AuthFeatureDemultiplexer",
            SOURCE_PKG + ".profiles.ProfilesActivity",
            SOURCE_PKG + ".history.ops.OpHistoryActivity",
            SOURCE_PKG + ".logcat.RecordLogDialogActivity",
            SOURCE_PKG + ".usage.AppUsageActivity",
            SOURCE_PKG + ".debloat.DebloaterActivityAlias"));

    @Test
    public void noNewUnguardedExports() {
        List<String> unexpected = new ArrayList<>();
        for (ExportedComponent comp : sComponents.values()) {
            if (!comp.exported) continue;
            if ("provider".equals(comp.tag)) continue;
            if (comp.name.contains("SplashAlias")) continue;
            if (comp.filters.isEmpty() && comp.permission == null
                    && !KNOWN_UNGUARDED_EXPORTS.contains(comp.name)) {
                unexpected.add(comp.name);
            }
        }
        if (!unexpected.isEmpty()) {
            StringBuilder msg = new StringBuilder(
                    "New exported components without intent-filter or permission guard.\n"
                    + "Add to KNOWN_UNGUARDED_EXPORTS if intentional, or add a guard:\n");
            for (String name : unexpected) {
                msg.append("  - ").append(name).append('\n');
            }
            fail(msg.toString());
        }
    }

    private static void collectComponents(String tagName) {
        NodeList nodes = sManifest.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String name = normalizeComponentName(el.getAttributeNS(ANDROID_NS, "name"));
            ExportedComponent comp = new ExportedComponent(name, tagName);
            comp.exported = Boolean.parseBoolean(el.getAttributeNS(ANDROID_NS, "exported"));

            String perm = el.getAttributeNS(ANDROID_NS, "permission");
            if (perm != null && !perm.isEmpty()) {
                comp.permission = perm;
            }

            String target = el.getAttributeNS(ANDROID_NS, "targetActivity");
            if (target != null && !target.isEmpty()) {
                comp.targetActivity = normalizeComponentName(target);
            }

            NodeList filterNodes = el.getElementsByTagName("intent-filter");
            for (int f = 0; f < filterNodes.getLength(); f++) {
                Element filterEl = (Element) filterNodes.item(f);
                IntentFilter filter = new IntentFilter();

                NodeList actions = filterEl.getElementsByTagName("action");
                for (int a = 0; a < actions.getLength(); a++) {
                    filter.actions.add(((Element) actions.item(a)).getAttributeNS(ANDROID_NS, "name"));
                }
                NodeList categories = filterEl.getElementsByTagName("category");
                for (int c = 0; c < categories.getLength(); c++) {
                    filter.categories.add(((Element) categories.item(c)).getAttributeNS(ANDROID_NS, "name"));
                }
                NodeList data = filterEl.getElementsByTagName("data");
                for (int d = 0; d < data.getLength(); d++) {
                    Element dataEl = (Element) data.item(d);
                    String scheme = dataEl.getAttributeNS(ANDROID_NS, "scheme");
                    if (scheme != null && !scheme.isEmpty()) filter.dataSchemes.add(scheme);
                    String host = dataEl.getAttributeNS(ANDROID_NS, "host");
                    if (host != null && !host.isEmpty()) filter.dataHosts.add(host);
                    String mimeType = dataEl.getAttributeNS(ANDROID_NS, "mimeType");
                    if (mimeType != null && !mimeType.isEmpty()) filter.dataMimeTypes.add(mimeType);
                }
                comp.filters.add(filter);
            }
            sComponents.put(name, comp);
        }
    }

    private static String normalizeComponentName(String name) {
        if (name.startsWith(".")) return SOURCE_PKG + name;
        if (name.contains(".")) return name;
        return SOURCE_PKG + "." + name;
    }

    private static Path findAppProjectDir() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("src/main/AndroidManifest.xml"))) return cursor;
            Path appDir = cursor.resolve("app");
            if (Files.exists(appDir.resolve("src/main/AndroidManifest.xml"))) return appDir;
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate app/src/main/AndroidManifest.xml");
    }

    private static final class ExportedComponent {
        final String name;
        final String tag;
        final List<IntentFilter> filters = new ArrayList<>();
        boolean exported;
        String permission;
        String targetActivity;

        ExportedComponent(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }
    }

    private static final class IntentFilter {
        final Set<String> actions = new HashSet<>();
        final Set<String> categories = new HashSet<>();
        final Set<String> dataSchemes = new HashSet<>();
        final Set<String> dataHosts = new HashSet<>();
        final Set<String> dataMimeTypes = new HashSet<>();
    }
}
