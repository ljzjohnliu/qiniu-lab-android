package com.qiniu.qiniulab.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.android.common.AutoZone;
import com.qiniu.android.common.FixedZone;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.Configuration;
import com.qiniu.android.storage.FileRecorder;
import com.qiniu.android.storage.Recorder;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.android.utils.AsyncRun;
import com.qiniu.android.utils.Utils;
import com.qiniu.qiniulab.R;
import com.qiniu.qiniulab.config.QiniuLabConfig;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "MainActivity";
    private static Context context;
    private final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1000;

    TextView resultTv;

    private boolean isEnablePart;
    private UploadManager simpleUploadManager;
    private UploadManager multiUploadManager;

    private String uploadToken = "j4uAjPplb7oCAtazVj_UQP1XH9anZSv-CJ_iy2BX:9wwpSBjKaHMOj2hFp2ygMi_CPgU=:eyJzY29wZSI6InVwbG9hZHRlc3QxMTEiLCJkZWFkbGluZSI6MTc3MjcwMzEzN30=";
    private String domain = "t9v4tja4n.hn-bkt.clouddn.com";
    private String uploadFilePath;

    private String[] fileSizeArray = {"5M", "10M", "20M", "50M", "80M", "150M", "400M", "800M", "1G", "10G"};
    private String fileSizeType;
    private String[] picFilePaths;
    private String[] videoFilePaths;

    private static long totalCostTime;
    private static int totalCount;
    private static int sucCount;

    public void updateResult(String fileSizeType) {
        String result = String.format("测试资源是：%s，上传平均耗时：， 成功率：", fileSizeType);
        resultTv.setText(result);
    }

    public void updateResult(final String fileSizeType, final long costTime, final int position, final int total) {
        resultTv.post(new Runnable() {
            @Override
            public void run() {
                String result = String.format("测试资源是：%s，上传平均耗时:%d， 成功率：%d / %d", fileSizeType, costTime, position, total);
                Log.d(TAG, "updateResult: ******* result = " + result);
                resultTv.setText(result);
            }
        });
    }

    class MySelectedListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            Toast.makeText(MainActivity.this, "您选择的是：" + fileSizeArray[i], Toast.LENGTH_SHORT).show();
            fileSizeType = fileSizeArray[i];

            String picFolderPath = "/mnt/sdcard/picture/" + fileSizeArray[i];
            Log.d(TAG, "onItemSelected: picFolderPath = " + picFolderPath);
            picFilePaths = MyUtil.traverseFolder(new File(picFolderPath));
            Log.d(TAG, "onItemSelected: picFilePaths = " + Arrays.toString(picFilePaths));

            String videoFolderPath = "/mnt/sdcard/video/" + fileSizeArray[i];
            Log.d(TAG, "onItemSelected: videoFolderPath = " + videoFolderPath);
            videoFilePaths = MyUtil.traverseFolder(new File(videoFolderPath));
            Log.d(TAG, "onItemSelected: videoFilePaths = " + Arrays.toString(videoFilePaths));
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    }

    private void initSpinner() {
        ArrayAdapter<String> starAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, fileSizeArray);
        Spinner sp = (Spinner)findViewById(R.id.file_size_spinner);
        sp.setAdapter(starAdapter);
        sp.setSelection(0);
        sp.setOnItemSelectedListener(new MySelectedListener());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        initSpinner();
        resultTv = (TextView) findViewById(R.id.upload_result);
        requestPermissions();

        // 关闭 DNS 预解析
