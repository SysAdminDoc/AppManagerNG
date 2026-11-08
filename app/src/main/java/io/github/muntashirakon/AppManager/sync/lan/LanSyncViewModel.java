// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.app.Application;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Holds both sides of local sync for the screen: sharing this device (the host) and talking to
 * another one (the client). Sharing and discovery only run while the screen asks for them.
 */
public class LanSyncViewModel extends AndroidViewModel {
    public static final String TAG = LanSyncViewModel.class.getSimpleName();
    private static final long STALE_DOWNLOAD_MS = TimeUnit.DAYS.toMillis(1);
    private static final int MAX_DEVICE_NAME_CHARS = 63;

    public static final class Connection {
        public static final int IDLE = 0;
        public static final int CONNECTING = 1;
        public static final int WAITING = 2;
        public static final int CONNECTED = 3;

        static final Connection NONE = new Connection(IDLE, null, null);

        public final int step;
        @Nullable
        public final String peerName;
        @Nullable
        public final String sas;

        Connection(int step, @Nullable String peerName, @Nullable String sas) {
            this.step = step;
            this.peerName = peerName;
            this.sas = sas;
        }
    }

    public static final class AppRow {
        @NonNull
        public final SyncApp remote;
        @Nullable
        public final SyncApp local;
        @NonNull
        public final SyncComparator.Status status;

        AppRow(@NonNull SyncApp remote, @Nullable SyncApp local) {
            this.remote = remote;
            this.local = local;
            this.status = SyncComparator.compare(remote, local);
        }
    }

    /**
     * The apps installed on this device, keyed by package name.
     */
    @VisibleForTesting
    interface LocalIndex {
        @WorkerThread
        @NonNull
        Map<String, SyncApp> load();
    }

    public static final class TrustedDevice {
        @NonNull
        public final String fingerprint;
        @NonNull
        public final String name;

        TrustedDevice(@NonNull String fingerprint, @NonNull String name) {
            this.fingerprint = fingerprint;
            this.name = name;
        }
    }

    private final Object mLock = new Object();
    private final ExecutorService mHostExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService mClientExecutor = Executors.newSingleThreadScheduledExecutor();
    private final SecureRandom mRandom = new SecureRandom();
    private final TrustStore mTrust;
    @Nullable
    private final NsdManager mNsd;
    private final MutableLiveData<Boolean> mSharing = new MutableLiveData<>(false);
    private final MutableLiveData<String> mHostStatus = new MutableLiveData<>();
    private final MutableLiveData<ApprovalGate.Request> mApproval = new MutableLiveData<>();
    private final MutableLiveData<List<LanSyncDiscovery.Peer>> mPeers =
            new MutableLiveData<>(Collections.<LanSyncDiscovery.Peer>emptyList());
    private final MutableLiveData<List<TrustedDevice>> mTrusted = new MutableLiveData<>();
    private final MutableLiveData<Connection> mConnection = new MutableLiveData<>(Connection.NONE);
    private final MutableLiveData<List<AppRow>> mRows = new MutableLiveData<>();
    private final MutableLiveData<String> mTask = new MutableLiveData<>();
    private final MutableLiveData<String> mMessage = new MutableLiveData<>();
    private final MutableLiveData<List<LanSyncDownloader.Result>> mReady = new MutableLiveData<>();
    @Nullable
    private volatile LocalIdentity mIdentity;
    // Guarded by mLock
    @Nullable
    private LanSyncHost mHost;
    private int mHostPort;
    private int mShareGeneration;
    @Nullable
    private LanSyncDiscovery mDiscovery;
    private int mDiscoveryGeneration;
    @Nullable
    private LanSyncConnector mConnector;
    @Nullable
    private SyncClientSession mClient;
    @Nullable
    private ScheduledFuture<?> mPing;
    private boolean mShowSystem;
    @NonNull
    private volatile LocalIndex mLocalIndex;
    @NonNull
    private Executor mCloser = ThreadUtils::postOnBackgroundThread;

