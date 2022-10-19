package io.agora.api.example.ui.main;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.metabolt.MTBServiceConfig;
import com.metabolt.MetaBoltTypes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import io.agora.api.example.R;
import io.agora.api.example.utils.CommonUtil;
import io.agora.api.example.utils.DownloadUtil;
import io.agora.api.example.utils.FileUtils;
import io.agora.api.example.utils.IMetaBoltDataHandler;
import io.agora.api.example.utils.IMetaFragmentHandler;
import io.agora.api.example.utils.MetaBoltManager;
import io.agora.api.example.utils.MetaBoltMgrCallback;
import io.agora.api.example.utils.PermissionUtils;
import io.agora.api.example.utils.TokenUtils;
import io.agora.api.example.utils.UserConfig;
import io.agora.rtc.AudioFrame;
import io.agora.rtc.Constants;
import io.agora.rtc.IAudioFrameObserver;
import io.agora.rtc.IMetadataObserver;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.IVideoFrameObserver;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.audio.AudioParams;
import io.agora.rtc.models.ChannelMediaOptions;
import io.agora.rtc.models.DataStreamConfig;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class MainFragment extends Fragment implements View.OnClickListener,
    TokenUtils.OnTokenListener,
    DownloadUtil.OnDownloadListener,
    IMetaFragmentHandler {
  private static final String TAG = "MainFragment";
  private MainViewModel mViewModel;

  private final int PLAY_MUSIC_IDLE = 0;
  private final int PLAY_MUSIC_PLAY = 1;
  private final int PLAY_MUSIC_PAUSE = 2;
  private boolean audioMixing = false;
  private int mLocalAudioStreamId = 0;
  private int mMetaServiceState = MetaBoltTypes.MTBServiceState.MTB_STATE_NOT_INIT;
  private boolean mIsRemoteVideoViewNeedShow = false;
  private boolean mIsRemoteMetaViewNeedShow = false;
  private String mRemoteUid = null;
  private FrameLayout fl_local_meta, fl_local, fl_remote_meta, fl_remote;
  private Button btn_join, btn_req_token, btn_music_dance, btn_music_beat;
  private EditText et_uid;
  private EditText et_channel;
  private RtcEngine engine;
  private boolean isUserJoined = false;
  private int mAudioMixingState = io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED;
  private int mAudioDuration = 0;
  private static final Integer SAMPLE_RATE = 44100;
  private static final Integer SAMPLE_NUM_OF_CHANNEL = 2;
  private static final Integer BIT_PER_SAMPLE = 16;
  private static final Integer SAMPLES_PER_CALL = 4410;
  private Handler mMainLooperHandler = new Handler(Looper.getMainLooper());


  // metabolt
  private View mRootView = null;
  private IMetaBoltDataHandler mMetaBoltDataHandler = null;
  private final String kLipSyncFileName = "lipsync";
  private final String kConfigJsonFileName = "/lipsync/config.json";

  private final String kRoleModelMale = "male_role";
  private final String kRoleModelFemale = "female_role";

  private final String kDanceResourceUrl = "https://test-rtcapm.duowan.cn/apm-admin/video?fileName=new_dance.zip";
  private final String kBeatAnimationResourceUrl = "https://test-rtcapm.duowan.cn/apm-admin/video?fileName=beat.zip";
  private final String kRoleModelResourceUrl = "https://test-rtcapm.duowan.cn/apm-admin/video?fileName=model_pkg_android.zip";

  private List<String> mMusicFileList = new ArrayList<>();
  private List<String> mBeatFileList = new ArrayList<>();
  private List<String> mBeatAnimationNameList = new ArrayList<>();
  private List<String> mRoleModelNameList = new ArrayList<>();

  public static MainFragment newInstance() {
    return new MainFragment();
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    mRootView = inflater.inflate(R.layout.main_fragment, container, false);
    return mRootView;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    btn_join = view.findViewById(R.id.btn_join);
    btn_req_token = view.findViewById(R.id.btn_req_token);
    btn_music_dance = view.findViewById(R.id.btn_music_dance);
    btn_music_beat = view.findViewById(R.id.btn_music_beat);
    btn_join.setOnClickListener(this);
    btn_req_token.setOnClickListener(this);
    btn_music_dance.setOnClickListener(this);
    btn_music_beat.setOnClickListener(this);
    et_uid = view.findViewById(R.id.et_uid);
    et_uid.setText(String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999)));
    et_channel = view.findViewById(R.id.et_channel);
    fl_local_meta = view.findViewById(R.id.fl_local_meta);
    fl_local = view.findViewById(R.id.fl_local);
    fl_remote_meta = view.findViewById(R.id.fl_remote_meta);
    fl_remote = view.findViewById(R.id.fl_remote);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    mViewModel = new ViewModelProvider(this).get(MainViewModel.class);
    // TODO: Use the ViewModel

    Context context = getContext();
    if (context == null) {
      return;
    }
    try {
      /**Creates an RtcEngine instance.
       * @param context The context of Android Activity
       * @param appId The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id">
       *              How to get the App ID</a>
       * @param handler IRtcEngineEventHandler is an abstract class providing default implementation.
       *                The SDK uses this class to report to the app on SDK runtime events.*/
      engine = RtcEngine.create(context.getApplicationContext(), getString(R.string.agora_app_id), iRtcEngineEventHandler);
    }
    catch (Exception e) {
      e.printStackTrace();
      getActivity().onBackPressed();
    }

    downResource();
    copyResource();
    initMetaService();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    /**leaveChannel and Destroy the RtcEngine instance*/
    if (engine != null) {
      engine.leaveChannel();
    }
    mMainLooperHandler.post(RtcEngine::destroy);
    engine = null;
    MetaBoltManager.instance().deInit();
  }

  protected void showInnerAlert(String message)
  {
    mMainLooperHandler.post(()->{
      Context context = getContext();
      if (context == null) {
        return;
      }

      new AlertDialog.Builder(context).setTitle("Tips").setMessage(message)
          .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
          .show();
    });
  }
  protected final void showInnerToast(final String msg)
  {
    mMainLooperHandler.post(new Runnable()
    {
      @Override
      public void run()
      {
        if (MainFragment.this == null || getContext() == null)
        {return;}
        Toast.makeText(getContext().getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View v) {
    switch(v.getId()) {
      case R.id.btn_req_token: {
        onRequestToken();
        break;
      }
      case R.id.btn_join: {
        if (isUserJoined) {
          isUserJoined = false;
          mAudioMixingState = io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED;
          mIsRemoteVideoViewNeedShow = false;
          mIsRemoteMetaViewNeedShow = false;
          mMetaServiceState = MetaBoltTypes.MTBServiceState.MTB_STATE_NOT_INIT;
          engine.stopAudioMixing();
          engine.leaveChannel();
          engine.stopPreview();
          btn_join.setText(getString(R.string.join_channel));

          MetaBoltManager.instance().destroyAvatarRole(UserConfig.kUid);
          MetaBoltManager.instance().destroyAvatarView(0);
          if (null != mRemoteUid) {
            MetaBoltManager.instance().destroyAvatarRole(mRemoteUid);
            MetaBoltManager.instance().destroyAvatarView(1);
          }
        } else {
          joinChannel(UserConfig.kChannelId);
          initMetaboltRole();
        }
        break;
      }
      case R.id.btn_music_dance: {
        if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING == mAudioMixingState) {
          btn_music_dance.setText(R.string.start_music_dance);
          engine.pauseAudioMixing();
          MetaBoltManager.instance().stopMusicDance();
        } else if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_COMPLETED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PAUSED == mAudioMixingState) {
          btn_music_dance.setText(R.string.stop_music_dance);
          String music = getMusicPath();
          if (0 == engine.getAudioMixingCurrentPosition()) {
            int ret = engine.startAudioMixing(music, false, false, 1, 0);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "startAudioMixing " + music + " failed " + engine.getErrorDescription(ret));
            }
            ret = engine.getAudioFileInfo(music);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "getAudioFileInfo " + music + " failed " + engine.getErrorDescription(ret));
            }
          } else {
            engine.resumeAudioMixing();
          }
          String danceMusic = getNewDanceDownPath();
          MetaBoltManager.instance().startMusicDance(danceMusic);
        }
        break;
      }
      case R.id.btn_music_beat: {
        if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING == mAudioMixingState) {
          btn_music_beat.setText(R.string.start_music_beat);
          engine.pauseAudioMixing();
          MetaBoltManager.instance().stopMusicBeat();
        } else if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_COMPLETED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PAUSED == mAudioMixingState) {
          btn_music_beat.setText(R.string.stop_music_beat);
          if (0 == engine.getAudioMixingCurrentPosition()) {
            String music = getMusicPath();
            int ret = engine.startAudioMixing(music, false, false, 1, 0);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "startAudioMixing " + music + " failed " + engine.getErrorDescription(ret));
            }
            ret = engine.getAudioFileInfo(music);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "getAudioFileInfo " + music + " failed " + engine.getErrorDescription(ret));
            }
          } else {
            engine.resumeAudioMixing();
          }
          String beatPath = getNewBeatDownPath();
          MetaBoltManager.instance().startMusicBeat(beatPath);
        }
        break;
      }
      default:
        break;
    }
  }

  private void onRequestToken() {
    CommonUtil.hideInputBoard(getActivity(), et_uid);
    CommonUtil.hideInputBoard(getActivity(), et_channel);
    // call when join button hit
    String uid = et_uid.getText().toString();
    String channelId = et_channel.getText().toString();
    if (uid.isEmpty() || channelId.isEmpty()) {
      showInnerToast("uid或者channelId不能为空!");
      return;
    }
    UserConfig.kUid = uid;
    UserConfig.kChannelId = channelId;
    TokenUtils.instance().requestTokenV2(getActivity(),
        UserConfig.kAppId, UserConfig.kUid, UserConfig.kChannelId, UserConfig.kTokenInvalidTime, this);
    PermissionUtils.checkPermissionAllGranted(getActivity());
//    if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE, Permission.Group.CAMERA)) {
//      return;
//    }
//    // Request permission
//    AndPermission.with(this).runtime().permission(
//        Permission.Group.STORAGE,
//        Permission.Group.MICROPHONE,
//        Permission.Group.CAMERA
//    ).onGranted(permissions ->
//    {
//      // Permissions Granted
//    }).start();
  }

  @Override
  public void onRequestTokenResult(int code, String token, String extra) {
    if (0 == code && token != null) {
      UserConfig.kToken = token;
      showInnerToast("请求token成功");
    } else {
      showInnerToast("请求token失败, code:" + code + ", extra:" + extra);
    }
  }


  /**
   * IRtcEngineEventHandler is an abstract class providing default implementation.
   * The SDK uses this class to report to the app on SDK runtime events.
   */
  private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler() {
    /**Reports a warning during SDK runtime.
     * Warning code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_warn_code.html*/
    @Override
    public void onWarning(int warn) {
      //Log.w(TAG, String.format("onWarning code %d message %s", warn, RtcEngine.getErrorDescription(warn)));
    }

    /**Reports an error during SDK runtime.
     * Error code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html*/
    @Override
    public void onError(int err) {
      Log.e(TAG, String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
      //showAlert(String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
    }

    /**Occurs when a user leaves the channel.
     * @param stats With this callback, the application retrieves the channel information,
     *              such as the call duration and statistics.*/
    @Override
    public void onLeaveChannel(RtcStats stats) {
      super.onLeaveChannel(stats);
      Log.i(TAG, String.format("local user %d leaveChannel!", UserConfig.kUid));
      showInnerToast(String.format("local user %d leaveChannel!", UserConfig.kUid));
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.onLeaveRoom();
      }
    }

    /**Occurs when the local user joins a specified channel.
     * The channel name assignment is based on channelName specified in the joinChannel method.
     * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
     * @param channel Channel name
     * @param uid User ID
     * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered*/
    @Override
    public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
      Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
      showInnerToast(String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
      isUserJoined = true;
      mMainLooperHandler.post(new Runnable() {
        @Override
        public void run() {
          btn_join.setEnabled(true);
          btn_join.setText(getString(R.string.leave_channel));
          // 创建createDataStream,发送音频sei
          DataStreamConfig dataStreamConfig = new DataStreamConfig();
          dataStreamConfig.ordered = true;
          dataStreamConfig.syncWithAudio = true;
          mLocalAudioStreamId = engine.createDataStream(dataStreamConfig);

          if (null != mMetaBoltDataHandler) {
            mMetaBoltDataHandler.onJoinRoomSuccess(UserConfig.kChannelId, UserConfig.kUid, elapsed);
          }
        }
      });
    }

    /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
     * @param uid ID of the user whose audio state changes.
     * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
     *                until this callback is triggered.*/
    @Override
    public void onUserJoined(int uid, int elapsed) {
      super.onUserJoined(uid, elapsed);
      Log.i(TAG, "onUserJoined->" + uid);
      showInnerToast(String.format("user %d joined!", uid));
      /**Check if the context is correct*/
      Context context = getContext();
      if (context == null) {
        return;
      }
      mMainLooperHandler.post(() ->
      {
        mRemoteUid = String.valueOf(uid);
        /**Display remote video stream*/
        if (addRemoteVideoView(context) < 0) {
          showInnerToast("添加" + uid + "视图失败");
          mIsRemoteVideoViewNeedShow = true;
        } else {
          mIsRemoteVideoViewNeedShow = false;
        }

        if (mMetaServiceState == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS) {
          mIsRemoteMetaViewNeedShow = false;
          // 创建远端meta role
          initRemoteMetaView(context);
        } else {
          mIsRemoteMetaViewNeedShow = true;
        }
      });
    }

    /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
     * @param uid ID of the user whose audio state changes.
     * @param reason Reason why the user goes offline:
     *   USER_OFFLINE_QUIT(0): The user left the current channel.
     *   USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
     *              packet was received within a certain period of time. If a user quits the
     *               call and the message is not passed to the SDK (due to an unreliable channel),
     *               the SDK assumes the user dropped offline.
     *   USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
     *               the host to the audience.*/
    @Override
    public void onUserOffline(int uid, int reason) {
      Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
      showInnerToast(String.format("user %d offline! reason:%d", uid, reason));
      mMainLooperHandler.post(new Runnable() {
        @Override
        public void run() {
          /**Clear render view
           Note: The video will stay at its last frame, to completely remove it you will need to
           remove the SurfaceView from its parent*/
          if (null != mMetaBoltDataHandler) {
            mMetaBoltDataHandler.onUserOffline(String.valueOf(uid), 0);
          }
          engine.setupRemoteVideo(new VideoCanvas(null, Constants.RENDER_MODE_HIDDEN, uid));
          MetaBoltManager.instance().destroyAvatarRole(mRemoteUid);
          MetaBoltManager.instance().destroyAvatarView(1);
          mRemoteUid = null;
        }
      });
    }

    @Override
    public void onStreamMessage(int uid, int streamId, byte[] data) {
      Log.i(TAG, "onStreamMessage uid:" + uid + ", streamId:" + streamId + ", data:" + data);
      // 回调音频sei数据
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handleRecvMediaExtraInfo(String.valueOf(uid), data, data.length);
      }
    }

    @Override
    public void onStreamMessageError(int uid, int streamId, int error, int missed, int cached) {
      Log.e(TAG, "onStreamMessageError uid:" + uid + ", streamId:" + streamId + ", err:" + error + ", missed:" + missed + ", cached:" + cached);
    }

    @Override
    public void onLocalAudioStateChanged(int state, int error) {
      //LOCAL_AUDIO_STREAM_STATE_STOPPED;
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.onLocalAudioStatusChanged(state, error);
      }
    }

    @Override
    public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
      if (null != mMetaBoltDataHandler) {
        if (Constants.REMOTE_AUDIO_STATE_STOPPED == state) {
          mMetaBoltDataHandler.onRemoteAudioStopped(String.valueOf(uid), true);
        } else if (Constants.REMOTE_AUDIO_STATE_STARTING == state) {
          mMetaBoltDataHandler.onRemoteAudioStopped(String.valueOf(uid), false);
        }
      }
    }

    @Override
    public void onAudioPublishStateChanged(String channel, int oldState, int newState, int elapseSinceLastState) {
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.onLocalAudioPublishStatus(newState);
//        switch (newState) {
//          case PUB_STATE_IDLE:
//          case PUB_STATE_NO_PUBLISHED:
//          case PUB_STATE_PUBLISHING: {
//            mMetaBoltDataHandler.onLocalAudioPublishStatus(THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_STOP);
//            break;
//          }
//          case PUB_STATE_PUBLISHED: {
//            mMetaBoltDataHandler.onLocalAudioPublishStatus(THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_START);
//            break;
//          }
//          default: {
//            break;
//          }
//        }
      }
    }

    @Override
    public void onAudioMixingStateChanged(int state, int reason) {
      Log.d(TAG, "onAudioMixingStateChanged state:" + state + ", reason:" + reason);
      mAudioMixingState = state;
      switch (state) {
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING: {
          MetaBoltManager.instance().enableAudioPlayStatus(true);
          break;
        }
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED:
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_FAILED: {
          MetaBoltManager.instance().enableAudioPlayStatus(false);
          break;
        }
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_COMPLETED: {
          MetaBoltManager.instance().stopMusicDance();
          break;
        }
        default:
          break;
      }
    }

    @Override
    public void onVideoPublishStateChanged(String channel, int oldState, int newState, int elapseSinceLastState) {
    }

    @Override
    public void onLocalVideoStateChanged(int localVideoState, int error) {
    }


  };


  private void joinChannel(String channelId) {
    // Check if the context is valid
    Context context = getContext();
    if (context == null) {
      return;
    }

    // Create render view by RtcEngine
    //SurfaceView surfaceView = new SurfaceView(context);
    SurfaceView surfaceView = RtcEngine.CreateRendererView(context);
    // Add to the local container
    fl_local.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // Setup local video to render your local camera preview
    engine.setupLocalVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, Integer.valueOf(UserConfig.kUid)));

    /** Sets the channel profile of the Agora RtcEngine.
     CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
     Use this profile in one-on-one calls or group calls, where all users can talk freely.
     CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
     channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
     an audience can only receive streams.*/
    engine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
    /**In the demo, the default is to enter as the anchor.*/
    engine.setClientRole(IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_BROADCASTER);
    // Enable video module
    engine.enableVideo();
    engine.startPreview();
    //engine.switchCamera();
    // Setup video encoding configs
    engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
        VideoEncoderConfiguration.VD_640x360,
        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
        VideoEncoderConfiguration.STANDARD_BITRATE,
        VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
    ));
    /**Set up to play remote sound with receiver*/
    engine.setDefaultAudioRoutetoSpeakerphone(true);
    engine.setEnableSpeakerphone(false);

    /**
     * Sets the audio recording format for the onRecordAudioFrame callback.
     * sampleRate	Sets the sample rate (samplesPerSec) returned in the onRecordAudioFrame callback, which can be set as 8000, 16000, 32000, 44100, or 48000 Hz.
     * channel	Sets the number of audio channels (channels) returned in the onRecordAudioFrame callback:
     * 1: Mono
     * 2: Stereo
     * mode	Sets the use mode (see RAW_AUDIO_FRAME_OP_MODE_TYPE) of the onRecordAudioFrame callback.
     * samplesPerCall	Sets the number of samples returned in the onRecordAudioFrame callback. samplesPerCall is usually set as 1024 for RTMP streaming.
     * The SDK triggers the onRecordAudioFrame callback according to the sample interval. Ensure that the sample interval ≥ 0.01 (s). And, Sample interval (sec) = samplePerCall/(sampleRate × channel).
     */
    engine.setRecordingAudioFrameParameters(16000, 2, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_WRITE, 1024);

    /**
     * Sets the audio playback format for the onPlaybackAudioFrame callback.
     * sampleRate	Sets the sample rate (samplesPerSec) returned in the onRecordAudioFrame callback, which can be set as 8000, 16000, 32000, 44100, or 48000 Hz.
     * channel	Sets the number of audio channels (channels) returned in the onRecordAudioFrame callback:
     * 1: Mono
     * 2: Stereo
     * mode	Sets the use mode (see RAW_AUDIO_FRAME_OP_MODE_TYPE) of the onRecordAudioFrame callback.
     * samplesPerCall	Sets the number of samples returned in the onRecordAudioFrame callback. samplesPerCall is usually set as 1024 for RTMP streaming.
     * The SDK triggers the onRecordAudioFrame callback according to the sample interval. Ensure that the sample interval ≥ 0.01 (s). And, Sample interval (sec) = samplePerCall/(sampleRate × channel).
     */
    engine.setPlaybackAudioFrameParameters(16000, 2, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024);

    /**
     * Sets the mixed audio format for the onMixedAudioFrame callback.
     * sampleRate	Sets the sample rate (samplesPerSec) returned in the onMixedAudioFrame callback, which can be set as 8000, 16000, 32000, 44100, or 48000 Hz.
     * samplesPerCall	Sets the number of samples (samples) returned in the onMixedAudioFrame callback. samplesPerCall is usually set as 1024 for RTMP streaming.
     */
    engine.setMixedAudioFrameParameters(32000, 1024);

    engine.registerVideoFrameObserver(iVideoFrameObserver);

    /**Please configure accessToken in the string_config file.
     * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
     *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
     * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
     *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/
    String accessToken = getString(R.string.agora_access_token);
    if (TextUtils.equals(accessToken, "") || TextUtils.equals(accessToken, "<#YOUR ACCESS TOKEN#>")) {
      accessToken = null;
    }
    /** Allows a user to join a channel.
     if you do not specify the uid, we will generate the uid for you*/

    ChannelMediaOptions option = new ChannelMediaOptions();
    option.autoSubscribeAudio = true;
    option.autoSubscribeVideo = true;
    int localUid = Integer.valueOf(UserConfig.kUid);
    int res = engine.joinChannel(accessToken, channelId, "Extra Optional Data", localUid, option);
    if (res != 0) {
      // Usually happens with invalid parameters
      // Error code description can be found at:
      // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
      // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
      showInnerAlert(RtcEngine.getErrorDescription(Math.abs(res)));
      return;
    }
    // Prevent repeated entry
    btn_join.setEnabled(false);
    /** Registers the audio observer object.
     *
     * @param observer Audio observer object to be registered. See {@link IAudioFrameObserver IAudioFrameObserver}. Set the value as @p null to cancel registering, if necessary.
     * @return
     * - 0: Success.
     * - < 0: Failure.
     */
    engine.registerAudioFrameObserver(audioFrameObserver);
    // 注册视频sei回调
    engine.registerMediaMetadataObserver(metadataObserver, IMetadataObserver.VIDEO_METADATA);
  }

  private final IVideoFrameObserver iVideoFrameObserver = new IVideoFrameObserver() {
    @Override
    public boolean onCaptureVideoFrame(VideoFrame videoFrame) {
      //mMetaBoltDataHandler.
//      if (!isSnapshot) {
//        return true;
//      }
//      byte[] rgb = new byte[videoFrame.yBuffer.remaining()];
//      videoFrame.yBuffer.get(rgb, 0 , videoFrame.yBuffer.remaining());
//      Bitmap bitmap = YUVUtils.bitmapFromRgba(videoFrame.width, videoFrame.height, rgb);
//      Matrix matrix = new Matrix();
//      matrix.setRotate(270);
//      Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, videoFrame.width, videoFrame.height, matrix, false);
//      saveBitmap2Gallery(newBitmap);
//
//      bitmap.recycle();
//      isSnapshot = false;
      return true;
    }

    @Override
    public boolean onRenderVideoFrame(int uid, VideoFrame videoFrame) {
      return true;
    }

    @Override
    public int getVideoFormatPreference() {
      return FRAME_TYPE_RGBA;
    }

    @Override
    public int getObservedFramePosition() {
      return POSITION_POST_CAPTURER;
    }
  };

  private byte[] readBuffer(){
    int byteSize = SAMPLES_PER_CALL * BIT_PER_SAMPLE / 8;
    byte[] buffer = new byte[byteSize];
//    try {
//      if(inputStream.read(buffer) < 0){
//        inputStream.reset();
//        return readBuffer();
//      }
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
    return buffer;
  }

  private byte[] audioAggregate(byte[] origin, byte[] buffer) {
    byte[] output = new byte[origin.length];
    for (int i = 0; i < origin.length; i++) {
      output[i] = (byte) ((int) origin[i] + (int) buffer[i] / 2);
    }
    return output;
  }

  private final IAudioFrameObserver audioFrameObserver = new IAudioFrameObserver() {
    @Override
    public boolean onRecordFrame(AudioFrame audioFrame) {
      Log.i(TAG, "onRecordAudioFrame " + audioFrame.toString());
      int length = audioFrame.samples.limit();
      byte[] buffer = new byte[length];
      audioFrame.samples.get(buffer);
      mMetaBoltDataHandler.handlerCaptureAudioData(buffer,
          length,
          audioFrame.samplesPerSec,
          audioFrame.channels,
          true);
      buffer = null;
      return true;
    }

    @Override
    public boolean onPlaybackFrame(AudioFrame audioFrame) {
      return false;
    }

    @Override
    public boolean onPlaybackFrameBeforeMixing(AudioFrame audioFrame, int uid) {
      return false;
    }

    @Override
    public boolean onMixedFrame(AudioFrame audioFrame) {
      Log.i(TAG, "onMixedFrame " + audioFrame.toString());
      return false;
    }

    @Override
    public boolean isMultipleChannelFrameWanted() {
      return false;
    }

    @Override
    public boolean onPlaybackFrameBeforeMixingEx(AudioFrame audioFrame, int uid, String channelId) {
      return false;
    }

    @Override
    public int getObservedAudioFramePosition() {
      return IAudioFrameObserver.POSITION_RECORD | IAudioFrameObserver.POSITION_MIXED;
    }

    @Override
    public AudioParams getRecordAudioParams() {
      return new AudioParams(SAMPLE_RATE, SAMPLE_NUM_OF_CHANNEL, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_WRITE, SAMPLES_PER_CALL);
    }

    @Override
    public AudioParams getPlaybackAudioParams() {
      return new AudioParams(SAMPLE_RATE, SAMPLE_NUM_OF_CHANNEL, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, SAMPLES_PER_CALL);
    }

    @Override
    public AudioParams getMixedAudioParams() {
      return new AudioParams(SAMPLE_RATE, SAMPLE_NUM_OF_CHANNEL, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, SAMPLES_PER_CALL);
    }
  };

  private IMetadataObserver metadataObserver = new IMetadataObserver() {
    @Override
    public int getMaxMetadataSize() {
      return 0;
    }

    @Override
    public byte[] onReadyToSendMetadata(long timeStampMs) {
      return new byte[0];
    }

    @Override
    public void onMetadataReceived(byte[] buffer, int uid, long timeStampMs) {

    }
  };

  private int addRemoteVideoView(Context context) {
    //SurfaceView surfaceView = new SurfaceView(context);
    SurfaceView surfaceView = RtcEngine.CreateRendererView(context);
    surfaceView.setZOrderMediaOverlay(true);
    if (fl_remote.getChildCount() > 0) {
      fl_remote.removeAllViews();
    }
    // Add to the remote container
    fl_remote.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // Setup remote video to render
    return engine.setupRemoteVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, Integer.valueOf(mRemoteUid)));
  }


  // meabolt
  private void initMetaboltRole() {
    // gsq code
    MetaBoltManager.instance().registerMgrCallback(new MetaBoltMgrCallback() {
      @Override
      public void onJoinRoomSuccess(String channel, String uid, int elapsed) {
      }

      @Override
      public void onUserOffline(String uid, int reason) {
      }

      @Override
      public void onRemoteAudioStopped(String uid, boolean stop) {
      }

      @Override
      public void onMetaBoltServiceStateChanged(int state) {
        mMetaServiceState = state;
        if (state == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS) {
          mMainLooperHandler.post(() -> {
            Context context = getContext();
            if (context == null) {
              return;
            }
            MetaBoltManager.instance().createAvatarView(context, 0);
            MetaBoltManager.instance().createAvatarRole(getRoleModelPath(kRoleModelFemale), UserConfig.kUid);
            MetaBoltManager.instance().setRoleIndex(0, UserConfig.kUid);
            MetaBoltManager.instance().setAvatarViewType(UserConfig.kUid, MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_WHOLE);
            // MetaBoltManager.instance().setAvatarViewType(UserConfig.kUid, MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HEAD);

            MetaBoltManager.instance().setAnimation(UserConfig.kUid, getAnimationDownPath());
            MetaBoltManager.instance().startFaceEmotionByAudio();
            //MetaBoltManager.instance().startFaceEmotionByCamera();
            //String beatDataPath = "/storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/Ku_puja_puja/Ku_puja_puja.bin";
            //MetaBoltManager.instance().startMusicBeat(getNewBeatDownPath());
            //String danceDataPath = "/storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/Pamer_Bojo/Pamer_Bojo.dat";
            //MetaBoltManager.instance().startMusicDance(getNewDanceDownPath());

            if (mIsRemoteMetaViewNeedShow) {
              mIsRemoteMetaViewNeedShow = false;
              initRemoteMetaView(context);
            }
            if (mIsRemoteVideoViewNeedShow) {
              mIsRemoteVideoViewNeedShow = false;
              addRemoteVideoView(context);
            }
            // showLongToast("metabolt service init success");
            Log.d(TAG, "metabolt service init success");
          });
        }
      }
    });

    MTBServiceConfig config = new MTBServiceConfig();
    config.context = getContext();
    config.appId = UserConfig.kAppId;
    config.accessToken = UserConfig.kToken;
    config.AIModelPath = UserConfig.kAIModelPath;
    MetaBoltManager.instance().init(config, true);
  }

  private void initRemoteMetaView(Context context) {
    MetaBoltManager.instance().createAvatarView(context, 1);
    MetaBoltManager.instance().createAvatarRole(getRoleModelPath(kRoleModelMale), mRemoteUid);
    MetaBoltManager.instance().setRoleIndex(1, mRemoteUid);
    MetaBoltManager.instance().setAvatarViewType(mRemoteUid, MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HALF);
    MetaBoltManager.instance().setAnimation(mRemoteUid, getRemoteAnimationDownPath());
  }

  private void initMetaService() {
    MetaBoltManager.createMetaBoltManager(getConfigJsonFile());
    MetaBoltManager.instance().registerSEICallback(this);
    MetaBoltManager.instance().setRootView(mRootView);

    mMetaBoltDataHandler = MetaBoltManager.instance();
  }

  /**
   * 资源相关 by Hsu
   */
  public void onDownloadSuccess(String url, String path, boolean isExist) {
    Log.i(TAG, "onDownloadSuccess url: " + url + ", path: " + path + ", is exist: " + isExist);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          String downPath = DownloadUtil.get().getDownloadPath(getContext());
          if (!isExist) {
            FileUtils.unZip(path, downPath);
          }

          String targetFile = "";
          if (url.equalsIgnoreCase(kDanceResourceUrl)) {
            targetFile = "new_dance";
          } else if (url.equalsIgnoreCase(kBeatAnimationResourceUrl)) {
            targetFile = "beat";
          } else if (url.equalsIgnoreCase(kRoleModelResourceUrl)) {
            targetFile = "model_pkg_android";
          }

          List<String> fileListWithoutTypeList = new ArrayList<>();
          List<String> datas = FileUtils.getFilesAllName(downPath + File.separator + targetFile);
          if (datas != null && !datas.isEmpty()) {
            for (String data : datas) {
              if (!data.contains(".")) {
                fileListWithoutTypeList.add(data);
              }
            }
          }

          int musicCnt = 0;
          StringBuilder fileContent = new StringBuilder();
          for (String fileNameStr : fileListWithoutTypeList) {
            fileContent.append("{ ").append(musicCnt).append(": ").append(fileNameStr).append(" }");
          }
          Log.i(TAG, "onDownloadSuccess run, url: " + url + ", path: " + path + ", data list: " + fileContent.toString());

          if (!fileListWithoutTypeList.isEmpty()) {
            mMainLooperHandler.post(() -> {
              if (url.equals(kDanceResourceUrl)) {
                updateDanceList(fileListWithoutTypeList);
                updateBeatList(fileListWithoutTypeList);
                updateMusicList(fileListWithoutTypeList);
              } else if (url.equalsIgnoreCase(kBeatAnimationResourceUrl)) {
                updateBeatAnimationList(fileListWithoutTypeList);
              } else if (url.equalsIgnoreCase(kRoleModelResourceUrl)) {
                updateRoleModelFile(fileListWithoutTypeList);
              }
            });
          }

          // gsq code
          //mMetaBoltDataHandler.onJoinRoomSuccess(UserConfig.kChannelId, UserConfig.kUid, elapsed);
//                    String roleModelPath = DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator + "model_pkg_android" + File.separator + "6767.json";
          //String rolePath = "/storage/emulated/0/Android/data/io.agora.api.example/files/download/model_pkg_android/6767.json";
//                    MetaBoltManager.instance().createAvatarView(getContext(), 0);
//                    MetaBoltManager.instance().createAvatarRole(roleModelPath, UserConfig.kUid);
        } catch (Exception e) {
          Log.e(TAG, "onDownloadSuccess, failed: " + e.toString());
        }
      }
    }).start();
  }

  @Override
  public void onDownloading(int progress) {
//        Log.i(TAG, "onDownloading progress: " + progress);
  }

  @Override
  public void onDownloadFailed(String url) {
    Log.e(TAG, "onDownloadFailed url: " + url);
    mMainLooperHandler.post(new Runnable() {
      @Override
      public void run() {
        showInnerToast("下载资源出错,请检查网络");
      }
    });
  }

  public void updateDanceList(List<String> musicNameList) {
    if (musicNameList.isEmpty()) {
      return;
    }

    mMusicFileList.clear();
    for (String string : musicNameList) {
      if (!string.contentEquals("Store")) {
        mMusicFileList.add(string);
      }
    }
  }

  public void updateBeatList(List<String> musicNameList) {
    if (musicNameList.isEmpty()) {
      return;
    }

    mMusicFileList.clear();
    for (String string : musicNameList) {
      if (!string.contentEquals("Store")) {
        mMusicFileList.add(string);
      }
    }
  }

  public void updateMusicList(List<String> musicNameList) {
    if (musicNameList.isEmpty()) {
      return;
    }

    if (!mMusicFileList.isEmpty()) {
      return;
    }

    for (String string : musicNameList) {
      if (!string.contentEquals("Store")) {
        mMusicFileList.add(string);
      }
    }
  }

  public void updateBeatAnimationList(List<String> animationList) {
    if (animationList.isEmpty()) {
      return;
    }

    mBeatAnimationNameList.clear();
    for (String string : animationList) {
      if (!string.contentEquals("Store")) {
        mBeatAnimationNameList.add(string);
      }
    }
  }

  public void updateRoleModelFile(List<String> modelList) {
    if (modelList.isEmpty()) {
      return;
    }

    mRoleModelNameList.clear();
    for (String string : modelList) {
      if (!string.contentEquals("Store") &&
          !string.contentEquals("json") &&
          !string.contentEquals("manifest") &&
          !string.contentEquals("clothes")) {
        mRoleModelNameList.add(string);
      }
    }

    if (mRoleModelNameList.isEmpty()) {
      return;
    }
  }


  private String getConfigJsonFile() {
    // targetDir="/data/user/0/io.agora.api.example/files"
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    return targetDir + kConfigJsonFileName;
  }

  private String getNewBeatDownPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/Ku_puja_puja/Ku_puja_puja.bin
    //String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/new_dance/Ku_puja_puja/Ku_puja_puja.bin";
    //return "/storage/emulated/0/Android/data/io.agora.api.example/files/download/new_dance/Ku_puja_puja/Ku_puja_puja.bin";
  }

  private String getNewDanceDownPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/Ku_puja_puja/Ku_puja_puja.dat
    // /storage/emulated/0/Android/data/io.agora.api.example/files/download/new_dance/Pamer_Bojo/Pamer_Bojo.dat
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/new_dance/Pamer_Bojo/Pamer_Bojo.dat";
    //return "/storage/emulated/0/Android/data/io.agora.api.example/files/download/new_dance/Pamer_Bojo/Pamer_Bojo.dat";
  }

  private String getAnimationDownPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/beat/singing/singing
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/beat/singing/singing";
    //return "/storage/emulated/0/Android/data/io.agora.api.example/files/download/beat/singing/singing";
  }

  private String getRemoteAnimationDownPath() {
    // /storage/emulated/0/Android/data/io.agora.api.example/files/download/beat/jump/jump
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/beat/singing/singing";
    //return "/storage/emulated/0/Android/data/io.agora.api.example/files/download/beat/jump/jump";
  }

  private String getRoleModelPath(String roleName) {
    //String roleModelPath = DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator + "model_pkg_android" + File.separator + "female_role.json";
    String downPath = DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator;
    //String targetPath = "model_pkg_android" + File.separator + "male_role" + ".json";
    String targetPath = "model_pkg_android" + File.separator + roleName + ".json";
    return downPath + targetPath;
  }

  private String getMusicPath() {
    // /data/user/0/io.agora.api.example/files/download/new_dance/qinghuaci/qinghuaci.mp3
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/qinghuaci/qinghuaci.mp3
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/new_dance/qinghuaci/qinghuaci.mp3";
    //return "/storage/emulated/0/Android/data/io.agora.api.example/files/download/new_dance/qinghuaci/qinghuaci.mp3";
  }

  private void downResource() {
    DownloadUtil.get().download(getContext(), kBeatAnimationResourceUrl, this);
    DownloadUtil.get().download(getContext(), kDanceResourceUrl, this);
    DownloadUtil.get().download(getContext(), kRoleModelResourceUrl, this);
  }

  private void copyResource() {
    String path = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    String targetDir = path + File.separator;
    copyFilesFromAssets(kLipSyncFileName, targetDir + kLipSyncFileName);
  }

  private void copyFilesFromAssets(String assetsPath, String savePath) {
    try {
      // 获取assets指定目录下的所有文件
      String[] fileList = Objects.requireNonNull(getActivity()).getAssets().list(assetsPath);
      if (fileList != null && fileList.length > 0) {
        File file = new File(savePath);
        // 如果目标路径文件夹不存在，则创建
        if (!file.exists()) {
          if (!file.mkdirs()) {
            Log.e(TAG, "mkdir error: " + savePath);
            return;
          }
        }
        for (String fileName : fileList) {
          copyFileFromAssets(assetsPath + "/" + fileName, savePath, fileName);
          Log.i(TAG, "copy files from assets, file name: " + fileName);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "copyFilesFromAssets failed: " + e.toString());
    }
  }

  public void copyFileFromAssets(String assetName, String savePath, String saveName) {
    // 若目标文件夹不存在，则创建
    File dir = new File(savePath);
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        Log.e(TAG, "mkdir error: " + savePath);
        return;
      }
    }

    // 拷贝文件
    String filename = savePath + "/" + saveName;
    File file = new File(filename);
    if (!file.exists()) {
      try {
        InputStream inStream = Objects.requireNonNull(getActivity()).getAssets().open(assetName);
        FileOutputStream fileOutputStream = new FileOutputStream(filename);

        int byteread;
        byte[] buffer = new byte[1024];
        while ((byteread = inStream.read(buffer)) != -1) {
          fileOutputStream.write(buffer, 0, byteread);
        }
        fileOutputStream.flush();
        inStream.close();
        fileOutputStream.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      Log.i(TAG, "[copyFileFromAssets] copy asset file: " + assetName + " to: " + filename);
    } else {
      Log.i(TAG, "[copyFileFromAssets] file is exist: " + filename);
    }
  }

  @Override
  public void onStateMsgCallback(String msg) {
    Log.i(TAG, "[onStateMsgCallback] " + msg);
  }

  @Override
  public int onAudioSEIData(ByteBuffer byteBuffer) {
    byteBuffer.rewind();
    byte[] buffer = new byte[byteBuffer.limit()];
    byteBuffer.get(buffer);
    int ret = engine.sendStreamMessage(mLocalAudioStreamId, buffer);
    if (ret < Constants.ERR_OK) {
      Log.e(TAG, "sendStreamMessage error:" + ret + ", desc:" + RtcEngine.getErrorDescription(ret));
    }
    buffer = null;
    return ret;
  }

  @Override
  public long getMusicPlayCurrentProgress() {
    //if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING != mAudioMixingState) return 0;
    int pos = engine.getAudioMixingCurrentPosition();
    Log.i(TAG, "AudioMixing music progress position:" + pos);
    return pos;
  }

  @Override
  public long getMusicPlayTotalProgress() {
    //if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING != mAudioMixingState) return 0;
    mAudioDuration = engine.getAudioMixingDuration();
    Log.i(TAG, "AudioMixing music progress total:" + mAudioDuration);
    return mAudioDuration;
  }
}