//        GlobalConfiguration.getInstance().isDnsOpen = false;
        Intent intent = getIntent();
        isEnablePart = intent.getBooleanExtra("isEnablePart", false);
        int sliceSize = intent.getIntExtra("sliceSize", 1024*1024);
        Log.d(TAG, "onCreate: isEnablePart = " + isEnablePart + ", sliceSize = " + sliceSize);
        if (!isEnablePart) {
//            this.simpleUploadManager = new UploadManager();
            // Upload_Domain1, Upload_Domain2 为服务下发的上传域名
            FixedZone fixedZone = new FixedZone(new String[]{"t9v4tja4n.hn-bkt.clouddn.com", "Upload_Domain2"});
            // 指定上传区域为 华南-广东
            FixedZone zone = FixedZone.createWithRegionId("z2");
            Configuration config = new Configuration.Builder()
                    .zone(zone)
                    .buildV2();
            simpleUploadManager = new UploadManager(config);
        } else {
            // 定义分片上传时，断点续传信息保存的 Recorder
            Recorder recorder = null;
            try {
                recorder = new FileRecorder(Utils.sdkDirectory() + "/recorder");
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 指定上传区域为 华南-广东
            FixedZone zone = FixedZone.createWithRegionId("z2");
            Configuration configuration = new Configuration.Builder()
                    .accelerateUploading(true) // 开启传输加速，此参数仅对使用 QNAutoZone 时有效（v8.8.0 开始支持）
//                    .zone(new AutoZone())              // 配置上传区域，使用 AutoZone
                    .zone(zone)
                    .recorder(recorder)
                    .useHttps(false)
                    .chunkSize(sliceSize) // 文件采用分片上传时，分片大小为 4MB
                    .putThreshold(4 * 1024 * 1024) // 分片上传阈值：4MB，大于 4MB 采用分片上传，小于 4MB 采用表单上传
//                    .connectTimeout(10) // 请求连接超时 10s
//                    .writeTimeout(30) // 请求写超时 30s
//                    .responseTimeout(10) // 请求响应超时 10s
//                    .retryMax(1) // 单个域名/IP请求失败后最大重试次数为 1 次
//                    .retryInterval(500) // 重试时间间隔
//                    .allowBackupHost(true) // 是否使用备用域名进行重试
//                    .urlConverter(new UrlConverter() {
//                        @Override
//                        public String convert(String url) {
//                            // 公有云不可配置
//                            return url;
//                        }
//                    })
                    .useConcurrentResumeUpload(true)  // 开启并发分片上传
                    .concurrentTaskCount(3)           // 使用并发分片上传时，一个文件并发上传的分片个数
                    .resumeUploadVersion(Configuration.RESUME_UPLOAD_VERSION_V2) // 使用分片 V2
                    .buildV2();
            multiUploadManager = new UploadManager(configuration);
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this, "您必须允许读取外部存储权限，否则无法上传文件", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void getVideoTokenDomain(View view) {
    }

    public void getImgTokenDomain(View view) {
    }

    public void videoUpload(View view) {
        if (TextUtils.isEmpty(uploadToken)) {
            Toast.makeText(context, "UploadToken is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(domain)) {
            Toast.makeText(context, "Domain is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isSingleFile = false;
        if (!isSingleFile) {
            String[] filePaths = videoFilePaths;
            if (filePaths == null) {
                Toast.makeText(MainActivity.this, "Picture filePaths is null!", Toast.LENGTH_SHORT).show();
                return;
            }
            sucCount = 0;
            totalCount = filePaths.length;
            totalCostTime = 0;
            updateResult("视频" + fileSizeType, 0, 0, !isSingleFile ? totalCount : 1);
            executeTaskByOrder(true, filePaths, 0);
        } else {
            uploadFilePath = "/mnt/sdcard/test_gif.gif";
            Log.d(TAG, "MainActivity, videoUpload: uploadFilePath = " + uploadFilePath);
            uploadFile(uploadFilePath, true, isEnablePart);
        }
    }

    public void picUpload(View view) {
        if (TextUtils.isEmpty(uploadToken)) {
            Toast.makeText(context, "UploadToken is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(domain)) {
            Toast.makeText(context, "Domain is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isSingleFile = false;
        if (!isSingleFile) {
            String[] filePaths = picFilePaths;
            if (filePaths == null) {
                Toast.makeText(MainActivity.this, "Picture filePaths is null!", Toast.LENGTH_SHORT).show();
                return;
            }
            sucCount = 0;
            totalCount = filePaths.length;
            totalCostTime = 0;
            updateResult("图片" + fileSizeType, 0, 0, !isSingleFile ? totalCount : 1);
            executeTaskByOrder(false, filePaths, 0);
        } else {
            uploadFilePath = "/mnt/sdcard/test_gif.gif";
            Log.d(TAG, "MainActivity, picUpload: uploadFilePath = " + uploadFilePath);
            uploadFile(uploadFilePath, false, isEnablePart);
        }
    }

    public void executeTaskByOrder(final boolean isVideo, final String[] filePaths, int position){
        if (position >= filePaths.length)
            return;
        final String uploadFilePath = filePaths[position];
        File file = new File(uploadFilePath);
        String fileName = file.getName();
        final int nextPos = position + 1;
        Log.d(TAG, "executeTaskByOrder: position = " + position + ", uploadFilePath = " + uploadFilePath + "， fileName = " + fileName + ", nextPos = " + nextPos);

        if (!TextUtils.isEmpty(uploadFilePath)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File uploadFile = new File(uploadFilePath);
                        UploadOptions uploadOptions = new UploadOptions(null, null, false,
                                new UpProgressHandler() {
                                    @Override
                                    public void progress(String key, double percent) {
//                                        Log.d(TAG, "progress: key = " + key + ", percent = " + percent);
                                    }
                                }, null);
                        final long startTime = System.currentTimeMillis();
                        UploadManager uploadManager = isEnablePart ? multiUploadManager : simpleUploadManager;
                        uploadManager.put(uploadFile, null, uploadToken,
                                new UpCompletionHandler() {
                                    @Override
                                    public void complete(String key, ResponseInfo respInfo,
                                                         JSONObject jsonData) {
                                        long costTime = System.currentTimeMillis() - startTime;
                                        Log.d(TAG, "executeTaskByOrder, complete: uploadFilePath = " + uploadFilePath + ", key = " + key + ", respInfo = " + respInfo
                                                + ", costTime = " + costTime + ", jsonData is: " + jsonData);
                                        if (respInfo != null && respInfo.isOK()) {
                                            sucCount++;
                                            totalCostTime += costTime;
                                            long averageCost = totalCostTime / sucCount;
                                            Log.d(TAG, "executeTaskByOrder onSuccess: costTime = " + costTime + ", sucCount = " + sucCount + ", totalCostTime = " + totalCostTime + ", averageCost = " + averageCost + ", totalCount = " + totalCount);
                                            updateResult((isVideo ? "视频":"图片") + fileSizeType, averageCost, sucCount, totalCount);
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.qiniu_upload_file_failed), Toast.LENGTH_LONG).show();
                                            Log.e(QiniuLabConfig.LOG_TAG, respInfo.toString());
                                        }
                                        executeTaskByOrder(isVideo, filePaths, nextPos);
                                    }
                                }, uploadOptions);

                    } catch (final Exception e) {
                        AsyncRun.runInMain(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                        Log.e(QiniuLabConfig.LOG_TAG, e.getMessage());
                        executeTaskByOrder(isVideo, filePaths, nextPos);
                    }
                }
            }).start();
        }
    }

    public void uploadFile(final String filePath, final boolean isVideo, final boolean isMultiPart) {
        if (TextUtils.isEmpty(filePath)) {
            Toast.makeText(context, "上传文件路径为空！", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    upload(filePath, isVideo, isMultiPart, uploadToken, domain);
                } catch (final Exception e) {
                    AsyncRun.runInMain(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    Log.e(QiniuLabConfig.LOG_TAG, e.getMessage());
                }
            }
        }).start();
    }

    private void upload(String filePath, final boolean isVideo, final boolean isMultiPart, final String uploadToken, final String domain) {
        File uploadFile = new File(filePath);
        UploadOptions uploadOptions = new UploadOptions(null, null, false,
                new UpProgressHandler() {
                    @Override
                    public void progress(String key, double percent) {
                        Log.d(TAG, "progress: key = " + key + ", percent = " + percent);
                    }
                }, null);
        final long startTime = System.currentTimeMillis();
        final long fileLength = uploadFile.length();
        UploadManager uploadManager = isMultiPart ? multiUploadManager : simpleUploadManager;

        uploadManager.put(uploadFile, null, uploadToken,
                new UpCompletionHandler() {
                    @Override
                    public void complete(String key, ResponseInfo respInfo,
                                         JSONObject jsonData) {
                        long lastMillis = System.currentTimeMillis() - startTime;
                        Log.d(TAG, "complete: upload key = " + key + "，respInfo is:" + respInfo + "，jsonData is:" + jsonData + "，cost is:" + lastMillis);
                        if (respInfo.isOK()) {

                        } else {
                            Toast.makeText(context, context.getString(R.string.qiniu_upload_file_failed), Toast.LENGTH_LONG).show();
                            Log.e(QiniuLabConfig.LOG_TAG, respInfo.toString());
                        }
                    }
                }, uploadOptions);
    }
}