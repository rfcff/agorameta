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

    public static byte[] doI420ToNV21(byte[] data, int width, int height) {
        byte[] ret = new byte[data.length];
        int total = width * height;

        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total + total / 4, total / 4);

        bufferY.put(data, 0, total);
        for (int i = 0; i < total / 4; i += 1) {
            bufferV.put(data[total + i]);
            bufferU.put(data[i + total + total / 4]);
        }

        return ret;
    }

    public static byte[] doI420ToNV21(ByteBuffer yBuffer, ByteBuffer uBuffer, ByteBuffer vBuffer, int width, int height) {
        int total = width * height;
        byte[] ret = new byte[(total * 3)>>1];

        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total + total / 4, total / 4);

        bufferY.put(yBuffer);
        for (int i = 0; i < total / 4; i += 1) {
            bufferV.put(vBuffer);
            bufferU.put(uBuffer);
        }

        return ret;
    }
}
