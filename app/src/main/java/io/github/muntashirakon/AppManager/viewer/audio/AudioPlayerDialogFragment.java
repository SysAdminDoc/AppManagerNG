// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.slider.Slider;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;

// Raw code taken from DialogMusicPlayer <https://github.com/VishnuSanal/DialogMusicPlayer>.
// Inspired by other players such as Music Player Go, Poweramp
public class AudioPlayerDialogFragment extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = AudioPlayerDialogFragment.class.getSimpleName();
    private static final String ARG_URI_LIST = "uris";
    private static final String ARG_CLOSE_ACTIVITY = "close";
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 250L;
    private static final int SKIP_MS = 10 * 1000;

    @NonNull
    public static AudioPlayerDialogFragment getInstance(@NonNull Uri[] uriList, boolean closeActivity) {
        AudioPlayerDialogFragment dialog = new AudioPlayerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArray(ARG_URI_LIST, uriList);
        args.putBoolean(ARG_CLOSE_ACTIVITY, closeActivity);
        dialog.setArguments(args);
        return dialog;
    }

    private ImageView mIconView;
    private TextView mPlaylistSizeView;
    private TextView mTitleView;
    private TextView mInfoView;
    private Slider mSlider;
    private TextView mProgressView;
    private TextView mDurationView;
    private TextView mPlaybackSpeedView;
    private ImageView mRewindButton;
    private ImageView mForwardButton;
    private ImageView mPlayPauseButton;
    private ImageView mRepeatButton;

    private Handler mUpdateHandler;
    private Runnable mUpdateRunnable;

    private boolean mIsTimeReversed = false;
    private boolean mShouldCheckRepeat = true;

    private MediaPlayer mMediaPlayer;
    /**
     * True once the current track is prepared. The view model resets and prepares the player on a background thread,
     * and a seek or a length read in the middle of that puts the player in its error state, so the track never plays.
     */
    private boolean mPrepared;
    private AudioPlayerViewModel mViewModel;
    private PowerManager.WakeLock mWakeLock;

    private boolean mCloseActivity;

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_audio_player, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(AudioPlayerViewModel.class);
        Uri[] uriList = (Uri[]) requireArguments().getParcelableArray(ARG_URI_LIST);
        mCloseActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY);
        mIconView = bodyView.findViewById(android.R.id.icon);
        mPlaylistSizeView = bodyView.findViewById(R.id.size);
        mTitleView = bodyView.findViewById(android.R.id.title);
        mInfoView = bodyView.findViewById(R.id.info);
        mSlider = bodyView.findViewById(R.id.slider);
        mProgressView = bodyView.findViewById(R.id.progress);
        mDurationView = bodyView.findViewById(R.id.duration);
        mPlaybackSpeedView = bodyView.findViewById(R.id.playback_speed);
        mRewindButton = bodyView.findViewById(R.id.action_rewind);
        mForwardButton = bodyView.findViewById(R.id.action_forward);
        mPlayPauseButton = bodyView.findViewById(R.id.action_play_pause);
        mRepeatButton = bodyView.findViewById(R.id.action_repeat);
        // Set tags
        mPlaybackSpeedView.setTag(1.0f);
        mRepeatButton.setTag(RepeatMode.NO_REPEAT);
        updateStaticControlLabels();
        updatePlaybackSpeedLabel();
        setRepeatButtonState(RepeatMode.NO_REPEAT);
        setPlayPauseButtonState(R.drawable.ic_play_arrow, R.string.audio_player_play);
        // Listeners
        mMediaPlayer = new MediaPlayer();
        setListeners();
        mViewModel.getAudioMetadataLiveData().observe(getViewLifecycleOwner(), this::setupMetadata);
        mViewModel.getMediaPlayerPreparedLiveData().observe(getViewLifecycleOwner(), prepared -> {
            if (!prepared) {
                return;
            }
            mPrepared = true;
            startPlayingMedia();
            mShouldCheckRepeat = true;
        });
        mViewModel.getPlaylistLoadedLiveData().observe(getViewLifecycleOwner(), loaded -> updatePlaylistSize());
        mViewModel.addToPlaylist(uriList);
        mWakeLock = CpuUtils.getPartialWakeLock("pasteThread");
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        if (mediaPlayerInitialized() && mMediaPlayer.isPlaying()) {
            startProgressUpdates();
        }
    }

    @Override
    public void onStop() {
        stopProgressUpdates();
        CpuUtils.releaseWakeLock(mWakeLock);
        super.onStop();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        stopProgressUpdates();
        releaseMediaPlayer();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroy() {
        stopProgressUpdates();
        releaseMediaPlayer();
        CpuUtils.releaseWakeLock(mWakeLock);
        super.onDestroy();
        if (mCloseActivity) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    }

    private void setListeners() {
        mSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo((int) value);

                if (!mMediaPlayer.isPlaying()) {
                    if (value != mMediaPlayer.getDuration()) {
                        setPlayPauseButtonState(R.drawable.ic_play_arrow, R.string.audio_player_play);
                    } else resetMediaPlayer();
                }
            }
        });
        mSlider.setLabelFormatter(value -> getFormattedTime((long) value, mIsTimeReversed));

        mUpdateHandler = new Handler(Looper.myLooper());
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayerInitialized()) {
                    int duration = mMediaPlayer.getDuration();
                    if (duration > 0 && (!mSlider.isEnabled() || duration != (int) mSlider.getValueTo())) {
                        // Streams and some VBR files only learn their length, or correct it, after playback starts.
                        applyDuration(duration);
                    }
                    // Right after a seek some players report the target, which can be a little below zero.
                    final int currentPosition = Math.max(0, mMediaPlayer.getCurrentPosition());
                    setSliderPosition(mSlider, currentPosition);

                    mProgressView.setText(getFormattedTime(currentPosition, mIsTimeReversed));
                    mUpdateHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS);
                }
            }
        };


        // Play/Pause: Click -> Play/pause, Long click -> Set duration to zero
        mPlayPauseButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized())
                if (!mMediaPlayer.isPlaying()) {
                    if (mMediaPlayer.getCurrentPosition() == mMediaPlayer.getDuration()) {
                        mMediaPlayer.seekTo(0);
                    }
                    resumeMediaPlayer();
                } else {
                    pauseMediaPlayer();
                }
        });
        mPlayPauseButton.setOnLongClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo(0);
            }
            return true;
        });

        mProgressView.setOnClickListener(v -> mIsTimeReversed = !mIsTimeReversed);

        mPlaybackSpeedView.setOnClickListener(v -> {
            float speed = (float) mPlaybackSpeedView.getTag();
            if (speed == 0.5F) {
                mPlaybackSpeedView.setText("0.75x");
                mPlaybackSpeedView.setTag(0.75F);
            } else if (speed == 0.75F) {
                mPlaybackSpeedView.setText("1x");
                mPlaybackSpeedView.setTag(1F);
            } else if (speed == 1.0F) {
                mPlaybackSpeedView.setText("1.25x");
                mPlaybackSpeedView.setTag(1.25F);
            } else if (speed == 1.25F) {
                mPlaybackSpeedView.setText("1.5x");
                mPlaybackSpeedView.setTag(1.5F);
            } else if (speed == 1.5F) {
                mPlaybackSpeedView.setText("2.0x");
                mPlaybackSpeedView.setTag(2.0F);
            } else if (speed == 2.0F) {
                mPlaybackSpeedView.setText("0.5x");
                mPlaybackSpeedView.setTag(0.5F);
            }
            updatePlaybackSpeedLabel();
            updatePlaybackSpeed();
        });

        mRepeatButton.setOnClickListener(v -> {
            @RepeatMode
            int state = (int) mRepeatButton.getTag();
            switch (state) {
                case RepeatMode.NO_REPEAT:
                    setRepeatButtonState(RepeatMode.REPEAT_INDEFINITELY);
                    break;
                case RepeatMode.REPEAT_INDEFINITELY:
                    setRepeatButtonState(RepeatMode.REPEAT_SINGLE_INDEFINITELY);
                    break;
                case RepeatMode.REPEAT_SINGLE_INDEFINITELY:
                    setRepeatButtonState(RepeatMode.NO_REPEAT);
                    break;
            }
        });

        // Rewind: Click -> -10 sec, Long click -> Play previous item
        mRewindButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo(skipTarget(mMediaPlayer.getCurrentPosition(), -SKIP_MS, mMediaPlayer.getDuration()));
            }
        });
        mRewindButton.setOnLongClickListener(v -> {
            mViewModel.playPrevious();
            return true;
        });

        // Forward: Click -> +10 sec, Long click -> Play next item
        mForwardButton.setOnClickListener(v -> {
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo(skipTarget(mMediaPlayer.getCurrentPosition(), SKIP_MS, mMediaPlayer.getDuration()));
            }
        });
        mForwardButton.setOnLongClickListener(v -> {
            mViewModel.playNext(false);
            return true;
        });

        mMediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!mShouldCheckRepeat) {
                mShouldCheckRepeat = true;
                return;
            }
            @RepeatMode
            int state = (int) mRepeatButton.getTag();
            resetMediaPlayer();
            if (state == RepeatMode.REPEAT_SINGLE_INDEFINITELY) {
                if (mediaPlayer.getCurrentPosition() == mediaPlayer.getDuration()) {
                    mediaPlayer.seekTo(0);
                }
                resumeMediaPlayer();
            } else {
                mViewModel.playNext(state);
            }
        });
    }

    private void updatePlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayerInitialized()) {
            boolean isPlaying = mMediaPlayer.isPlaying();
            mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed((float) mPlaybackSpeedView.getTag()));
            if (!isPlaying) {
                mMediaPlayer.pause();
            }
        }
    }

    private void pauseMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.pause();
            setPlayPauseButtonState(R.drawable.ic_play_arrow, R.string.audio_player_play);
        }
    }

    private void resumeMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.start();
            setPlayPauseButtonState(R.drawable.ic_pause, R.string.audio_player_pause);
        }
    }

    private void resetMediaPlayer() {
        setPlayPauseButtonState(R.drawable.ic_replay, R.string.audio_player_replay);
    }

    private String getFormattedTime(long millis, boolean isTimeReversed) {
        if (!mediaPlayerInitialized()) {
            return formatTime(0, -1, false);
        }
        return formatTime(millis, mMediaPlayer.getDuration(), isTimeReversed);
    }

    /** "m:ss", or the time left as "-m:ss" when {@code remaining} is asked for and the length is known. */
    @VisibleForTesting
    @NonNull
    static String formatTime(long millis, int durationMs, boolean remaining) {
        if (remaining && durationMs > 0) {
            return "-" + formatTime(durationMs - millis, durationMs, false);
        }
        long totalSeconds = Math.max(0L, millis) / 1000;
        long seconds = totalSeconds % 60;
        return (totalSeconds / 60) + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private boolean mediaPlayerInitialized() {
        if (mMediaPlayer == null || !mPrepared) {
            return false;
        }
        try {
            mMediaPlayer.isPlaying();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer == null) {
            return;
        }
        mPrepared = false;
        mMediaPlayer.release();
        mMediaPlayer = null;
    }

    private void updatePlaylistSize() {
        int currentAudio = mViewModel.getCurrentPlaylistIndex() + 1;
        int playlistSize = mViewModel.playlistSize();
        mPlaylistSizeView.setVisibility(playlistSize > 1 ? View.VISIBLE : View.GONE);
        mPlaylistSizeView.setText(String.format(Locale.ROOT, "%d/%d", currentAudio, playlistSize));
    }

    private void setupMetadata(@NonNull AudioMetadata meta) {
        if (meta.cover != null) {
            mIconView.setImageBitmap(meta.cover);
        } else {
            mIconView.setImageResource(R.drawable.ic_audio_file);
        }
        updatePlaylistSize();
        mTitleView.setText(String.format(Locale.ROOT, "%s - %s", meta.artist, meta.title));
        mTitleView.setSelected(true);
        mInfoView.setText(String.format(Locale.ROOT, "%s - %s", meta.albumArtist, meta.album));
        mInfoView.setSelected(true);

        mShouldCheckRepeat = false;
        // Nothing touches the player until it's prepared, and the controls show a track that isn't loaded yet.
        mPrepared = false;
        stopProgressUpdates();
        applyDuration(-1);
        mProgressView.setText(formatTime(0, -1, false));
        mViewModel.prepareMediaPlayer(mMediaPlayer, meta.uri);
    }

    private void startPlayingMedia() {
        applyDuration(mMediaPlayer.getDuration());

        mMediaPlayer.seekTo(0);
        resumeMediaPlayer();
        startProgressUpdates();

        setPlayPauseButtonState(R.drawable.ic_pause, R.string.audio_player_pause);
    }

    /**
     * Sets up the slider and the length label for a track of this length, or -1 if unknown. The skip buttons stay on:
     * their long press changes the track, and a skip without a known length only stops at the start.
     */
    private void applyDuration(int duration) {
        setSliderRange(mSlider, duration);
        mDurationView.setText(duration > 0 ? formatTime(duration, duration, false)
                : getString(R.string.audio_player_unknown_length));
    }

    /**
     * Gives the slider the track's length, or disables it when the length isn't known (a stream, or a file the
     * player can't measure). The value goes back to the start too, since the last track's position can be past the
     * new end, and Material's Slider throws on a value outside its range.
     */
    @VisibleForTesting
    static void setSliderRange(@NonNull Slider slider, int durationMs) {
        boolean known = durationMs > 0;
        slider.setValueFrom(0);
        slider.setValueTo(known ? durationMs : 1);
        slider.setValue(0);
        slider.setEnabled(known);
    }

    /** Moves an enabled slider to a position, kept inside its range. */
    @VisibleForTesting
    static void setSliderPosition(@NonNull Slider slider, int positionMs) {
        if (slider.isEnabled()) {
            slider.setValue(Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), positionMs)));
        }
    }

    /** Where a skip lands: never before the start, and never past the end when the length is known. */
    @VisibleForTesting
    static int skipTarget(int positionMs, int deltaMs, int durationMs) {
        long target = Math.max(0L, (long) positionMs + deltaMs);
        if (durationMs > 0) {
            target = Math.min(target, durationMs);
        }
        return (int) Math.min(target, Integer.MAX_VALUE);
    }

    private void startProgressUpdates() {
        if (mUpdateHandler == null || mUpdateRunnable == null) {
            return;
        }
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
        mUpdateHandler.postDelayed(mUpdateRunnable, 0);
    }

    private void stopProgressUpdates() {
        if (mUpdateHandler != null && mUpdateRunnable != null) {
            mUpdateHandler.removeCallbacks(mUpdateRunnable);
        }
    }

    private void updateStaticControlLabels() {
        setControlLabel(mRewindButton, R.string.audio_player_rewind_10);
        setControlLabel(mForwardButton, R.string.audio_player_forward_10);
    }

    private void updatePlaybackSpeedLabel() {
        String label = getString(R.string.audio_player_playback_speed_a11y, mPlaybackSpeedView.getText());
        mPlaybackSpeedView.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPlaybackSpeedView.setTooltipText(label);
        }
    }

    private void setRepeatButtonState(@RepeatMode int repeatMode) {
        mRepeatButton.setTag(repeatMode);
        switch (repeatMode) {
            case RepeatMode.REPEAT_INDEFINITELY:
                setButtonState(mRepeatButton, R.drawable.ic_repeat, R.string.audio_player_repeat_all);
                break;
            case RepeatMode.REPEAT_SINGLE_INDEFINITELY:
                setButtonState(mRepeatButton, R.drawable.ic_repeat_one, R.string.audio_player_repeat_one);
                break;
            case RepeatMode.NO_REPEAT:
            default:
                setButtonState(mRepeatButton, R.drawable.ic_repeat_off, R.string.audio_player_repeat_off);
                break;
        }
    }

    private void setPlayPauseButtonState(@DrawableRes int iconRes, @StringRes int labelRes) {
        setButtonState(mPlayPauseButton, iconRes, labelRes);
    }

    private void setButtonState(@NonNull ImageView button, @DrawableRes int iconRes, @StringRes int labelRes) {
        button.setImageResource(iconRes);
        setControlLabel(button, labelRes);
    }

    private void setControlLabel(@NonNull View control, @StringRes int labelRes) {
        String label = getString(labelRes);
        control.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            control.setTooltipText(label);
        }
    }
}
