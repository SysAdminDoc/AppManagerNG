// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class SyncFixtures {
    static final String SIGNER_A = repeat('a');
    static final String SIGNER_B = repeat('b');
    static final String SIGNER_C = repeat('c');

    private SyncFixtures() {
    }

    @NonNull
    static String repeat(char c) {
        char[] chars = new char[SyncCrypto.HASH_LENGTH * 2];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    @NonNull
    static SyncApp app(@NonNull String packageName, long versionCode, @NonNull String signer) {
        return app(packageName, versionCode, Collections.singletonList(signer), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
    }

    @NonNull
    static SyncApp app(@NonNull String packageName, long versionCode, @NonNull List<String> signers,
                       @NonNull List<String> lineage, @NonNull List<SyncApp.ApkFileInfo> files) {
        return new SyncApp(packageName, packageName, versionCode, "v" + versionCode, false, signers, lineage, files);
    }

    @NonNull
    static File resource(@NonNull String path) {
        return new File(Objects.requireNonNull(SyncFixtures.class.getClassLoader().getResource(path)).getFile());
    }

    /**
     * A catalog backed by plain maps.
     */
    static final class Catalog implements HostCatalog {
        final List<SyncApp> apps = new ArrayList<>();
        final Map<String, Map<String, File>> files = new HashMap<>();
        final List<String> fileLookups = Collections.synchronizedList(new ArrayList<String>());

        Catalog add(@NonNull SyncApp app, @NonNull File... apkFiles) {
            apps.add(app);
            Map<String, File> map = new LinkedHashMap<>();
            for (int i = 0; i < apkFiles.length; ++i) {
                map.put(app.files.get(i).name, apkFiles[i]);
            }
            files.put(app.packageName, map);
            return this;
        }

        @NonNull
        @Override
        public List<SyncApp> listApps(boolean includeSystem) {
            return new ArrayList<>(apps);
        }

        @Nullable
        @Override
        public Map<String, File> apkFiles(@NonNull String packageName) {
            fileLookups.add(packageName);
            return files.get(packageName);
        }
    }

    static final class MemoryTrust implements TrustedDevices {
        final Map<String, String> devices = new TreeMap<>();

        @Override
        public synchronized boolean isTrusted(@NonNull String fingerprint) {
            return devices.containsKey(fingerprint);
        }

        @Override
        public synchronized void remember(@NonNull String fingerprint, @NonNull String name) {
            devices.put(fingerprint, name);
        }

        @Override
        public synchronized void forget(@NonNull String fingerprint) {
            devices.remove(fingerprint);
        }

        @NonNull
        @Override
        public synchronized Map<String, String> list() {
            return new TreeMap<>(devices);
        }
    }
}
