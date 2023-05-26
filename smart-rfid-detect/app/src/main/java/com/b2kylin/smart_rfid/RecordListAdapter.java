package com.b2kylin.smart_rfid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class RecordListAdapter extends ArrayAdapter<ArrayList<String>> {
    private LayoutInflater inflter;
    private int textViewResourceId;

    public RecordListAdapter(@NonNull Context context, int resource) {
        super(context, resource);
        this.textViewResourceId = resource;
        inflter = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ArrayList<String> record = getItem(position);
        return update(record);
    }

    private View update(ArrayList<String> record) {
        @SuppressLint("ViewHolder") View view = inflter.inflate(textViewResourceId, null);
        TextView textViewIndex = view.findViewById(R.id.textviewRecordIndex);
        TextView textViewRecordDevicesCarNumber = view.findViewById(R.id.textViewRecordDevicesCarNumber);
        TextView textViewRecordDate = view.findViewById(R.id.textViewRecordDate);
        TextView textViewRecordUploadState = view.findViewById(R.id.textViewRecordUploadState);

        textViewIndex.setText(record.get(0));
        textViewRecordDevicesCarNumber.setText(record.get(1));
        textViewRecordDate.setText(record.get(2));
        textViewRecordUploadState.setText(record.get(3).equals("0") ? "未上传" : "已上传");
        if (!record.get(3).equals("0")) {
            textViewRecordUploadState.setTextColor(Color.BLUE);
        }

        return view;
    }
}
