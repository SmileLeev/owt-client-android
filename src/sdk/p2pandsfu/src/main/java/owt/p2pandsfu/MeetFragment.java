package owt.p2pandsfu;

import static org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
import static owt.base.MediaCodecs.AudioCodec.OPUS;
import static owt.base.MediaCodecs.AudioCodec.PCMU;
import static owt.base.MediaCodecs.VideoCodec.VP8;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
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
import owt.p2pandsfu.audio.AppRTCAudioManager;
import owt.p2pandsfu.bean.Message;
import owt.p2pandsfu.bean.UserInfo;
import owt.p2pandsfu.connection.Connection;
import owt.p2pandsfu.p2p.P2PHelper;
import owt.p2pandsfu.p2p.P2PRemoteStream;
import owt.p2pandsfu.p2p.P2PSocket;
import owt.p2pandsfu.utils.HttpUtils;
import owt.p2pandsfu.utils.OwtBaseCapturer;
import owt.p2pandsfu.utils.OwtScreenCapturer;
import owt.p2pandsfu.utils.OwtVideoCapturer;
import owt.p2pandsfu.view.LargeVideo;
import owt.p2pandsfu.view.ThumbnailAdapter;

public class MeetFragment extends Fragment {
    private static final String TAG = "MeetFragment";
    private static final int OWT_REQUEST_CODE = 1;
    private static boolean contextHasInitialized = false;
    private static EglBase rootEglBase;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());
    private String serverUrl;
    private String roomId;
    private boolean screenSharing;
    private ConferenceClient conferenceClient;
    private ConferenceInfo conferenceInfo;
    private LocalStream localStream;
    private OwtBaseCapturer capturer;
    private P2PHelper p2PHelper = new P2PHelper();
    private UserInfo selfInfo;
    private HashMap<String, UserInfo> userInfoMap = new HashMap<>();
    private RecyclerView rvSmall;
    private LargeVideo largeVideo;
    private ThumbnailAdapter thumbnailAdapter;
    private MyConferenceClientObserver conferenceClientObserver;
    private boolean publishWait = false;
    private boolean speakerphoneOn = true;
    private AppRTCAudioManager audioManager;
    private View btnScreenShare;
    private View btnAudioRoute;
    private View btnAudioMute;
    private View btnVideoMute;
    private ScreenSharingLifecycleObserver screenSharingLifecycleObserver;

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
        btnScreenShare = llToolbox.findViewById(R.id.btnScreenShare);
        btnScreenShare.setOnClickListener(v -> {
            screenSharing = !screenSharing;
            stopPublish();
            initLocalStream();
            if (p2PHelper.isEnabled()) {
                p2PHelper.republish();
            } else {
                sfuPublish();
            }
        });
        btnAudioRoute = llToolbox.findViewById(R.id.btnAudioRoute);
        btnAudioRoute.setOnClickListener(v -> {
            speakerphoneOn = !speakerphoneOn;
            if (speakerphoneOn) {
                audioManager.setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
            } else {
                audioManager.setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.EARPIECE);
            }
        });
        btnAudioMute = llToolbox.findViewById(R.id.btnAudioMute);
        btnAudioMute.setOnClickListener(v -> {
            selfInfo.setAudioMuted(!selfInfo.isAudioMuted());
            applyAudioMute();
            sendSelfInfo(null);
        });
        btnVideoMute = llToolbox.findViewById(R.id.btnVideoMute);
        btnVideoMute.setOnClickListener(v -> {
            selfInfo.setVideoMuted(!selfInfo.isVideoMuted());
            applyVideoMute();
            thumbnailAdapter.update(selfInfo);
            sendSelfInfo(null);
        });
        llToolbox.findViewById(R.id.btnHangUp).setOnClickListener(v -> {
            requireActivity().finish();
        });
        llToolbox.findViewById(R.id.btnCameraSwitch).setOnClickListener(v -> {
            if (!(capturer instanceof OwtVideoCapturer)) {
                return;
            }
            ((OwtVideoCapturer) capturer).switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
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

    private void applyVideoMute() {
        if (selfInfo.isVideoMuted()) {
            capturer.stopCapture();
            btnVideoMute.setBackgroundColor(Color.parseColor("#ff0000"));
            localStream.disableVideo();
        } else {
            capturer.startCapture();
            btnVideoMute.setBackgroundColor(Color.parseColor("#00ff00"));
            localStream.enableVideo();
        }
    }

    private void applyAudioMute() {
        if (selfInfo.isAudioMuted()) {
            btnAudioMute.setBackgroundColor(Color.parseColor("#ff0000"));
            localStream.disableAudio();
        } else {
            btnAudioMute.setBackgroundColor(Color.parseColor("#00ff00"));
            localStream.enableAudio();
        }
    }

    private void initLocal() {
        getLifecycle().addObserver(new LifecycleObserver() {
            private final Context context = requireContext();

            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            void create() {
                audioManager = AppRTCAudioManager.create(context);
                audioManager.start((selectedAudioDevice, availableAudioDevices) -> {
                    if (selectedAudioDevice == AppRTCAudioManager.AudioDevice.SPEAKER_PHONE) {
                        btnAudioRoute.setBackgroundColor(Color.parseColor("#0000ff"));
                    } else {
                        btnAudioRoute.setBackgroundColor(Color.parseColor("#ff00ff"));
                    }
                });
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            void destroy() {
                audioManager.stop();
            }
        });
        initLocalStream();
    }

    private void stopPublish() {
        if (thumbnailAdapter != null) {
            thumbnailAdapter.stopPublish();
        }
        p2PHelper.stopPublish();
        if (capturer != null) {
            capturer.stopCapture();
            capturer.dispose();
            capturer = null;
        }

        if (localStream != null) {
            localStream.disableVideo();
            localStream.disableAudio();
            localStream.dispose();
            localStream = null;
        }
        if (screenSharingLifecycleObserver != null) {
            screenSharingLifecycleObserver.destroy();
            getLifecycle().removeObserver(screenSharingLifecycleObserver);
            screenSharingLifecycleObserver = null;
        }
    }

    private void initLocalStream() {
        if (screenSharing) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 安卓Q必须要有个特定type的前台服务才能使用屏幕共享，
                screenSharingLifecycleObserver = new ScreenSharingLifecycleObserver();
                getLifecycle().addObserver(screenSharingLifecycleObserver);
            }

            MediaProjectionManager manager =
                    (MediaProjectionManager) requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), OWT_REQUEST_CODE);
        } else {
            boolean vga = true;
            capturer = OwtVideoCapturer.create(vga ? 640 : 1280, vga ? 480 : 720, 30, true,
                    true);
            localStream = new LocalStream(capturer,
                    new MediaConstraints.AudioTrackConstraints());
            if (selfInfo.isAudioMuted()) {
                applyAudioMute();
            }
            if (selfInfo.isVideoMuted()) {
                applyVideoMute();
            }
        }
        thumbnailAdapter.initLocal(localStream, selfInfo);
        p2PHelper.setLocal(localStream);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OWT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            capturer = new OwtScreenCapturer(data, 1280, 720);
            localStream = new LocalStream(capturer,
                    new MediaConstraints.AudioTrackConstraints());
            thumbnailAdapter.initLocal(localStream, selfInfo);
            p2PHelper.setLocal(localStream);
            if (publishWait) {
                sfuPublish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        conferenceClientObserver = new MyConferenceClientObserver();
        conferenceClient.addObserver(conferenceClientObserver);
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
        if (localStream == null) {
            publishWait = true;
            return;
        }

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

    @WorkerThread
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

        if (TextUtils.isEmpty(token)) {
            runOnUiThread(() -> {
                showError("Create token failed", "IO Error");
            });
            return;
        }
        conferenceClient.join(token, new ActionCallback<ConferenceInfo>() {
            @Override
            public void onSuccess(ConferenceInfo conferenceInfo) {
                MeetFragment.this.conferenceInfo = conferenceInfo;
                if (p2PHelper.isEnabled()) {
                    p2PHelper.onJoinSuccess(conferenceInfo, new P2PSocket(conferenceClient), () -> {
                        sfuPublish();
                    });
                } else {
                    sfuPublish();
                }
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
                runOnUiThread(() -> {
                    showError("Join room failed", e.errorMessage);
                });
            }
        });
    }

    @UiThread
    private void showError(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    requireActivity().finish();
                }).show();
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
        if (conferenceClientObserver != null) {
            conferenceClient.removeObserver(conferenceClientObserver);
        }
        conferenceClient.leave();
        release();
        super.onDestroy();
    }

    private void release() {
        if (capturer != null) {
            capturer.stopCapture();
            capturer.dispose();
            capturer = null;
        }

        if (localStream != null) {
            localStream.disableVideo();
            localStream.disableAudio();
            localStream.dispose();
            localStream = null;
        }
        if (largeVideo != null) {
            largeVideo.release();
            largeVideo = null;
        }
        selfInfo = null;
        thumbnailAdapter = null;
        userInfoMap.clear();
    }

    public static MeetFragment newInstance(String serverUrl, String roomId, UserInfo userInfo, boolean screenSharing) {
        MeetFragment fragment = new MeetFragment();
        Bundle args = new Bundle();
        args.putString("serverUrl", serverUrl);
        args.putString("roomId", roomId);
        args.putString("selfInfo", JSON.toJSONString(userInfo));
        args.putBoolean("screenSharing", screenSharing);
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
            serverUrl = getArguments().getString("serverUrl");
            roomId = getArguments().getString("roomId");
            selfInfo = JSON.parseObject(getArguments().getString("selfInfo"), UserInfo.class);
            screenSharing = getArguments().getBoolean("screenSharing", false);
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
            p2PHelper.onParticipantJoined(participant.id);
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
                if (TextUtils.equals(userInfo.getParticipantId(), selfInfo.getParticipantId())) {
                    // skip self,
                    return;
                }
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

            release();
        }
    }

    private class ScreenSharingLifecycleObserver implements LifecycleObserver {
        private final Context context = requireContext();

        @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
        void create() {
            ScreenRecordingService.start(context);
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        void destroy() {
            ScreenRecordingService.stop(context);
        }
    }
}