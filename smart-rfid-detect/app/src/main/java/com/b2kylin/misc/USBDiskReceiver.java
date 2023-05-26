package com.b2kylin.misc;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.Toast;

import com.b2kylin.smart_rfid.Global;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class USBDiskReceiver extends BroadcastReceiver {

    private static final String TAG = "USBDiskReceiver";
    static long tp = 0;

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        Log.e(TAG, action);
        if (action.equals(VolumeInfo.ACTION_USB_DEVICE_ATTACHED)) {
            Log.i(TAG, "ACTION_USB_DEVICE_ATTACHED");
        } else if (action.equals(VolumeInfo.ACTION_USB_DEVICE_DETACHED)) {
            Log.i(TAG, "ACTION_USB_DEVICE_DETACHED");
        } else if (action.equals(VolumeInfo.ACTION_VOLUME_STATE_CHANGED)) {
            Log.i(TAG, "ACTION_VOLUME_STATE_CHANGED");
        } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            String path = getStoragePath(context);
            if (System.currentTimeMillis() - tp < 5000) {
                Log.i(TAG, "already detected " + path);
                return;
            }
            tp = System.currentTimeMillis();
            Log.i(TAG, "ACTION_MEDIA_MOUNTED: " + path);
            if (path == null) {
                Log.w(TAG, "path null, do nothing");
                return;
            }
            File file = new File(path);
            long freeSpace = file.getFreeSpace();

            Bundle bundle = new Bundle();
            Message message = new Message();
            message.what = Global.UI_WHAT.USB_COPY_STATUS.value;

            String zipFile = Utils.createNotUploadZip();
            if (zipFile == null) {
                bundle.putString("STATUS", "none");
                message.setData(bundle);
                if (Global.handlerUI != null) {
                    Global.handlerUI.sendMessage(message);
                }
                return;
            }

            Log.i(TAG, "free space " + freeSpace);
            Toast.makeText(context, "发现设备可用空间" + (freeSpace / 1024.0 / 1024.0) + "M\n正在拷贝...", Toast.LENGTH_LONG).show();

            File zipSrc = new File(zipFile);
            String filename = zipSrc.getName(); // like xxx.zip
            File zipDst = new File(path + "/" + filename);

            Log.i(TAG, zipSrc.getPath() + " " + zipDst.getPath());


            try {
                Files.copy(zipSrc, zipDst);
            } catch (IOException e) {
                e.printStackTrace();
//                bundle.putString("STATUS", "fail");
//                message.setData(bundle);
//                if (Global.handlerUI != null) {
//                    Global.handlerUI.sendMessage(message);
//                }
//                return;
            }

            bundle.putString("STATUS", "success");
            message.setData(bundle);
            if (Global.handlerUI != null) {
                Global.handlerUI.sendMessage(message);
            }
        }
    }

    public static String getStoragePath(Context context) {

        String storagePath = null;
        StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        try {
            Class storeManagerClazz = Class.forName("android.os.storage.StorageManager");
            Method getVolumesMethod = storeManagerClazz.getMethod("getVolumes");
            List<?> volumeInfo = (List<?>) getVolumesMethod.invoke(mStorageManager);
            Class volumeInfoClazz = Class.forName("android.os.storage.VolumeInfo");
            Method getFsUuidMethod = volumeInfoClazz.getMethod("getFsUuid");
            Field pathField = volumeInfoClazz.getDeclaredField("path");
            if (volumeInfo != null) {
                for (Object volume : volumeInfo) {
                    String uuid = (String) getFsUuidMethod.invoke(volume);
                    if (uuid != null) {
                        storagePath = (String) pathField.get(volume);
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        return storagePath;
    }
}
