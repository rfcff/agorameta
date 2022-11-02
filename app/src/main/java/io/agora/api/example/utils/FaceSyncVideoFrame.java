package io.agora.api.example.utils;

public class FaceSyncVideoFrame {
  int imageFormat; //视频图片格式 {@link android.graphics.ImageFormat}
  byte[] videoData; //视频数据
  int width;
  int height;
  int rotation; //视频数据顺时针旋转角度。竖向站立为0度，取值：0，90，180，270。
  boolean isHorizontalFlip; //是否镜像翻转

  public FaceSyncVideoFrame(int imageFormat, byte[] videoData, int width, int height, int rotation, boolean isHorizontalFlip) {
    this.imageFormat = imageFormat;
    this.videoData = videoData;
    this.width = width;
    this.height = height;
    this.height = height;
    this.rotation = rotation;
    this.isHorizontalFlip = isHorizontalFlip;
  }
}
