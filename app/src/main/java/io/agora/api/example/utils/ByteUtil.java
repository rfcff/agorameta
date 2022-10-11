package io.agora.api.example.utils;

public class ByteUtil {
  public static final byte[] integerToTwoBytes(int value) {
    byte[] result = new byte[2];
    result[0] = (byte) ((value >>> 8) & 0xFF);
    result[1] = (byte) (value & 0xFF);
    return result;
  }

  public static final int twoBytesToInt(byte[] arr, int off) {
    return arr[off] << 8 & 0xFF00 | arr[off + 1] & 0xFF;
  } // end of getInt

  public static final byte[] floatToFourBytes(float value) {
    int intBits = Float.floatToIntBits(value);
    return new byte[]{
        (byte) (intBits >> 24), (byte) (intBits >> 16), (byte) (intBits >> 8), (byte) (intBits)};
  }


  public static final float fourBytesToFloat(byte[] buffer, int n) {
    int intBits =
        buffer[n] << 24 | (buffer[n + 1] & 0xFF) << 16 | (buffer[n + 2] & 0xFF) << 8 | (buffer[n + 3] & 0xFF);
    return Float.intBitsToFloat(intBits);
  }

  /**
   * 截取byte数组   不改变原数组
   *
   * @param b      原数组
   * @param off    偏差值（索引）
   * @param length 长度
   * @return 截取后的数组
   */
  public static final byte[] subByte(byte[] b, int off, int length) {
    byte[] b1 = new byte[length];
    System.arraycopy(b, off, b1, 0, length);
    return b1;
  }
}
