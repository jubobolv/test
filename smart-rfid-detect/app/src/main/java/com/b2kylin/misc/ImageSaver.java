package com.b2kylin.misc;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.b2kylin.grpc.DiggingsAction;
import com.b2kylin.smart_rfid.Global;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import commons.proto.DiggingsOuterClass;

/**
 * Created by dang on 2020-01-14.
 * Time will tell.
 * 保存图片的方法
 *
 * @description
 */
public class ImageSaver implements Runnable {
    private Image mImage;
    private File mFile;
    private Context context;
    private static final String TAG = "IMAGE_SAVE";
    private String rfid;
    private String rfidReaderNo;
    private String usefulName;
    private static String fullName;

    public ImageSaver(Image image, Context context, String rfid, String rfidReaderNo) {
        this.mImage = image;
        this.context = context;
        this.rfid = rfid;
        this.rfidReaderNo = rfidReaderNo;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void run() {
        //使用nio的ByteBuffer
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        SimpleDateFormat time = new SimpleDateFormat("HHmmss", Locale.CHINA);
        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);

        String imageName = rfid + "_" + time.format(new Date()) + ".jpg";
        String imagePath = Global.pictureRoot + rfidReaderNo + "/" + date.format(new Date());
        usefulName = "/" + date.format(new Date()) + "/" + imageName;
        fullName = imagePath + "/" + imageName;

        Log.i(TAG, "image path: " + imagePath);
        Log.i(TAG, "image name: " + imageName);
        Log.i(TAG, "useful name: " + usefulName);
        Log.i(TAG, "full name: " + fullName);

        saveImage(imagePath, fullName, bytes);
        saveRecordToTmp();
//        uploadRecord();
        mImage.close();
    }

    public static void saveImage(String imagePath, String fullName, byte[] image) {

        File path = new File(imagePath);
        if (!path.exists()) {
            path.mkdirs();
        }

        File file = new File(fullName);
        if (!file.exists()) {
            try {
                if (!file.createNewFile())
                    return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            OutputStream outputStream = new FileOutputStream(file);
            outputStream.write(image);
            outputStream.flush();
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    void saveRecordToTmp() {
        if (Global.localRecordSQLite != null) {
            Global.localRecordSQLite.addTmpRecord(Global.dbName, Global.excavatorId, Global.rfidReaderNo, rfid, usefulName);
            Log.i(TAG, "saveRecordToTmp : " + rfid + " " + usefulName);
        }
    }
}
