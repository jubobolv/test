package com.b2kylin.serial;

import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.b2kylin.smart_rfid.Global;
import com.cpdevice.cpcomm.port.Uart;
import com.cpdevice.cpcomm.port.Port;
import com.cpdevice.cpcomm.exception.CPBusException;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import commons.proto.DiggingsOuterClass;

public class RfidReader extends Thread {
    private Uart rfidReader;
    private volatile int exExpireMin;
    private volatile int exFreezeMin;
    private final String TAG = "RFID";
    private String currentDetectedRfid;
    private byte[] buff; // 串口 HEX 缓存
    private byte[] rfidHex; // rfid HEX 缓存
    private final int fifoBuffSize = 30; // 缓存大小
    private ReentrantLock fifoBuffLock; // 缓冲区读写锁
    private ReentrantLock recordedDevicesLock; // 已记录缓存读写锁
    private ArrayList<Info> fifoBuff; // 识别中的缓冲区
    private ArrayList<Info> recordedDevices; // 已记录的（装车成功）设备集合
    private boolean exit = false;

    private class Info {
        String rfidStr;
        int count = 0;
        long timestamp = 0; // 装车成功时间

        public Info(String rfidStr, long timestamp, int count) {
            this.rfidStr = rfidStr;
            this.timestamp = timestamp;
            this.count = count;
        }
    }

    public RfidReader(int exExpireMin, int exFreezeMin) {
        this.exExpireMin = exExpireMin;
        this.exFreezeMin = exFreezeMin;
        buff = new byte[21];
        rfidHex = new byte[12];
        fifoBuffLock = new ReentrantLock(true);
        recordedDevicesLock = new ReentrantLock(true);
        recordedDevices = new ArrayList<>();
        fifoBuff = new ArrayList<>(fifoBuffSize);
        currentDetectedRfid = "";
    }

    public String getCurrentDetectedRfid() {
        return currentDetectedRfid;
    }

    public void setExExpireMin(int exExpireMin) {
        this.exExpireMin = exExpireMin;
    }

    public void setExFreezeMin(int exFreezeMin) {
        this.exFreezeMin = exFreezeMin;
    }

    public String recordSave() {
        return recordSave(currentDetectedRfid);
    }

    public String recordSave(String rfid) {
        if (rfid.equals("")) {
            Log.i(TAG, "rfid null, recordSave do nothing.");
            return null;
        }
        recordedDevicesLock.lock();
        recordedDevices.add(new Info(rfid, System.currentTimeMillis(), 0));
        recordedDevicesLock.unlock();
        Log.i(TAG, "recordSave: " + rfid);

        return rfid;
    }

    @Override
    public void interrupt() {
        exit = true;
        super.interrupt();
    }

