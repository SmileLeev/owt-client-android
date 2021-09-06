package owt.p2pandsfu.p2p;

import static owt.base.MediaCodecs.VideoCodec.H264;
import static owt.base.MediaCodecs.VideoCodec.H265;
import static owt.base.MediaCodecs.VideoCodec.VP8;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import owt.base.ActionCallback;
import owt.base.LocalStream;
import owt.base.OwtError;
import owt.base.VideoEncodingParameters;
import owt.conference.ConferenceInfo;
import owt.conference.Participant;

public class P2PHelper {
    public static final String TAG = "P2PHelper";
    private P2PClient p2PClient;
    private boolean enabled = true;
    private ConferenceInfo conferenceInfo;
    private LocalStream localStream;
    private Map<String, P2PPublication> publicationMap = new HashMap<>();
    private OnP2PDisabledListener onP2PDisabledListener;

    public void onServerDisconnected() {
        p2PClient.onServerDisconnected();
    }

    public void initClient(P2PClient.P2PClientObserver observer) {
        VideoEncodingParameters h264 = new VideoEncodingParameters(H264);
        VideoEncodingParameters h265 = new VideoEncodingParameters(H265);
        VideoEncodingParameters vp8 = new VideoEncodingParameters(VP8);
        P2PClientConfiguration configuration = P2PClientConfiguration.builder()
                .addVideoParameters(h264)
                .addVideoParameters(vp8)
                .addVideoParameters(h265)
                .build();
        p2PClient = new P2PClient(configuration, new P2PSocketSignalingChannel());
        p2PClient.addObserver(observer);
    }

    public void onJoinSuccess(ConferenceInfo conferenceInfo, P2PSocket p2PSocket, OnP2PDisabledListener onP2PDisabledListener) {
        this.conferenceInfo = conferenceInfo;
        this.onP2PDisabledListener = onP2PDisabledListener;
        updateEnabled();
        for (Participant participant : conferenceInfo.getParticipants()) {
            p2PClient.addAllowedRemotePeer(participant.id);
        }
        p2PClient.connect(p2PSocket, conferenceInfo.self().id);
    }

    public void onParticipantJoined(String participantId, ActionCallback<P2PPublication> callback) {
        updateEnabled();
        p2PClient.addAllowedRemotePeer(participantId);
        publish(participantId, callback);
    }

    private void updateEnabled() {
        if (!isEnabled()) {
            return;
        }
        setEnabled(conferenceInfo.getParticipants().size() <= 2);
        if (!isEnabled()) {
            onServerDisconnected();
            if (onP2PDisabledListener != null) {
                onP2PDisabledListener.onP2PDisabled();
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLocal(LocalStream localStream) {
        this.localStream = localStream;
    }

    public void publish(String participantId, ActionCallback<P2PPublication> callback) {
        if (!isEnabled()) {
            return;
        }
        Log.d(TAG, "publish() called with: participant = [" + participantId + "]");
        p2PClient.publish(participantId, localStream, callback);
    }

    public interface OnP2PDisabledListener {
        void onP2PDisabled();
    }
}
