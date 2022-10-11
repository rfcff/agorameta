package io.agora.api.example.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class DownloadUtil {
  private final static String TAG = "DownloadUtil";

  private static DownloadUtil downloadUtil;
  private final OkHttpClient okHttpClient;
  private final String kDownloadPath = "download";
  private List<String> mTasks = new ArrayList<>();
  private ConcurrentLinkedQueue<OnDownloadListener> mDownloadListener = new ConcurrentLinkedQueue<>();

  public void addDownloadListener(OnDownloadListener listener) {
    if (listener != null) {
      mDownloadListener.add(listener);
    }
  }

  public void removeDownloadListener(OnDownloadListener listener) {
    if (listener != null) {
      mDownloadListener.remove(listener);
    }
  }

  public static DownloadUtil get() {
    if (downloadUtil == null) {
      downloadUtil = new DownloadUtil();
    }
    return downloadUtil;
  }

  private DownloadUtil() {
    okHttpClient = new OkHttpClient();
  }

  public void clearDownloads(Context context) {
    String savePath = context.getExternalFilesDir(kDownloadPath).getAbsolutePath();
    File file = new File(savePath);
    if (file.exists()) {
      deleteFile(file);
    }
  }

  public String getDownloadPath(Context context) {
    String savePath = context.getExternalFilesDir(kDownloadPath).getAbsolutePath();
    return savePath;
  }

  public void delete(Context context, String url) {
    String savePath = context.getExternalFilesDir(kDownloadPath).getAbsolutePath();
    File file = new File(savePath, getNameFromUrl(url));
    if (file.exists()) {
      deleteFile(file);
    }
  }

  public static void deleteFile(File file) {
    if (file.isFile()) {
      file.delete();
      return;
    }

    if (file.isDirectory()) {
      File[] childFiles = file.listFiles();
      if (childFiles == null || childFiles.length == 0) {
        file.delete();
        return;
      }

      for (File childFile : childFiles) {
        deleteFile(childFile);
      }
      file.delete();
    }
  }

  public void download(Context context, final String url) {
    download(context, url, null);
  }

  /**
   * @param url      下载连接
   * @param listener 下载监听
   */
  public void download(Context context, final String url, final OnDownloadListener listener) {
    if (android.text.TextUtils.isEmpty(url)) {
      if (listener != null) {
        listener.onDownloadFailed(url);
      }

      for (OnDownloadListener l : mDownloadListener) {
        l.onDownloadFailed(url);
      }
      Log.i(TAG, "download is  url is empty");
      return;
    }

    String cachePath = isExist(context, url);
    if (!TextUtils.isEmpty(cachePath)) {
      if (listener != null) {
        listener.onDownloadSuccess(url, cachePath, true);
      }

      for (OnDownloadListener l : mDownloadListener) {
        l.onDownloadSuccess(url, cachePath, true);
      }
      Log.i(TAG, "download is  cached url =" + url + ",cachePath = " + cachePath);
      return;
    }

    if (mTasks.contains(url)) {
      Log.i(TAG, "download is  running url =" + url);
      return;
    }

    Request request = new Request.Builder().url(url).build();
    okHttpClient.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NotNull Call call, @NotNull IOException e) {
        mTasks.remove(url);
        delete(context, url);
        // 下载失败
        if (listener != null) {
          listener.onDownloadFailed(url);
        }
        for (OnDownloadListener l : mDownloadListener) {
          l.onDownloadFailed(url);
        }
      }

      @Override
      public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
        InputStream is = null;
        byte[] buf = new byte[2048];
        int len = 0;
        FileOutputStream fos = null;
        // 储存下载文件的目录
        String savePath = context.getExternalFilesDir(kDownloadPath).getAbsolutePath();
        try {
          is = response.body().byteStream();
          long total = response.body().contentLength();
          File file = new File(savePath, getNameFromUrl(url));
          fos = new FileOutputStream(file);
          long sum = 0;
          while ((len = is.read(buf)) != -1) {
            fos.write(buf, 0, len);
            sum += len;
            int progress = (int) (sum * 1.0f / total * 100);
            // 下载中
            if (listener != null) {
              listener.onDownloading(progress);
            }
          }
          fos.flush();
          // 下载完成
          if (listener != null) {
            listener.onDownloadSuccess(url, file.getAbsolutePath(), false);
          }
          for (OnDownloadListener l : mDownloadListener) {
            l.onDownloadSuccess(url, file.getAbsolutePath(), false);
          }
        } catch (Exception e) {
          Log.e(TAG, "download", e);
          delete(context, url);
          if (listener != null) {
            listener.onDownloadFailed(url);
            for (OnDownloadListener l : mDownloadListener) {
              l.onDownloadFailed(url);
            }
          }
        } finally {
          mTasks.remove(url);
          try {
            if (is != null)
              is.close();
          } catch (IOException e) {
            Log.e(TAG, "download", e);
          }
          try {
            if (fos != null)
              fos.close();
          } catch (IOException e) {
            Log.e(TAG, "download", e);
          }
        }
      }
    });
  }

  private String isExist(Context context, final String url) {
    // 储存下载文件的目录
    String savePath = context.getExternalFilesDir(kDownloadPath).getAbsolutePath();
    File file = new File(savePath, getNameFromUrl(url));
    if (file.exists()) {
      return file.getAbsolutePath();
    }
    return null;
  }

  /**
   * @param url
   * @return 从下载连接中解析出文件名
   */
  @NonNull
  private String getNameFromUrl(String url) {
    return url.substring(url.lastIndexOf("/") + 1);
  }

  public interface OnDownloadListener {
    /**
     * 下载成功
     */
    void onDownloadSuccess(String url, String path, boolean isExist);

    /**
     * @param progress 下载进度
     */
    void onDownloading(int progress);

    /**
     * 下载失败
     */
    void onDownloadFailed(String ur);
  }
}
