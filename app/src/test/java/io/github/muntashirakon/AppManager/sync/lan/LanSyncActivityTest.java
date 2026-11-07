// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

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
