// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.TEXTLAUNCHER;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

/**
 * Revision 3 of the sync messages: the app list carries who signed each app, for search. It's
 * only sent when both devices said they're on revision 3, so older phones on either side get the
 * list they always got.
 */
@RunWith(RobolectricTestRunner.class)
public class SignerSubjectsTest {
    private static final String PKG = "com.example.app";
    private static final List<String> SUBJECTS = Arrays.asList("CN=Jane Doe,O=Example Apps", "CN=Release key");

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.Catalog mCatalog;
    private TestPipe.Duplex mDuplex;

    @Before
    public void setUp() throws GeneralSecurityException {
        mExecutor = Executors.newFixedThreadPool(2);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(41));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(42));
        mCatalog = new SyncFixtures.Catalog();
        mCatalog.apps.add(signed(PKG, SUBJECTS));
    }

    @After
    public void tearDown() {
        mExecutor.shutdownNow();
    }

    @Test
    public void twoDevicesOnRevisionThreeShareWhoSignedEachApp() throws Exception {
        Future<Void> host = startHost(SyncMessages.REVISION);
        List<SyncApp> apps;
        try (SyncClientSession client = connect()) {
            apps = client.list(false);
            // The session still works after the longer list
            client.ping();
        }
        host.get(10, TimeUnit.SECONDS);

        assertEquals(1, apps.size());
        assertEquals(SUBJECTS, apps.get(0).signerSubjects);
        assertEquals(Collections.singletonList(SIGNER_A), apps.get(0).signers);
    }

    @Test
    public void anOlderHostStillListsItsAppsWithoutSubjects() throws Exception {
        for (int revision : new int[]{1, 2}) {
            Future<Void> host = startHost(revision);
            List<SyncApp> apps;
            try (SyncClientSession client = connect()) {
                apps = client.list(false);
                assertEquals(revision, client.hostRevision());
            }
            host.get(10, TimeUnit.SECONDS);

            assertEquals("Revision " + revision, 1, apps.size());
            assertEquals(PKG, apps.get(0).packageName);
            assertTrue(apps.get(0).signerSubjects.isEmpty());
        }
    }

    @Test
    public void aRevisionTwoClientGetsTheListItCanRead() throws Exception {
        Future<Void> host = startHost(SyncMessages.REVISION);
        SyncClientSession client = connect();
        try {
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(SyncMessages.revisionQuery(2));
            channel.receive(SyncMessages.MAX_CONTROL);
            channel.send(SyncMessages.listRequest(false));
            byte[] reply = channel.receive(SyncMessages.MAX_LIST);
            // What a revision 2 build runs, which refuses trailing bytes
            List<SyncApp> apps = SyncMessages.parseList(reply);
            assertEquals(PKG, apps.get(0).packageName);
        } finally {
            client.getPairedSession().getChannel().close();
        }
        host.cancel(true);
    }

    @Test
    public void aFirstReleaseClientThatNeverAsksGetsTheSameFramesAsBefore() throws Exception {
        Future<Void> host = startHost(SyncMessages.REVISION);
        SyncClientSession client = connect();
        try {
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(SyncMessages.listRequest(false));
            byte[] reply = channel.receive(SyncMessages.MAX_LIST);
            assertEquals(1, SyncMessages.parseList(reply).size());
            assertArrayEquals(SyncMessages.listResponse(mCatalog.apps), reply);
        } finally {
            client.getPairedSession().getChannel().close();
        }
        host.cancel(true);
    }

    @Test
    public void theListCapsAndClipsSubjectsAndRefusesTooMany() throws IOException {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < SyncMessages.MAX_SIGNERS + 3; ++i) {
            many.add("CN=Signer " + i);
        }
        char[] longName = new char[SyncMessages.MAX_LABEL_CHARS + 50];
        Arrays.fill(longName, 'x');
        many.set(0, "CN=" + new String(longName));

        List<SyncApp> parsed = SyncMessages.parseList(SyncMessages.listResponse(
                Collections.singletonList(signed(PKG, many)), true), true);

        List<String> subjects = parsed.get(0).signerSubjects;
        assertEquals(SyncMessages.MAX_SIGNERS, subjects.size());
        assertEquals(SyncMessages.MAX_LABEL_CHARS, subjects.get(0).length());
        assertEquals("CN=Signer 1", subjects.get(1));

        // A hostile host claims more subjects than any app has signers
        byte[] honest = SyncMessages.listResponse(Collections.singletonList(signed(PKG,
                Collections.<String>emptyList())), true);
        byte[] lying = honest.clone();
        lying[lying.length - 1] = (byte) (SyncMessages.MAX_SIGNERS + 1);
        assertThrows(SyncProtocolException.class, () -> SyncMessages.parseList(lying, true));
        // A list that stops before its subjects
        byte[] plain = SyncMessages.listResponse(Collections.singletonList(signed(PKG, SUBJECTS)), false);
        assertThrows(IOException.class, () -> SyncMessages.parseList(plain, true));
    }

    @Test
    public void theCatalogTakesTheSubjectFromEachSigningCertificate() {
        SignerInfo signerInfo = PackageUtils.getVerifiedSignerInfo(SyncFixtures.resource(TEXTLAUNCHER));
        assertNotNull(signerInfo);
        X509Certificate[] certs = signerInfo.getCurrentSignerCerts();
        assertNotNull(certs);

        List<String> subjects = AndroidPackageCatalog.signerSubjects(signerInfo);

        assertEquals(Collections.singletonList(certs[0].getSubjectX500Principal().getName()), subjects);
        assertTrue(subjects.get(0).contains("="));
        assertTrue(AndroidPackageCatalog.signerSubjects(null).isEmpty());
    }

    // Helpers

    @NonNull
    private static SyncApp signed(@NonNull String packageName, @NonNull List<String> subjects) {
        return new SyncApp(packageName, "Example", 4, "4.0", false, Collections.singletonList(SIGNER_A),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)),
                subjects);
    }

    @NonNull
    private Future<Void> startHost(int revision) {
        mDuplex = new TestPipe.Duplex();
        FrameIo io = mDuplex.endpointA();
        return mExecutor.submit(() -> {
            HostSession session = new HostSession(io, HostSession.NO_TIMEOUTS, "Galaxy host", mHostIdentity,
                    new SeededRandom(51), new SyncFixtures.MemoryTrust(), s -> ApprovalGate.Decision.ALLOW,
                    mCatalog, null);
            session.setRevision(revision);
            session.run();
            return null;
        });
    }

    @NonNull
    private SyncClientSession connect() throws IOException {
        return SyncClientSession.connect(mDuplex.endpointB(), HostSession.NO_TIMEOUTS, "Pixel client",
                mClientIdentity, new SeededRandom(52), null);
    }
}
