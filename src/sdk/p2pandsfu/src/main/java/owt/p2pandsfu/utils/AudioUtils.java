package owt.p2pandsfu.utils;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public class AudioUtils {
    private static final String TAG = "AudioUtils";
    private static AudioManager audioManager;
    private static boolean speakerphoneOn;

    public static void init(Context context) {
        Log.d(TAG, "init() called with: context = [" + context + "]");
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        speakerphoneOn = audioManager.isSpeakerphoneOn();
        openSpeaker();
    }

    public static void openSpeaker() {
        Log.d(TAG, "openSpeaker() called");
        if (!audioManager.isSpeakerphoneOn()) {
            audioManager.setSpeakerphoneOn(true);
        }
    }

    public static void closeSpeaker() {
        Log.d(TAG, "closeSpeaker() called");
        if (audioManager.isSpeakerphoneOn()) {
            audioManager.setSpeakerphoneOn(false);
        }
    }
    public static void release() {
        Log.d(TAG, "release() called");
        audioManager.setSpeakerphoneOn(speakerphoneOn);
        audioManager = null;
    }
}
