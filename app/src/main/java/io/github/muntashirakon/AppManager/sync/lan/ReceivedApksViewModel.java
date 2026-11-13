// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.app.Application;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Lists what {@link ReceivedApkStore} holds and installs from it, with no other device needed.
 */
public class ReceivedApksViewModel extends AndroidViewModel {
    public static final String TAG = ReceivedApksViewModel.class.getSimpleName();

    public static final class Item {
        @NonNull
        public final ReceivedApkStore.Entry entry;
        /**
         * Bytes on disk, which for an unfinished download is less than the listed size.
         */
        public final long bytes;

        Item(@NonNull ReceivedApkStore.Entry entry, long bytes) {
            this.entry = entry;
            this.bytes = bytes;
        }
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<List<Item>> mItems = new MutableLiveData<>();
    private final MutableLiveData<String> mMessage = new MutableLiveData<>();
    private final MutableLiveData<LanSyncDownloader.Result> mReady = new MutableLiveData<>();
    private final MutableLiveData<Intent> mSystemInstaller = new MutableLiveData<>();
    @NonNull
    private final LanSyncSettings mSettings;
    @NonNull
    private volatile File mDir;
    @NonNull
    private volatile LanSyncViewModel.LocalIndex mLocalIndex;
    @NonNull
    private volatile ApkSignatureGate.DigestReader mReader = ApkSigners.VERIFIED_FILE_READER;
    /**
     * Where saved copies go in tests. Null means the public Downloads folder.
     */
    @Nullable
    private volatile DestinationFactory mDestinations;

    interface DestinationFactory {
        @NonNull
        ReceivedApkExport.Destination create();
    }

    public ReceivedApksViewModel(@NonNull Application application) {
        super(application);
        mSettings = new LanSyncSettings(application);
        mDir = new File(application.getCacheDir(), LanSyncDownloader.DIR_NAME);
        mLocalIndex = () -> new AndroidPackageCatalog(application).indexInstalled();
    }

    @VisibleForTesting
    void setUpForTesting(@NonNull File dir, @NonNull LanSyncViewModel.LocalIndex localIndex,
                         @NonNull ApkSignatureGate.DigestReader reader) {
        mDir = dir;
        mLocalIndex = localIndex;
        mReader = reader;
    }

    @VisibleForTesting
    void setDestinationsForTesting(@Nullable DestinationFactory destinations) {
        mDestinations = destinations;
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
    }

    @NonNull
    public LanSyncSettings getSettings() {
        return mSettings;
    }

    @NonNull
    public LiveData<List<Item>> getItems() {
        return mItems;
    }

    /**
     * A one-off message for a toast. Call {@link #onMessageShown()} once it is on screen.
     */
    @NonNull
    public LiveData<String> getMessage() {
        return mMessage;
    }

    /**
     * An install that passed the checks again and waits for the installer. Call
     * {@link #onInstallHandedOff()} once it is handed over.
     */
    @NonNull
    public LiveData<LanSyncDownloader.Result> getReady() {
        return mReady;
    }

    /**
     * Android's own installer, ready to open a received APK. Call
     * {@link #onSystemInstallerStarted()} once it is started.
     */
    @NonNull
    public LiveData<Intent> getSystemInstaller() {
        return mSystemInstaller;
    }

    public void onSystemInstallerStarted() {
        mSystemInstaller.setValue(null);
    }

    public void onMessageShown() {
        mMessage.setValue(null);
    }

    public void onInstallHandedOff() {
        mReady.setValue(null);
    }

    /**
     * Cleans up first, so what shows is what the retention setting keeps.
     */
    public void load() {
        mExecutor.execute(() -> {
            LanSyncViewModel.cleanUp(mDir, mSettings, mLocalIndex, System.currentTimeMillis());
            postList();
        });
    }

    public void setRetention(int retention) {
        if (retention == mSettings.getRetention()) {
            return;
        }
        mSettings.setRetention(retention);
        load();
    }

    public void setKeepAfterInstall(boolean keep) {
        if (keep == mSettings.isKeepAfterInstall()) {
            return;
        }
        mSettings.setKeepAfterInstall(keep);
        load();
    }

    public void delete(@NonNull ReceivedApkStore.Entry entry) {
        mExecutor.execute(() -> {
            ReceivedApkStore store = new ReceivedApkStore(mDir);
            ReceivedApkStore.Entry current = store.find(entry.key);
            if (current != null) {
                store.delete(current);
            }
            mMessage.postValue(getApplication().getString(R.string.received_apks_deleted, entry.app.label));
            postList();
        });
    }

