package com.joyy.metabolt.example.utils;

public interface IMetaSEIHandler {
  void onEmotionBinData(byte[] data);
  void onDanceBinData(byte[] data);
  void onBetaBinData(byte[] data);
}
