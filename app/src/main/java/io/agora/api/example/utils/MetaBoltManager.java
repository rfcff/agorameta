package io.agora.api.example.utils;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.metabolt.IMTBLogCallback;
import com.metabolt.MTBAvatarRole;
import com.metabolt.MTBAvatarView;
import com.metabolt.MTBFaceEmotion;
import com.metabolt.MTBMusicBeatInfo;
import com.metabolt.MTBMusicDanceInfo;
import com.metabolt.MTBServiceConfig;
import com.metabolt.MTBServiceEventHandler;
import com.metabolt.MTBTrackEngine;
import com.metabolt.MetaBoltService;
import com.metabolt.MetaBoltTypes;
import com.metabolt.impl.MetaBoltServiceImpl;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.agora.api.example.R;


public class MetaBoltManager extends MTBServiceEventHandler implements View.OnClickListener, IMTBLogCallback, IMetaBoltDataHandler, MTBTrackEngine.TrackFaceEmotionHandler, MTBTrackEngine.TrackMusicBeatHandler, MTBTrackEngine.TrackMusicDanceHandler {
  private static final String TAG = "MetaBoltManager";
  public static final int kMaxViewNum = 7;

  private Handler mHandler = new Handler(Looper.getMainLooper());
  private final int mHandlerDelayMs = 41;

  @SuppressLint("StaticFieldLeak")
  private static MetaBoltManager mInstance = null;
  public synchronized static void createMetaBoltManager(String modelPath) {
    mInstance = new MetaBoltManager(modelPath);
  }

  public synchronized static void destroyMetaBoltManager() {
    if (mInstance != null) {
      mInstance.deInit();
    }
    mInstance = null;
  }

  public static MetaBoltManager instance() {
    return mInstance;
  }

  private String mAIModelPath = null;
  private MetaBoltManager(String modelPath) {
    mAIModelPath = modelPath;
    UserConfig.kAIModelPath = mAIModelPath;
  }

  public String getAIModelPath() {
    return mAIModelPath;
  }

  private WeakReference<IMetaFragmentHandler> mMetaFragmentHandler = null;
  public void registerSEICallback(IMetaFragmentHandler handler) {
    mMetaFragmentHandler = new WeakReference<>(handler);
  }

  private WeakReference<View> mRootView = null;
  public void setRootView(View rootView) {
    mRootView = new WeakReference<>(rootView);
  }

  private MetaBoltService mMetaBoltSrv = null;

  // 保存对象
  private final ConcurrentHashMap<String, MTBAvatarRole> mAvatarRoleMap = new ConcurrentHashMap<>(); // uid -> role， uid对应的role
  private final ConcurrentHashMap<Integer, MTBAvatarView> mAvatarViewMap = new ConcurrentHashMap<>(); // index -> view, 布局index对应的view

  public boolean isInit() {
    return (mMetaBoltSrv != null) && (mMetaBoltSrv.getMetaBoltServiceState() == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS);
  }

  public int init(MTBServiceConfig config, boolean isAddHandler) {
    if (isInit()) {
      return -1;
    }

    mMetaBoltSrv = new MetaBoltServiceImpl();
    if (isAddHandler) {
      mMetaBoltSrv.addMetaBoltObserver(this);
    }
    mMetaBoltSrv.setLogCallback(this);
    int res = mMetaBoltSrv.initWithConfig(config);
    if (res != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      Log.e(TAG, "metabolt init failed config:" + config.toString());
      mMetaBoltSrv.removeMetaBoltObserver(this);
      mMetaBoltSrv.setLogCallback(null);
      mMetaBoltSrv = null;
      return res;
    }
    Log.i(TAG, "metabolt init config:" + config.toString() + ", isAddHandler:" + isAddHandler);
    return res;
  }

  public int deInit() {
    if (!isInit()) {
      return -1;
    }

    for (Map.Entry<Integer, MTBAvatarView> viewEntry : mAvatarViewMap.entrySet()) {
      LinearLayout linearLayout = mRootView.get().findViewById(getAvatarContainerViewIdByIndex(viewEntry.getKey()));
      linearLayout.removeView(viewEntry.getValue());
      mMetaBoltSrv.destroyAvatarView(viewEntry.getValue());
    }

    for (Map.Entry<String, MTBAvatarRole> roleEntry : mAvatarRoleMap.entrySet()) {
      mMetaBoltSrv.destroyAvatarRole(roleEntry.getValue());
    }

    mAvatarRoleMap.clear();
    mAvatarViewMap.clear();

    synchronized (mRevInfoLock) {
      mEmotionData = null;
      mBeatData = null;
      mDanceData = null;
    }

    int res = mMetaBoltSrv.unInit();
    if (res != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      Log.e(TAG, "uninit metabolt failed: res: " + res);
    }
    mMetaBoltSrv.setLogCallback(null);
    mMetaBoltSrv = null;
    return res;
  }

