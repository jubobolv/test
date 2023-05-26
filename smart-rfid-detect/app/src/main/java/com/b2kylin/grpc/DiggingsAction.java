package com.b2kylin.grpc;

import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.b2kylin.smart_rfid.Global;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

import commons.proto.DiggingsGrpc;
import commons.proto.DiggingsOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class DiggingsAction {
    private static ManagedChannel channel;
    private static final String TAG = "GRPC";
    private static final String host = "pm.um-mining.com";
    private static final int port = 4001;

    public static class Beat extends AsyncTask<String, Void, Void> {
        private volatile boolean grpcFinish = false;
        private volatile boolean grpcGetImage = false;

        public volatile ImageInfo imageInfo = null;

        public class ImageInfo {
            public String imagePath;
            public String rfidReaderNo;
        }

        public boolean isGrpcFinish() {
            return grpcFinish;
        }

        public boolean isGrpcGetImage() {
            return grpcGetImage;
        }

        @Override
        protected Void doInBackground(String... strings) {
            String rfidReaderNoStr = strings[0];
            int rfidReaderNo = Integer.valueOf(rfidReaderNoStr).intValue();

            imageInfo = new ImageInfo();
            imageInfo.rfidReaderNo = rfidReaderNoStr;

            if (channel == null) {
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            }
            DiggingsGrpc.DiggingsStub stub = DiggingsGrpc.newStub(channel);

            DiggingsOuterClass.StreamRequest beat = DiggingsOuterClass.StreamRequest.newBuilder()
                    .setReqType(0)
                    .setRfidNo(rfidReaderNo)
                    .build();

            StreamObserver<DiggingsOuterClass.StreamResponse> response = new StreamObserver<DiggingsOuterClass.StreamResponse>() {
                @Override
                public void onNext(DiggingsOuterClass.StreamResponse value) {
                    Log.i(TAG, "Beat talk: " + value.getRspType() + " " + value.getRfidNo());
                    if (value.getRspType() != 0) {
                        Log.i(TAG, "Get Image: " + value.getImagePath());
                        grpcGetImage = true;
                        imageInfo.imagePath = value.getImagePath();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.i(TAG, "Beat Error: " + t.getMessage());
                    grpcFinish = true;
                    if (t.getMessage().contains("UNAVAILABLE")) {
                        Global.deviceOnline = false;
                        Message message = new Message();
                        message.what = Global.UI_WHAT.OFFLINE.value;
                        if (Global.handlerUI != null) {
                            Global.handlerUI.sendMessage(message);
                        }
                    }
                }

                @Override
                public void onCompleted() {
                    Log.i(TAG, "Beat Completed\n");
                    grpcFinish = true;
                    // sync online status
                    Global.deviceOnline = true;
                    Message message = new Message();
                    message.what = Global.UI_WHAT.ONLINE.value;
                    if (Global.handlerUI != null) {
                        Global.handlerUI.sendMessage(message);
                    }
                }
            };

            Log.i(TAG, "Beating...");

            StreamObserver<DiggingsOuterClass.StreamRequest> request = stub.streamExHeartBeat(response);
            request.onNext(beat);
            request.onCompleted();

            return null;
        }
    }

    // 自卸车数据
    public static class DownloadDeviceList extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String dbName = strings[0];
            String json = strings[1];

            if (channel == null)
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            DiggingsGrpc.DiggingsBlockingStub stub = DiggingsGrpc.newBlockingStub(channel);

            DiggingsOuterClass.PostRequest request = DiggingsOuterClass.PostRequest.newBuilder()
                    .setRelativePath("/public/device/pageDevice?dbName=" + dbName)
                    .setParamJSON(json)
                    .build();

            DiggingsOuterClass.DeviceInfoPageResponse response = null;
            try {
                response = stub.pageDevice(request);
            } catch (io.grpc.StatusRuntimeException e) {
                e.printStackTrace();
                return null;
            }

            Log.i(TAG, "DownloadDeviceList : " + response.getCode() + " " + response.getMessage());

            // write to cache
            if (response.getMessage().equals("Success")) {
                DiggingsOuterClass.DeviceInfoPageVo deviceInfoPageVo = response.getData();
                for (DiggingsOuterClass.DeviceInfoVo i : deviceInfoPageVo.getRecordsList()) {
                    Log.i(TAG, "getCarFrameNumber: " + i.getCarFrameNumber());
                    Log.i(TAG, "getCarNumber: " + i.getCarNumber());
                    Log.i(TAG, "getRfidDeviceNumber: " + i.getRfidDeviceNumber());
                    Log.i(TAG, "getCarVolume: " + i.getCarVolume());
                    Log.i(TAG, "------------------");
                }
                Global.deviceInfoPageVo = deviceInfoPageVo;
                // save to local
                File file = new File(Global.deviceListBin);
                if (!file.exists()) {
                    try {
                        if (!file.createNewFile()) {
                            Log.w(TAG, "can not create DeviceList.bin");
                            return null;
                        }
                        Log.i(TAG, "Created DeviceList.bin");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(deviceInfoPageVo.toByteArray(), 0, deviceInfoPageVo.getSerializedSize());
                    Log.i(TAG, Global.deviceListBin + " size " + deviceInfoPageVo.getSerializedSize());
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    // 挖机自身设备信息
    public static class DownloadSelfInfo extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String dbName = strings[0];
            String excavatorId = strings[1];

            if (channel == null)
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            DiggingsGrpc.DiggingsBlockingStub stub = DiggingsGrpc.newBlockingStub(channel);

            DiggingsOuterClass.GetRequest request = DiggingsOuterClass.GetRequest.newBuilder()
                    .setRelativePath("/public/device/findDeviceById/" + excavatorId + "?dbName=" + dbName)
                    .build();

            DiggingsOuterClass.DeviceInfoResponse response = null;
            try {
                response = stub.findDeviceInfoById(request);
            } catch (io.grpc.StatusRuntimeException e) {
                e.printStackTrace();
                return null;
            }

            Log.i(TAG, "DownloadSelfInfo : " + response.getCode() + " " + response.getMessage());

            // write to cache
            if (response.getMessage().equals("Success")) {
                Message message = new Message();
                message.what = Global.UI_WHAT.ONLINE.value;
                if (Global.handlerUI != null) {
                    Global.handlerUI.sendMessage(message);
                }

//                String exExpireMinStr = response.getData().getExcavatorParam().getExExpireMin();
//                String exFreezeMinStr = response.getData().getExcavatorParam().getExFreezeMin();
//                int exExpireMin = 1; // default 1 min
//                int exFreezeMin = 5; // default 5 min
//                if (!exExpireMinStr.equals("")) {
//                    exExpireMin = Integer.valueOf(exExpireMinStr);
//                    Global.exExpireMin = exExpireMin;
//                }
//                if (!exFreezeMinStr.equals("")) {
//                    exFreezeMin = Integer.valueOf(exFreezeMinStr);
//                    Global.exFreezeMin = exFreezeMin;
//                }
//                Log.i(TAG, "getExExpireMin: " + exExpireMin + "  getExFreezeMin: " + exFreezeMin);
                Global.selfInfo = response;

                Log.i(TAG, "###############################");
                if (Global.selfInfo.getData().hasExcavatorParam()) {
                    Global.exFreezeMin = Integer.parseInt(Global.selfInfo.getData().getExcavatorParam().getExFreezeMin());
                    Global.exExpireMin = Integer.parseInt(Global.selfInfo.getData().getExcavatorParam().getExExpireMin());
                    Global.exEntruckingMin = Integer.parseInt(Global.selfInfo.getData().getExcavatorParam().getExEntruckingMin());
                    Log.i(TAG, "exExpireMin: " + Global.exExpireMin);
                    Log.i(TAG, "exFreezeMin: " + Global.exFreezeMin);
                    Log.i(TAG, "exEntruckingMin: " + Global.exEntruckingMin);
                } else {
                    Log.i(TAG, "default exExpireMin: " + Global.exExpireMin);
                    Log.i(TAG, "default exFreezeMin: " + Global.exFreezeMin);
                    Log.i(TAG, "default exEntruckingMin: " + Global.exEntruckingMin);

                }
                Log.i(TAG, "getCarFrameNumber: " + Global.selfInfo.getData().getCarFrameNumber());
                Log.i(TAG, "getCarNumber: " + Global.selfInfo.getData().getCarNumber());
                Log.i(TAG, "getCarTypeName: " + Global.selfInfo.getData().getCarTypeName());
                Log.i(TAG, "getId: " + Global.selfInfo.getData().getId());
                Log.i(TAG, "getCarType: " + Global.selfInfo.getData().getCarType());
                Log.i(TAG, "getCarVolume: " + Global.selfInfo.getData().getCarVolume());
                Log.i(TAG, "getOptime: " + Global.selfInfo.getData().getOptime());
                Log.i(TAG, "getRemark: " + Global.selfInfo.getData().getRemark());
                Log.i(TAG, "getRfidDeviceNumber: " + Global.selfInfo.getData().getRfidDeviceNumber());
                Log.i(TAG, "getSubTypeName: " + Global.selfInfo.getData().getSubTypeName());
                Log.i(TAG, "getDelFlag: " + Global.selfInfo.getData().getDelFlag());
                Log.i(TAG, "getIfLease: " + Global.selfInfo.getData().getIfLease());
                Log.i(TAG, "###############################");

                Message message2 = new Message();
                Bundle bundle = new Bundle();
                bundle.putString("NUMBER", Global.selfInfo.getData().getCarNumber());
                message2.setData(bundle);
                message2.what = Global.UI_WHAT.SELF_CAR_NUMBER.value;
                if (Global.handlerUI != null) {
                    Global.handlerUI.sendMessage(message2);
                }

                /// save to local
                File file = new File(Global.selfInfoBin);
                if (!file.exists()) {
                    try {
                        if (!file.createNewFile()) {
                            Log.w(TAG, "can not create selfInfo.bin");
                            return null;
                        }
                        Log.i(TAG, "Created selfInfo.bin");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(response.toByteArray(), 0, response.getSerializedSize());
                    Log.i(TAG, Global.selfInfoBin + " size " + response.getSerializedSize());
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    public static class DownloadShiftInfo extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String dbName = strings[0];

            if (channel == null)
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            DiggingsGrpc.DiggingsBlockingStub stub = DiggingsGrpc.newBlockingStub(channel);

            DiggingsOuterClass.GetRequest request = DiggingsOuterClass.GetRequest.newBuilder()
                    .setRelativePath("/public/system/findBuilderShift?dbName=" + dbName)
                    .build();

            DiggingsOuterClass.BuilderShiftResponse response = null;
            try {
                response = stub.findBuilderShift(request);
            } catch (io.grpc.StatusRuntimeException e) {
                e.printStackTrace();
                return null;
            }

            Log.i(TAG, "DownloadShiftInfo : " + response.getCode() + " " + response.getMessage());
            Global.shiftInfo = response;
            Global.dayShiftStart = Global.shiftInfo.getData().getDayStart();
            Global.nightShiftStart = Global.shiftInfo.getData().getNightStart();

            // write to cache
            if (response.getMessage().equals("Success")) {

                Log.i(TAG, "###############################");
                Log.i(TAG, "getId: " + response.getData().getId());
                Log.i(TAG, "getOptime: " + response.getData().getOptime());
                Log.i(TAG, "getDayStart: " + response.getData().getDayStart());
                Log.i(TAG, "getDayEnd: " + response.getData().getDayEnd());
                Log.i(TAG, "getNightStart: " + response.getData().getNightStart());
                Log.i(TAG, "getNightEnd: " + response.getData().getNightEnd());
                Log.i(TAG, "getNewest: " + response.getData().getNewest());
                Log.i(TAG, "getShiftDate: " + response.getData().getShiftDate());
                Log.i(TAG, "getBuilderShift: " + response.getData().getBuilderShift());
                Log.i(TAG, "###############################");


                /// save to local
                File file = new File(Global.shiftInfoBin);
                if (!file.exists()) {
                    try {
                        if (!file.createNewFile()) {
                            Log.w(TAG, "can not create shiftInfoBin.bin");
                            return null;
                        }
                        Log.i(TAG, "Created shiftInfoBin.bin");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(response.toByteArray(), 0, response.getSerializedSize());
                    Log.i(TAG, Global.shiftInfoBin + " size " + response.getSerializedSize());
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                /// save to config file for UI init fast read
                File config = new File(Global.shiftConfig);
                if (!config.exists()) {
                    try {
                        if (config.createNewFile()) {
                            Log.i(TAG, "create " + Global.shiftConfig);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    OutputStream outputStream = new FileOutputStream(config);
                    String _day = "DayShift=" + Global.dayShiftStart + "\n";
                    String _night = "NightShift=" + Global.nightShiftStart + "\n";
                    outputStream.write(_day.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(_night.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    outputStream.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    public static class UploadRecord extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... strings) {
            String dbName = strings[0];
            String excavatorId = strings[1];
            String rfidReaderNo = strings[2];
            String rfid = strings[3];
            String picPath = strings[4];
            String json = "{";

            long timestamp = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm:ss");
            String date = sdf.format(timestamp);
            String time = sdf2.format(timestamp);

            ////// add record /////
            String carNumber = null;
            for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                if (info.getRfidDeviceNumber().equals(rfid)) {
                    carNumber = info.getCarNumber();
                    break;
                }
            }
            Message message = new Message();
            Bundle bundle = new Bundle();
            message.what = Global.UI_WHAT.RECORD_ADD.value;
            bundle.putString("NUMBER", carNumber);
            bundle.putString("TIME", time);
            message.setData(bundle);
            if (Global.handlerUI != null) {
                Global.handlerUI.sendMessage(message);
            }

            json += "\"loadTime\":\"" + date + "\",";
            json += "\"excavatorId\":\"" + excavatorId + "\",";
            json += "\"rfid\":\"" + rfid + "\",";
            json += "\"picAddress\":\"" + picPath;
            json += "?rfidNo=" + rfidReaderNo + "\"}";

            Log.i(TAG, "json: " + json);

            if (channel == null)
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            DiggingsGrpc.DiggingsBlockingStub stub = DiggingsGrpc.newBlockingStub(channel);

            DiggingsOuterClass.PostRequest request = DiggingsOuterClass.PostRequest.newBuilder()
                    .setRelativePath("/public/loading/saveLoadingRecord?dbName=" + dbName)
                    .setParamJSON(json)
                    .build();

            DiggingsOuterClass.CommonResponse response = null;
            try {
                response = stub.saveLoadingRecord(request);
            } catch (io.grpc.StatusRuntimeException e) {
                e.printStackTrace();
                Log.w(TAG, "saveLoadingRecord fail, save to local sqlite");
                /// save to local sqlite
                if (Global.localRecordSQLite != null) {
                    Global.localRecordSQLite.addRecord(carNumber, time, date, dbName, excavatorId, rfidReaderNo, rfid, picPath, json, false);
                }

                return null;
            }
            if (Global.localRecordSQLite != null) {
                Global.localRecordSQLite.addRecord(carNumber, time, date, dbName, excavatorId, rfidReaderNo, rfid, picPath, json, true);
                Log.i(TAG, "UploadRecord : " + response.getCode() + " " + response.getMessage());
            }

            return null;
        }
    }

    public static class UploadRecord2 extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... strings) {
            String dbName = strings[0];
            String json = strings[1];
            String id = strings[2];

            if (channel == null)
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            DiggingsGrpc.DiggingsBlockingStub stub = DiggingsGrpc.newBlockingStub(channel);

            DiggingsOuterClass.PostRequest request = DiggingsOuterClass.PostRequest.newBuilder()
                    .setRelativePath("/public/loading/saveLoadingRecord?dbName=" + dbName)
                    .setParamJSON(json)
                    .build();

            DiggingsOuterClass.CommonResponse response = null;
            try {
                response = stub.saveLoadingRecord(request);
            } catch (io.grpc.StatusRuntimeException e) {
                e.printStackTrace();
                Log.i(TAG, "UploadRecord2 Fail: ");
                return null;
            }

            Log.i(TAG, "UploadRecord2 Success: " + response.getCode() + " " + response.getMessage());
            if (Global.localRecordSQLite != null) {
                int i = Global.localRecordSQLite.settingUploadRecordStatus(id, "1");
                Log.i(TAG, "UploadRecord2 DB update return " + i);
            }

            return null;
        }
    }

    public static class UploadImage extends AsyncTask<String, Void, Void> {

        private volatile boolean grpcFinish = false;

        public boolean isGrpcFinish() {
            return grpcFinish;
        }

        @Override
        protected Void doInBackground(String... strings) {
            String rfidReaderNo = strings[0];
            String picPath = strings[1];
            FileInputStream pic = null;
            byte[] picBytes = null;

            try {
                // picture path looks like: pictureRoot/rfidReadNo/date/picName, e.g. /sdcard/DCIM/2/20220521/0307988D210359042D0004FF_150137.jpg
                String file = Global.pictureRoot + rfidReaderNo + picPath;
                Log.i(TAG, "image file " + file);
                pic = new FileInputStream(file);
                picBytes = new byte[pic.available()];
                pic.read(picBytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, picPath + " open fail, do nothing !");
                grpcFinish = true;
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                grpcFinish = true;
                Log.e(TAG, picPath + " read fail, do nothing !");
                return null;
            }

            if (channel == null)
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            DiggingsGrpc.DiggingsStub stub = DiggingsGrpc.newStub(channel);

            DiggingsOuterClass.StreamImageRequest image = DiggingsOuterClass.StreamImageRequest.newBuilder()
                    .setRfidNo(Integer.parseInt(rfidReaderNo))
                    .setImagePath(picPath)
                    .setReqStream(ByteString.copyFrom(picBytes))
                    .build();

            StreamObserver<DiggingsOuterClass.StreamResponse> response = new StreamObserver<DiggingsOuterClass.StreamResponse>() {
                @Override
                public void onNext(DiggingsOuterClass.StreamResponse value) {
                    Log.i(TAG, "Uploading Image : " + value.getRspType() + " " + value.getRfidNo());
                }

                @Override
                public void onError(Throwable t) {
                    Log.i(TAG, "Upload Image Fail : " + t.getMessage());
                    grpcFinish = true;
                }

                @Override
                public void onCompleted() {
                    Log.i(TAG, "Upload Image Completed");
                    grpcFinish = true;
                }
            };

            StreamObserver<DiggingsOuterClass.StreamImageRequest> request = stub.streamExImage(response);
            request.onNext(image);
            request.onCompleted();
            grpcFinish = false;
            Log.i(TAG, "Uploading Image...");

            return null;
        }
    }
}
