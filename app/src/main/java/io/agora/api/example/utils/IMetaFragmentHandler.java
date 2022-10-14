package io.agora.api.example.utils;

import java.nio.ByteBuffer;

public interface IMetaFragmentHandler {
  void onStateMsgCallback(String msg);
  int onAudioSEIData(ByteBuffer byteBuffer);
  long getMusicPlayCurrentProgress();
  long getMusicPlayTotalProgress();
}
