package io.agora.api.example.utils;

public interface IMetaBoltDataHandler {
  void handlerCaptureAudioData(byte[] data, int dataSize, int sampleRate, int channel, boolean vad);
  void handleRecvMediaExtraInfo(String uid, byte[] data, int dataLen);
  void onJoinRoomSuccess(String channel, String uid, int elapsed);
}