    public LanSyncViewModel(@NonNull Application application) {
        super(application);
        mTrust = new TrustStore(application);
        mNsd = (NsdManager) application.getSystemService(Context.NSD_SERVICE);
        mLocalIndex = () -> new AndroidPackageCatalog(application).indexInstalled();
    }

    @VisibleForTesting
    void setLocalIndex(@NonNull LocalIndex localIndex) {
        mLocalIndex = localIndex;
    }

    @VisibleForTesting
    void setCloser(@NonNull Executor closer) {
        mCloser = closer;
    }

    @VisibleForTesting
    @MainThread
    void setRows(@Nullable List<AppRow> rows) {
        mRows.setValue(rows);
    }

    @Override
    protected void onCleared() {
        stopSharing();
        stopDiscovery();
        disconnect();
        mHostExecutor.shutdownNow();
        mClientExecutor.shutdownNow();
    }

    @NonNull
    public LiveData<Boolean> getSharing() {
        return mSharing;
    }

    @NonNull
    public LiveData<String> getHostStatus() {
        return mHostStatus;
    }

    @NonNull
    public LiveData<ApprovalGate.Request> getApproval() {
        return mApproval;
    }

    @NonNull
    public LiveData<List<LanSyncDiscovery.Peer>> getPeers() {
        return mPeers;
    }

    @NonNull
    public LiveData<List<TrustedDevice>> getTrusted() {
        return mTrusted;
    }

    @NonNull
    public LiveData<Connection> getConnection() {
        return mConnection;
    }

    @NonNull
    public LiveData<List<AppRow>> getRows() {
        return mRows;
    }

    /**
     * What the client side is busy with, or {@code null} when it is idle.
     */
    @NonNull
    public LiveData<String> getTask() {
        return mTask;
    }

    /**
     * A one-off message for a toast. Call {@link #onMessageShown()} once it is on screen.
     */
    @NonNull
    public LiveData<String> getMessage() {
        return mMessage;
    }

    /**
     * Downloads that passed every check and wait for the installer. Call
     * {@link #onInstallHandedOff()} once they are handed over.
     */
    @NonNull
    public LiveData<List<LanSyncDownloader.Result>> getReady() {
        return mReady;
    }

    @MainThread
    public void onMessageShown() {
        mMessage.setValue(null);
    }

    @MainThread
    public void onInstallHandedOff() {
        mReady.setValue(null);
    }

    public boolean isShowingSystemApps() {
        synchronized (mLock) {
            return mShowSystem;
        }
    }

    /**
     * Clears downloads an earlier visit left behind, for example when the app was closed while the
     * installer was still open.
     */
    public void purgeStaleDownloads() {
        File dir = downloadDir();
        mHostExecutor.execute(() -> LanSyncDownloader.purgeStale(dir, STALE_DOWNLOAD_MS, System.currentTimeMillis()));
    }

    // Sharing this device

    @MainThread
    public void startSharing() {
        final int generation;
        synchronized (mLock) {
            if (mHost != null) {
                return;
            }
            generation = ++mShareGeneration;
        }
        mSharing.setValue(true);
        mHostStatus.setValue(getApplication().getString(R.string.lan_sync_share_starting));
        mHostExecutor.execute(() -> {
            try {
                LanSyncHost host = new LanSyncHost(mNsd, deviceName(getApplication()), identity(), mTrust,
                        new AndroidPackageCatalog(getApplication()), mRandom, new HostListener(generation));
                int port;
                synchronized (mLock) {
                    // Started under the lock so a stop either sees this host or cancels the start
                    if (generation != mShareGeneration) {
                        return;
                    }
                    // The same port as last time keeps other devices' cached announcement right
                    port = host.start(mTrust.getHostPort());
                    mHost = host;
                    mHostPort = port;
                }
                mTrust.setHostPort(port);
                mHostStatus.postValue(waitingStatus(port));
            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                Log.w(TAG, "Could not start sharing", e);
                synchronized (mLock) {
                    if (generation != mShareGeneration) {
                        return;
                    }
                    ++mShareGeneration;
                }
                mSharing.postValue(false);
                mHostStatus.postValue(null);
                mMessage.postValue(getApplication().getString(R.string.lan_sync_share_failed, describe(e)));
            }
        });
    }

