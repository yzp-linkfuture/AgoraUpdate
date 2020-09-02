package com.ksyun.media.diversity.agorastreamer.agora.kit;

import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.RectF;
import android.media.AudioManager;
import android.util.Log;

import com.ksyun.media.diversity.agorastreamer.agora.MediaManager;
import com.ksyun.media.streamer.encoder.ImgTexToBuf;
import com.ksyun.media.streamer.filter.imgtex.ImgTexMixer;
import com.ksyun.media.streamer.filter.imgtex.ImgTexScaleFilter;
import com.ksyun.media.streamer.kit.KSYStreamer;

import java.util.Arrays;

import io.agora.rtc.IRtcEngineEventHandler;

/**
 * Created by qyvideo on 12/10/16.
 */
public class KSYAgoraStreamer extends KSYStreamer {
    private static final String TAG = "KSYAgoraStreamer";
    private static final boolean DEBUG = false;
    private static final String VERSION = "1.0.5.0";

    private static final int mIdxVideoSub = 3;
    private static final int mIdxAudioRemote = 2;

    private AgoraRTCClient mRTCClient;
    private MediaManager mMediaManager;
    private ImgTexToBuf mRTCImgTexToBuf;
    private ImgTexScaleFilter mRTCImgTexScaleFilter;
    private ImgTexScaleFilter mRTCRemoteImgTexScaleFilter;

    public static final int RTC_MAIN_SCREEN_CAMERA = 1;
    public static final int RTC_MAIN_SCREEN_REMOTE = 2;
    private int mRTCMainScreen = RTC_MAIN_SCREEN_CAMERA;

    public static final int SCALING_MODE_FULL_FILL = ImgTexMixer.SCALING_MODE_FULL_FILL;
    public static final int SCALING_MODE_BEST_FIT = ImgTexMixer.SCALING_MODE_BEST_FIT;
    public static final int SCALING_MODE_CENTER_CROP = ImgTexMixer.SCALING_MODE_CENTER_CROP;

    private MusicIntentReceiver mHeadSetReceiver;
    private boolean mHeadSetPluged = false;
    private boolean mIsCalling = false;
    private boolean mHasSubConnecting = false;

    public KSYAgoraStreamer(Context context) {
        super(context);
    }


    /**
     * set rtc main Screen
     *
     * @param mainScreenType
     */
    public void setRTCMainScreen(int mainScreenType) {
        if (mainScreenType < RTC_MAIN_SCREEN_CAMERA
                || mainScreenType > RTC_MAIN_SCREEN_REMOTE) {
            throw new IllegalArgumentException("Invalid rtc main screen type");
        }
        mRTCMainScreen = mainScreenType;
    }

    /**
     * the sub screen position
     * must be set before registerRTC
     *
     * @param width  0~1 default value 0.35f
     * @param height 0~1 default value 0.3f
     * @param left   0~1 default value 0.65f
     * @param top    0~1 default value 0.f
     * @param mode   scaling mode
     */
    public void setRTCSubScreenRect(float left, float top, float width, float height, int mode) {
        mPresetSubLeft = left;
        mPresetSubTop = top;
        mPresetSubWidth = width;
        mPresetSubHeight = height;
        mPresetSubMode = mode;

        mImgTexMixer.setRenderRect(mIdxVideoSub, left, top, width, height, 1.0f);
        mImgTexMixer.setRenderRect(mIdxCamera, 0.f, 0.f, 1.f, 1.f, 1.0f);
        mImgTexMixer.setScalingMode(mIdxVideoSub, mode);

        mImgTexPreviewMixer.setRenderRect(mIdxVideoSub, left, top, width, height, 1.0f);
        mImgTexPreviewMixer.setRenderRect(mIdxCamera, 0.f, 0.f, 1.f, 1.f, 1.0f);
        mImgTexPreviewMixer.setScalingMode(mIdxVideoSub, mode);
    }

