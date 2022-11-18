package com.joyy.metabolt.example.utils;

public class UserConfig {
  public static final int METABOLT_INIT_TYPE_AGORA = 0; // metabolt借用agora通道
  public static final int METABOLT_INIT_TYPE_TRTC = 1; // metabolt借用trtc通道
  public static final int METABOLT_INIT_TYPE_THUNDERBOLT = 3; // metabolt借用thunderbolt通道

  public static String kChannelId = "010242010";

  public static String kMetaAppId = "1545517873";
  public static String kMetaUid = "";
  public static String kMetaToken = "";
  public static String kMetaAIModelPath = "";

  public static String kTRTCAppId = "1400759077";
  public static String kTRTCCert = "aa3f792564c1cdba3dbe1553c864b3f21b64bd7c9114404f1cb2d6e16e8f9b0c";
  public static String kTRTCToken = "";

  public static String kAgoraAppId = "e730126bbdc0451fbb0598062fe8c3f5";
  public static String kAgoraCert = "1145474754754d79931d865c0ea54451";
  public static String kAgoraToken = "";

  public static int kTokenInvalidTime = 24 * 3600;
}
