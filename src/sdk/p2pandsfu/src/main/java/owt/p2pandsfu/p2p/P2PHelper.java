package owt.p2pandsfu.p2p;

import static owt.base.MediaCodecs.VideoCodec.H264;
import static owt.base.MediaCodecs.VideoCodec.H265;
import static owt.base.MediaCodecs.VideoCodec.VP8;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import owt.base.ActionCallback;
import owt.base.LocalStream;
import owt.base.OwtError;
import owt.base.VideoEncodingParameters;
import owt.conference.ConferenceInfo;
import owt.conference.Participant;
import owt.p2pandsfu.connection.Connection;

public class P2PHelper {
    public static final String TAG = "P2PHelper";
    private P2PClient p2PClient;
    private boolean enabled = true;
    private ConferenceInfo conferenceInfo;
    private LocalStream localStream;
    private Map<String, P2PRemoteStream> remoteStreamMap = new WeakHashMap<>();
    private Map<String, P2PPublication> publicationMap = new WeakHashMap<>();
    private OnP2PDisabledListener onP2PDisabledListener;
    private P2PAttachListener attachListener;
    private Map<String, ActionCallback<P2PPublication>> publishCache = new HashMap();

    public void onServerDisconnected() {
        p2PClient.onServerDisconnected();
    }

    public void initClient(P2PAttachListener attachListener) {
        this.attachListener = attachListener;
        VideoEncodingParameters h264 = new VideoEncodingParameters(H264);
        VideoEncodingParameters h265 = new VideoEncodingParameters(H265);
        VideoEncodingParameters vp8 = new VideoEncodingParameters(VP8);
        P2PClientConfiguration configuration = P2PClientConfiguration.builder()
                .addVideoParameters(h264)
                .addVideoParameters(vp8)
                .addVideoParameters(h265)
                .build();
        p2PClient = new P2PClient(configuration, new P2PSocketSignalingChannel());
        p2PClient.addObserver(new P2PClient.P2PClientObserver() {
            @Override
            public void onStreamAdded(P2PRemoteStream remoteStream) {
                Log.d(TAG, "onStreamAdded() called with: remoteStream = [" + remoteStream.id() + "]");
                String participantId = remoteStream.origin();
                remoteStreamMap.put(participantId, remoteStream);
                remoteStream.addObserver(new owt.base.RemoteStream.StreamObserver() {
                    @Override
                    public void onEnded() {
                        Log.d(TAG, "onEnded() called");
                        if (attachListener != null) {
                            attachListener.onDetach(participantId, remoteStream);
                        }
                    }

                    @Override
                    public void onUpdated() {
                        Log.d(TAG, "onUpdated() called");
                    }
                });
                if (publicationMap.containsKey(participantId)) {
                    callAttach(participantId);
                } else {
                    publish(participantId, new P2PPublicationActionCallback());
                }
            }

            @Override
            public void onDataReceived(String peerId, String message) {
                Log.d(TAG, "onDataReceived() called with: peerId = [" + peerId + "], message = [" + message + "]");
            }
        });
    }

    private void callAttach(String participantId) {
        P2PPublication publication = publicationMap.get(participantId);
        if (publication == null) {
            Log.d(TAG, "callAttach: publication == null, participantId: " + participantId);
            return;
        }
        P2PRemoteStream remoteStream = remoteStreamMap.get(participantId);
        if (remoteStream == null) {
            Log.d(TAG, "callAttach: remoteStream == null, participantId: " + participantId);
            return;
        }
        if (attachListener != null) {
            attachListener.onAttach(participantId, Connection.getInstance(publication), remoteStream);
        }
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

    public void onParticipantJoined(String participantId) {
        updateEnabled();
        p2PClient.addAllowedRemotePeer(participantId);
        publish(participantId, new P2PPublicationActionCallback());
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
        if (!publishCache.isEmpty()) {
            for (Map.Entry<String, ActionCallback<P2PPublication>> entry : publishCache.entrySet()) {
                publish(entry.getKey(), entry.getValue());
            }
            publishCache.clear();
        }
    }

    public void stopPublish() {
        Log.d(TAG, "stopPublish() called");
        for (P2PPublication publication : publicationMap.values()) {
            publication.stop();
        }
        publicationMap.clear();
        localStream = null;
    }

    public void republish() {
        Log.d(TAG, "republish() called");
        for (Participant participant : conferenceInfo.getParticipants()) {
            if (TextUtils.equals(participant.id, conferenceInfo.self().id)) {
                continue;
            }
            publish(participant.id, new P2PPublicationActionCallback());
        }
    }

    public void publish(String participantId, ActionCallback<P2PPublication> callback) {
        if (!isEnabled()) {
            return;
        }
        if (publicationMap.containsKey(participantId)) {
            Log.d(TAG, "publish: duplicate publish");
            return;
        }
        Log.d(TAG, "publish() called with: participant = [" + participantId + "]");
        if (localStream == null) {
            publishCache.put(participantId, callback);
            return;
        }
        p2PClient.publish(participantId, localStream, new ActionCallback<P2PPublication>() {
            @Override
            public void onSuccess(P2PPublication result) {
                publicationMap.put(participantId, result);
                callAttach(participantId);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(OwtError error) {
                callback.onFailure(error);
            }
        });
    }

    public interface OnP2PDisabledListener {
        void onP2PDisabled();
    }

    public interface P2PAttachListener {
        void onAttach(String participantId, Connection connection, P2PRemoteStream remoteStream);

        void onDetach(String participantId, P2PRemoteStream remoteStream);
    }

    private class P2PPublicationActionCallback implements ActionCallback<P2PPublication> {
        @Override
        public void onSuccess(P2PPublication result) {
            Log.d(TAG, "onSuccess() called with: result = [" + result.id() + "]");
        }

        @Override
        public void onFailure(OwtError error) {
            Log.d(TAG, "onFailure() called with: error = [" + error.errorMessage + "]");
            setEnabled(false);
            if (onP2PDisabledListener != null) {
                onP2PDisabledListener.onP2PDisabled();
            }
        }
    }
}
