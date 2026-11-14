// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.app.Application;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.UserHandleHidden;
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
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Holds both sides of local sync for the screen: sharing this device (the host) and talking to
 * another one (the client). Sharing and discovery only run while the screen asks for them.
 */
public class LanSyncViewModel extends AndroidViewModel {
    public static final String TAG = LanSyncViewModel.class.getSimpleName();
    public static final int TROUBLE_NONE = 0;
    /** Nothing nearby answered for a while. */
    public static final int TROUBLE_NOTHING_FOUND = 1;
    /** A connection failed or timed out before the other device could answer. */
    public static final int TROUBLE_CONNECT_FAILED = 2;
    /** Opened from the menu. */
    public static final int TROUBLE_ASKED = 3;
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
    interface LocalIndex {
        @WorkerThread
        @NonNull
        Map<String, SyncApp> load();
    }

    /**
     * Reads one installed app again, right before its download.
     */
    interface InstalledLookup {
        @WorkerThread
        @Nullable
        SyncApp find(@NonNull String packageName);
    }

    /**
     * Removes an app for every user, data included, before a copy signed by another key goes in.
     */
    interface Uninstaller {
        /**
         * @return {@code null} once the app is gone, otherwise why it isn't
         */
        @WorkerThread
        @Nullable
        String uninstall(@NonNull String packageName, @NonNull String label);
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
    private final ScreenOnPolicy mScreenOn = new ScreenOnPolicy();
    private final MutableLiveData<Integer> mTrouble = new MutableLiveData<>(TROUBLE_NONE);
    // Main thread only
    @Nullable
    private String mSearchQuery;
    @AdvancedSearchView.SearchType
    private int mSearchType = AdvancedSearchView.SEARCH_TYPE_CONTAINS;
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
    @NonNull
    private volatile InstalledLookup mInstalledLookup;
    @NonNull
    private volatile Uninstaller mUninstaller;
    // An uninstall can wait on Android's prompt, so it runs apart from the session's pings
    @NonNull
    private volatile Executor mReplaceExecutor = ThreadUtils::postOnBackgroundThread;

    public LanSyncViewModel(@NonNull Application application) {
        super(application);
        mTrust = new TrustStore(application);
        mNsd = (NsdManager) application.getSystemService(Context.NSD_SERVICE);
        mLocalIndex = () -> new AndroidPackageCatalog(application).indexInstalled();
        mInstalledLookup = packageName -> new AndroidPackageCatalog(application).findInstalled(packageName);
        mUninstaller = this::uninstallEverywhere;
    }

    @VisibleForTesting
    void setInstalledLookup(@NonNull InstalledLookup installedLookup) {
        mInstalledLookup = installedLookup;
    }

