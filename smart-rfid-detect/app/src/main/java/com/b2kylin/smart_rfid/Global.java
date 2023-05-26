package com.b2kylin.smart_rfid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.b2kylin.misc.LocalRecordSQLite;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import commons.proto.DiggingsOuterClass;

public class Global {
    public static volatile int exExpireMin = 60;
    public static volatile int exFreezeMin = 300;
    public static volatile int exEntruckingMin = 60;
    public static volatile int fifoCheckPeriod = 5;
    public static volatile int syncInfoPeriod = 300; // seconds
    public static volatile DiggingsOuterClass.DeviceInfoPageVo deviceInfoPageVo;
    public static volatile DiggingsOuterClass.DeviceInfoResponse selfInfo;
    public static volatile DiggingsOuterClass.BuilderShiftResponse shiftInfo;
    public static volatile boolean deviceOnline = false;
    @SuppressLint("SdCardPath")
    public static final String pictureRoot = "/storage/emulated/0/DCIM/";
    public static final String bigButtonBroadcast = "BigButton";
    public static final String protoBinRoot = "/storage/emulated/0/Download/";
    public static final String deviceListBin = protoBinRoot + "DeviceList.bin";
    public static final String selfInfoBin = protoBinRoot + "SelfInfo.bin";
    public static final String shiftInfoBin = protoBinRoot + "ShiftInfo.bin";
    public static final String config = protoBinRoot + "Config.bin";
    public static final String shiftConfig = protoBinRoot + "ShiftConfig.bin";
    public static volatile String dbName = "umining_saas_13012340002";
    public static volatile String excavatorId = "122";
    public static volatile String rfidReaderNo = "3";
    public static Handler handlerUI;
    public static LocalRecordSQLite localRecordSQLite;
    public static final String localRecordDb = protoBinRoot + "record.db";
    public static volatile String dayShiftStart = "08:00:00";
    public static volatile String nightShiftStart = "20:00:00";
    public static volatile int loadCount = 0;
    public static volatile double loadTotal = 0;
    public static volatile long loadSuccessTimestamp = 0;
    public static SharedPreferences sharedPreferences;
    public static SHIFT_MODE shiftMode;

    public enum UI_WHAT {
        ONLINE(0),
        OFFLINE(1),
        LOADING_STATUS(2),
        LOADING_NOTHING_STATUS(3),
        LOAD_SUCCESS_STATUS(4),
        SELF_CAR_NUMBER(5),
        LOAD_COUNT(6),
        LOAD_TOTAL(7),
        DEVICE_CAR_NUMBER(8),
        RECORD_ADD(9),
        USB_COPY_STATUS(10),
        SWITCH_DAY_NIGHT_MODE(11);

        public int value;

        UI_WHAT(int value) {
            this.value = value;
        }
    }

    public enum SHIFT_MODE {
        DAY_SHIFT_MODE(0),
        NIGHT_SHIFT_MODE(1),
        UNKNOWN_MODE(2);

        public int value;

        SHIFT_MODE(int value) {
            this.value = value;
        }
    }

    static void rfidPowerOn() {
        File file = new File("/sys/class/gpio/gpio135/value");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write('1');
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