  public int addMetaBoltObserver(MTBServiceEventHandler handler) {
    if (handler != null) {
      return mMetaBoltSrv.addMetaBoltObserver(handler);
    } else {
      return mMetaBoltSrv.addMetaBoltObserver(this);
    }
  }

  public int removeMetaBoltObserver(MTBServiceEventHandler handler) {
    if (handler != null) {
      return mMetaBoltSrv.removeMetaBoltObserver(handler);
    } else {
      return mMetaBoltSrv.removeMetaBoltObserver(this);
    }
  }

  public int getMetaBoltServiceState() {
    return mMetaBoltSrv.getMetaBoltServiceState();
  }

  public int startFaceEmotionByAudio() {
    Log.i(TAG, "startFaceEmotionByAudio");
    int ret = mMetaBoltSrv.getTrackEngine().startTrackFaceEmotion(MTBTrackEngine.MTTrackMode.MT_TRACK_MODE_AUDIO, this::onRecvTrackEmotionInfo);
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
    startHandler();
    return ret;
  }

  public int startFaceEmotionByCamera() {
    Log.i(TAG, "startFaceEmotionByCamera");
    int ret = mMetaBoltSrv.getTrackEngine().startTrackFaceEmotion(MTBTrackEngine.MTTrackMode.MT_TRACK_MODE_CAMERA, this::onRecvTrackEmotionInfo);
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
//        startHandler();
    return ret;
  }

  public int stopFaceEmotion() {
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackFaceEmotion();
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
    // TODO: video handler
    stopHandler();
    return ret;
  }

  public int startMusicDance(String danceFilePath) {
    Log.i(TAG, "startMusicDance path: " + danceFilePath);
    int ret = mMetaBoltSrv.getTrackEngine().startTrackMusicDance(danceFilePath, this::onRecvTrackDanceInfo);
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
    startHandler();
    return ret;
  }

  public int stopMusicDance() {
    Log.i(TAG, "stopMusicDance");
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackMusicDance();
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
    stopHandler();
    return ret;
  }

  public int startMusicBeat(String beatFilePath) {
    Log.i(TAG, "startMusicBeat path: " + beatFilePath);
    int ret = mMetaBoltSrv.getTrackEngine().startTrackMusicBeat(beatFilePath, this::onRecvTrackBetaInfo);
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
    startHandler();
    return ret;
  }

  public int stopMusicBeat() {
    Log.i(TAG, "stopMusicBeat");
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackMusicBeat();
    if (ret != MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
      return ret;
    }
    stopHandler();
    return ret;
  }

  public int updateMusicPlayProgress(long currentMs, long totalMs) {
    if (mMetaBoltSrv != null && mMetaBoltSrv.getTrackEngine() != null) {
      return mMetaBoltSrv.getTrackEngine().updateMusicPlayProgress((int) currentMs, (int) totalMs);
    }
    return -1;
  }

  public int createAvatarView(Context context, int index) {
    Log.i(TAG, "createAvatarView, context: " + context + ", index: " + index);
    if (mAvatarViewMap.get(index) != null) {
      Log.w(TAG, "createAvatarView failed, view exist in the sdk");
      return 0;
    }

    MTBAvatarView view = mMetaBoltSrv.createAvatarView(context);
    if (view == null) {
      Log.e(TAG, "create view failed, index: " + index);
      return 0;
    }

    mAvatarViewMap.put(index, view);

    LinearLayout frameLayout = mRootView.get().findViewById(R.id.fl_local_meta);
    frameLayout.setOnClickListener(this);
    view.setLayoutParams(frameLayout.getLayoutParams());
    view.setBackgroundColor(getAvatarViewColorIdByIndex(index));
    frameLayout.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
    view.setOnClickListener(this);
    view.bringToFront();
    Log.i(TAG, "createAvatarView MTBAvatarView:" + view + ", frameLayout:" + frameLayout);

    return 0;
  }

