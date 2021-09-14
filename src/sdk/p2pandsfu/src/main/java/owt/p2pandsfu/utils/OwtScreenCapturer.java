/*
 * Copyright (C) 2018 Intel Corporation
 * SPDX-License-Identifier: Apache-2.0
 */
package owt.p2pandsfu.utils;

import android.annotation.TargetApi;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;

import org.webrtc.ScreenCapturerAndroid;

import owt.base.Stream;
import owt.base.VideoCapturer;

public class OwtScreenCapturer extends ScreenCapturerAndroid implements OwtBaseCapturer{
    private int width, height;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public OwtScreenCapturer(Intent data, int width, int height) {
        super(data, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
            }
        });
        this.width = width;
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getFps() {
        // ignored
        return 0;
    }

    @Override
    public void startCapture() {
        super.startCapture(getWidth(), getHeight(), getFps());
    }

    @Override
    public Stream.StreamSourceInfo.VideoSourceInfo getVideoSource() {
        return Stream.StreamSourceInfo.VideoSourceInfo.SCREEN_CAST;
    }
}
