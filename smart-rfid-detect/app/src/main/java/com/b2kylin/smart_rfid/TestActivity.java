package com.b2kylin.smart_rfid;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.b2kylin.misc.ImageSaver;
import com.b2kylin.misc.SetPreviewAndCapture;
import com.b2kylin.misc.SoundPlayer;
import com.b2kylin.misc.Utils;
import com.b2kylin.serial.BigButton;
import com.b2kylin.serial.RfidReader;
import com.b2kylin.grpc.DiggingsAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import commons.proto.DiggingsOuterClass;

public class TestActivity extends AppCompatActivity {
    private RfidReader rfidReader;
    BroadcastReceiver broadcastReceiver;
    private final String TAG = "MainActivity";
    private SoundPlayer soundPlayer = null;


    //////////////
    private SetPreviewAndCapture setPreviewAndCapture;
    private int currentCameraId = CameraCharacteristics.LENS_FACING_FRONT;//手机后面的摄像头
    private SurfaceHolder surfaceHolder;
    private Size mWinSize;//获取屏幕的尺寸
    private ImageReader imageReader;//接受图片数据
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private TextureView textureView;
    private HandlerThread handlerThread;
    private Size previewSize;//图片尺寸
    private Handler handler;
    ///////////

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        mWinSize = Utils.loadWinSize(this);
        Button takePicture = findViewById(R.id.takePicture);
        takePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setPreviewAndCapture.takePhoto();
            }
        });

        initView();
    }

    /**
     * 加载布局，初始化组件
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initView() {
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                setAndOpenCamera(i, i1);
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
        // https://github.com/dqx-eterning/CustomCamera/blob/master/app/src/main/java/com/dangqx/customcamera/MainActivity.java
    }

    private void setAndOpenCamera(int h, int w) {
        //获取摄像头属性描述
        CameraCharacteristics cameraCharacteristics = null;
        try {
            //根据摄像头id获取摄像头属性类
            cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(currentCameraId));
            //获取支持的缩放
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //初始化imageReader
            imageReader = ImageReader.newInstance(h, w, ImageFormat.JPEG, 2);
            //设置回调处理接受图片数据
            previewSize = new Size(w, h);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    //发送数据进子线程处理
                    handler.post(new ImageSaver(reader.acquireNextImage(), TestActivity.this, new String("NULL"), new String("NULL")));
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
            setPreviewAndCapture = new SetPreviewAndCapture(cameraDevice, textureView.getSurfaceTexture(), imageReader, handler, TestActivity.this, previewSize);
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
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }
}