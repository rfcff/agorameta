package com.joyy.metabolt.example.utils;

import android.app.Activity;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.nio.ByteBuffer;

/**
 * @author cjw
 */
public class CommonUtil {

    public static void hideInputBoard(Activity activity, EditText editText)
    {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }

    public static byte[] doI420ToNV21(byte[] ydata, byte[] udata, byte[] vdata, int width, int height) {
        int total = width * height;
        byte[] ret = new byte[total * 3 / 2];
        System.arraycopy(ydata, 0, ret, 0, total);
        for (int i = 0; i < total / 4; i += 1) {
            System.arraycopy(vdata, i, ret, total + 2 * i, 1);
            System.arraycopy(udata, i, ret, total + 2 * i + 1, 1);
        }
        return ret;
    }

    public static ByteBuffer doI420ToNV21(ByteBuffer ydata, ByteBuffer udata, ByteBuffer vdata, int width, int height) {
        int total = width * height;
        ByteBuffer buf = ByteBuffer.allocate(total * 3 / 2);
        byte[] uByte = udata.array();
        byte[] vByte = vdata.array();
        buf.put(ydata);
        for (int i = 0; i < total / 4; i += 1) {
            buf.put(vByte, 2 * i, 1);
            buf.put(uByte, 2 * i + 1, 1);
        }
        return buf;
    }
}