    /**
     * Checks the files again, against the signers the other device listed and the copy installed
     * now, and hands them to the installer.
     */
    public void install(@NonNull ReceivedApkStore.Entry entry) {
        mExecutor.execute(() -> {
            String label = entry.app.label;
            ReceivedApkStore store = new ReceivedApkStore(mDir);
            ReceivedApkStore.Entry current = store.find(entry.key);
            if (current == null || !current.complete || !current.hasAllFiles()) {
                mMessage.postValue(getApplication().getString(R.string.received_apks_gone, label));
                postList();
                return;
            }
            SyncApp installed = mLocalIndex.load().get(current.app.packageName);
            if (SyncComparator.compare(current.app, installed) == SyncComparator.Status.SIGNATURE_DIFFERS) {
                // The files may still be fine, for example once the other copy is gone
                mMessage.postValue(getApplication().getString(R.string.received_apks_signature_differs, label));
                return;
            }
            try {
                mReady.postValue(LanSyncDownloader.prepare(store, current, mDir, installed, mReader));
            } catch (IOException e) {
                Log.w(TAG, "Could not install %s", e, current.app.packageName);
                mMessage.postValue(getApplication().getString(R.string.received_apks_install_failed, label,
                        describe(e)));
                postList();
            }
        });
    }

    /**
     * Saves a copy in Downloads after checking it against the signers the other device listed,
     * so it can go on with another installer. A split set becomes one .apks file.
     */
    public void save(@NonNull String key) {
        mExecutor.execute(() -> {
            ReceivedApkStore store = new ReceivedApkStore(mDir);
            ReceivedApkStore.Entry current = findComplete(store, key);
            if (current == null) {
                return;
            }
            String label = current.app.label;
            try {
                LanSyncDownloader.verify(store, current, null, mReader);
                DestinationFactory destinations = mDestinations;
                String name = destinations != null ? ReceivedApkExport.save(current, destinations.create())
                        : ReceivedApkExport.saveToDownloads(getApplication(), current);
                mMessage.postValue(getApplication().getString(R.string.received_apks_saved, name));
            } catch (IOException | RuntimeException e) {
                // MediaStore reports a full or missing volume as an unchecked exception
                Log.w(TAG, "Could not save %s", e, current.app.packageName);
                mMessage.postValue(getApplication().getString(R.string.received_apks_save_failed, label,
                        describe(e)));
                postList();
            }
        });
    }

    /**
     * Opens a received APK in Android's own installer, after the same checks an install here
     * gets. Android's installer takes only single APKs.
     */
    public void openInSystemInstaller(@NonNull String key) {
        mExecutor.execute(() -> {
            ReceivedApkStore store = new ReceivedApkStore(mDir);
            ReceivedApkStore.Entry current = findComplete(store, key);
            if (current == null) {
                return;
            }
            String label = current.app.label;
            if (current.isBundle()) {
                mMessage.postValue(getApplication().getString(R.string.received_apks_split_no_system_installer));
                return;
            }
            SyncApp installed = mLocalIndex.load().get(current.app.packageName);
            if (SyncComparator.compare(current.app, installed) == SyncComparator.Status.SIGNATURE_DIFFERS) {
                mMessage.postValue(getApplication().getString(R.string.received_apks_signature_differs, label));
                return;
            }
            try {
                LanSyncDownloader.verify(store, current, installed, mReader);
            } catch (IOException e) {
                Log.w(TAG, "Could not open %s in the system installer", e, current.app.packageName);
                mMessage.postValue(getApplication().getString(R.string.received_apks_install_failed, label,
                        describe(e)));
                postList();
                return;
            }
            Intent intent = ReceivedApkExport.systemInstallerIntent(getApplication(), current);
            if (intent == null) {
                mMessage.postValue(getApplication().getString(R.string.received_apks_no_system_installer));
                return;
            }
            mSystemInstaller.postValue(intent);
        });
    }

    /**
     * The entry, or null after posting why it can't be used.
     */
    @WorkerThread
    @Nullable
    private ReceivedApkStore.Entry findComplete(@NonNull ReceivedApkStore store, @NonNull String key) {
        ReceivedApkStore.Entry current = store.find(key);
        if (current == null) {
            mMessage.postValue(getApplication().getString(R.string.received_apks_missing));
            postList();
            return null;
        }
        if (!current.complete || !current.hasAllFiles()) {
            mMessage.postValue(getApplication().getString(R.string.received_apks_gone, current.app.label));
            postList();
            return null;
        }
        return current;
    }

    @WorkerThread
    private void postList() {
        List<ReceivedApkStore.Entry> entries = new ReceivedApkStore(mDir).list();
        List<Item> items = new ArrayList<>(entries.size());
        for (ReceivedApkStore.Entry entry : entries) {
            items.add(new Item(entry, entry.bytesHere()));
        }
        mItems.postValue(items);
    }

    @NonNull
    private static String describe(@NonNull Exception e) {
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }
}
