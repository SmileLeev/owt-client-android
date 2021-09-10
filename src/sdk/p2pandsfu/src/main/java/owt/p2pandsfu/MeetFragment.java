package owt.p2pandsfu;

import static org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
import static owt.base.MediaCodecs.AudioCodec.OPUS;
import static owt.base.MediaCodecs.AudioCodec.PCMU;
import static owt.base.MediaCodecs.VideoCodec.VP8;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import owt.base.ActionCallback;
import owt.base.AudioCodecParameters;
import owt.base.ContextInitialization;
import owt.base.LocalStream;
import owt.base.MediaConstraints;
import owt.base.OwtError;
import owt.base.VideoCodecParameters;
import owt.base.VideoEncodingParameters;
import owt.conference.ConferenceClient;
import owt.conference.ConferenceClientConfiguration;
import owt.conference.ConferenceInfo;
import owt.conference.Participant;
import owt.conference.Publication;
import owt.conference.PublishOptions;
import owt.conference.RemoteMixedStream;
import owt.conference.RemoteStream;
import owt.conference.SubscribeOptions;
import owt.conference.Subscription;
import owt.p2pandsfu.bean.Message;
import owt.p2pandsfu.bean.UserInfo;
import owt.p2pandsfu.connection.Connection;
import owt.p2pandsfu.p2p.P2PHelper;
import owt.p2pandsfu.p2p.P2PPublication;
import owt.p2pandsfu.p2p.P2PRemoteStream;
import owt.p2pandsfu.p2p.P2PSocket;
import owt.p2pandsfu.utils.HttpUtils;
import owt.p2pandsfu.utils.OwtVideoCapturer;
import owt.p2pandsfu.view.LargeVideo;
import owt.p2pandsfu.view.ThumbnailAdapter;

