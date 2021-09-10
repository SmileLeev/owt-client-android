package owt.p2pandsfu.bean;

import android.text.TextUtils;

import java.util.Arrays;

@SuppressWarnings("unused")
public class UserInfo {
    private String participantId;
    private String username;
    private String avatarUrl;
    private boolean audioMuted;
    private boolean videoMuted;

    @Override
    public String toString() {
        return "UserInfo{" +
                "participantId='" + participantId + '\'' +
                ", username='" + username + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", audioMuted=" + audioMuted +
                ", videoMuted=" + videoMuted +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserInfo userInfo = (UserInfo) o;
        return TextUtils.equals(participantId, userInfo.participantId);
    }

    public boolean isAudioMuted() {
        return audioMuted;
    }

    public void setAudioMuted(boolean audioMuted) {
        this.audioMuted = audioMuted;
    }

    public boolean isVideoMuted() {
        return videoMuted;
    }

    public void setVideoMuted(boolean videoMuted) {
        this.videoMuted = videoMuted;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{participantId});
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
