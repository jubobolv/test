package com.b2kylin.smart_rfid;

import android.Manifest;
import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.b2kylin.grpc.DiggingsAction;
import com.b2kylin.misc.ImageSaver;
import com.b2kylin.misc.LocalRecordSQLite;
import com.b2kylin.misc.SetPreviewAndCapture;
import com.b2kylin.misc.SoundPlayer;
import com.b2kylin.misc.Utils;
import com.b2kylin.smart_rfid.databinding.ActivityFullscreenBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import commons.proto.DiggingsOuterClass;

//  adb shell wm density 160
public class FullscreenActivity extends AppCompatActivity {
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    TextView textViewRecordCarNumber1;
    TextView textViewRecordCarNumber2;
    TextView textViewRecordCarNumber3;
    TextView textViewRecordCarNumber4;
    TextView textViewRecordCarNumber5;
    TextView textViewRecordDate1;
    TextView textViewRecordDate2;
    TextView textViewRecordDate3;
    TextView textViewRecordDate4;
    TextView textViewRecordDate5;
    TextView textViewSelfCarNumber;
    TextView textViewOnlineStatus;
    TextView textViewLoadCount;
    TextView textViewLoadTotal;
    TextView textViewDevicesCarNumber;
    ImageView imageViewLoadingEnable;
    ImageView imageViewCameraCapture;
    ImageView imageViewCameraNotCapture;
    ImageView imageViewLoading;
    ImageView imageViewLoadingNothing;
    ImageView imageViewLoadSuccess;
    ImageView imageViewOnlineDot;
    ImageView imageViewOfflineDot;
    ImageView imageViewRectUndo;
    ImageView imageViewUndo;
    FrameLayout frameLayoutUndo;
    TextView textureViewUndo1;
    TextView textureViewUndo2;
    TextView textureViewUndo3;
    TextView textureViewUndo4;
    long humanInputTimestamp = 0;
    long undoTimeout = 21;
    UndoCheckThread undoCheckThread = null;

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }

            hideNav();
        }
    };

    private void hideNav() {
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private ActivityFullscreenBinding binding;

    BroadcastReceiver broadcastReceiver;
    private final String TAG = "MainActivity";
    private SoundPlayer soundPlayer = null;
    private BackGroundTasks backGroundTasks;
    private volatile String currentRfid = null;
    private IntentFilter intentFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mContentView = binding.fullscreenContent;

        hide();
        checkPermission();
        initConfig();

        intentFilter = new IntentFilter();
        intentFilter.addAction(Global.bigButtonBroadcast);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String buttonStatus = (String) intent.getExtras().get("STATUS");
                if (buttonStatus.equals("DOWN")) {
                    Log.i(TAG, "BigButton: " + buttonStatus);
                    load();
                }
            }
        };

        View viewSettings = findViewById(R.id.viewSettings);
        viewSettings.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent intent = new Intent(FullscreenActivity.this, SettingActivity.class);
                startActivity(intent);
                return true;
            }
        });

        ImageView imageViewRecordListShow = findViewById(R.id.imageViewRecordListShow);
        imageViewRecordListShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(FullscreenActivity.this, RecordListActivity.class);
                startActivity(intent);
            }
        });

        Global.localRecordSQLite = new LocalRecordSQLite(FullscreenActivity.this, Global.localRecordDb, null, 1);
        uiUpdate();

        ///// shift show
        Global.SHIFT_MODE shiftMode;
        String[] dayStartStr = Global.dayShiftStart.split(":");
        int dayStartHour = Integer.parseInt(dayStartStr[0]);
        int dayStartMinute = Integer.parseInt(dayStartStr[1]);
        int dayStartSecond = Integer.parseInt(dayStartStr[2]);
        Log.i(TAG, "config day: " + dayStartHour + " " + dayStartMinute + " " + dayStartSecond);
        String[] nightStartStr = Global.nightShiftStart.split(":");
        int nightStartHour = Integer.parseInt(nightStartStr[0]);
        int nightStartMinute = Integer.parseInt(nightStartStr[1]);
        int nightStartSecond = Integer.parseInt(nightStartStr[2]);
        Log.i(TAG, "config night: " + nightStartHour + " " + nightStartMinute + " " + nightStartSecond);

        Date date = Calendar.getInstance().getTime();
        int nowHour = date.getHours();
        int nowMinute = date.getMinutes();
        int nowSecond = date.getSeconds();
        Log.i(TAG, "config now: " + nowHour + " " + nowMinute + " " + nowSecond);

        if (nowHour >= dayStartHour && nowHour <= nightStartHour) {
            if ((nowHour == dayStartHour && nowMinute < dayStartMinute) || (nowHour == nightStartHour && nowMinute >= nightStartMinute)) {
                shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
            } else {
                shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
            }
        } else {
            shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
        }

        Log.i(TAG, "init shift mode: " + shiftMode);

        if (shiftMode == Global.SHIFT_MODE.DAY_SHIFT_MODE) {
            dayShiftShow();
        } else {
            nightShiftShow();
        }
        //// end

        //// start
        Global.sharedPreferences = FullscreenActivity.this.getSharedPreferences("TMP", MODE_PRIVATE);
        long tp = Global.sharedPreferences.getLong("TP", -1);
        int loadCount = Global.sharedPreferences.getInt("COUNT", -1);
        float loadTotal = Global.sharedPreferences.getFloat("TOTAL", -1);
        int mode = Global.sharedPreferences.getInt("MODE", -1);
        Log.i(TAG, "---------------------------: " + tp + " " + loadCount + " " + loadTotal);
        if (tp == -1 || loadTotal < 0 || loadCount == -1 || System.currentTimeMillis() - tp > 1000 * 60 * 60 * 24/*hour*/) {
            Global.loadCount = 0;
            Global.loadTotal = 0;
            textViewLoadCount.setText("0");
            textViewLoadTotal.setText("0.0");
        } else {
            // 班次切换，直接清零
            if (shiftMode.value != mode) {
                SharedPreferences.Editor editor = Global.sharedPreferences.edit();
                editor.putInt("COUNT", 0);
                editor.putFloat("TOTAL", 0f);
                editor.apply();
                Global.loadCount = 0;
                Global.loadTotal = 0;
            } else {
                // 班次未变化，判断和上次装车时差大于16小时，则表示跳过了一个班次，清零
                if (System.currentTimeMillis() - tp > 1000 * 60 * 60 * 16/*hour*/) {
                    SharedPreferences.Editor editor = Global.sharedPreferences.edit();
                    editor.putInt("COUNT", 0);
                    editor.putFloat("TOTAL", 0f);
                    editor.apply();
                    Global.loadCount = 0;
                    Global.loadTotal = 0;
                } else {
                    Global.loadTotal = loadTotal;
                    Global.loadCount = loadCount;
                }
            }

            textViewLoadCount.setText(String.valueOf(Global.loadCount));
            java.text.DecimalFormat format = new java.text.DecimalFormat("0.0");
            textViewLoadTotal.setText(format.format(Global.loadTotal));
        }
        ///// end
        initRecords(shiftMode, dayStartHour, dayStartMinute, nightStartHour, nightStartMinute);

        backGroundTasks = new BackGroundTasks(this);
        backGroundTasks.initAll();
        initCameraView();
        Global.rfidPowerOn();
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, AUTO_HIDE_DELAY_MILLIS);
    }

    void load() {
        if (System.currentTimeMillis() - Global.loadSuccessTimestamp < 1000 * Global.exEntruckingMin) {
            return;
        }
        if (backGroundTasks.rfidReader.getCurrentDetectedRfid().equals("") && !textViewDevicesCarNumber.getText().equals("等待卡车...")) {
            boolean isFound = false;
            String carNumber = textViewDevicesCarNumber.getText().toString();
            if (Global.deviceInfoPageVo != null) {
                for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                    if (info.getCarNumber().equals(carNumber)) {
                        currentRfid = info.getRfidDeviceNumber();
                        isFound = true;
                        break;
                    }
                }
            } else {
                return;
            }
            if (!isFound)
                return;
        } else {
            currentRfid = backGroundTasks.rfidReader.getCurrentDetectedRfid();
            if (currentRfid == null || currentRfid.length() == 0) {
                Log.w(TAG, "rfid null do nothing");
                return;
            }
        }

        setPreviewAndCapture.takePhoto();

        Message message = new Message();
        message.what = Global.UI_WHAT.LOAD_SUCCESS_STATUS.value;
        if (Global.handlerUI != null) {
            Global.handlerUI.sendMessage(message);
        }

        if (soundPlayer != null) soundPlayer.release();
        soundPlayer = new SoundPlayer();
        AssetFileDescriptor assetFileDescriptor = getResources().openRawResourceFd(R.raw.success);
        soundPlayer.execute(assetFileDescriptor);
        Global.loadSuccessTimestamp = System.currentTimeMillis();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Log.i(TAG, "onActivityResult: " + data.getStringExtra("NUMBER"));
            textViewDevicesCarNumber.setText(data.getStringExtra("NUMBER"));
            humanInputTimestamp = System.currentTimeMillis();
            // 装车状态
            imageViewLoading.setVisibility(ImageView.VISIBLE);
            imageViewLoadingNothing.setVisibility(ImageView.GONE);
            imageViewLoadSuccess.setVisibility(ImageView.GONE);
            imageViewLoadingEnable.setVisibility(ImageView.VISIBLE);
            imageViewCameraNotCapture.setVisibility(ImageView.VISIBLE);
            imageViewCameraCapture.setVisibility(ImageView.GONE);
            frameLayoutUndo.setVisibility(View.INVISIBLE);
        }
    }

    /////////////
    ///// UI ////
    /////////////
    void dayShiftShow() {
        binding.getRoot().setBackgroundResource(R.drawable.full_bg);
        textViewRecordCarNumber1.setTextColor(Color.BLACK);
        textViewRecordCarNumber2.setTextColor(Color.BLACK);
        textViewRecordCarNumber3.setTextColor(Color.BLACK);
        textViewRecordCarNumber4.setTextColor(Color.BLACK);
        textViewRecordCarNumber5.setTextColor(Color.BLACK);
        textViewSelfCarNumber.setTextColor(Color.BLACK);
        textViewOnlineStatus.setTextColor(Color.BLACK);
        textViewLoadCount.setTextColor(Color.BLACK);
        textViewLoadTotal.setTextColor(Color.BLACK);
        textViewDevicesCarNumber.setTextColor(Color.BLACK);
        imageViewLoadingEnable.setImageResource(R.drawable.big_button);
        imageViewCameraCapture.setImageResource(R.drawable.capture);
        imageViewCameraNotCapture.setImageResource(R.drawable.not_capture);

        imageViewRectUndo.setImageResource(R.drawable.rect_undo);
        textureViewUndo1.setTextColor(0xff0088FE);
        textureViewUndo2.setTextColor(0xff0088FE);
        textureViewUndo3.setTextColor(Color.BLACK);
        textureViewUndo4.setTextColor(0xff0088FE);
    }

    void nightShiftShow() {
        binding.getRoot().setBackgroundResource(R.drawable.dark_full_bg);
        textViewRecordCarNumber1.setTextColor(Color.WHITE);
        textViewRecordCarNumber2.setTextColor(Color.WHITE);
        textViewRecordCarNumber3.setTextColor(Color.WHITE);
        textViewRecordCarNumber4.setTextColor(Color.WHITE);
        textViewRecordCarNumber5.setTextColor(Color.WHITE);
        textViewSelfCarNumber.setTextColor(Color.WHITE);
        textViewOnlineStatus.setTextColor(Color.WHITE);
        textViewLoadCount.setTextColor(Color.WHITE);
        textViewLoadTotal.setTextColor(Color.WHITE);
        textViewDevicesCarNumber.setTextColor(getResources().getColor(R.color.my_color));
        imageViewLoadingEnable.setImageResource(R.drawable.dark_big_button);
        imageViewCameraCapture.setImageResource(R.drawable.dark_capture);
        imageViewCameraNotCapture.setImageResource(R.drawable.dark_not_capture);

        imageViewRectUndo.setImageResource(R.drawable.dark_rect_undo);
        textureViewUndo1.setTextColor(0xff00ffff);
        textureViewUndo2.setTextColor(0xff00ffff);
        textureViewUndo3.setTextColor(Color.WHITE);
        textureViewUndo4.setTextColor(0xff00ffff);
    }

    void uploadRecord(String dbName, String excavatorId, String rfidReaderNo, String rfid, String usefulName) {
        DiggingsAction.UploadRecord record = new DiggingsAction.UploadRecord();
        record.execute(dbName, excavatorId, rfidReaderNo, rfid, usefulName);

        for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
            if (info.getRfidDeviceNumber().equals(rfid)) {
                java.text.DecimalFormat format = new java.text.DecimalFormat("0.0");
                double volume = Double.parseDouble(info.getCarVolume());
                Global.loadTotal += volume;
                Log.i(TAG, "carNumber:" + info.getCarNumber() + " volume:" + volume + " total:" + format.format(Global.loadTotal));

                Message message = new Message();
                Bundle bundle = new Bundle();
                bundle.putString("NUMBER", String.valueOf(++Global.loadCount));
                message.setData(bundle);
                message.what = Global.UI_WHAT.LOAD_COUNT.value;

                Message message1 = new Message();
                Bundle bundle1 = new Bundle();
                bundle1.putString("NUMBER", format.format(Global.loadTotal));
                message1.setData(bundle1);
                message1.what = Global.UI_WHAT.LOAD_TOTAL.value;
                if (Global.handlerUI != null) {
                    Global.handlerUI.sendMessage(message);
                    Global.handlerUI.sendMessage(message1);
                }

                SharedPreferences.Editor editor = Global.sharedPreferences.edit();
                editor.putInt("COUNT", Global.loadCount);
                editor.putFloat("TOTAL", (float) Global.loadTotal);
                editor.putInt("MODE", Global.shiftMode.value);
                editor.putLong("TP", System.currentTimeMillis());
                editor.apply();

                Global.localRecordSQLite.flushOverview(info.getCarNumber());

                break;
            }
        }
    }

    class UndoCheckThread extends Thread {
        volatile boolean exit = false;

        @Override
        public void interrupt() {
            exit = true;
            super.interrupt();
        }

        @Override
        public void run() {
            super.run();
            while (!exit) {

                if (undoTimeout-- <= 1) {
                    break;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textureViewUndo3.setText("" + undoTimeout);
                    }
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    frameLayoutUndo.setVisibility(View.INVISIBLE);
                }
            });

            if (undoTimeout <= 0) { // 超时退出，正常上传
                backGroundTasks.recordSave(currentRfid);
                if (Global.localRecordSQLite != null) {
                    // string[](dbName, excavatorId, rfidReaderNo, rfid, picPath, timestamp)
                    ArrayList<String> record = Global.localRecordSQLite.readTmpRecord();
                    if (record.size() != 0) {
                        uploadRecord(record.get(0), record.get(1), record.get(2), record.get(3), record.get(4));
                    }
                }
            } else { // 主动撤销
                if (Global.localRecordSQLite != null) {
                    Global.localRecordSQLite.delTmpRecord();
                }
                // 撤销后允许立刻装车
                Global.loadSuccessTimestamp = 0;
            }

            undoTimeout = 21;
        }
    }

    void uiUpdate() {
        textViewRecordCarNumber1 = findViewById(R.id.textViewRecordCarNumber1);
        textViewRecordCarNumber2 = findViewById(R.id.textViewRecordCarNumber2);
        textViewRecordCarNumber3 = findViewById(R.id.textViewRecordCarNumber3);
        textViewRecordCarNumber4 = findViewById(R.id.textViewRecordCarNumber4);
        textViewRecordCarNumber5 = findViewById(R.id.textViewRecordCarNumber5);
        textViewRecordDate1 = findViewById(R.id.textViewRecordDate1);
        textViewRecordDate2 = findViewById(R.id.textViewRecordDate2);
        textViewRecordDate3 = findViewById(R.id.textViewRecordDate3);
        textViewRecordDate4 = findViewById(R.id.textViewRecordDate4);
        textViewRecordDate5 = findViewById(R.id.textViewRecordDate5);

        imageViewLoading = findViewById(R.id.imageViewLoading);
        imageViewLoadingNothing = findViewById(R.id.imageViewLoadingNothing);
        imageViewLoadSuccess = findViewById(R.id.imageViewLoadSuccess);
        imageViewLoadingEnable = findViewById(R.id.imageViewLoadingEnable);
        imageViewCameraCapture = findViewById(R.id.imageViewCameraCapture);
        imageViewCameraNotCapture = findViewById(R.id.imageViewCameraNotCapture);
        imageViewOnlineDot = findViewById(R.id.imageViewOnlineDot);
        imageViewOfflineDot = findViewById(R.id.imageViewOfflineDot);
        textViewDevicesCarNumber = findViewById(R.id.textViewDevicesCarNumber);
        textViewLoadCount = findViewById(R.id.textViewLoadCount);
        textViewLoadTotal = findViewById(R.id.textViewLoadTotal);
        textViewOnlineStatus = findViewById(R.id.textViewOnlineStatus);
        textViewSelfCarNumber = findViewById(R.id.textViewSelfCarNumber);

        frameLayoutUndo = findViewById(R.id.frameLayoutUndo);
        imageViewRectUndo = findViewById(R.id.imageViewRectUndo);
        imageViewUndo = findViewById(R.id.imageViewUndo);
        textureViewUndo1 = findViewById(R.id.textureViewUndo1);
        textureViewUndo2 = findViewById(R.id.textureViewUndo2);
        textureViewUndo3 = findViewById(R.id.textureViewUndo3);
        textureViewUndo4 = findViewById(R.id.textureViewUndo4);

        textViewLoadCount.setText(String.valueOf(Global.loadCount));
        textViewLoadTotal.setText(String.valueOf(Global.loadTotal));

        imageViewUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "onClick: imageViewUndo");
                if (undoCheckThread != null) {
                    undoCheckThread.interrupt();
                }
            }
        });

        imageViewLoadingEnable.setOnClickListener(new View.OnClickListener() {
            volatile long last = 0;

            @Override
            public void onClick(View view) {

                if (System.currentTimeMillis() - last < 1000 * 10) {
                    Log.i(TAG, "onClick: do nothing.");
                    return;
                }
                last = System.currentTimeMillis();
                load();
            }
        });

        textViewDevicesCarNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (System.currentTimeMillis() - Global.loadSuccessTimestamp < 1000 * Global.exEntruckingMin) {
                    Log.i(TAG, "loaded success delay, do nothing.");
                    return;
                }
                // RFID 识别正常不允许输入
                if (!backGroundTasks.rfidReader.getCurrentDetectedRfid().equals("")) {
                    return;
                }
                Intent intent = new Intent(FullscreenActivity.this, InputActivity.class);
                startActivityForResult(intent, 1);
            }
        });

        Global.handlerUI = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message message) {
                switch (Global.UI_WHAT.values()[message.what]) {
                    case ONLINE:
                        textViewOnlineStatus.setText("设备在线");
                        imageViewOnlineDot.setVisibility(ImageView.VISIBLE);
                        imageViewOfflineDot.setVisibility(ImageView.GONE);
                        break;
                    case OFFLINE:
                        textViewOnlineStatus.setText("设备离线");
                        imageViewOnlineDot.setVisibility(ImageView.GONE);
                        imageViewOfflineDot.setVisibility(ImageView.VISIBLE);
                        break;
                    case DEVICE_CAR_NUMBER:
                        if (System.currentTimeMillis() - Global.loadSuccessTimestamp < 1000 * Global.exEntruckingMin) {
                            return true;
                        }
                        String number1 = message.getData().getString("NUMBER");
                        if (!number1.equals("")) textViewDevicesCarNumber.setText(number1);
                        imageViewCameraNotCapture.setVisibility(ImageView.VISIBLE);
                        imageViewCameraCapture.setVisibility(ImageView.GONE);
                        break;
                    case LOADING_STATUS:
                        if (System.currentTimeMillis() - Global.loadSuccessTimestamp < 1000 * Global.exEntruckingMin) {
                            return true;
                        }
                        imageViewLoading.setVisibility(ImageView.VISIBLE);
                        imageViewLoadingNothing.setVisibility(ImageView.GONE);
                        imageViewLoadSuccess.setVisibility(ImageView.GONE);
                        imageViewLoadingEnable.setVisibility(ImageView.VISIBLE);
                        imageViewCameraNotCapture.setVisibility(ImageView.VISIBLE);
                        imageViewCameraCapture.setVisibility(ImageView.GONE);
                        frameLayoutUndo.setVisibility(View.INVISIBLE);
                        break;
                    case LOADING_NOTHING_STATUS:
                        if (System.currentTimeMillis() - Global.loadSuccessTimestamp < 1000 * Global.exEntruckingMin) {
                            return true;
                        }
                        // 未检出RFID，手动输入20s内不清除显示
                        if (backGroundTasks.rfidReader.getCurrentDetectedRfid().equals("") && System.currentTimeMillis() - humanInputTimestamp < 1000 * 20/*s*/)
                            break;
                        imageViewLoading.setVisibility(ImageView.GONE);
                        imageViewLoadingNothing.setVisibility(ImageView.VISIBLE);
                        imageViewLoadSuccess.setVisibility(ImageView.GONE);
                        imageViewLoadingEnable.setVisibility(ImageView.INVISIBLE);
                        imageViewCameraNotCapture.setVisibility(ImageView.VISIBLE);
                        imageViewCameraCapture.setVisibility(ImageView.GONE);
                        textViewDevicesCarNumber.setText("等待卡车...");
                        frameLayoutUndo.setVisibility(View.INVISIBLE);
                        break;
                    case LOAD_SUCCESS_STATUS:
                        imageViewLoading.setVisibility(ImageView.GONE);
                        imageViewLoadingNothing.setVisibility(ImageView.GONE);
                        imageViewLoadSuccess.setVisibility(ImageView.VISIBLE);
                        imageViewLoadingEnable.setVisibility(ImageView.INVISIBLE);
                        imageViewCameraNotCapture.setVisibility(ImageView.GONE);
                        imageViewCameraCapture.setVisibility(ImageView.VISIBLE);
                        frameLayoutUndo.setVisibility(View.VISIBLE);
                        undoCheckThread = new UndoCheckThread();
                        undoCheckThread.start();
                        break;
                    case SELF_CAR_NUMBER:
                        String number = message.getData().getString("NUMBER");
                        textViewSelfCarNumber.setText(number);
                        break;
                    case LOAD_COUNT:
                        String _count = message.getData().getString("NUMBER");
                        textViewLoadCount.setText(_count);
                        // 当清空装车次数时清除历史记录
                        if (_count.equals("0")) {
                            textViewRecordCarNumber5.setText("");
                            textViewRecordCarNumber4.setText("");
                            textViewRecordCarNumber3.setText("");
                            textViewRecordCarNumber2.setText("");
                            textViewRecordCarNumber1.setText("");
                            textViewRecordDate5.setText("");
                            textViewRecordDate4.setText("");
                            textViewRecordDate3.setText("");
                            textViewRecordDate2.setText("");
                            textViewRecordDate1.setText("");
                        }
                        break;
                    case LOAD_TOTAL:
                        String _total = message.getData().getString("NUMBER");
                        textViewLoadTotal.setText(_total);
                        break;
                    case RECORD_ADD:
                        String carNumber = message.getData().getString("NUMBER");
                        String time = message.getData().getString("TIME");
                        textViewRecordCarNumber5.setText(textViewRecordCarNumber4.getText());
                        textViewRecordCarNumber4.setText(textViewRecordCarNumber3.getText());
                        textViewRecordCarNumber3.setText(textViewRecordCarNumber2.getText());
                        textViewRecordCarNumber2.setText(textViewRecordCarNumber1.getText());
                        textViewRecordCarNumber1.setText(carNumber);
                        textViewRecordDate5.setText(textViewRecordDate4.getText());
                        textViewRecordDate4.setText(textViewRecordDate3.getText());
                        textViewRecordDate3.setText(textViewRecordDate2.getText());
                        textViewRecordDate2.setText(textViewRecordDate1.getText());
                        textViewRecordDate1.setText(time);
                        Log.i(TAG, "handleMessage: " + carNumber + " " + time);
                        break;
                    case USB_COPY_STATUS:
                        AlertDialog alertDialog = new AlertDialog.Builder(FullscreenActivity.this).setTitle("数据拷贝").create();
                        String usbCopyStatus = message.getData().getString("STATUS");
                        if (usbCopyStatus.equals("none")) {
                            alertDialog.setMessage("无数据拷贝");
                        } else if (usbCopyStatus.equals("success")) {
                            alertDialog.setMessage("拷贝成功");
                        } else {
                            alertDialog.setMessage("拷贝失败");
                        }
                        alertDialog.show();
                        break;
                    case SWITCH_DAY_NIGHT_MODE:
                        String mode = message.getData().getString("MODE");
                        if (mode.equals("day")) {
                            dayShiftShow();
                        } else {
                            nightShiftShow();
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    ////////////////////
    ////// records /////
    ////////////////////
    void initRecords(Global.SHIFT_MODE nowShiftMode, int dayStartHour, int dayStartMinute, int nightStartHour, int nightStartMinute) {
        // String[3]{carNumber, time, date}
        ArrayList<ArrayList<String>> records = Global.localRecordSQLite.readRecord5();
        Iterator<ArrayList<String>> entry = records.iterator();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 1st list
        if (entry.hasNext()) {
            ArrayList<String> r = entry.next();
            long tp = 0;
            int hour = 0;
            int minute = 0;
            Global.SHIFT_MODE shiftMode = Global.SHIFT_MODE.UNKNOWN_MODE;
            try {
                Date date = simpleDateFormat.parse(r.get(2));
                tp = date.getTime();
                hour = date.getHours();
                minute = date.getMinutes();

                if (hour >= dayStartHour && hour <= nightStartHour) {
                    if ((hour == dayStartHour && minute < dayStartMinute) || (hour == nightStartHour && minute >= nightStartMinute)) {
                        shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                    } else {
                        shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
                    }
                } else {
                    shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - tp < 1000 * 60 * 60 * 16/*hour*/ && shiftMode == nowShiftMode) {
                textViewRecordDate1.setText(r.get(1));
                textViewRecordCarNumber1.setText(r.get(0));
            }
        }
        // 2nd list
        if (entry.hasNext()) {
            ArrayList<String> r = entry.next();
            long tp = 0;
            int hour = 0;
            int minute = 0;
            Global.SHIFT_MODE shiftMode = Global.SHIFT_MODE.UNKNOWN_MODE;
            try {
                Date date = simpleDateFormat.parse(r.get(2));
                tp = date.getTime();
                hour = date.getHours();
                minute = date.getMinutes();

                if (hour >= dayStartHour && hour <= nightStartHour) {
                    if ((hour == dayStartHour && minute < dayStartMinute) || (hour == nightStartHour && minute >= nightStartMinute)) {
                        shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                    } else {
                        shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
                    }
                } else {
                    shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - tp < 1000 * 60 * 60 * 16/*hour*/ && shiftMode == nowShiftMode) {
                textViewRecordDate2.setText(r.get(1));
                textViewRecordCarNumber2.setText(r.get(0));
            }
        }
        // 3th list
        if (entry.hasNext()) {
            ArrayList<String> r = entry.next();
            long tp = 0;
            int hour = 0;
            int minute = 0;
            Global.SHIFT_MODE shiftMode = Global.SHIFT_MODE.UNKNOWN_MODE;
            try {
                Date date = simpleDateFormat.parse(r.get(2));
                tp = date.getTime();
                hour = date.getHours();
                minute = date.getMinutes();

                if (hour >= dayStartHour && hour <= nightStartHour) {
                    if ((hour == dayStartHour && minute < dayStartMinute) || (hour == nightStartHour && minute >= nightStartMinute)) {
                        shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                    } else {
                        shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
                    }
                } else {
                    shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - tp < 1000 * 60 * 60 * 16/*hour*/ && shiftMode == nowShiftMode) {
                textViewRecordDate3.setText(r.get(1));
                textViewRecordCarNumber3.setText(r.get(0));
            }
        }
        // 4st list
        if (entry.hasNext()) {
            ArrayList<String> r = entry.next();
            long tp = 0;
            int hour = 0;
            int minute = 0;
            Global.SHIFT_MODE shiftMode = Global.SHIFT_MODE.UNKNOWN_MODE;
            try {
                Date date = simpleDateFormat.parse(r.get(2));
                tp = date.getTime();
                hour = date.getHours();
                minute = date.getMinutes();

                if (hour >= dayStartHour && hour <= nightStartHour) {
                    if ((hour == dayStartHour && minute < dayStartMinute) || (hour == nightStartHour && minute >= nightStartMinute)) {
                        shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                    } else {
                        shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
                    }
                } else {
                    shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - tp < 1000 * 60 * 60 * 16/*hour*/ && shiftMode == nowShiftMode) {
                textViewRecordDate4.setText(r.get(1));
                textViewRecordCarNumber4.setText(r.get(0));
            }
        }
        // 5st list
        if (entry.hasNext()) {
            ArrayList<String> r = entry.next();
            long tp = 0;
            int hour = 0;
            int minute = 0;
            Global.SHIFT_MODE shiftMode = Global.SHIFT_MODE.UNKNOWN_MODE;
            try {
                Date date = simpleDateFormat.parse(r.get(2));
                tp = date.getTime();
                hour = date.getHours();
                minute = date.getMinutes();

                if (hour >= dayStartHour && hour <= nightStartHour) {
                    if ((hour == dayStartHour && minute < dayStartMinute) || (hour == nightStartHour && minute >= nightStartMinute)) {
                        shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                    } else {
                        shiftMode = Global.SHIFT_MODE.DAY_SHIFT_MODE;
                    }
                } else {
                    shiftMode = Global.SHIFT_MODE.NIGHT_SHIFT_MODE;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - tp < 1000 * 60 * 60 * 16/*hour*/ && shiftMode == nowShiftMode) {
                textViewRecordDate5.setText(r.get(1));
                textViewRecordCarNumber5.setText(r.get(0));
            }
        }
    }

    ////////////////////
    ///// Configure ////
    ////////////////////
    void initConfig() {
        File config = new File(Global.config);

        if (config.exists()) {
            try {
                byte[] bytes = new byte[1024];
                InputStream inputStream = new FileInputStream(config);
                inputStream.read(bytes);
                String str = new String(bytes);
                String[] strArray = str.split("\n");
                Log.i(TAG, "read 0:" + strArray[0]);
                Log.i(TAG, "read 1:" + strArray[1]);
                Log.i(TAG, "read 2:" + strArray[2]);
                Global.dbName = strArray[0].split("=")[1];
                Global.excavatorId = strArray[1].split("=")[1];
                Global.rfidReaderNo = strArray[2].split("=")[1];
                if (strArray.length > 4) {
                    Global.fifoCheckPeriod = Integer.parseInt(strArray[3].split("=")[1]);
                    Log.i(TAG, "read 4:" + strArray[3]);
                }
                if (strArray.length > 5) {
                    Global.syncInfoPeriod = Integer.parseInt(strArray[4].split("=")[1]);
                    Log.i(TAG, "read 5:" + strArray[3]);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        File config2 = new File(Global.shiftConfig);

        if (config2.exists()) {
            try {
                byte[] bytes = new byte[1024];
                InputStream inputStream = new FileInputStream(config2);
                inputStream.read(bytes);
                String str = new String(bytes);
                String[] strArray = str.split("\n");
                Log.i(TAG, "read day:" + strArray[0]);
                Log.i(TAG, "read night:" + strArray[1]);
                Global.dayShiftStart = strArray[0].split("=")[1];
                Global.nightShiftStart = strArray[1].split("=")[1];
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    /////////////////////
    ///// Permission ////
    /////////////////////
    String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.MANAGE_EXTERNAL_STORAGE};
    List<String> mPermissionList = new ArrayList<>();
    private static final int PERMISSION_REQUEST = 1;

    private void checkPermission() {
        mPermissionList.clear();
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(this, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);
            }
        }

        if (mPermissionList.isEmpty()) {
        } else {
            String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
            ActivityCompat.requestPermissions(FullscreenActivity.this, permissions, PERMISSION_REQUEST);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(FullscreenActivity.this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }
    }

    /////////////////
    ///// camera ////
    /////////////////
    private SetPreviewAndCapture setPreviewAndCapture;
    private int currentCameraId = CameraCharacteristics.LENS_FACING_EXTERNAL;
    private Size previewSize;//图片尺寸
    private Size mWinSize;//获取屏幕的尺寸
    private ImageReader imageReader;//接受图片数据
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private TextureView textureView;
    private HandlerThread handlerThread;
    private Handler handler;

    /**
     * 加载布局，初始化组件
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initCameraView() {
        mWinSize = Utils.loadWinSize(this);
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                setAndOpenCamera();
                int height = textureView.getHeight();
                int width = textureView.getWidth();
                if (height > width) {
                    float justH = width * 4.f / 3;
                    textureView.setScaleX(height / justH);
                } else {
                    float justW = height * 4.f / 3;
                    textureView.setScaleY(width / justW);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                closeCamera();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });

        //获取相机管理
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //开启子线程，处理某些耗时操作
        handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    private void setAndOpenCamera() {
        //获取摄像头属性描述
        CameraCharacteristics cameraCharacteristics = null;
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                currentCameraId = 100;
            //根据摄像头id获取摄像头属性类
            cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(currentCameraId));
            //获取支持的缩放
            //获取该摄像头支持输出的图片尺寸
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //根据屏幕尺寸即摄像头输出尺寸计算图片尺寸，或者直接选取最大的图片尺寸进行输出
            previewSize = Utils.fitPhotoSize(map, mWinSize);
            //初始化imageReader
            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
            //设置回调处理接受图片数据
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    //发送数据进子线程处理
                    handler.post(new ImageSaver(reader.acquireNextImage(), FullscreenActivity.this, currentRfid, Global.rfidReaderNo));
                }
            }, handler);
            //打开相机，先检查权限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开摄像头
            cameraManager.openCamera(String.valueOf(currentCameraId), stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 打开相机后的状态回调，获取CameraDevice对象
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            //打开相机后开启预览，以及拍照的工具类,主要是将CameraDevice对象传递进工具类
            setPreviewAndCapture = new SetPreviewAndCapture(cameraDevice, textureView.getSurfaceTexture(), imageReader, handler, FullscreenActivity.this, previewSize);
            setPreviewAndCapture.startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            finish();
        }
    };

    /**
     * 关闭相机
     */
    private void closeCamera() {

        //关闭相机
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        //关闭拍照处理器
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backGroundTasks.destroyAll();
    }
}