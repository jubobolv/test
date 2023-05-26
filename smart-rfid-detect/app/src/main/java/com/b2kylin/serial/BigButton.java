package com.b2kylin.serial;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.b2kylin.smart_rfid.Global;
import com.cpdevice.cpcomm.exception.CPBusException;
import com.cpdevice.cpcomm.port.Port;
import com.cpdevice.cpcomm.port.Uart;

import java.nio.charset.StandardCharsets;

public class BigButton extends Thread {
    private Uart bigButton;
    private String TAG = "BUTTON";
    private final String LoopbackCmd = "DOWN";
    private long lastDownBroadcastTimeStamp = 0;
    private long lastUpBroadcastTimeStamp = 0;
    private volatile long pushDownTimeStamp = 0;
    private ButtonStatus lastButtonStatus = ButtonStatus.BUTTON_UP;
    private LocalBroadcastManager localBroadcastManager;
    private boolean exit = false;

    public enum ButtonStatus {
        BUTTON_UP, BUTTON_DOWN,
    }

    public BigButton(Context ctx) {
        localBroadcastManager = LocalBroadcastManager.getInstance(ctx);
    }

    @Override
    public void interrupt() {
        exit = true;
        super.interrupt();
    }

    @Override
    public void run() {
        super.run();
        String uartDev = "/dev/ttyS6";
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
            uartDev = "/dev/ttyS4";
        try {
            bigButton = new Uart(uartDev, Uart.B115200);
        } catch (CPBusException e) {
            e.printStackTrace();
            Log.e(TAG, "open UART file");
            return;
        }
        bigButton.setReceiveListener(new Port.DataReceiveListener() {
            @Override
            public void onReceive(byte[] bytes) {
                String cmd = new String(bytes);
                if (cmd.contains(LoopbackCmd)) {
                    pushDownTimeStamp = System.currentTimeMillis();
                }
            }
        });
        bigButton.start();

        while (!exit) {

            bigButton.send(LoopbackCmd.getBytes(StandardCharsets.UTF_8));

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }

            long now = System.currentTimeMillis();
            // 50ms 无数据则说明按键抬起
            if (now - pushDownTimeStamp > 50) {
                if (lastButtonStatus == ButtonStatus.BUTTON_UP) {
                    continue;
                }
                lastButtonStatus = ButtonStatus.BUTTON_UP;
                Log.i(TAG, "BigButton pull up");

                // 10s 内不重复触发 UP
                if (now - lastUpBroadcastTimeStamp < 1000 * 10) {
                    continue;
                }

                Intent intent = new Intent(Global.bigButtonBroadcast);
                intent.putExtra("STATUS", "UP");
                localBroadcastManager.sendBroadcast(intent);
                lastUpBroadcastTimeStamp = now;
            } else {
                // 上一状态为按下不上报，防止未释放按键而重复触发
                if (lastButtonStatus == ButtonStatus.BUTTON_DOWN) {
                    continue;
                }
                lastButtonStatus = ButtonStatus.BUTTON_DOWN;
                Log.i(TAG, "BigButton push down");

                // 10s 内不重复触发 DOWN
                if (now - lastDownBroadcastTimeStamp < 1000 * 10) {
                    continue;
                }

                Intent intent = new Intent(Global.bigButtonBroadcast);
                intent.putExtra("STATUS", "DOWN");
                localBroadcastManager.sendBroadcast(intent);
                lastDownBroadcastTimeStamp = now;
            }
        }

        bigButton.stop();
        bigButton.release();

        Log.e(TAG, "BigButton exit");
    }
}
