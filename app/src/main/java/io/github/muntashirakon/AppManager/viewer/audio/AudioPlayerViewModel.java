// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.app.Application;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class AudioPlayerViewModel extends AndroidViewModel {
    private static final String TAG = AudioPlayerViewModel.class.getSimpleName();

    private final MutableLiveData<AudioMetadata> mAudioMetadataLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mMediaPlayerPreparedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mPlaylistLoadedLiveData = new MutableLiveData<>();
    private final List<AudioMetadata> mPlaylist = Collections.synchronizedList(new ArrayList<>());

    private int mCurrentPlaylistIndex = -1;

    public AudioPlayerViewModel(@NonNull Application application) {
        super(application);
    }

    public int getCurrentPlaylistIndex() {
        return mCurrentPlaylistIndex;
    }

    public int playlistSize() {
        return mPlaylist.size();
    }

    public void addToPlaylist(@NonNull Uri[] uriList) {
        ThreadUtils.postOnBackgroundThread(() -> {
            for (Uri uri : uriList) {
                AudioMetadata audioMetadata = fetchAudioMetadata(uri);
                mPlaylist.add(audioMetadata);
                int index = mCurrentPlaylistIndex;
                if (index == -1) {
                    // Start playing immediately if nothing is playing
                    mCurrentPlaylistIndex = 0;
                    mAudioMetadataLiveData.postValue(audioMetadata);
                }
            }
            mPlaylistLoadedLiveData.postValue(true);
        });
    }

    public void playNext(@RepeatMode int repeatMode) {
        switch (repeatMode) {
            default:
            case RepeatMode.NO_REPEAT:
                playNext(false);
                break;
            case RepeatMode.REPEAT_INDEFINITELY:
                playNext(true);
                break;
            case RepeatMode.REPEAT_SINGLE_INDEFINITELY:
                mAudioMetadataLiveData.postValue(mPlaylist.get(mCurrentPlaylistIndex));
        }
    }

    public void playNext(boolean repeat) {
        int index = mCurrentPlaylistIndex;
        if (index < (mPlaylist.size() - 1)) {
            ++index;
            mCurrentPlaylistIndex = index;
            mAudioMetadataLiveData.postValue(mPlaylist.get(index));
        } else if (repeat) {
            // Reset index
            index = 0;
            mCurrentPlaylistIndex = index;
            mAudioMetadataLiveData.postValue(mPlaylist.get(index));
        }
    }

    public void playPrevious() {
        int index = mCurrentPlaylistIndex;
        if (index > 0) {
            --index;
            mCurrentPlaylistIndex = index;
            mAudioMetadataLiveData.postValue(mPlaylist.get(index));
        }
    }

    public LiveData<AudioMetadata> getAudioMetadataLiveData() {
        return mAudioMetadataLiveData;
    }

    public LiveData<Boolean> getMediaPlayerPreparedLiveData() {
        return mMediaPlayerPreparedLiveData;
    }

    public LiveData<Boolean> getPlaylistLoadedLiveData() {
        return mPlaylistLoadedLiveData;
    }

    public void prepareMediaPlayer(@NonNull MediaPlayer mediaPlayer, @NonNull Uri uri) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(getApplication(), uri);
                mediaPlayer.prepare();
                mMediaPlayerPreparedLiveData.postValue(true);
            } catch (IOException e) {
                Log.e(TAG, "Could not prepare media player for %s.", e, uri);
                mMediaPlayerPreparedLiveData.postValue(false);
            }
        });
    }

    @NonNull
    private AudioMetadata fetchAudioMetadata(@NonNull Uri uri) {
        AudioMetadata audioMetadata = new AudioMetadata();
        audioMetadata.uri = uri;
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(getApplication(), uri);
            final byte[] raw = retriever.getEmbeddedPicture();
            if (raw != null) {
                audioMetadata.cover = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            }
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (TextUtils.isEmpty(title)) {
                title = uri.getLastPathSegment();
                if (TextUtils.isEmpty(title)) {
                    title = getString(R.string.audio_player_unknown_title);
                }
            }
            String artist = getMetadataOrFallback(retriever,
                    MediaMetadataRetriever.METADATA_KEY_ARTIST, R.string.audio_player_unknown_artist);
            String album = getMetadataOrFallback(retriever,
                    MediaMetadataRetriever.METADATA_KEY_ALBUM, R.string.audio_player_unknown_album);
            String albumArtist = getMetadataOrFallback(retriever,
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, R.string.audio_player_unknown_artist);
            audioMetadata.title = title;
            audioMetadata.album = album;
            audioMetadata.albumArtist = albumArtist;
            audioMetadata.artist = artist;
        } catch (RuntimeException | IOException e) {
            Log.e(TAG, "Could not read audio metadata for %s.", e, uri);
            String title = uri.getLastPathSegment();
            if (TextUtils.isEmpty(title)) {
                title = getString(R.string.audio_player_unknown_title);
            }
            audioMetadata.title = title;
            audioMetadata.album = getString(R.string.audio_player_unknown_album);
            audioMetadata.albumArtist = getString(R.string.audio_player_unknown_artist);
            audioMetadata.artist = getString(R.string.audio_player_unknown_artist);
        }
        return audioMetadata;
    }

    @NonNull
    private String getMetadataOrFallback(@NonNull MediaMetadataRetriever retriever,
                                         int metadataKey,
                                         @StringRes int fallbackRes) {
        String value = retriever.extractMetadata(metadataKey);
        return TextUtils.isEmpty(value) ? getString(fallbackRes) : value;
    }

    @NonNull
    private String getString(@StringRes int resId) {
        return getApplication().getString(resId);
    }
}
