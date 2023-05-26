package com.b2kylin.smart_rfid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import commons.proto.DiggingsOuterClass;

public class InputActivity extends AppCompatActivity implements View.OnClickListener {

    private String TAG = "InputActivity";
    private LinearLayout linearLayoutInput;
    private Button buttonOK;
    private Button buttonCancel;
    private EditText editTextCarNumber;
    private TextView textViewHint;
    private TextView textViewCarNumberHistory1;
    private TextView textViewCarNumberHistory2;
    private TextView textViewCarNumberHistory3;
    private TextView textViewCarNumberHistory4;
    private TextView textViewCarNumberHistory5;
    private TextView textViewCarNumberHistory6;
    private TextView textViewCarNumberHistory7;
    private TextView textViewCarNumberHistory8;
    private TextView textViewCarNumberHistory9;
    private TextView textViewCarNumberHistory10;

    private ArrayList<ArrayList<String>> records;
    private RealtimeRetrieval realtimeRetrieval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input);

        // TODO 获取当前白夜班模式，修改当前背景及字体颜色

        linearLayoutInput = findViewById(R.id.linearLayoutInput);
        editTextCarNumber = findViewById(R.id.editTextCarNumber);
        buttonOK = findViewById(R.id.buttonOK);
        buttonCancel = findViewById(R.id.buttonCancel);
        textViewHint = findViewById(R.id.textViewHint);
        textViewCarNumberHistory1 = findViewById(R.id.textViewCarNumberHistory1);
        textViewCarNumberHistory2 = findViewById(R.id.textViewCarNumberHistory2);
        textViewCarNumberHistory3 = findViewById(R.id.textViewCarNumberHistory3);
        textViewCarNumberHistory4 = findViewById(R.id.textViewCarNumberHistory4);
        textViewCarNumberHistory5 = findViewById(R.id.textViewCarNumberHistory5);
        textViewCarNumberHistory6 = findViewById(R.id.textViewCarNumberHistory6);
        textViewCarNumberHistory7 = findViewById(R.id.textViewCarNumberHistory7);
        textViewCarNumberHistory8 = findViewById(R.id.textViewCarNumberHistory8);
        textViewCarNumberHistory9 = findViewById(R.id.textViewCarNumberHistory9);
        textViewCarNumberHistory10 = findViewById(R.id.textViewCarNumberHistory10);

        buttonOK.setOnClickListener(this);
        buttonCancel.setOnClickListener(this);
        textViewCarNumberHistory1.setOnClickListener(this);
        textViewCarNumberHistory2.setOnClickListener(this);
        textViewCarNumberHistory3.setOnClickListener(this);
        textViewCarNumberHistory4.setOnClickListener(this);
        textViewCarNumberHistory5.setOnClickListener(this);
        textViewCarNumberHistory6.setOnClickListener(this);
        textViewCarNumberHistory7.setOnClickListener(this);
        textViewCarNumberHistory8.setOnClickListener(this);
        textViewCarNumberHistory9.setOnClickListener(this);
        textViewCarNumberHistory10.setOnClickListener(this);

        if (Global.localRecordSQLite == null)
            return;
        // String[]{id, carNumber, timestamp, count}
        records = Global.localRecordSQLite.readSortedOverview();
        Iterator<ArrayList<String>> entry = records.iterator();
        if (entry.hasNext()) {
            textViewCarNumberHistory1.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory2.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory3.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory4.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory5.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory6.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory7.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory8.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory9.setText(entry.next().get(1));
        }
        if (entry.hasNext()) {
            textViewCarNumberHistory10.setText(entry.next().get(1));
        }

        editTextCarNumber.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                textViewHint.setText("匹配结果：");
                if (!realtimeRetrieval.isAlive()) realtimeRetrieval.start();
            }
        });

        realtimeRetrieval = new RealtimeRetrieval();
    }

    class RealtimeRetrieval extends Thread {
        private boolean exit = false;

        @Override
        public void run() {
            super.run();

            while (!exit) {

                ArrayList<String> tmp = new ArrayList<>();
                String inputString = editTextCarNumber.getText().toString();
                Log.i(TAG, "inputString: " + inputString);
                if (inputString.length() != 0 && Global.deviceInfoPageVo != null) {
                    for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                        if (info.getCarNumber().toUpperCase(Locale.ROOT).contains(inputString.toUpperCase(Locale.ROOT))) {
                            Log.i(TAG, "contains: " + info.getCarNumber());
                            tmp.add(info.getCarNumber());
                        }
                    }
                }
                Iterator<String> entry = tmp.iterator();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textViewCarNumberHistory1.setText("");
                        textViewCarNumberHistory2.setText("");
                        textViewCarNumberHistory3.setText("");
                        textViewCarNumberHistory4.setText("");
                        textViewCarNumberHistory5.setText("");
                        textViewCarNumberHistory6.setText("");
                        textViewCarNumberHistory7.setText("");
                        textViewCarNumberHistory8.setText("");
                        textViewCarNumberHistory9.setText("");
                        textViewCarNumberHistory10.setText("");

                        if (entry.hasNext()) {
                            textViewCarNumberHistory1.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory2.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory3.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory4.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory5.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory6.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory7.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory8.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory9.setText(entry.next());
                        }
                        if (entry.hasNext()) {
                            textViewCarNumberHistory10.setText(entry.next());
                        }

                    }
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
            }
        }

        @Override
        public void interrupt() {
            exit = true;
            super.interrupt();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonOK:
                if (editTextCarNumber.getText().length() == 0) {
                    setResult(RESULT_CANCELED);
                } else {
                    // carNumber check
                    boolean isFound = false;
                    if (Global.deviceInfoPageVo != null) {
                        for (DiggingsOuterClass.DeviceInfoVo info : Global.deviceInfoPageVo.getRecordsList()) {
                            if (info.getCarNumber().equals(editTextCarNumber.getText().toString())) {
                                isFound = true;
                                break;
                            }
                        }
                        if (!isFound) {
                            Toast.makeText(this, "无此车次号", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    // 装车超时时间判断
                    for (ArrayList<String> record : records) {
                        if (record.get(1).equals(editTextCarNumber.getText().toString())) {
                            long timestamp = Integer.valueOf(record.get(2));
                            long delay = System.currentTimeMillis() / 1000 - timestamp;
                            if (delay < Global.exEntruckingMin + Global.exFreezeMin) {
                                Toast.makeText(this, "此车暂被冻结，请 " + (Global.exEntruckingMin + Global.exFreezeMin - delay) + " 秒后重试", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                    }

                    Intent intent = new Intent();
                    intent.putExtra("NUMBER", editTextCarNumber.getText().toString());
                    setResult(RESULT_OK, intent);
                }
                realtimeRetrieval.interrupt();
                finish();
                break;
            case R.id.buttonCancel:
                setResult(RESULT_CANCELED);
                realtimeRetrieval.interrupt();
                finish();
                break;
            case R.id.textViewCarNumberHistory1:
            case R.id.textViewCarNumberHistory2:
            case R.id.textViewCarNumberHistory3:
            case R.id.textViewCarNumberHistory4:
            case R.id.textViewCarNumberHistory5:
            case R.id.textViewCarNumberHistory6:
            case R.id.textViewCarNumberHistory7:
            case R.id.textViewCarNumberHistory8:
            case R.id.textViewCarNumberHistory9:
            case R.id.textViewCarNumberHistory10:
                editTextCarNumber.setText(((TextView) view).getText());
                textViewHint.setText("匹配结果：");
                if (!realtimeRetrieval.isAlive()) realtimeRetrieval.start();
                break;
            default:
                break;
        }
    }
}