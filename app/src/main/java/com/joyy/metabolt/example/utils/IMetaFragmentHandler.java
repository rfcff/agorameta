package com.joyy.metabolt.example.utils;

import java.nio.ByteBuffer;

public interface IMetaFragmentHandler {
  void onMetaBoltState(int state);
  void onStateMsgCallback(String msg);
  int onAudioSEIData(ByteBuffer byteBuffer);
  long getMusicPlayCurrentProgress();
  long getMusicPlayTotalProgress();
}
