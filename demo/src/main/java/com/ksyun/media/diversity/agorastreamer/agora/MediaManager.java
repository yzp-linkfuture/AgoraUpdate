package com.ksyun.media.diversity.agorastreamer.agora;

import android.content.Context;
import android.util.Log;

import com.ksyun.media.diversity.agorastreamer.demo.R;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;

/**
 * Created by szc on 22/12/2015.
 */
public class MediaManager extends IRtcEngineEventHandler {
    private static final String LOG_TAG = MediaManager.class.getSimpleName();

    private Context context;

    private List<MediaUiHandler> uiHandlers = new ArrayList<>(3);

    private RtcEngine rtcEngine;

    String channelId;

    private int mVideoProfile = VideoProfile.VIDEO_PROFILE_360P_3;  //360*640
    private boolean mSwapWidth = false;

    public interface MediaUiHandler {
        int JOIN_CHANNEL_RESULT = 1;

        int FIRST_FRAME_DECODED = 2;

        int ON_VENDOR_MSG = 3;

        int USER_JOINED = 4;

        int ERROR = 5;

        int LEAVE_CHANNEL = 6;

        int USER_OFFLINE = 7;

        void onMediaEvent(int event, Object... data);
    }

    public MediaManager(Context context) {
        this.context = context;
        init();
    }

    public void init() {
        String appId = context.getString(R.string.app_id);

        if (appId == null || appId.equals("")) {
            throw new IllegalArgumentException("Please set your app_id to strings.app_id");
        }

        Log.d(LOG_TAG, "init " + appId);
        try{
            rtcEngine = RtcEngine.create(context, appId, this);
        }catch(Exception e){
            Log.e(LOG_TAG, "Agora RtcEngine create failed");
        }
        //set external Video type
        rtcEngine.setExternalVideoSource(true, false, true);
        rtcEngine.enableVideo();
    }

    public void registerUiHandler(MediaUiHandler uiHandler) {
        if (!uiHandlers.contains(uiHandler))
            uiHandlers.add(uiHandler);
    }

    public void unRegisterUiHandler(MediaUiHandler uiHandler) {
        uiHandlers.remove(uiHandler);
    }

    /**
     * set rtc Resolution
     * @param profile IRtcEngineEventHandler.VideoProfile.
     * @param swap swapWidthAndHeight
     */
    public void setVideoProfile(int profile, boolean swap) {
        mVideoProfile = profile;
        mSwapWidth = swap;
    }

    public void joinChannel(String channelId) {
        Log.d(LOG_TAG, "joinChannel " + channelId);

        this.channelId = channelId;

        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
        rtcEngine.setVideoProfile(mVideoProfile, mSwapWidth);
        rtcEngine.setClientRole(ClientRole.CLIENT_ROLE_BROADCASTER);

        rtcEngine.joinChannel(null, channelId, null, 0);
    }

    public void joinChannelForAudience(String channelId) {
        Log.d(LOG_TAG, "joinChannel " + channelId);

        this.channelId = channelId;

        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
        rtcEngine.setVideoProfile(mVideoProfile, mSwapWidth);
        rtcEngine.setClientRole(ClientRole.CLIENT_ROLE_AUDIENCE);

        rtcEngine.joinChannel(null, channelId, null, 0);
    }

    public void leaveChannel() {
        Log.d(LOG_TAG, "leaveChannel " + channelId);

        rtcEngine.leaveChannel();
        rtcEngine.stopPreview();
        channelId = null;
    }

    @Override
    public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
        Log.d(LOG_TAG, "onFirstRemoteVideoDecoded " + uid + " " + width + " " + height + " " + elapsed);

        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.FIRST_FRAME_DECODED, uid, width, height, elapsed);
        }
    }

    @Override
    public void onFirstLocalVideoFrame(int width, int height, int elapsed) {
        Log.d(LOG_TAG, "onFirstLocalVideoFrame " + " " + width + " " + height + " " + elapsed);
    }

    //用户进入
    @Override
    public void onUserJoined(int uid, int elapsed) {
        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.USER_JOINED, uid);
        }
    }

    @Override
    public void onUserOffline(int uid, int reason) {
        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.USER_OFFLINE, uid);
        }
    }

    //监听其他用户是否关闭视频
    @Override
    public void onUserMuteVideo(int uid, boolean muted) {
    }

    //更新聊天数据
    @Override
    public void onRtcStats(RtcStats stats) {
    }


    @Override
    public void onLeaveChannel(RtcStats stats) {
        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.LEAVE_CHANNEL);
        }
    }

    @Override
    public void onError(int err) {
        super.onError(err);
        Log.d(LOG_TAG, "onError " + err);

        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.ERROR, err);
        }
    }

    @Override
    public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
        Log.d(LOG_TAG, "onJoinChannelSuccess " + channel + " " + uid + " " + elapsed);

        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.JOIN_CHANNEL_RESULT, true, channel, uid, elapsed);
        }
    }

    @Override
    public void onRejoinChannelSuccess(String channel, int uid, int elapsed) {
        Log.d(LOG_TAG, "onRejoinChannelSuccess " + channel + " " + uid + " " + elapsed);
    }

    @Override
    public void onWarning(int warn) {
        Log.d(LOG_TAG, "onWarning " + warn);
    }

    public void onMediaEngineEvent(int code) {
        Log.d(LOG_TAG, "onMediaEngineEvent " + code);
    }

    public void onVendorMessage(int uid, byte[] data) {
        Log.d(LOG_TAG, "onVendorMessage " + uid + " " + data.toString());

        for (MediaUiHandler uiHandler : uiHandlers) {
            uiHandler.onMediaEvent(MediaUiHandler.ON_VENDOR_MSG, uid, data);
        }
    }

    public RtcEngine getRtcEngine() {
        return rtcEngine;
    }

}
