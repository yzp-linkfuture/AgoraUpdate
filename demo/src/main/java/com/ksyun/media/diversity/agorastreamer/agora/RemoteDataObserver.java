package com.ksyun.media.diversity.agorastreamer.agora;

import android.util.Log;

import com.ksyun.media.streamer.framework.AVConst;
import com.ksyun.media.streamer.framework.AudioBufFormat;
import com.ksyun.media.streamer.framework.AudioBufFrame;
import com.ksyun.media.streamer.framework.ImgBufFormat;
import com.ksyun.media.streamer.framework.ImgBufFrame;
import com.ksyun.media.streamer.framework.SrcPin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Created by qyvideo on 12/13/16.
 */

public class RemoteDataObserver {
    private static final String TAG = "RemoteDataObserver";

    private long mObserverInstance = UNINIT;
    private final static int UNINIT = -1;

    private SrcPin<ImgBufFrame> mVideoSrcPin;
    private SrcPin<AudioBufFrame> mRemoteAudioSrcPin;
    private SrcPin<AudioBufFrame> mLocalAudioSrcPin;

    private volatile boolean mReceivingRemoteData = false;
    private ByteBuffer mVideoDirectBuffer;

    private ByteBuffer mRemoteAudioBuffer;
    private AudioBufFormat mRemoteAudioBufFormat;
    private ByteBuffer mLocalAudioBuffer;
    private AudioBufFormat mLocalAudioBufFormat;
    private ImgBufFormat mImgBufFormat;


    public RemoteDataObserver() {
        mVideoSrcPin = new SrcPin<>();
        mRemoteAudioSrcPin = new SrcPin<>();
        mLocalAudioSrcPin = new SrcPin<>();

        mObserverInstance = createObserver();
    }

    public SrcPin<ImgBufFrame> getVideoSrcPin() {
        return mVideoSrcPin;
    }

    public SrcPin<AudioBufFrame> getRemoteAudioSrcPin() {
        return mRemoteAudioSrcPin;
    }

    public SrcPin<AudioBufFrame> getLocalAudioSrcPin() {
        return mLocalAudioSrcPin;
    }

    public void release() {
        if (mObserverInstance == UNINIT) {
            Log.d(TAG, "have been released");
            return;
        }

        release(mObserverInstance);
        mObserverInstance = UNINIT;
    }

    public void enableObserver(boolean enable) {
        if (mObserverInstance == UNINIT) {
            Log.d(TAG, "have been released");
            return;
        }
        enableObserver(mObserverInstance, enable);
    }

    public void resetRemoteUid() {
        resetRemoteUid(mObserverInstance);
    }

    private native long createObserver();

    private native void release(long wrapperInstance);

    private native void enableObserver(long wrapperInstance, boolean enable);

    private native void resetRemoteUid(long wrapperInstance);

    public void onVideoFrame(ByteBuffer buffer, int size, int width, int height, int orientation, double pts) {
        if (mReceivingRemoteData) {
            if (mVideoDirectBuffer == null) {
                mVideoDirectBuffer = ByteBuffer.allocateDirect(size);
                mImgBufFormat = new ImgBufFormat(ImgBufFormat.FMT_RGBA, width, height, orientation);
                mVideoSrcPin.onFormatChanged(mImgBufFormat);
            } else if (mImgBufFormat.width != width || mImgBufFormat.height != height ||
                    mImgBufFormat.orientation != orientation) {
                mImgBufFormat.width = width;
                mImgBufFormat.height = height;
                mImgBufFormat.orientation = orientation;
                mVideoSrcPin.onFormatChanged(mImgBufFormat);

                mVideoDirectBuffer.clear();
                mVideoDirectBuffer = null;
                mVideoDirectBuffer = ByteBuffer.allocateDirect(size);
            }
            mVideoDirectBuffer.clear();
            mVideoDirectBuffer.put(buffer);
            mVideoDirectBuffer.rewind();
            ImgBufFrame bufFrame = new ImgBufFrame(mImgBufFormat, mVideoDirectBuffer, (long)pts);
            if(mVideoSrcPin.isConnected()) {
                mVideoSrcPin.onFrameAvailable(bufFrame);
            }
        }
    }