    /**
     * Closes the listening socket, withdraws the announcement and turns down a pending request.
     */
    @MainThread
    public void stopSharing() {
        LanSyncHost host;
        synchronized (mLock) {
            ++mShareGeneration;
            host = mHost;
            mHost = null;
        }
        if (host != null) {
            host.stop();
        }
        mSharing.setValue(false);
        mHostStatus.setValue(null);
        mApproval.setValue(null);
    }

    @NonNull
    private String waitingStatus(int port) {
        return getApplication().getString(R.string.lan_sync_share_waiting, deviceName(getApplication()), port);
    }

    @VisibleForTesting
    @Nullable
    LanSyncHost getHost() {
        synchronized (mLock) {
            return mHost;
        }
    }

    @MainThread
    public void decide(@NonNull ApprovalGate.Request request, @NonNull ApprovalGate.Decision decision) {
        request.decide(decision);
        if (mApproval.getValue() == request) {
            mApproval.setValue(null);
        }
    }

    public void loadTrusted() {
        mHostExecutor.execute(() -> {
            List<TrustedDevice> devices = new ArrayList<>();
            for (Map.Entry<String, String> entry : mTrust.list().entrySet()) {
                devices.add(new TrustedDevice(entry.getKey(), entry.getValue()));
            }
            mTrusted.postValue(devices);
        });
    }

    public void forget(@NonNull TrustedDevice device) {
        mHostExecutor.execute(() -> {
            mTrust.forget(device.fingerprint);
            LanSyncHost host;
            synchronized (mLock) {
                host = mHost;
            }
            // Its open session would otherwise go on serving apps until it disconnects
            if (host != null) {
                host.endSessionWith(device.fingerprint);
            }
        });
        loadTrusted();
    }

    private final class HostListener implements LanSyncHost.Listener {
        private final int mGeneration;

        HostListener(int generation) {
            mGeneration = generation;
        }

        private boolean isCurrent() {
            synchronized (mLock) {
                return mGeneration == mShareGeneration;
            }
        }

        @Override
        public void onApprovalRequest(@Nullable ApprovalGate.Request request) {
            if (request == null || isCurrent()) {
                mApproval.postValue(request);
            }
        }

        @Override
        public void onClientAccepted(@NonNull String peerName, @NonNull String peerFingerprint) {
            if (isCurrent()) {
                mHostStatus.postValue(getApplication().getString(R.string.lan_sync_share_connected, peerName));
                loadTrusted();
            }
        }

        @Override
        public void onFileSent(@NonNull String packageName, @NonNull String fileName) {
        }

        @Override
        public void onSessionEnded(@Nullable String error) {
            if (!isCurrent()) {
                return;
            }
            LanSyncHost host;
            int port;
            synchronized (mLock) {
                host = mHost;
                port = mHostPort;
            }
            // The next device may need the port to type the address by hand
            mHostStatus.postValue(host == null ? null : waitingStatus(port));
            if (error != null) {
                mMessage.postValue(getApplication().getString(R.string.lan_sync_share_session_ended, error));
            }
        }

        @Override
        public void onHostFailed(@NonNull String error) {
            LanSyncHost host;
            synchronized (mLock) {
                if (mGeneration != mShareGeneration) {
                    return;
                }
                ++mShareGeneration;
                host = mHost;
                mHost = null;
            }
            if (host != null) {
                host.stop();
            }
            mSharing.postValue(false);
            mHostStatus.postValue(null);
            mApproval.postValue(null);
            mMessage.postValue(getApplication().getString(R.string.lan_sync_share_failed, error));
        }
    }

    // Finding devices

