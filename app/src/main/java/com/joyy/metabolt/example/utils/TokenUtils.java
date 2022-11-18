package com.joyy.metabolt.example.utils;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadLocalRandom;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TokenUtils {
  private final String TAG = "TokenUtils";
  private final String URL_V2 = "http://webapi.jocloud.com/webservice/app/v2/auth/genToken";
  private final String AGORA_URL = "http://avatar-token.duowan.com/avatartoken/agora";
  private final String TRTC_URL = "http://avatar-token.duowan.com/avatartoken/trtc";
  private TokenUtils.OnTokenListener mTokenListener;
  private int mReqType;

  private static TokenUtils inst = new TokenUtils();
  public static TokenUtils instance() {
    return inst;
  }

  public void requestTokenV2(Activity activity,
                             String appId,
                             String uid,
                             String channelName,
                             int validTime,
                             TokenUtils.OnTokenListener listener) {
    final WeakReference<Activity> activityWeakReference = new WeakReference<>(activity);
    if (null != listener) {
      mTokenListener = listener;
    }

    OkHttpClient client = new OkHttpClient();
    JSONObject jasonObj = new JSONObject();
    try {
      jasonObj.put("appId", appId);
      jasonObj.put("uid", uid);
      jasonObj.put("channelName", channelName);
      jasonObj.put("validTime", validTime);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    RequestBody body =  RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jasonObj.toString());
    Request request = new Request.Builder()
        .url(URL_V2)
        .post(body)
        .build();

    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        e.printStackTrace();
        final Activity activity = activityWeakReference.get();
        if (activity != null) {
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mTokenListener.onRequestTokenResult(-1, UserConfig.METABOLT_INIT_TYPE_THUNDERBOLT, "", "请求Token失败,请检查网络!");
            }
          });
        }
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        String message = "";
        if (response.isSuccessful()) {
          if (response.code() == 200) {
            String result = response.body().string();
            if (!TextUtils.isEmpty(result)) {
              try {
                JSONObject object = new JSONObject(result);
                int code = object.getInt("code");
                message = object.getString("message");
                boolean success = object.getBoolean("success");

                Log.i(TAG,"request token code=" + code + " msg=" + message + " success=" + success);
                if (code == 0 && success) {
                  String token = object.getString("object");
                  mTokenListener.onRequestTokenResult(0, UserConfig.METABOLT_INIT_TYPE_THUNDERBOLT, token, "success");
                  return;
                } else {
                  UserConfig.kMetaToken = "";
                }
              } catch (JSONException e) {
                e.printStackTrace();
              }
            }

            final Activity activity = activityWeakReference.get();
            final String errMessage = message;
            if (activity != null) {
              activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  mTokenListener.onRequestTokenResult(-1, UserConfig.METABOLT_INIT_TYPE_THUNDERBOLT, "", "请求Token失败,message:" + errMessage);
                }
              });
            }
          } else {
            UserConfig.kMetaToken = "";
            mTokenListener.onRequestTokenResult(-1, UserConfig.METABOLT_INIT_TYPE_THUNDERBOLT, "", "request token fail code=" + response.code());
            Log.e(TAG, "request token fail code=" + response.code());
          }
        }
      }
    });
  }


  public void requestExternalToken(Activity activity,
                                   String appId,
                                   String uid,
                                   String channelName,
                                   String certificate,
                                   int type,
                                   TokenUtils.OnTokenListener listener) {
    final WeakReference<Activity> activityWeakReference = new WeakReference<>(activity);
    if (null != listener) {
      mTokenListener = listener;
      mReqType = type;
    }

    int traceId = ThreadLocalRandom.current().nextInt(10000000, 99999999);
    OkHttpClient client = new OkHttpClient();
    JSONObject jasonObj = new JSONObject();
    try {
      jasonObj.put("appId", appId);
      jasonObj.put("uid", uid);
      if (UserConfig.METABOLT_INIT_TYPE_AGORA == type) {
        jasonObj.put("channelName", channelName);
      }
      jasonObj.put("appCertificate", certificate);
      jasonObj.put("trace", traceId);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    Log.i(TAG, "requestExternalToken type:" + type + ", param:" + jasonObj.toString());
    String servUrl = AGORA_URL;
    if (UserConfig.METABOLT_INIT_TYPE_TRTC == type) {
      servUrl = TRTC_URL;
    }
    RequestBody body =  RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jasonObj.toString());
    Request request = new Request.Builder()
        .url(servUrl)
        .post(body)
        .build();

    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        e.printStackTrace();
        final Activity activity = activityWeakReference.get();
        if (activity != null) {
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mTokenListener.onRequestTokenResult(-1, mReqType, "", "请求Token失败,请检查网络!");
            }
          });
        }
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        String message = "";
        if (response.isSuccessful()) {
          if (response.code() == 200) {
            String result = response.body().string();
            if (!TextUtils.isEmpty(result)) {
              try {
                JSONObject object = new JSONObject(result);
                int code = object.getInt("code");
                int traceId = object.getInt("traceId");
                message = object.getString("message");

                Log.i(TAG,"request token " + mReqType + " code=" + code + " traceId=" + traceId + ", msg=" + message);
                if (code == 0) {
                  String token = object.getString("token");
                  mTokenListener.onRequestTokenResult(0, mReqType, token, "success");
                  return;
                } else {
                  Log.e(TAG, "request token " + mReqType + " code failed");
                }
              } catch (JSONException e) {
                e.printStackTrace();
              }
            }

            final Activity activity = activityWeakReference.get();
            final String errMessage = message;
            if (activity != null) {
              activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  mTokenListener.onRequestTokenResult(-1, mReqType, "", "请求Token失败,message:" + errMessage);
                }
              });
            }
          } else {
            UserConfig.kMetaToken = "";
            mTokenListener.onRequestTokenResult(-1, mReqType, "", "request token fail code=" + response.code());
            Log.e(TAG, "request token fail code=" + response.code());
          }
        }
      }
    });
  }

  public interface OnTokenListener {
    /**
     * 请求token结果
     */
    void onRequestTokenResult(int code, int type, String token, String extra);
  }
}

