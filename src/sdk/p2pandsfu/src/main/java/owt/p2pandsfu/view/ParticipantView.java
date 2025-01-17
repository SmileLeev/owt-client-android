package owt.p2pandsfu.view;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.Request;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;

import java.util.Objects;

import owt.base.LocalStream;
import owt.base.Stream;
import owt.p2pandsfu.BuildConfig;
import owt.p2pandsfu.R;
import owt.p2pandsfu.bean.UserInfo;

public class ParticipantView extends RelativeLayout {
    private static final String TAG = "ParticipantView";
    private SurfaceViewRenderer renderer;
    private Stream stream;
    @Nullable
    private UserInfo userInfo;
    private ImageView ivAvatar;
    private TextView tvDebug;
    private boolean onTop;
    private Boolean showVideo = null;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isFrontCamera;
    private OnStreamUpdateListener onStreamUpdateListener;

    public ParticipantView(Context context) {
        super(context);
        init();
    }

    public ParticipantView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ParticipantView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ParticipantView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public SurfaceViewRenderer getRenderer() {
        return renderer;
    }

    private void init() {
        View.inflate(getContext(), R.layout.view_participant, this);
        renderer = findViewById(R.id.renderer);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvDebug = findViewById(R.id.tvDebug);
        if (!BuildConfig.DEBUG) {
            tvDebug.setVisibility(View.GONE);
        }
    }

    public void initEgl(EglBase.Context eglBaseContext) {
        Log.d(TAG, "initEgl() called with: this@" + Integer.toHexString(this.hashCode())
                + " eglBaseContext = [" + Integer.toHexString(eglBaseContext.hashCode()) + "]");
        renderer.init(eglBaseContext, null);
        renderer.setMirror(true);
        renderer.setEnableHardwareScaler(true);
        renderer.setZOrderMediaOverlay(true);
    }

    public void setOnTop(boolean onTop) {
        this.onTop = onTop;
        renderer.setZOrderMediaOverlay(onTop);
    }

    public void attachStream(Stream stream) {
        Stream oldStream = this.stream;
        if (stream != null) {
            if (oldStream != stream) {
                if (oldStream != null) {
                    _detachStream();
                }
                _attachStream(stream);
            }
        } else {
            if (oldStream != null) {
                _detachStream();
            }
        }
        updateAvatar();
    }

    public void detachStream(Stream stream) {
        Stream oldStream = this.stream;
        if (oldStream == stream) {
            _detachStream();
        }
        updateAvatar();
    }

    private void _attachStream(Stream stream) {
        this.stream = stream;
        try {
            stream.attach(renderer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        onSwitchCamera(isFrontCamera);
        updateAvatar();
    }

    private boolean notUiThread() {
        return Looper.myLooper() != uiHandler.getLooper();
    }

    public final void runOnUiThread(Runnable action) {
        if (notUiThread()) {
            uiHandler.post(action);
        } else {
            action.run();
        }
    }

    @UiThread
    public void updateAvatar() {
        Log.d(TAG, "updateAvatar() called: stream is null " + (stream == null));
        if (notUiThread()) {
            runOnUiThread(this::updateAvatar);
            return;
        }
        Boolean oldShowVideo = showVideo;
        boolean newShowVideo = stream != null && stream.videoEnabled() && (userInfo == null || !userInfo.isVideoMuted());
        Log.d(TAG, String.format("updateAvatar: old=%s, new=%s, userInfo=%s", oldShowVideo, newShowVideo, userInfo));
        if (oldShowVideo != null && oldShowVideo && newShowVideo) {
            return;
        }
        showVideo = newShowVideo;
        if (newShowVideo) {
            ivAvatar.setVisibility(View.GONE);
            renderer.setZOrderMediaOverlay(onTop);
        } else {
            ivAvatar.setVisibility(View.VISIBLE);
            if (userInfo != ivAvatar.getTag(R.id.avatar_tag)) {
                showAvatar();
            }
            renderer.setZOrderMediaOverlay(false);
        }
        if (onStreamUpdateListener != null) {
            onStreamUpdateListener.onStreamUpdate(stream != null && stream.audioEnabled(),
                    stream != null && stream.videoEnabled());
        }
    }

    private void _detachStream() {
        renderer.clearImage();
        try {
            stream.detach(renderer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.stream = null;
        updateAvatar();
    }

    public void setUserInfo(@Nullable String participantId, @Nullable UserInfo userInfo) {
        Log.d(TAG, "setUserInfo() called with: participantId = [" + participantId + "], userInfo = [" + userInfo + "]");
        this.userInfo = userInfo;
        runOnUiThread(() -> {
            tvDebug.setText(participantId);
            updateAvatar();
        });
    }

    @Nullable
    public UserInfo getUserInfo() {
        return userInfo;
    }

    private void showAvatar() {
        ivAvatar.setTag(R.id.avatar_tag, userInfo);
        if (userInfo == null || TextUtils.isEmpty(userInfo.getAvatarUrl())) {
            if (ivAvatar.getTag() instanceof Request) {
                ((Request) ivAvatar.getTag()).clear();
            }
            ivAvatar.setImageResource(R.drawable.default_avatar);
            return;
        }
        Log.d(TAG, "load avatar: url = [" + userInfo.getAvatarUrl() + "]");
        if (getContext() instanceof Activity && ((Activity) getContext()).isFinishing()) {
            return;
        }
        Glide.with(getContext()).load(userInfo.getAvatarUrl()).into(ivAvatar);
    }

    public Stream getStream() {
        return stream;
    }

    public void onSwitchCamera(boolean isFrontCamera) {
        this.isFrontCamera = isFrontCamera;
        if (renderer != null) {
            renderer.setMirror(stream instanceof LocalStream && isFrontCamera);
        }
    }

    public void release() {
        Log.d(TAG, "release() called with: this@" + Integer.toHexString(this.hashCode())
                + " renderer = [" + (renderer == null ? null : Integer.toHexString(renderer.hashCode())) + "]");
        if (renderer == null) {
            return;
        }
        _detachStream();
        renderer.getHolder().getSurface().release();
        renderer.release();
        renderer = null;
    }

    public void setOnStreamUpdateListener(OnStreamUpdateListener onStreamUpdateListener) {
        this.onStreamUpdateListener = onStreamUpdateListener;
    }

    public interface OnStreamUpdateListener {
        void onStreamUpdate(boolean audioEnabled, boolean videoEnabled);
    }
}