    @Override
    protected void initModules() {
        mHeadSetPluged = false;
        mIsCalling = false;
        super.initModules();
        mAudioCapture.setSampleRate(16000);
        //rtc remote image
        mMediaManager = new MediaManager(mContext);
        mRTCClient = new AgoraRTCClient(mGLRender, mMediaManager);

        mRTCImgTexToBuf = new ImgTexToBuf(mGLRender);
        mRTCImgTexScaleFilter = new ImgTexScaleFilter(mGLRender);
        mImgTexFilterMgt.getSrcPin().connect(mRTCImgTexScaleFilter.getSinkPin());
        mRTCImgTexScaleFilter.getSrcPin().connect(mRTCImgTexToBuf.mSinkPin);
        //send local video to rtc module sink pin
        mRTCImgTexToBuf.mSrcPin.connect(mRTCClient.getRTCIO().getImgSinkPin());

        mRTCRemoteImgTexScaleFilter = new ImgTexScaleFilter(mGLRender);
        mRTCRemoteImgTexScaleFilter.setReuseFbo(false);

        setRTCSubScreenRect(0.65f, 0.f, 0.35f, 0.3f, SCALING_MODE_CENTER_CROP);

        mMediaManager.registerUiHandler(new MediaManager.MediaUiHandler() {
            @Override
            public void onMediaEvent(int event, Object... data) {
                switch (event) {
                    case MediaManager.MediaUiHandler.USER_JOINED: {
                        mRTCClient.getRTCIO().startReceiveRemoteData();
                        break;
                    }

                    case MediaManager.MediaUiHandler.JOIN_CHANNEL_RESULT: {
                        boolean success = (Boolean) data[0];
                        if (success) {

                        } else {

                        }
                        mRTCClient.getRTCIO().startReceiveRemoteData();
                        break;
                    }

                    case MediaManager.MediaUiHandler.FIRST_FRAME_DECODED: {
                        //收到辅播数据后，设置辅播画面为大窗口
                        setAudioMode(mHeadSetPluged == true ?
                                AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
                        mHasSubConnecting = true;
                        updateRTCConnect(mRTCMainScreen);
                        Log.d(TAG, "onFirstRemoteVideoDecoded " + Arrays.toString(data));
                        break;
                    }

                    case MediaManager.MediaUiHandler.LEAVE_CHANNEL: {
                        // temporarily only one remote stream supported, so reset uid here
                        mHasSubConnecting = false;
                        mRTCClient.getRTCIO().stopReceiveRemoteData();
                        updateRTCConnect(RTC_MAIN_SCREEN_CAMERA);
                        if (!mIsCalling) {
                            if ((mIsRecording || mIsFileRecording) &&
                                    !mAudioCapture.isRecordingState()) {
                                mAudioCapture.start();
                            }
                        }
                        break;
                    }

                    case MediaManager.MediaUiHandler.USER_OFFLINE: {
                        //辅播断开后，设置主播画面为大窗口
                        mHasSubConnecting = false;
                        updateRTCConnect(RTC_MAIN_SCREEN_CAMERA);
                        break;
                    }

                    case MediaManager.MediaUiHandler.ERROR: {
                        int errorCode = (Integer) data[0];
                        if (errorCode == IRtcEngineEventHandler.ErrorCode.ERR_INVALID_APP_ID) {

                        } else {

                        }
                    }
                }
            }
        });
        registerHeadsetPlugReceiver();
    }

    /**
     * Get {@link AgoraRTCClient} module instance.
     *
     * @return AgoraRTCClient instance.
     */
    public AgoraRTCClient getRTCClient() {
        return mRTCClient;
    }

    @Override
    public void setRotateDegrees(int degrees) {
        boolean isLastLandscape = (mRotateDegrees % 180) != 0;
        boolean isLandscape = (degrees % 180) != 0;
        if (isLastLandscape != isLandscape) {
            if (mHasSubConnecting) {
                setRTCSubScreenRect(mPresetSubLeft, mPresetSubTop, mPresetSubHeight,
                        mPresetSubWidth, mPresetSubMode);
            }
        }
        super.setRotateDegrees(degrees);
        //setRTCMainScreen(mRTCMainScreen);
        if (mHasSubConnecting) {
            updateRTCConnect(mRTCMainScreen);
        }
    }

    @Override
    protected void setPreviewParams() {
        super.setPreviewParams();
        //转换成agora支持的编码分辨率
        switch (mTargetWidth) {
            case 360:
                mRTCImgTexScaleFilter.setTargetSize(360, 640);
                mMediaManager.setVideoProfile(IRtcEngineEventHandler.VideoProfile
                        .VIDEO_PROFILE_360P, true);
                break;
            case 480:
                mRTCImgTexScaleFilter.setTargetSize(480, 848);
                mMediaManager.setVideoProfile(IRtcEngineEventHandler.VideoProfile
                        .VIDEO_PROFILE_480P_8, true);
                break;
            case 720:
                mRTCImgTexScaleFilter.setTargetSize(720, 1280);
                mMediaManager.setVideoProfile(IRtcEngineEventHandler.VideoProfile
                        .VIDEO_PROFILE_720P, true);
                break;
            default:
                //just demo for rtc img,see following for details(setVideoProfile)
                // https://docs.agora.io/cn/user_guide/API/android_api_live.html
                boolean isLandscape = (mRotateDegrees % 180) != 0;
                if (isLandscape) {
                    mRTCImgTexScaleFilter.setTargetSize(640, 360);
                    mMediaManager.setVideoProfile(IRtcEngineEventHandler.VideoProfile
                            .VIDEO_PROFILE_360P, false);
                } else {
                    mRTCImgTexScaleFilter.setTargetSize(360, 640);
                    mMediaManager.setVideoProfile(IRtcEngineEventHandler.VideoProfile
                            .VIDEO_PROFILE_360P, true);
                }

                break;

        }
    }

    public void startRTC(String channel) {
        if (mIsCalling) {
            return;
        }
        mIsCalling = true;

        setAudioParams();
        mAudioCapture.getSrcPin().disconnect(false);
        if (mAudioCapture.isRecordingState()) {
            mAudioCapture.stop();
        }

        //join rtc
        mRTCClient.joinChannel(channel);

        //connect rtc audio
        mRTCClient.getRTCIO().getLocalAudioSrcPin().connect(mAudioFilterMgt.getSinkPin());
        mRTCClient.getRTCIO().getRemoteAudioSrcPin().connect(mAudioMixer.getSinkPin(mIdxAudioRemote));
    }

    public void stopRTC() {
        if (!mIsCalling) {
            return;
        }
        mIsCalling = false;

        //leave rtc
        mRTCClient.getRTCIO().stopReceiveRemoteData();
        setRTCMainScreen(RTC_MAIN_SCREEN_CAMERA);
        mRTCClient.leaveChannel();

        //disconnect rtc audio
        mRTCClient.getRTCIO().getLocalAudioSrcPin().disconnect(false);
        mRTCClient.getRTCIO().getRemoteAudioSrcPin().disconnect(false);
        //connect audio capture
        mAudioCapture.getSrcPin().connect(mAudioFilterMgt.getSinkPin());
    }

    /**
     * Set if start audio preview.<br/>
     * Should start only when headset plugged.
     *
     * @param enable true to start, false to stop.
     */
    @Override
    public void setEnableAudioPreview(boolean enable) {
        if (mHasSubConnecting) {
            setAudioMode(mHeadSetPluged == true ?
                    AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
        }

        super.setEnableAudioPreview(enable);
    }

    /**
     * Release all resources used by KSYAgoraStreamer.
     */
    @Override
    public void release() {
        super.release();
        mRTCClient.release();
        unregisterHeadsetPlugReceiver();
    }


    private void registerHeadsetPlugReceiver() {
        mHeadSetReceiver = new MusicIntentReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        mContext.registerReceiver(mHeadSetReceiver, filter);
    }

    private void unregisterHeadsetPlugReceiver() {
        if (mHeadSetReceiver != null) {
            mContext.unregisterReceiver(mHeadSetReceiver);
        }
    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean headeSetConnected = false;

            String action = intent.getAction();
            int state = BluetoothHeadset.STATE_DISCONNECTED;

            if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_DISCONNECTED);
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    headeSetConnected = true;
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    headeSetConnected = false;
                }
            } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED))// audio
            {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    headeSetConnected = true;
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    headeSetConnected = false;
                }
            } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                state = intent.getIntExtra("state", -1);

                switch (state) {
                    case 0:
                        Log.d(TAG, "Headset is unplugged");
                        headeSetConnected = false;
                        break;
                    case 1:
                        Log.d(TAG, "Headset is plugged");
                        headeSetConnected = true;
                        break;
                    default:
                        Log.d(TAG, "I have no idea what the headset state is");
                }
            }

            mHeadSetPluged = headeSetConnected;
            if (mHasSubConnecting) {
                setAudioMode(mHeadSetPluged == true ?
                        AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
            }
        }
    }

    public RectF getRTCSubScreenRect() {
        return mImgTexPreviewMixer.getRenderRect(mIdxVideoSub);
    }

    public static String getAgoraRTCVersion() {
        return VERSION;
    }

    private void updateRTCConnect(int rtcMainScreen) {

        mImgTexFilterMgt.getSrcPin().disconnect(mImgTexMixer.getSinkPin(mIdxCamera), false);
        mImgTexFilterMgt.getSrcPin().disconnect(mImgTexPreviewMixer.getSinkPin(mIdxCamera), false);
        mImgTexFilterMgt.getSrcPin().disconnect(mImgTexMixer.getSinkPin(mIdxVideoSub), false);
        mImgTexFilterMgt.getSrcPin().disconnect(mImgTexPreviewMixer.getSinkPin(mIdxVideoSub), false);

        mRTCRemoteImgTexScaleFilter.getSrcPin().disconnect(false);
        mRTCClient.getRTCIO().getImgSrcPin().disconnect(false);


        boolean needScale = false;
        if (mRTCClient.getRTCIO().getRemoteImgFormat() != null) {
            boolean isLandscape = (mRotateDegrees % 180) != 0;

            if ((isLandscape && mRTCClient.getRTCIO()
                    .getRemoteImgFormat().width < mRTCClient.getRTCIO().getRemoteImgFormat().height) ||
                    (!isLandscape && mRTCClient.getRTCIO().getRemoteImgFormat().width > mRTCClient.getRTCIO()
                            .getRemoteImgFormat().height)) {
                needScale = false;
            }
        }

        if (rtcMainScreen == RTC_MAIN_SCREEN_REMOTE) {
            mImgTexFilterMgt.getSrcPin().connect(mImgTexMixer.getSinkPin(mIdxVideoSub));
            mImgTexFilterMgt.getSrcPin().connect(mImgTexPreviewMixer.getSinkPin(mIdxVideoSub));

            mImgTexFilterMgt.getSrcPin().connect(mRTCImgTexScaleFilter.getSinkPin());

            if (needScale) {
                mRTCRemoteImgTexScaleFilter.setTargetSize(mRTCClient.getRTCIO()
                                .getRemoteImgFormat().height,
                        mRTCClient.getRTCIO().getRemoteImgFormat().width);
                mRTCClient.getRTCIO().getImgSrcPin().connect(mRTCRemoteImgTexScaleFilter.getSinkPin());
                mRTCRemoteImgTexScaleFilter.getSrcPin().connect(mImgTexPreviewMixer.getSinkPin(mIdxCamera));
                mRTCRemoteImgTexScaleFilter.getSrcPin().connect(mImgTexMixer.getSinkPin(mIdxCamera));
            } else {
                mRTCClient.getRTCIO().getImgSrcPin().connect(mImgTexPreviewMixer.getSinkPin(mIdxCamera));
                mRTCClient.getRTCIO().getImgSrcPin().connect(mImgTexMixer.getSinkPin(mIdxCamera));
            }
            mImgTexPreviewMixer.setMainSinkPinIndex(mIdxVideoSub);
            mImgTexMixer.setMainSinkPinIndex(mIdxVideoSub);
        } else {
            mImgTexFilterMgt.getSrcPin().connect(mImgTexPreviewMixer.getSinkPin(mIdxCamera));
            mImgTexFilterMgt.getSrcPin().connect(mImgTexMixer.getSinkPin(mIdxCamera));
            mImgTexFilterMgt.getSrcPin().connect(mRTCImgTexScaleFilter.getSinkPin());
            if (mHasSubConnecting) {
                if (needScale) {
                    mRTCRemoteImgTexScaleFilter.setTargetSize(mRTCClient.getRTCIO()
                                    .getRemoteImgFormat().height,
                            mRTCClient.getRTCIO().getRemoteImgFormat().width);
                    mRTCClient.getRTCIO().getImgSrcPin().connect(mRTCRemoteImgTexScaleFilter.getSinkPin());
                    mRTCRemoteImgTexScaleFilter.getSrcPin().connect(mImgTexPreviewMixer
                            .getSinkPin(mIdxVideoSub));
                    mRTCRemoteImgTexScaleFilter.getSrcPin().connect(mImgTexMixer.getSinkPin(mIdxVideoSub));
                } else {
                    mRTCClient.getRTCIO().getImgSrcPin().connect(mImgTexPreviewMixer.getSinkPin(mIdxVideoSub));
                    mRTCClient.getRTCIO().getImgSrcPin().connect(mImgTexMixer.getSinkPin(mIdxVideoSub));
                }
            }
            mImgTexPreviewMixer.setMainSinkPinIndex(mIdxCamera);
            mImgTexMixer.setMainSinkPinIndex(mIdxCamera);
        }

        updateRemoteSize(rtcMainScreen);
    }

    public void setAudioMode(int mode) {
        if (mode != AudioManager.MODE_NORMAL && mode != AudioManager.MODE_IN_COMMUNICATION) {
            return;
        }
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (mode == AudioManager.MODE_NORMAL) {
            audioManager.setSpeakerphoneOn(true);//打开扬声器
        } else if (mode == AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.setSpeakerphoneOn(false);//关闭扬声器
        }
        audioManager.setMode(mode);
    }

    private float mPresetSubLeft;
    private float mPresetSubTop;
    private float mPresetSubWidth;
    private float mPresetSubHeight;
    private int mPresetSubMode;

    private void updateRemoteSize(int rtcMainScreen) {
        if (!mHasSubConnecting) {
            //当前并未处与连麦状态，恢复大屏为全屏显示
            mImgTexPreviewMixer.setRenderRect(mIdxCamera, 0.f, 0.f, 1.f, 1.f, 1.0f);
            mImgTexMixer.setRenderRect(mIdxCamera, 0.f, 0.f, 1.f, 1.f, 1.0f);
            return;
        }

        boolean isLandscape = (mRotateDegrees % 180) != 0;
        boolean tIsLandscape = mRTCClient.getRTCIO()
                .getRemoteImgFormat().width > mRTCClient.getRTCIO().getRemoteImgFormat()
                .height ? true : false;

        boolean needScale = false;

        int tWidth = mRTCClient.getRTCIO()
                .getRemoteImgFormat().width;
        int tHeight = mRTCClient.getRTCIO().getRemoteImgFormat().height;

        //本地预览显示和remote的横竖屏不一致时，需要resize
        if ((isLandscape && tWidth < tHeight) ||
                (!isLandscape && tWidth > tHeight)) {
            needScale = true;
        }

        if (needScale) {
            if (rtcMainScreen == RTC_MAIN_SCREEN_REMOTE) {
                float left, top, w_new, h_new;
                left = mPresetSubLeft;
                top = mPresetSubTop;
                w_new = mPresetSubWidth;
                h_new = mPresetSubHeight;

                if (!isLandscape && tIsLandscape) {
                    left = 0;
                    w_new = (float) 1.0;
//                    h_new = (float) (tHeight * mScreenRenderWidth) / (mScreenRenderHeight * tWidth);
                    h_new = (float) (tHeight * mPreviewWidth) / (mPreviewHeight * tWidth);
                    top = (float) (1.0 - h_new) / (float) 2.0;
                } else if (isLandscape && !tIsLandscape) {
                    top = 0;
                    h_new = (float) 1.0;
//                  w_new = (float) (tWidth * mScreenRenderHeight) / (mScreenRenderWidth * tHeight);
                    w_new = (float) (tWidth * mPreviewHeight) / (mPreviewWidth * tHeight);
                    left = (float) (1.0 - w_new) / (float) 2.0;
                }

                mImgTexMixer.setRenderRect(mIdxCamera, left, top, w_new,
                        h_new, 1.0f);
                mImgTexPreviewMixer.setRenderRect(mIdxCamera, left, top, w_new,
                        h_new, 1.0f);

            } else if (rtcMainScreen == RTC_MAIN_SCREEN_CAMERA) {
                RectF rect = getRTCSubScreenRect();
                float w = rect.width();
                float h = rect.height();

                float w_new;
                float h_new;
//              w_new = (float) mScreenRenderHeight * h / (float) mScreenRenderWidth;
//              h_new = (float) mScreenRenderWidth * w / (float) mScreenRenderHeight;
                w_new = (float) mPreviewHeight * h / (float) mPreviewWidth;
                h_new = (float) mPreviewWidth * w / (float) mPreviewHeight;

                float left = (float) (1.0 - w_new - 0.10);
                float top = (float) 0.05;
                mImgTexMixer.setRenderRect(mIdxVideoSub, left, top, w_new,
                        h_new, 1.0f);
                mImgTexPreviewMixer.setRenderRect(mIdxVideoSub, left, top, w_new,
                        h_new, 1.0f);

            }
        } else {
            mImgTexMixer.setRenderRect(mIdxCamera, 0.f, 0.f, 1.f, 1.f, 1.0f);
            mImgTexMixer.setRenderRect(mIdxVideoSub, mPresetSubLeft, mPresetSubTop,
                    mPresetSubWidth, mPresetSubHeight, 1.0f);
            mImgTexPreviewMixer.setRenderRect(mIdxCamera, 0.f, 0.f, 1.f, 1.f, 1.0f);
            mImgTexPreviewMixer.setRenderRect(mIdxVideoSub, mPresetSubLeft, mPresetSubTop,
                    mPresetSubWidth, mPresetSubHeight, 1.0f);
        }
    }
}
