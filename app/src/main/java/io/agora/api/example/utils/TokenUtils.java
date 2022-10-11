package io.agora.api.example.utils;


import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TokenUtils {
  public static String TAG = "TokenUtils";
  public static String URL = "http://webapi.jocloud.com/app/auth/genToken";
  public static String URL_V2 = "http://webapi.jocloud.com/webservice/app/v2/auth/genToken";
  private TokenUtils.OnTokenListener mTokenListener;

  private static TokenUtils inst = new TokenUtils();
  public static TokenUtils instance() {
    return inst;
  }

  public void requestToken(Activity activity, String appId, String uid, int validTime) {
    final WeakReference<Activity> activityWeakReference = new WeakReference<>(activity);

    OkHttpClient client = new OkHttpClient();
    JSONObject jasonObj = new JSONObject();
    try {
      jasonObj.put("appId", appId);
      jasonObj.put("uid", uid);
      jasonObj.put("validTime", validTime);
      jasonObj.put("extraInfo", "");
    } catch (JSONException e) {
      e.printStackTrace();
    }
    RequestBody body =  RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jasonObj.toString());
    Request request = new Request.Builder()
        .url(URL)
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
              Toast.makeText(activity, "请求Token失败，请检查网络！", Toast.LENGTH_SHORT).show();
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
                if (code == 0 && success) {
                  String token = object.getString("object");
                  UserConfig.kToken = token;
                  return;
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
                  Toast.makeText(activity, "请求Token失败，message = " + errMessage, Toast.LENGTH_SHORT).show();
                }
              });
            }
          }
        }
      }
    });
  }

  public void requestTokenV2(Activity activity, String appId, String uid, String channelName, int validTime) {
    final WeakReference<Activity> activityWeakReference = new WeakReference<>(activity);

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
              Toast.makeText(activity, "请求Token失败，请检查网络！", Toast.LENGTH_SHORT).show();
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
                  UserConfig.kToken = token;
                  return;
                } else {
                  UserConfig.kToken = "";
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
                  Toast.makeText(activity, "请求Token失败，message = " + errMessage, Toast.LENGTH_SHORT).show();
                }
              });
            }
          } else {
            UserConfig.kToken = "";
            Log.e(TAG, "request token fail code=" + response.code());
          }
        }
      }
    });
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
              mTokenListener.onRequestTokenResult(-1, "", "请求Token失败,请检查网络!");
              //Toast.makeText(activity, "请求Token失败，请检查网络！", Toast.LENGTH_SHORT).show();
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
                  UserConfig.kToken = token;
                  mTokenListener.onRequestTokenResult(0, token, "success");
                  return;
                } else {
                  UserConfig.kToken = "";
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
                  mTokenListener.onRequestTokenResult(-1, "", "请求Token失败,message:" + errMessage);
                  //Toast.makeText(activity, "请求Token失败，message = " + errMessage, Toast.LENGTH_SHORT).show();
                }
              });
            }
          } else {
            UserConfig.kToken = "";
            mTokenListener.onRequestTokenResult(-1, "", "request token fail code=" + response.code());
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
    void onRequestTokenResult(int code, String token, String extra);
  }
}

