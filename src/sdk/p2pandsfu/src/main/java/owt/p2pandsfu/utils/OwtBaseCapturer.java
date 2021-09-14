package owt.p2pandsfu.utils;

import owt.base.VideoCapturer;

public interface OwtBaseCapturer extends VideoCapturer {
    void startCapture();

    void stopCapture();
}
