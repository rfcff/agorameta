package io.agora.api.example.utils;

public interface IMetaSEIHandler {
  void onEmotionBinData(byte[] data);
  void onDanceBinData(byte[] data);
  void onBetaBinData(byte[] data);
}
