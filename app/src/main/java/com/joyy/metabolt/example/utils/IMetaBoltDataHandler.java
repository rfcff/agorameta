package com.joyy.metabolt.example.utils;

public interface IMetaBoltDataHandler {
  void handlerCaptureAudioData(byte[] data, int dataSize, int sampleRate, int channel, boolean vad);
  void handleRecvMediaExtraInfo(String uid, byte[] data, int dataLen);

  void onJoinRoomSuccess(String channel, String uid, int elapsed);
  void onLocalAudioStatusChanged(int status, int errorReason);
  void onLocalAudioPublishStatus(int status);
  void onLeaveRoom();
  void onUserOffline(String uid, int reason);
  void onRemoteAudioStopped(String uid, boolean stop);
}
