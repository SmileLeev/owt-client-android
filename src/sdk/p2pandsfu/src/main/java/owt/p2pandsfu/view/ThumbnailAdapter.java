package owt.p2pandsfu.view;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.webrtc.EglBase;

import java.util.ArrayList;
import java.util.List;

import owt.base.LocalStream;
import owt.base.RemoteStream;
import owt.base.Stream;
import owt.conference.RemoteMixedStream;
import owt.p2pandsfu.R;
import owt.p2pandsfu.bean.UserInfo;
import owt.p2pandsfu.connection.Connection;

public class ThumbnailAdapter extends RecyclerView.Adapter<ThumbnailAdapter.ViewHolder> {
    private static final String TAG = "RendererAdapter";
    private final List<Item> data = new ArrayList<>();
    private final EglBase.Context eglBaseContext;
    @NonNull
    private final ParticipantView fullParticipantView;
    @Nullable
    private Item selectedItem;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isFrontCamera;

    public ThumbnailAdapter(EglBase.Context eglBaseContext, @NonNull ParticipantView fullParticipantView) {
        this.eglBaseContext = eglBaseContext;
        this.fullParticipantView = fullParticipantView;
        setHasStableIds(true);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_remote_video, viewGroup, false);
        return new ViewHolder(itemView, eglBaseContext);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        Item item = data.get(i);
        Stream stream = item.stream;
        if (item.participantView != viewHolder.thumbnail.getParticipantView()) {
            _detachStream(stream, item.participantView);
            item.participantView = viewHolder.thumbnail.getParticipantView();
            _attackStream(stream, item.participantView);
        }
        item.participantView.setUserInfo(item.participantId, item.userInfo);
        viewHolder.itemView.setOnClickListener(view -> {
            selectedItem = item;
            updateFullVideo();
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void _attackStream(Stream stream, ParticipantView renderer) {
        if (renderer == null) {
            Log.w(TAG, "_attackStream: renderer not found");
            return;
        }
        renderer.attachStream(stream);
    }

    private void _detachStream(Stream stream, ParticipantView renderer) {
        if (stream == null) {
            Log.w(TAG, "_detachStream: stream not found");
            return;
        }
        if (renderer == null) {
            Log.w(TAG, "_detachStream: renderer not found");
            return;
        }
        renderer.detachStream(stream);
    }
    public void initLocal(LocalStream stream, UserInfo userInfo) {
        Log.d(TAG, "attachLocalStream() called with: stream = [" + (stream == null ? null : stream.id()) + "], userInfo = [" + userInfo + "]");
        Item item = createItem(null, stream);
        item.local = true;
        item.userInfo = userInfo;
        _attackStream(item.stream, item.participantView);
        updateFullVideo();
    }

    public void updateLocal(String participantId) {
        Log.d(TAG, "attachLocalStream() called with: participantId = [" + participantId + "]");
        Item item = getLocalItem();
        item.participantId = participantId;
    }

    public void updateLocal(Connection connection) {
        Log.d(TAG, "attachLocalStream() called with: connection = [" + connection.id() + "]");
        Item item = getLocalItem();
        item.connection = connection;
        updateFullVideo();
    }

    public void attachLocalStream(@NonNull LocalStream stream) {
        Log.d(TAG, "attachLocalStream() called with: stream = [" + stream.id() + "]");
        Item item = getLocalItem();
        item.stream = stream;
        _attackStream(item.stream, item.participantView);
        updateFullVideo();
    }

    @WorkerThread
    public void attachRemoteStream(Connection connection, @NonNull RemoteStream stream) {
        Log.d(TAG, "attachRemoteStream() called with: origin = [" + stream.origin() + "], connection = [" + connection.id() + "], stream = [" + stream.id() + "]");
        if (stream instanceof RemoteMixedStream) {
            return;
        }
        Item item = getOrCreateItem(stream.origin(), stream);
        if (item.local) {
            Log.d(TAG, "attachRemoteStream: ignore local user");
            return;
        }
        stop(item);
        item.stream = stream;
        item.participantId = stream.origin();
        item.connection = connection;
        _attackStream(item.stream, item.participantView);
        updateFullVideo();
    }

    @WorkerThread
    public void detachRemoteStream(RemoteStream stream) {
        Log.d(TAG, "detachRemoteStream() called with: origin = [" + stream.origin() + "], stream = [" + stream.id() + "]");
        Item item = getItemByParticipantId(stream.origin());
        if (item != null) {
            stop(item);
        }
    }

    private void stop(Item item) {
        _detachStream(item.stream, item.participantView);
        item.stream = null;
        if (item.connection != null) {
            item.connection.stop();
        }
        item.connection = null;
        updateFullVideo();
    }

    private void notifyItemIfExists(int pos) {
        if (pos < 0 || pos >= data.size()) {
            Log.e(TAG, String.format("notifyItemIfExists: out of range %d not in [0,%d]", pos, data.size() - 1));
            return;
        }
        notifyItemChanged(pos);
    }

    private int getIndexByParticipantId(String participantId) {
        for (int i = 0; i < data.size(); i++) {
            Item item = data.get(i);
            if (TextUtils.equals(item.participantId, participantId)) {
                return i;
            }
        }
        return -1;
    }

    private Item getOrCreateItem(String participantId, Stream stream) {
        Item item = getItemByParticipantId(participantId);
        if (item == null) {
            item = createItem(participantId, stream);
        }
        return item;
    }

    private Item createItem(String participantId, Stream stream) {
        Item item;
        item = new Item(participantId, stream);
        runOnUiThread(() -> {
            data.add(item);
            notifyItemInserted(data.size() - 1);
        });
        return item;
    }

    @NonNull
    private Item getLocalItem() {
        for (Item item : data) {
            if (item.local) {
                return item;
            }
        }
        throw new IllegalStateException("local item not found");
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

    @Nullable
    private Item getItemByParticipantId(String participantId) {
        Item item;
        int index = getIndexByParticipantId(participantId);
        if (index == -1) {
            item = null;
        } else {
            item = data.get(index);
        }
        return item;
    }

    @UiThread
    public void add(String participantId, @Nullable UserInfo userInfo) {
        Log.d(TAG, "add() called with: participantId = [" + participantId + "], userInfo = [" + userInfo + "]");
        int index = getIndexByParticipantId(participantId);
        if (index == -1) {
            Item item = new Item(participantId);
            item.userInfo = userInfo;
            data.add(item);
            notifyItemInserted(data.size() - 1);
            return;
        }
        Item item = data.get(index);
        if (userInfo != null || item.userInfo == null) {
            item.userInfo = userInfo;
        }
        notifyItemIfExists(index);
        updateFullVideo();
    }

    @UiThread
    public void update(@NonNull UserInfo userInfo) {
        Log.d(TAG, "update() called with: userInfo = [" + userInfo + "]");
        int index = getIndexByParticipantId(userInfo.getParticipantId());
        if (index == -1) {
            Log.w(TAG, "update: not found " + userInfo);
            return;
        }
        Item item = data.get(index);
        item.userInfo = userInfo;
        notifyItemIfExists(index);
        updateFullVideo();
    }

    @UiThread
    public void remove(String participantId, @Nullable UserInfo userInfo) {
        Log.d(TAG, "remove() called with: participantId = [" + participantId + "], userInfo = [" + userInfo + "]");
        int index = getIndexByParticipantId(participantId);
        if (index == -1) {
            Log.e(TAG, "remove: not found participantId = " + participantId);
            return;
        }
        Item item = data.remove(index);
        _detachStream(item.stream, item.participantView);
        item.stream = null;
        item.connection = null;
        item.participantView = null;
        notifyItemRemoved(index);
        updateFullVideo();
    }

    private void updateFullVideo() {
        if (selectedItem != null) {
            Item item = selectedItem;
            int index = data.indexOf(item);
            if (index != -1) {
                updateFullVideo(item);
                return;
            }
        }
        for (int i = data.size() - 1; i >= 0; i--) {
            Item item = data.get(i);
            if (item.stream != null) {
                updateFullVideo(item);
                return;
            }
        }
        if (!data.isEmpty()) {
            updateFullVideo(data.get(0));
            return;
        }
        _detachStream(fullParticipantView.getStream(), fullParticipantView);
    }

    private void updateFullVideo(Item item) {
        fullParticipantView.setUserInfo(item.participantId, item.userInfo);
        _attackStream(item.stream, fullParticipantView);
    }

    private void setVisibility(View view, int visibility) {
        if (notUiThread()) {
            uiHandler.post(() -> view.setVisibility(visibility));
        } else {
            view.setVisibility(visibility);
        }
    }

    public void onStop() {
        for (Item item : data) {
            _detachStream(item.stream, item.participantView);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void onStart() {
        notifyDataSetChanged();
    }

    public void onSwitchCamera(boolean isFrontCamera) {
        this.isFrontCamera = isFrontCamera;
        fullParticipantView.onSwitchCamera(isFrontCamera);
        for (Item item : data) {
            if (item.participantView != null) {
                item.participantView.onSwitchCamera(isFrontCamera);
            }
        }
    }

    public static class Item {
        @Nullable
        private String participantId;
        @Nullable
        private UserInfo userInfo;
        @Nullable
        private Stream stream;
        @Nullable
        private Connection connection;
        @Nullable
        private ParticipantView participantView;
        private boolean local = false;

        public Item(@Nullable String participantId, @NonNull Stream stream) {
            this.participantId = participantId;
            this.stream = stream;
        }

        public Item(@Nullable String participantId) {
            this.participantId = participantId;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final Thumbnail thumbnail = itemView.findViewById(R.id.thumbnail);

        public ViewHolder(View itemView, EglBase.Context eglBaseContext) {
            super(itemView);
            thumbnail.initEgl(eglBaseContext);
        }

    }
}
