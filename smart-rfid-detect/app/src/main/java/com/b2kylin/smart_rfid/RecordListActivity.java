package com.b2kylin.smart_rfid;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ListView;

import java.util.ArrayList;

public class RecordListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_list);

        ListView listView = findViewById(R.id.listViewRecordList);
        RecordListAdapter recordListAdapter = new RecordListAdapter(this, R.layout.item_record_list);
        ArrayList<ArrayList<String>> record = Global.localRecordSQLite.readRecordAll();
        recordListAdapter.addAll(record);
        listView.setAdapter(recordListAdapter);
    }
}