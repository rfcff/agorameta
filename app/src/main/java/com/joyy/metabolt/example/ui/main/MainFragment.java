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
import com.tencent.liteav.audio.TXAudioEffectManager;
import com.tencent.liteav.device.TXDeviceManager;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.joyy.metabolt.example.R;
import com.joyy.metabolt.example.utils.CommonUtil;
import com.joyy.metabolt.example.utils.FileUtils;
import com.joyy.metabolt.example.utils.IMetaBoltDataHandler;
import com.joyy.metabolt.example.utils.IMetaFragmentHandler;
import com.joyy.metabolt.example.utils.MetaBoltManager;
import com.joyy.metabolt.example.utils.MetaBoltMgrCallback;
import com.joyy.metabolt.example.utils.PermissionUtils;
import com.joyy.metabolt.example.utils.TokenUtils;
import com.joyy.metabolt.example.utils.UserConfig;
import com.tencent.trtc.TRTCCloudDef;
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
    IMetaFragmentHandler {
  private static final String TAG = "MainFragment";
  private MainViewModel mViewModel;
  private View mRootView = null;
  private FrameLayout fl_local_meta, fl_local, fl_remote_meta, fl_remote;
  private TextView tv_metabolt_show;
  private Button btn_join, btn_init_rtc, btn_music_dance, btn_music_beat;
  private RadioGroup bg_rtc_type;
  private Spinner sp_sync_type, sp_avatar_view_type, sp_music_res, sp_beat_res;
  private EditText et_uid;
  private EditText et_channel;
  private Handler mMainLooperHandler = new Handler(Looper.getMainLooper());


  private static final Integer SAMPLE_RATE = 44100;
  private static final Integer SAMPLE_NUM_OF_CHANNEL = 2;
  private static final Integer BIT_PER_SAMPLE = 16;
  private static final Integer SAMPLES_PER_CALL = 4410;


  private boolean mIsUserJoined = false;
  private String mRemoteUid = null;

  // metabolt
  private final int METABOLT_SYNC_TYPE_AUDIO = 0; // libsync
  private final int METABOLT_SYNC_TYPE_VIDEO = 1; // facesync

  private final int METABOLT_INIT_TYPE_AGORA = 0; // metabolt借用agora通道
  private final int METABOLT_INIT_TYPE_TRTC = 1; // metabolt借用trtc通道
  private final int METABOLT_INIT_TYPE_THUNDERBOLT = 3; // metabolt借用thunderbolt通道

  private final int MUSIC_PLAY_STATE_STOPPED = 0; // 伴奏播放停止状态
  private final int MUSIC_PLAY_STATE_STARTING = 1; // 伴奏播放开始状态

  private IMetaBoltDataHandler mMetaBoltDataHandler = null;
  private boolean mIsRtcInitialized = false;
  private int mMetaboltInitType = METABOLT_INIT_TYPE_AGORA;
  private int mMetaSyncType = METABOLT_SYNC_TYPE_AUDIO;
  private int mAvatarViewType = MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_WHOLE; // 默认显示全身
  private int mMetaServiceState = MetaBoltTypes.MTBServiceState.MTB_STATE_NOT_INIT;
  private int mMusicPlayingState = MUSIC_PLAY_STATE_STOPPED;
  private boolean mIsRemoteMetaViewNeedShow = false;

  private RtcEngine mAgoraEngine; // agora声网
  private int mAgoraAudioStreamId = 0;

  private TRTCCloud mTRTCCloud; // trtc腾讯云rtc
  private TXDeviceManager mTXDeviceManager;
  private TXAudioEffectManager mTXAudioEffectManager;
  TXCloudVideoView mTrtcLocalView, mTrtcRemoteView;
  private final int TRTC_CMDID = 1;
  private final int TRTC_MUSIC_ID = 1024;

  /**
   * 资源相关
   */
  private int mMusicIdx = 0; // 音乐文件索引
  private int mBeatIdx = 0; // 节拍文件索引
  private final String kLipSyncFileName = "lipsync";
  private final String kFaceSyncFileName = "facesync";
  private final String kConfigJsonFileName = "/lipsync/config.json";

  private final String kRoleModelMale = "male_role";
  private final String kRoleModelFemale = "female_role";

  private final static String kDanceMusicDir = "new_dance";
  private final static String kBeatAnimationDir = "beat";
  private final static String kRoleModelPkgDir = "model_pkg_android";

  private List<String> mMusicFileList = new ArrayList<>();
  private List<String> mBeatAnimationNameList = new ArrayList<>();

  public static MainFragment newInstance() {
    return new MainFragment();
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    mRootView = inflater.inflate(R.layout.main_fragment, container, false);
    PermissionUtils.checkPermissionAllGranted(getActivity());
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
    btn_join.setEnabled(false);
    btn_music_dance.setEnabled(false);
    btn_music_beat.setEnabled(false);
    et_uid = view.findViewById(R.id.et_uid);
    et_uid.setText(String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999)));
    et_channel = view.findViewById(R.id.et_channel);
    et_channel.setText(UserConfig.kChannelId);
    fl_local_meta = view.findViewById(R.id.fl_local_meta);
    fl_local = view.findViewById(R.id.fl_local);
    mTrtcLocalView = view.findViewById(R.id.trtc_local);
    mTrtcRemoteView = view.findViewById(R.id.trtc_remote);
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
        if (position == mMetaSyncType) return;
        if (position == 0) {
          mMetaSyncType = METABOLT_SYNC_TYPE_AUDIO;
          if (MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS == mMetaServiceState) {
            MetaBoltManager.instance().startFaceEmotionByAudio();
          }
        } else {
          mMetaSyncType = METABOLT_SYNC_TYPE_VIDEO;
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

    sp_music_res = view.findViewById(R.id.sp_music_array);
    sp_music_res.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (null == mMusicFileList || 0 == mMusicFileList.size() || position == mMusicIdx) return;
        Log.i(TAG, "sp_music_array position:" + position + ", id:" + id);
        mMusicIdx = position;
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });

    sp_beat_res = view.findViewById(R.id.sp_beat_array);
    sp_beat_res.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (null == mBeatAnimationNameList || 0 == mBeatAnimationNameList.size() || position == mBeatIdx) return;
        Log.i(TAG, "sp_beat_res position:" + position + ", id:" + id);
        mBeatIdx = position;
        if (MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS == mMetaServiceState) {
          MetaBoltManager.instance().setAnimation(UserConfig.kMetaUid, getAnimationDownPath());
          if (null != mRemoteUid && !mRemoteUid.isEmpty()) {
            MetaBoltManager.instance().setAnimation(mRemoteUid, getRemoteAnimationDownPath());
          }
        }
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

    copyResource();
    initMetaService();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    /**leaveChannel and Destroy the RtcEngine instance*/
    deinitRTC();
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

  private void playMusic(boolean bPlay) {
    String musicPath = getMusicPath();
    if (musicPath.isEmpty() || !FileUtils.isFileExists(musicPath)) {
      showInnerToast("Resource music file " + musicPath + " not download, please wait...");
      return;
    }
    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_TRTC: {
        trtcPlayMusic(musicPath, bPlay);
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        // todo: 添加thunder播放音乐文件实现
        break;
      }
      case METABOLT_INIT_TYPE_AGORA:
      default: {
        agoraPlayMusic(musicPath, bPlay);
        break;
      }
    }
  }

  private void trtcPlayMusic(String musicPath, boolean bPlay) {
    if (bPlay) {
      TXAudioEffectManager.AudioMusicParam musicParam = new TXAudioEffectManager.AudioMusicParam(TRTC_MUSIC_ID, musicPath);
      musicParam.publish = true;
      mTXAudioEffectManager.startPlayMusic(musicParam);
    } else {
      mTXAudioEffectManager.stopPlayMusic(TRTC_MUSIC_ID);
      mMusicPlayingState = MUSIC_PLAY_STATE_STOPPED;
    }
  }

  private void agoraPlayMusic(String musicPath, boolean bPlay) {
    if (bPlay) {
      int ret = mAgoraEngine.startAudioMixing(musicPath, false, false, 1, 0);
      if (Constants.ERR_OK != ret) {
        Log.e(TAG, "startAudioMixing " + musicPath + " failed " + mAgoraEngine.getErrorDescription(ret));
      }
      ret = mAgoraEngine.getAudioFileInfo(musicPath);
      if (Constants.ERR_OK != ret) {
        Log.e(TAG, "getAudioFileInfo " + musicPath + " failed " + mAgoraEngine.getErrorDescription(ret));
      }
    } else {
      mAgoraEngine.stopAudioMixing();
    }
  }

  @Override
  public void onClick(View v) {
    switch(v.getId()) {
      case R.id.btn_init_rtc: {
        if (mIsRtcInitialized) {
          deinitRTC();
          btn_join.setEnabled(false);
          btn_music_dance.setEnabled(false);
          btn_music_beat.setEnabled(false);
          btn_init_rtc.setText(getString(R.string.init_rtc));
        } else {
          initRTC();
          btn_init_rtc.setText(getString(R.string.deinit_rtc));
        }
        break;
      }
      case R.id.btn_join: {
        if (mIsUserJoined) {
          mIsUserJoined = false;
          mMusicPlayingState = MUSIC_PLAY_STATE_STOPPED;
          btn_join.setText(getString(R.string.join_channel));
          leaveChannel(false);

          //mIsRemoteMetaViewNeedShow = false;
          mMetaServiceState = MetaBoltTypes.MTBServiceState.MTB_STATE_NOT_INIT;
          MetaBoltManager.instance().registerMgrCallback(null);
          MetaBoltManager.instance().destroyAvatarRole(UserConfig.kMetaUid);
          MetaBoltManager.instance().destroyAvatarView(0);
          if (null != mRemoteUid) {
            MetaBoltManager.instance().destroyAvatarRole(mRemoteUid);
            MetaBoltManager.instance().destroyAvatarView(1);
			      mRemoteUid = null;
          }
          MetaBoltManager.instance().deInit();
        } else {
          joinChannel();
          initMetaboltRole();
        }
        break;
      }
      case R.id.btn_music_dance: {
        if (MUSIC_PLAY_STATE_STARTING == mMusicPlayingState) {
          playMusic(false);
          btn_music_dance.setText(R.string.start_music_dance);
          MetaBoltManager.instance().stopMusicDance();
        } else {
          playMusic(true);
          btn_music_dance.setText(R.string.stop_music_dance);
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
        if (MUSIC_PLAY_STATE_STARTING == mMusicPlayingState) {
          playMusic(false);
          btn_music_beat.setText(R.string.start_music_beat);
          MetaBoltManager.instance().stopMusicBeat();
        } else {
          playMusic(true);
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
      mIsUserJoined = false;

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
      Log.i(TAG, String.format("Agora joinChannel success channel:%s uid:%d", channel, uid));
      showInnerToast(String.format("Agora joinChannel success channel:%s uid:%d", channel, uid));
      mIsUserJoined = true;
      mMainLooperHandler.post(new Runnable() {
        @Override
        public void run() {
          btn_join.setEnabled(true);
          btn_music_dance.setEnabled(true);
          btn_music_beat.setEnabled(true);
          btn_join.setText(getString(R.string.leave_channel));
          // 创建createDataStream,发送音频sei
          DataStreamConfig dataStreamConfig = new DataStreamConfig();
          dataStreamConfig.ordered = true;
          dataStreamConfig.syncWithAudio = true;
          mAgoraAudioStreamId = mAgoraEngine.createDataStream(dataStreamConfig);

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
      showInnerToast(String.format("remote user %d joined!", uid));
      /**Check if the context is correct*/
      Context context = getContext();
      if (context == null) {
        return;
      }
      mRemoteUid = String.valueOf(uid);
      mMainLooperHandler.post(() ->
      {
        /**Display remote video stream*/
        //SurfaceView surfaceView = new SurfaceView(context);
        SurfaceView surfaceView = RtcEngine.CreateRendererView(context);
        surfaceView.setZOrderMediaOverlay(true);
        if (fl_remote.getChildCount() > 0) {
          fl_remote.removeAllViews();
        }
        // Add to the remote container
        fl_remote.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Setup remote video to render
        mAgoraEngine.setupRemoteVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, Integer.valueOf(mRemoteUid)));

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
      Log.i(TAG, String.format("remote user %d offline! reason:%d", uid, reason));
      showInnerToast(String.format("remote user %d offline! reason:%d", uid, reason));
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
      switch (state) {
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING: {
          mMusicPlayingState = MUSIC_PLAY_STATE_STARTING;
          MetaBoltManager.instance().enableAudioPlayStatus(true);
          break;
        }
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_STOPPED:
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_FAILED: {
          mMusicPlayingState = MUSIC_PLAY_STATE_STOPPED;
          MetaBoltManager.instance().enableAudioPlayStatus(false);
          break;
        }
        case io.agora.rtc2.Constants.AUDIO_MIXING_STATE_COMPLETED: {
          mMusicPlayingState = MUSIC_PLAY_STATE_STOPPED;
          MetaBoltManager.instance().stopMusicDance();
          break;
        }
        default:
          break;
      }
    }
  };

  private void joinAgoraChannel(Context context) {
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
    int res = mAgoraEngine.joinChannel(UserConfig.kAgoraToken, UserConfig.kChannelId, "Extra Optional Data", localUid, option);
    if (res != 0) {
      // Usually happens with invalid parameters
      // Error code description can be found at:
      // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
      // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
      showInnerAlert(RtcEngine.getErrorDescription(Math.abs(res)));
      return;
    }
  }

  private void leaveAgoraChannel(boolean isDeInit) {
    if (mAgoraEngine != null) {
      mAgoraEngine.stopAudioMixing();
      mAgoraEngine.stopPreview();
      mAgoraEngine.leaveChannel();
      if (isDeInit) {
        mMainLooperHandler.post(RtcEngine::destroy);
        mAgoraEngine = null;
      }
    }
  }

  private void joinTrtcChannel(Context context) {
    // 事件监听
    mTRTCCloud.setListener(new TRTCCloudImplListener(MainFragment.this));

    // 伴奏音乐回调
    mTXAudioEffectManager.setMusicObserver(TRTC_MUSIC_ID, trtcAudioPlayListener);

    // 设置音频回调格式
    TRTCCloudDef.TRTCAudioFrameCallbackFormat audioFormat = new TRTCCloudDef.TRTCAudioFrameCallbackFormat();
    audioFormat.channel = SAMPLE_NUM_OF_CHANNEL;
    audioFormat.sampleRate = SAMPLE_RATE;
    audioFormat.samplesPerCall = SAMPLES_PER_CALL;
    mTRTCCloud.setCapturedRawAudioFrameCallbackFormat(audioFormat);
    mTRTCCloud.setMixedPlayAudioFrameCallbackFormat(audioFormat);
    mTRTCCloud.setAudioFrameListener(trtcAudioFrameListener);

    // 设置视频回调格式
    TRTCCloudDef.TRTCVideoEncParam videoParam = new TRTCCloudDef.TRTCVideoEncParam();
    videoParam.videoFps = 15;
    videoParam.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360; //640 * 360;
    videoParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
    mTRTCCloud.setVideoEncoderParam(videoParam);
    mTRTCCloud.setLocalVideoRenderListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420,
        TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY,
        trtcVideoRenderListener);
//    mTRTCCloud.setLocalVideoProcessListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420,
//        TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY, trtcVideoFrameListener);

    // 预览视频
    mTRTCCloud.startLocalPreview(true, mTrtcLocalView);
    mTRTCCloud.startLocalAudio(TRTCCloudDef.TRTC_AUDIO_QUALITY_SPEECH);

    // 进频道
    TRTCCloudDef.TRTCParams params = new TRTCCloudDef.TRTCParams();
    params.sdkAppId = Integer.parseInt(UserConfig.kTRTCAppId);
    params.userId = UserConfig.kMetaUid;
    params.roomId = Integer.parseInt(UserConfig.kChannelId);
    params.userSig = UserConfig.kTRTCToken;
    //params.userSig = CommonUtil.genTLSSignature(Integer.parseInt(UserConfig.kTRTCAppId),
    //    UserConfig.kMetaUid, 604800, null, UserConfig.kTRTCCert);

    Log.i(TAG, "joinTrtcChannel sdkAppId:" + params.sdkAppId + ", userSig:" + params.userSig);
    mTRTCCloud.enterRoom(params, TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL);
  }

  private void leaveTrtcChannel(boolean isDeInit) {
    if (mTRTCCloud != null) {
      mTRTCCloud.stopLocalAudio();
      mTRTCCloud.stopLocalPreview();
      mTRTCCloud.exitRoom();
      mTRTCCloud.setListener(null);
      mTRTCCloud.setAudioFrameListener(null);
      if (isDeInit) {
        mTRTCCloud = null;
        TRTCCloud.destroySharedInstance();
      }
    }
  }

  private void leaveChannel(boolean isDeInit) {
    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_TRTC: {
        leaveTrtcChannel(isDeInit);
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        // todo: 待添加THUNDERBOLT
        break;
      }
      case METABOLT_INIT_TYPE_AGORA:
      default: {
        leaveAgoraChannel(isDeInit);
        break;
      }
    }
  }

  private void joinChannel() {
    // Check if the context is valid
    Context context = getContext();
    if (context == null) {
      return;
    }

    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_TRTC: {
        joinTrtcChannel(context);
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        // todo: 待添加THUNDERBOLT
        break;
      }
      case METABOLT_INIT_TYPE_AGORA:
      default: {
        joinAgoraChannel(context);
        break;
      }
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
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handleCaptureVideoFrame(width, height, nv21,
            MetaBoltTypes.MTBPixelFormat.MTB_PIXEL_FORMAT_NV21, true, 360 - videoFrame.rotation);
      }
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

  private final IAudioFrameObserver audioFrameObserver = new IAudioFrameObserver() {
    @Override
    public boolean onRecordFrame(AudioFrame audioFrame) {
      Log.i(TAG, "onRecordAudioFrame " + audioFrame.toString());
      int length = audioFrame.samples.limit();
      byte[] buffer = new byte[length];
      audioFrame.samples.get(buffer);
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handlerCaptureAudioData(buffer, length, audioFrame.samplesPerSec, audioFrame.channels,true);
      }
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
            if (METABOLT_SYNC_TYPE_AUDIO == mMetaSyncType) {
              MetaBoltManager.instance().startFaceEmotionByAudio();
            } else {
              MetaBoltManager.instance().startFaceEmotionByCamera();
            }
            if (mIsRemoteMetaViewNeedShow) {
              mIsRemoteMetaViewNeedShow = false;
              initRemoteMetaView(context);
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
      case METABOLT_INIT_TYPE_TRTC: {
        fl_local.setVisibility(View.GONE);
        fl_remote.setVisibility(View.GONE);
        mTrtcLocalView.setVisibility(View.VISIBLE);
        mTrtcRemoteView.setVisibility(View.VISIBLE);

        mTRTCCloud = TRTCCloud.sharedInstance(context);
        mTXDeviceManager = mTRTCCloud.getDeviceManager();
        mTXAudioEffectManager = mTRTCCloud.getAudioEffectManager();
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        Log.e(TAG, "initRTC METABOLT_INIT_TYPE_THUNDERBOLT need to be supported!");
        break;
      }
      case METABOLT_INIT_TYPE_AGORA:
      default: {
        fl_local.setVisibility(View.VISIBLE);
        fl_remote.setVisibility(View.VISIBLE);
        mTrtcLocalView.setVisibility(View.GONE);
        mTrtcRemoteView.setVisibility(View.GONE);
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
    }
    requestToken();
    mIsRtcInitialized = true;
    btn_join.setEnabled(true);
    btn_music_dance.setEnabled(true);
    btn_music_beat.setEnabled(true);
  }

  void deinitRTC() {
    mIsRtcInitialized = false;
    leaveChannel(true);
  }

  private void initMetaService() {
    MetaBoltManager.createMetaBoltManager(getConfigJsonFile());
    MetaBoltManager.instance().registerSEICallback(this);
    MetaBoltManager.instance().setRootView(mRootView);

    mMetaBoltDataHandler = MetaBoltManager.instance();
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
      switch (mMetaboltInitType) {
        case METABOLT_INIT_TYPE_TRTC: {
          mTRTCCloud.sendCustomCmdMsg(TRTC_CMDID, buffer, true, true);
          break;
        }
        case METABOLT_INIT_TYPE_THUNDERBOLT: {
          break;
        }
        default: {
          ret = mAgoraEngine.sendStreamMessage(mAgoraAudioStreamId, buffer);
          if (ret < Constants.ERR_OK) {
            Log.e(TAG, "agora sendStreamMessage error:" + ret + ", desc:" + RtcEngine.getErrorDescription(ret));
          }
          break;
        }
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
    long pos = 0;
    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_TRTC: {
        pos = mTXAudioEffectManager.getMusicCurrentPosInMS(TRTC_MUSIC_ID);
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        // todo:
        break;
      }
      case METABOLT_INIT_TYPE_AGORA:
      default: {
        pos = mAgoraEngine.getAudioMixingCurrentPosition();
        break;
      }
    }

    Log.i(TAG, "AudioMixing music progress position:" + pos);
    return pos;
  }

  @Override
  public long getMusicPlayTotalProgress() {
    //if (io.agora.rtc2.Constants.AUDIO_MIXING_STATE_PLAYING != mAudioMixingState) return 0;
    long duration = 0L;
    switch (mMetaboltInitType) {
      case METABOLT_INIT_TYPE_TRTC: {
        String musicPath = getMusicPath();
        duration = mTXAudioEffectManager.getMusicDurationInMS(musicPath);
        break;
      }
      case METABOLT_INIT_TYPE_THUNDERBOLT: {
        // todo:
        break;
      }
      case METABOLT_INIT_TYPE_AGORA:
      default: {
        duration = (long)mAgoraEngine.getAudioMixingDuration();
        break;
      }
    }

    Log.i(TAG, "AudioMixing music progress total:" + duration);
    return duration;
  }



  // TRTC实现部分
  TRTCCloudListener.TRTCAudioFrameListener trtcAudioFrameListener = new TRTCCloudListener.TRTCAudioFrameListener() {
    @Override
    public void onCapturedRawAudioFrame(TRTCCloudDef.TRTCAudioFrame audioFrame) {
      Log.i(TAG, "trtc onCapturedRawAudioFrame audio sampleRate:" + audioFrame.sampleRate + ", channel:" + audioFrame.channel);
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handlerCaptureAudioData(audioFrame.data, audioFrame.data.length, audioFrame.sampleRate, audioFrame.channel, true);
      }
    }

    @Override
    public void onLocalProcessedAudioFrame(TRTCCloudDef.TRTCAudioFrame trtcAudioFrame) {

    }

    @Override
    public void onRemoteUserAudioFrame(TRTCCloudDef.TRTCAudioFrame trtcAudioFrame, String s) {

    }

    @Override
    public void onMixedPlayAudioFrame(TRTCCloudDef.TRTCAudioFrame trtcAudioFrame) {

    }

    @Override
    public void onMixedAllAudioFrame(TRTCCloudDef.TRTCAudioFrame trtcAudioFrame) {

    }
  };

  TRTCCloudListener.TRTCVideoFrameListener trtcVideoFrameListener = new TRTCCloudListener.TRTCVideoFrameListener() {
    @Override
    public void onGLContextCreated() {
    }

    @Override
    public int onProcessVideoFrame(TRTCCloudDef.TRTCVideoFrame srcFrame, TRTCCloudDef.TRTCVideoFrame dstFrame) {
      Log.i(TAG, "trtc onProcessVideoFrame srcFrame:" + srcFrame.width + "*" + srcFrame.height + ", pixelFormat:" +
          srcFrame.pixelFormat + ", rotation:" + srcFrame.rotation);
      int width = srcFrame.width;
      int height = srcFrame.height;

      byte[] nv21 = CommonUtil.doI420ToNV21(srcFrame.data, width, height);
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handleCaptureVideoFrame(width, height, nv21,
            MetaBoltTypes.MTBPixelFormat.MTB_PIXEL_FORMAT_NV21, true, 360 - srcFrame.rotation);
      }
      dstFrame = srcFrame;
      return 0;
    }

    @Override
    public void onGLContextDestory() {
    }
  };

  private TRTCCloudListener.TRTCVideoRenderListener trtcVideoRenderListener = new TRTCCloudListener.TRTCVideoRenderListener() {
    @Override
    public void onRenderVideoFrame(String userId, int streamType, TRTCCloudDef.TRTCVideoFrame videoFrame) {
      int width = videoFrame.width;
      int height = videoFrame.height;

      byte[] nv21 = CommonUtil.doI420ToNV21(videoFrame.data, width, height);
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handleCaptureVideoFrame(width, height, nv21,
            MetaBoltTypes.MTBPixelFormat.MTB_PIXEL_FORMAT_NV21, true, 360 - videoFrame.rotation);
      }
    }
  };
  private class TRTCCloudImplListener extends TRTCCloudListener {

    private WeakReference<MainFragment> mContext;

    public TRTCCloudImplListener(MainFragment activity) {
      super();
      mContext = new WeakReference<>(activity);
    }

    @Override
    public void onRecvCustomCmdMsg(String userId, int cmdID, int seq, byte[] message) {
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.handleRecvMediaExtraInfo(userId, message, message.length);
      }
    }

    @Override
    public void onUserVideoAvailable(String userId, boolean available) {
      if (available) {
        // 当前只考虑1v1
        //if (null != mRemoteUid && userId != mRemoteUid) return;
        Context context = getContext();
        if (context == null) {
          return;
        }
        mMainLooperHandler.post(() ->
        {
          /**Display remote video stream*/
          mTRTCCloud.startRemoteView(mRemoteUid, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG, mTrtcRemoteView);

          if (mMetaServiceState == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS) {
            mIsRemoteMetaViewNeedShow = false;
            // 创建远端meta role
            initRemoteMetaView(context);
          } else {
            mIsRemoteMetaViewNeedShow = true;
          }
        });
      }
    }

    @Override
    public void onError(int errCode, String errMsg, Bundle extraInfo) {
      Log.d(TAG, "trtc onError: " + errMsg + "[" + errCode + "]");
      showInnerToast("trtc onError: " + errMsg + "[" + errCode + "]");
      leaveTrtcChannel(false);
    }

    @Override
    public void onEnterRoom(long result) {
      if (result > 0) {
        mIsUserJoined = true;
        showInnerToast(String.format("trtc joinChannel success channel:%s uid:%s", UserConfig.kChannelId, UserConfig.kMetaUid));
        mMainLooperHandler.post(new Runnable() {
          @Override
          public void run() {
            btn_join.setEnabled(true);
            btn_music_dance.setEnabled(true);
            btn_music_beat.setEnabled(true);
            btn_join.setText(getString(R.string.leave_channel));
            if (null != mMetaBoltDataHandler) {
              mMetaBoltDataHandler.onJoinRoomSuccess(UserConfig.kChannelId, UserConfig.kMetaUid, (int)result);
            }
          }
        });
      } else {
        Log.e(TAG, String.format("trtc joinChannel failed channel:%s uid:%s", UserConfig.kChannelId, UserConfig.kMetaUid));
      }
    }

    @Override
    public void onExitRoom(int reason) {
      Log.i(TAG, String.format("trtc local user %s leaveChannel!", UserConfig.kMetaUid));
      showInnerToast(String.format("trtc local user %s leaveChannel!", UserConfig.kMetaUid));
      mIsUserJoined = false;
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.onLocalAudioStatusChanged(ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_STOPPED, 0);
        mMetaBoltDataHandler.onLocalAudioPublishStatus(ThunderRtcConstant.ThunderLocalAudioPublishStatus.THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_STOP);
        mMetaBoltDataHandler.onLeaveRoom();
      }
    }

    @Override
    public void onRemoteUserEnterRoom(String userId) {
      if (null != mRemoteUid && userId != mRemoteUid) return;
      mRemoteUid = userId;
      showInnerToast(String.format("trtc remote user %s joined!", userId));
    }

    @Override
    public void onRemoteUserLeaveRoom(String userId, int reason) {
      showInnerToast(String.format("trtc remote user %s offline! reason:%d", userId, reason));
      mMainLooperHandler.post(new Runnable() {
        @Override
        public void run() {
          /**Clear render view
           Note: The video will stay at its last frame, to completely remove it you will need to
           remove the SurfaceView from its parent*/
          if (null != mMetaBoltDataHandler) {
            mMetaBoltDataHandler.onUserOffline(mRemoteUid, 0);
          }
          mTRTCCloud.stopRemoteView(mRemoteUid, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
          MetaBoltManager.instance().destroyAvatarRole(mRemoteUid);
          MetaBoltManager.instance().destroyAvatarView(1);
          mRemoteUid = null;
        }
      });
    }

    @Override
    public void onRemoteVideoStatusUpdated(String userId, int streamType, int status, int reason, Bundle extraInfo) {
    }

    @Override
    public void onRemoteAudioStatusUpdated(String userId, int status, int reason, Bundle extraInfo) {
      if (null != mMetaBoltDataHandler) {
        if (TRTCCloudDef.TRTCAVStatusStopped == status || TRTCCloudDef.TRTCAVStatusLoading == status) {
          mMetaBoltDataHandler.onRemoteAudioStopped(userId, true);
        } else if (TRTCCloudDef.TRTCAVStatusStopped == status) {
          mMetaBoltDataHandler.onRemoteAudioStopped(userId, false);
        }
      }
    }

    @Override
    public void onSendFirstLocalAudioFrame() {
      if (null != mMetaBoltDataHandler) {
        mMetaBoltDataHandler.onLocalAudioStatusChanged(ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_SENDING, 0);
        mMetaBoltDataHandler.onLocalAudioPublishStatus(ThunderRtcConstant.ThunderLocalAudioPublishStatus.THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_START);
      }
    }
  }

  TXAudioEffectManager.TXMusicPlayObserver trtcAudioPlayListener = new TXAudioEffectManager.TXMusicPlayObserver() {
    @Override
    public void onStart(int i, int i1) {
      mMusicPlayingState = MUSIC_PLAY_STATE_STARTING;
      MetaBoltManager.instance().enableAudioPlayStatus(true);
    }

    @Override
    public void onPlayProgress(int i, long l, long l1) {

    }

    @Override
    public void onComplete(int i, int i1) {
      mMusicPlayingState = MUSIC_PLAY_STATE_STOPPED;
      MetaBoltManager.instance().enableAudioPlayStatus(false);
    }
  };






  // 拷贝资源操作
  private String getConfigJsonFile() {
    // targetDir="/data/user/0/com.joyy.metabolt.example/files"
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    return targetDir + kConfigJsonFileName;
  }

  private String getNewBeatDownPath() {
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath() + File.separator;
    String template = mMusicFileList.get(mMusicIdx);
    String fileDir = template.substring(0, template.indexOf("."));
    return targetDir + kDanceMusicDir + File.separator + fileDir + File.separator + fileDir + ".bin";
  }

  private String getNewDanceDownPath() {
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath() + File.separator;
    String template = mMusicFileList.get(mMusicIdx);
    String fileDir = template.substring(0, template.indexOf("."));
    return targetDir + kDanceMusicDir + File.separator + fileDir + File.separator + fileDir + ".dat";
  }

  private String getAnimationDownPath() {
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath() + File.separator;
    String template = mBeatAnimationNameList.get(mBeatIdx);
    return targetDir +  "beat" + File.separator + template + File.separator + template;
  }

  private String getRemoteAnimationDownPath() {
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath() + File.separator;
    String template = mBeatAnimationNameList.get(mBeatIdx);
    return targetDir +  "beat" + File.separator + template + File.separator + template;
  }

  private String getRoleModelPath(String roleName) {
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath() + File.separator;
    String targetPath = "model_pkg_android" + File.separator + roleName + ".json";
    return targetDir + targetPath;
  }

  private String getMusicPath() {
    String targetDir = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath() + File.separator;
    String fileName = mMusicFileList.get(mMusicIdx);
    String fileDir = fileName.substring(0, fileName.indexOf("."));
    return targetDir + kDanceMusicDir + File.separator + fileDir + File.separator + fileName;
  }

  private void copyResource() {
    String path = Objects.requireNonNull(getActivity()).getFilesDir().getAbsolutePath();
    String targetDir = path + File.separator;
    copyFilesFromAssets(kLipSyncFileName, targetDir + kLipSyncFileName);
    //共用kLipSyncFileName目录和config.json
    copyFilesFromAssets(kFaceSyncFileName, targetDir + kLipSyncFileName);
    // 拷贝其他资源
    copyFilesFromAssets(kDanceMusicDir, targetDir + kDanceMusicDir);
    copyFilesFromAssets(kBeatAnimationDir, targetDir + kBeatAnimationDir);
    copyFilesFromAssets(kRoleModelPkgDir, targetDir + kRoleModelPkgDir);

    String[] musicItems = new String[mMusicFileList.size()];
    for (int i = 0; i < mMusicFileList.size(); i ++) {
      musicItems[i] = mMusicFileList.get(i);
    }
    ArrayAdapter<String> musicAdapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, musicItems);
    musicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp_music_res.setAdapter(musicAdapter);

    String[] beatItems = new String[mBeatAnimationNameList.size()];
    for (int i = 0; i < mBeatAnimationNameList.size(); i ++) {
      beatItems[i] = mBeatAnimationNameList.get(i);
    }
    ArrayAdapter<String> beatAdapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, beatItems);
    beatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp_beat_res.setAdapter(beatAdapter);
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
          String[] subFileList = Objects.requireNonNull(getActivity()).getAssets().list(assetsPath + File.separator + fileName);
          if (subFileList != null && subFileList.length > 0) {
            Log.i(TAG, "copy directory from assets, dir name: " + fileName);
            copyFilesFromAssets(assetsPath + File.separator + fileName, savePath + File.separator + fileName);
          } else {
            copyFileFromAssets(assetsPath + "/" + fileName, savePath, fileName);
            Log.i(TAG, "copy files from assets, file name: " + fileName);
          }
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
    if (assetName.startsWith(kDanceMusicDir) && saveName.endsWith(".mp3")) {
      mMusicFileList.add(saveName);
    } else if (assetName.startsWith(kBeatAnimationDir)) {
      if (saveName.equals("handup") || saveName.equals("jump") || saveName.equals("singing")) {
        mBeatAnimationNameList.add(saveName);
      }
    }

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
}