    @VisibleForTesting
    void setUninstaller(@NonNull Uninstaller uninstaller, @NonNull Executor executor) {
        mUninstaller = uninstaller;
        mReplaceExecutor = executor;
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
    void setTask(@Nullable String task) {
        mTask.setValue(task);
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

    /**
     * What the app list is searched for, kept here so it survives turning the phone.
     */
    @Nullable
    public String getSearchQuery() {
        return mSearchQuery;
    }

    @AdvancedSearchView.SearchType
    public int getSearchType() {
        return mSearchType;
    }

    @MainThread
    public void setSearch(@Nullable String query, @AdvancedSearchView.SearchType int type) {
        // Collapsing the search view clears its text, which reports an empty query
        mSearchQuery = TextUtils.isEmpty(query) ? null : query;
        mSearchType = type;
    }

    /**
     * Kept here so turning the phone doesn't restart a time limit on keeping the screen on.
     */
    @NonNull
    public ScreenOnPolicy getScreenOnPolicy() {
        return mScreenOn;
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
     * Why the help for blocked networks is showing, one of the TROUBLE constants.
     */
    @NonNull
    public LiveData<Integer> getTrouble() {
        return mTrouble;
    }

    /**
     * Shows the help for blocked networks. Nothing being found doesn't replace a stronger reason
     * already on screen.
     */
    @MainThread
    public void showTrouble(int trouble) {
        Integer current = mTrouble.getValue();
        if (trouble == TROUBLE_NOTHING_FOUND && current != null && current != TROUBLE_NONE) {
            return;
        }
        mTrouble.setValue(trouble);
    }

    /**
     * Hides the help, or only the given reason for it when one is passed.
     */
    @MainThread
    public void clearTrouble(@Nullable Integer onlyIf) {
        Integer current = mTrouble.getValue();
        if (onlyIf == null || onlyIf.equals(current)) {
            mTrouble.setValue(TROUBLE_NONE);
        }
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
     * Clears what earlier visits left behind: received APKs past the time the user keeps them,
     * the ones whose app is now installed (unless the user keeps those), and bundles from an
     * install that never finished. Runs with the downloads, so it never pulls files from under one.
     */
    public void cleanUpDownloads() {
        File dir = downloadDir();
        LanSyncSettings settings = new LanSyncSettings(getApplication());
        mClientExecutor.execute(() -> cleanUp(dir, settings, mLocalIndex, System.currentTimeMillis()));
    }

    @WorkerThread
    static void cleanUp(@NonNull File dir, @NonNull LanSyncSettings settings, @NonNull LocalIndex localIndex,
                        long now) {
        LanSyncDownloader.purgeStale(dir, STALE_DOWNLOAD_MS, now);
        ReceivedApkStore store = new ReceivedApkStore(dir);
        store.purge(settings.getRetentionMillis(), now);
        if (!settings.isKeepAfterInstall()) {
            store.removeInstalled(localIndex.load());
        }
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
                mTrouble.postValue(TROUBLE_NONE);
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
                    if (e instanceof IOException && !(e instanceof SyncClientSession.RejectedException)) {
                        // Refused or no answer: often a firewall or a per-app data setting
                        mTrouble.postValue(TROUBLE_CONNECT_FAILED);
                    }
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
        // An app that just went on no longer needs its received copy
        cleanUpDownloads();
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
        pullAll(Collections.singletonList(row), false);
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
            pullAll(newer, false);
        }
    }

    /**
     * Gets an app signed by a different key than the copy here, uninstalls that copy with its
     * data, then hands the new one to the installer. The caller checks that the user turned
     * replacing on ({@link LanSyncSettings#isAllowReplace()}).
     */
    @MainThread
    public void replace(@NonNull AppRow row) {
        pullAll(Collections.singletonList(row), true);
    }

    private void pullAll(@NonNull List<AppRow> rows, boolean replace) {
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
            List<LanSyncDownloader.Result> ready = new ArrayList<>();
            List<LanSyncDownloader.Result> replacements = new ArrayList<>();
            try {
                for (AppRow row : rows) {
                    String label = row.remote.label;
                    int[] lastPercent = {-1};
                    try {
                        // Read the installed copy again: it may have changed since the list came in
                        SyncApp installed = mInstalledLookup.find(row.remote.packageName);
                        boolean replacing = replace && installed != null && SyncComparator.compare(row.remote,
                                installed) == SyncComparator.Status.SIGNATURE_DIFFERS;
                        if (replacing && installed.system) {
                            // Its copy on the system partition would stay, still signed by the old key
                            mMessage.postValue(getApplication().getString(R.string.lan_sync_replace_system, label));
                            continue;
                        }
                        mTask.postValue(getApplication().getString(R.string.lan_sync_downloading, label, 0));
                        LanSyncDownloader.Result result = LanSyncDownloader.pull(client, row.remote, installed,
                                replacing, dir, ApkSigners.VERIFIED_FILE_READER, (done, total) -> {
                                    int percent = total > 0 ? (int) (done * 100 / total) : 0;
                                    if (percent != lastPercent[0]) {
                                        lastPercent[0] = percent;
                                        mTask.postValue(getApplication().getString(R.string.lan_sync_downloading,
                                                label, percent));
                                    }
                                });
                        (replacing ? replacements : ready).add(result);
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
                // A replacement keeps the screen busy until the old copy is gone
                mTask.postValue(replacements.isEmpty() ? null : getApplication().getString(
                        R.string.lan_sync_uninstalling, replacements.get(0).label));
            }
            // Whatever arrived before a failure still goes to the installer
            if (!ready.isEmpty()) {
                mReady.postValue(ready);
            }
            for (LanSyncDownloader.Result result : replacements) {
                mReplaceExecutor.execute(() -> uninstallThenInstall(result));
            }
        });
    }

    /**
     * The new copy goes to the installer only once the old one is gone. When the new copy's signer
     * pin would refuse it or the uninstall fails, the installed app stays as it was and the new
     * copy waits in Received APKs.
     */
    @WorkerThread
    private void uninstallThenInstall(@NonNull LanSyncDownloader.Result result) {
        try {
            mTask.postValue(getApplication().getString(R.string.lan_sync_uninstalling, result.label));
            // Checked first: a refused install after the uninstall would leave neither copy
            String failure = signerPinRefusal(result);
            int message = R.string.lan_sync_replace_pin_refused;
            if (failure == null) {
                message = R.string.lan_sync_replace_uninstall_failed;
                try {
                    failure = mUninstaller.uninstall(result.packageName, result.label);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Could not uninstall %s", e, result.packageName);
                    failure = describe(e);
                }
            }
            if (failure != null) {
                if (result.owned) {
                    // Only the bundle made for the installer goes, the received files stay
                    LanSyncDownloader.deleteQuietly(result.file);
                }
                mMessage.postValue(getApplication().getString(message, result.label, failure));
                return;
            }
            mReady.postValue(Collections.singletonList(result));
        } finally {
            mTask.postValue(null);
        }
    }

    @WorkerThread
    @Nullable
    private String signerPinRefusal(@NonNull LanSyncDownloader.Result result) {
        Path[] apks = new Path[result.apks.size()];
        for (int i = 0; i < apks.length; ++i) {
            apks[i] = Paths.get(result.apks.get(i));
        }
        // The installer screen signs them first when that's on, so the pin is checked against
        // what it will install
        return PackageInstallerCompat.signerPinRefusal(getApplication(), apks, result.packageName,
                Prefs.Installer.canSignApk());
    }

    @WorkerThread
    @Nullable
    private String uninstallEverywhere(@NonNull String packageName, @NonNull String label) {
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        installer.setAppLabel(label);
        String[] reason = new String[1];
        installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
            @Override
            public void onStartInstall(int sessionId, String packageName) {
            }

            @Override
            public void onFinishedInstall(int sessionId, String packageName, int result,
                                          @Nullable String blockingPackage, @Nullable String statusMessage) {
                reason[0] = statusMessage;
            }
        });
        if (installer.uninstall(packageName, UserHandleHidden.USER_ALL, false)) {
            return null;
        }
        return reason[0] != null ? reason[0] : getApplication().getString(R.string.failed_to_uninstall_app);
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
