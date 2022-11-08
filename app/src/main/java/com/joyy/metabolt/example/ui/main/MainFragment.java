package com.joyy.metabolt.example.ui.main;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.metabolt.MTBServiceConfig;
import com.metabolt.MetaBoltTypes;
import com.tencent.trtc.TRTCCloud;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import com.joyy.metabolt.example.R;
import com.joyy.metabolt.example.utils.CommonUtil;
import com.joyy.metabolt.example.utils.DownloadUtil;
import com.joyy.metabolt.example.utils.FileUtils;
import com.joyy.metabolt.example.utils.IMetaBoltDataHandler;
import com.joyy.metabolt.example.utils.IMetaFragmentHandler;
import com.joyy.metabolt.example.utils.MetaBoltManager;
import com.joyy.metabolt.example.utils.MetaBoltMgrCallback;
import com.joyy.metabolt.example.utils.PermissionUtils;
import com.joyy.metabolt.example.utils.TokenUtils;
import com.joyy.metabolt.example.utils.UserConfig;
import com.tencent.trtc.TRTCCloudListener;
import com.thunder.livesdk.ThunderRtcConstant;

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

  private boolean audioMixing = false;
  private int mLocalAudioStreamId = 0;
  private int mMetaServiceState = MetaBoltTypes.MTBServiceState.MTB_STATE_NOT_INIT;
  private boolean mIsRemoteVideoViewNeedShow = false;
  private boolean mIsRemoteMetaViewNeedShow = false;
  private String mRemoteUid = null;
  private FrameLayout fl_local_meta, fl_local, fl_remote_meta, fl_remote;
  private TextView tv_metabolt_show;
  private Button btn_join, btn_init_rtc, btn_music_dance, btn_music_beat;
  private RadioGroup bg_rtc_type, bg_sync_type;
  private Spinner sp_sync_type, sp_avatar_view_type, sp_music_array;
  private EditText et_uid;
  private EditText et_channel;
  private boolean isUserJoined = false;
  private int mAudioMixingState = io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED;
  private int mAudioDuration = 0;
  private static final Integer SAMPLE_RATE = 44100;
  private static final Integer SAMPLE_NUM_OF_CHANNEL = 2;
  private static final Integer BIT_PER_SAMPLE = 16;
  private static final Integer SAMPLES_PER_CALL = 4410;
  private Handler mMainLooperHandler = new Handler(Looper.getMainLooper());

  private final int SYNC_TYPE_AUDIO = 0; // libsync
  private final int SYNC_TYPE_VIDEO = 1; // facesync
  private final int METABOLT_INIT_TYPE_AGORA = 0; // metabolt借用agora通道
  private final int METABOLT_INIT_TYPE_TRTC = 1; // metabolt借用trtc通道
  private final int METABOLT_INIT_TYPE_THUNDERBOLT = 3; // metabolt借用thunderbolt通道
  private boolean mIsRtcInitialized = false;
  private int mMetaboltInitType = METABOLT_INIT_TYPE_AGORA;
  private int mSyncType = SYNC_TYPE_AUDIO;
  private int mAvatarViewType = MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HALF; // 默认显示半身

  private RtcEngine mAgoraEngine; // agora声网

  private TRTCCloud mTRTCCloud; // trtc腾讯云rtc
  private TRTCCloudListener.TRTCAudioFrameListener trtcAudioFrameListener;

  // metabolt
  private View mRootView = null;
  private IMetaBoltDataHandler mMetaBoltDataHandler = null;
  private final String kLipSyncFileName = "lipsync";
  private final String kFaceSyncFileName = "facesync";
  private final String kConfigJsonFileName = "/lipsync/config.json";

  private final String kRoleModelMale = "male_role";
  private final String kRoleModelFemale = "female_role";

  private final String kZipType = ".zip";
  private final String kServiceHttpsUrl = "https://test-rtcapm.duowan.cn/apm-admin/video?fileName=";

  private final int kAudioPlayInterval = 40;

  /**
   * 资源默认路径
   */
  private final static String kDanceMusicDir = "new_dance";
  private final static String kBeatAnimationDir = "beat";
  private final static String kRoleModelPkgDir = "model_pkg_android";

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
    btn_init_rtc = view.findViewById(R.id.btn_init_rtc);
    btn_music_dance = view.findViewById(R.id.btn_music_dance);
    btn_music_beat = view.findViewById(R.id.btn_music_beat);
    btn_join.setOnClickListener(this);
    btn_init_rtc.setOnClickListener(this);
    btn_music_dance.setOnClickListener(this);
    btn_music_beat.setOnClickListener(this);
    et_uid = view.findViewById(R.id.et_uid);
    et_uid.setText(String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999)));
    et_channel = view.findViewById(R.id.et_channel);
    et_channel.setText(UserConfig.kChannelId);
    fl_local_meta = view.findViewById(R.id.fl_local_meta);
    fl_local = view.findViewById(R.id.fl_local);
    fl_remote_meta = view.findViewById(R.id.fl_remote_meta);
    fl_remote = view.findViewById(R.id.fl_remote);
    tv_metabolt_show = view.findViewById(R.id.tv_metabolt_show);
    bg_rtc_type = view.findViewById(R.id.rg_rtc_type);
    bg_rtc_type.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
          case R.id.rb_trtc:
            mMetaboltInitType = METABOLT_INIT_TYPE_TRTC;
            tv_metabolt_show.setText(getString(R.string.metabolt_type_trtc_desc));
            break;
          case R.id.rb_thunder:
            mMetaboltInitType = METABOLT_INIT_TYPE_THUNDERBOLT;
            tv_metabolt_show.setText(getString(R.string.metabolt_type_thunder_desc));
            break;
          default:
            mMetaboltInitType = METABOLT_INIT_TYPE_AGORA;
            tv_metabolt_show.setText(getString(R.string.metabolt_type_agora_desc));
            break;
        }
      }
    });

    String[] syncItems = getResources().getStringArray(R.array.sync_type);
    ArrayAdapter<String> syncAdapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, syncItems);
    syncAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp_sync_type = view.findViewById(R.id.sp_sync_type);
    sp_sync_type.setAdapter(syncAdapter);
    sp_sync_type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == mSyncType) return;
        if (position == 0) {
          mSyncType = SYNC_TYPE_AUDIO;
          if (MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS == mMetaServiceState) {
            MetaBoltManager.instance().startFaceEmotionByAudio();
          }
        } else {
          mSyncType = SYNC_TYPE_VIDEO;
          if (MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS == mMetaServiceState) {
            MetaBoltManager.instance().startFaceEmotionByCamera();
          }
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });

    String[] avatarItems = getResources().getStringArray(R.array.avatar_view_type);
    ArrayAdapter<String> avatarAdapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, avatarItems);
    avatarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp_avatar_view_type = view.findViewById(R.id.sp_avatar_view_type);
    sp_avatar_view_type.setAdapter(avatarAdapter);
    sp_avatar_view_type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mAvatarViewType == position) return;
        switch (position) {
          case 1: { // 半身
            mAvatarViewType = MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HALF;
            break;
          }
          case 2: { // 头像
            mAvatarViewType = MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HEAD;
            break;
          }
          default: { // 全身
            mAvatarViewType = MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_WHOLE;
            break;
          }
        }
        if (MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS == mMetaServiceState) {
          MetaBoltManager.instance().setAvatarViewType(UserConfig.kMetaUid, mAvatarViewType);
          if (null != mRemoteUid && !mRemoteUid.isEmpty()) {
            MetaBoltManager.instance().setAvatarViewType(mRemoteUid, mAvatarViewType);
          }
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });

    sp_music_array = view.findViewById(R.id.sp_music_array);
    sp_music_array.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.i(TAG, "sp_music_array position:" + position + ", id:" + id);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    mViewModel = new ViewModelProvider(this).get(MainViewModel.class);
    // TODO: Use the ViewModel

    downResource(false);
    copyResource();
    initMetaService();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    /**leaveChannel and Destroy the RtcEngine instance*/
    {
      if (mAgoraEngine != null) {
        mAgoraEngine.leaveChannel();
        mMainLooperHandler.post(RtcEngine::destroy);
        mAgoraEngine = null;
      }
    }
    {
      if (mTRTCCloud != null) {
        mTRTCCloud.stopLocalAudio();
        mTRTCCloud.stopLocalPreview();
        mTRTCCloud.exitRoom();
        mTRTCCloud.setListener(null);
        mTRTCCloud = null;
        TRTCCloud.destroySharedInstance();
      }
    }

    mIsRtcInitialized = false;
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
      case R.id.btn_init_rtc: {
        if (mIsRtcInitialized) {
          deinitRTC();
          btn_init_rtc.setText(getString(R.string.init_rtc));
        } else {
          initRTC();
          btn_init_rtc.setText(getString(R.string.deinit_rtc));
        }
        break;
      }
      case R.id.btn_join: {
        if (isUserJoined) {
          isUserJoined = false;
          mAudioMixingState = io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED;
          mIsRemoteVideoViewNeedShow = false;
          mIsRemoteMetaViewNeedShow = false;
          mMetaServiceState = MetaBoltTypes.MTBServiceState.MTB_STATE_NOT_INIT;
          mAgoraEngine.stopAudioMixing();
          mAgoraEngine.leaveChannel();
          mAgoraEngine.stopPreview();
          btn_join.setText(getString(R.string.join_channel));

          MetaBoltManager.instance().destroyAvatarRole(UserConfig.kMetaUid);
          MetaBoltManager.instance().destroyAvatarView(0);
          if (null != mRemoteUid) {
            MetaBoltManager.instance().destroyAvatarRole(mRemoteUid);
            MetaBoltManager.instance().destroyAvatarView(1);
			mRemoteUid = null;
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
          mAgoraEngine.pauseAudioMixing();
          MetaBoltManager.instance().stopMusicDance();
        } else if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_COMPLETED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PAUSED == mAudioMixingState) {
          String musicPath = getMusicPath();
          if (musicPath.isEmpty() || !FileUtils.isFileExists(musicPath)) {
            showInnerToast("Resource music file " + musicPath + " not download, please wait...");
            return;
          }
          btn_music_dance.setText(R.string.stop_music_dance);
          if (0 == mAgoraEngine.getAudioMixingCurrentPosition()) {
            int ret = mAgoraEngine.startAudioMixing(musicPath, false, false, 1, 0);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "startAudioMixing " + musicPath + " failed " + mAgoraEngine.getErrorDescription(ret));
            }
            ret = mAgoraEngine.getAudioFileInfo(musicPath);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "getAudioFileInfo " + musicPath + " failed " + mAgoraEngine.getErrorDescription(ret));
            }
          } else {
            mAgoraEngine.resumeAudioMixing();
          }
          String danceMusicPath = getNewDanceDownPath();
          if (danceMusicPath.isEmpty() || !FileUtils.isFileExists(danceMusicPath)) {
            showInnerToast("Resource dance music file not download, please wait...");
            return;
          }
          MetaBoltManager.instance().startMusicDance(danceMusicPath);
        }
        break;
      }
      case R.id.btn_music_beat: {
        if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING == mAudioMixingState) {
          btn_music_beat.setText(R.string.start_music_beat);
          mAgoraEngine.pauseAudioMixing();
          MetaBoltManager.instance().stopMusicBeat();
        } else if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_COMPLETED == mAudioMixingState
            || io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PAUSED == mAudioMixingState) {
          if (0 == mAgoraEngine.getAudioMixingCurrentPosition()) {
            String musicPath = getMusicPath();
            if (musicPath.isEmpty() || !FileUtils.isFileExists(musicPath)) {
              showInnerToast("Resource music file not download, please wait...");
              return;
            }
            int ret = mAgoraEngine.startAudioMixing(musicPath, false, false, 1, 0);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "startAudioMixing " + musicPath + " failed " + mAgoraEngine.getErrorDescription(ret));
            }
            ret = mAgoraEngine.getAudioFileInfo(musicPath);
            if (Constants.ERR_OK != ret) {
              Log.e(TAG, "getAudioFileInfo " + musicPath + " failed " + mAgoraEngine.getErrorDescription(ret));
            }
          } else {
            mAgoraEngine.resumeAudioMixing();
          }
          btn_music_beat.setText(R.string.stop_music_beat);

          String beatPath = getNewBeatDownPath();
          if (beatPath.isEmpty() || !FileUtils.isFileExists(beatPath)) {
            showInnerToast("Resource beat file not download, please wait...");
            return;
          }
          MetaBoltManager.instance().startMusicBeat(beatPath);
        }
        break;
      }
      default:
        break;
    }
  }

  private void requestToken() {
//    CommonUtil.hideInputBoard(getActivity(), et_uid);
//    CommonUtil.hideInputBoard(getActivity(), et_channel);
    // call when join button hit
    String uid = et_uid.getText().toString();
    String channelId = et_channel.getText().toString();
    if (uid.isEmpty() || channelId.isEmpty()) {
      showInnerToast("uid或者channelId不能为空!");
      return;
    }
    UserConfig.kMetaUid = uid;
    UserConfig.kChannelId = channelId;
    if (METABOLT_INIT_TYPE_AGORA == mMetaboltInitType) {
      TokenUtils.instance().requestExternalToken(getActivity(),
          UserConfig.kAgoraAppId, UserConfig.kMetaUid, UserConfig.kChannelId, UserConfig.kAgoraCert, mMetaboltInitType, this);
    } else if (METABOLT_INIT_TYPE_TRTC == mMetaboltInitType) {
      TokenUtils.instance().requestExternalToken(getActivity(),
          UserConfig.kTRTCAppId, UserConfig.kMetaUid, UserConfig.kChannelId, UserConfig.kTRTCCert, mMetaboltInitType, this);
    }
    TokenUtils.instance().requestTokenV2(getActivity(),
        UserConfig.kMetaAppId, UserConfig.kMetaUid, UserConfig.kChannelId, UserConfig.kTokenInvalidTime, this);
    PermissionUtils.checkPermissionAllGranted(getActivity());
  }

  @Override
  public void onRequestTokenResult(int code, int type, String token, String extra) {
    switch (type) {
      case METABOLT_INIT_TYPE_AGORA: {
        if (0 == code && token != null) {
          UserConfig.kAgoraToken = token;
          showInnerToast("Agora token request success");
        } else {
          showInnerToast("Agora token request failed, code:" + code + ", msg:" + extra);
        }
        break;
      }
      case METABOLT_INIT_TYPE_TRTC: {
        if (0 == code && token != null) {
          UserConfig.kTRTCToken = token;
          showInnerToast("TRTC token request success");
        } else {
          showInnerToast("TRTC token request failed, code:" + code + ", msg:" + extra);
        }
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT:
      default: {
        if (0 == code && token != null) {
          UserConfig.kMetaToken = token;
          showInnerToast("Metabolt token request success");
        } else {
          showInnerToast("Metabolt token request failed, code:" + code + ", msg:" + extra);
        }
        break;
      }
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
      Log.i(TAG, String.format("local user %s leaveChannel!", UserConfig.kMetaUid));
      showInnerToast(String.format("local user %s leaveChannel!", UserConfig.kMetaUid));
      isUserJoined = false;
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
          mLocalAudioStreamId = mAgoraEngine.createDataStream(dataStreamConfig);

          if (null != mMetaBoltDataHandler) {
            mMetaBoltDataHandler.onJoinRoomSuccess(UserConfig.kChannelId, UserConfig.kMetaUid, elapsed);
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
          mAgoraEngine.setupRemoteVideo(new VideoCanvas(null, Constants.RENDER_MODE_HIDDEN, uid));
          MetaBoltManager.instance().destroyAvatarRole(mRemoteUid);
          MetaBoltManager.instance().destroyAvatarView(1);
          mRemoteUid = null;
        }
      });
    }

    @Override
    public void onStreamMessage(int uid, int streamId, byte[] data) {
      //Log.i(TAG, "onStreamMessage uid:" + uid + ", streamId:" + streamId + ", data:" + data);
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
      int audioState = ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_STOPPED;
      switch (state) {
        case io.agora.rtc.Constants.LOCAL_AUDIO_STREAM_STATE_STOPPED: {
          audioState = ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_STOPPED;
          break;
        }
        case io.agora.rtc.Constants.LOCAL_AUDIO_STREAM_STATE_FAILED: {
          audioState = ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_FAILED;
          break;
        }
        case io.agora.rtc.Constants.LOCAL_AUDIO_STREAM_STATE_CAPTURING: {
          audioState = ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_CAPTURING;
          break;
        }
        case io.agora.rtc.Constants.LOCAL_AUDIO_STREAM_STATE_ENCODING: {
          audioState = ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_ENCODING;
          break;
        }
        default:
          break;
      }
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.onLocalAudioStatusChanged(audioState, error);
      }
    }

    @Override
    public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
      if (null != mMetaBoltDataHandler) {
        if (io.agora.rtc.Constants.REMOTE_AUDIO_STATE_STOPPED == state) {
          mMetaBoltDataHandler.onRemoteAudioStopped(String.valueOf(uid), true);
        } else if (io.agora.rtc.Constants.REMOTE_AUDIO_STATE_STARTING == state) {
          mMetaBoltDataHandler.onRemoteAudioStopped(String.valueOf(uid), false);
        }
      }
    }

    @Override
    public void onAudioPublishStateChanged(String channel, int oldState, int newState, int elapseSinceLastState) {
      if (null != mMetaBoltDataHandler) {
        //mMetaBoltDataHandler.onLocalAudioPublishStatus(newState);
        switch (newState) {
          case io.agora.rtc.Constants.PUB_STATE_IDLE:
          case io.agora.rtc.Constants.PUB_STATE_NO_PUBLISHED: {
            mMetaBoltDataHandler.onLocalAudioPublishStatus(ThunderRtcConstant.ThunderLocalAudioPublishStatus.THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_STOP);
            //MetaBoltManager.instance().enableAudioPlayStatus(false);
            break;
          }
          case io.agora.rtc.Constants.PUB_STATE_PUBLISHING:
          case io.agora.rtc.Constants.PUB_STATE_PUBLISHED: {
            mMetaBoltDataHandler.onLocalAudioPublishStatus(ThunderRtcConstant.ThunderLocalAudioPublishStatus.THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_START);
            //MetaBoltManager.instance().enableAudioPlayStatus(true);
            break;
          }
          default: {
            break;
          }
        }
      }
    }

    @Override
    public void onAudioMixingStateChanged(int state, int reason) {
      Log.d(TAG, "onAudioMixingStateChanged state:" + state + ", reason:" + RtcEngine.getErrorDescription(reason));
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

    int localUid = Integer.valueOf(UserConfig.kMetaUid);
    // Create render view by RtcEngine
    //SurfaceView surfaceView = new SurfaceView(context);
    SurfaceView surfaceView = RtcEngine.CreateRendererView(context);
    // Add to the local container
    fl_local.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // Setup local video to render your local camera preview
    mAgoraEngine.setupLocalVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, localUid));

    /** Sets the channel profile of the Agora RtcEngine.
     CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
     Use this profile in one-on-one calls or group calls, where all users can talk freely.
     CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
     channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
     an audience can only receive streams.*/
    mAgoraEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
    /**In the demo, the default is to enter as the anchor.*/
    mAgoraEngine.setClientRole(IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_BROADCASTER);
    // Enable video module
    mAgoraEngine.enableVideo();
    mAgoraEngine.startPreview();
    //engine.switchCamera();
    // Setup video encoding configs
    mAgoraEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
        VideoEncoderConfiguration.VD_640x360,
        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
        VideoEncoderConfiguration.STANDARD_BITRATE,
        VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
    ));
    /**Set up to play remote sound with receiver*/
    mAgoraEngine.setDefaultAudioRoutetoSpeakerphone(true);
    mAgoraEngine.setEnableSpeakerphone(false);

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
    mAgoraEngine.setRecordingAudioFrameParameters(16000, 2, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_WRITE, 1024);

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
    mAgoraEngine.setPlaybackAudioFrameParameters(16000, 2, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1024);

    /**
     * Sets the mixed audio format for the onMixedAudioFrame callback.
     * sampleRate	Sets the sample rate (samplesPerSec) returned in the onMixedAudioFrame callback, which can be set as 8000, 16000, 32000, 44100, or 48000 Hz.
     * samplesPerCall	Sets the number of samples (samples) returned in the onMixedAudioFrame callback. samplesPerCall is usually set as 1024 for RTMP streaming.
     */
    mAgoraEngine.setMixedAudioFrameParameters(32000, 1024);

    /** Registers the audio observer object.
     *
     * @param observer Audio observer object to be registered. See {@link IAudioFrameObserver IAudioFrameObserver}. Set the value as @p null to cancel registering, if necessary.
     * @return
     * - 0: Success.
     * - < 0: Failure.
     */
    mAgoraEngine.registerAudioFrameObserver(audioFrameObserver);

    mAgoraEngine.registerVideoFrameObserver(iVideoFrameObserver);
    // 注册视频sei回调
    mAgoraEngine.registerMediaMetadataObserver(metadataObserver, IMetadataObserver.VIDEO_METADATA);

    /** Allows a user to join a channel.
     if you do not specify the uid, we will generate the uid for you*/

    ChannelMediaOptions option = new ChannelMediaOptions();
    option.autoSubscribeAudio = true;
    option.autoSubscribeVideo = true;
    int res = mAgoraEngine.joinChannel(UserConfig.kAgoraToken, channelId, "Extra Optional Data", localUid, option);
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
  }

  int count = 0;
  private final IVideoFrameObserver iVideoFrameObserver = new IVideoFrameObserver() {
    @Override
    public boolean onCaptureVideoFrame(VideoFrame videoFrame) {
      //mMetaBoltDataHandler.
      int width = videoFrame.width;
      int height = videoFrame.height;

      int yLength = videoFrame.yBuffer.remaining();
      int vLength = videoFrame.vBuffer.remaining();
      int uLength = videoFrame.uBuffer.remaining();
      byte[] yBuf = new byte[yLength];
      byte[] vBuf = new byte[vLength];
      byte[] uBuf = new byte[uLength];
      videoFrame.yBuffer.get(yBuf);
      videoFrame.vBuffer.get(vBuf);
      videoFrame.uBuffer.get(uBuf);
      byte[] nv21 = CommonUtil.doI420ToNV21(yBuf, uBuf, vBuf, width, height);
      mMetaBoltDataHandler.handleCaptureVideoFrame(width, height, nv21,
          MetaBoltTypes.MTBPixelFormat.MTB_PIXEL_FORMAT_NV21, true, 360 - videoFrame.rotation);
      return true;
    }

    @Override
    public boolean onRenderVideoFrame(int uid, VideoFrame videoFrame) {
      return true;
    }

    @Override
    public int getVideoFormatPreference() {
      return FRAME_TYPE_YUV420;
    }

    @Override
    public int getObservedFramePosition() {
      return POSITION_POST_CAPTURER;
    }
  };