public class MeetFragment extends Fragment {
    private static final String TAG = "MeetFragment";
    private static final String ARG_SERVER_URL = "ARG_SERVER_URL";
    private static final String ARG_ROOM_ID = "ARG_ROOM_ID";
    private static final String ARG_USER_INFO = "ARG_USER_INFO";
    private static boolean contextHasInitialized = false;
    private static EglBase rootEglBase;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());
    private String serverUrl;
    private String roomId;
    private ConferenceClient conferenceClient;
    private ConferenceInfo conferenceInfo;
    private LocalStream localStream;
    private OwtVideoCapturer capturer;
    private P2PHelper p2PHelper = new P2PHelper();
    private UserInfo selfInfo;
    private HashMap<String, UserInfo> userInfoMap = new HashMap<>();
    private RecyclerView rvSmall;
    private LargeVideo largeVideo;
    private ThumbnailAdapter thumbnailAdapter;
    private boolean audioMuted = false;
    private boolean videoMuted = false;

    public MeetFragment() {
        // Required empty public constructor
    }

    private void initView(View rootView) {
        initEgl();
        rvSmall = rootView.findViewById(R.id.rvSmall);
        largeVideo = rootView.findViewById(R.id.largeVideo);
        EglBase.Context eglBaseContext = rootEglBase.getEglBaseContext();
        largeVideo.initEgl(eglBaseContext);
        thumbnailAdapter = new ThumbnailAdapter(eglBaseContext, largeVideo.getParticipantView());
        rvSmall.setAdapter(thumbnailAdapter);
        initToolbox(rootView.findViewById(R.id.llToolbox));
    }

    private void initToolbox(ViewGroup llToolbox) {
        llToolbox.findViewById(R.id.btnAudioMute).setOnClickListener(v -> {
            audioMuted = !audioMuted;
            if (audioMuted) {
                v.setBackgroundColor(Color.parseColor("#ff0000"));
                localStream.disableAudio();
            } else {
                v.setBackgroundColor(Color.parseColor("#00ff00"));
                localStream.enableAudio();
            }
        });
        llToolbox.findViewById(R.id.btnVideoMute).setOnClickListener(v -> {
            videoMuted = !videoMuted;
            if (videoMuted) {
                v.setBackgroundColor(Color.parseColor("#ff0000"));
                localStream.disableVideo();
            } else {
                v.setBackgroundColor(Color.parseColor("#00ff00"));
                localStream.enableVideo();
            }
        });
        llToolbox.findViewById(R.id.btnHangUp).setOnClickListener(v -> {
            requireActivity().finish();
        });
        llToolbox.findViewById(R.id.btnCameraSwitch).setOnClickListener(v -> {
            capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    Log.d(TAG, "onCameraSwitchDone() called with: isFrontCamera = [" + isFrontCamera + "]");
                }

                @Override
                public void onCameraSwitchError(String errorDescription) {

                }
            });
        });
    }

    private void initLocal() {
        boolean vga = true;
        capturer = OwtVideoCapturer.create(vga ? 640 : 1280, vga ? 480 : 720, 30, true,
                true);
        localStream = new LocalStream(capturer,
                new MediaConstraints.AudioTrackConstraints());
        thumbnailAdapter.initLocal(localStream, selfInfo);
        p2PHelper.setLocal(localStream);
    }

    private void initConferenceClient() {

        PeerConnection.IceServer iceServer = PeerConnection.IceServer
                .builder("stun:stun.l.google.com:19302")
                .createIceServer();
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(iceServer);
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(
                iceServers);
        HttpUtils.setUpINSECURESSLContext();
        rtcConfiguration.continualGatheringPolicy = GATHER_CONTINUALLY;
        ConferenceClientConfiguration configuration
                = ConferenceClientConfiguration.builder()
                .setHostnameVerifier(HttpUtils.hostnameVerifier)
                .setSSLContext(HttpUtils.sslContext)
                .setRTCConfiguration(rtcConfiguration)
                .build();
        conferenceClient = new ConferenceClient(configuration);
        conferenceClient.addObserver(new MyConferenceClientObserver());
        p2PHelper.initClient(new P2PHelper.P2PAttachListener() {
            @Override
            public void onAttach(String participantId, Connection connection, P2PRemoteStream remoteStream) {
                if (disposed()) {
                    return;
                }
                thumbnailAdapter.attachRemoteStream(connection, remoteStream);
            }

            @Override
            public void onDetach(String participantId, P2PRemoteStream remoteStream) {
                if (disposed()) {
                    return;
                }
                thumbnailAdapter.detachRemoteStream(remoteStream);
            }
        });
    }

    private void initEgl() {
        if (!contextHasInitialized) {
            rootEglBase = EglBase.create();
            ContextInitialization.create()
                    .setApplicationContext(requireContext().getApplicationContext())
                    .addIgnoreNetworkType(ContextInitialization.NetworkType.LOOPBACK)
                    .setVideoHardwareAccelerationOptions(
                            rootEglBase.getEglBaseContext(),
                            rootEglBase.getEglBaseContext())
                    .initialize();
            contextHasInitialized = true;
        }
    }

    private void sfuPublish() {
        ActionCallback<Publication> callback = new ActionCallback<Publication>() {
            @Override
            public void onSuccess(final Publication result) {

                runOnUiThread(() -> {
                    if (disposed()) {
                        return;
                    }
                    thumbnailAdapter.updateLocal(Connection.getInstance(result));
                });

                try {
                    JSONArray mixBody = new JSONArray();
                    JSONObject body = new JSONObject();
                    body.put("op", "add");
                    body.put("path", "/info/inViews");
                    body.put("value", "common");
                    mixBody.put(body);

                    String uri = serverUrl
                            + "/rooms/" + conferenceInfo.id()
                            + "/streams/" + result.id();
                    HttpUtils.request(uri, "PATCH", mixBody.toString(), true);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(final OwtError error) {
                Log.d(TAG, "onFailure() called with: error = [" + error.errorMessage + "]");
            }
        };

        conferenceClient.publish(localStream, setPublishOptions(), callback);
    }

    public PublishOptions setPublishOptions() {
        return PublishOptions.builder()
                .addVideoParameter(new VideoEncodingParameters(VP8))
                .build();
    }

    private void join() {

        JSONObject joinBody = new JSONObject();
        try {
            joinBody.put("role", "presenter");
            joinBody.put("username", "user");
            joinBody.put("room", roomId.equals("") ? "" : roomId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String uri = serverUrl + "/createToken/";
        String token = HttpUtils.request(uri, "POST", joinBody.toString(), true);

        conferenceClient.join(token, new ActionCallback<ConferenceInfo>() {
            @Override
            public void onSuccess(ConferenceInfo conferenceInfo) {
                MeetFragment.this.conferenceInfo = conferenceInfo;
                p2PHelper.onJoinSuccess(conferenceInfo, new P2PSocket(conferenceClient), () -> {
                    sfuPublish();
                });
                selfInfo.setParticipantId(conferenceInfo.self().id);
                thumbnailAdapter.updateLocal(conferenceInfo.self().id);
                userInfoMap.put(selfInfo.getParticipantId(), selfInfo);
                sendSelfInfo(null);
                for (RemoteStream remoteStream : conferenceInfo.getRemoteStreams()) {
                    subscribeForward(remoteStream);
                }
                for (Participant participant : conferenceInfo.getParticipants()) {
                    runOnUiThread(() -> {
                        if (disposed()) {
                            return;
                        }
                        thumbnailAdapter.add(participant.id, userInfoMap.get(participant.id));
                    });
                    observeLeft(participant);
                }
            }

            @Override
            public void onFailure(OwtError e) {
                if (disposed()) {
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setMessage("join room failed")
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            requireActivity().finish();
                        });
            }
        });
    }

    private void observeLeft(Participant participant) {
        participant.addObserver(new Participant.ParticipantObserver() {
            @Override
            public void onLeft() {
                participant.removeObserver(this);
                UserInfo userInfo = userInfoMap.remove(participant.id);
                Log.d(TAG, "onLeft() called: participant.id = " + participant.id + ", userInfo = " + userInfo);
                runOnUiThread(() -> {
                    onMemberLeft(participant.id, userInfo);
                });
            }
        });
    }

    public void subscribeForward(RemoteStream remoteStream) {
        if (remoteStream instanceof RemoteMixedStream) {
            // skip mix stream,
            return;
        }
        if (TextUtils.equals(remoteStream.origin(), selfInfo.getParticipantId())) {
            // skip self stream,
            return;
        }
        SubscribeOptions.VideoSubscriptionConstraints.Builder videoOptionBuilder =
                SubscribeOptions.VideoSubscriptionConstraints.builder();

        VideoCodecParameters vcp = new VideoCodecParameters(VP8);

        SubscribeOptions.VideoSubscriptionConstraints videoOption = videoOptionBuilder
                .addCodec(vcp)
                .build();

        SubscribeOptions.AudioSubscriptionConstraints audioOption =
                SubscribeOptions.AudioSubscriptionConstraints.builder()
                        .addCodec(new AudioCodecParameters(OPUS))
                        .addCodec(new AudioCodecParameters(PCMU))
                        .build();

        SubscribeOptions options = SubscribeOptions.builder(true, true)
                .setAudioOption(audioOption)
                .setVideoOption(videoOption)
                .build();

        conferenceClient.subscribe(remoteStream, options,
                new ActionCallback<Subscription>() {
                    @Override
                    public void onSuccess(Subscription result) {
                        if (disposed()) {
                            return;
                        }
                        thumbnailAdapter.attachRemoteStream(Connection.getInstance(result), remoteStream);
                    }

                    @Override
                    public void onFailure(OwtError error) {
                        Log.e(TAG, "Failed to subscribe "
                                + error.errorMessage);
                    }
                });
    }

    private void sendSelfInfo(String to) {
        if (disposed()) {
            return;
        }
        Message message = new Message(Message.TYPE_USER_INFO, JSON.toJSONString(selfInfo));
        conferenceClient.send(to, JSON.toJSONString(message), new ActionCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "send selfInfo: success");
            }

            @Override
            public void onFailure(OwtError error) {
                Log.d(TAG, "send selfInfo: error = [" + error.errorMessage + "]");
            }
        });
    }

    @UiThread
    private void onMemberJoined(String participantId, @Nullable UserInfo userInfo) {
        if (disposed()) {
            return;
        }
        if (!selfInfo.equals(userInfo)) {
            Toast.makeText(requireContext(), getUsername(participantId, userInfo) + " joined", Toast.LENGTH_SHORT).show();
        }
        thumbnailAdapter.add(participantId, userInfo);
    }

    private String getUsername(String participantId, @Nullable UserInfo userInfo) {
        if (userInfo != null) {
            return userInfo.getUsername();
        }
        return participantId;
    }

    @UiThread
    private void onMemberUpdate(UserInfo userInfo) {
        Log.d(TAG, "onMemberUpdate() called with: userInfo = [" + userInfo + "]");
        if (disposed()) {
            return;
        }
        thumbnailAdapter.update(userInfo);
    }

    @UiThread
    private void onMemberLeft(String participantId, @Nullable UserInfo userInfo) {
        if (disposed()) {
            return;
        }
        if (!selfInfo.equals(userInfo)) {
            Toast.makeText(requireContext(), getUsername(participantId, userInfo) + " left", Toast.LENGTH_SHORT).show();
        }
        thumbnailAdapter.remove(participantId, userInfo);
    }

    private boolean disposed() {
        Activity activity = getActivity();
        return activity == null || activity.isFinishing();
    }

    @Override
    public void onDestroy() {
        conferenceClient.leave();
        super.onDestroy();
    }

    public static MeetFragment newInstance(String serverUrl, String roomId, UserInfo userInfo) {
        MeetFragment fragment = new MeetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_URL, serverUrl);
        args.putString(ARG_ROOM_ID, roomId);
        args.putString(ARG_USER_INFO, JSON.toJSONString(userInfo));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initLocal();
        initConferenceClient();
        executor.execute(this::join);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            serverUrl = getArguments().getString(ARG_SERVER_URL);
            roomId = getArguments().getString(ARG_ROOM_ID);
            selfInfo = JSON.parseObject(getArguments().getString(ARG_USER_INFO), UserInfo.class);
        }
    }

    private void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_meet, container, false);
        initView(rootView);
        return rootView;
    }

    private class MyConferenceClientObserver implements ConferenceClient.ConferenceClientObserver {
        @Override
        public void onStreamAdded(RemoteStream remoteStream) {
            if (TextUtils.equals(remoteStream.origin(), selfInfo.getParticipantId())) {
                // skip self stream,
                return;
            }
            subscribeForward(remoteStream);
            remoteStream.addObserver(new owt.base.RemoteStream.StreamObserver() {
                @Override
                public void onEnded() {
                    Log.d(TAG, "onEnded() called: remoteStream.id = " + remoteStream.id() + ", userInfo = " + userInfoMap.get(remoteStream.origin()));
                    if (disposed()) {
                        return;
                    }
                    thumbnailAdapter.detachRemoteStream(remoteStream);
                }

                @Override
                public void onUpdated() {
                    Log.d(TAG, "onUpdated() called");
                }
            });
        }

        @Override
        public void onParticipantJoined(Participant participant) {
            Log.d(TAG, "onParticipantJoined() called with: participant = [" + participant.id + "]");
            p2PHelper.onParticipantJoined(participant.id, new ActionCallback<P2PPublication>() {
                @Override
                public void onSuccess(P2PPublication result) {
                    Log.d(TAG, "onSuccess() called with: result = [" + result.id() + "]");
                }

                @Override
                public void onFailure(OwtError error) {
                    Log.d(TAG, "onFailure() called with: error = [" + error.errorMessage + "]");
                }
            });
            sendSelfInfo(participant.id);
            runOnUiThread(() -> {
                onMemberJoined(participant.id, userInfoMap.get(participant.id));
            });
            observeLeft(participant);
        }

        @Override
        public void onMessageReceived(String message, String from, String to) {
            Log.d(TAG, "onMessageReceived() called with: message = [" + message + "], from = [" + from + "], to = [" + to + "]");
            Message messageBean = Message.fromJson(message);
            if (messageBean.getType() == Message.TYPE_USER_INFO) {
                UserInfo userInfo = messageBean.getDataBean(UserInfo.class);
                boolean exists = userInfoMap.containsKey(userInfo.getParticipantId());
                userInfoMap.put(userInfo.getParticipantId(), userInfo);
                runOnUiThread(() -> {
                    if (!exists) {
                        onMemberJoined(userInfo.getParticipantId(), userInfo);
                    } else {
                        onMemberUpdate(userInfo);
                    }
                });
            }
        }

        @Override
        public void onServerDisconnected() {
            p2PHelper.onServerDisconnected();

            if (capturer != null) {
                capturer.stopCapture();
                capturer.dispose();
                capturer = null;
            }

            if (localStream != null) {
                localStream.dispose();
                localStream = null;
            }
            selfInfo = null;
            thumbnailAdapter = null;
            userInfoMap.clear();
        }
    }
}