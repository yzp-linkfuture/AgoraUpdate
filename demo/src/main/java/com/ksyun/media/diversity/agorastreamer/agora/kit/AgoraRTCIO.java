package com.ksyun.media.diversity.agorastreamer.agora.kit;


import android.util.Log;

import com.ksyun.media.diversity.agorastreamer.agora.MediaManager;
import com.ksyun.media.diversity.agorastreamer.agora.RemoteDataObserver;
import com.ksyun.media.streamer.capture.ImgTexSrcPin;
import com.ksyun.media.streamer.framework.AudioBufFormat;
import com.ksyun.media.streamer.framework.AudioBufFrame;
import com.ksyun.media.streamer.framework.ImgBufFormat;
import com.ksyun.media.streamer.framework.ImgBufFrame;
import com.ksyun.media.streamer.framework.ImgTexFormat;
import com.ksyun.media.streamer.framework.ImgTexFrame;
import com.ksyun.media.streamer.framework.SinkPin;
import com.ksyun.media.streamer.framework.SrcPin;
import com.ksyun.media.streamer.util.gles.GLRender;

import io.agora.extvideo.AgoraVideoSource;
import io.agora.rtc.video.AgoraVideoFrame;

import static com.ksy.statlibrary.util.Constants.LOG_TAG;


/**
 * RTC IO module
 * 1. receive video data from remote peer
 * 2. receive mixed audio data from agora, agora process mix and aecm
 * 3. send video data to remote peer
 */
public class AgoraRTCIO {
    private static String TAG = "RTCIO";

    private MediaManager mRTCWrapper;
    private GLRender mGLRender;

    private AgoraVideoSource mVideoSource;

    //remote video data src pin
    private ImgTexSrcPin mImgTexSrcPin;
    //local and remote audio data src pin
    private SrcPin<AudioBufFrame> mRemoteAudioSrcPin;
    private SrcPin<AudioBufFrame> mLocalAudioSrcPin;
    //local video data sin pin;
    private SinkPin<ImgBufFrame> mLocalImgSinkPin;
    private RemoteDataObserver mRemoteDataObserver;

    //sink pin to receive remote rtc data
    private SinkPin<ImgBufFrame> mRemoteImgSinkPin;
    private ImgTexFormat mImgTexFormat;
    //sink pin to receive remote and local data from agora sdk (remote data observer)
    private SinkPin<AudioBufFrame> mRemoteAudioSinkPin;
    private SinkPin<AudioBufFrame> mLocalAudioSinkPin;
    private AudioBufFormat mRemoteAudioBufFormat;
    private AudioBufFormat mLocalAudioBufFormat;

    public AgoraRTCIO(GLRender glRender, MediaManager rtcWrapper) {
        mRTCWrapper = rtcWrapper;
        mGLRender = glRender;
        mImgTexSrcPin = new ImgTexSrcPin(mGLRender);
        mRemoteAudioSrcPin = new SrcPin<AudioBufFrame>();
        mLocalAudioSrcPin = new SrcPin<AudioBufFrame>();

        mLocalImgSinkPin = new RTCLocalImgSinkPin();

        mRemoteDataObserver = new RemoteDataObserver();
        mRemoteImgSinkPin = new RTCRemoteImgSinkPin();
        mRemoteAudioSinkPin = new RTCRemoteAudioSinkPin();
        mLocalAudioSinkPin = new RTCLocalAudioSinkPin();
        //connect sink to to remote data observer src pin
        mRemoteDataObserver.getRemoteAudioSrcPin().connect(mRemoteAudioSinkPin);
        mRemoteDataObserver.getLocalAudioSrcPin().connect(mLocalAudioSinkPin);
        mRemoteDataObserver.getVideoSrcPin().connect(mRemoteImgSinkPin);
    }

    public void release() {
        mRemoteDataObserver.release();
    }

    public void enableObserver(boolean enable) {
        mRemoteDataObserver.enableObserver(enable);
    }

    public void resetRemoteUid() {
        mRemoteDataObserver.resetRemoteUid();
    }

    public SrcPin<ImgTexFrame> getImgSrcPin() {
        return mImgTexSrcPin;
    }

    public SrcPin<AudioBufFrame> getRemoteAudioSrcPin() {
        return mRemoteAudioSrcPin;
    }

    public SrcPin<AudioBufFrame> getLocalAudioSrcPin() {
        return mLocalAudioSrcPin;
    }

    public SinkPin<ImgBufFrame> getImgSinkPin() {
        return mLocalImgSinkPin;
    }

    public void startReceiveRemoteData() {
        mRemoteDataObserver.resetRemoteUid();
        mRemoteDataObserver.startReceiveRemoteData();
    }

