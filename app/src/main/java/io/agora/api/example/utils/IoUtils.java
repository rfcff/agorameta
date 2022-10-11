package io.agora.api.example.utils;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author zhaochong
 * @desc TODO
 * @date on 2019-06-08
 * @email zoro.zhaochong@gmail.com
 */

public class IoUtils {

  public IoUtils() {
    throw new AssertionError();
  }


  /**
   * Close closable object and wrap {@link IOException} with {@link RuntimeException}
   * @param closeable closeable object
   */
  /**
   * 关闭IO
   *
   * @param closeables closeables
   */
  public static void close(Closeable... closeables) {
    if (closeables == null) return;
    for (Closeable closeable : closeables) {
      if (closeable != null) {
        try {
          closeable.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Close closable and hide possible {@link IOException}
   *
   * @param closeable closeable object
   */
  public static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

}