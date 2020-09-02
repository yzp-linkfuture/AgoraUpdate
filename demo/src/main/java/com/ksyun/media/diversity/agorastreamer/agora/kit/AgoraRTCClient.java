package com.ksyun.media.diversity.agorastreamer.agora.kit;

import com.ksyun.media.diversity.agorastreamer.agora.MediaManager;
import com.ksyun.media.streamer.util.gles.GLRender;


/**
 * Created by qyvideo on 12/13/16.
 */

public class AgoraRTCClient {

    private MediaManager mMediaManager;
    private AgoraRTCIO mIO;

    public AgoraRTCClient(GLRender glRender, MediaManager mediaManager) {
        mMediaManager = mediaManager;
        mIO = new AgoraRTCIO(glRender, mediaManager);
    }

    public void release() {

    }

    public AgoraRTCIO getRTCIO() {
        return mIO;
    }

    public void joinChannel(String channel) {
        enableObserver(true);
        mMediaManager.joinChannel(channel);
    }

    public void leaveChannel() {
        enableObserver(false);
        mMediaManager.leaveChannel();
    }

    public void enableObserver(boolean enable) {
        mIO.enableObserver(enable);
    }
}
