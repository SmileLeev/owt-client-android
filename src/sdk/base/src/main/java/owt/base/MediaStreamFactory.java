/*
 * Copyright (C) 2018 Intel Corporation
 * SPDX-License-Identifier: Apache-2.0
 */
package owt.base;

import static owt.base.CheckCondition.DCHECK;
import static owt.base.CheckCondition.RCHECK;
import static owt.base.ContextInitialization.localContext;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.HashMap;
import java.util.UUID;

import owt.base.MediaConstraints.AudioTrackConstraints;

final class MediaStreamFactory {

    private static MediaStreamFactory instance;
    private final HashMap<String, VideoSource> unsharedVideoSources = new HashMap<>();
    private AudioSource sharedAudioSource;
    private int audioSourceRef = 0;

    private MediaStreamFactory() {
    }

    synchronized static MediaStreamFactory instance() {
        if (instance == null) {
            instance = new MediaStreamFactory();
        }
        return instance;
    }

    @SuppressWarnings("unused")
    MediaStream createMediaStream(VideoCapturer videoCapturer,
                                  AudioTrackConstraints audioMediaConstraints) {
        return createMediaStream(videoCapturer, audioMediaConstraints, true, true);
    }
    MediaStream createMediaStream(VideoCapturer videoCapturer,
            AudioTrackConstraints audioMediaConstraints,
            boolean audioEnabled, boolean videoEnabled) {
        RCHECK(videoCapturer != null || audioMediaConstraints != null);

        String label = UUID.randomUUID().toString();
        MediaStream mediaStream = PCFactoryProxy.instance().createLocalMediaStream(label);

        if (videoCapturer != null) {
            VideoSource videoSource = PCFactoryProxy.instance().createVideoSource(
                    videoCapturer.isScreencast());
            SurfaceTextureHelper helper = SurfaceTextureHelper.create("CT", localContext);
            videoCapturer.initialize(helper, ContextInitialization.context,
                    videoSource.getCapturerObserver());
            if (videoEnabled) {
                videoCapturer.startCapture(videoCapturer.getWidth(),
                        videoCapturer.getHeight(),
                        videoCapturer.getFps());
            }
            VideoTrack videoTrack = PCFactoryProxy.instance().createVideoTrack(label + "v0",
                    videoSource);
            videoTrack.setEnabled(videoEnabled);
            mediaStream.addTrack(videoTrack);
            unsharedVideoSources.put(label, videoSource);
        }

        if (audioMediaConstraints != null) {
            if (sharedAudioSource == null) {
                sharedAudioSource = PCFactoryProxy.instance().createAudioSource(
                        audioMediaConstraints.convertToWebRTCConstraints());
            }
            audioSourceRef++;
            AudioTrack audioTrack = PCFactoryProxy.instance().createAudioTrack(label + "a0", sharedAudioSource);
            audioTrack.setEnabled(audioEnabled);
            mediaStream.addTrack(audioTrack);
        }

        return mediaStream;
    }

    void onAudioSourceRelease() {
        DCHECK(audioSourceRef > 0);
        if (--audioSourceRef == 0) {
            sharedAudioSource.dispose();
            sharedAudioSource = null;
        }
    }

    void onVideoSourceRelease(String label) {
        DCHECK(unsharedVideoSources.containsKey(label));
        VideoSource videoSource = unsharedVideoSources.get(label);
        unsharedVideoSources.remove(label);
        videoSource.dispose();
    }

}