    @Override
    public void run() {
        super.run();

        String uartDev = "/dev/ttyS4";
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
            uartDev = "/dev/ttyS3";
        try {
            rfidReader = new Uart(uartDev, Uart.B115200);
        } catch (CPBusException e) {
            e.printStackTrace();
        }

        rfidReader.setReceiveListener(new Port.DataReceiveListener() {
            @Override
            public void onReceive(byte[] bytes) {

//                Log.i(TAG, "onReceive: " + bytes.length);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                    int len = bytes.length;
                    if (len > 21)
                        len = 21;
                    System.arraycopy(bytes, 0, buff, 0, len);
                    if ((buff[0]) == (byte) 0xa0) {
                        System.arraycopy(buff, 7, rfidHex, 0, 12);
                        String rfidStr = byte2HexStr(rfidHex);

                        // 排除非自卸车RFID
                        boolean isFound = false;
                        if (Global.deviceInfoPageVo == null) {
                            Log.w(TAG, "device list is null, detected rfid: " + rfidStr);
                            return;
                        }
                        for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                            if (info.getRfidDeviceNumber().equals(rfidStr)) {
                                isFound = true;
                                break;
                            }
                        }
                        if (!isFound) {
                            Log.w(TAG, "not a useful rfid: " + rfidStr);
                            return;
                        }

                        // 排除已装车冻结时间内设备
                        if (!recordedDevicesLock.tryLock()) {
                            Log.w(TAG, "try lock fail 1");
                            return;
                        }
                        for (Info info : recordedDevices) {
                            if (info.rfidStr.equals(rfidStr)) {
                                // 冻结时间未到，不存储此RFID
                                if (System.currentTimeMillis() - info.timestamp < (Global.exFreezeMin + Global.exEntruckingMin) * 1000) {
                                    recordedDevicesLock.unlock();
                                    return;
                                }
                            }
                        }
                        recordedDevicesLock.unlock();

                        // 写入fifoBuff
                        if (!fifoBuffLock.tryLock()) {
                            Log.w(TAG, "try lock fail 2");
                            return;
                        }
                        fifoBuff.add(new Info(rfidStr, System.currentTimeMillis(), 0));
                        if (fifoBuff.size() > fifoBuffSize) {
                            fifoBuff.remove(0);
                        }
                        fifoBuffLock.unlock();
                    }
                } else {
                    if (bytes.length == 16) {
                        System.arraycopy(bytes, 0, buff, 0, bytes.length);
                    } else if (bytes.length == 5) {
                        System.arraycopy(bytes, 0, buff, 16, bytes.length);
                        if ((buff[0]) == (byte) 0xa0) {
                            System.arraycopy(buff, 7, rfidHex, 0, 12);
                            String rfidStr = byte2HexStr(rfidHex);

                            // 排除非自卸车RFID
                            boolean isFound = false;
                            if (Global.deviceInfoPageVo == null) {
                                Log.w(TAG, "device list is null, detected rfid: " + rfidStr);
                                return;
                            }
                            for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                                if (info.getRfidDeviceNumber().equals(rfidStr)) {
                                    isFound = true;
                                    break;
                                }
                            }
                            if (!isFound) {
                                Log.w(TAG, "not a useful rfid: " + rfidStr);
                                return;
                            }

                            // 排除已装车冻结时间内设备
                            if (!recordedDevicesLock.tryLock()) {
                                Log.w(TAG, "try lock fail 1");
                                return;
                            }
                            for (Info info : recordedDevices) {
                                if (info.rfidStr.equals(rfidStr)) {
                                    // 冻结时间未到，不存储此RFID
                                    if (System.currentTimeMillis() - info.timestamp < (Global.exFreezeMin + Global.exEntruckingMin) * 1000) {
                                        recordedDevicesLock.unlock();
                                        return;
                                    }
                                }
                            }
                            recordedDevicesLock.unlock();

                            // 写入fifoBuff
                            if (!fifoBuffLock.tryLock()) {
                                Log.w(TAG, "try lock fail 2");
                                return;
                            }
                            fifoBuff.add(new Info(rfidStr, System.currentTimeMillis(), 0));
                            if (fifoBuff.size() > fifoBuffSize) {
                                fifoBuff.remove(0);
                            }
                            fifoBuffLock.unlock();
                        }
                    }
                }
            }
        });
        rfidReader.start();

        long timestamp_last_rfid = System.currentTimeMillis();
        // thread loop
        while (!exit) {

            try {
                Thread.sleep(1000 * Global.fifoCheckPeriod);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }

            ArrayList<Info> tmp = new ArrayList<>();

            // 统计各 RFID 出现次数，并获取其最新时间戳
            fifoBuffLock.lock();
            for (Info dev : fifoBuff) {
                int i;
                for (i = 0; i < tmp.size(); i++) {
                    Info _tmp = tmp.get(i);
                    if (dev.rfidStr.equals(_tmp.rfidStr)) {
                        _tmp.count++;
                        if (dev.timestamp > _tmp.timestamp) {
                            _tmp.timestamp = dev.timestamp;
                            timestamp_last_rfid = dev.timestamp;
                        }
                        tmp.set(i, _tmp);
                        break;
                    }
                }
                // 目录中无此RFID则添加
                if (i == tmp.size()) {
                    tmp.add(dev);
                }
            }
            fifoBuffLock.unlock();

            // 获取出现最多、最新的 RFID
            Info rfidMax = null;
            int max = 0;
            for (Info _tmp : tmp) {
                // 获取出现最多的 RFID
                if (_tmp.count > max) {
                    max = _tmp.count;
                    rfidMax = _tmp;
                }
                // 如果有相同数量 RFID，则使用最新 RFID
                if (rfidMax != null && rfidMax.count == _tmp.count && rfidMax.timestamp < _tmp.timestamp) {
                    Log.i(TAG, "max eq: " + rfidMax.rfidStr + " count:" + rfidMax.count + " tp:" + rfidMax.timestamp);
                    Log.i(TAG, "tmp eq: " + _tmp.rfidStr + " count:" + _tmp.count + " tp:" + _tmp.timestamp);
                    rfidMax = _tmp;
                }
            }
            tmp.clear();

            // TODO：车辆识别 RFID 闪动问题优化，待验证
            if (rfidMax != null && rfidMax.count < 5) {
                rfidMax = null;
            }

            // 以最后一个RFID识别时间为准，超时 exExpireMin 秒清除队列
            if (System.currentTimeMillis() - timestamp_last_rfid >= 1000 * Global.exExpireMin) {
                fifoBuffLock.lock();
                fifoBuff.clear();
                fifoBuffLock.unlock();
                Log.i(TAG, "clear fifo at exExpireMin: " + Global.exExpireMin + "s last tp:" + timestamp_last_rfid);
            }

            // 去除冻结无效记录
            int i;
            long now = System.currentTimeMillis();
            recordedDevicesLock.lock();
            for (i = 0; i < recordedDevices.size(); i++) {
                Info _tmp = recordedDevices.get(i);
                if (now - _tmp.timestamp > 1000 * (Global.exFreezeMin + Global.exEntruckingMin)) {
                    Log.i(TAG, "remove record: " + _tmp.rfidStr);
                    recordedDevices.remove(i);
                }
            }
            recordedDevicesLock.unlock();

            // 排除装载成功RFID
            if (rfidMax != null) {
                for (Info _tmp : recordedDevices) {
                    if (rfidMax.rfidStr.equals(_tmp.rfidStr) && System.currentTimeMillis() - _tmp.timestamp < 1000 * (Global.exFreezeMin + Global.exEntruckingMin)) {
                        rfidMax = null;
                        break;
                    }
                }
                if (rfidMax == null) {
                    Log.i(TAG, "NO RFIDs");
                    currentDetectedRfid = "";
                } else {
                    Log.i(TAG, "RFID: " + rfidMax.rfidStr + " count:" + rfidMax.count);
                    currentDetectedRfid = rfidMax.rfidStr;
                }

            } else {
                Log.i(TAG, "NO RFIDS");
                currentDetectedRfid = "";
            }

            ///
            Message message1 = new Message();
            ///// Devices car number ///////
            Message message2 = new Message();
            Bundle bundle = new Bundle();
            if (Global.deviceInfoPageVo != null) {
                for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                    if (info.getRfidDeviceNumber().equals(currentDetectedRfid)) {
                        bundle.putString("NUMBER", info.getCarNumber());
                        break;
                    }
                }
            }
            if (bundle.isEmpty()) {
                bundle.putString("NUMBER", "");
                message1.what = Global.UI_WHAT.LOADING_NOTHING_STATUS.value;
            } else {
                message1.what = Global.UI_WHAT.LOADING_STATUS.value;
            }
            message2.setData(bundle);

            message2.what = Global.UI_WHAT.DEVICE_CAR_NUMBER.value;
            if (Global.handlerUI != null) {
                Global.handlerUI.sendMessage(message1);
                Global.handlerUI.sendMessage(message2);
            }
        }

        rfidReader.stop();
        rfidReader.release();

        Log.e(TAG, "RfidReader exit");
    }

    public static String byte2HexStr(byte[] b) {
        String stmp = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0xFF);
            sb.append((stmp.length() == 1) ? "0" + stmp : stmp);
        }
        return sb.toString().toUpperCase().trim();
    }
}
