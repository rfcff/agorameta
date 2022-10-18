package io.agora.api.example.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionUtils {
  /**
   * 检测运行时权限
   */
  public static void checkPermissionAllGranted(Activity activity) {
    String[] permissions = new String[]{
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE
    };
    List<String> mPermissionList = new ArrayList<>();

    for (int i = 0; i < permissions.length; i++) {
      if (ContextCompat.checkSelfPermission(activity, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
        mPermissionList.add(permissions[i]);
      }
    }
    if (!mPermissionList.isEmpty()) {//未授予的权限为空，表示都授予了，//请求权限方法
      String[] reqPermissions = mPermissionList.toArray(new String[mPermissionList.size()]);//将List转为数组
      ActivityCompat.requestPermissions(activity, reqPermissions, 2);
      // requestPermissions
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
    }
  }

}
