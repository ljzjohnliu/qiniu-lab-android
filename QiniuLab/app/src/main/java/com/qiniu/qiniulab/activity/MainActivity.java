package com.qiniu.qiniulab.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.android.common.AutoZone;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.http.UrlConverter;
import com.qiniu.android.storage.Configuration;
import com.qiniu.android.storage.FileRecorder;
import com.qiniu.android.storage.GlobalConfiguration;
import com.qiniu.android.storage.KeyGenerator;
import com.qiniu.android.storage.Recorder;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.android.utils.AsyncRun;
import com.qiniu.android.utils.Utils;
import com.qiniu.qiniulab.R;
import com.qiniu.qiniulab.config.QiniuLabConfig;
import com.qiniu.qiniulab.utils.Tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "MainActivity";
    private static Context context;
    private final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1000;

    TextView videoDomainTokenTxt;
    TextView imgDomainTokenTxt;
    Switch partSwitch;
    EditText filePathEdt;
    EditText sliceSizeEdt;

    private UploadManager simpleUploadManager;
    private UploadManager multiUploadManager;
    private String videoUploadToken;
    private String videoDomain;
    private String imgUploadToken;
    private String imgDomain;
    private long uploadLastTimePoint;
    private long uploadLastOffset;
    private long uploadFileLength;
    private String uploadFilePath;

    private String[] fileSizeArray = {"5M", "10M", "20M", "50M", "80M", "150M", "400M", "800M", "1G", "10G"};
    private String fileSizeType;
    private String[] picFilePaths;
    private String[] videoFilePaths;

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
        videoDomainTokenTxt = (TextView) findViewById(R.id.video_domain);
        imgDomainTokenTxt = (TextView) findViewById(R.id.img_domain);
        partSwitch = (Switch) findViewById(R.id.switch_part);
        filePathEdt = (EditText) findViewById(R.id.file_path);
        sliceSizeEdt = (EditText) findViewById(R.id.slice_size);
        initSpinner();
        requestPermissions();

        // 关闭 DNS 预解析
        GlobalConfiguration.getInstance().isDnsOpen = false;
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
        getTokenAndDomain(true);
    }

    public void getImgTokenDomain(View view) {
        getTokenAndDomain(false);
    }

    public void getTokenAndDomain(final boolean isVideo) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final OkHttpClient httpClient = new OkHttpClient();
                Request req = new Request.Builder().url(QiniuLabConfig.makeUrl(
                        QiniuLabConfig.REMOTE_SERVICE_SERVER,
                        isVideo ? QiniuLabConfig.QUICK_START_VIDEO_DEMO_PATH : QiniuLabConfig.QUICK_START_IMAGE_DEMO_PATH)).method("GET", null).build();
                Response resp = null;
                try {
                    resp = httpClient.newCall(req).execute();
                    JSONObject jsonObject = new JSONObject(resp.body().string());
                    final String uploadToken = jsonObject.getString("uptoken");
                    final String domain = jsonObject.getString("domain");
                    AsyncRun.runInMain(new Runnable() {
                        @Override
                        public void run() {
                            if (isVideo) {
                                videoDomain = domain;
                                videoUploadToken = uploadToken;
                                videoDomainTokenTxt.setText("视频UploadToken:" + videoUploadToken + "videoDomain:" + videoDomain);
                            } else {
                                imgDomain = domain;
                                imgUploadToken = uploadToken;
                                imgDomainTokenTxt.setText("图片UploadToken:" + videoUploadToken + "videoDomain:" + videoDomain);
                            }
                        }
                    });
                } catch (Exception e) {
                    AsyncRun.runInMain(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, context.getString(R.string.qiniu_get_upload_token_failed), Toast.LENGTH_LONG).show();
                        }
                    });
                    Log.e(QiniuLabConfig.LOG_TAG, e.getMessage());
                } finally {
                    if (resp != null) {
                        resp.body().close();
                    }
                }
            }
        }).start();
    }

    public void videoUpload(View view) {
        boolean isSingleFile = true;
        if (!isSingleFile) {
            String[] filePaths = videoFilePaths;
            if (filePaths == null) {
                Toast.makeText(MainActivity.this, "Picture filePaths is null!", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int i = 0; i < filePaths.length; i++) {
                Log.d(TAG, "MainActivity, videoUpload: uploadFilePath = " + uploadFilePath + ", filePaths[" + i + "] = " + filePaths[i]);
                uploadFile(filePaths[i], true, partSwitch.isChecked());
            }
        } else {
            uploadFilePath = filePathEdt.getText().toString();
            uploadFilePath = "/mnt/sdcard/test_gif.gif";
            Log.d(TAG, "MainActivity, videoUpload: uploadFilePath = " + uploadFilePath);
            uploadFile(uploadFilePath, true, partSwitch.isChecked());
        }
    }

    public void picUpload(View view) {
        boolean isSingleFile = true;
        if (!isSingleFile) {
            String[] filePaths = picFilePaths;
            if (filePaths == null) {
                Toast.makeText(MainActivity.this, "Picture filePaths is null!", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int i = 0; i < filePaths.length; i++) {
                Log.d(TAG, "MainActivity, picUpload: uploadFilePath = " + uploadFilePath + ", filePaths[" + i + "] = " + filePaths[i]);
                uploadFile(filePaths[i], false, partSwitch.isChecked());
            }
        } else {
            uploadFilePath = filePathEdt.getText().toString();
            uploadFilePath = "/mnt/sdcard/test_gif.gif";
            Log.d(TAG, "MainActivity, picUpload: uploadFilePath = " + uploadFilePath);
            uploadFile(uploadFilePath, false, partSwitch.isChecked());
        }
    }

    public void uploadFile(final String filePath, final boolean isVideo, final boolean isMultiPart) {
        if (TextUtils.isEmpty(isVideo ? videoUploadToken : imgUploadToken)) {
            Toast.makeText(context, (isVideo ? "Video" : "Img") + " UploadToken is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(isVideo ? videoDomain : imgDomain)) {
            Toast.makeText(context, (isVideo ? "Video" : "Img") + " Domain is null！", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(filePath)) {
            Toast.makeText(context, "上传文件路径为空！", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    upload(filePath, isVideo, isMultiPart, isVideo ? videoUploadToken : imgUploadToken, isVideo ? videoDomain : imgDomain);
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
        if (!isMultiPart) {
            if (this.simpleUploadManager == null) {
                this.simpleUploadManager = new UploadManager();
            }
        } else {
            if (this.multiUploadManager == null) {
                // 定义分片上传时，断点续传信息保存的 Recorder
                Recorder recorder = null;
                try {
                    recorder = new FileRecorder(Utils.sdkDirectory() + "/recorder");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // 断点续传信息保存时使用的 key 的生成器，根据 key 可以获取相应文件的上传信息
                // 需要确保每个文件的 key 是唯一的，下面为默认值
                // 可以自定义，也可以不配置（keyGenerator 参数可以不传）
                KeyGenerator keyGenerator = new KeyGenerator() {
                    @Override
                    public String gen(String key, File file) {
                        return key + "_._" + new StringBuffer(file.getAbsolutePath()).reverse();
                    }
                    @Override
                    public String gen(String key, String sourceId) {
                        if (sourceId == null) {
                            sourceId = "";
                        }
                        return key + "_._" + sourceId;
                    }
                };
                Configuration configuration = new Configuration.Builder()
                        .zone(new AutoZone())              // 配置上传区域，使用 AutoZone
                        .recorder(recorder, keyGenerator)  // 文件分片上传时断点续传信息保存，表单上传此配置无效
//                        .proxy(proxy)
                        .useHttps(false)
                        .chunkSize(4*1024*1024) // 文件采用分片上传时，分片大小为 4MB
                        .putThreshold(4 * 1024 * 1024) // 分片上传阈值：4MB，大于 4MB 采用分片上传，小于 4MB 采用表单上传
                        .connectTimeout(10) // 请求连接超时 10s
//                        .writeTimeout(30) // 请求写超时 30s
                        .responseTimeout(10) // 请求响应超时 10s
                        .retryMax(1) // 单个域名/IP请求失败后最大重试次数为 1 次
                        .retryInterval(500) // 重试时间间隔
                        .allowBackupHost(true) // 是否使用备用域名进行重试
                        .urlConverter(new UrlConverter() {
                            @Override
                            public String convert(String url) {
                                // 公有云不可配置
                                return url;
                            }
                        })
                        .useConcurrentResumeUpload(true)  // 开启并发分片上传
                        .concurrentTaskCount(3)           // 使用并发分片上传时，一个文件并发上传的分片个数
                        .resumeUploadVersion(Configuration.RESUME_UPLOAD_VERSION_V2) // 使用分片 V2
                        .buildV2();
                this.multiUploadManager = new UploadManager(configuration);
            }
        }
        File uploadFile = new File(filePath);
        UploadOptions uploadOptions = new UploadOptions(null, null, false,
                new UpProgressHandler() {
                    @Override
                    public void progress(String key, double percent) {
                        updateStatus(percent);
                    }
                }, null);
        final long startTime = System.currentTimeMillis();
        final long fileLength = uploadFile.length();
        this.uploadFileLength = fileLength;
        this.uploadLastTimePoint = startTime;
        this.uploadLastOffset = 0;
        UploadManager uploadManager = isMultiPart ? multiUploadManager : simpleUploadManager;

        uploadManager.put(uploadFile, null, uploadToken,
                new UpCompletionHandler() {
                    @Override
                    public void complete(String key, ResponseInfo respInfo,
                                         JSONObject jsonData) {
                        long lastMillis = System.currentTimeMillis() - startTime;
                        Log.d(TAG, "complete: upload key = " + key + "，respInfo is:" + respInfo + "，jsonData is:" + jsonData + "，cost is:" + lastMillis);
                        if (respInfo.isOK()) {
                            try {
                                String fileKey = jsonData.getString("key");
                                if (isVideo) {
                                    final String persistentId = jsonData.getString("persistentId");
                                    final String videoUrl = domain + "/" + fileKey;
                                    Log.d(TAG, "complete: persistentId is " + persistentId + ", videoUrl is " + videoUrl);
                                } else {
                                    DisplayMetrics dm = new DisplayMetrics();
                                    getWindowManager().getDefaultDisplay().getMetrics(dm);
                                    final int width = dm.widthPixels;
                                    final String imageUrl = domain + "/" + fileKey + "?imageView2/0/w/" + width + "/format/jpg";
                                    Log.d(TAG, "complete: imageUrl is " + imageUrl);
                                }
                            } catch (JSONException e) {
                                Toast.makeText(context, context.getString(R.string.qiniu_upload_file_response_parse_error), Toast.LENGTH_LONG).show();
                                Log.e(QiniuLabConfig.LOG_TAG, e.getMessage());
                            }
                        } else {
                            Toast.makeText(
                                    context,
                                    context.getString(R.string.qiniu_upload_file_failed),
                                    Toast.LENGTH_LONG).show();
                            Log.e(QiniuLabConfig.LOG_TAG, respInfo.toString());
                        }
                    }

                }, uploadOptions);
    }

    private void updateStatus(final double percentage) {
        long now = System.currentTimeMillis();
        long deltaTime = now - uploadLastTimePoint;
        long currentOffset = (long) (percentage * uploadFileLength);
        long deltaSize = currentOffset - uploadLastOffset;
        if (deltaTime <= 100) {
            return;
        }

        final String speed = Tools.formatSpeed(deltaSize, deltaTime);
        // update
        uploadLastTimePoint = now;
        uploadLastOffset = currentOffset;
    }
}