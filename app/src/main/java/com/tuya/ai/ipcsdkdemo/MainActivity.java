package com.tuya.ai.ipcsdkdemo;

import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_LIGHT;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.tuya.ai.ipcsdkdemo.audio.FileAudioCapture;
import com.tuya.ai.ipcsdkdemo.video.VideoCapture;
import com.tuya.smart.aiipc.base.permission.PermissionUtil;
import com.tuya.smart.aiipc.ipc_sdk.IPCSDK;
import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IControllerManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IDeviceManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IFeatureManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMqttProcessManager;
import com.tuya.smart.aiipc.ipc_sdk.api.INetConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IParamConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.callback.DPConst;
import com.tuya.smart.aiipc.ipc_sdk.callback.IMqttStatusCallback;
import com.tuya.smart.aiipc.ipc_sdk.callback.NetConfigCallback;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;
import com.tuya.smart.aiipc.netconfig.ConfigProvider;
import com.tuya.smart.aiipc.netconfig.mqtt.TuyaNetConfig;
import com.tuya.smart.aiipc.trans.IPCLog;
import com.tuya.smart.aiipc.trans.ServeInfo;
import com.tuya.smart.aiipc.trans.TransJNIInterface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    SurfaceView surfaceView;

    VideoCapture videoCapture;

    FileAudioCapture fileAudioCapture;

    private Handler mHandler;
    private boolean isCallEnable = false;
    Button callBtn;

    String pid = BuildConfig.PID;
    String uuid = BuildConfig.UUID;
    String authkey = BuildConfig.AUTHOR_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface);
        mHandler = new Handler();

        findViewById(R.id.reset).setOnClickListener(v -> IPCServiceManager.getInstance().reset());

        findViewById(R.id.start_record).setOnClickListener(v -> TransJNIInterface.getInstance().startLocalStorage());

        findViewById(R.id.stop_record).setOnClickListener(v -> TransJNIInterface.getInstance().stopLocalStorage());

        callBtn = findViewById(R.id.call);
        callBtn.setOnClickListener(v -> {
            if (isCallEnable) {
                IDeviceManager iDeviceManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.DEVICE_SERVICE);
                // check register status
                int regStat = iDeviceManager.getRegisterStatus();
                Log.d(TAG, "ccc getting qrcode, register status: " + regStat);
                if (regStat != 2) {
                    // get short url for qrcode
                    String code = iDeviceManager.getQrCode("168");
                    Log.d(TAG, "ccc qrcode: " + code);
                }

            /*
            IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);

            try {
                InputStream fileStream = getAssets().open("leijun.jpeg");

                byte[] buffer = new byte[2048];
                int bytesRead;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                byte[] file = output.toByteArray();
                mediaTransManager.sendDoorBellCallForPress(file, Common.NOTIFICATION_CONTENT_TYPE_E.NOTIFICATION_CONTENT_JPEG);

            } catch (IOException e) {
                e.printStackTrace();
            }
*/
            }
        });

        PermissionUtil.check(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        }, this::initSurface);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        IPCSDK.closeWriteLog();
    }

    private void initSurface() {
        if (surfaceView.getHolder().getSurface().isValid()) {
            Log.d(TAG, "initSurface isValid");
            initSDK();
            startConfig();
        } else {
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder surfaceHolder) {
                    Log.d(TAG, "initSurface surfaceCreated");
                    initSDK();
                    startConfig();
                }

                @Override
                public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

                }
            });
        }
    }

    private void initSDK() {
        IPCSDK.initSDK(MainActivity.this);
        IPCSDK.openWriteLog(MainActivity.this, "/sdcard/tuya_log/ipc/", 3);
        LoadParamConfig();

        //注册简单处理DP的接口
        IControllerManager controllerManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.CONTROLLER_SERVICE);
        controllerManager.setDpEventSimpleCallback((v, dpid, time_stamp) -> {
            //处理dp
            if (dpid == TUYA_DP_LIGHT) {
                return new DPConst.DPResult(true, DPConst.Type.PROP_BOOL);
            }
            return null;
        });

        IPCServiceManager.getInstance().setResetHandler(isHardward -> restart());

        //开始配网，目前支持蓝牙\二维码\MQTT
        INetConfigManager iNetConfigManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.NET_CONFIG_SERVICE);
        SurfaceView surfaceView = findViewById(R.id.surface);

        iNetConfigManager.setPID(pid);
        iNetConfigManager.setAuthorKey(authkey);
        iNetConfigManager.setUserId(uuid);

        iNetConfigManager.config(INetConfigManager.QR_OUTPUT, surfaceView.getHolder());

        iNetConfigManager.config(INetConfigManager.QR_PARAMETERS, (INetConfigManager.OnParameterSetting) (p, camera) -> {
            camera.setDisplayOrientation(90);
        });

        ConfigProvider.enableMQTT(false);
        ConfigProvider.enableQR(true);

        TuyaNetConfig.setDebug(true);

    }

    private void restart() {
        Intent mStartActivity = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (mStartActivity != null) {
            int mPendingIntentId = 123456;
            PendingIntent mPendingIntent = PendingIntent.getActivity(MainActivity.this, mPendingIntentId
                    , mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
            Runtime.getRuntime().exit(0);
        }
    }

    private void startConfig() {
        IPCServiceManager.getInstance().setDnsHandler(hostname -> {
            String hostAddr = null;
            try {
                hostAddr = InetAddress.getByName(hostname).getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                Log.e(TAG, "DNSHandler: " + e.getMessage());
            }

            Log.e(TAG, "getIP: " + hostAddr);
            return hostAddr;
        });

        INetConfigManager iNetConfigManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.NET_CONFIG_SERVICE);


        NetConfigCallback netConfigCallback = new NetConfigCallback() {

            /**
             * 配网失败，建议提示用户重新操作
             * @param type 配网类型 ConfigProvider.TYPE_XX
             * @param msg 错误信息
             */
            @Override
            public void onNetConnectFailed(int type, String msg) {
                IPCLog.w(TAG, String.format(Locale.getDefault(), "onNetConnectFailed: %d %s", type, msg));
            }

            /**
             * 配网准备阶段失败，建议重试
             * @param type 配网类型 ConfigProvider.TYPE_XX
             * @param msg 错误信息
             */
            @Override
            public void onNetPrepareFailed(int type, String msg) {
                IPCLog.w(TAG, "onNetPrepareFailed: " + type + " / " + msg);
                iNetConfigManager.retry(type);
            }

            @Override
            public void configOver(boolean first, String token) {

                IPCLog.w(TAG, "configOver: token " + token);
                IMediaTransManager transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
                IMqttProcessManager mqttProcessManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MQTT_SERVICE);
                IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
                IFeatureManager featureManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.FEATURE_SERVICE);

                mqttProcessManager.setMqttStatusChangedCallback(new IMqttStatusCallback() {
                    @Override
                    public void onMqttStatus(int i) {
                        Log.d(TAG, "onMqttStatus status is " + i);
                        if (i == Common.MqttConnectStatus.STATUS_CLOUD_CONN) {
                            featureManager.initDoorBellFeatureEnv();
                            // video stream from camera
                            videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
                            videoCapture.startVideoCapture();

                            // audio stream from local file
                            fileAudioCapture = new FileAudioCapture(MainActivity.this);
                            fileAudioCapture.startFileCapture();

                            //  start push media
                            transManager.startMultiMediaTrans(5);

//                            keepHeartToWakeup();
                        }
                    }
                });

                mediaTransManager.setDoorBellCallStatusCallback(status -> {

                    Log.d(TAG, "doorbell back: " + status);

                });

                IDeviceManager iDeviceManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.DEVICE_SERVICE);
                // set region
                iDeviceManager.setRegion(IDeviceManager.IPCRegion.REGION_CN);

                int ret;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ret = transManager.initTransSDK(token, "/data/data/com.tuya.ai.ipcsdkdemo/files/ipc/", "/data/data/com.tuya.ai.ipcsdkdemo/files/ipc/", pid, uuid, authkey);
                } else {
                    ret = transManager.initTransSDK(token, "/sdcard/ipc/", "/sdcard/ipc/", pid, uuid, authkey);
                }

                Log.d(TAG, "initTransSDK ret is " + ret);

                if (!isCallEnable) {
                    isCallEnable = true;
                    runOnUiThread(() -> callBtn.setEnabled(true));
                }
                syncTimeZone();
            }

            @Override
            public void startConfig() {
                IPCLog.w(TAG, "startConfig: ");
            }

            @Override
            public void recConfigInfo() {
                IPCLog.w(TAG, "recConfigInfo: ");
            }
        };
        iNetConfigManager.configNetInfo(netConfigCallback);
    }

    private void LoadParamConfig() {
        IParamConfigManager configManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_PARAM_SERVICE);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 24);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);
    }

    private static void syncTimeZone() {
        int rawOffset = TransJNIInterface.getInstance().getAppTimezoneBySecond();
        String[] availableIDs = TimeZone.getAvailableIDs(rawOffset * 1000);
        if (availableIDs.length > 0) {
            android.util.Log.d(TAG, "syncTimeZone: " + rawOffset + " , " + availableIDs[0] + " ,  ");
        }
    }

    //唤醒部分长连接可以参考以下部分
    Socket socket = null;

    private void keepHeartToWakeup() {
        IMediaTransManager transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
        //获取当前的deviceId
        String dip = transManager.getIpcDeviceId();
        //获取当前的key
        String key = transManager.getIpcLocalKey();
        //获取云端的ip
        ServeInfo serveInfo = transManager.getIpcLowPowerServerWithIpString();
        Log.d(TAG, "keepHeartToWakeup getIpcDeviceId is " + dip + " transManager.getIpcLocalKey() is " + key + " serveInfo.ip is " + serveInfo.ip + " port is " + serveInfo.port);
        try {
            socket = new Socket(serveInfo.ip, Integer.parseInt(serveInfo.port));
            socket.setSoTimeout(1000 * 10);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "create sockect is error is " + e.getMessage());
        }

        // 判断客户端和服务器是否连接成功
        if (socket.isConnected()) {
            Log.d(TAG, "sockect is connected");
            try {
                //发送鉴权信息给云端
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(transManager.authInit(dip, key));
                outputStream.flush();

                InputStream isr = socket.getInputStream();
                byte[] data = new byte[512];
                AtomicInteger len = new AtomicInteger(isr.read(data, 0, 512));
                int ret;
                if (len.get() > 0) {
                    Log.d(TAG, "sockect authResult read len is " + len);
                    //处理鉴权结果， ret > 0 鉴权成功
                    ret = transManager.authResult(dip, key, data, len.get());
                    if (ret > 0) {
                        Log.d(TAG, "sockect authResult success!");
                        //获取唤醒数据
                        byte[] waupStr = transManager.getWakeUpMessage();
                        //获取心跳数据
                        byte[] heart = transManager.getHeartMessage();
                        new Thread(() -> {
                            while (true) {
                                try {
                                    //发送心跳
                                    outputStream.write(heart);
                                    outputStream.flush();

                                    for (int i = 0; i < heart.length; i++) {
                                        Log.d(TAG, "now heart data is " + heart[i]);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Log.d(TAG, "sockect send heart error is " + e.getMessage());
                                }

                                //接收服务端数据
                                try {
                                    len.set(isr.read(data, 0, 512));
                                    Log.d(TAG, "sockect read heart len is " + len);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Log.d(TAG, "sockect thread read error is " + e.getMessage());
                                    break;
                                }

                                if (len.get() > 0) {
                                    int count = 0;
                                    for (int i = 0; i < len.get(); i++) {
                                        Log.d(TAG, "now wake up data is " + data[i] + " wak is " + waupStr[i]);
                                        if (data[i] == waupStr[i]) {
                                            count++;
                                        }
                                    }

                                    if (count == len.get()) {
                                        Log.d(TAG, "now wake up sdk !!!!");
                                        break;
                                    }
                                }
                                try {
                                    Thread.sleep(1000 * 5);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    } else {
                        Log.d(TAG, "sockect authResult failed!");
                    }
                } else {
                    Log.d(TAG, "sockect authResult read len is " + len);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Exception error is " + e.getMessage());
            }
        } else {
            Log.d(TAG, "sockect is not connected");
        }
    }
}
