package com.b2kylin.misc;

import static com.b2kylin.smart_rfid.Global.localRecordSQLite;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.b2kylin.smart_rfid.Global;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Size loadWinSize(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(outMetrics);
        }
        return new Size(outMetrics.widthPixels, outMetrics.heightPixels);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Size fitPhotoSize(StreamConfigurationMap map, Size mWinSize) {
        // 获取摄像头支持的最大尺寸
        List<Size> sizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));

        int minIndex = 0;//差距最小的索引
        int minDx = Integer.MAX_VALUE;
        int minDy = Integer.MAX_VALUE;
        int[] dxs = new int[sizes.size()];
        int justW = mWinSize.getHeight() * 2;//相机默认是横向的，so
        int justH = mWinSize.getWidth() * 2;
        for (int i = 0; i < sizes.size(); i++) {
            dxs[i] = sizes.get(i).getWidth() - justW;
        }
        for (int i = 0; i < dxs.length; i++) {
            int abs = Math.abs(dxs[i]);
            if (abs < minDx) {
                minIndex = i;//获取高的最适索引
                minDx = abs;
            }
        }
        for (int i = 0; i < sizes.size(); i++) {
            Size size = sizes.get(i);
            if (size.getWidth() == sizes.get(minIndex).getWidth()) {
                int dy = Math.abs(justH - size.getHeight());
                if (dy < minDy) {
                    minIndex = i;//获取宽的最适索引
                    minDy = dy;
                }
            }
        }
        return sizes.get(minIndex);
    }

    // String[]{id, carNumber, time, date, dbName, excavatorId, rfidReaderNo, rfid, picPath, json}
    public static String recordsToJson(ArrayList<ArrayList<String>> records) {
        if (records.size() <= 0)
            return null;

        String json = "[";
        for (ArrayList<String> r : records) {
            json += "{";
            json += "\"loadTime\":\"" + r.get(3) + "\",";
            json += "\"excavatorId\":\"" + r.get(5) + "\",";
            json += "\"rfid\":\"" + r.get(7) + "\",";
            json += "\"picAddress\":\"" + r.get(8) + "?rfidNo=";
            json += r.get(6) + "\"";
            json += "},";
        }

        String json2 = json.substring(0, json.length() - 1);
        json2 += "]";
        Log.i("recordsToJson", json2);

        return json2;
    }

    // imageFile: /20230304/E20047150A5068210CEB010B_220536.jpg ---> /storage/emulated/0/DCIM/$rfidNo/20230304/E20047150A5068210CEB010B_220536.jpg
    // pathTo: dbName_rfidNo_yyyyMMdd ---> /storage/emulated/0/DCIM/$dbName_$rfidNo_$yyyyMMdd/20230304/
    public static boolean copyImageToPath(String imageFile, String pathTo) {
        File _image = new File(imageFile);
        String _imageParent = _image.getParent(); // like 20230304

        File path = new File(Global.pictureRoot + pathTo + "/" + _imageParent);
        if (!path.exists()) {
            path.mkdirs();
        }

        File imageSrc = new File(Global.pictureRoot + Global.rfidReaderNo + "/" + imageFile);
        String filename = imageSrc.getName(); // like E20047150A5068210CEB010B_220536.jpg
        File imageDst = new File(path.getPath() + "/" + filename);

        Log.i("TAG", "copyImageToPath: " + imageSrc.getPath() + " " + imageDst.getPath());

        try {
            Files.copy(imageSrc, imageDst);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static String createNotUploadZip() {

        ArrayList<ArrayList<String>> notUploadRecords = localRecordSQLite.readNotUploadRecordAll();
        String json = Utils.recordsToJson(notUploadRecords);
        SimpleDateFormat _date = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);
        String path = Global.dbName + "_" + Global.rfidReaderNo + "_" + _date.format(new Date());

        Log.i("TAG", "createNotUploadZip: " + path);
        Log.i("TAG", "createNotUploadZip: " + json);

        for (ArrayList<String> r : notUploadRecords) {
            Utils.copyImageToPath(r.get(8), path);
        }

        if (json == null) {
            return null;
        }

        File jsonFile = new File(Global.pictureRoot + path + "/data.json");

        Log.i("TAG", "createNotUploadZip: " + jsonFile.getPath());

        if (!jsonFile.exists()) {
            try {
                jsonFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(jsonFile);
            fileOutputStream.write(json.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(Global.pictureRoot + path + ".zip"));
            ZipUtils.toZip(Global.pictureRoot + path, fileOutputStream, true);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


        return Global.pictureRoot + path + ".zip";
    }

    public static class ZipUtils {
        private static final int BUFFER_SIZE = 2 * 1024;

        /**
         * 压缩成ZIP 方法1
         *
         * @param srcDir           压缩文件夹路径
         * @param out              压缩文件输出流
         * @param KeepDirStructure 是否保留原来的目录结构,true:保留目录结构;
         *                         false:所有文件跑到压缩包根目录下(注意：不保留目录结构可能会出现同名文件,会压缩失败)
         * @throws RuntimeException 压缩失败会抛出运行时异常
         */
        public static void toZip(String srcDir, OutputStream out, boolean KeepDirStructure)
                throws RuntimeException {
            long start = System.currentTimeMillis();
            ZipOutputStream zos = null;
            try {
                zos = new ZipOutputStream(out);
                File sourceFile = new File(srcDir);
                compress(sourceFile, zos, sourceFile.getName(), KeepDirStructure);
                long end = System.currentTimeMillis();
                System.out.println("压缩完成，耗时：" + (end - start) + " ms");
            } catch (Exception e) {
                throw new RuntimeException("zip error from ZipUtils", e);
            } finally {
                if (zos != null) {
                    try {

                        zos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * 压缩成ZIP 方法2
         *
         * @param srcFiles 需要压缩的文件列表
         * @param out      压缩文件输出流
         * @throws RuntimeException 压缩失败会抛出运行时异常
         */
        public static void toZip(List<File> srcFiles, OutputStream out) throws RuntimeException {
            long start = System.currentTimeMillis();
            ZipOutputStream zos = null;
            try {
                zos = new ZipOutputStream(out);
                for (File srcFile : srcFiles) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    zos.putNextEntry(new ZipEntry(srcFile.getName()));
                    int len;
                    FileInputStream in = new FileInputStream(srcFile);
                    while ((len = in.read(buf)) != -1) {
                        zos.write(buf, 0, len);
                    }
                    zos.closeEntry();
                    in.close();
                }
                long end = System.currentTimeMillis();
                System.out.println("压缩完成，耗时：" + (end - start) + " ms");
            } catch (Exception e) {
                throw new RuntimeException("zip error from ZipUtils", e);
            } finally {
                if (zos != null) {
                    try {
                        zos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * 递归压缩方法
         *
         * @param sourceFile       源文件
         * @param zos              zip输出流
         * @param name             压缩后的名称
         * @param KeepDirStructure 是否保留原来的目录结构,true:保留目录结构;
         *                         false:所有文件跑到压缩包根目录下(注意：不保留目录结构可能会出现同名文件,会压缩失败)
         * @throws Exception
         */
        private static void compress(File sourceFile, ZipOutputStream zos, String name,
                                     boolean KeepDirStructure) throws Exception {
            byte[] buf = new byte[BUFFER_SIZE];
            if (sourceFile.isFile()) {
                // 向zip输出流中添加一个zip实体，构造器中name为zip实体的文件的名字
                zos.putNextEntry(new ZipEntry(name));
                // copy文件到zip输出流中
                int len;
                FileInputStream in = new FileInputStream(sourceFile);
                while ((len = in.read(buf)) != -1) {
                    zos.write(buf, 0, len);
                }
                // Complete the entry
                zos.closeEntry();
                in.close();
            } else {
                File[] listFiles = sourceFile.listFiles();
                if (listFiles == null || listFiles.length == 0) {
                    // 需要保留原来的文件结构时,需要对空文件夹进行处理
                    if (KeepDirStructure) {
                        // 空文件夹的处理
                        zos.putNextEntry(new ZipEntry(name + "/"));
                        // 没有文件，不需要文件的copy
                        zos.closeEntry();
                    }

                } else {
                    for (File file : listFiles) {
                        // 判断是否需要保留原来的文件结构
                        if (KeepDirStructure) {
                            // 注意：file.getName()前面需要带上父文件夹的名字加一斜杠,
                            // 不然最后压缩包中就不能保留原来的文件结构,即：所有文件都跑到压缩包根目录下了

                            compress(file, zos, name + "/" + file.getName(), KeepDirStructure);

                        } else {

                            compress(file, zos, file.getName(), KeepDirStructure);

                        }
                    }
                }
            }
        }
    }
}