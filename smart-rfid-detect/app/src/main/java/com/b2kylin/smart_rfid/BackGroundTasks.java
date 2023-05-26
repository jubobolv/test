package com.b2kylin.smart_rfid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.b2kylin.grpc.DiggingsAction;
import com.b2kylin.misc.Utils;
import com.b2kylin.serial.BigButton;
import com.b2kylin.serial.RfidReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import commons.proto.DiggingsOuterClass;

public class BackGroundTasks {
    private Context context;
    public RfidReader rfidReader;
    private Thread beat;
    private Thread infoSync;
    private Thread shiftCheck;
    private final String TAG = "BackGroundTasks";

    public BackGroundTasks(Context context) {
        this.context = context;
    }

    public void destroyAll() {
        beat.interrupt();
        infoSync.interrupt();
        shiftCheck.interrupt();
        try {
            beat.join();
            infoSync.join();
            shiftCheck.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String recordSave() {
        return rfidReader.recordSave();
    }

    public String recordSave(String rfid) {
        return rfidReader.recordSave(rfid);
    }

    public void initAll() {

        infoSync = new Thread(new Runnable() {
            @Override
            public void run() {
                // read devices list
                File file = new File(Global.deviceListBin);
                if (file.exists()) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(file);
                        Global.deviceInfoPageVo = DiggingsOuterClass.DeviceInfoPageVo.parseFrom(fileInputStream);
                        fileInputStream.close();
                        for (DiggingsOuterClass.DeviceInfoVo i : Global.deviceInfoPageVo.getRecordsList()) {
                            Log.i(TAG, "local getCarFrameNumber " + i.getCarFrameNumber());
                            Log.i(TAG, "local getCarNumber " + i.getCarNumber());
                            Log.i(TAG, "local getRfidDeviceNumber " + i.getRfidDeviceNumber());
                            Log.i(TAG, "local getCarVolume: " + i.getCarVolume());
                            Log.i(TAG, "-----------------------");
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, Global.deviceListBin + " not exist");
                }

                // read self info
                File file2 = new File(Global.selfInfoBin);
                if (file2.exists()) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(file2);
                        Global.selfInfo = DiggingsOuterClass.DeviceInfoResponse.parseFrom(fileInputStream);
                        fileInputStream.close();

                        Log.i(TAG, "###############################");
                        if (Global.selfInfo.getData().hasExcavatorParam()) {
                            Global.exFreezeMin = Integer.parseInt(Global.selfInfo.getData().getExcavatorParam().getExFreezeMin());
                            Global.exExpireMin = Integer.parseInt(Global.selfInfo.getData().getExcavatorParam().getExExpireMin());
                            Global.exEntruckingMin = Integer.parseInt(Global.selfInfo.getData().getExcavatorParam().getExEntruckingMin());
                            Log.i(TAG, "local exExpireMin: " + Global.exExpireMin);
                            Log.i(TAG, "local exFreezeMin: " + Global.exFreezeMin);
                            Log.i(TAG, "local exEntruckingMin: " + Global.exEntruckingMin);
                        } else {
                            Log.i(TAG, "default exExpireMin: " + Global.exExpireMin);
                            Log.i(TAG, "default exFreezeMin: " + Global.exFreezeMin);
                        }

                        Log.i(TAG, "local getCarFrameNumber: " + Global.selfInfo.getData().getCarFrameNumber());
                        Log.i(TAG, "local getCarNumber: " + Global.selfInfo.getData().getCarNumber());
                        Log.i(TAG, "local getCarTypeName: " + Global.selfInfo.getData().getCarTypeName());
                        Log.i(TAG, "local getId: " + Global.selfInfo.getData().getId());
                        Log.i(TAG, "local getCarType: " + Global.selfInfo.getData().getCarType());
                        Log.i(TAG, "local getCarVolume: " + Global.selfInfo.getData().getCarVolume());
                        Log.i(TAG, "local getOptime: " + Global.selfInfo.getData().getOptime());
                        Log.i(TAG, "local getRemark: " + Global.selfInfo.getData().getRemark());
                        Log.i(TAG, "local getRfidDeviceNumber: " + Global.selfInfo.getData().getRfidDeviceNumber());
                        Log.i(TAG, "local getSubTypeName: " + Global.selfInfo.getData().getSubTypeName());
                        Log.i(TAG, "local getDelFlag: " + Global.selfInfo.getData().getDelFlag());
                        Log.i(TAG, "local getIfLease: " + Global.selfInfo.getData().getIfLease());
                        Log.i(TAG, "###############################");

                        Message message = new Message();
                        Bundle bundle = new Bundle();
                        bundle.putString("NUMBER", Global.selfInfo.getData().getCarNumber());
                        message.setData(bundle);
                        message.what = Global.UI_WHAT.SELF_CAR_NUMBER.value;
                        if (Global.handlerUI != null) {
                            Global.handlerUI.sendMessage(message);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, Global.selfInfoBin + " not exist");
                }

                // read shift info
                File file3 = new File(Global.shiftInfoBin);
                if (file3.exists()) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(file3);
                        Global.shiftInfo = DiggingsOuterClass.BuilderShiftResponse.parseFrom(fileInputStream);
                        fileInputStream.close();

                        Global.dayShiftStart = Global.shiftInfo.getData().getDayStart();
                        Global.nightShiftStart = Global.shiftInfo.getData().getNightStart();

                        Log.i(TAG, "###############################");
                        Log.i(TAG, "local getId: " + Global.shiftInfo.getData().getId());
                        Log.i(TAG, "local getOptime: " + Global.shiftInfo.getData().getOptime());
                        Log.i(TAG, "local getDayStart: " + Global.shiftInfo.getData().getDayStart());
                        Log.i(TAG, "local getDayEnd: " + Global.shiftInfo.getData().getDayEnd());
                        Log.i(TAG, "local getNightStart: " + Global.shiftInfo.getData().getNightStart());
                        Log.i(TAG, "local getNightEnd: " + Global.shiftInfo.getData().getNightEnd());
                        Log.i(TAG, "local getNewest: " + Global.shiftInfo.getData().getNewest());
                        Log.i(TAG, "local getShiftDate: " + Global.shiftInfo.getData().getShiftDate());
                        Log.i(TAG, "local getBuilderShift: " + Global.shiftInfo.getData().getBuilderShift());
                        Log.i(TAG, "###############################");

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, Global.shiftInfoBin + " not exist");
                }

                while (!infoSync.isInterrupted()) {

                    DiggingsAction.DownloadDeviceList deviceList = new DiggingsAction.DownloadDeviceList();
                    deviceList.execute(Global.dbName, "{\"size\":9999,\"page\":1,\"param\":{\"carType\":\"1\"}}");
                    DiggingsAction.DownloadSelfInfo selfInfo = new DiggingsAction.DownloadSelfInfo();
                    selfInfo.execute(Global.dbName, Global.excavatorId);
                    DiggingsAction.DownloadShiftInfo shiftInfoInfo = new DiggingsAction.DownloadShiftInfo();
                    shiftInfoInfo.execute(Global.dbName);
                    Log.i(TAG, "online synced: " + System.currentTimeMillis());

                    // sync not uploaded records
                    if (Global.localRecordSQLite != null) {
                        ArrayList<ArrayList<String>> notUpdatedRecords = Global.localRecordSQLite.readNotUploadRecord5();
                        for (ArrayList<String> r : notUpdatedRecords) {
                            DiggingsAction.UploadRecord2 uploadRecord2 = new DiggingsAction.UploadRecord2();
                            // String[]{id, carNumber, time, date, dbName, excavatorId, rfidReaderNo, rfid, picPath, json}
                            uploadRecord2.execute(r.get(4), r.get(9), r.get(0));
                            Log.i(TAG, "sync not uploaded records id=" + r.get(0));
                        }
                    }

                    try {
                        Thread.sleep(1000 * Global.syncInfoPeriod);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                Log.e(TAG, "infoSync exit");
            }
        });
        infoSync.start();

        beat = new Thread(new Runnable() {
            boolean exit = false;

            @Override
            public void run() {
                BigButton bigButton = new BigButton(context);
                bigButton.start();

                rfidReader = new RfidReader(Global.exExpireMin, Global.exFreezeMin);
                rfidReader.start();

                while (!exit) {

                    DiggingsAction.Beat beat = new DiggingsAction.Beat();
                    beat.execute(Global.rfidReaderNo);
                    while (!beat.isGrpcFinish()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            exit = true;
                            break;
                        }
                    }
                    beat.cancel(false);

                    // upload image
                    if (beat.isGrpcGetImage()) {
                        DiggingsAction.UploadImage uploadImage = new DiggingsAction.UploadImage();
                        uploadImage.execute(beat.imageInfo.rfidReaderNo, beat.imageInfo.imagePath);
                        while (!uploadImage.isGrpcFinish()) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        uploadImage.cancel(false);
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                rfidReader.interrupt();
                bigButton.interrupt();
                try {
                    rfidReader.join();
                    bigButton.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Log.e(TAG, "BEAT exit");
            }
        });
        beat.start();

        shiftCheck = new Thread(new Runnable() {
            @Override
            public void run() {

                Global.shiftMode = Global.SHIFT_MODE.UNKNOWN_MODE;
                Global.SHIFT_MODE shiftModeLast = Global.SHIFT_MODE.UNKNOWN_MODE;

                while (true) {

                    String[] dayStartStr = Global.dayShiftStart.split(":");
                    if (dayStartStr[0].equals("") || dayStartStr[1].equals("") || dayStartStr[2].equals("")) {
                        try {
                            Thread.sleep(1000 * 10/*s*/);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                        continue;
                    }
                    int dayStartHour = Integer.parseInt(dayStartStr[0]);
                    int dayStartMinute = Integer.parseInt(dayStartStr[1]);
                    int dayStartSecond = Integer.parseInt(dayStartStr[2]);
                    Log.i(TAG, "day: " + dayStartHour + " " + dayStartMinute + " " + dayStartSecond);

                    String[] nightStartStr = Global.nightShiftStart.split(":");
                    if (nightStartStr[0].equals("") || nightStartStr[1].equals("") || nightStartStr[2].equals("")) {
                        try {
                            Thread.sleep(1000 * 10/*s*/);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                        continue;
                    }
                    int nightStartHour = Integer.parseInt(nightStartStr[0]);
                    int nightStartMinute = Integer.parseInt(nightStartStr[1]);
                    int nightStartSecond = Integer.parseInt(nightStartStr[2]);
                    Log.i(TAG, "night: " + nightStartHour + " " + nightStartMinute + " " + nightStartSecond);

                    Date date = Calendar.getInstance().getTime();
                    int nowHour = date.getHours();
                    int nowMinute = date.getMinutes();
                    int nowSecond = date.getSeconds();
                    Log.i(TAG, "now: " + nowHour + " " + nowMinute + " " + nowSecond);

                    if (nowHour >= dayStartHour && nowHour <= nightStartHour) {
                        if ((nowHour == dayStartHour && nowMinute < dayStartMinute) || (nowHour == nightStartHour && nowMinute >= nightStartMinute)) {
                            Global.shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                        } else {
                            Global.shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
                        }
                    } else {
                        Global.shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                    }

                    if (shiftModeLast != Global.shiftMode) {
                        if (shiftModeLast == Global.SHIFT_MODE.UNKNOWN_MODE) {
                            shiftModeLast = Global.shiftMode;
                            continue;
                        }

                        Global.loadTotal = 0;
                        Global.loadCount = 0;

                        Log.i(TAG, "shift mode changed: " + shiftModeLast + " " + Global.shiftMode);

                        if (Global.sharedPreferences != null && shiftModeLast != Global.SHIFT_MODE.UNKNOWN_MODE) {
                            SharedPreferences.Editor editor = Global.sharedPreferences.edit();
                            editor.putInt("COUNT", Global.loadCount);
                            editor.putFloat("TOTAL", (float) Global.loadTotal);
                            editor.putInt("MODE", Global.shiftMode.value);
                            editor.putLong("TP", System.currentTimeMillis());
                            editor.apply();
                        }

                        shiftModeLast = Global.shiftMode;

                        Message message = new Message();
                        Bundle bundle = new Bundle();
                        bundle.putString("NUMBER", String.valueOf(Global.loadTotal));
                        message.setData(bundle);
                        message.what = Global.UI_WHAT.LOAD_TOTAL.value;

                        Message message1 = new Message();
                        Bundle bundle1 = new Bundle();
                        bundle1.putString("NUMBER", String.valueOf(Global.loadCount));
                        message1.setData(bundle1);
                        message1.what = Global.UI_WHAT.LOAD_COUNT.value;
                        if (Global.handlerUI != null) {
                            Global.handlerUI.sendMessage(message);
                            Global.handlerUI.sendMessage(message1);
                        }
                    }

                    Log.i(TAG, "shift mode: " + Global.shiftMode);
                    Message message2 = new Message();
                    message2.what = Global.UI_WHAT.SWITCH_DAY_NIGHT_MODE.value;
                    Bundle bundle2 = new Bundle();
                    if (Global.shiftMode == Global.SHIFT_MODE.NIGHT_SHIFT_MODE) {
                        bundle2.putString("MODE", "night");
                    } else {
                        bundle2.putString("MODE", "day");
                    }
                    message2.setData(bundle2);
                    if (Global.handlerUI != null) {
                        Global.handlerUI.sendMessage(message2);
                    }

                    try {
                        Thread.sleep(1000 * 10/*s*/);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                Log.e(TAG, "shiftCheck exit");
            }
        });
        shiftCheck.start();
    }
}