    @MainThread
    public void startDiscovery() {
        if (mNsd == null) {
            return;
        }
        final int generation;
        synchronized (mLock) {
            if (mDiscovery != null) {
                return;
            }
            generation = ++mDiscoveryGeneration;
        }
        mHostExecutor.execute(() -> {
            try {
                LanSyncDiscovery discovery = new LanSyncDiscovery(mNsd, identity().getFingerprint(),
                        new LanSyncDiscovery.Listener() {
                            @Override
                            public void onPeersChanged(@NonNull List<LanSyncDiscovery.Peer> peers) {
                                if (isDiscoveryCurrent(generation)) {
                                    mPeers.postValue(peers);
                                }
                            }

                            @Override
                            public void onDiscoveryFailed(int errorCode) {
                                if (isDiscoveryCurrent(generation)) {
                                    mMessage.postValue(getApplication().getString(
                                            R.string.lan_sync_discovery_failed, errorCode));
                                }
                            }
                        });
                synchronized (mLock) {
                    if (generation != mDiscoveryGeneration) {
                        return;
                    }
                    discovery.start();
                    mDiscovery = discovery;
                }
            } catch (GeneralSecurityException | RuntimeException e) {
                Log.w(TAG, "Could not look for devices", e);
                mMessage.postValue(getApplication().getString(R.string.lan_sync_discovery_failed, -1));
            }
        });
    }

    @MainThread
    public void stopDiscovery() {
        LanSyncDiscovery discovery;
        synchronized (mLock) {
            ++mDiscoveryGeneration;
            discovery = mDiscovery;
            mDiscovery = null;
        }
        if (discovery != null) {
            discovery.stop();
        }
        mPeers.setValue(Collections.<LanSyncDiscovery.Peer>emptyList());
    }

    private boolean isDiscoveryCurrent(int generation) {
        synchronized (mLock) {
            return generation == mDiscoveryGeneration;
        }
    }

    @VisibleForTesting
    @Nullable
    LanSyncDiscovery getDiscovery() {
        synchronized (mLock) {
            return mDiscovery;
        }
    }

    // Talking to another device

