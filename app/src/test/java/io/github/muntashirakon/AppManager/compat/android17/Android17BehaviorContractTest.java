// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat.android17;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

public class Android17BehaviorContractTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void manifestDeclaresLocalNetworkPermissionBeforeTarget37() throws Exception {
        Document manifest = parse(findAppProjectDir().resolve("src/main/AndroidManifest.xml"));
        NodeList permissions = manifest.getElementsByTagName("uses-permission");
        boolean found = false;
        for (int i = 0; i < permissions.getLength(); i++) {
            Element permission = (Element) permissions.item(i);
            if ("android.permission.ACCESS_LOCAL_NETWORK".equals(
                    permission.getAttributeNS(ANDROID_NS, "name"))) {
                found = true;
                break;
            }
        }

        assertTrue("Android 17 targetSdk 37 requires ACCESS_LOCAL_NETWORK for wireless ADB discovery",
                found);
    }

    @Test
    public void networkSecurityConfigScopesCleartextToLoopbackOnly() throws Exception {
        Document manifest = parse(findAppProjectDir().resolve("src/main/AndroidManifest.xml"));
        Element application = (Element) manifest.getElementsByTagName("application").item(0);
        assertFalse("Do not reintroduce manifest-wide cleartext; use network security config domains",
                "true".equals(application.getAttributeNS(ANDROID_NS, "usesCleartextTraffic")));

        Document config = parse(findAppProjectDir().resolve("src/main/res/xml/network_security_config.xml"));
        Element baseConfig = (Element) config.getElementsByTagName("base-config").item(0);
        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"));

        NodeList domainConfigs = config.getElementsByTagName("domain-config");
        Set<String> cleartextDomains = new HashSet<>();
        for (int i = 0; i < domainConfigs.getLength(); i++) {
            Element domainConfig = (Element) domainConfigs.item(i);
            if (!"true".equals(domainConfig.getAttribute("cleartextTrafficPermitted"))) {
                continue;
            }
            NodeList domains = domainConfig.getElementsByTagName("domain");
            for (int j = 0; j < domains.getLength(); j++) {
                cleartextDomains.add(domains.item(j).getTextContent());
            }
        }

        assertEquals("Only same-profile loopback endpoints may retain cleartext", setOf("127.0.0.1", "localhost"),
                cleartextDomains);
    }

    @Test
    public void typefaceStaticFinalReflectionDoesNotWriteBackSystemFontMap() throws Exception {
        Path source = findProjectRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/TypefaceUtil.java");
        String contents = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

        assertFalse("Android 17 targetSdk 37 rejects Field.set(null, ...) on Typeface.sSystemFontMap",
                contents.contains("field.set(null, allFontsForThisApp)"));
    }

    @Test
    public void rootServiceResourcesStaticFinalHackStaysBelowAndroid17() throws Exception {
        Path source = findProjectRoot().resolve(
                "server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java");
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);

        int writeLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("systemResField.set(null, wrapper)")) {
                writeLine = i;
                break;
            }
        }
        assertTrue("Expected the legacy Resources.mSystem write to remain visible to the Android 17 guard",
                writeLine >= 0);

        boolean guarded = false;
        int searchStart = Math.max(0, writeLine - 12);
        for (int i = searchStart; i < writeLine; i++) {
            if (lines.get(i).contains("Build.VERSION.SDK_INT < 37")) {
                guarded = true;
                break;
            }
        }
        assertTrue("Resources.mSystem static-final write must stay gated below Android 17", guarded);
    }

    @Test
    public void messageQueuePrivateImplementationIsNotReflected() throws Exception {
        Path root = findProjectRoot();
        List<String> offenders = new ArrayList<>();
        for (Path sourceRoot : sourceRoots(root)) {
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(sourceRoot)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(path -> collectMessageQueueReflectionOffenders(root, path, offenders));
            }
        }
        Collections.sort(offenders);

        assertTrue("Android 17 lock-free MessageQueue can break private-field reflection:\n"
                + String.join("\n", offenders), offenders.isEmpty());
    }

    private static List<Path> sourceRoots(Path root) {
        List<Path> roots = new ArrayList<>();
        roots.add(root.resolve("app/src/main/java"));
        roots.add(root.resolve("libcore/compat/src/main/java"));
        roots.add(root.resolve("libcore/io/src/main/java"));
        roots.add(root.resolve("libcore/ui/src/main/java"));
        roots.add(root.resolve("libserver/src/main/java"));
        roots.add(root.resolve("server/src/main/java"));
        roots.add(root.resolve("hiddenapi/src/main/java"));
        return roots;
    }

    private static void collectMessageQueueReflectionOffenders(Path root,
                                                               Path path,
                                                               List<String> offenders) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isMessageQueuePrivateImplementationReference(line)) {
                    offenders.add(root.relativize(path) + ":" + (i + 1) + ": " + line.trim());
                }
            }
        } catch (IOException e) {
            offenders.add(root.relativize(path) + ": " + e.getMessage());
        }
    }

    private static boolean isMessageQueuePrivateImplementationReference(String line) {
        return line.contains("MessageQueue.class.getDeclaredField")
                || line.contains("MessageQueue.class.getDeclaredMethod")
                || line.contains("\"mMessages\"")
                || line.contains("\"mIdleHandlers\"")
                || line.contains("\"mBlocked\"")
                || line.contains("\"mQuitting\"")
                || line.contains("\"postSyncBarrier\"")
                || line.contains("\"removeSyncBarrier\"");
    }

    private static Set<String> setOf(String... values) {
        Set<String> set = new HashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return set;
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static Path findAppProjectDir() throws IOException {
        return findProjectRoot().resolve("app");
    }

    private static Path findProjectRoot() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("settings.gradle"))
                    && Files.exists(cursor.resolve("app/src/main/AndroidManifest.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate AppManagerNG project root");
    }
}
