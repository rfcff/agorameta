package io.agora.api.example.utils;

public interface IMetaFragmentHandler {
  void onStateMsgCallback(String msg);
  void onAudioSEIData(byte[] data);
}
