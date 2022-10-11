package io.agora.api.example.utils;

public interface MetaBoltMgrCallback {
  void onJoinRoomSuccess(String channel, String uid, int elapsed);
  void onMetaBoltServiceStateChanged(int state);
}
