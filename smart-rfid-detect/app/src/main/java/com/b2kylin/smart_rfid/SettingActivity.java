package com.b2kylin.smart_rfid;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SettingActivity extends AppCompatActivity {
    private final String TAG = "SettingActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        EditText editViewRfidNo = findViewById(R.id.editViewRfidNo);
        EditText editViewExcavatorId = findViewById(R.id.editViewExcavatorId);
        EditText editViewDbName = findViewById(R.id.editViewDbName);
        EditText editViewFifoCheckPeriod = findViewById(R.id.editViewFifoCheckPeriod);
        EditText editViewExExpireMin = findViewById(R.id.editViewExExpireMin);
        EditText editViewExFreezeMin = findViewById(R.id.editViewExFreezeMin);
        EditText editViewExEntruckingMin = findViewById(R.id.editViewExEntruckingMin);
        EditText editViewInfoSync = findViewById(R.id.editViewInfoSync);

        Button buttonSaveAll = findViewById(R.id.buttonSaveAll);

        editViewFifoCheckPeriod.setText(Global.fifoCheckPeriod + "");
        editViewInfoSync.setText(Global.syncInfoPeriod + "");
        editViewExExpireMin.setText(Global.exExpireMin + "");
        editViewExFreezeMin.setText(Global.exFreezeMin + "");
        editViewExEntruckingMin.setText(Global.exEntruckingMin + "");

        buttonSaveAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File config = new File(Global.config);
                try {
                    OutputStream outputStream = new FileOutputStream(config);
                    String dbName = "DbName=" + editViewDbName.getText().toString() + "\n";
                    String excavatorId = "ExcavatorId=" + editViewExcavatorId.getText().toString() + "\n";
                    String rfidReaderNo = "RfidReaderNo=" + editViewRfidNo.getText().toString() + "\n";
                    int period = Integer.parseInt(editViewFifoCheckPeriod.getText().toString());
                    if (period <= 0) {
                        period = 1;
                    }
                    Global.fifoCheckPeriod = period;

                    int syncPeriod = Integer.parseInt(editViewInfoSync.getText().toString());
                    if (syncPeriod < 10) {
                        syncPeriod = 10;
                    }
                    Global.syncInfoPeriod = syncPeriod;

                    String fifoCheckPeriodStr = "fifoCheckPeriod=" + period + "\n";
                    outputStream.write(dbName.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(excavatorId.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(rfidReaderNo.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(fifoCheckPeriodStr.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(SettingActivity.this, "保存失败", Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(SettingActivity.this, "保存成功", Toast.LENGTH_LONG).show();
            }
        });

        File config = new File(Global.config);
        if (!config.exists()) {
            try {
                if (config.createNewFile()) {
                    OutputStream outputStream = new FileOutputStream(config);
                    String dbName = "DbName=" + Global.dbName + "\n";
                    String excavatorId = "ExcavatorId=" + Global.excavatorId + "\n";
                    String rfidReaderNo = "RfidReaderNo=" + Global.rfidReaderNo + "\n";
                    String fifoCheckPeriodStr = "fifoCheckPeriod=" + Global.fifoCheckPeriod + "\n";
                    outputStream.write(dbName.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(excavatorId.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(rfidReaderNo.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(fifoCheckPeriodStr.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        try {
            byte[] bytes = new byte[1024];
            InputStream inputStream = new FileInputStream(config);
            inputStream.read(bytes);
            String str = new String(bytes);
            String[] strArray = str.split("\n");
            Log.i(TAG, "0:" + strArray[0]);
            Log.i(TAG, "1:" + strArray[1]);
            Log.i(TAG, "2:" + strArray[2]);

            editViewDbName.setText(strArray[0].split("=")[1]);
            editViewExcavatorId.setText(strArray[1].split("=")[1]);
            editViewRfidNo.setText(strArray[2].split("=")[1]);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}