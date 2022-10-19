package io.agora.api.example.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.agora.api.example.R;
import io.agora.rtc.Constants;


public class MetaBoltManager extends MTBServiceEventHandler implements View.OnClickListener, IMTBLogCallback, IMetaBoltDataHandler, MTBTrackEngine.TrackFaceEmotionHandler, MTBTrackEngine.TrackMusicBeatHandler, MTBTrackEngine.TrackMusicDanceHandler, Handler.Callback {
  private static final String TAG = "MetaBoltManager";
  public static final int kMaxViewNum = 6;

  private Handler mHandler = null;
  private final int kHandlerDelayMs = 40;

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
      Log.e(TAG, "init config failed, model path: " + mAIModelPath);
      mMetaBoltSrv.removeMetaBoltObserver(this);
      mMetaBoltSrv.setLogCallback(null);
      mMetaBoltSrv = null;
      return res;
    }
    Log.i(TAG, "metaBolt sdk version: " + mMetaBoltSrv.getMetaBoltVersion() + ", model path: " + mAIModelPath);
    startHandler();
    return res;
  }

  public int deInit() {
    if (!isInit()) {
      return -1;
    }
    stopHandler();
    for (Map.Entry<Integer, MTBAvatarView> viewEntry : mAvatarViewMap.entrySet()) {
      LinearLayout linearLayout = mRootView.get().findViewById(getAvatarContainerViewIdByIndex(viewEntry.getKey()));
      linearLayout.setBackgroundColor(Color.WHITE);
      linearLayout.removeView(viewEntry.getValue());
      mMetaBoltSrv.destroyAvatarView(viewEntry.getValue());
    }

    for (Map.Entry<String, MTBAvatarRole> roleEntry : mAvatarRoleMap.entrySet()) {
      mMetaBoltSrv.destroyAvatarRole(roleEntry.getValue());
    }

    mAvatarRoleMap.clear();
    mAvatarViewMap.clear();

    mEmotionDataBufferList.clear();
    mBeatDataBufferList.clear();
    mDanceDataBufferList.clear();

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

  private boolean mIsOpenAudioEmotion = false;
  public int startFaceEmotionByAudio() {
    Log.i(TAG, "startFaceEmotionByAudio");
    mIsOpenAudioEmotion = true;
    return mMetaBoltSrv.getTrackEngine().startTrackFaceEmotion(MTBTrackEngine.MTTrackMode.MT_TRACK_MODE_AUDIO, this::onRecvTrackEmotionInfo);
  }

  public int startFaceEmotionByCamera() {
    Log.i(TAG, "startFaceEmotionByCamera");
    return mMetaBoltSrv.getTrackEngine().startTrackFaceEmotion(MTBTrackEngine.MTTrackMode.MT_TRACK_MODE_CAMERA, this::onRecvTrackEmotionInfo);
  }

  public int stopFaceEmotion() {
    Log.i(TAG, "stopFaceEmotion");
    mEmotionDataBufferList.clear();
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackFaceEmotion();
    // TODO: video handler
    mIsOpenAudioEmotion = false;
    handleSendMediaExtraInfo();
    return ret;
  }

  private boolean mIsOpenDance = false;
  public int startMusicDance(String danceFilePath) {
    Log.i(TAG, "startMusicDance path: " + danceFilePath);
    if (null == mMetaBoltSrv) return -1;
    mIsOpenDance = true;
    return mMetaBoltSrv.getTrackEngine().startTrackMusicDance(danceFilePath, this::onRecvTrackDanceInfo);
  }

  public int stopMusicDance() {
    Log.i(TAG, "stopMusicDance");
    mDanceDataBufferList.clear();
    if (null == mMetaBoltSrv) return -1;
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackMusicDance();
    mIsOpenDance = false;
    handleSendMediaExtraInfo();
    return ret;
  }

  private boolean mIsOpenBeat = false;
  public int startMusicBeat(String beatFilePath) {
    Log.i(TAG, "startMusicBeat path: " + beatFilePath);
    mIsOpenBeat = true;
    return mMetaBoltSrv.getTrackEngine().startTrackMusicBeat(beatFilePath, this::onRecvTrackBetaInfo);
  }

  public int stopMusicBeat() {
    Log.i(TAG, "stopMusicBeat");
    mBeatDataBufferList.clear();
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackMusicBeat();
    mIsOpenBeat = false;
    handleSendMediaExtraInfo();
    return ret;
  }

  private volatile boolean mAudioPlayerStart = false;
  public void enableAudioPlayStatus(boolean enable) {
    mAudioPlayerStart = enable;
  }

  private int updateMusicPlayProgressToMetaBolt() {
    if (isInit() && (mIsOpenDance || mIsOpenBeat) && mAudioPlayerStart) {
      if (mMetaBoltSrv != null && mMetaBoltSrv.getTrackEngine() != null) {
        long currentMs = mMetaFragmentHandler.get().getMusicPlayCurrentProgress();
        long totalMs = mMetaFragmentHandler.get().getMusicPlayTotalProgress();
        if (currentMs != -1 && totalMs != -1) {
          return mMetaBoltSrv.getTrackEngine().updateMusicPlayProgress((int) currentMs, (int) totalMs);
        }
      }
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

    FrameLayout layout = mRootView.get().findViewById(getAvatarContainerViewIdByIndex(index));
    //layout.setBackgroundColor(Color.GRAY);
    view.setLayoutParams(layout.getLayoutParams());
    layout.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    view.setOnClickListener(this);
    //view.bringToFront();

    return 0;
  }

  public int destroyAvatarView(int index) {
    Log.i(TAG, "destroyAvatarView index: " + index);
    MTBAvatarView view = mAvatarViewMap.get(index);
    if (view != null) {
      FrameLayout layout = mRootView.get().findViewById(getAvatarContainerViewIdByIndex(index));
      //layout.setBackgroundColor(Color.WHITE);
      layout.removeView(view);
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

  public int refresh(int index) {
    MTBAvatarView view = mAvatarViewMap.get(index);
    if (view != null) {
      view.refresh();
    }
    return 0;
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

  private volatile int mAudioPublishStatus = -1;
  @Override
  public void onJoinRoomSuccess(String channel, String uid, int elapsed) {
    if (mMtbMgrCallback != null) {
      mMtbMgrCallback.onJoinRoomSuccess(channel, uid, elapsed);
    }
  }

  @Override
  public void onLocalAudioStatusChanged(int status, int errorReason) {
//    if (status == ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_STOPPED ||
//        status == ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_FAILED) {
    if (Constants.LOCAL_AUDIO_STREAM_STATE_STOPPED == status || Constants.LOCAL_AUDIO_STREAM_STATE_FAILED == status) {
      if (isInit()) {
        MTBAvatarRole myRole = mAvatarRoleMap.get(UserConfig.kUid);
        if (myRole != null && mIsOpenAudioEmotion) {
          myRole.resetFaceEmotion();
        }
      }
    }
  }

  @Override
  public void onLocalAudioPublishStatus(int status) {
    mAudioPublishStatus = status;
  }

  @Override
  public void onLeaveRoom() {
    mRevMediaSEIMap.clear();
    mSendMediaSEIMap.clear();
  }

  @Override
  public void onUserOffline(String uid, int reason) {
    if (mMtbMgrCallback != null) {
      mMtbMgrCallback.onUserOffline(uid, reason);
    }

    Pair<Long, Map<Integer, Integer>> mapObject = mRevMediaSEIMap.get(uid);
    if (mapObject != null) {
      mRevMediaSEIMap.remove(uid);
    }
  }

  @Override
  public void onRemoteAudioStopped(String uid, boolean stop) {
    Log.i(TAG, "onRemoteAudioStopped: " + uid + ", stop: " + stop);
    if (mMtbMgrCallback != null) {
      mMtbMgrCallback.onRemoteAudioStopped(uid, stop);
    }

//        if (stop) {
//            MTBAvatarRole role = mAvatarRoleMap.get(uid);
//            if (role != null) {
//                role.resetFaceEmotion();
//                role.resetMusicBeat();
//                role.resetMusicDance();
//            }
//        }
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

  private final Queue<byte[]> mEmotionDataBufferList = new ConcurrentLinkedDeque<>();
  private final Queue<byte[]> mBeatDataBufferList = new ConcurrentLinkedDeque<>();
  private final Queue<byte[]> mDanceDataBufferList = new ConcurrentLinkedDeque<>();

  @Override
  public int onRecvTrackEmotionInfo(MTBFaceEmotion info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kUid);
    if (role != null) {
      role.setFaceEmotion(info);
    }
    mEmotionDataBufferList.clear();
    mEmotionDataBufferList.add(info.serializeToBin());
    return 0;
  }

  @Override
  public int onRecvTrackBetaInfo(MTBMusicBeatInfo info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kUid);
    if (role != null) {
      role.setMusicBeat(info);
    }
    mBeatDataBufferList.add(info.serializeToBin());
    return 0;
  }

  @Override
  public int onRecvTrackDanceInfo(MTBMusicDanceInfo info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kUid);
    if (role != null) {
      role.setMusicDance(info);
    }
    if (mDanceDataBufferList.size() > 3) {
      Log.i(TAG, "remain too much dance info");
      mDanceDataBufferList.remove();
    }
    mDanceDataBufferList.add(info.serializeToBin());
    return 0;
  }

  private void startHandler() {
    mHandler = new Handler(Looper.getMainLooper(), this::handleMessage);
    Log.i(TAG, "start handler");
    sendMsg(kHandlerDelayMs);
  }

  private void stopHandler() {
    Log.i(TAG, "stopHandler");
    mHandler.removeCallbacksAndMessages(null);
    mHandler = null;
  }

  private void sendMsg(long delayMillis) {
    Message msg = Message.obtain();
    msg.what = MetaBoltHandlerEvent.kRunnable;
    mHandler.sendMessageDelayed(msg, kHandlerDelayMs);
  }

  private class MetaBoltHandlerEvent {
    public static final int kRunnable = 1000;
  }

  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MetaBoltHandlerEvent.kRunnable:
        updateMusicPlayProgressToMetaBolt();
        handleSendMediaExtraInfo();
        break;
      default:
        Log.i(TAG, "handleMessage failed msg what: " + msg.what);
        break;
    }

    Message newMsg = Message.obtain();
    newMsg.what = MetaBoltHandlerEvent.kRunnable;
    mHandler.sendMessageDelayed(newMsg, kHandlerDelayMs);
    return false;
  }

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
  private final static String kLipsyncFlag = "lipsync";
  public final static int kSEIStartLen = kLipsyncFlag.getBytes().length;

  public final static int kBlendShapeType = 1;
  public final static int kDanceType      = 2;
  public final static int kBeatType       = 3;
  private String switchTypeToString(int type) {
    if (type == kBlendShapeType) {
      return "bs";
    } else if (type == kDanceType) {
      return "dance";
    } else if (type == kBeatType) {
      return "beat";
    } else {
      return "unknown";
    }
  }

  private long mLastSendSEITimestamp = 0;
  private final Map<Integer, Integer> mSendMediaSEIMap = new ConcurrentHashMap<>();
  public void handleSendMediaExtraInfo() {
    if ((mIsOpenAudioEmotion || mIsOpenDance || mIsOpenBeat) &&
        mAudioPublishStatus == Constants.PUB_STATE_PUBLISHED) {

      if (mEmotionDataBufferList.isEmpty() && mDanceDataBufferList.isEmpty() && mBeatDataBufferList.isEmpty()) {
        return;
      }

      if (mSendMediaSEIMap.isEmpty()) {
        mSendMediaSEIMap.put(kBlendShapeType, 0);
        mSendMediaSEIMap.put(kDanceType, 0);
        mSendMediaSEIMap.put(kBeatType, 0);
        mLastSendSEITimestamp = System.currentTimeMillis();
      }

      int bufferLen = kSEIStartLen;
      int typeLen = 3;

      byte[] emotionData = mEmotionDataBufferList.poll();
      if (emotionData != null) {
        bufferLen += emotionData.length + typeLen;
      }

      byte[] danceData = mDanceDataBufferList.poll();
      if (danceData != null) {
        bufferLen += danceData.length + typeLen;
      }

      byte[] beatData = mBeatDataBufferList.poll();
      if (beatData != null) {
        bufferLen += beatData.length + typeLen;
      }

      ByteBuffer byteBuffer = ByteBuffer.allocate(bufferLen);
      byteBuffer.put(kLipsyncFlag.getBytes());

      int type = -1;
      if (emotionData != null) {
        type = kBlendShapeType;
        byteBuffer.put((byte) type);
        byteBuffer.put(ByteUtil.integerToTwoBytes(emotionData.length));
        byteBuffer.put(emotionData);
        mSendMediaSEIMap.put(type, mSendMediaSEIMap.get(type) + 1);
      }

      if (danceData != null) {
        type = kDanceType;
        byteBuffer.put((byte) type);
        byteBuffer.put(ByteUtil.integerToTwoBytes(danceData.length));
        byteBuffer.put(danceData);
        mSendMediaSEIMap.put(type, mSendMediaSEIMap.get(type) + 1);
      }

      if (beatData != null) {
        type = kBeatType;
        byteBuffer.put((byte) type);
        byteBuffer.put(ByteUtil.integerToTwoBytes(beatData.length));
        byteBuffer.put(beatData);
        mSendMediaSEIMap.put(type, mSendMediaSEIMap.get(type) + 1);
      }

      int sendBufferLen = byteBuffer.array().length;
      if (sendBufferLen > kSEIStartLen) {
        int ret = mMetaFragmentHandler.get().onAudioSEIData(byteBuffer);
        if (ret != 0 || sendBufferLen > 500) { // 500 is max SEI length of audio opus dse
          Log.e(TAG, "send SEI failed, ret: " + ret + ", length: " + sendBufferLen);
        }

        long currentTimestamp = System.currentTimeMillis();
        if ((currentTimestamp - mLastSendSEITimestamp) > 1000) {
          mLastSendSEITimestamp = currentTimestamp;
          StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append("handleSendMediaExtraInfo ");
          for (Map.Entry<Integer, Integer> entry : mSendMediaSEIMap.entrySet()) {
            stringBuilder.append(" { type: ");
            stringBuilder.append(switchTypeToString(entry.getKey()));
            stringBuilder.append(", count: ");
            stringBuilder.append(entry.getValue());
            stringBuilder.append(" } ");
            mSendMediaSEIMap.put(entry.getKey(), 0);
          }
          Log.i(TAG, stringBuilder.toString());
        }
      }
    }
  }

  private Map<String, Pair<Long, Map<Integer, Integer>>> mRevMediaSEIMap = new ConcurrentHashMap<>(); // uid -> <type, nums>
  @Override
  public void handleRecvMediaExtraInfo(String uid, byte[] data, int dataLen) {
    Pair<Long, Map<Integer, Integer>> revMediaInfoPair = mRevMediaSEIMap.get(uid);
    if (revMediaInfoPair == null) {
      Map<Integer, Integer> newRevMediaSEIUidMap = new ConcurrentHashMap<>();
      newRevMediaSEIUidMap.put(kBlendShapeType, 0);
      newRevMediaSEIUidMap.put(kDanceType, 0);
      newRevMediaSEIUidMap.put(kBeatType, 0);

      revMediaInfoPair = new Pair<>((long) 0, newRevMediaSEIUidMap);
      mRevMediaSEIMap.put(uid, revMediaInfoPair);
    }

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
        revMediaInfoPair.second.put(trackType, revMediaInfoPair.second.get(trackType) + 1);
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

        long currentTimestamp = System.currentTimeMillis();
        long lastTimestamp = revMediaInfoPair.first;
        if ((currentTimestamp - lastTimestamp) > 1000) {
          Pair<Long, Map<Integer, Integer>> newPair = new Pair<>(currentTimestamp, revMediaInfoPair.second);
          mRevMediaSEIMap.put(uid, newPair);
          StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append("handleRecvMediaExtraInfo ");
          stringBuilder.append("{ uid: ");
          stringBuilder.append(uid);
          stringBuilder.append(", rev media SEI: ");
          for (Map.Entry<Integer, Integer> entry : revMediaInfoPair.second.entrySet()) {
            stringBuilder.append("[type: ");
            stringBuilder.append(switchTypeToString(entry.getKey()));
            stringBuilder.append(", num: ");
            stringBuilder.append(entry.getValue());
            stringBuilder.append("]");
            revMediaInfoPair.second.put(entry.getKey(), 0);
          }
          stringBuilder.append(" }");
          Log.i(TAG, stringBuilder.toString());
        }
      }
    }
  }

  /**
   *  UI
   */
  private int getAvatarContainerViewIdByIndex(int index) {
    switch (index) {
      case 0:
        return R.id.fl_local_meta;
      default:
        return R.id.fl_remote_meta;
    }
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

  boolean mIsFullScreenNow = false;
  private void controlOtherView(boolean hide) {
//    int value = hide ? View.GONE : View.VISIBLE;
//    mRootView.get().findViewById(R.id.meta_view_rect).setVisibility(value);
//    mRootView.get().findViewById(R.id.meta_tab_host_test).setVisibility(value);
//    mRootView.get().findViewById(R.id.meta_tab_host_test2).setVisibility(value);
  }

  @Override
  public void onClick(View view) {
//    Log.i(TAG, "onClick, is full view now: " + mIsFullScreenNow + ", view object: " + view);
//    LinearLayout fullLayout = mRootView.get().findViewById(R.id.metabolt_full_view_container);
//    if (fullLayout == null) {
//      return ;
//    }
//
//    try {
//      // OPPO-CPH2127总是在这里报错：java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
//      int findIndex = -1;
//      if (!mIsFullScreenNow) {
//        // 放大变全屏
//        for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
//          if (entry.getValue() == view) {
//            findIndex = entry.getKey();
//            entry.getValue().setShowOnTop(false);
//          }
//        }
//
//        if (findIndex != -1) {
//          fullLayout.removeAllViews();
//          fullLayout.setVisibility(View.VISIBLE);
//          controlOtherView(true);
//          ViewParent parent = view.getParent();
//          if (parent!=null) {
//            ((ViewGroup)parent).removeView(view);
//          }
//          view.setLayoutParams(new LinearLayout.LayoutParams(
//              ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
//          fullLayout.addView(view);
//          view.setVisibility(View.VISIBLE);
//          ((MTBAvatarView)view).setShowOnTop(true);
//        }
//
//        mIsFullScreenNow = !mIsFullScreenNow;
//      } else {
//        // 还原非全屏
//        ViewParent parent = view.getParent();
//        if (parent!=null) {
//          ((ViewGroup)parent).removeView(view);
//        }
//        if (view instanceof MTBAvatarView) {
//          ((MTBAvatarView)view).setShowOnTop(false);
//        }
//        controlOtherView(false);
//        fullLayout.removeAllViews();
//        fullLayout.setVisibility(View.GONE);
//        for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
//          if (entry.getValue() == view) {
//            findIndex = entry.getKey();
//          }
//        }
//        int containerId = getAvatarContainerViewIdByIndex(findIndex);
//        LinearLayout windowLayout = mRootView.get().findViewById(containerId);
//        windowLayout.addView(view);
//
//        mIsFullScreenNow = !mIsFullScreenNow;
//      }
//    } catch (Exception e) {
//      Log.e(TAG, e.getMessage());
//      onClick(view);
//    }
  }

  public void onHiddenChanged(boolean hidden) {
//    if (mIsFullScreenNow) {
//      LinearLayout fullLayout = mRootView.get().findViewById(R.id.metabolt_full_view_container);
//      if (hidden) {
//        fullLayout.setVisibility(View.GONE);
//      } else {
//        fullLayout.setVisibility(View.VISIBLE);
//        View childView = fullLayout.getChildAt(0);
//        ((MTBAvatarView)childView).refresh();
//      }
//    } else {
//      controlOtherView(hidden);
//      if (!hidden) {
//        for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
//          MTBAvatarView view = entry.getValue();
//          view.setVisibility(View.VISIBLE);
//          view.refresh();
//        }
//      }
//    }
  }

  public void resumeView() {
//    if (mIsFullScreenNow) {
//      controlOtherView(true);
//      LinearLayout fullLayout = mRootView.get().findViewById(R.id.metabolt_full_view_container);
//      fullLayout.setVisibility(View.VISIBLE);
//    } else {
//      controlOtherView(false);
//      for (Map.Entry<Integer, MTBAvatarView> entry : mAvatarViewMap.entrySet()) {
//        MTBAvatarView view = entry.getValue();
//        view.setVisibility(View.VISIBLE);
//        view.refresh();
//      }
//    }
  }
}
