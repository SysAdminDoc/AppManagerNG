// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * Local sync between two devices on the same Wi-Fi. Sharing and the search for nearby devices only
 * run while this screen is in front: both stop in {@link #onStop()}. A connection to another device
 * stays open so the installer can come and go.
 */
public class LanSyncActivity extends BaseActivity {
    private static final String STATE_TAB = "tab";
    private static final int TAB_DEVICES = 0;
    private static final int TAB_APPS = 1;
    private static final int KEY_CHARS_SHOWN = 16;

    private final ActivityResultLauncher<String> mPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                updatePermissionCard();
                if (isStarted()) {
                    startDiscoveryIfAllowed();
                }
            });

    @Nullable
    private LanSyncViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private TabLayout mTabs;
    private View mDevicesPane;
    private View mAppsPane;
    private View mPermissionCard;
    private MaterialSwitch mShareSwitch;
    private TextView mShareStatus;
    private View mApprovalPanel;
    private TextView mApprovalTitle;
    private TextView mApprovalCode;
    private MaterialCheckBox mApprovalRemember;
    private TextView mConnectionStatus;
    private TextView mConnectionCode;
    private MaterialButton mDisconnect;
    private LinearLayout mPeersContainer;
    private TextView mPeersEmpty;
    private TextInputLayout mManualAddressLayout;
    private TextInputEditText mManualAddress;
    private MaterialButton mManualConnect;
    private LinearLayout mTrustedContainer;
    private TextView mTrustedEmpty;
    private TextView mAppsSummary;
    private final Map<SyncComparator.Status, Chip> mChips = new EnumMap<>(SyncComparator.Status.class);
    private MaterialButton mUpdateAll;
    private TextView mEmptyTitle;
    private LanSyncAppsAdapter mAdapter;

    private boolean mUpdatingSwitch;
    private boolean mPermissionMissing;
    @NonNull
    private SyncComparator.Status mFilter = SyncComparator.Status.NEWER;
    @Nullable
    private String mTask;
    @Nullable
    private PackageWatcher mPackageWatcher;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_lan_sync);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.lan_sync_title);
        }
        mViewModel = new ViewModelProvider(this).get(LanSyncViewModel.class);
        if (savedInstanceState == null) {
            mViewModel.cleanUpDownloads();
        }

        mProgress = findViewById(R.id.progress_linear);
        mProgress.setVisibilityAfterHide(View.GONE);
        mProgress.hide();
        mDevicesPane = findViewById(R.id.devices_pane);
        mAppsPane = findViewById(R.id.apps_pane);
        mTabs = findViewById(R.id.tab_layout);
        mTabs.addTab(mTabs.newTab().setText(R.string.lan_sync_tab_devices));
        mTabs.addTab(mTabs.newTab().setText(R.string.lan_sync_tab_apps));
        mTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPane(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        int tab = savedInstanceState == null ? TAB_DEVICES : savedInstanceState.getInt(STATE_TAB, TAB_DEVICES);
        selectTab(tab);
        showPane(tab);

        // Devices
        mPermissionCard = findViewById(R.id.permission_card);
        findViewById(R.id.permission_allow).setOnClickListener(v ->
                mPermissionLauncher.launch(ManifestCompat.permission.ACCESS_LOCAL_NETWORK));
        mShareSwitch = findViewById(R.id.share_switch);
        mShareSwitch.setOnCheckedChangeListener(this::onShareSwitchChanged);
        mShareStatus = findViewById(R.id.share_status);
        mApprovalPanel = findViewById(R.id.approval_panel);
        mApprovalTitle = findViewById(R.id.approval_title);
        mApprovalCode = findViewById(R.id.approval_code);
        mApprovalRemember = findViewById(R.id.approval_remember);
        findViewById(R.id.approval_allow).setOnClickListener(v -> decide(mApprovalRemember.isChecked()
                ? ApprovalGate.Decision.ALLOW_AND_REMEMBER : ApprovalGate.Decision.ALLOW));
        findViewById(R.id.approval_deny).setOnClickListener(v -> decide(ApprovalGate.Decision.DENY));
        mConnectionStatus = findViewById(R.id.connection_status);
        mConnectionCode = findViewById(R.id.connection_code);
        mDisconnect = findViewById(R.id.disconnect);
        mDisconnect.setOnClickListener(v -> mViewModel.disconnect());
        mPeersContainer = findViewById(R.id.peers_container);
        mPeersEmpty = findViewById(R.id.peers_empty);
        mManualAddressLayout = findViewById(R.id.manual_address_layout);
        mManualAddress = findViewById(R.id.manual_address);
        mManualAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connectManually();
                return true;
            }
            return false;
        });
        mManualConnect = findViewById(R.id.manual_connect);
        mManualConnect.setOnClickListener(v -> connectManually());
        mTrustedContainer = findViewById(R.id.trusted_container);
        mTrustedEmpty = findViewById(R.id.trusted_empty);

        // Apps
        mAppsSummary = findViewById(R.id.apps_summary);
        MaterialSwitch showSystem = findViewById(R.id.show_system);
        showSystem.setChecked(mViewModel.isShowingSystemApps());
        showSystem.setOnCheckedChangeListener((button, checked) -> mViewModel.setShowSystemApps(checked));
        mChips.put(SyncComparator.Status.NEWER, findViewById(R.id.chip_newer));
        mChips.put(SyncComparator.Status.NOT_INSTALLED, findViewById(R.id.chip_not_installed));
        mChips.put(SyncComparator.Status.SIGNATURE_DIFFERS, findViewById(R.id.chip_signature));
        mChips.put(SyncComparator.Status.SAME, findViewById(R.id.chip_same));
        mChips.put(SyncComparator.Status.OLDER, findViewById(R.id.chip_older));
        ChipGroup chipGroup = findViewById(R.id.bucket_chips);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            mFilter = statusForChip(checkedIds.isEmpty() ? R.id.chip_newer : checkedIds.get(0));
            renderRows();
        });
        mFilter = statusForChip(chipGroup.getCheckedChipId());
        mUpdateAll = findViewById(R.id.update_all);
        mUpdateAll.setOnClickListener(v -> mViewModel.pullAllNewer());
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        View emptyState = findViewById(android.R.id.empty);
        ((ImageView) emptyState.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_sync);
        mEmptyTitle = emptyState.findViewById(R.id.empty_state_title);
        emptyState.findViewById(R.id.empty_state_summary).setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        recyclerView.setEmptyView(emptyState);
        mAdapter = new LanSyncAppsAdapter(row -> mViewModel.pull(row));
        recyclerView.setAdapter(mAdapter);

        mViewModel.getSharing().observe(this, sharing -> setShareSwitch(Boolean.TRUE.equals(sharing)));
        mViewModel.getHostStatus().observe(this, status -> {
            if (status == null) {
                mShareStatus.setText(R.string.lan_sync_share_summary);
            } else {
                mShareStatus.setText(status);
            }
        });
        mViewModel.getApproval().observe(this, this::renderApproval);
        mViewModel.getPeers().observe(this, peers -> renderPeers());
        mViewModel.getTrusted().observe(this, this::renderTrusted);
        mViewModel.getConnection().observe(this, this::renderConnection);
        mViewModel.getRows().observe(this, rows -> renderRows());
        mViewModel.getTask().observe(this, task -> {
            mTask = task;
            updateProgress();
            renderRows();
        });
        mViewModel.getMessage().observe(this, message -> {
            if (message != null) {
                UIUtils.displayLongToast(message);
                mViewModel.onMessageShown();
            }
        });
        mViewModel.getReady().observe(this, this::handOffToInstaller);

        mViewModel.loadTrusted();
        updatePermissionCard();
        if (isStarted()) {
            // Signed in after the screen was already showing
            startDiscoveryIfAllowed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mPackageWatcher == null) {
            // The installer only covers this screen as a dialog, so it is paused, never stopped:
            // the install itself says when an app moves out of the Get groups
            mPackageWatcher = new PackageWatcher();
        }
        if (mViewModel != null) {
            updatePermissionCard();
            startDiscoveryIfAllowed();
            // Back from somewhere else: anything installed or removed meanwhile
            mViewModel.recheckInstalled();
        }
    }

    @Override
    protected void onStop() {
        if (mPackageWatcher != null) {
            mPackageWatcher.close();
            mPackageWatcher = null;
        }
        if (mViewModel != null && !isChangingConfigurations()) {
            // Nothing is shared or searched for once the screen is out of sight. Turning the phone
            // only rebuilds the screen, and stopping then would drop the connected device.
            mViewModel.stopSharing();
            mViewModel.stopDiscovery();
            setShareSwitch(false);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_lan_sync_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_received_apks) {
            startActivity(new Intent(this, ReceivedApksActivity.class));
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mTabs != null) {
            outState.putInt(STATE_TAB, mTabs.getSelectedTabPosition());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private void onShareSwitchChanged(@NonNull CompoundButton button, boolean checked) {
        if (mUpdatingSwitch || mViewModel == null) {
            return;
        }
        if (checked) {
            mViewModel.startSharing();
        } else {
            mViewModel.stopSharing();
        }
    }

    private void setShareSwitch(boolean checked) {
        mUpdatingSwitch = true;
        try {
            mShareSwitch.setChecked(checked);
        } finally {
            mUpdatingSwitch = false;
        }
    }

    private void updatePermissionCard() {
        mPermissionMissing = Ops.isLocalNetworkPermissionMissing(this);
        mPermissionCard.setVisibility(mPermissionMissing ? View.VISIBLE : View.GONE);
        mShareSwitch.setEnabled(!mPermissionMissing);
        updateConnectControls();
        renderPeers();
    }

    /**
     * Call only while the screen is started. Inside {@link #onStart()} the lifecycle still reads
     * CREATED, so the caller decides rather than this method.
     */
    private void startDiscoveryIfAllowed() {
        if (mViewModel != null && !mPermissionMissing) {
            mViewModel.startDiscovery();
        }
    }

    private boolean isStarted() {
        return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private void selectTab(int position) {
        TabLayout.Tab tab = mTabs.getTabAt(position);
        if (tab != null && !tab.isSelected()) {
            tab.select();
        }
    }

    private void showPane(int position) {
        mDevicesPane.setVisibility(position == TAB_APPS ? View.GONE : View.VISIBLE);
        mAppsPane.setVisibility(position == TAB_APPS ? View.VISIBLE : View.GONE);
    }

    // Sharing this device

    private void renderApproval(@Nullable ApprovalGate.Request request) {
        if (request == null || request.isSettled()) {
            mApprovalPanel.setVisibility(View.GONE);
            return;
        }
        mApprovalTitle.setText(getString(R.string.lan_sync_approval_title, request.peerName));
        mApprovalCode.setText(LanSyncViewModel.formatSas(request.sas));
        if (mApprovalPanel.getVisibility() != View.VISIBLE) {
            mApprovalRemember.setChecked(false);
            mApprovalPanel.setVisibility(View.VISIBLE);
        }
    }

    private void decide(@NonNull ApprovalGate.Decision decision) {
        ApprovalGate.Request request = mViewModel.getApproval().getValue();
        if (request != null) {
            mViewModel.decide(request, decision);
        }
        mApprovalPanel.setVisibility(View.GONE);
    }

    private void renderTrusted(@Nullable List<LanSyncViewModel.TrustedDevice> devices) {
        mTrustedContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (devices != null) {
            for (LanSyncViewModel.TrustedDevice device : devices) {
                View row = inflateDeviceRow(inflater, mTrustedContainer, device.name,
                        getString(R.string.lan_sync_device_key, shortKey(device.fingerprint)));
                MaterialButton action = row.findViewById(R.id.action);
                action.setText(R.string.lan_sync_forget);
                action.setOnClickListener(v -> mViewModel.forget(device));
                mTrustedContainer.addView(row);
            }
        }
        mTrustedEmpty.setVisibility(devices == null || devices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Talking to another device

    private void renderPeers() {
        mPeersContainer.removeAllViews();
        List<LanSyncDiscovery.Peer> peers = mViewModel.getPeers().getValue();
        boolean canConnect = isIdle() && !mPermissionMissing;
        LayoutInflater inflater = LayoutInflater.from(this);
        if (peers != null) {
            for (LanSyncDiscovery.Peer peer : peers) {
                View row = inflateDeviceRow(inflater, mPeersContainer, peer.name, describe(peer.address, peer.port));
                MaterialButton action = row.findViewById(R.id.action);
                action.setText(R.string.lan_sync_connect);
                action.setEnabled(canConnect);
                action.setOnClickListener(v -> mViewModel.connect(peer.address, peer.port, peer.name));
                mPeersContainer.addView(row);
            }
        }
        boolean none = peers == null || peers.isEmpty();
        mPeersEmpty.setVisibility(none && !mPermissionMissing ? View.VISIBLE : View.GONE);
    }

    /**
     * Only a numeric address on the local network is accepted. A name is never looked up, so typing
     * one cannot send a DNS query anywhere.
     */
    private void connectManually() {
        Editable editable = mManualAddress.getText();
        String text = editable == null ? "" : editable.toString().trim();
        LanAddresses.HostPort hostPort = LanAddresses.parse(text);
        InetAddress address = hostPort == null ? null : LanAddresses.toAddress(hostPort.host);
        if (hostPort == null || address == null) {
            mManualAddressLayout.setError(getString(R.string.lan_sync_address_invalid));
            return;
        }
        if (!LanAddresses.isLocal(address)) {
            mManualAddressLayout.setError(getString(R.string.lan_sync_address_not_local));
            return;
        }
        mManualAddressLayout.setError(null);
        mViewModel.connect(address, hostPort.port, text);
    }

    private void renderConnection(@Nullable LanSyncViewModel.Connection connection) {
        int step = connection == null ? LanSyncViewModel.Connection.IDLE : connection.step;
        String peer = connection == null || connection.peerName == null ? "" : connection.peerName;
        switch (step) {
            case LanSyncViewModel.Connection.CONNECTING:
                mConnectionStatus.setText(getString(R.string.lan_sync_connecting, peer));
                break;
            case LanSyncViewModel.Connection.WAITING:
                mConnectionStatus.setText(getString(R.string.lan_sync_waiting_for_approval, peer));
                break;
            case LanSyncViewModel.Connection.CONNECTED:
                mConnectionStatus.setText(getString(R.string.lan_sync_connected, peer));
                break;
            case LanSyncViewModel.Connection.IDLE:
            default:
                mConnectionStatus.setText(R.string.lan_sync_connect_summary);
                break;
        }
        boolean showCode = step == LanSyncViewModel.Connection.WAITING && connection.sas != null;
        mConnectionCode.setVisibility(showCode ? View.VISIBLE : View.GONE);
        if (showCode) {
            mConnectionCode.setText(LanSyncViewModel.formatSas(connection.sas));
        }
        mDisconnect.setVisibility(step == LanSyncViewModel.Connection.IDLE ? View.GONE : View.VISIBLE);
        updateConnectControls();
        updateProgress();
        renderPeers();
        renderRows();
        if (step == LanSyncViewModel.Connection.CONNECTED && mTabs.getSelectedTabPosition() != TAB_APPS
                && mViewModel.getRows().getValue() == null) {
            // Just connected: the list is what people came for
            selectTab(TAB_APPS);
        }
    }

    private void updateConnectControls() {
        boolean enabled = isIdle() && !mPermissionMissing;
        mManualAddressLayout.setEnabled(enabled);
        mManualConnect.setEnabled(enabled);
    }

    private boolean isIdle() {
        LanSyncViewModel.Connection connection = mViewModel.getConnection().getValue();
        return connection == null || connection.step == LanSyncViewModel.Connection.IDLE;
    }

    // The other device's apps

    private void renderRows() {
        List<LanSyncViewModel.AppRow> rows = mViewModel.getRows().getValue();
        Map<SyncComparator.Status, Integer> counts = new EnumMap<>(SyncComparator.Status.class);
        for (SyncComparator.Status status : SyncComparator.Status.values()) {
            counts.put(status, 0);
        }
        List<LanSyncViewModel.AppRow> shown = new ArrayList<>();
        if (rows != null) {
            for (LanSyncViewModel.AppRow row : rows) {
                counts.put(row.status, counts.get(row.status) + 1);
                if (row.status == mFilter) {
                    shown.add(row);
                }
            }
        }
        mChips.get(SyncComparator.Status.NEWER).setText(getString(R.string.lan_sync_bucket_newer,
                counts.get(SyncComparator.Status.NEWER)));
        mChips.get(SyncComparator.Status.NOT_INSTALLED).setText(getString(R.string.lan_sync_bucket_not_installed,
                counts.get(SyncComparator.Status.NOT_INSTALLED)));
        mChips.get(SyncComparator.Status.SIGNATURE_DIFFERS).setText(getString(R.string.lan_sync_bucket_signature,
                counts.get(SyncComparator.Status.SIGNATURE_DIFFERS)));
        mChips.get(SyncComparator.Status.SAME).setText(getString(R.string.lan_sync_bucket_same,
                counts.get(SyncComparator.Status.SAME)));
        mChips.get(SyncComparator.Status.OLDER).setText(getString(R.string.lan_sync_bucket_older,
                counts.get(SyncComparator.Status.OLDER)));

        boolean idle = mTask == null;
        int newer = counts.get(SyncComparator.Status.NEWER);
        mUpdateAll.setVisibility(rows != null && newer > 0 ? View.VISIBLE : View.GONE);
        mUpdateAll.setText(getString(R.string.lan_sync_update_all, newer));
        mUpdateAll.setEnabled(idle);

        if (mTask != null) {
            mAppsSummary.setText(mTask);
        } else if (rows == null) {
            mAppsSummary.setText(R.string.lan_sync_apps_not_connected);
        } else {
            LanSyncViewModel.Connection connection = mViewModel.getConnection().getValue();
            String peer = connection == null || connection.peerName == null ? "" : connection.peerName;
            mAppsSummary.setText(getString(R.string.lan_sync_apps_summary, peer, rows.size()));
        }
        mEmptyTitle.setText(rows == null ? R.string.lan_sync_apps_not_connected : R.string.lan_sync_apps_empty_bucket);
        mAdapter.setRows(shown, idle);
    }

    private void updateProgress() {
        LanSyncViewModel.Connection connection = mViewModel.getConnection().getValue();
        boolean connecting = connection != null && (connection.step == LanSyncViewModel.Connection.CONNECTING
                || connection.step == LanSyncViewModel.Connection.WAITING);
        if (mTask != null || connecting) {
            mProgress.show();
        } else {
            mProgress.hide();
        }
    }

    /**
     * Every download here already matched the signer list and the installed copy. A bundle is a
     * temporary file the installer owns and deletes when it is done. A single APK is the received
     * copy itself, which stays for Received APKs until it's cleaned up.
     */
    private void handOffToInstaller(@Nullable List<LanSyncDownloader.Result> ready) {
        if (ready == null || ready.isEmpty()) {
            return;
        }
        mViewModel.onInstallHandedOff();
        if (ready.size() == 1) {
            startActivity(PackageInstallerActivity.getLaunchableInstance(this, sourceOf(ready.get(0))));
            return;
        }
        // The installer runs one at a time and queues the rest as they arrive
        for (LanSyncDownloader.Result result : ready) {
            startActivity(PackageInstallerActivity.getBatchInstallInstance(this, sourceOf(result)));
        }
    }

    @NonNull
    static ApkSource sourceOf(@NonNull LanSyncDownloader.Result result) {
        return result.owned ? ApkSource.getOwnedCachedApkSource(result.file, result.mimeType)
                : ApkSource.getCachedApkSource(Uri.fromFile(result.file), result.mimeType);
    }

    @NonNull
    private static View inflateDeviceRow(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent,
                                         @NonNull String name, @NonNull String detail) {
        View row = inflater.inflate(R.layout.item_lan_sync_device, parent, false);
        ((TextView) row.findViewById(R.id.name)).setText(name);
        ((TextView) row.findViewById(R.id.detail)).setText(detail);
        return row;
    }

    @NonNull
    private static SyncComparator.Status statusForChip(int chipId) {
        if (chipId == R.id.chip_not_installed) {
            return SyncComparator.Status.NOT_INSTALLED;
        } else if (chipId == R.id.chip_signature) {
            return SyncComparator.Status.SIGNATURE_DIFFERS;
        } else if (chipId == R.id.chip_same) {
            return SyncComparator.Status.SAME;
        } else if (chipId == R.id.chip_older) {
            return SyncComparator.Status.OLDER;
        }
        return SyncComparator.Status.NEWER;
    }

    @NonNull
    static String describe(@NonNull InetAddress address, int port) {
        String host = address.getHostAddress();
        return address instanceof Inet6Address ? "[" + host + "]:" + port : host + ":" + port;
    }

    /**
     * The start of a key fingerprint in groups of four, enough to tell two devices apart.
     */
    @NonNull
    static String shortKey(@NonNull String fingerprint) {
        String start = fingerprint.length() > KEY_CHARS_SHOWN ? fingerprint.substring(0, KEY_CHARS_SHOWN) : fingerprint;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < start.length(); i += 4) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(start, i, Math.min(i + 4, start.length()));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Sorts the other device's apps again whenever an app is installed, updated or removed here
     * while this screen is showing.
     */
    private final class PackageWatcher extends PackageChangeReceiver {
        PackageWatcher() {
            super(LanSyncActivity.this);
        }

        @Override
        protected void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            runOnUiThread(() -> {
                if (mViewModel != null) {
                    mViewModel.recheckInstalled();
                }
            });
        }
    }
}
