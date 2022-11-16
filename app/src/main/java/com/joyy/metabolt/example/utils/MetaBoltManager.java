package com.joyy.metabolt.example.utils;

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
import com.joyy.metabolt.example.R;
import com.thunder.livesdk.ThunderRtcConstant;


public class MetaBoltManager extends MTBServiceEventHandler implements  IMTBLogCallback,
    IMetaBoltDataHandler,
    MTBTrackEngine.TrackFaceEmotionHandler,
    MTBTrackEngine.TrackMusicBeatHandler,
    MTBTrackEngine.TrackMusicDanceHandler,
    Handler.Callback {
  private static final String TAG = "MetaBoltManager";
  public static final int kMaxViewNum = 6;

  private Handler mHandler = null;
  private final int kHandlerDelayMs = 33; // 需要比25fps稍微快一点，不然发SEI可能会略低一点

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
    UserConfig.kMetaAIModelPath = mAIModelPath;
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
      Log.e(TAG, "init config failed:" + res + ", model path:" + mAIModelPath);
      mMetaBoltSrv.removeMetaBoltObserver(this);
      mMetaBoltSrv.setLogCallback(null);
      mMetaBoltSrv = null;
      return res;
    }
    Log.i(TAG, "metaBolt sdk version: " + mMetaBoltSrv.getMetaBoltVersion() + ", model path: " + mAIModelPath);
    initHandleSend();
    startHandler();
    return res;
  }

  public int deInit() {
    if (!isInit()) {
      return -1;
    }
    deInitHandleSend();
    stopHandler();
    for (Map.Entry<Integer, MTBAvatarView> viewEntry : mAvatarViewMap.entrySet()) {
      FrameLayout layout = mRootView.get().findViewById(getAvatarContainerViewIdByIndex(viewEntry.getKey()));
      layout.setBackgroundColor(Color.WHITE);
      layout.removeView(viewEntry.getValue());
      mMetaBoltSrv.destroyAvatarView(viewEntry.getValue());
    }

    for (Map.Entry<String, MTBAvatarRole> roleEntry : mAvatarRoleMap.entrySet()) {
      mMetaBoltSrv.destroyAvatarRole(roleEntry.getValue());
    }

    mAvatarRoleMap.clear();
    mAvatarViewMap.clear();


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

  private boolean mIsOpenVideoEmotion = false;
  public int startFaceEmotionByCamera() {
    Log.i(TAG, "startFaceEmotionByCamera");
    mIsOpenVideoEmotion = true;
    return mMetaBoltSrv.getTrackEngine().startTrackFaceEmotion(MTBTrackEngine.MTTrackMode.MT_TRACK_MODE_VIDEO, this::onRecvTrackEmotionInfo);
  }

  public int stopFaceEmotion() {
    Log.i(TAG, "stopFaceEmotion");
    int ret = mMetaBoltSrv.getTrackEngine().stopTrackFaceEmotion();
    mIsOpenVideoEmotion = false;
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
    if (status == ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_STOPPED ||
        status == ThunderRtcConstant.LocalAudioStreamStatus.THUNDER_LOCAL_AUDIO_STREAM_STATUS_FAILED) {
      if (isInit()) {
        MTBAvatarRole myRole = mAvatarRoleMap.get(UserConfig.kMetaUid);
        if (myRole != null && (mIsOpenAudioEmotion || mIsOpenVideoEmotion)) {
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

    if (stop) {
      MTBAvatarRole role = mAvatarRoleMap.get(uid);
      if (role != null) {
        role.resetFaceEmotion();
        role.resetMusicBeat();
        role.resetMusicDance();
      }
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
      mMetaFragmentHandler.get().onMetaBoltState(state);
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

  private final Object mDataLock = new Object();
  private MTBFaceEmotion mFaceEmotion = null;
  private MTBMusicBeatInfo mBeatInfo = null;
  private MTBMusicDanceInfo mDanceInfo = null;

  @Override
  public int onRecvTrackEmotionInfo(MTBFaceEmotion info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kMetaUid);
    if (role != null) {
      role.setFaceEmotion(info);
    }
    synchronized (mDataLock) {
      mFaceEmotion = info;
    }
    return 0;
  }

  @Override
  public int onRecvTrackBetaInfo(MTBMusicBeatInfo info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kMetaUid);
    if (role != null) {
      role.setMusicBeat(info);
    }
    synchronized (mDataLock) {
      mBeatInfo = info;
    }
    return 0;
  }

  @Override
  public int onRecvTrackDanceInfo(MTBMusicDanceInfo info) {
    MTBAvatarRole role = mAvatarRoleMap.get(UserConfig.kMetaUid);
    if (role != null) {
      role.setMusicDance(info);
    }
    synchronized (mDataLock) {
      mDanceInfo = info;
    }
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
    if (null != mMetaBoltSrv
        && null != mMetaBoltSrv.getTrackEngine()
        && MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS == mMetaBoltSrv.getMetaBoltServiceState()) {
      mMetaBoltSrv.getTrackEngine().applyAudioPCMData(data, channel, sampleRate, 16, vad);
    }
  }

  byte[] nv21AlignBytes;
  private void nv21BytesAlignTest(byte[] nv21Bytes, int width, int height, boolean isHorizontalFlip, int rotation) {
    int size = (width + 16) * height * 3 / 2;
    if (nv21AlignBytes == null || nv21AlignBytes.length != size) {
      nv21AlignBytes = new byte[size];
    }
    for (int i = 0; i < height / 2; i++) {
      System.arraycopy(nv21Bytes, i * width, nv21AlignBytes, i * (width + 16), width);
      System.arraycopy(nv21Bytes, width * height + i * width, nv21AlignBytes, (width + 16) * height + i * (width + 16), width);
    }
    for (int i = height / 2; i < height; i++) {
      System.arraycopy(nv21Bytes, i * width, nv21AlignBytes, i * (width + 16), width);
    }
    MetaBoltTypes.MTBVideoFrame videoFrame = new MetaBoltTypes.MTBVideoFrame();
    videoFrame.initFromNV21Data(nv21AlignBytes, width + 16, width, height);
    mMetaBoltSrv.getTrackEngine().applyVideoData(videoFrame, isHorizontalFlip, rotation);
  }

  byte[] nv21YBytes;
  byte[] nv21VUBytes;
  private void nv21BytesPlanesTest(byte[] nv21Bytes, int width, int height, boolean isHorizontalFlip, int rotation) {
    int yStride = width;
    int vuStride = width;
    int ySize = yStride * height;
    int vuSize = vuStride * height / 2;
    if (nv21YBytes == null || nv21YBytes.length != ySize) {
      nv21YBytes = new byte[ySize];
    }
    if (nv21VUBytes == null || nv21VUBytes.length != vuSize) {
      nv21VUBytes = new byte[vuSize];
    }
    for (int i = 0; i < height; i++) {
      System.arraycopy(nv21Bytes, i * width, nv21YBytes, i * yStride, width);
    }
    for (int i = 0; i < height / 2; i++) {
      System.arraycopy(nv21Bytes, width * height + i * width, nv21VUBytes,  i * vuStride, width);
    }
    MetaBoltTypes.MTBVideoFrame videoFrame = new MetaBoltTypes.MTBVideoFrame();
    videoFrame.initFromNV21PlanesData(nv21YBytes, yStride, nv21VUBytes, vuStride, width, height);
    mMetaBoltSrv.getTrackEngine().applyVideoData(videoFrame, isHorizontalFlip, rotation);
  }

  byte[] i420Bytes;
  private void i420BytesTest(byte[] nv21Bytes, int width, int height, boolean isHorizontalFlip, int rotation) {
    int yStride = width;
    int uStride = width;
    int vStride = width;
    int size = yStride * height + uStride * height / 2 + vStride * height / 2;
    if (i420Bytes == null || i420YBytes.length != size) {
      i420YBytes = new byte[size];
    }

//        ImageUtil.nv21TransToI420(nv21Bytes, width, height, 0, false, i420Bytes);

    MetaBoltTypes.MTBVideoFrame videoFrame = new MetaBoltTypes.MTBVideoFrame();
//        videoFrame.initFromI420PlanesData(i420Bytes, yStride, width, height);
    mMetaBoltSrv.getTrackEngine().applyVideoData(videoFrame, isHorizontalFlip, rotation);
  }

  byte[] i420YBytes;
  byte[] i420UBytes;
  byte[] i420VBytes;
  private void i420BytesPlanesTest(byte[] nv21Bytes, int width, int height, boolean isHorizontalFlip, int rotation) {
    int yStride = width;
    int uStride = width;
    int vStride = width;
    int ySize = yStride * height;
    int uSize = uStride * height / 2;
    int vSize = vStride * height / 2;

    if (i420YBytes == null || i420YBytes.length != ySize) {
      i420YBytes = new byte[ySize];
    }
    if (i420UBytes == null || i420UBytes.length != uSize) {
      i420UBytes = new byte[uSize];
    }
    if (i420VBytes == null || i420VBytes.length != vSize) {
      i420VBytes = new byte[vSize];
    }

//        ImageUtil.nv21TransToI420(nv21Bytes, width, height, 0, false, i420YBytes, i420UBytes, i420VBytes);

    MetaBoltTypes.MTBVideoFrame videoFrame = new MetaBoltTypes.MTBVideoFrame();
//        videoFrame.initFromI420PlanesData(i420YBytes, yStride, i420UBytes, uStride, i420VBytes, vStride, width, height);
    mMetaBoltSrv.getTrackEngine().applyVideoData(videoFrame, isHorizontalFlip, rotation);
  }

  @Override
  public void handleCaptureVideoFrame(int width, int height, byte[] data, int imageFormat, boolean isHorizontalFlip,
                                      int rotation) {
    if (mMetaBoltSrv != null &&
        mMetaBoltSrv.getMetaBoltServiceState() == MetaBoltTypes.MTBServiceState.MTB_STATE_INIT_SUCCESS) {

      MetaBoltTypes.MTBVideoFrame videoFrame = new MetaBoltTypes.MTBVideoFrame();
      videoFrame.initFromNV21Data(data, width, height);
      mMetaBoltSrv.getTrackEngine().applyVideoData(videoFrame, isHorizontalFlip, rotation);

//            //nv21数据对齐输入测试
//            nv21BytesAlignTest(data, width, height, isHorizontalFlip, rotation);
//            //nv21数据多plane输入测试
//            nv21BytesPlanesTest(data, width, height, isHorizontalFlip, rotation);
//            //I420数据测试
//            i420BytesPlanesTest(data, width, height, isHorizontalFlip, rotation);
//            i420BytesTest(data, width, height, isHorizontalFlip, rotation);
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
  public final static String kLipsyncFlagString = "lipsync";
  private final static byte[] kLipsyncFlagByteBuffer = kLipsyncFlagString.getBytes();
  public final static int kSEIStartLen = kLipsyncFlagByteBuffer.length;

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
  private void initHandleSend() {
    mSendMediaSEIMap.put(kBlendShapeType, 0);
    mSendMediaSEIMap.put(kDanceType, 0);
    mSendMediaSEIMap.put(kBeatType, 0);
  }

  private void deInitHandleSend() {
    mSendMediaSEIMap.clear();
    mSendMediaExtraInfoBuffer.clear();
  }

  private final int kThunderMaxLengthOfSEI = 500; // 500 is max SEI length of audio opus dse
  private final ByteBuffer mSendMediaExtraInfoBuffer = ByteBuffer.allocate(kThunderMaxLengthOfSEI);
  public void handleSendMediaExtraInfo() {
    if (mAudioPublishStatus == ThunderRtcConstant.ThunderLocalAudioPublishStatus.THUNDER_LOCAL_AUDIO_PUBLISH_STATUS_START) {
      if ((mIsOpenVideoEmotion || mIsOpenAudioEmotion || mIsOpenDance || mIsOpenBeat)) {
        synchronized (mDataLock) {
          if (mFaceEmotion == null && mDanceInfo == null && mBeatInfo == null) {
            return;
          }
        }

        int bufferLen = kSEIStartLen;
        int typeLen = 3;

        byte[] emotionData = null;
        byte[] danceData = null;
        byte[] beatData = null;
        synchronized (mDataLock) {
          if (mFaceEmotion != null) {
            emotionData = mFaceEmotion.serializeToBin();
            bufferLen += emotionData.length + typeLen;
            mFaceEmotion = null;
          }

          if (mDanceInfo != null) {
            danceData = mDanceInfo.serializeToBin();
            bufferLen += danceData.length + typeLen;
            mDanceInfo = null;
          }

          if (mBeatInfo != null) {
            beatData = mBeatInfo.serializeToBin();
            bufferLen += beatData.length + typeLen;
            mBeatInfo = null;
          }
        }

        if (bufferLen > kThunderMaxLengthOfSEI) {
          Log.e(TAG, "handleSendMediaExtraInfo send SEI failed, buffer len: " + bufferLen + ", is supper than max len: " + kThunderMaxLengthOfSEI);
          return;
        }

        mSendMediaExtraInfoBuffer.clear();
        mSendMediaExtraInfoBuffer.limit(bufferLen);
        mSendMediaExtraInfoBuffer.put(kLipsyncFlagByteBuffer);

        int type = -1;
        if (emotionData != null) {
          type = kBlendShapeType;
          mSendMediaExtraInfoBuffer.put((byte) type);
          mSendMediaExtraInfoBuffer.put(ByteUtil.integerToTwoBytes(emotionData.length));
          mSendMediaExtraInfoBuffer.put(emotionData);
          mSendMediaSEIMap.put(type, mSendMediaSEIMap.get(type) + 1);
        }

        if (danceData != null) {
          type = kDanceType;
          mSendMediaExtraInfoBuffer.put((byte) type);
          mSendMediaExtraInfoBuffer.put(ByteUtil.integerToTwoBytes(danceData.length));
          mSendMediaExtraInfoBuffer.put(danceData);
          mSendMediaSEIMap.put(type, mSendMediaSEIMap.get(type) + 1);
        }

        if (beatData != null) {
          type = kBeatType;
          mSendMediaExtraInfoBuffer.put((byte) type);
          mSendMediaExtraInfoBuffer.put(ByteUtil.integerToTwoBytes(beatData.length));
          mSendMediaExtraInfoBuffer.put(beatData);
          mSendMediaSEIMap.put(type, mSendMediaSEIMap.get(type) + 1);
        }

        int sendBufferLen = mSendMediaExtraInfoBuffer.limit();
        if (sendBufferLen > kSEIStartLen) {
          int ret = mMetaFragmentHandler.get().onAudioSEIData(mSendMediaExtraInfoBuffer);
          if (ret != 0 || sendBufferLen > kThunderMaxLengthOfSEI) {
            Log.e(TAG, "send SEI failed, ret: " + ret + ", length: " + sendBufferLen);
          } else {
            long currentTimestamp = System.currentTimeMillis();
            if (mLastSendSEITimestamp == 0) {
              mLastSendSEITimestamp = currentTimestamp;
            } else {
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

    if (isInit()) {
      byte[] startData = ByteUtil.subByte(data, 0, kSEIStartLen);
      String startStr = new String(startData, StandardCharsets.UTF_8);
      if (!startStr.equalsIgnoreCase(kLipsyncFlagString)) {
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
          return;
        }
        remnantLen = remnantLen - typeLine - parseDataLen;
        offsetLen = offsetLen + parseDataLen;
        revMediaInfoPair.second.put(trackType, revMediaInfoPair.second.get(trackType) + 1);
      }

      if (emotionData != null || beatData != null || danceData != null) {
        MTBAvatarRole role = mAvatarRoleMap.get(uid);
        if (role != null) {
          if (emotionData != null) {
            MTBFaceEmotion emotion = new MTBFaceEmotion();
            int ret = emotion.unserializeFromBin(emotionData);
            if (ret == MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
              role.setFaceEmotion(emotion);
            } else {
              Log.e(TAG, "emotion info unserialize from bin failed, data: " + Base64.encodeToString(emotionData, Base64.NO_WRAP));
            }
          }

          if (danceData != null) {
            MTBMusicDanceInfo danceInfo = new MTBMusicDanceInfo();
            int ret = danceInfo.unserializeFromBin(danceData);
            if (ret == MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
              role.setMusicDance(danceInfo);
            } else {
              Log.e(TAG, " dance info unserialize from bin failed, data: " + Base64.encodeToString(danceData, Base64.NO_WRAP));
            }
          }

          if (beatData != null) {
            MTBMusicBeatInfo beatInfo = new MTBMusicBeatInfo();
            int ret = beatInfo.unserializeFromBin(beatData);
            if (ret == MetaBoltTypes.MTBErrorCode.MTB_ERR_SUCCESS) {
              role.setMusicBeat(beatInfo);
            } else {
              Log.e(TAG, " beat info unserialize from bin failed, data: " + Base64.encodeToString(beatData, Base64.NO_WRAP));
            }
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
}