  public int destroyAvatarView(int index) {
    Log.i(TAG, "destroyAvatarView index: " + index);
    MTBAvatarView view = mAvatarViewMap.get(index);
    if (view != null) {
      LinearLayout linearLayout = mRootView.get().findViewById(R.id.fl_local_meta);
      linearLayout.removeView(view);
      Log.i(TAG, "destroyAvatarView remove view: " + index);
      mAvatarViewMap.remove(index);
      mMetaBoltSrv.destroyAvatarView(view);
    } else {
      return -1;
    }
    return 0;
  }

  public int createAvatarRole(String roleModelPath, String uid) {
    Log.i(TAG, "createAvatarRole path: " + roleModelPath + ", uid: " + uid);
    MTBAvatarRole role = mMetaBoltSrv.createAvatarRole(roleModelPath, uid);
    if (role == null) {
      Log.e(TAG, "create role failed, path: " + roleModelPath + ", uid: " + uid);
      return -1;
    }
    mAvatarRoleMap.put(uid, role);
    return 0;
  }

  public int setRoleIndex(int targetIndex, String targetUid) {
    Log.i(TAG, "setRoleIndex target index: " + targetIndex + ", target uid: " + targetUid);
    MTBAvatarView targetView = mAvatarViewMap.get(targetIndex);
    if (targetView == null) {
      Log.e(TAG, "setRoleIndex failed, view has not been created, target index: " + targetIndex);
      return -1;
    }

    MTBAvatarRole role = mAvatarRoleMap.get(targetUid);
    if (role == null) {
      Log.e(TAG, "setRoleIndex failed, role has not been created, target uid: " + targetUid);
      return -1;
    }
    return targetView.setAvatarRole(role);
  }

  public int destroyAvatarRole(String uid) {
    Log.i(TAG, "destroyAvatarRole uid: " + uid);
    MTBAvatarRole role = mAvatarRoleMap.get(uid);
    if (role != null) {
      mAvatarRoleMap.remove(uid);
      mMetaBoltSrv.destroyAvatarRole(role);
    }
    return 0;
  }

  public int setAnimation(String uid, String path) {
    Log.i(TAG, "setAnimation uid: " + uid + ", path: " + path);
    MTBAvatarRole role = mAvatarRoleMap.get(uid);
    if (role != null) {
      return role.setMusicBeatAnimation(path);
    }
    return -1;
  }

  public int setAvatarViewType(String uid, int type) {
    Log.i(TAG, "setAvatarViewType uid: " + uid + ", type: " + type);
    MTBAvatarRole role = mAvatarRoleMap.get(uid);
    if (role != null) {
      return role.setAvatarViewType(type);
    }
    return 0;
  }

  public int resetFaceEmotion(String uid) {
    Log.i(TAG, "resetRole uid: " + uid);
    MTBAvatarRole role = mAvatarRoleMap.get(uid);
    if (role != null) {
      return role.resetFaceEmotion();
    }
    return -1;
  }

  public int resetMusicBeat(String uid) {
    Log.i(TAG, "resetMusicBeat uid: " + uid);
    MTBAvatarRole role = mAvatarRoleMap.get(uid);
    if (role != null) {
      return role.resetMusicBeat();
    }
    return -1;
  }

  public int resetMusicDance(String uid) {
    Log.i(TAG, "resetMusicDance uid: " + uid);
    MTBAvatarRole role = mAvatarRoleMap.get(uid);
    if (role != null) {
      return role.resetMusicDance();
    }
    return -1;
  }

  public boolean isRoleExist(String uid) {
    return mAvatarRoleMap.get(uid) != null;
  }

  public boolean isViewExist(int index) {
    return mAvatarViewMap.get(index) != null;
  }

  // callback to MetaBoltTest
  private MetaBoltMgrCallback mMtbMgrCallback = null;
  public void registerMgrCallback(MetaBoltMgrCallback callback) {
    mMtbMgrCallback = callback;
  }

  @Override
  public void onJoinRoomSuccess(String channel, String uid, int elapsed) {
    if (mMtbMgrCallback != null) {
      mMtbMgrCallback.onJoinRoomSuccess(channel, uid, elapsed);
    }
  }

  /**
   *  MetaBolt callback by Hsu
   */
  @Override
  public void onError(int errCode, String description) {
    super.onError(errCode, description);
    if (mMetaFragmentHandler.get() != null) {
      mMetaFragmentHandler.get().onStateMsgCallback(description + ", error code: " + errCode);
    }
  }