    public void onAudioFrame(ByteBuffer buffer, int length, int bytesPerSample, int sampleRate,
                             int channels, double pts, boolean remote) {
        if (mReceivingRemoteData) {
            if (remote) {
                onRemoteAudioFrame(buffer, length, bytesPerSample, sampleRate, channels, pts);
            } else {
                onLocalAudioFrame(buffer, length, bytesPerSample, sampleRate, channels, pts);
            }

        }
    }

    private void onRemoteAudioFrame(ByteBuffer buffer, int length, int bytesPerSample,
                                    int sampleRate, int channels, double pts) {
        if (mRemoteAudioBuffer == null) {
            mRemoteAudioBuffer = ByteBuffer.allocateDirect(length);
            mRemoteAudioBuffer.order(ByteOrder.nativeOrder());

            int sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            if (bytesPerSample == 2) {
                sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            }
            mRemoteAudioBufFormat = new AudioBufFormat(sampleFormat, sampleRate, channels);
            mRemoteAudioSrcPin.onFormatChanged(mRemoteAudioBufFormat);
        } else if (mRemoteAudioBufFormat.sampleRate != sampleRate ||
                mRemoteAudioBufFormat.channels != channels) {
            int sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            if (bytesPerSample == 2) {
                sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            }
            mRemoteAudioBufFormat = new AudioBufFormat(sampleFormat, sampleRate, channels);
            mRemoteAudioSrcPin.onFormatChanged(mRemoteAudioBufFormat);

            mRemoteAudioBuffer.clear();
            mRemoteAudioBuffer = null;
            mRemoteAudioBuffer = ByteBuffer.allocateDirect(length);
        }

        mRemoteAudioBuffer.clear();
        mRemoteAudioBuffer.put(buffer);
        mRemoteAudioBuffer.flip();

        AudioBufFrame bufFrame = new AudioBufFrame(mRemoteAudioBufFormat, mRemoteAudioBuffer, (long)pts);
        if(mRemoteAudioSrcPin.isConnected()) {
            mRemoteAudioSrcPin.onFrameAvailable(bufFrame);
        }
    }

    private void onLocalAudioFrame(ByteBuffer buffer, int length, int bytesPerSample,
                                   int sampleRate, int channels, double pts) {
        if (mLocalAudioBuffer == null) {
            mLocalAudioBuffer = ByteBuffer.allocateDirect(length);
            mLocalAudioBuffer.order(ByteOrder.nativeOrder());

            int sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            if (bytesPerSample == 2) {
                sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            }
            mLocalAudioBufFormat = new AudioBufFormat(sampleFormat, sampleRate, channels);
            mLocalAudioSrcPin.onFormatChanged(mLocalAudioBufFormat);
        } else if (mLocalAudioBufFormat.sampleRate != sampleRate ||
                mLocalAudioBufFormat.channels != channels) {
            int sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            if (bytesPerSample == 2) {
                sampleFormat = AVConst.AV_SAMPLE_FMT_S16;
            }
            mLocalAudioBufFormat = new AudioBufFormat(sampleFormat, sampleRate, channels);
            mLocalAudioSrcPin.onFormatChanged(mLocalAudioBufFormat);

            mLocalAudioBuffer.clear();
            mLocalAudioBuffer = null;
            mLocalAudioBuffer = ByteBuffer.allocateDirect(length);
        }

        mLocalAudioBuffer.clear();
        mLocalAudioBuffer.put(buffer);
        mLocalAudioBuffer.flip();

        AudioBufFrame bufFrame = new AudioBufFrame(mLocalAudioBufFormat, mLocalAudioBuffer, (long)pts);
        if(mLocalAudioSrcPin.isConnected()) {
            mLocalAudioSrcPin.onFrameAvailable(bufFrame);
        }
    }

    static {
        try {
            System.loadLibrary("apm-remote-data-observer");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "No libapm-remote-data-observer.so! Please check");
        }
    }

    public void startReceiveRemoteData() {
        mReceivingRemoteData = true;
    }

    public void stopReceiveRemoteData() {
        mReceivingRemoteData = false;
    }
}
