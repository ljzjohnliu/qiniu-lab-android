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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.android.utils.AsyncRun;
import com.qiniu.qiniulab.R;
import com.qiniu.qiniulab.config.QiniuLabConfig;
import com.qiniu.qiniulab.utils.Tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "MainActivity";
    private static Context context;
    private final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1000;

    TextView videoDomainTokenTxt;
    TextView imgDomainTokenTxt;
    EditText filePathEdt;
    EditText sliceSizeEdt;

    private UploadManager uploadManager;
    private String videoUploadToken;
    private String videoDomain;
    private String imgUploadToken;
    private String imgDomain;
    private long uploadLastTimePoint;
    private long uploadLastOffset;
    private long uploadFileLength;
    private String uploadFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        videoDomainTokenTxt = (TextView) findViewById(R.id.video_domain);
        imgDomainTokenTxt = (TextView) findViewById(R.id.img_domain);
        filePathEdt = (EditText) findViewById(R.id.file_path);
        sliceSizeEdt = (EditText) findViewById(R.id.slice_size);
        requestPermissions();
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

    public void videoSimpleUpload(View view) {
        uploadFile(true, false);
    }

    public void videoMultiUpload(View view) {
        uploadFile(true, true);
    }

    public void imgSimpleUpload(View view) {
        uploadFile(false, false);
    }

    public void imgMultiUpload(View view) {
        uploadFile(false, true);
    }

    public void uploadFile(final boolean isVideo, final boolean isMulti) {
        if (TextUtils.isEmpty(isVideo ? videoUploadToken : imgUploadToken)) {
            Toast.makeText(context, isVideo ? "Video" : "Img" + " UploadToken is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(isVideo ? videoDomain : imgDomain)) {
            Toast.makeText(context, isVideo ? "Video" : "Img" + " Domain is null！", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadFilePath = filePathEdt.getText().toString();
        uploadFilePath = "/mnt/sdcard/test_gif.gif";
        Log.d(TAG, "MainActivity, simpleUpload:  uploadFilePath = " + uploadFilePath);
        if (TextUtils.isEmpty(uploadFilePath)) {
            Toast.makeText(context, "请输入需要上传文件路径！", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    upload(isVideo, isMulti, isVideo ? videoUploadToken : imgUploadToken, isVideo ? videoDomain : imgDomain);
                } catch (final Exception e) {
                    AsyncRun.runInMain(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    Log.e(QiniuLabConfig.LOG_TAG, e.getMessage());
                }
            }
        }).start();
    }

    private void upload(final boolean isVideo, final boolean isMulti, final String uploadToken, final String domain) {
        if (this.uploadManager == null) {
            this.uploadManager = new UploadManager();
        }
        File uploadFile = new File(this.uploadFilePath);
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

        this.uploadManager.put(uploadFile, null, uploadToken,
                new UpCompletionHandler() {
                    @Override
                    public void complete(String key, ResponseInfo respInfo,
                                         JSONObject jsonData) {
                        long lastMillis = System.currentTimeMillis() - startTime;
                        Log.d(TAG, "complete: upload cost is:" + lastMillis);
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