    public void stopReceiveRemoteData() {
        mRemoteDataObserver.resetRemoteUid();
        mRemoteDataObserver.stopReceiveRemoteData();
    }

    public ImgTexFormat getRemoteImgFormat() {
        return mImgTexFormat;
    }

    public class RTCAudioSinkPin extends SinkPin<AudioBufFrame> {

        @Override
        public void onFormatChanged(Object format) {
            if(format instanceof AudioBufFormat) {
//                mRTCWrapper.onSendAudioFormatChanged((AudioBufFormat) format);
            }
        }

        @Override
        public void onFrameAvailable(AudioBufFrame frame) {
//            mRTCWrapper.sendAudio(frame);
        }

        @Override
        public void onDisconnect(boolean recursive) {

        }
    }

    public class RTCLocalImgSinkPin extends SinkPin<ImgBufFrame> {

        @Override
        public void onFormatChanged(Object format) {

        }

        @Override
        public void onFrameAvailable(ImgBufFrame frame) {
           //send video frame to peer
            AgoraVideoFrame vf = new AgoraVideoFrame();
            vf.format = 1; //1 is I420
            vf.timeStamp = frame.pts;
            vf.stride = frame.format.width;
            vf.height = frame.format.height;
            vf.rotation = 0;
            byte[] buf = new byte[frame.buf.remaining()];
            frame.buf.get(buf);
            vf.buf = buf;
            boolean result = mRTCWrapper.getRtcEngine().pushExternalVideoFrame(vf);
            Log.d(LOG_TAG, "send frame to Agora SDK status " + result);
        }

        @Override
        public void onDisconnect(boolean recursive) {

        }
    }


    private class RTCRemoteImgSinkPin extends SinkPin<ImgBufFrame> {

        @Override
        public void onFormatChanged(Object format) {
            ImgBufFormat f = (ImgBufFormat) format;
            mImgTexFormat = new ImgTexFormat(ImgTexFormat.COLOR_RGBA, f.width, f.height);
            if (mImgTexSrcPin.isConnected()) {
                mImgTexSrcPin.onFormatChanged(mImgTexFormat);
            }
        }

        @Override
        public void onFrameAvailable(ImgBufFrame frame) {
            if (mImgTexSrcPin.isConnected()) {
                int orientation = 360 - frame.format.orientation;
                mImgTexSrcPin.updateFrame(frame.buf, frame.format.width * 4, frame.format.width, frame.format
                        .height, orientation, frame.pts);
            }
        }

        @Override
        public void onDisconnect(boolean recursive) {
            if (mImgTexSrcPin.isConnected()) {
                ImgTexFrame frame = new ImgTexFrame(mImgTexFormat,
                        ImgTexFrame.NO_TEXTURE, null, 0);
                mImgTexSrcPin.onFrameAvailable(frame);
            }
        }
    }

    private class RTCRemoteAudioSinkPin extends SinkPin<AudioBufFrame> {

        @Override
        public void onFormatChanged(Object format) {
            mRemoteAudioBufFormat = (AudioBufFormat)format;
            if (mRemoteAudioSrcPin.isConnected()) {
                mRemoteAudioSrcPin.onFormatChanged(format);
            }
        }

        @Override
        public void onFrameAvailable(AudioBufFrame frame) {
            if (mRemoteAudioSrcPin.isConnected()) {
                mRemoteAudioSrcPin.onFrameAvailable(frame);
            }
        }

        @Override
        public void onDisconnect(boolean recursive) {
            if (mRemoteAudioSrcPin.isConnected()) {
                AudioBufFrame frame = new AudioBufFrame(mRemoteAudioBufFormat, null, 0);
                mRemoteAudioSrcPin.onFrameAvailable(frame);
            }
        }
    }

    private class RTCLocalAudioSinkPin extends SinkPin<AudioBufFrame> {

        @Override
        public void onFormatChanged(Object format) {
            mLocalAudioBufFormat = (AudioBufFormat)format;
            if (mLocalAudioSrcPin.isConnected()) {
                mLocalAudioSrcPin.onFormatChanged(format);
            }
        }

        @Override
        public void onFrameAvailable(AudioBufFrame frame) {
            if (mLocalAudioSrcPin.isConnected()) {
                mLocalAudioSrcPin.onFrameAvailable(frame);
            }
        }

        @Override
        public void onDisconnect(boolean recursive) {
            if (mLocalAudioSrcPin.isConnected()) {
                AudioBufFrame frame = new AudioBufFrame(mLocalAudioBufFormat, null, 0);
                mLocalAudioSrcPin.onFrameAvailable(frame);
            }
        }
    }
}
