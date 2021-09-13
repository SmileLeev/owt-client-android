package owt.p2pandsfu.view;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import org.webrtc.EglBase;

import owt.p2pandsfu.R;

public class Thumbnail extends RelativeLayout {
    private ParticipantView participantView;

    public Thumbnail(Context context) {
        super(context);
        init();
    }

    public Thumbnail(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Thumbnail(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Thumbnail(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ParticipantView getParticipantView() {
        return participantView;
    }

    private void init() {
        View.inflate(getContext(), R.layout.view_tumbnail, this);
        participantView = findViewById(R.id.participant);
        AppCompatActivity activity = (AppCompatActivity) getContext();
        activity.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            void destroy() {
                release();
            }
        });
    }

    public void initEgl(EglBase.Context eglBaseContext) {
        participantView.initEgl(eglBaseContext);
        participantView.setOnTop(true);
    }

    public void release() {
        participantView.release();
    }
}
