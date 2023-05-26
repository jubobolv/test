package com.b2kylin.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.b2kylin.smart_rfid.FullscreenActivity;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("TAG", "onReceive: " + intent.getAction());
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent _intent = new Intent(context, FullscreenActivity.class);
            //非常重要，如果缺少的话，程序将在启动时报错
            _intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //自启动APP（Activity）
            context.startActivity(_intent);
            //自启动服务（Service）
            //context.startService(_intent);
        }
    }
}
