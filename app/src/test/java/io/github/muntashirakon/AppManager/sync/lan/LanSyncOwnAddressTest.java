// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowNsdManager;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Next to the address field, this phone shows its own address so it can be typed on the other
 * phone (#22). The port exists only while sharing is on.
 */
@RunWith(RobolectricTestRunner.class)
public class LanSyncOwnAddressTest {
    private ActivityController<LanSyncActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(LanSyncActivity.class).setup();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
        ShadowNsdManager.reset();
    }

    @Test
    public void theAddressGetsItsPortOnceSharingIsOnAndCanBeCopied() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        viewModel.setOwnAddressSource(() -> Arrays.asList("192.168.1.23", "10.0.0.7"));
        viewModel.refreshOwnAddresses();
        TextView own = activity.findViewById(R.id.own_address);
        View copy = activity.findViewById(R.id.copy_own_address);

        String withoutPort = activity.getString(R.string.lan_sync_own_address_no_port, "192.168.1.23, 10.0.0.7");
        LanSyncScreen.waitFor(() -> withoutPort.equals(own.getText().toString()) ? Boolean.TRUE : null);
        assertEquals(View.VISIBLE, copy.getVisibility());

        MaterialSwitch share = activity.findViewById(R.id.share_switch);
        share.performClick();
        LanSyncHost host = LanSyncScreen.waitFor(viewModel::getHost);
        int port = LanSyncScreen.waitFor(() -> {
            Integer shared = viewModel.getSharedPort().getValue();
            return shared != null && shared > 0 ? shared : null;
        });
        assertTrue(host.isRunning());
        String withPort = activity.getString(R.string.lan_sync_own_address,
                "192.168.1.23:" + port + ", 10.0.0.7:" + port);
        LanSyncScreen.waitFor(() -> withPort.equals(own.getText().toString()) ? Boolean.TRUE : null);

        copy.performClick();
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("192.168.1.23:" + port, clip.getItemAt(0).getText().toString());

        share.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        LanSyncScreen.waitFor(() -> withoutPort.equals(own.getText().toString()) ? Boolean.TRUE : null);
    }

    @Test
    public void withoutANetworkItSaysSoAndOffersNothingToCopy() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        viewModel.setOwnAddressSource(Collections::emptyList);
        viewModel.refreshOwnAddresses();
        TextView own = activity.findViewById(R.id.own_address);

        String none = activity.getString(R.string.lan_sync_own_address_none);
        LanSyncScreen.waitFor(() -> none.equals(own.getText().toString()) ? Boolean.TRUE : null);
        assertEquals(View.GONE, activity.findViewById(R.id.copy_own_address).getVisibility());
    }
}
