// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

public final class ScopedKeyStoreImporter {

    @VisibleForTesting
    static final Set<String> AM_OWNED_ALIASES;

    static {
        Set<String> s = new HashSet<>();
        s.add("adb_rsa");
        s.add("signing_key");
        s.add("backup_rsa");
        s.add("backup_aes");
        s.add("backup_ecc");
        AM_OWNED_ALIASES = Collections.unmodifiableSet(s);
    }

    public static boolean isAmOwnedAlias(@NonNull String alias) {
        return AM_OWNED_ALIASES.contains(alias);
    }

    public static final class ImportPreview {
        @NonNull
        public final List<String> amAliasesFound;
        @NonNull
        public final List<String> collisions;
        @NonNull
        public final List<String> foreignAliases;

        ImportPreview(@NonNull List<String> amAliasesFound,
                      @NonNull List<String> collisions,
                      @NonNull List<String> foreignAliases) {
            this.amAliasesFound = amAliasesFound;
            this.collisions = collisions;
            this.foreignAliases = foreignAliases;
        }

        public boolean hasImportableAliases() {
            return !amAliasesFound.isEmpty();
        }
    }

    public static final class ImportResult {
        @NonNull
        public final List<String> succeeded;
        @NonNull
        public final List<String> failed;

        ImportResult(@NonNull List<String> succeeded, @NonNull List<String> failed) {
            this.succeeded = succeeded;
            this.failed = failed;
        }

        public boolean hasFailures() {
            return !failed.isEmpty();
        }

        public boolean allFailed() {
            return succeeded.isEmpty() && !failed.isEmpty();
        }
    }

    @NonNull
    public static ImportPreview preview(@NonNull KeyStore importKs, @NonNull KeyStoreManager liveKsm)
            throws KeyStoreException {
        List<String> amFound = new ArrayList<>();
        List<String> collisions = new ArrayList<>();
        List<String> foreign = new ArrayList<>();

        Enumeration<String> aliases = importKs.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (isAmOwnedAlias(alias)) {
                amFound.add(alias);
                if (liveKsm.containsKey(alias)) {
                    collisions.add(alias);
                }
            } else {
                foreign.add(alias);
            }
        }
        Collections.sort(amFound);
        Collections.sort(collisions);
        Collections.sort(foreign);
        return new ImportPreview(amFound, collisions, foreign);
    }

    @NonNull
    public static ImportResult importScoped(
            @NonNull KeyStore importKs,
            @NonNull char[] importPassword,
            @NonNull KeyStoreManager liveKsm) {
        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        Enumeration<String> aliases;
        try {
            aliases = importKs.aliases();
        } catch (KeyStoreException e) {
            return new ImportResult(succeeded, failed);
        }

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!isAmOwnedAlias(alias)) continue;

            try {
                Key key = importKs.getKey(alias, importPassword);
                if (key instanceof SecretKey) {
                    liveKsm.addSecretKey(alias, (SecretKey) key, true);
                    succeeded.add(alias);
                } else if (key instanceof PrivateKey) {
                    Certificate cert = importKs.getCertificate(alias);
                    KeyPair kp = new KeyPair((PrivateKey) key, cert);
                    liveKsm.addKeyPair(alias, kp, true);
                    kp.destroy();
                    succeeded.add(alias);
                } else {
                    failed.add(alias);
                }
            } catch (Exception e) {
                failed.add(alias);
            }
        }
        return new ImportResult(succeeded, failed);
    }

    private ScopedKeyStoreImporter() {
    }
}
