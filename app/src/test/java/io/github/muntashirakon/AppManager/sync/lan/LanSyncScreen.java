// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;

/**
 * Drives the Local sync screen in tests: a host on this machine that lets every device in, and
 * waits that keep the main looper turning.
 */
final class LanSyncScreen {
    private static final long WAIT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private LanSyncScreen() {
    }

    /**
     * A host that allows every connection and lists the catalog's apps.
     */
    @NonNull
    static LanSyncHost allowingHost(@NonNull SyncFixtures.Catalog catalog, long seed) throws GeneralSecurityException {
        return new LanSyncHost(null, "Other phone", LocalIdentity.generate(new SeededRandom(seed)),
                new SyncFixtures.MemoryTrust(), catalog, new SeededRandom(seed + 1), new LanSyncHost.Listener() {
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
    static LanSyncViewModel.AppRow connectAndWaitForRow(@NonNull LanSyncViewModel viewModel, int port)
            throws Exception {
        viewModel.connect(InetAddress.getLoopbackAddress(), port, "Other phone");
        return waitFor(() -> {
            List<LanSyncViewModel.AppRow> rows = viewModel.getRows().getValue();
            return rows != null && !rows.isEmpty() ? rows.get(0) : null;
        });
    }

    /**
     * The first row's holder, after laying the list out.
     */
    @NonNull
    static LanSyncAppsAdapter.ViewHolder firstRow(@NonNull LanSyncActivity activity) {
        RecyclerView list = activity.findViewById(R.id.recycler_view);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        return (LanSyncAppsAdapter.ViewHolder) holder;
    }

    @NonNull
    static <T> T waitFor(@NonNull Callable<T> probe) throws Exception {
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
}