  @Override
  public void onMetaBoltServiceStateChanged(int state) {
    super.onMetaBoltServiceStateChanged(state);
    if (mMetaFragmentHandler.get() != null) {
      mMetaFragmentHandler.get().onStateMsgCallback("onMetaBoltServiceStateChanged :" + state);
    }

    if (mMtbMgrCallback != null) {
      mMtbMgrCallback.onMetaBoltServiceStateChanged(state);
    }
  }

  @Override
  public void onMTBLog(int level, String tag, String text) {
    switch (level) {
      case MetaBoltTypes.MTBLogLevel.MTB_LOG_LEVEL_TRACE:
      case MetaBoltTypes.MTBLogLevel.MTB_LOG_LEVEL_DEBUG:
        Log.d(TAG, "[" + tag + "] " + text);
        break;
      case MetaBoltTypes.MTBLogLevel.MTB_LOG_LEVEL_INFO:
        Log.i(TAG, "[" + tag + "] " + text);
        break;
      case MetaBoltTypes.MTBLogLevel.MTB_LOG_LEVEL_WARN:
        Log.w(TAG, "[" + tag + "] " + text);
        break;
      case MetaBoltTypes.MTBLogLevel.MTB_LOG_LEVEL_ERROR:
        Log.e(TAG, "[" + tag + "] " + text);
        break;
      default:
        Log.e(TAG, "[" + tag + "] " + text);
        break;
    }
  }

  private final Object mRevInfoLock = new Object();
  byte[] mEmotionData = null;
  byte[] mBeatData = null;
  byte[] mDanceData = null;

  @Override
  public int onRecvTrackEmotionInfo(MTBFaceEmotion info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kUid);
    if (role != null) {
      role.setFaceEmotion(info);
    }

