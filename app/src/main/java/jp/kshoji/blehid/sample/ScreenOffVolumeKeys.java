package jp.kshoji.blehid.sample;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The volume keys while the phone is asleep. Android passes them to an app only through a playing media session
 * that sets its own volume, as a casting app does for a TV's, so while active this one claims remote playback and
 * turns each press into a page. It posts no notification, so no player shows, and Android shows no volume panel
 * while the phone is asleep. The app must never play a sound: Android would then send headset and car play/pause
 * buttons to this session too.
 */
final class ScreenOffVolumeKeys {
    private static final String TAG = "ScreenOffVolumeKeys";

    /**
     * Receives the presses
     */
    interface Listener {
        /**
         * @param up true: volume up, false: volume down
         */
        void onVolumeKey(boolean up);
    }

    // Keeps the phone awake to send the page key, and to send it again to a Kindle that reconnects just after:
    // KindleClickPeripheral repeats a page key up to 4 s after the press. Android 13+ honours it after each press;
    // older versions may ignore it once the screen has been off for a minute.
    private static final long WAKE_LOCK_MS = 5000;
    // A key held down can arrive as repeated presses (on the always-on display, or from some phones' drivers);
    // one this close to the last, before the key is released, is the same press
    private static final long KEY_REPEAT_TIMEOUT_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSession session;
    private final AudioManager audioManager;
    private final PowerManager.WakeLock wakeLock;
    private final Listener listener;
    private final CallWatcher callWatcher;
    private boolean enabled;
    private boolean active;
    private boolean keyDown;
    private long lastStepTime;

    ScreenOffVolumeKeys(@NonNull final Context context, @NonNull final Listener listener) {
        this.listener = listener;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenKindleClick:pageKey");
        wakeLock.setReferenceCounted(false);

        session = new MediaSession(context, TAG);
        // The session passes volume steps on only while it has a callback
        session.setCallback(new MediaSession.Callback() {
        }, handler);
        session.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 2, 1) {
            @Override
            public void onAdjustVolume(final int direction) {
                // To the main thread: Android 5.0 calls this on a binder thread
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onVolumeStep(direction);
                    }
                });
            }
        });
        // What a Bluetooth car display or a watch shows, if it lists the session
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, context.getString(R.string.app_name))
                .build());
        setPlaybackState(PlaybackState.STATE_STOPPED);
        callWatcher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new CallWatcher(context) : null;
    }

    /**
     * @return true during a call, or while one rings: the volume keys are the call's then
     */
    static boolean isInCall(@NonNull final AudioManager audioManager) {
        return audioManager.getMode() != AudioManager.MODE_NORMAL;
    }

    /**
     * Take the volume keys (while the phone is asleep), or give them back. During a call they are the call's.
     */
    void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        update();
    }

    private void update() {
        final boolean active = enabled && !isInCall(audioManager);
        if (this.active == active) {
            return;
        }
        this.active = active;
        keyDown = false;
        if (active) {
            // Starting to play puts the session ahead of any other that is playing
            setPlaybackState(PlaybackState.STATE_PLAYING);
            session.setActive(true);
        } else {
            session.setActive(false);
            setPlaybackState(PlaybackState.STATE_STOPPED);
        }
    }

    void release() {
        setEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callWatcher.release();
        }
        session.release();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void setPlaybackState(final int state) {
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0)
                .build());
    }

    /**
     * @param direction +1 for a volume up press, -1 for down, 0 when it is released (reported from Android 7.0)
     */
    private void onVolumeStep(final int direction) {
        if (!active) {
            return;
        }
        if (direction == 0) {
            keyDown = false;
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        final boolean repeat = keyDown && now - lastStepTime < KEY_REPEAT_TIMEOUT_MS;
        // Without releases (Android 5.0 to 6.0) every step is a press
        keyDown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        lastStepTime = now;
        if (repeat) {
            return;
        }
        final int mode = audioManager.getMode();
        if (mode != AudioManager.MODE_NORMAL) {
            // A call: phone calls take the keys before the session, but some calling apps' don't. Give the keys
            // back first, or the step passed on would come straight back here. A ringing call needs no step.
            update();
            if (mode != AudioManager.MODE_RINGTONE) {
                audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI);
            }
            return;
        }
        wakeLock.acquire(WAKE_LOCK_MS);
        listener.onVolumeKey(direction > 0);
    }

    /**
     * Gives the keys back as soon as a call starts ringing, before any key reaches the session, and takes them
     * again when it ends
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private final class CallWatcher implements AudioManager.OnModeChangedListener {
        CallWatcher(@NonNull final Context context) {
            audioManager.addOnModeChangedListener(context.getMainExecutor(), this);
        }

        @Override
        public void onModeChanged(final int mode) {
            update();
        }

        void release() {
            audioManager.removeOnModeChangedListener(this);
        }
    }
}
