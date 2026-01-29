package com.qiniu.qiniulab.activity;

import android.util.Log;

import java.io.File;

public class MyUtil {
    public static final String TAG = "Util";

    public static String[] traverseFolder(File folder) {
        Log.d(TAG, "traverseFolder: folder = " + folder + ", folder.exists() = " + folder.exists());
        String[] result = null;
        int position = 0;
        if (folder == null || !folder.exists()) {
            return null;
        }

        File[] files = folder.listFiles();
        Log.d(TAG, "traverseFolder: files = " + files);
        if (files != null) {
            Log.d(TAG, "traverseFolder: files length = " + files.length);
            result = new String[files.length];
            for (File file : files) {
                if (file.isDirectory()) {
                    // 如果是目录，递归调用
                    traverseFolder(file);
                } else {
                    // 如果是文件，进行操作
                    Log.d(TAG, "File Path: " + file.getAbsolutePath());
                    result[position++] = file.getAbsolutePath();
                }
            }
        }
        return result;
    }

}
