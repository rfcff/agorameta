package io.agora.api.example.utils;


import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * @author zhaochong
 * @desc TODO
 * @date on 2019-06-08
 * @email zoro.zhaochong@gmail.com
 */
public class FileUtils {

  private static final String TAG = "FileUtils";
  private FileUtils() {
    throw new UnsupportedOperationException("u can't instantiate me...");
  }

  /**
   * 文件拷贝，原工程方法
   *
   * @param context
   * @param dirPath
   * @param name
   * @param resId
   */
  @Deprecated
  public static void copyFile(Context context, String dirPath, String name, int resId) {
    String filePath = dirPath + "/" + name;
    try {
      File dir = new File(dirPath);
      if (!dir.exists()) {
        dir.mkdirs();
      }

      File file = new File(filePath);
      InputStream is = context.getResources().openRawResource(resId);
      FileOutputStream fs = new FileOutputStream(file);
      byte[] buffer = new byte[1024];
      int count;
      while ((count = is.read(buffer)) > 0) {
        fs.write(buffer, 0, count);
      }
      fs.close();
      is.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static final String LINE_SEP = System.getProperty("line.separator");

  /**
   * 根据文件路径获取文件
   *
   * @param filePath 文件路径
   * @return 文件
   */
  public static File getFileByPath(String filePath) {
    return isSpace(filePath) ? null : new File(filePath);
  }

  /**
   * 判断文件是否存在
   *
   * @param filePath 文件路径
   * @return {@code true}: 存在<br>{@code false}: 不存在
   */
  public static boolean isFileExists(String filePath) {
    return isFileExists(getFileByPath(filePath));
  }

  /**
   * 判断文件是否存在
   *
   * @param file 文件
   * @return {@code true}: 存在<br>{@code false}: 不存在
   */
  public static boolean isFileExists(File file) {
    return file != null && file.exists();
  }

  /**
   * 重命名文件
   *
   * @param filePath 文件路径
   * @param newName  新名称
   * @return {@code true}: 重命名成功<br>{@code false}: 重命名失败
   */
  public static boolean rename(String filePath, String newName) {
    return rename(getFileByPath(filePath), newName);
  }

  /**
   * 重命名文件
   *
   * @param file    文件
   * @param newName 新名称
   * @return {@code true}: 重命名成功<br>{@code false}: 重命名失败
   */
  public static boolean rename(File file, String newName) {
    // 文件为空返回false
    if (file == null) return false;
    // 文件不存在返回false
    if (!file.exists()) return false;
    // 新的文件名为空返回false
    if (isSpace(newName)) return false;
    // 如果文件名没有改变返回true
    if (newName.equals(file.getName())) return true;
    File newFile = new File(file.getParent() + File.separator + newName);
    // 如果重命名的文件已存在返回false
    return !newFile.exists()
        && file.renameTo(newFile);
  }

  /**
   * 判断是否是目录
   *
   * @param dirPath 目录路径
   * @return {@code true}: 是<br>{@code false}: 否
   */
  public static boolean isDir(String dirPath) {
    return isDir(getFileByPath(dirPath));
  }

  /**
   * 判断是否是目录
   *
   * @param file 文件
   * @return {@code true}: 是<br>{@code false}: 否
   */
  public static boolean isDir(File file) {
    return isFileExists(file) && file.isDirectory();
  }

  /**
   * 判断是否是文件
   *
   * @param filePath 文件路径
   * @return {@code true}: 是<br>{@code false}: 否
   */
  public static boolean isFile(String filePath) {
    return isFile(getFileByPath(filePath));
  }

  /**
   * 判断是否是文件
   *
   * @param file 文件
   * @return {@code true}: 是<br>{@code false}: 否
   */
  public static boolean isFile(File file) {
    return isFileExists(file) && file.isFile();
  }

  /**
   * 判断目录是否存在，不存在则判断是否创建成功
   *
   * @param dirPath 目录路径
   * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
   */
  public static boolean createOrExistsDir(String dirPath) {
    return createOrExistsDir(getFileByPath(dirPath));
  }

  /**
   * 判断目录是否存在，不存在则判断是否创建成功
   *
   * @param file 文件
   * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
   */
  public static boolean createOrExistsDir(File file) {
    // 如果存在，是目录则返回true，是文件则返回false，不存在则返回是否创建成功
    return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
  }

  /**
   * 判断文件是否存在，不存在则判断是否创建成功
   *
   * @param filePath 文件路径
   * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
   */
  public static boolean createOrExistsFile(String filePath) {
    return createOrExistsFile(getFileByPath(filePath));
  }

  /**
   * 判断文件是否存在，不存在则判断是否创建成功
   *
   * @param file 文件
   * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
   */
  public static boolean createOrExistsFile(File file) {
    if (file == null) return false;
    // 如果存在，是文件则返回true，是目录则返回false
    if (file.exists()) return file.isFile();
    if (!createOrExistsDir(file.getParentFile())) return false;
    try {
      return file.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * 判断文件是否存在，存在则在创建之前删除
   *
   * @param file 文件
   * @return {@code true}: 创建成功<br>{@code false}: 创建失败
   */
  public static boolean createFileByDeleteOldFile(File file) {
    if (file == null) return false;
    // 文件存在并且删除失败返回false
    if (file.exists() && !file.delete()) return false;
    // 创建目录失败返回false
    if (!createOrExistsDir(file.getParentFile())) return false;
    try {
      return file.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * 复制或移动目录
   *
   * @param srcDirPath  源目录路径
   * @param destDirPath 目标目录路径
   * @param isMove      是否移动
   * @return {@code true}: 复制或移动成功<br>{@code false}: 复制或移动失败
   */
  private static boolean copyOrMoveDir(String srcDirPath, String destDirPath, boolean isMove) {
    return copyOrMoveDir(getFileByPath(srcDirPath), getFileByPath(destDirPath), isMove);
  }

  /**
   * 复制或移动目录
   *
   * @param srcDir  源目录
   * @param destDir 目标目录
   * @param isMove  是否移动
   * @return {@code true}: 复制或移动成功<br>{@code false}: 复制或移动失败
   */
  private static boolean copyOrMoveDir(File srcDir, File destDir, boolean isMove) {
    if (srcDir == null || destDir == null) return false;
    // 如果目标目录在源目录中则返回false，看不懂的话好好想想递归怎么结束
    // srcPath : F:\\MyGithub\\AndroidUtilCode\\utilcode\\src\\test\\res
    // destPath: F:\\MyGithub\\AndroidUtilCode\\utilcode\\src\\test\\res1
    // 为防止以上这种情况出现出现误判，须分别在后面加个路径分隔符
    String srcPath = srcDir.getPath() + File.separator;
    String destPath = destDir.getPath() + File.separator;
    if (destPath.contains(srcPath)) return false;
    // 源文件不存在或者不是目录则返回false
    if (!srcDir.exists() || !srcDir.isDirectory()) return false;
    // 目标目录不存在返回false
    if (!createOrExistsDir(destDir)) return false;
    File[] files = srcDir.listFiles();
    for (File file : files) {
      File oneDestFile = new File(destPath + file.getName());
      if (file.isFile()) {
        // 如果操作失败返回false
        if (!copyOrMoveFile(file, oneDestFile, isMove)) return false;
      } else if (file.isDirectory()) {
        // 如果操作失败返回false
        if (!copyOrMoveDir(file, oneDestFile, isMove)) return false;
      }
    }
    return !isMove || deleteDir(srcDir);
  }

  /**
   * 复制或移动文件
   *
   * @param srcFilePath  源文件路径
   * @param destFilePath 目标文件路径
   * @param isMove       是否移动
   * @return {@code true}: 复制或移动成功<br>{@code false}: 复制或移动失败
   */
  private static boolean copyOrMoveFile(String srcFilePath, String destFilePath, boolean isMove) {
    return copyOrMoveFile(getFileByPath(srcFilePath), getFileByPath(destFilePath), isMove);
  }

  /**
   * 复制或移动文件
   *
   * @param srcFile  源文件
   * @param destFile 目标文件
   * @param isMove   是否移动
   * @return {@code true}: 复制或移动成功<br>{@code false}: 复制或移动失败
   */
  private static boolean copyOrMoveFile(File srcFile, File destFile, boolean isMove) {
    if (srcFile == null || destFile == null) return false;
    // 源文件不存在或者不是文件则返回false
    if (!srcFile.exists() || !srcFile.isFile()) return false;
    // 目标文件存在且是文件则返回false
    if (destFile.exists() && destFile.isFile()) return false;
    // 目标目录不存在返回false
    if (!createOrExistsDir(destFile.getParentFile())) return false;
    try {
      return FileIOUtils.writeFileFromIS(destFile, new FileInputStream(srcFile), false)
          && !(isMove && !deleteFile(srcFile));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * 复制目录
   *
   * @param srcDirPath  源目录路径
   * @param destDirPath 目标目录路径
   * @return {@code true}: 复制成功<br>{@code false}: 复制失败
   */
  public static boolean copyDir(String srcDirPath, String destDirPath) {
    return copyDir(getFileByPath(srcDirPath), getFileByPath(destDirPath));
  }

  /**
   * 复制目录
   *
   * @param srcDir  源目录
   * @param destDir 目标目录
   * @return {@code true}: 复制成功<br>{@code false}: 复制失败
   */
  public static boolean copyDir(File srcDir, File destDir) {
    return copyOrMoveDir(srcDir, destDir, false);
  }

  /**
   * 复制文件
   *
   * @param srcFilePath  源文件路径
   * @param destFilePath 目标文件路径
   * @return {@code true}: 复制成功<br>{@code false}: 复制失败
   */
  public static boolean copyFile(String srcFilePath, String destFilePath) {
    return copyFile(getFileByPath(srcFilePath), getFileByPath(destFilePath));
  }

  /**
   * 复制文件
   *
   * @param srcFile  源文件
   * @param destFile 目标文件
   * @return {@code true}: 复制成功<br>{@code false}: 复制失败
   */
  public static boolean copyFile(File srcFile, File destFile) {
    return copyOrMoveFile(srcFile, destFile, false);
  }

  /**
   * 移动目录
   *
   * @param srcDirPath  源目录路径
   * @param destDirPath 目标目录路径
   * @return {@code true}: 移动成功<br>{@code false}: 移动失败
   */
  public static boolean moveDir(String srcDirPath, String destDirPath) {
    return moveDir(getFileByPath(srcDirPath), getFileByPath(destDirPath));
  }

  /**
   * 移动目录
   *
   * @param srcDir  源目录
   * @param destDir 目标目录
   * @return {@code true}: 移动成功<br>{@code false}: 移动失败
   */
  public static boolean moveDir(File srcDir, File destDir) {
    return copyOrMoveDir(srcDir, destDir, true);
  }

  /**
   * 移动文件
   *
   * @param srcFilePath  源文件路径
   * @param destFilePath 目标文件路径
   * @return {@code true}: 移动成功<br>{@code false}: 移动失败
   */
  public static boolean moveFile(String srcFilePath, String destFilePath) {
    return moveFile(getFileByPath(srcFilePath), getFileByPath(destFilePath));
  }

  /**
   * 移动文件
   *
   * @param srcFile  源文件
   * @param destFile 目标文件
   * @return {@code true}: 移动成功<br>{@code false}: 移动失败
   */
  public static boolean moveFile(File srcFile, File destFile) {
    return copyOrMoveFile(srcFile, destFile, true);
  }

  /**
   * 删除目录
   *
   * @param dirPath 目录路径
   * @return {@code true}: 删除成功<br>{@code false}: 删除失败
   */
  public static boolean deleteDir(String dirPath) {
    return deleteDir(getFileByPath(dirPath));
  }

  /**
   * 删除目录
   *
   * @param dir 目录
   * @return {@code true}: 删除成功<br>{@code false}: 删除失败
   */
  public static boolean deleteDir(File dir) {
    if (dir == null) return false;
    // 目录不存在返回true
    if (!dir.exists()) return true;
    // 不是目录返回false
    if (!dir.isDirectory()) return false;
    // 现在文件存在且是文件夹
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (file.isFile()) {
          if (!deleteFile(file)) return false;
        } else if (file.isDirectory()) {
          if (!deleteDir(file)) return false;
        }
      }
    }
    return dir.delete();
  }

  /**
   * 删除文件
   *
   * @param srcFilePath 文件路径
   * @return {@code true}: 删除成功<br>{@code false}: 删除失败
   */
  public static boolean deleteFile(String srcFilePath) {
    return deleteFile(getFileByPath(srcFilePath));
  }

  /**
   * 删除文件
   *
   * @param file 文件
   * @return {@code true}: 删除成功<br>{@code false}: 删除失败
   */
  public static boolean deleteFile(File file) {
    return file != null && (!file.exists() || file.isFile() && file.delete());
  }

  /**
   * 删除目录下的所有文件
   *
   * @param dirPath 目录路径
   * @return {@code true}: 删除成功<br>{@code false}: 删除失败
   */
  public static boolean deleteFilesInDir(String dirPath) {
    return deleteFilesInDir(getFileByPath(dirPath));
  }

  /**
   * 删除目录下的所有文件
   *
   * @param dir 目录
   * @return {@code true}: 删除成功<br>{@code false}: 删除失败
   */
  public static boolean deleteFilesInDir(File dir) {
    if (dir == null) return false;
    // 目录不存在返回true
    if (!dir.exists()) return true;
    // 不是目录返回false
    if (!dir.isDirectory()) return false;
    // 现在文件存在且是文件夹
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (file.isFile()) {
          if (!deleteFile(file)) return false;
        } else if (file.isDirectory()) {
          if (!deleteDir(file)) return false;
        }
      }
    }
    return true;
  }

  /**
   * 获取目录下所有文件
   *
   * @param dirPath     目录路径
   * @param isRecursive 是否递归进子目录
   * @return 文件链表
   */
  public static List<File> listFilesInDir(String dirPath, boolean isRecursive) {
    return listFilesInDir(getFileByPath(dirPath), isRecursive);
  }

  /**
   * 获取目录下所有文件
   *
   * @param dir         目录
   * @param isRecursive 是否递归进子目录
   * @return 文件链表
   */
  public static List<File> listFilesInDir(File dir, boolean isRecursive) {
    if (!isDir(dir)) return null;
    if (isRecursive) return listFilesInDir(dir);
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      Collections.addAll(list, files);
    }
    return list;
  }

  /**
   * 获取目录下所有文件包括子目录
   *
   * @param dirPath 目录路径
   * @return 文件链表
   */
  public static List<File> listFilesInDir(String dirPath) {
    return listFilesInDir(getFileByPath(dirPath));
  }

  /**
   * 获取目录下所有文件包括子目录
   *
   * @param dir 目录
   * @return 文件链表
   */
  public static List<File> listFilesInDir(File dir) {
    if (!isDir(dir)) return null;
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        list.add(file);
        if (file.isDirectory()) {
          List<File> fileList = listFilesInDir(file);
          if (fileList != null) {
            list.addAll(fileList);
          }
        }
      }
    }
    return list;
  }

  /**
   * 获取目录下所有后缀名为suffix的文件
   * <p>大小写忽略</p>
   *
   * @param dirPath     目录路径
   * @param suffix      后缀名
   * @param isRecursive 是否递归进子目录
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(String dirPath, String suffix, boolean isRecursive) {
    return listFilesInDirWithFilter(getFileByPath(dirPath), suffix, isRecursive);
  }

  /**
   * 获取目录下所有后缀名为suffix的文件
   * <p>大小写忽略</p>
   *
   * @param dir         目录
   * @param suffix      后缀名
   * @param isRecursive 是否递归进子目录
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(File dir, String suffix, boolean isRecursive) {
    if (isRecursive) return listFilesInDirWithFilter(dir, suffix);
    if (dir == null || !isDir(dir)) return null;
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (file.getName().toUpperCase().endsWith(suffix.toUpperCase())) {
          list.add(file);
        }
      }
    }
    return list;
  }

  /**
   * 获取目录下所有后缀名为suffix的文件包括子目录
   * <p>大小写忽略</p>
   *
   * @param dirPath 目录路径
   * @param suffix  后缀名
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(String dirPath, String suffix) {
    return listFilesInDirWithFilter(getFileByPath(dirPath), suffix);
  }

  /**
   * 获取目录下所有后缀名为suffix的文件包括子目录
   * <p>大小写忽略</p>
   *
   * @param dir    目录
   * @param suffix 后缀名
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(File dir, String suffix) {
    if (dir == null || !isDir(dir)) return null;
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (file.getName().toUpperCase().endsWith(suffix.toUpperCase())) {
          list.add(file);
        }
        if (file.isDirectory()) {
          list.addAll(listFilesInDirWithFilter(file, suffix));
        }
      }
    }
    return list;
  }

  /**
   * 获取目录下所有符合filter的文件
   *
   * @param dirPath     目录路径
   * @param filter      过滤器
   * @param isRecursive 是否递归进子目录
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(String dirPath, FilenameFilter filter, boolean isRecursive) {
    return listFilesInDirWithFilter(getFileByPath(dirPath), filter, isRecursive);
  }

  /**
   * 获取目录下所有符合filter的文件
   *
   * @param dir         目录
   * @param filter      过滤器
   * @param isRecursive 是否递归进子目录
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(File dir, FilenameFilter filter, boolean isRecursive) {
    if (isRecursive) return listFilesInDirWithFilter(dir, filter);
    if (dir == null || !isDir(dir)) return null;
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (filter.accept(file.getParentFile(), file.getName())) {
          list.add(file);
        }
      }
    }
    return list;
  }

  /**
   * 获取目录下所有符合filter的文件包括子目录
   *
   * @param dirPath 目录路径
   * @param filter  过滤器
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(String dirPath, FilenameFilter filter) {
    return listFilesInDirWithFilter(getFileByPath(dirPath), filter);
  }

  /**
   * 获取目录下所有符合filter的文件包括子目录
   *
   * @param dir    目录
   * @param filter 过滤器
   * @return 文件链表
   */
  public static List<File> listFilesInDirWithFilter(File dir, FilenameFilter filter) {
    if (dir == null || !isDir(dir)) return null;
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (filter.accept(file.getParentFile(), file.getName())) {
          list.add(file);
        }
        if (file.isDirectory()) {
          list.addAll(listFilesInDirWithFilter(file, filter));
        }
      }
    }
    return list;
  }

  /**
   * 获取目录下指定文件名的文件包括子目录
   * <p>大小写忽略</p>
   *
   * @param dirPath  目录路径
   * @param fileName 文件名
   * @return 文件链表
   */
  public static List<File> searchFileInDir(String dirPath, String fileName) {
    return searchFileInDir(getFileByPath(dirPath), fileName);
  }

  /**
   * 获取目录下指定文件名的文件包括子目录
   * <p>大小写忽略</p>
   *
   * @param dir      目录
   * @param fileName 文件名
   * @return 文件链表
   */
  public static List<File> searchFileInDir(File dir, String fileName) {
    if (dir == null || !isDir(dir)) return null;
    List<File> list = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (file.getName().toUpperCase().equals(fileName.toUpperCase())) {
          list.add(file);
        }
        if (file.isDirectory()) {
          list.addAll(searchFileInDir(file, fileName));
        }
      }
    }
    return list;
  }

  /**
   * 获取文件最后修改的毫秒时间戳
   *
   * @param filePath 文件路径
   * @return 文件最后修改的毫秒时间戳
   */
  public static long getFileLastModified(String filePath) {
    return getFileLastModified(getFileByPath(filePath));
  }

  /**
   * 获取文件最后修改的毫秒时间戳
   *
   * @param file 文件
   * @return 文件最后修改的毫秒时间戳
   */
  public static long getFileLastModified(File file) {
    if (file == null) return -1;
    return file.lastModified();
  }

  /**
   * 简单获取文件编码格式
   *
   * @param filePath 文件路径
   * @return 文件编码
   */
  public static String getFileCharsetSimple(String filePath) {
    return getFileCharsetSimple(getFileByPath(filePath));
  }

  /**
   * 简单获取文件编码格式
   *
   * @param file 文件
   * @return 文件编码
   */
  public static String getFileCharsetSimple(File file) {
    int p = 0;
    InputStream is = null;
    try {
      is = new BufferedInputStream(new FileInputStream(file));
      p = (is.read() << 8) + is.read();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      IoUtils.close(is);
    }
    switch (p) {
      case 0xefbb:
        return "UTF-8";
      case 0xfffe:
        return "Unicode";
      case 0xfeff:
        return "UTF-16BE";
      default:
        return "GBK";
    }
  }

  /**
   * 获取文件行数
   *
   * @param filePath 文件路径
   * @return 文件行数
   */
  public static int getFileLines(String filePath) {
    return getFileLines(getFileByPath(filePath));
  }

  /**
   * 获取文件行数
   * <p>比readLine要快很多</p>
   *
   * @param file 文件
   * @return 文件行数
   */
  public static int getFileLines(File file) {
    int count = 1;
    InputStream is = null;
    try {
      is = new BufferedInputStream(new FileInputStream(file));
      byte[] buffer = new byte[1024];
      int readChars;
      if (LINE_SEP.endsWith("\n")) {
        while ((readChars = is.read(buffer, 0, 1024)) != -1) {
          for (int i = 0; i < readChars; ++i) {
            if (buffer[i] == '\n') ++count;
          }
        }
      } else {
        while ((readChars = is.read(buffer, 0, 1024)) != -1) {
          for (int i = 0; i < readChars; ++i) {
            if (buffer[i] == '\r') ++count;
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      IoUtils.close(is);
    }
    return count;
  }

  /**
   * 获取目录大小
   *
   * @param dirPath 目录路径
   * @return 文件大小
   */
  public static String getDirSize(String dirPath) {
    return getDirSize(getFileByPath(dirPath));
  }

  /**
   * 获取目录大小
   *
   * @param dir 目录
   * @return 文件大小
   */
  public static String getDirSize(File dir) {
    long len = getDirLength(dir);
    return len == -1 ? "" : byte2FitMemorySize(len);
  }

  /**
   * 获取文件大小
   *
   * @param filePath 文件路径
   * @return 文件大小
   */
  public static String getFileSize(String filePath) {
    return getFileSize(getFileByPath(filePath));
  }

  /**
   * 获取文件大小
   *
   * @param file 文件
   * @return 文件大小
   */
  public static String getFileSize(File file) {
    long len = getFileLength(file);
    return len == -1 ? "" : byte2FitMemorySize(len);
  }

  /**
   * 获取目录长度
   *
   * @param dirPath 目录路径
   * @return 目录长度
   */
  public static long getDirLength(String dirPath) {
    return getDirLength(getFileByPath(dirPath));
  }

  /**
   * 获取目录长度
   *
   * @param dir 目录
   * @return 目录长度
   */
  public static long getDirLength(File dir) {
    if (!isDir(dir)) return -1;
    long len = 0;
    File[] files = dir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        if (file.isDirectory()) {
          len += getDirLength(file);
        } else {
          len += file.length();
        }
      }
    }
    return len;
  }

  /**
   * 获取文件长度
   *
   * @param filePath 文件路径
   * @return 文件长度
   */
  public static long getFileLength(String filePath) {
    return getFileLength(getFileByPath(filePath));
  }

  /**
   * 获取文件长度
   *
   * @param file 文件
   * @return 文件长度
   */
  public static long getFileLength(File file) {
    if (!isFile(file)) return -1;
    return file.length();
  }

  /**
   * 获取文件的MD5校验码
   *
   * @param filePath 文件路径
   * @return 文件的MD5校验码
   */
  public static String getFileMD5ToString(String filePath) {
    File file = isSpace(filePath) ? null : new File(filePath);
    return getFileMD5ToString(file);
  }

  /**
   * 获取文件的MD5校验码
   *
   * @param filePath 文件路径
   * @return 文件的MD5校验码
   */
  public static byte[] getFileMD5(String filePath) {
    File file = isSpace(filePath) ? null : new File(filePath);
    return getFileMD5(file);
  }

  /**
   * 获取文件的MD5校验码
   *
   * @param file 文件
   * @return 文件的MD5校验码
   */
  public static String getFileMD5ToString(File file) {
    return bytes2HexString(getFileMD5(file));
  }

  /**
   * 获取文件的MD5校验码
   *
   * @param file 文件
   * @return 文件的MD5校验码
   */
  public static byte[] getFileMD5(File file) {
    if (file == null) return null;
    DigestInputStream dis = null;
    try {
      FileInputStream fis = new FileInputStream(file);
      MessageDigest md = MessageDigest.getInstance("MD5");
      dis = new DigestInputStream(fis, md);
      byte[] buffer = new byte[1024 * 256];
      while (true) {
        if (!(dis.read(buffer) > 0)) break;
      }
      md = dis.getMessageDigest();
      return md.digest();
    } catch (NoSuchAlgorithmException | IOException e) {
      e.printStackTrace();
    } finally {
      IoUtils.close(dis);
    }
    return null;
  }

  /**
   * 获取全路径中的最长目录
   *
   * @param file 文件
   * @return filePath最长目录
   */
  public static String getDirName(File file) {
    if (file == null) return null;
    return getDirName(file.getPath());
  }

  /**
   * 获取全路径中的最长目录
   *
   * @param filePath 文件路径
   * @return filePath最长目录
   */
  public static String getDirName(String filePath) {
    if (isSpace(filePath)) return filePath;
    int lastSep = filePath.lastIndexOf(File.separator);
    return lastSep == -1 ? "" : filePath.substring(0, lastSep + 1);
  }

  /**
   * 获取全路径中的文件名
   *
   * @param file 文件
   * @return 文件名
   */
  public static String getFileName(File file) {
    if (file == null) return null;
    return getFileName(file.getPath());
  }

  /**
   * 获取全路径中的文件名
   *
   * @param filePath 文件路径
   * @return 文件名
   */
  public static String getFileName(String filePath) {
    if (isSpace(filePath)) return filePath;
    int lastSep = filePath.lastIndexOf(File.separator);
    return lastSep == -1 ? filePath : filePath.substring(lastSep + 1);
  }

  /**
   * 获取全路径中的不带拓展名的文件名
   *
   * @param file 文件
   * @return 不带拓展名的文件名
   */
  public static String getFileNameNoExtension(File file) {
    if (file == null) return null;
    return getFileNameNoExtension(file.getPath());
  }

  /**
   * 获取全路径中的不带拓展名的文件名
   *
   * @param filePath 文件路径
   * @return 不带拓展名的文件名
   */
  public static String getFileNameNoExtension(String filePath) {
    if (isSpace(filePath)) return filePath;
    int lastPoi = filePath.lastIndexOf('.');
    int lastSep = filePath.lastIndexOf(File.separator);
    if (lastSep == -1) {
      return (lastPoi == -1 ? filePath : filePath.substring(0, lastPoi));
    }
    if (lastPoi == -1 || lastSep > lastPoi) {
      return filePath.substring(lastSep + 1);
    }
    return filePath.substring(lastSep + 1, lastPoi);
  }

  /**
   * 获取全路径中的文件拓展名
   *
   * @param file 文件
   * @return 文件拓展名
   */
  public static String getFileExtension(File file) {
    if (file == null) return null;
    return getFileExtension(file.getPath());
  }

  /**
   * 获取全路径中的文件拓展名
   *
   * @param filePath 文件路径
   * @return 文件拓展名
   */
  public static String getFileExtension(String filePath) {
    if (isSpace(filePath)) return filePath;
    int lastPoi = filePath.lastIndexOf('.');
    int lastSep = filePath.lastIndexOf(File.separator);
    if (lastPoi == -1 || lastSep >= lastPoi) return "";
    return filePath.substring(lastPoi + 1);
  }

  ///////////////////////////////////////////////////////////////////////////
  // copy from ConvertUtils
  ///////////////////////////////////////////////////////////////////////////

  private static final char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  /**
   * byteArr转hexString
   * <p>例如：</p>
   * bytes2HexString(new byte[] { 0, (byte) 0xa8 }) returns 00A8
   *
   * @param bytes 字节数组
   * @return 16进制大写字符串
   */
  private static String bytes2HexString(byte[] bytes) {
    if (bytes == null) return null;
    int len = bytes.length;
    if (len <= 0) return null;
    char[] ret = new char[len << 1];
    for (int i = 0, j = 0; i < len; i++) {
      ret[j++] = hexDigits[bytes[i] >>> 4 & 0x0f];
      ret[j++] = hexDigits[bytes[i] & 0x0f];
    }
    return new String(ret);
  }

  /**
   * 字节数转合适内存大小
   * <p>保留3位小数</p>
   *
   * @param byteNum 字节数
   * @return 合适内存大小
   */
  @SuppressLint("DefaultLocale")
  private static String byte2FitMemorySize(long byteNum) {
    if (byteNum < 0) {
      return "shouldn't be less than zero!";
    } else if (byteNum < 1024) {
      return String.format("%.3fB", (double) byteNum + 0.0005);
    } else if (byteNum < 1048576) {
      return String.format("%.3fKB", (double) byteNum / 1024 + 0.0005);
    } else if (byteNum < 1073741824) {
      return String.format("%.3fMB", (double) byteNum / 1048576 + 0.0005);
    } else {
      return String.format("%.3fGB", (double) byteNum / 1073741824 + 0.0005);
    }
  }

  private static boolean isSpace(String s) {
    if (s == null) return true;
    for (int i = 0, len = s.length(); i < len; ++i) {
      if (!Character.isWhitespace(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean hasExStorage() {
    return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
  }

  /**
   * 获取文件后缀
   *
   * @param path
   * @return
   */
  public static String getFileType(String path) {
    try {
      int index = path.lastIndexOf(".");
      if (index > 2) {
        String fileType = path.substring(index, path.length());
        return fileType;
      } else {
        return "unknow";
      }
    } catch (Exception e) {
      e.printStackTrace();
      return "exception";
    }
  }

  public static List<File> orderByName(List<File> files) {
    List fileList = Arrays.asList(files);
    Collections.sort(fileList, (Comparator<File>) (o1, o2) -> {
      if (o1.isDirectory() && o2.isFile())
        return -1;
      if (o1.isFile() && o2.isDirectory())
        return 1;
      return o1.getName().compareTo(o2.getName());
    });
    for (File file1 : files) {
      System.out.println(file1.getName());
    }
    return files;
  }

  public static File[] sort(File[] s) {
    //中间值
    File temp = null;
    //外循环:我认为最小的数,从0~长度-1
    for (int j = 0; j < s.length - 1; j++) {
      //最小值:假设第一个数就是最小的
      String min = s[j].getName();
      //记录最小数的下标的
      int minIndex = j;
      //内循环:拿我认为的最小的数和后面的数一个个进行比较
      for (int k = j + 1; k < s.length; k++) {
        //找到最小值
        if (Integer.parseInt(min.substring(0, min.indexOf("."))) > Integer.parseInt(s[k].getName().substring(0, s[k].getName().indexOf(".")))) {
          //修改最小
          min = s[k].getName();
          minIndex = k;
        }
      }
      //当退出内层循环就找到这次的最小值
      //交换位置
      temp = s[j];
      s[j] = s[minIndex];
      s[minIndex] = temp;
    }
    return s;
  }

  public static final int BUFFER_SIZE = 2 * 1024 * 1024;
  private static void copy(Context context, String zipPath, String targetPath) throws Exception {
    if (TextUtils.isEmpty(zipPath) || TextUtils.isEmpty(targetPath)) {
      return;
    }
    File dest = new File(targetPath);
    dest.getParentFile().mkdirs();
    InputStream in = null;
    OutputStream out = null;
    try {
      in = new BufferedInputStream(context.getAssets().open(zipPath));
      out = new BufferedOutputStream(new FileOutputStream(dest));
      byte[] buffer = new byte[BUFFER_SIZE];
      int length = 0;
      while ((length = in.read(buffer)) != -1) {
        out.write(buffer, 0, length);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        out.close();
        in.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * 拷贝assets文件下文件到指定路径
   */
  public static void copyAssets(Context context, String assetDir, String targetDir) throws Exception {
    if (TextUtils.isEmpty(assetDir) || TextUtils.isEmpty(targetDir)) {
      return;
    }
    String separator = File.separator;
    // 获取assets目录assetDir下一级所有文件以及文件夹
    String[] fileNames = context.getResources().getAssets().list(assetDir);

    // 如果是文件夹(目录),则继续递归遍历
    if (fileNames.length > 0) {
      File targetFile = new File(targetDir);
      if (!targetFile.exists() && !targetFile.mkdirs()) {
        return;
      }
      for (String fileName : fileNames) {
        copyAssets(context, assetDir + separator + fileName, targetDir + separator + fileName);
      }
    } else { // 文件,则执行拷贝
      copy(context, assetDir, targetDir);
    }
  }

  private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;


  /**
   * 获取文件夹下的所有子文件名称
   * @param path
   * @return
   */
  public static List<String> getFilesAllName(String path) {
    File file=new File(path);
    File[] files=file.listFiles();
    if (files == null){Log.e("error","空目录");return null;}
    List<String> s = new ArrayList<>();
    for(int i =0;i<files.length;i++){
      s.add(files[i].getName());
    }
    return s;
  }

  /**
   * 解压
   *
   * @param unZipfileName
   * @param mDestPath
   * @return
   * @throws
   */
  public static int unZip(String unZipfileName, String mDestPath) throws Exception {
    Log.i(TAG, "unZip destPath=" + mDestPath);
    int fileCount = 0;
    if (!mDestPath.endsWith("/")) {
      mDestPath = mDestPath + "/";
    }
    FileOutputStream fileOut = null;
    ZipInputStream zipIn = null;
    try {
      createDir(mDestPath);
      ZipEntry zipEntry = null;
      File file = null;
      int readedBytes = 0;
      byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
      zipIn = new ZipInputStream(new FileInputStream(unZipfileName));
      while ((zipEntry = zipIn.getNextEntry()) != null) {
        file = new File(mDestPath + zipEntry.getName());
        if (zipEntry.isDirectory()) {
          file.mkdirs();
        } else {
          // 如果指定文件的目录不存在,则创建之.
          File parent = file.getParentFile();
          if (!parent.exists()) {
            parent.mkdirs();
          }
          fileOut = new FileOutputStream(file);
          while ((readedBytes = zipIn.read(buf)) > 0) {
            fileOut.write(buf, 0, readedBytes);
          }
          fileOut.flush();
          fileOut.close();
          fileCount++;
        }
        zipIn.closeEntry();
      }
    } catch (Exception e) {
      e.printStackTrace();
      fileCount = -1;
    } finally {
      try {
        if (fileOut != null) {
          fileOut.close();
        }
        if (zipIn != null) {
          zipIn.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return fileCount;

  }

  public static void createDir(String dir) {
    File file = new File(dir);
    if (file.exists()) {
      return;
    }
    file.mkdirs();
  }

  public static void processAssetsFile(Context context, String fileName, String dirPath) {
    try {
      File file = new File(dirPath);
      //存在已经有这个目录但是里面的文件不全
      copyAssets(context, fileName, dirPath);
      // if (!file.exists()) {
      //     copyAssets(context, fileName, dirPath);
      // } else {
      //     Log.i(TAG, "path is ready:" + dirPath);
      // }
    } catch (Exception e) {
      Log.e(TAG, "processAssetZipFile "+e);
    }
  }

}