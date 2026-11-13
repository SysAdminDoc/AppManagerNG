// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNsdManager;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;

@RunWith(RobolectricTestRunner.class)
public class LanSyncActivityTest {
    private static final long WAIT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private ActivityController<LanSyncActivity> mController;
    private boolean mStopped;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(LanSyncActivity.class).setup();
    }

    @After
    public void tearDown() {
        if (!mStopped) {
            mController.pause().stop();
        }
        mController.destroy();
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
        ShadowNsdManager.reset();
    }

    @Test
    public void leavingTheScreenStopsSharingAndTheSearchForDevices() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        NsdManager nsdManager = (NsdManager) ApplicationProvider.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE);
        ShadowNsdManager nsd = Shadow.extract(nsdManager);

        MaterialSwitch share = activity.findViewById(R.id.share_switch);
        assertTrue(share.isEnabled());
        share.performClick();
        assertTrue(share.isChecked());

        LanSyncHost host = waitFor(viewModel::getHost);
        assertTrue(host.isRunning());
        NsdManager.RegistrationListener registration = host.getRegistrationListener();
        assertNotNull(registration);
        NsdServiceInfo info = nsd.getRegisteredServiceInfo(registration);
        assertNotNull("This device is announced while sharing is on", info);
        int port = info.getPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
        }
        assertNotNull(waitFor(viewModel::getDiscovery));
        waitFor(() -> isEmpty(nsd.getDiscoveryListeners(LanSyncHost.SERVICE_TYPE)) ? null : Boolean.TRUE);

        mController.pause().stop();
        mStopped = true;

        assertFalse(host.isRunning());
        assertNull(viewModel.getHost());
        assertNull(host.getRegistrationListener());
        assertNull("The announcement is withdrawn", nsd.getRegisteredServiceInfo(registration));
        assertRefused(port);
        assertFalse(share.isChecked());
        assertNull(viewModel.getDiscovery());
        assertTrue("The search for devices ends", isEmpty(nsd.getDiscoveryListeners(LanSyncHost.SERVICE_TYPE)));

        // Coming back looks for devices again, but sharing waits to be turned on
        mController.restart().resume();
        mStopped = false;
        assertNotNull(waitFor(viewModel::getDiscovery));
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(viewModel.getHost());
        assertFalse(share.isChecked());
    }

    @Test
    public void sharingAgainAfterComingBackAnnouncesTheSamePort() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        ShadowNsdManager nsd = Shadow.extract(ApplicationProvider.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE));
        MaterialSwitch share = activity.findViewById(R.id.share_switch);

        share.performClick();
        int first = announcedPort(nsd, waitFor(viewModel::getHost));
        mController.pause().stop();
        mController.restart().resume();
        assertNull(viewModel.getHost());

        share.performClick();
        int second = announcedPort(nsd, waitFor(viewModel::getHost));
        // A nearby device that cached the first announcement still reaches this one
        assertEquals(first, second);
    }

    @Test
    public void turningThePhoneKeepsSharingAndTheConnectedDevice() throws Exception {
        LanSyncViewModel viewModel = new ViewModelProvider(mController.get()).get(LanSyncViewModel.class);
        ShadowNsdManager nsd = Shadow.extract(ApplicationProvider.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE));
        mController.get().findViewById(R.id.share_switch).performClick();
        LanSyncHost host = waitFor(viewModel::getHost);
        int port = announcedPort(nsd, host);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
            mController.recreate();
            shadowOf(Looper.getMainLooper()).idle();

            assertSame("Turning the phone is not leaving the screen", host, viewModel.getHost());
            assertTrue(host.isRunning());
            MaterialSwitch share = mController.get().findViewById(R.id.share_switch);
            assertTrue(share.isChecked());
        }
    }

    @Test
    public void theScreenStaysOnWhileSharingOrDownloadingAsLongAsTheSettingSays() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        MaterialSwitch share = activity.findViewById(R.id.share_switch);
        shadowOf(Looper.getMainLooper()).idle();
        Menu menu = ((MaterialToolbar) activity.findViewById(R.id.toolbar)).getMenu();
        assertTrue("Until transfers finish is the default",
                menu.findItem(R.id.keep_screen_until_done).isChecked());
        assertFalse("Nothing running yet", keepsScreenOn(activity));

        share.performClick();
        waitFor(viewModel::getHost);
        assertTrue("The screen going off would stop sharing", keepsScreenOn(activity));
        share.performClick();
        assertFalse("Sharing off lets the screen sleep again", keepsScreenOn(activity));

        viewModel.setTask("Downloading");
        assertTrue("A download holds it too", keepsScreenOn(activity));
        viewModel.setTask(null);
        assertFalse(keepsScreenOn(activity));

        shadowOf(activity).clickMenuItem(R.id.keep_screen_10_minutes);
        assertEquals(LanSyncSettings.SCREEN_ON_10_MINUTES, new LanSyncSettings(activity).getKeepScreenOn());
        viewModel.setTask("Downloading");
        assertTrue(keepsScreenOn(activity));
        shadowOf(Looper.getMainLooper()).idleFor(9, TimeUnit.MINUTES);
        assertTrue(keepsScreenOn(activity));
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MINUTES);
        assertFalse("Ten minutes are up, the download goes on", keepsScreenOn(activity));

        shadowOf(activity).clickMenuItem(R.id.keep_screen_until_done);
        assertTrue(keepsScreenOn(activity));
        shadowOf(activity).clickMenuItem(R.id.keep_screen_off);
        assertFalse(keepsScreenOn(activity));
        assertEquals(LanSyncSettings.SCREEN_ON_OFF, new LanSyncSettings(activity).getKeepScreenOn());
        viewModel.setTask(null);
    }

    private static boolean keepsScreenOn(@NonNull LanSyncActivity activity) {
        return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    @Test
    public void findingNothingForAWhileShowsHowToUnblockTheNetwork() {
        LanSyncActivity activity = mController.get();
        View card = activity.findViewById(R.id.trouble_card);
        TextView title = activity.findViewById(R.id.trouble_title);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Not while the search has only just started", View.GONE, card.getVisibility());

        shadowOf(Looper.getMainLooper()).idleFor(LanSyncActivity.NOTHING_FOUND_MILLIS - 1000, TimeUnit.MILLISECONDS);
        assertEquals(View.GONE, card.getVisibility());
        shadowOf(Looper.getMainLooper()).idleFor(1000, TimeUnit.MILLISECONDS);

        assertEquals(View.VISIBLE, card.getVisibility());
        assertEquals(activity.getString(R.string.lan_sync_trouble_nothing_found), title.getText().toString());
        assertTrue(shows(card, activity.getString(R.string.lan_sync_trouble_firewall)));
        assertTrue(shows(card, activity.getString(R.string.lan_sync_trouble_afwall)));
        assertTrue(shows(card, activity.getString(R.string.lan_sync_trouble_huawei)));

        activity.findViewById(R.id.trouble_dismiss).performClick();
        assertEquals(View.GONE, card.getVisibility());
    }

    @Test
    public void theHelpIsInTheMenuToo() {
        LanSyncActivity activity = mController.get();
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        tabs.getTabAt(1).select();
        assertEquals(View.GONE, activity.findViewById(R.id.devices_pane).getVisibility());

        shadowOf(activity).clickMenuItem(R.id.action_troubleshooting);

        assertEquals(View.VISIBLE, activity.findViewById(R.id.devices_pane).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.trouble_card).getVisibility());
        assertEquals(activity.getString(R.string.lan_sync_trouble_asked),
                ((TextView) activity.findViewById(R.id.trouble_title)).getText().toString());
    }

    @Test
    public void aConnectionNobodyAnswersShowsTheHelp() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        viewModel.connect(InetAddress.getLoopbackAddress(), closedPort, "Other phone");
        waitFor(() -> Integer.valueOf(LanSyncViewModel.TROUBLE_CONNECT_FAILED).equals(viewModel.getTrouble().getValue())
                ? Boolean.TRUE : null);

        assertEquals(View.VISIBLE, activity.findViewById(R.id.trouble_card).getVisibility());
        assertEquals(activity.getString(R.string.lan_sync_trouble_connect_failed),
                ((TextView) activity.findViewById(R.id.trouble_title)).getText().toString());

        // Nothing found later doesn't hide the stronger reason
        viewModel.showTrouble(LanSyncViewModel.TROUBLE_NOTHING_FOUND);
        assertEquals(Integer.valueOf(LanSyncViewModel.TROUBLE_CONNECT_FAILED), viewModel.getTrouble().getValue());
    }

    private static boolean shows(@NonNull View root, @NonNull String text) {
        if (root instanceof TextView) {
            return text.contentEquals(((TextView) root).getText()) && root.isShown();
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); ++i) {
                if (shows(group.getChildAt(i), text)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void thePortStaysOnScreenAfterADeviceLeaves() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        ShadowNsdManager nsd = Shadow.extract(ApplicationProvider.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE));
        TextView status = activity.findViewById(R.id.share_status);
        activity.findViewById(R.id.share_switch).performClick();
        int port = announcedPort(nsd, waitFor(viewModel::getHost));
        String portText = "port " + port;
        waitFor(() -> status.getText().toString().contains(portText) ? Boolean.TRUE : null);

        // A device that connects and goes away ends a session
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
        }
        waitFor(ShadowToast::getTextOfLatestToast);
        shadowOf(Looper.getMainLooper()).idle();

        // Typing the address by hand on the next device still needs the port
        assertTrue(status.getText().toString(), status.getText().toString().contains(portText));
    }

    @Test
    public void forgettingTheConnectedDeviceEndsItsSession() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        ShadowNsdManager nsd = Shadow.extract(ApplicationProvider.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE));
        activity.findViewById(R.id.share_switch).performClick();
        int port = announcedPort(nsd, waitFor(viewModel::getHost));
        LocalIdentity clientIdentity = LocalIdentity.generate(new SeededRandom(93));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SyncClientSession> pending = executor.submit(() -> new LanSyncConnector().connect(
                    InetAddress.getLoopbackAddress(), port, "Pixel client", clientIdentity, new SeededRandom(94), null));
            ApprovalGate.Request request = waitFor(() -> viewModel.getApproval().getValue());
            viewModel.decide(request, ApprovalGate.Decision.ALLOW_AND_REMEMBER);
            try (SyncClientSession session = pending.get(10, TimeUnit.SECONDS)) {
                LanSyncViewModel.TrustedDevice remembered = waitFor(() -> {
                    List<LanSyncViewModel.TrustedDevice> devices = viewModel.getTrusted().getValue();
                    return devices != null && devices.size() == 1 ? devices.get(0) : null;
                });
                session.ping();

                viewModel.forget(remembered);

                // It can't keep pulling apps on the session it already had
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                boolean ended = false;
                while (!ended && System.nanoTime() < deadline) {
                    try {
                        session.ping();
                        Thread.sleep(50);
                    } catch (IOException e) {
                        ended = true;
                    }
                }
                assertTrue("Forgetting the device ends its open session", ended);
                waitFor(() -> {
                    List<LanSyncViewModel.TrustedDevice> devices = viewModel.getTrusted().getValue();
                    return devices != null && devices.isEmpty() ? Boolean.TRUE : null;
                });
                assertTrue("Sharing stays on for other devices", waitFor(viewModel::getHost).isRunning());
                shadowOf(Looper.getMainLooper()).idle();
                assertNull("Ending it on purpose isn't reported as a failure", ShadowToast.getTextOfLatestToast());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int announcedPort(@NonNull ShadowNsdManager nsd, @NonNull LanSyncHost host) {
        NsdManager.RegistrationListener registration = host.getRegistrationListener();
        assertNotNull(registration);
        NsdServiceInfo info = nsd.getRegisteredServiceInfo(registration);
        assertNotNull(info);
        return info.getPort();
    }

    @Test
    public void aTypedAddressMustBeNumericAndOnTheLocalNetwork() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        TextInputEditText address = activity.findViewById(R.id.manual_address);
        TextInputLayout layout = activity.findViewById(R.id.manual_address_layout);
        View connect = activity.findViewById(R.id.manual_connect);

        // A name would need a lookup, so it is refused before anything goes out
        address.setText("printer.local:4000");
        connect.performClick();
        assertEquals(activity.getString(R.string.lan_sync_address_invalid), String.valueOf(layout.getError()));
        assertEquals(LanSyncViewModel.Connection.IDLE, viewModel.getConnection().getValue().step);

        address.setText("8.8.8.8:4000");
        connect.performClick();
        assertEquals(activity.getString(R.string.lan_sync_address_not_local), String.valueOf(layout.getError()));
        assertEquals(LanSyncViewModel.Connection.IDLE, viewModel.getConnection().getValue().step);

        // A local address goes through. Nothing listens there, so the attempt fails on its own.
        int port;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = closed.getLocalPort();
        }
        address.setText("127.0.0.1:" + port);
        connect.performClick();
        assertNull(layout.getError());
        assertEquals(LanSyncViewModel.Connection.CONNECTING, viewModel.getConnection().getValue().step);
        waitFor(() -> {
            LanSyncViewModel.Connection connection = viewModel.getConnection().getValue();
            return connection != null && connection.step == LanSyncViewModel.Connection.IDLE ? Boolean.TRUE : null;
        });
    }

    @Test
    public void aSignatureMismatchSaysWhyAndOffersNoGetButton() {
        LanSyncActivity activity = mController.get();
        List<LanSyncViewModel.AppRow> pulled = new ArrayList<>();
        LanSyncAppsAdapter adapter = new LanSyncAppsAdapter(pulled::add);
        FrameLayout parent = new FrameLayout(activity);
        LanSyncViewModel.AppRow mismatch = new LanSyncViewModel.AppRow(SyncFixtures.app("com.example.a", 5, SIGNER_B),
                SyncFixtures.app("com.example.a", 3, SIGNER_A));
        LanSyncViewModel.AppRow newer = new LanSyncViewModel.AppRow(SyncFixtures.app("com.example.b", 5, SIGNER_A),
                SyncFixtures.app("com.example.b", 3, SIGNER_A));
        assertEquals(SyncComparator.Status.SIGNATURE_DIFFERS, mismatch.status);
        assertEquals(SyncComparator.Status.NEWER, newer.status);
        adapter.setRows(Arrays.asList(mismatch, newer), true);

        LanSyncAppsAdapter.ViewHolder first = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(first, 0);
        assertEquals(View.GONE, first.pull.getVisibility());
        assertEquals(activity.getString(R.string.lan_sync_note_signature), first.note.getText().toString());
        first.pull.performClick();
        assertTrue(pulled.isEmpty());

        LanSyncAppsAdapter.ViewHolder second = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(second, 1);
        assertEquals(View.VISIBLE, second.pull.getVisibility());
        assertTrue(second.pull.isEnabled());
        second.pull.performClick();
        assertEquals(Collections.singletonList(newer), pulled);

        // A recycled row that now shows the mismatch loses the button and its action
        adapter.onBindViewHolder(second, 0);
        assertEquals(View.GONE, second.pull.getVisibility());
        second.pull.performClick();
        assertEquals(1, pulled.size());

        // While a download runs, nothing else can start
        adapter.setRows(Collections.singletonList(newer), false);
        adapter.onBindViewHolder(second, 0);
        assertFalse(second.pull.isEnabled());
    }

    @Test
    public void withReplacingAllowedAnAppSignedByAnotherKeyGetsAReplaceButton() {
        LanSyncActivity activity = mController.get();
        List<LanSyncViewModel.AppRow> pulled = new ArrayList<>();
        List<LanSyncViewModel.AppRow> replaced = new ArrayList<>();
        LanSyncAppsAdapter adapter = new LanSyncAppsAdapter(pulled::add, replaced::add);
        FrameLayout parent = new FrameLayout(activity);
        LanSyncViewModel.AppRow mismatch = new LanSyncViewModel.AppRow(SyncFixtures.app("com.example.a", 5, SIGNER_B),
                SyncFixtures.app("com.example.a", 3, SIGNER_A));
        SyncApp systemHere = new SyncApp("com.example.s", "com.example.s", 3, "v3", true,
                Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
        LanSyncViewModel.AppRow system = new LanSyncViewModel.AppRow(SyncFixtures.app("com.example.s", 5, SIGNER_B),
                systemHere);
        assertEquals(SyncComparator.Status.SIGNATURE_DIFFERS, system.status);
        adapter.setRows(Arrays.asList(mismatch, system), true);
        LanSyncAppsAdapter.ViewHolder first = adapter.onCreateViewHolder(parent, 0);
        LanSyncAppsAdapter.ViewHolder second = adapter.onCreateViewHolder(parent, 0);

        // Off by default: nothing changes for anyone who hasn't asked for it
        adapter.onBindViewHolder(first, 0);
        assertEquals(View.GONE, first.pull.getVisibility());
        assertEquals(activity.getString(R.string.lan_sync_note_signature), first.note.getText().toString());

        adapter.setAllowReplace(true);
        adapter.onBindViewHolder(first, 0);
        assertEquals(View.VISIBLE, first.pull.getVisibility());
        assertTrue(first.pull.isEnabled());
        assertEquals(activity.getString(R.string.lan_sync_replace), first.pull.getText().toString());
        assertEquals(activity.getString(R.string.lan_sync_note_signature_replace), first.note.getText().toString());
        first.pull.performClick();
        assertEquals(Collections.singletonList(mismatch), replaced);
        assertTrue("Replace never goes down the plain Get path", pulled.isEmpty());

        // The copy of a system app on the system partition can't be removed, so there's no button
        adapter.onBindViewHolder(second, 1);
        assertEquals(View.GONE, second.pull.getVisibility());
        assertEquals(activity.getString(R.string.lan_sync_note_signature_system), second.note.getText().toString());
        second.pull.performClick();
        assertEquals(1, replaced.size());

        // While a download runs, nothing else can start
        adapter.setRows(Collections.singletonList(mismatch), false);
        adapter.onBindViewHolder(first, 0);
        assertEquals(View.VISIBLE, first.pull.getVisibility());
        assertFalse(first.pull.isEnabled());
    }

    @Test
    public void searchNarrowsTheListAndTheChipsCountWhatItFinds() {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        viewModel.setRows(Arrays.asList(
                new LanSyncViewModel.AppRow(SyncFixtures.app("org.mozilla.firefox", 5, SIGNER_A),
                        SyncFixtures.app("org.mozilla.firefox", 4, SIGNER_A)),
                new LanSyncViewModel.AppRow(SyncFixtures.app("org.mozilla.focus", 5, SIGNER_A), null),
                new LanSyncViewModel.AppRow(SyncFixtures.app("com.termux", 5, SIGNER_A),
                        SyncFixtures.app("com.termux", 4, SIGNER_A))));
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView list = activity.findViewById(R.id.recycler_view);
        Chip newer = activity.findViewById(R.id.chip_newer);
        Chip notInstalled = activity.findViewById(R.id.chip_not_installed);
        TabLayout tabs = activity.findViewById(R.id.tab_layout);
        assertEquals(2, list.getAdapter().getItemCount());
        assertEquals(0, tabs.getSelectedTabPosition());

        MenuItem searchItem = ((MaterialToolbar) activity.findViewById(R.id.toolbar)).getMenu()
                .findItem(R.id.action_search);
        searchItem.expandActionView();
        AdvancedSearchView searchView = (AdvancedSearchView) searchItem.getActionView();
        searchView.setQuery("MOZILLA", false);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("Typing on the Devices tab moves to the apps", 1, tabs.getSelectedTabPosition());
        assertEquals(1, list.getAdapter().getItemCount());
        assertEquals(activity.getString(R.string.lan_sync_bucket_newer, 1), newer.getText().toString());
        assertEquals(activity.getString(R.string.lan_sync_bucket_not_installed, 1), notInstalled.getText().toString());
        MaterialButton updateAll = activity.findViewById(R.id.update_all);
        assertEquals("Update all still means every newer app",
                activity.getString(R.string.lan_sync_update_all, 2), updateAll.getText().toString());

        // A version number works as well, and the chips still pick the group
        searchView.setQuery("v4", false);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, list.getAdapter().getItemCount());
        notInstalled.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, list.getAdapter().getItemCount());
        TextView emptyTitle = activity.findViewById(android.R.id.empty).findViewById(R.id.empty_state_title);
        assertEquals(activity.getString(R.string.no_matching_package_found), emptyTitle.getText().toString());

        searchItem.collapseActionView();
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(viewModel.getSearchQuery());
        assertEquals(1, list.getAdapter().getItemCount());
    }

    @Test
    public void comingBackFromTheInstallerMovesTheAppOutOfNotInstalled() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        Map<String, SyncApp> installed = new ConcurrentHashMap<>();
        viewModel.setLocalIndex(() -> new HashMap<>(installed));
        SyncApp remote = SyncFixtures.app("com.example.a", 5, SIGNER_A);
        viewModel.setRows(Collections.singletonList(new LanSyncViewModel.AppRow(remote, null)));
        shadowOf(Looper.getMainLooper()).idle();
        Chip notInstalled = activity.findViewById(R.id.chip_not_installed);
        assertEquals(activity.getString(R.string.lan_sync_bucket_not_installed, 1), notInstalled.getText().toString());

        // Get hands the app to the installer, which covers this screen and puts it on
        mController.pause().stop();
        installed.put("com.example.a", SyncFixtures.app("com.example.a", 5, SIGNER_A));
        mController.restart().resume();

        List<LanSyncViewModel.AppRow> rows = waitFor(() -> {
            List<LanSyncViewModel.AppRow> shown = viewModel.getRows().getValue();
            return shown != null && shown.get(0).status == SyncComparator.Status.SAME ? shown : null;
        });
        assertEquals(1, rows.size());
        assertEquals(activity.getString(R.string.lan_sync_bucket_not_installed, 0), notInstalled.getText().toString());
    }

    @Test
    public void anInstallBehindTheInstallerDialogMovesTheAppOutOfNotInstalled() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        Map<String, SyncApp> installed = new ConcurrentHashMap<>();
        viewModel.setLocalIndex(() -> new HashMap<>(installed));
        SyncApp remote = SyncFixtures.app("com.example.a", 5, SIGNER_A);
        viewModel.setRows(Collections.singletonList(new LanSyncViewModel.AppRow(remote, null)));
        shadowOf(Looper.getMainLooper()).idle();
        Chip notInstalled = activity.findViewById(R.id.chip_not_installed);
        assertEquals(activity.getString(R.string.lan_sync_bucket_not_installed, 1), notInstalled.getText().toString());

        // The installer is a dialog: this screen is only paused behind it, never stopped
        mController.pause();
        installed.put("com.example.a", SyncFixtures.app("com.example.a", 5, SIGNER_A));
        Intent added = packageAdded("com.example.a");
        ApplicationProvider.getApplicationContext().sendBroadcast(added);
        mController.resume();

        List<LanSyncViewModel.AppRow> rows = waitFor(() -> {
            List<LanSyncViewModel.AppRow> shown = viewModel.getRows().getValue();
            return shown != null && shown.get(0).status == SyncComparator.Status.SAME ? shown : null;
        });
        assertEquals(1, rows.size());
        assertEquals(activity.getString(R.string.lan_sync_bucket_not_installed, 0), notInstalled.getText().toString());

        // Out of sight it stops listening
        assertTrue(listensForInstalls(added));
        mController.pause().stop();
        assertFalse(listensForInstalls(added));
    }

    @Test
    public void disconnectSaysGoodbyeOffTheMainThread() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        List<Runnable> closes = Collections.synchronizedList(new ArrayList<>());
        viewModel.setCloser(closes::add);
        CountDownLatch ended = new CountDownLatch(1);
        SyncFixtures.Catalog catalog = new SyncFixtures.Catalog();
        catalog.apps.add(SyncFixtures.app("com.example.a", 5, SIGNER_A));
        LanSyncHost host = new LanSyncHost(null, "Other phone", LocalIdentity.generate(new SeededRandom(91)),
                new SyncFixtures.MemoryTrust(), catalog, new SeededRandom(92), new LanSyncHost.Listener() {
            @Override
            public void onApprovalRequest(@Nullable ApprovalGate.Request request) {
                if (request != null) {
                    request.decide(ApprovalGate.Decision.ALLOW);
                }
            }

            @Override
            public void onClientAccepted(@NonNull String peerName, @NonNull String peerFingerprint) {
            }

            @Override
            public void onFileSent(@NonNull String packageName, @NonNull String fileName) {
            }

            @Override
            public void onSessionEnded(@Nullable String error) {
                ended.countDown();
            }

            @Override
            public void onHostFailed(@NonNull String error) {
            }
        });
        try {
            int port = host.start();
            viewModel.connect(InetAddress.getLoopbackAddress(), port, "Other phone");
            waitFor(() -> {
                LanSyncViewModel.Connection connection = viewModel.getConnection().getValue();
                return connection != null && connection.step == LanSyncViewModel.Connection.CONNECTED
                        ? connection : null;
            });

            // Android throws when the main thread writes to a socket, which crashed the app here
            activity.findViewById(R.id.disconnect).performClick();

            assertEquals(LanSyncViewModel.Connection.IDLE, viewModel.getConnection().getValue().step);
            assertEquals("The goodbye goes to a background thread", 1, closes.size());
            assertFalse("Nothing was sent from the main thread", ended.await(300, TimeUnit.MILLISECONDS));
            Thread worker = new Thread(closes.get(0));
            worker.start();
            worker.join(TimeUnit.NANOSECONDS.toMillis(WAIT_NANOS));
            assertTrue("The other device sees the session end", ended.await(10, TimeUnit.SECONDS));
        } finally {
            host.stop();
        }
    }

    @Test
    public void replacingUninstallsTheOldCopyBeforeTheInstallerGetsTheNewOne() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        List<String> uninstalled = Collections.synchronizedList(new ArrayList<>());
        List<Object> readyDuringUninstall = Collections.synchronizedList(new ArrayList<>());
        viewModel.setUninstaller((packageName, label) -> {
            uninstalled.add(packageName);
            readyDuringUninstall.add(String.valueOf(viewModel.getReady().getValue()));
            return null;
        }, Runnable::run);
        SyncFixtures.Catalog catalog = new SyncFixtures.Catalog();
        LanSyncHost host = textLauncherHost(viewModel, catalog);
        try {
            LanSyncViewModel.AppRow row = connectAndWaitForRow(viewModel, host.start());
            assertEquals(SyncComparator.Status.SIGNATURE_DIFFERS, row.status);
            ((Chip) activity.findViewById(R.id.chip_signature)).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            MaterialSwitch allowReplace = activity.findViewById(R.id.allow_replace);
            assertEquals(View.VISIBLE, allowReplace.getVisibility());
            assertFalse("Replacing is off until asked for", allowReplace.isChecked());
            assertEquals(View.GONE, rowButton(activity).getVisibility());

            // A Replace button left over from before the switch went off does nothing
            LanSyncAppsAdapter adapter = (LanSyncAppsAdapter) ((RecyclerView) activity.findViewById(
                    R.id.recycler_view)).getAdapter();
            adapter.setAllowReplace(true);
            rowButton(activity).performClick();
            Thread.sleep(300);
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(catalog.fileLookups.isEmpty());
            assertTrue(uninstalled.isEmpty());
            adapter.setAllowReplace(false);

            allowReplace.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(new LanSyncSettings(activity).isAllowReplace());
            MaterialButton replace = rowButton(activity);
            assertEquals(activity.getString(R.string.lan_sync_replace), replace.getText().toString());
            assertTrue(replace.isEnabled());
            replace.performClick();

            Intent started = waitFor(() -> shadowOf(activity).getNextStartedActivity());
            assertEquals(Collections.singletonList("ademar.textlauncher"), uninstalled);
            assertEquals("The installer waits for the uninstall", Collections.singletonList("null"),
                    readyDuringUninstall);
            assertEquals(PackageInstallerActivity.class.getName(), started.getComponent().getClassName());
            assertNull(ShadowToast.getTextOfLatestToast());
        } finally {
            host.stop();
            LanSyncInstallTracker.resetForTesting();
        }
    }

    @Test
    public void aFailedUninstallKeepsTheOldAppAndSaysWhy() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        List<String> uninstalled = Collections.synchronizedList(new ArrayList<>());
        viewModel.setUninstaller((packageName, label) -> {
            uninstalled.add(packageName);
            return "DELETE_FAILED_INTERNAL_ERROR";
        }, Runnable::run);
        SyncFixtures.Catalog catalog = new SyncFixtures.Catalog();
        LanSyncHost host = textLauncherHost(viewModel, catalog);
        try {
            LanSyncViewModel.AppRow row = connectAndWaitForRow(viewModel, host.start());
            viewModel.replace(row);

            String toast = waitFor(() -> ShadowToast.getTextOfLatestToast());
            assertEquals(activity.getString(R.string.lan_sync_replace_uninstall_failed, "Text Launcher",
                    "DELETE_FAILED_INTERNAL_ERROR"), toast);
            assertEquals(Collections.singletonList("ademar.textlauncher"), uninstalled);
            assertNull("Nothing goes to the installer", shadowOf(activity).getNextStartedActivity());
            waitFor(() -> viewModel.getTask().getValue() == null ? Boolean.TRUE : null);
            // The view model's Application can outlive a Robolectric test, so ask it for the folder
            ReceivedApkStore.Entry received = ReceivedApkStore.of(viewModel.getApplication())
                    .find(ReceivedApkStore.keyOf(row.remote));
            assertNotNull("The new copy waits in Received APKs", received);
            assertTrue(received.complete);
            assertTrue(received.hasAllFiles());
        } finally {
            host.stop();
            LanSyncInstallTracker.resetForTesting();
        }
    }

    /**
     * Lists the text launcher fixture signed by its own key, while the copy installed here is
     * signed by another one.
     */
    @NonNull
    private static LanSyncHost textLauncherHost(@NonNull LanSyncViewModel viewModel,
                                                @NonNull SyncFixtures.Catalog catalog) throws Exception {
        File apk = SyncFixtures.resource(ApkSignatureGateTest.TEXTLAUNCHER);
        catalog.add(new SyncApp("ademar.textlauncher", "Text Launcher", 5, "5", false,
                Collections.singletonList(ApkSignatureGateTest.TEXTLAUNCHER_SIGNER), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", apk.length()))), apk);
        SyncApp here = SyncFixtures.app("ademar.textlauncher", 3, ApkSignatureGateTest.DNSFILTER_SIGNER);
        viewModel.setLocalIndex(() -> new HashMap<>(Collections.singletonMap(here.packageName, here)));
        viewModel.setInstalledLookup(packageName -> here.packageName.equals(packageName) ? here : null);
        return new LanSyncHost(null, "Other phone", LocalIdentity.generate(new SeededRandom(93)),
                new SyncFixtures.MemoryTrust(), catalog, new SeededRandom(94), new LanSyncHost.Listener() {
            @Override
            public void onApprovalRequest(@Nullable ApprovalGate.Request request) {
                if (request != null) {
                    request.decide(ApprovalGate.Decision.ALLOW);
                }
            }

            @Override
            public void onClientAccepted(@NonNull String peerName, @NonNull String peerFingerprint) {
            }

            @Override
            public void onFileSent(@NonNull String packageName, @NonNull String fileName) {
            }

            @Override
            public void onSessionEnded(@Nullable String error) {
            }

            @Override
            public void onHostFailed(@NonNull String error) {
            }
        });
    }

    @NonNull
    private static LanSyncViewModel.AppRow connectAndWaitForRow(@NonNull LanSyncViewModel viewModel, int port)
            throws Exception {
        viewModel.connect(InetAddress.getLoopbackAddress(), port, "Other phone");
        return waitFor(() -> {
            List<LanSyncViewModel.AppRow> rows = viewModel.getRows().getValue();
            return rows != null && !rows.isEmpty() ? rows.get(0) : null;
        });
    }

    @NonNull
    private static MaterialButton rowButton(@NonNull LanSyncActivity activity) {
        RecyclerView list = activity.findViewById(R.id.recycler_view);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        return ((LanSyncAppsAdapter.ViewHolder) holder).pull;
    }

    @NonNull
    private static Intent packageAdded(@NonNull String packageName) {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED, Uri.fromParts("package", packageName, null));
        intent.putExtra(Intent.EXTRA_UID, 10123);
        return intent;
    }

    private static boolean listensForInstalls(@NonNull Intent intent) {
        Application app = ApplicationProvider.getApplicationContext();
        for (BroadcastReceiver receiver : shadowOf(app).getReceiversForIntent(intent)) {
            if (receiver instanceof PackageChangeReceiver) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static <T> T waitFor(@NonNull Callable<T> probe) throws Exception {
        long deadline = System.nanoTime() + WAIT_NANOS;
        while (true) {
            shadowOf(Looper.getMainLooper()).idle();
            T value = probe.call();
            if (value != null) {
                return value;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting");
            }
            Thread.sleep(20);
        }
    }

    private static boolean isEmpty(@Nullable List<?> list) {
        return list == null || list.isEmpty();
    }

    private static void assertRefused(int port) {
        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
            }
        });
    }
}