//  public void saveBitmap2Gallery(Bitmap bm){
//    long currentTime = System.currentTimeMillis();
//
//    // name the file
//    String imageFileName = "IMG_AGORA_"+ currentTime + ".jpg";
//    String imageFilePath;
//
//    // write to file
//
//    OutputStream outputStream;
//    ContentResolver resolver = requireContext().getContentResolver();
//    ContentValues newScreenshot = new ContentValues();
//    Uri insert;
//    newScreenshot.put(MediaStore.Images.ImageColumns.DATE_ADDED,currentTime);
//    newScreenshot.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, imageFileName);
//    newScreenshot.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/jpg");
//    newScreenshot.put(MediaStore.Images.ImageColumns.WIDTH, bm.getWidth());
//    newScreenshot.put(MediaStore.Images.ImageColumns.HEIGHT, bm.getHeight());
//    try {
//      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//        newScreenshot.put(MediaStore.Images.ImageColumns.RELATIVE_PATH,imageFilePath);
//      }else{
//        // make sure the path is existed
//        File imageFileDir = new File(imageFilePath);
//        if(!imageFileDir.exists()){
//          boolean mkdir = imageFileDir.mkdirs();
//          if(!mkdir) {
//            return;
//          }
//        }
//        newScreenshot.put(MediaStore.Images.ImageColumns.DATA, imageFilePath+imageFileName);
//        newScreenshot.put(MediaStore.Images.ImageColumns.TITLE, imageFileName);
//      }
//
//      // insert a new image
//      insert = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newScreenshot);
//      // write data
//      outputStream = resolver.openOutputStream(insert);
//
//      bm.compress(Bitmap.CompressFormat.PNG, 80, outputStream);
//      outputStream.flush();
//      outputStream.close();
//
//      newScreenshot.clear();
//      newScreenshot.put(MediaStore.Images.ImageColumns.SIZE, new File(imageFilePath).length());
//      resolver.update(insert, newScreenshot, null, null);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//  }

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
      //Log.i(TAG, "onMixedFrame " + audioFrame.toString());
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
    return mAgoraEngine.setupRemoteVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, Integer.valueOf(mRemoteUid)));
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
        showInnerToast("Metabolt init state:" + state);
        Log.d(TAG, "Metabolt init state:" + state);
        if (state == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS) {
          mMainLooperHandler.post(() -> {
            Context context = getContext();
            if (context == null) {
              return;
            }
            MetaBoltManager.instance().createAvatarView(context, 0);
            String modelPath = getRoleModelPath(kRoleModelFemale);
            MetaBoltManager.instance().createAvatarRole(modelPath, UserConfig.kMetaUid);
            Log.i(TAG, "local view path:" + modelPath);
            MetaBoltManager.instance().setRoleIndex(0, UserConfig.kMetaUid);
            MetaBoltManager.instance().setAvatarViewType(UserConfig.kMetaUid, mAvatarViewType);

            MetaBoltManager.instance().setAnimation(UserConfig.kMetaUid, getAnimationDownPath());
            if (SYNC_TYPE_AUDIO == mSyncType) {
              MetaBoltManager.instance().startFaceEmotionByAudio();
            } else {
              MetaBoltManager.instance().startFaceEmotionByCamera();
            }
            if (mIsRemoteMetaViewNeedShow) {
              mIsRemoteMetaViewNeedShow = false;
              initRemoteMetaView(context);
            }
            if (mIsRemoteVideoViewNeedShow) {
              mIsRemoteVideoViewNeedShow = false;
              addRemoteVideoView(context);
            }
          });
        }
      }
    });

    MTBServiceConfig config = new MTBServiceConfig();
    config.context = getContext();
    config.appId = UserConfig.kMetaAppId;
    config.accessToken = UserConfig.kMetaToken;
    config.AIModelPath = UserConfig.kMetaAIModelPath;
    MetaBoltManager.instance().init(config, true);
  }

  private void initRemoteMetaView(Context context) {
    MetaBoltManager.instance().createAvatarView(context, 1);
    String modelPath = getRoleModelPath(kRoleModelMale);
    MetaBoltManager.instance().createAvatarRole(modelPath, mRemoteUid);
    MetaBoltManager.instance().setRoleIndex(1, mRemoteUid);
    MetaBoltManager.instance().setAvatarViewType(mRemoteUid, mAvatarViewType);
    MetaBoltManager.instance().setAnimation(mRemoteUid, getRemoteAnimationDownPath());
  }

  private void initRTC() {
    Context context = getContext();
    if (mIsRtcInitialized) {
      Log.i(TAG, "rtc already initialized");
      return;
    }
    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_AGORA: {
        try {
          /**Creates an RtcEngine instance.
           * @param context The context of Android Activity
           * @param appId The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id">
           *              How to get the App ID</a>
           * @param handler IRtcEngineEventHandler is an abstract class providing default implementation.
           *                The SDK uses this class to report to the app on SDK runtime events.*/
          mAgoraEngine = RtcEngine.create(context.getApplicationContext(), UserConfig.kAgoraAppId, iRtcEngineEventHandler);
          //mAgoraEngine.setLogLevel(Constants.LogLevel.LOG_LEVEL_WARN);
        } catch (Exception e) {
          e.printStackTrace();
          getActivity().onBackPressed();
        }
        break;
      }
      case METABOLT_INIT_TYPE_TRTC: {
        mTRTCCloud = TRTCCloud.sharedInstance(context.getApplicationContext());
        //mTRTCCloud.setAudioFrameListener(trtcAudioFrameListener);
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        Log.e(TAG, "initRTC METABOLT_INIT_TYPE_THUNDERBOLT need to be supported!");
        break;
      }
      default:
        break;
    }
    requestToken();
    mIsRtcInitialized = true;
  }

  void deinitRTC() {
    mIsRtcInitialized = false;
    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_AGORA: {
        if (mAgoraEngine != null) {
          mAgoraEngine.leaveChannel();
          mMainLooperHandler.post(RtcEngine::destroy);
          mAgoraEngine = null;
        }
        break;
      }
      case METABOLT_INIT_TYPE_TRTC: {
        if (mTRTCCloud != null) {
          mTRTCCloud.stopLocalAudio();
          mTRTCCloud.stopLocalPreview();
          mTRTCCloud.exitRoom();
          mTRTCCloud.setListener(null);
          mTRTCCloud = null;
          TRTCCloud.destroySharedInstance();
        }
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        break;
      }
      default:
        break;
    }
  }

  private void initMetaService() {
    MetaBoltManager.createMetaBoltManager(getConfigJsonFile());
    MetaBoltManager.instance().registerSEICallback(this);
    MetaBoltManager.instance().setRootView(mRootView);

    mMetaBoltDataHandler = MetaBoltManager.instance();
  }

  private void checkZipAllFiles(String checkUrl) {
    if (mWaitDownloadUrlList.contains(checkUrl)) {
      ++mWaitCount;
    }

    if (mWaitDownloadUrlList.size() == mWaitCount) {
      new Thread(() -> {
        Log.i(TAG, "start unzip all files...");
        List<String> zipSuccessFileList = new ArrayList<>();
        for (String url : mWaitDownloadUrlList) {
          try {
            String zipFileName = url.substring(url.lastIndexOf("/") + 1); // video?filename=xxx.zip
            String downFilePath = DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator + zipFileName;
            boolean zipRet = FileUtils.unZipUtil(downFilePath, DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator, true);
            if (!zipRet) {
              FileUtils.deleteDir(downFilePath);
              showInnerToast("解压 " + zipFileName + " 失败, 需要去点重新下载!!!");
            } else {
              String zipResFileName = zipFileName.substring(zipFileName.lastIndexOf("=") + 1, zipFileName.lastIndexOf("."));
              zipSuccessFileList.add(zipResFileName);
            }
          } catch (Exception e) {
            Log.e(TAG, "onDownloadSuccess, failed: " + e);
          }
        };
        mWaitDownloadUrlList.clear();
      }).start();
      mWaitCount = 0;
    }
  }

  /**
   * 资源相关 by Hsu
   */
  public void onDownloadSuccess(String url, String path, boolean isExist) {
    Log.i(TAG, "onDownloadSuccess url: " + url + ", path: " + path + ", isExist: " + isExist);
    checkZipAllFiles(url);
  }

  @Override
  public void onDownloading(int progress) {
  }

  @Override
  public void onDownloadFailed(String url) {
    Log.e(TAG, "onDownloadFailed url: " + url);
    checkZipAllFiles(url);
    showInnerToast("下载资源 " + url.substring(url.lastIndexOf("/") + 1) + " 出错, 请检查网络");
  }

  private void filtrateFiles(List<String> fileList) {
    List<String> removeList = new ArrayList<>();
    for (String file : fileList) {
      if (file.contentEquals("Store") ||
          file.contentEquals("json") ||
          file.contentEquals("manifest") ||
          file.contentEquals("clothes") ||
          file.contentEquals("listeningtomusic"))
      {
        removeList.add(file);
      }
    }

    for (String string : removeList) {
      fileList.remove(string);
    }
  }

  public void updateDanceList(List<String> musicNameList) {
    if (musicNameList.isEmpty() || !mMusicFileList.isEmpty()) {
      return;
    }

    filtrateFiles(musicNameList);
    mMusicFileList.addAll(musicNameList);
  }

  public void updateBeatList(List<String> musicNameList) {
    if (musicNameList.isEmpty() || !mMusicFileList.isEmpty()) {
      return;
    }

    filtrateFiles(musicNameList);
    mMusicFileList.addAll(musicNameList);
  }

  public void updateMusicList(List<String> musicNameList) {
    if (musicNameList.isEmpty() || !mMusicFileList.isEmpty()) {
      return;
    }

    filtrateFiles(musicNameList);
    mMusicFileList.addAll(musicNameList);
  }

  public void updateBeatAnimationList(List<String> animationList) {
    if (animationList.isEmpty() || !mBeatAnimationNameList.isEmpty()) {
      return;
    }

    filtrateFiles(animationList);
    mBeatAnimationNameList.addAll(animationList);
  }

  public void updateRoleModelFile(List<String> modelList) {
    if (modelList.isEmpty() || !mRoleModelNameList.isEmpty()) {
      return;
    }

    filtrateFiles(modelList);
    mRoleModelNameList.addAll(modelList);
  }

  private String mRoleModelName = "";
  public void updateRoleModelList(List<String> roleModelList) {
    Log.i(TAG, "performanceTest update role list num: " + roleModelList.size() + ", roleName: " + mRoleModelName);
    filtrateFiles(roleModelList);
    mRoleModelName = roleModelList.get(0);
  }

  private String getConfigJsonFile() {
    // targetDir="/data/user/0/com.joyy.metabolt.example/files"
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    return targetDir + kConfigJsonFileName;
  }

  private String getNewBeatDownPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/Ku_puja_puja/Ku_puja_puja.bin
    //String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
