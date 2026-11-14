// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;

/**
 * Decodes file thumbnails in an isolated process (see the manifest): no permissions, no app
 * data, no paths. The app hands it read-only descriptors through {@link IThumbnailDecoder}.
 */
public class ThumbnailDecoderService extends Service {
    @Nullable
    private ThumbnailDecoderBinder mBinder;

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new ThumbnailDecoderBinder(() -> Process.killProcess(Process.myPid()));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mBinder != null) {
            mBinder.shutdown();
        }
        super.onDestroy();
    }
}
