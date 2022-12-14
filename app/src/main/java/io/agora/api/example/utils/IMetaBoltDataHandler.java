package io.agora.api.example.utils;

public interface IMetaBoltDataHandler {
  void handlerCaptureAudioData(byte[] data, int dataSize, int sampleRate, int channel, boolean vad);
  void handleCaptureVideoFrame(int width, int height, byte[] data, int imageFormat, boolean isHorizontalFlip, int rotation);
  void handleRecvMediaExtraInfo(String uid, byte[] data, int dataLen);

  void onJoinRoomSuccess(String channel, String uid, int elapsed);
  void onLocalAudioStatusChanged(int status, int errorReason);
  void onLocalAudioPublishStatus(int status);
  void onLeaveRoom();
  void onUserOffline(String uid, int reason);
  void onRemoteAudioStopped(String uid, boolean stop);
}