    synchronized (mRevInfoLock) {
      mEmotionData = info.serializeToBin();
    }
    return 0;
  }

  @Override
  public int onRecvTrackBetaInfo(MTBMusicBeatInfo info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kUid);
    if (role != null) {
      role.setMusicBeat(info);
    }

    synchronized (mRevInfoLock) {
      mBeatData = info.serializeToBin();
    }
    return 0;
  }

  @Override
  public int onRecvTrackDanceInfo(MTBMusicDanceInfo info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kUid);
    if (role != null) {
      role.setMusicDance(info);
    }

    synchronized (mRevInfoLock) {
      mDanceData = info.serializeToBin();
    }
    return 0;
  }

  private int mUseHandlerCnt = 0;
  private void startHandler() {
    if (mUseHandlerCnt == 0) {
      mHandler.removeCallbacks(mRunnable);
      sendMsg(0);
    }
    ++mUseHandlerCnt;
  }

  private void stopHandler() {
    --mUseHandlerCnt;
    if (mUseHandlerCnt == 0) {
      mHandler.removeCallbacks(mRunnable);
    }
  }

  private void sendMsg(long delayMillis) {
    mHandler.postDelayed(mRunnable, delayMillis);
  }

  private final Runnable mRunnable = () -> {
    handleSendMediaExtraInfo();
    sendMsg(mHandlerDelayMs);
  };

  /**
   *  thunder callback by Hsu
   */
  @Override
  public void handlerCaptureAudioData(byte[] data, int dataSize, int sampleRate, int channel, boolean vad) {
    if (mMetaBoltSrv != null && mMetaBoltSrv.getMetaBoltServiceState() == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS) {
      mMetaBoltSrv.getTrackEngine().applyAudioPCMData(data, channel, sampleRate, 16, vad);
    }
  }

  /**
   *  SEI format: |lipsync|type(1)|len(2)|data(n)||type(1)|len(2)|data(n)||type(1)|len(2)|data(n)|
   *  1. >1 字节表示数值的字段使用大端序
   *  2. len: data 的字节数，不包括自己
   *
   *  type: 1  blend shape
   *  type: 2  dance
   *  type: 3  beat
   */
  private final int kBlendShapeType = 1;
  private final int kDanceType      = 2;
  private final int kBeatType       = 3;
  public void handleSendMediaExtraInfo() {
    byte[] emotionData = null;
    byte[] beatData = null;
    byte[] danceData = null;

    synchronized (mRevInfoLock) {
      emotionData = mEmotionData;
      beatData = mBeatData;
      danceData = mDanceData;

      mEmotionData = null;
      mBeatData = null;
      mDanceData = null;
    }

    String extraInfoStart = "lipsync";
    int bufferLen = extraInfoStart.length();
    int typeLen = 3;
    if (emotionData != null) {
      bufferLen += emotionData.length + typeLen;
    }

    if (danceData != null) {
      bufferLen += danceData.length + typeLen;
    }

    if (beatData != null) {
      bufferLen += beatData.length + typeLen;
    }

    ByteBuffer byteBuffer = ByteBuffer.allocate(bufferLen);
    byteBuffer.put("lipsync".getBytes());

    if (emotionData != null) {
      byteBuffer.put((byte)kBlendShapeType);
      byteBuffer.put(ByteUtil.integerToTwoBytes(emotionData.length));
      byteBuffer.put(emotionData);
    }

    if (danceData != null) {
      byteBuffer.put((byte)kDanceType);
      byteBuffer.put(ByteUtil.integerToTwoBytes(danceData.length));
      byteBuffer.put(danceData);
    }

    if (beatData != null) {
      byteBuffer.put((byte)kBeatType);
      byteBuffer.put(ByteUtil.integerToTwoBytes(beatData.length));
      byteBuffer.put(beatData);
    }

    int sendBufferLen = byteBuffer.array().length;
    if (sendBufferLen != 0) {
//      int ret = mMetaFragmentHandler.get().onAudioSEIData(byteBuffer.array());
//      if (ret != 0 || sendBufferLen > 500) { // 500 is max SEI length of audio opus dse
//        Log.e(TAG, "send SEI failed, ret: " + ret + ", length: " + sendBufferLen);
//      }
    }
  }

  private final String kLipsyncFlag = "lipsync";
  private final int kSEIStartLen = kLipsyncFlag.getBytes().length;

  private int mRevMediaExtraInfoCnt = 0;
  @Override
  public void handleRecvMediaExtraInfo(String uid, byte[] data, int dataLen) {
    if (mRevMediaExtraInfoCnt % 100 == 0) {
      Log.i(TAG, "handleRecvMediaExtraInfo, uid: " + uid + ", base64 data: " + Base64.encodeToString(data, Base64.NO_WRAP));
    }
    mRevMediaExtraInfoCnt++;
    if (mMetaBoltSrv != null) {
      byte[] startData = ByteUtil.subByte(data, 0, kSEIStartLen);
      String startStr = new String(startData, StandardCharsets.UTF_8);
      if (!startStr.equalsIgnoreCase(kLipsyncFlag)) {
        Log.e(TAG, "sei is not begin with lipsync");
        return;
      }

      byte[] trackData = ByteUtil.subByte(data, kSEIStartLen, data.length - kSEIStartLen);
      int trackDataLen = data.length - kSEIStartLen;

      byte[] emotionData = null;
      byte[] danceData = null;
      byte[] beatData = null;
      int offsetLen = 0;
      final int typeLine = 3;
      int remnantLen = trackDataLen;
      while (remnantLen > typeLine) {
        int trackType = trackData[offsetLen];
        int parseDataLen = ByteUtil.twoBytesToInt(trackData, offsetLen + 1);
        offsetLen += typeLine;

        byte[] parseData = ByteUtil.subByte(trackData, offsetLen, parseDataLen);
        if (trackType == kBlendShapeType) {
          emotionData = parseData;
        } else if (trackType == kDanceType) {
          danceData = parseData;
        } else if (trackType == kBeatType) {
          beatData = parseData;
        } else {
          Log.e(TAG, "handleRecvMediaExtraInfo, uid: " + uid + ", data format is error: " + Base64.encodeToString(data, Base64.NO_WRAP));
        }
        remnantLen = remnantLen - typeLine - parseDataLen;
        offsetLen = offsetLen + parseDataLen;
      }

      if (emotionData != null || beatData != null || danceData != null) {
        if (emotionData != null) {
          MTBFaceEmotion emotion = new MTBFaceEmotion();
          int ret = emotion.unserializeFromBin(emotionData);
          if (ret == MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
            MTBAvatarRole role = mAvatarRoleMap.get(uid);
            if (role != null) {
              role.setFaceEmotion(emotion);
            }
          } else {
            Log.e(TAG, "emotion info unserialize from bin failed, data: " + Base64.encodeToString(emotionData, Base64.NO_WRAP));
          }
        }

        if (danceData != null) {
          MTBMusicDanceInfo danceInfo = new MTBMusicDanceInfo();
          int ret = danceInfo.unserializeFromBin(danceData);
          if (ret == MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
            MTBAvatarRole role = mAvatarRoleMap.get(uid);
            if (role != null) {
              role.setMusicDance(danceInfo);
            }
          } else {
            Log.e(TAG, " dance info unserialize from bin failed, data: " + Base64.encodeToString(danceData, Base64.NO_WRAP));
          }
        }

        if (beatData != null) {
          MTBMusicBeatInfo beatInfo = new MTBMusicBeatInfo();
          int ret = beatInfo.unserializeFromBin(beatData);
          if (ret == MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
            MTBAvatarRole role = mAvatarRoleMap.get(uid);
            if (role != null) {
              role.setMusicBeat(beatInfo);
            }
          } else {
            Log.e(TAG, " beat info unserialize from bin failed, data: " + Base64.encodeToString(beatData, Base64.NO_WRAP));
          }
        }
      }
    }
  }

  /**
   *  UI
   */
  private int getAvatarContainerViewIdByIndex(int index) {
//    if (index == 0) {
//      return R.id.avatar_view_container_0;
//    } else if (index == 1) {
//      return R.id.avatar_view_container_1;
//    } else if (index == 2) {
//      return R.id.avatar_view_container_2;
//    } else if (index == 3) {
//      return R.id.avatar_view_container_3;
//    } else if (index == 4) {
//      return R.id.avatar_view_container_4;
//    } else if (index == 5) {
//      return R.id.avatar_view_container_5;
//    }
//    return R.id.avatar_view_container_6;
    return R.id.fl_local_meta;
//    return R.id.fl_local;
  }

  private int getAvatarViewColorIdByIndex(int index) {
    if (index == 0) {
      return Color.BLUE;
    } else if (index == 1) {
      return Color.RED;
    } else if (index == 2) {
      return Color.GRAY;
    } else if (index == 3) {
      return Color.GREEN;
    } else if (index == 4) {
      return Color.BLACK;
    } else if (index == 5) {
      return Color.YELLOW;
    }
    return Color.LTGRAY;
  }

  boolean mIsFullScreen = false;
  private void controlOtherView(boolean hide) {
//    int value = hide ? View.INVISIBLE : View.VISIBLE;
//    mRootView.get().findViewById(R.id.meta_view_rect).setVisibility(value);
//    mRootView.get().findViewById(R.id.meta_tab_host_test).setVisibility(value);
//    mRootView.get().findViewById(R.id.meta_tab_host_test2).setVisibility(value);
  }

  @Override
  public void onClick(View view) {
    Log.i(TAG, "onClick view:" + view);
//    LinearLayout fullLayout = mRootView.get().findViewById(R.id.metabolt_full_view_container);
//    if (fullLayout == null) {
//      return ;
//    }
//    mIsFullScreen = !mIsFullScreen;
//    if (mIsFullScreen) {
//      // 全屏
//      int findIndex = -1;
//      for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
//        entry.getValue().setVisibility(View.INVISIBLE);
//        if (entry.getValue() == view) {
//          findIndex = entry.getKey();
//        }
//      }
//
//      if (findIndex != -1) {
//        int containerId = getAvatarContainerViewIdByIndex(findIndex);
//        LinearLayout windowLayout = mRootView.get().findViewById(containerId);
//        windowLayout.removeView(view);
//        controlOtherView(true);
//        fullLayout.addView(view);
//        fullLayout.setVisibility(View.VISIBLE);
//        view.setVisibility(View.VISIBLE);
//      }
//    } else {
//      // 非全屏
//      fullLayout.removeView(view);
//      fullLayout.setVisibility(View.GONE);
//
//      controlOtherView(false);
//      for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
//        int containerId = getAvatarContainerViewIdByIndex(entry.getKey());
//        LinearLayout windowLayout = mRootView.get().findViewById(containerId);
//        windowLayout.setVisibility(View.VISIBLE);
//        if (entry.getValue() == view) {
//          windowLayout.addView(view);
//        } else {
//          entry.getValue().refresh();
//        }
//        entry.getValue().setVisibility(View.VISIBLE);
//      }
//    }
  }

  public void onHiddenChanged(boolean hidden) {
    for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
      MTBAvatarView view = entry.getValue();
      Integer viewIndex = entry.getKey();
      int containerId = getAvatarContainerViewIdByIndex(viewIndex);
      LinearLayout windowLayout = mRootView.get().findViewById(containerId);

      if (hidden) {
        windowLayout.removeView(view);
        windowLayout.setVisibility(View.INVISIBLE);
        view.setVisibility(View.INVISIBLE);
      } else {
        windowLayout.addView(view);
        windowLayout.setVisibility(View.VISIBLE);
        view.setVisibility(View.VISIBLE);
        view.refresh();
      }
    }

  }

  public void resumeView() {
    for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
      MTBAvatarView view = entry.getValue();
      view.refresh();
    }
  }
}