    /**
     * Connects to a device found nearby or typed in. The address must already be numeric, from
     * {@link LanAddresses#toAddress(String)} or discovery, so nothing is looked up by name.
     */
    @MainThread
    public void connect(@NonNull InetAddress address, int port, @NonNull String label) {
        LanSyncConnector connector = new LanSyncConnector();
        synchronized (mLock) {
            if (mConnector != null || mClient != null) {
                return;
            }
            mConnector = connector;
        }
        mConnection.setValue(new Connection(Connection.CONNECTING, label, null));
        mClientExecutor.execute(() -> {
            SyncClientSession session = null;
            try {
                session = connector.connect(address, port, deviceName(getApplication()), identity(), mRandom,
                        paired -> mConnection.postValue(new Connection(Connection.WAITING, paired.getPeerName(),
                                paired.getSas())));
                String peerName = session.getPairedSession().getPeerName();
                synchronized (mLock) {
                    if (mConnector != connector) {
                        // Disconnected while the other device was deciding
                        session.close();
                        return;
                    }
                    mConnector = null;
                    mClient = session;
                    mPing = mClientExecutor.scheduleWithFixedDelay(this::ping, SyncClientSession.PING_INTERVAL_MS,
                            SyncClientSession.PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
                }
                mConnection.postValue(new Connection(Connection.CONNECTED, peerName, null));
                loadList();
            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                if (session != null) {
                    session.close();
                }
                boolean current;
                synchronized (mLock) {
                    current = mConnector == connector;
                    if (current) {
                        mConnector = null;
                    }
                }
                if (current) {
                    Log.w(TAG, "Could not connect", e);
                    mConnection.postValue(Connection.NONE);
                    mMessage.postValue(e instanceof SyncClientSession.RejectedException
                            ? getApplication().getString(R.string.lan_sync_connect_rejected)
                            : getApplication().getString(R.string.lan_sync_connect_failed, describe(e)));
                }
            }
        });
    }

    @MainThread
    public void disconnect() {
        LanSyncConnector connector;
        SyncClientSession client;
        ScheduledFuture<?> ping;
        synchronized (mLock) {
            connector = mConnector;
            client = mClient;
            ping = mPing;
            mConnector = null;
            mClient = null;
            mPing = null;
        }
        if (ping != null) {
            ping.cancel(false);
        }
        if (connector != null) {
            connector.cancel();
        }
        if (client != null) {
            // Saying goodbye writes to the socket, which Android refuses on the main thread
            mCloser.execute(client::close);
        }
        mConnection.setValue(Connection.NONE);
        mRows.setValue(null);
        mTask.setValue(null);
    }

    @MainThread
    public void setShowSystemApps(boolean showSystem) {
        synchronized (mLock) {
            if (mShowSystem == showSystem) {
                return;
            }
            mShowSystem = showSystem;
        }
        loadList();
    }

    public void loadList() {
        mClientExecutor.execute(() -> {
            SyncClientSession client = currentClient();
            if (client == null) {
                return;
            }
            mTask.postValue(getApplication().getString(R.string.lan_sync_loading_list));
            try {
                List<SyncApp> remote = client.list(isShowingSystemApps());
                Map<String, SyncApp> local = mLocalIndex.load();
                List<AppRow> rows = new ArrayList<>(remote.size());
                for (SyncApp app : remote) {
                    rows.add(new AppRow(app, local.get(app.packageName)));
                }
                Collections.sort(rows, (a, b) -> {
                    int byLabel = a.remote.label.toLowerCase(Locale.ROOT).compareTo(b.remote.label.toLowerCase(Locale.ROOT));
                    return byLabel != 0 ? byLabel : a.remote.packageName.compareTo(b.remote.packageName);
                });
                mRows.postValue(rows);
            } catch (IOException e) {
                onClientError(client, e);
            } finally {
                mTask.postValue(null);
            }
        });
    }

    /**
     * Sorts the other device's apps against what is installed here again, without asking it for
     * the list. Run when the screen comes back or an app is installed or removed while it shows,
     * so an app the installer just put on moves to the right group instead of still offering Get.
     */
    @MainThread
    public void recheckInstalled() {
        List<AppRow> shown = mRows.getValue();
        if (shown == null || shown.isEmpty()) {
            return;
        }
        mClientExecutor.execute(() -> {
            Map<String, SyncApp> local = mLocalIndex.load();
            List<AppRow> rows = new ArrayList<>(shown.size());
            for (AppRow row : shown) {
                rows.add(new AppRow(row.remote, local.get(row.remote.packageName)));
            }
            ThreadUtils.postOnMainThread(() -> {
                // A disconnect or a fresh list since then wins
                if (mRows.getValue() == shown) {
                    mRows.setValue(rows);
                }
            });
        });
    }

    /**
     * Downloads one app, checks it and queues it for the installer.
     */
    @MainThread
    public void pull(@NonNull AppRow row) {
        pullAll(Collections.singletonList(row));
    }

    /**
     * Downloads every app the other device has a newer copy of. Each one that passes the checks
     * goes to the installer's queue together.
     */
    @MainThread
    public void pullAllNewer() {
        List<AppRow> rows = mRows.getValue();
        if (rows == null) {
            return;
        }
        List<AppRow> newer = new ArrayList<>();
        for (AppRow row : rows) {
            if (row.status == SyncComparator.Status.NEWER) {
                newer.add(row);
            }
        }
        if (!newer.isEmpty()) {
            pullAll(newer);
        }
    }

    private void pullAll(@NonNull List<AppRow> rows) {
        mClientExecutor.execute(() -> {
            SyncClientSession client = currentClient();
            if (client == null) {
                return;
            }
            File dir = downloadDir();
            if (!dir.mkdirs() && !dir.isDirectory()) {
                mMessage.postValue(getApplication().getString(R.string.lan_sync_storage_failed));
                return;
            }
            AndroidPackageCatalog catalog = new AndroidPackageCatalog(getApplication());
            List<LanSyncDownloader.Result> ready = new ArrayList<>();
            try {
                for (AppRow row : rows) {
                    String label = row.remote.label;
                    int[] lastPercent = {-1};
                    mTask.postValue(getApplication().getString(R.string.lan_sync_downloading, label, 0));
                    try {
                        // Read the installed copy again: it may have changed since the list came in
                        SyncApp installed = catalog.findInstalled(row.remote.packageName);
                        ready.add(LanSyncDownloader.pull(client, row.remote, installed, dir,
                                ApkSigners.VERIFIED_FILE_READER, (done, total) -> {
                                    int percent = total > 0 ? (int) (done * 100 / total) : 0;
                                    if (percent != lastPercent[0]) {
                                        lastPercent[0] = percent;
                                        mTask.postValue(getApplication().getString(R.string.lan_sync_downloading,
                                                label, percent));
                                    }
                                }));
                    } catch (SyncClientSession.RemoteError | ApkSignatureGate.MismatchException e) {
                        // This app failed, the session is still fine
                        Log.w(TAG, "Could not get %s", e, row.remote.packageName);
                        mMessage.postValue(getApplication().getString(R.string.lan_sync_pull_failed, label,
                                describe(e)));
                    }
                }
            } catch (IOException e) {
                onClientError(client, e);
            } finally {
                mTask.postValue(null);
            }
            // Whatever arrived before a failure still goes to the installer
            if (!ready.isEmpty()) {
                mReady.postValue(ready);
            }
        });
    }

    @WorkerThread
    private void ping() {
        SyncClientSession client = currentClient();
        if (client == null) {
            return;
        }
        try {
            client.ping();
        } catch (IOException e) {
            onClientError(client, e);
        }
    }

    @WorkerThread
    private void onClientError(@NonNull SyncClientSession client, @NonNull IOException e) {
        Log.w(TAG, "Local sync request failed", e);
        if (!client.isClosed()) {
            mMessage.postValue(getApplication().getString(R.string.lan_sync_request_failed, describe(e)));
            return;
        }
        // The session is gone: forget it so the screen goes back to the device list
        ScheduledFuture<?> ping = null;
        synchronized (mLock) {
            if (mClient == client) {
                mClient = null;
                ping = mPing;
                mPing = null;
            }
        }
        if (ping != null) {
            ping.cancel(false);
        }
        mConnection.postValue(Connection.NONE);
        mRows.postValue(null);
        mMessage.postValue(getApplication().getString(R.string.lan_sync_connection_lost, describe(e)));
    }

    @Nullable
    private SyncClientSession currentClient() {
        synchronized (mLock) {
            return mClient;
        }
    }

    @NonNull
    private File downloadDir() {
        return new File(getApplication().getCacheDir(), LanSyncDownloader.DIR_NAME);
    }

    @WorkerThread
    @NonNull
    private LocalIdentity identity() throws GeneralSecurityException {
        LocalIdentity identity = mIdentity;
        if (identity == null) {
            synchronized (mTrust) {
                identity = mIdentity;
                if (identity == null) {
                    identity = mTrust.getOrCreateIdentity(mRandom);
                    mIdentity = identity;
                }
            }
        }
        return identity;
    }

    @NonNull
    private static String describe(@NonNull Exception e) {
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }

    /**
     * The name people gave this device in Settings, or its model.
     */
    @NonNull
    public static String deviceName(@NonNull Context context) {
        String name = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            name = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
        }
        if (TextUtils.isEmpty(name)) {
            name = Build.MODEL;
        }
        if (TextUtils.isEmpty(name)) {
            name = "Android";
        }
        name = name.trim();
        return name.length() > MAX_DEVICE_NAME_CHARS ? name.substring(0, MAX_DEVICE_NAME_CHARS) : name;
    }

    /**
     * Shows the six digits as two groups of three, the way both screens print them.
     */
    @NonNull
    public static String formatSas(@NonNull String sas) {
        return sas.length() == 6 ? sas.substring(0, 3) + " " + sas.substring(3) : sas;
    }
}