//    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
//    if (mMusicFileList.size() > 1) {
//      String music = mMusicFileList.get(0);
//      if (music.contains("k-pop")) {
//        music = mMusicFileList.get(1);
//      }
//      return targetDir + "/download/new_dance/" + music + "/" + music + ".bin";
//    }
//    return "";
    return "/storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/new_dance/【中国风】天涯/【中国风】天涯.bin";
  }

  private String getNewDanceDownPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/Ku_puja_puja/Ku_puja_puja.dat
    // /storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/new_dance/Pamer_Bojo/Pamer_Bojo.dat
//    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
//    if (mMusicFileList.size() > 1) {
//      String music = mMusicFileList.get(0);
//      if (music.contains("k-pop")) {
//        music = mMusicFileList.get(1);
//      }
//      return targetDir + "/download/new_dance/" + music + "/" + music + ".dat";
//    }
//    return "";
    return "/storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/new_dance/【中国风】天涯/【中国风】天涯.dat";
  }

  private String getAnimationDownPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/beat/singing/singing
    // /storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/beat/singing/singing
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/beat/singing/singing";
    //return "/storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/beat/singing/singing";
  }

  private String getRemoteAnimationDownPath() {
    // /storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/beat/jump/jump
    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
    return targetDir + "/download/beat/singing/singing";
    //return "/storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/beat/jump/jump";
  }

  private String getRoleModelPath(String roleName) {
    // /storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/model_pkg_android/female_role.json
    String downPath = DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator;
    String targetPath = "model_pkg_android" + File.separator + roleName + ".json";
    return downPath + targetPath;
  }

  private String getMusicPath() {
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/【舞曲】爱的主打歌/【舞曲】爱的主打歌.mp3
    // /data/user/0/com.joyy.metabolt.example/files/download/new_dance/qinghuaci/qinghuaci.mp3
    // /storage/emulated/0/Android/data/yy.com.thunderbolt/files/download/new_dance/qinghuaci/qinghuaci.mp3
//    String targetDir = Objects.requireNonNull(getContext()).getExternalFilesDir(null).getAbsolutePath();
//    if (mMusicFileList.size() > 1) {
//      String music = mMusicFileList.get(0);
//      if (music.contains("k-pop")) {
//        music = mMusicFileList.get(1);
//      }
//      return targetDir + "/download/new_dance/" + music + "/" + music + ".mp3";
//    }
//    return "";
    return "/storage/emulated/0/Android/data/com.joyy.metabolt.example/files/download/new_dance/【中国风】天涯/【中国风】天涯.mp3";
  }

  private ExecutorService threadPoolExecutorService = null;
  private int mWaitCount = 0;
  private final List<String> mWaitDownloadUrlList = new CopyOnWriteArrayList<>();
  private void downResource(boolean isForce) {
    List<String> downFileList = new ArrayList<>();
    downFileList.add(kDanceMusicDir);
    downFileList.add(kBeatAnimationDir);
    downFileList.add(kRoleModelPkgDir);

    List<String> existFileList = new ArrayList<>();
    for (String fileName : downFileList) {
      List<String> getFiles = FileUtils.getFilesAllName(DownloadUtil.get().getDownloadPath(Objects.requireNonNull(getContext())) + File.separator + fileName);
      if (!isForce && getFiles != null && !getFiles.isEmpty()) {
        existFileList.add(fileName);
      } else {
        mWaitDownloadUrlList.add(kServiceHttpsUrl + fileName + kZipType);
      }
    }

    for (String url : mWaitDownloadUrlList) {
      DownloadUtil.get().download(getContext(), url, this);
    }
  }

  private void copyResource() {
    String path = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    String targetDir = path + File.separator;
    copyFilesFromAssets(kLipSyncFileName, targetDir + kLipSyncFileName);
    //共用kLipSyncFileName目录和config.json
    copyFilesFromAssets(kFaceSyncFileName, targetDir + kLipSyncFileName);
    // 拷贝其他资源
//    copyFilesFromAssets(kDanceMusicDir, targetDir + kDanceMusicDir);
//    copyFilesFromAssets(kBeatAnimationDir, targetDir + kBeatAnimationDir);
//    copyFilesFromAssets(kRoleModelPkgDir, targetDir + kRoleModelPkgDir);
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
  public void onMetaBoltState(int state) {

  }

  @Override
  public void onStateMsgCallback(String msg) {
    Log.i(TAG, "[onStateMsgCallback] " + msg);
  }

  private boolean needPrint = true;
  @Override
  public int onAudioSEIData(ByteBuffer byteBuffer) {
    byteBuffer.rewind();
    byte[] buffer = new byte[byteBuffer.limit()];
    byteBuffer.get(buffer);
    int type = buffer[MetaBoltManager.kSEIStartLen];
    int ret = 0;
    if (MetaBoltManager.kDanceType != type) {
      ret = mAgoraEngine.sendStreamMessage(mLocalAudioStreamId, buffer);
      if (ret < Constants.ERR_OK) {
        Log.e(TAG, "sendStreamMessage error:" + ret + ", desc:" + RtcEngine.getErrorDescription(ret));
      }
    } else {
      if (needPrint) {
        needPrint = false;
        Log.i(TAG, "kDanceType data size bigger than agora sendStreamMessage permitted, need not send to remote");
      }
    }
    buffer = null;
    return ret;
  }

  @Override
  public long getMusicPlayCurrentProgress() {
    //if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING != mAudioMixingState) return 0;
    int pos = mAgoraEngine.getAudioMixingCurrentPosition();
    Log.i(TAG, "AudioMixing music progress position:" + pos);
    return pos;
  }

  @Override
  public long getMusicPlayTotalProgress() {
    //if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING != mAudioMixingState) return 0;
    mAudioDuration = mAgoraEngine.getAudioMixingDuration();
    Log.i(TAG, "AudioMixing music progress total:" + mAudioDuration);
    return mAudioDuration;
  }
}