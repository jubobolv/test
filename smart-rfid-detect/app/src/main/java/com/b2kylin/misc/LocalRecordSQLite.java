package com.b2kylin.misc;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.b2kylin.smart_rfid.Global;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocalRecordSQLite extends SQLiteOpenHelper {

    private final String TAG = "LocalRecordSQLite";

    public LocalRecordSQLite(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    public void addRecord(String carNumber, String time, String date, String dbName, String excavatorId, String rfidReaderNo, String rfid, String picPath, String json, boolean uploaded) {
        SQLiteDatabase database = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("carNumber", carNumber);
        contentValues.put("time", time);
        contentValues.put("date", date);
        contentValues.put("dbName", dbName);
        contentValues.put("excavatorId", excavatorId);
        contentValues.put("rfidReaderNo", rfidReaderNo);
        contentValues.put("rfid", rfid);
        contentValues.put("picPath", picPath);
        contentValues.put("json", json);
        contentValues.put("uploaded", uploaded ? "1" : "0");
        database.insert("record", null, contentValues);
    }

    public void addTmpRecord(String dbName, String excavatorId, String rfidReaderNo, String rfid, String picPath) {
        SQLiteDatabase database = getWritableDatabase();

        database.delete("tmp", "id=1", null);

        ContentValues contentValues = new ContentValues();
        contentValues.put("id", 1);
        contentValues.put("dbName", dbName);
        contentValues.put("excavatorId", excavatorId);
        contentValues.put("rfidReaderNo", rfidReaderNo);
        contentValues.put("rfid", rfid);
        contentValues.put("picPath", picPath);
        contentValues.put("timestamp", System.currentTimeMillis() / 1000);
        database.insert("tmp", null, contentValues);
    }

    public void flushOverview(String carNumber) {
        SQLiteDatabase database;
        try {
            database = getWritableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            return;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put("carNumber", carNumber);
        contentValues.put("timestamp", System.currentTimeMillis() / 1000);

        Cursor cursor = database.query("overview", null, "carNumber=?", new String[]{carNumber}, null, null, null);
        if (cursor.moveToNext()) {
            @SuppressLint("Range") String _id = String.valueOf(cursor.getInt(cursor.getColumnIndex("id")));
            @SuppressLint("Range") String _carNumber = cursor.getString(cursor.getColumnIndex("carNumber"));
            @SuppressLint("Range") String _timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));
            @SuppressLint("Range") int _count = cursor.getInt(cursor.getColumnIndex("count"));

            Log.i(TAG, "flushOverview: "
                    + _id + " "
                    + _carNumber + " "
                    + _timestamp + " "
                    + _count);

            contentValues.put("count", ++_count + "");
            database.update("overview", contentValues, "carNumber=?", new String[]{carNumber});
        } else {
            contentValues.put("count", "0");
            database.insert("overview", null, contentValues);
        }
    }

    // String[]{id, carNumber, timestamp, count}
    public ArrayList<ArrayList<String>> readSortedOverview() {
        ArrayList<ArrayList<String>> record = new ArrayList<>();
        SQLiteDatabase database;
        try {
            database = getWritableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            return record;
        }

        Cursor cursor = database.query("overview", null, null, null, null, null, "count DESC", "10");

        while (cursor.moveToNext()) {
            @SuppressLint("Range") String id = String.valueOf(cursor.getInt(cursor.getColumnIndex("id")));
            @SuppressLint("Range") String carNumber = cursor.getString(cursor.getColumnIndex("carNumber"));
            @SuppressLint("Range") String timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));
            @SuppressLint("Range") String count = cursor.getString(cursor.getColumnIndex("count"));

            ArrayList<String> r = new ArrayList<>();
            r.add(id);
            r.add(carNumber);
            r.add(timestamp);
            r.add(count);
            record.add(r);

            Log.i(TAG, "readSortedOverview: "
                    + id + " "
                    + carNumber + " "
                    + timestamp + " "
                    + count);
        }

        return record;
    }

    // string[](dbName, excavatorId, rfidReaderNo, rfid, picPath, timestamp)
    public ArrayList<String> readTmpRecord() {
        ArrayList<String> record = new ArrayList<>();
        SQLiteDatabase database;
        try {
            database = getWritableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            return record;
        }

        Cursor cursor = database.query("tmp", null, "id=1", null, null, null, null);
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String dbName = cursor.getString(cursor.getColumnIndex("dbName"));
            @SuppressLint("Range") String excavatorId = cursor.getString(cursor.getColumnIndex("excavatorId"));
            @SuppressLint("Range") String rfidReaderNo = cursor.getString(cursor.getColumnIndex("rfidReaderNo"));
            @SuppressLint("Range") String rfid = cursor.getString(cursor.getColumnIndex("rfid"));
            @SuppressLint("Range") String picPath = cursor.getString(cursor.getColumnIndex("picPath"));
            @SuppressLint("Range") String timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));


            record.add(dbName);
            record.add(excavatorId);
            record.add(rfidReaderNo);
            record.add(rfid);
            record.add(picPath);
            record.add(timestamp);
        }

        return record;
    }

    public void delTmpRecord() {
        SQLiteDatabase database = getWritableDatabase();
        database.delete("tmp", "id=1", null);
    }

    void deleteRecord() {

    }

    public int settingUploadRecordStatus(String id, String uploadStatus) {
        ContentValues values = new ContentValues();
        values.put("uploaded", Integer.valueOf(uploadStatus));
        SQLiteDatabase database = getWritableDatabase();
        return database.update("record", values, "id=?", new String[]{id});
    }

    // String[]{id, carNumber, time, date, dbName, excavatorId, rfidReaderNo, rfid, picPath, json}
    public ArrayList<ArrayList<String>> readNotUploadRecordAll() {
        ArrayList<ArrayList<String>> record = new ArrayList<>();
        SQLiteDatabase database;
        try {
            database = getWritableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            return record;
        }

        Cursor cursor = database.query("record", null, "uploaded=?", new String[]{"0"}, null, null, "id ASC");
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String id = String.valueOf(cursor.getInt(cursor.getColumnIndex("id")));
            @SuppressLint("Range") String carNumber = cursor.getString(cursor.getColumnIndex("carNumber"));
            @SuppressLint("Range") String time = cursor.getString(cursor.getColumnIndex("time"));
            @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex("date"));
            @SuppressLint("Range") String dbName = cursor.getString(cursor.getColumnIndex("dbName"));
            @SuppressLint("Range") String excavatorId = cursor.getString(cursor.getColumnIndex("excavatorId"));
            @SuppressLint("Range") String rfidReaderNo = cursor.getString(cursor.getColumnIndex("rfidReaderNo"));
            @SuppressLint("Range") String rfid = cursor.getString(cursor.getColumnIndex("rfid"));
            @SuppressLint("Range") String picPath = cursor.getString(cursor.getColumnIndex("picPath"));
            @SuppressLint("Range") String json = cursor.getString(cursor.getColumnIndex("json"));

            ArrayList<String> r = new ArrayList<>();
            r.add(id);
            r.add(carNumber);
            r.add(time);
            r.add(date);
            r.add(dbName);
            r.add(excavatorId);
            r.add(rfidReaderNo);
            r.add(rfid);
            r.add(picPath);
            r.add(json);
            record.add(r);

            Log.i(TAG, "readNotUploadRecordAll: "
                    + id + " "
                    + carNumber + " "
                    + time + " "
                    + date + " "
                    + dbName + " "
                    + excavatorId + " "
                    + rfidReaderNo + " "
                    + rfid + " "
                    + picPath + " "
                    + json);
        }

        return record;
    }

    // String[5]{id, carNumber, time, date, dbName, excavatorId, rfidReaderNo, rfid, picPath, json}
    public ArrayList<ArrayList<String>> readNotUploadRecord5() {
        ArrayList<ArrayList<String>> record = new ArrayList<>();
        SQLiteDatabase database;
        try {
            database = getWritableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            return record;
        }

        Cursor cursor = database.query("record", null, "uploaded=?", new String[]{"0"}, null, null, "id ASC", "100");
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String id = String.valueOf(cursor.getInt(cursor.getColumnIndex("id")));
            @SuppressLint("Range") String carNumber = cursor.getString(cursor.getColumnIndex("carNumber"));
            @SuppressLint("Range") String time = cursor.getString(cursor.getColumnIndex("time"));
            @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex("date"));
            @SuppressLint("Range") String dbName = cursor.getString(cursor.getColumnIndex("dbName"));
            @SuppressLint("Range") String excavatorId = cursor.getString(cursor.getColumnIndex("excavatorId"));
            @SuppressLint("Range") String rfidReaderNo = cursor.getString(cursor.getColumnIndex("rfidReaderNo"));
            @SuppressLint("Range") String rfid = cursor.getString(cursor.getColumnIndex("rfid"));
            @SuppressLint("Range") String picPath = cursor.getString(cursor.getColumnIndex("picPath"));
            @SuppressLint("Range") String json = cursor.getString(cursor.getColumnIndex("json"));

            ArrayList<String> r = new ArrayList<>();
            r.add(id);
            r.add(carNumber);
            r.add(time);
            r.add(date);
            r.add(dbName);
            r.add(excavatorId);
            r.add(rfidReaderNo);
            r.add(rfid);
            r.add(picPath);
            r.add(json);
            record.add(r);

            Log.i(TAG, "readNotUploadRecord5: "
                    + id + " "
                    + carNumber + " "
                    + time + " "
                    + date + " "
                    + dbName + " "
                    + excavatorId + " "
                    + rfidReaderNo + " "
                    + rfid + " "
                    + picPath + " "
                    + json);
        }

        return record;
    }

    // String[3]{carNumber, time, date}
    public ArrayList<ArrayList<String>> readRecord5() {
        ArrayList<ArrayList<String>> record = new ArrayList<>();
        SQLiteDatabase database;
        try {
            database = getWritableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            e.printStackTrace();
            return record;
        }

        Cursor cursor = database.query("record", null, null, null, null, null, "id DESC", "5");
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String carNumber = cursor.getString(cursor.getColumnIndex("carNumber"));
            @SuppressLint("Range") String time = cursor.getString(cursor.getColumnIndex("time"));
            @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex("date"));

            ArrayList<String> r = new ArrayList<>();
            r.add(carNumber);
            r.add(time);
            r.add(date);
            record.add(r);

            Log.i(TAG, "readRecord5: "
                    + carNumber + " "
                    + time + " "
                    + date);
        }

        return record;
    }

    // String[]{index, carNumber, time, uploaded}
    public ArrayList<ArrayList<String>> readRecordAll() {
        // TODO limit by date
        SQLiteDatabase database = getWritableDatabase();
        int i = 0;
        Cursor cursor = database.query("record", null, null, null, null, null, "id DESC");
        ArrayList<ArrayList<String>> record = new ArrayList<>();
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String carNumber = cursor.getString(cursor.getColumnIndex("carNumber"));
            @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex("date"));
            @SuppressLint("Range") String uploaded = String.valueOf(cursor.getInt(cursor.getColumnIndex("uploaded")));
            ArrayList<String> r = new ArrayList<>();
            r.add(String.valueOf(++i));
            r.add(carNumber);
            r.add(date);
            r.add(uploaded);
            record.add(r);
            Log.i(TAG, "readRecordAll: " + i + " " + carNumber + " " + date + " " + uploaded);
        }
        return record;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String sql = "create table record(id integer primary key autoincrement,carNumber text,time text,date text,dbName text,excavatorId text,rfidReaderNo text,rfid text,picPath text,json text,uploaded integer)";
        sqLiteDatabase.execSQL(sql);
        String sql2 = "create table tmp(id integer primary key,dbName text,excavatorId text,rfidReaderNo text,rfid text,picPath text,timestamp integer)";
        sqLiteDatabase.execSQL(sql2);
        String sql3 = "create table overview(id integer primary key autoincrement,carNumber text,timestamp integer,count integer)";
        sqLiteDatabase.execSQL(sql3);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
