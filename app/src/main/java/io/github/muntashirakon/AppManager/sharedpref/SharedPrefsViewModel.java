// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.app.Application;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.io.AtomicExtendedFile;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.UidGidPair;

public class SharedPrefsViewModel extends AndroidViewModel {
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    private final MutableLiveData<Map<String, Object>> mSharedPrefsMapLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSharedPrefsSavedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSharedPrefsDeletedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSharedPrefsModifiedLiveData = new MutableLiveData<>();

    private Path mSharedPrefsFile;
    private Map<String, Object> mSharedPrefsMap;
    private boolean mModified;

    public SharedPrefsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
        super.onCleared();
    }

    public void setSharedPrefsFile(@NonNull Path sharedPrefFile) {
        mSharedPrefsFile = sharedPrefFile;
    }

    public boolean isModified() {
        return mModified;
    }

    @Nullable
    public String getSharedPrefFilename() {
        if (mSharedPrefsFile != null) {
            return mSharedPrefsFile.getName();
        }
        return null;
    }

    @Nullable
    public Object getValue(@NonNull String key) {
        return mSharedPrefsMap.get(key);
    }

    public void remove(@NonNull String key) {
        mSharedPrefsModifiedLiveData.postValue(mModified = true);
        mSharedPrefsMap.remove(key);
        mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
    }

    public void add(@NonNull String key, @NonNull Object value) {
        mSharedPrefsModifiedLiveData.postValue(mModified = true);
        mSharedPrefsMap.put(key, value);
        mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
    }

    public LiveData<Map<String, Object>> getSharedPrefsMapLiveData() {
        return mSharedPrefsMapLiveData;
    }

    public LiveData<Boolean> getSharedPrefsSavedLiveData() {
        return mSharedPrefsSavedLiveData;
    }

    public LiveData<Boolean> getSharedPrefsDeletedLiveData() {
        return mSharedPrefsDeletedLiveData;
    }

    public LiveData<Boolean> getSharedPrefsModifiedLiveData() {
        return mSharedPrefsModifiedLiveData;
    }

    @AnyThread
    public void deleteSharedPrefFile() {
        mExecutor.submit(() -> mSharedPrefsDeletedLiveData.postValue(mSharedPrefsFile.delete()));
    }

    @AnyThread
    public void writeSharedPrefs() {
        mExecutor.submit(() -> {
            try {
                writeSharedPrefsAtomically(mSharedPrefsFile, mSharedPrefsMap);
                mSharedPrefsSavedLiveData.postValue(true);
                mSharedPrefsModifiedLiveData.postValue(mModified = false);
            } catch (IOException e) {
                e.printStackTrace();
                mSharedPrefsSavedLiveData.postValue(false);
            }
        });
    }

    @VisibleForTesting
    static void writeSharedPrefsAtomically(@NonNull Path sharedPrefsFile,
                                           @NonNull Map<String, Object> sharedPrefsMap)
            throws IOException {
        writeSharedPrefsAtomically(sharedPrefsFile,
                outputStream -> SharedPrefsUtil.writeSharedPref(outputStream, sharedPrefsMap));
    }

    @VisibleForTesting
    static void writeSharedPrefsAtomically(@NonNull Path sharedPrefsFile,
                                           @NonNull SharedPrefsWriter writer)
            throws IOException {
        ExtendedFile baseFile = sharedPrefsFile.getFile();
        if (baseFile == null) {
            try (OutputStream outputStream = sharedPrefsFile.openOutputStream()) {
                writer.write(outputStream);
            }
            return;
        }
        AtomicExtendedFile atomicFile = new AtomicExtendedFile(baseFile);
        FileMetadata originalMetadata = FileMetadata.from(sharedPrefsFile);
        ExtendedFile pendingFile = Objects.requireNonNull(baseFile.getParentFile())
                .getChildFile(baseFile.getName() + ".new");
        FileOutputStream outputStream = null;
        try {
            outputStream = atomicFile.startWrite();
            writer.write(outputStream);
            atomicFile.finishWrite(outputStream);
            outputStream = null;
            if (pendingFile.exists()) {
                throw new IOException("Failed to commit atomic SharedPreferences write.");
            }
            if (originalMetadata != null) {
                originalMetadata.applyTo(sharedPrefsFile);
            }
        } catch (IOException | RuntimeException e) {
            atomicFile.failWrite(outputStream);
            throw e;
        }
    }

    @VisibleForTesting
    interface SharedPrefsWriter {
        void write(@NonNull OutputStream outputStream) throws IOException;
    }

    private static class FileMetadata {
        private final int mode;
        @Nullable
        private final UidGidPair uidGid;
        @Nullable
        private final String selinuxContext;

        private FileMetadata(int mode, @Nullable UidGidPair uidGid, @Nullable String selinuxContext) {
            this.mode = mode;
            this.uidGid = uidGid;
            this.selinuxContext = selinuxContext;
        }

        @Nullable
        private static FileMetadata from(@NonNull Path path) {
            if (!path.exists()) {
                return null;
            }
            return new FileMetadata(path.getMode(), path.getUidGid(), path.getSelinuxContext());
        }

        private void applyTo(@NonNull Path path) {
            if (mode != 0) {
                path.setMode(mode);
            }
            if (uidGid != null) {
                path.setUidGid(uidGid);
            }
            if (selinuxContext != null) {
                path.setSelinuxContext(selinuxContext);
            }
        }
    }

    @AnyThread
    public void loadSharedPrefs() {
        mExecutor.submit(() -> {
            try (InputStream rulesStream = mSharedPrefsFile.openInputStream()) {
                mSharedPrefsModifiedLiveData.postValue(mModified = false);
                mSharedPrefsMap = SharedPrefsUtil.readSharedPref(rulesStream);
                mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
                mSharedPrefsMap = new HashMap<>();
                mSharedPrefsMapLiveData.postValue(mSharedPrefsMap);
            }
        });
    }
}
