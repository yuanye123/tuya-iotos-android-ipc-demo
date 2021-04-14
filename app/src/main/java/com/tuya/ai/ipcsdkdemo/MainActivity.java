package com.tuya.ai.ipcsdkdemo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;

import androidx.appcompat.app.AppCompatActivity;

import com.tuya.ai.ipcsdkdemo.audio.AudioCapture;
import com.tuya.ai.ipcsdkdemo.audio.FileAudioCapture;
import com.tuya.ai.ipcsdkdemo.video.FileVideoCapture;
import com.tuya.ai.ipcsdkdemo.video.H264FileVideoCapture;
import com.tuya.ai.ipcsdkdemo.video.VideoCapture;
import com.tuya.smart.aiipc.base.permission.PermissionUtil;
import com.tuya.smart.aiipc.ipc_sdk.IPCSDK;
import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IControllerManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IGatewayManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMqttProcessManager;
import com.tuya.smart.aiipc.ipc_sdk.api.INetConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IParamConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.callback.DPConst;
import com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent;
import com.tuya.smart.aiipc.ipc_sdk.callback.NetConfigCallback;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;
import com.tuya.smart.aiipc.netconfig.ConfigProvider;
import com.tuya.smart.aiipc.netconfig.mqtt.TuyaNetConfig;
import com.tuya.smart.aiipc.trans.DevDescIf;
import com.tuya.smart.aiipc.trans.HomeSecurityCallbacks;
import com.tuya.smart.aiipc.trans.IPCLog;
import com.tuya.smart.aiipc.trans.IoTGwCallbacks;
import com.tuya.smart.aiipc.trans.IoTGwMiscCallbacks;
import com.tuya.smart.aiipc.trans.ScePanel;
import com.tuya.smart.aiipc.trans.TransJNIInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.TimeZone;

import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_LIGHT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IPC_SDK_DEMO";

    VideoCapture videoCapture;
    AudioCapture audioCapture;

    FileAudioCapture fileAudioCapture;
    FileVideoCapture fileVideoCapture;

    H264FileVideoCapture h264FileMainVideoCapture, h264FileSubVideoCapture;

    private HomeSecurityCallbacks mHomeSecurityCallbacks = new HomeSecurityCallbacks() {

        @Override
        public int onGwOfflineDpSave(String devId, Object[] dp_data) {
            DPEvent[] dpEvent = (DPEvent[])dp_data;
            Log.d(TAG, "ccc onHomeSecurityAlarmDev devId: " + devId);
            return 0;
        }

        @Override
        public void onHomeSecurityIf(String modeStr, int time, boolean isSound) {
            Log.d(TAG, "ccc onHomeSecurityIf modeStr: " + modeStr + " time: " + time + " isSound: " + isSound);
        }

        @Override
        public void onHomeSecurityAlarmDev(String cid, String jsonDpInf) {
            Log.d(TAG, "ccc onHomeSecurityAlarmDev cid: " + cid + " jsonDpInf: " + jsonDpInf);
        }

        @Override
        public void onHomeSecurityAlarmEnvDev(String cid, String jsonDpInf) {
            Log.d(TAG, "ccc onHomeSecurityAlarmEnvDev cid: " + cid + " dpInf: " + jsonDpInf);
        }

        @Override
        public void onHomeSecurityAlarmDelayStatus(int alarmStatus) {
            Log.d(TAG, "ccc onHomeSecurityAlarmDelayStatus alarmStatus: " + alarmStatus);

        }

        @Override
        public void onHomeSecurityEvent(int status, int data) {
            Log.d(TAG, "ccc onHomeSecurityEvent status: " + status + " data: " + data);
        }

        @Override
        public void onHomeSecurityCancelAlarm() {
            Log.d(TAG, "ccc onHomeSecurityCancelAlarm ");
        }

        @Override
        public void onHomeSecurityAlarmDevNew(String cid, String dpInfJson, int devType) {
            Log.d(TAG, "ccc onHomeSecurityAlarmDevNew cid: " + cid + " dpInfJson: " + dpInfJson + " devType: " + devType);
        }

        @Override
        public void onHomeSecurityEnterAlarm(Boolean alarmStatus, String alarmInfo) {
            Log.d(TAG, "ccc onHomeSecurityEnterAlarm alarmStatus: " + alarmStatus + " alarmInfo: " + alarmInfo);
        }
    };

    private IoTGwMiscCallbacks mIoTGwMiscCallbacks = new IoTGwMiscCallbacks() {
        @Override
        public void onExeDpIssue(String condCid, int condDpId, String actDpCid, int actDpId) {
            Log.d(TAG, "ccc onExeDpIssue condCid: " + condCid + " condDpId: " + condDpId + "actDpCid: " + actDpCid + " actDpId: " + actDpId);
        }

        @Override
        public void onGwEngrToNormalFinish(String path) {
            Log.d(TAG, "ccc onGwEngrToNormalFinish path: " + path);
        }

        @Override
        public void onDevHeartbeatSend(String dev_id) {
            Log.d(TAG, "ccc onDevHeartbeatSend dev_id: " + dev_id);
        }

        @Override
        public int onEngrGwScePanel(String dev_id, ScePanel scePanel, int btn_num) {
            Log.d(TAG, "ccc onEngrGwScePanel dev_id: " + dev_id + " btn_num: " + btn_num);

            return 0;
        }

        @Override
        public void onAppLogPath(int lengthLimit) {
            Log.d(TAG, "ccc onAppLogPath lengthLimit: " + lengthLimit);

        }
    };

    private IoTGwCallbacks mIoTGwCallbacks = new IoTGwCallbacks() {
        @Override
        public int onGwAddDev(int tp, int permit, int timeout) {
            Log.d(TAG, "ccc onGwAddDev tp: " + tp + " permit: " + permit + " timeout: " + timeout);

            if (permit == 0)
                return 0;

            IGatewayManager gatewayManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.GATEWAY_SERVICE);

            /* 绑定子设备 */


            return 0;
        }

        @Override
        public void onGwDelDev(String dev_id, int type) {
            Log.d(TAG, "ccc onGwDelDev dev_id: " + dev_id + " type: " + type);

            IGatewayManager gatewayManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.GATEWAY_SERVICE);

            /* 解绑子设备 */
            if (dev_id.equals("changcheng01"))
                gatewayManager.gwUnbindDevice("changcheng01");

            /* 解绑子设备 */
            if (dev_id.equals("changcheng02"))
                gatewayManager.gwUnbindDevice("changcheng02");

        }

        @Override
        public int onGwDevGrp(int action, String dev_id, String grp_id) {
            Log.d(TAG, "ccc onGwDevGrp action: " + action + " dev_id: " + dev_id + " grp_id: " + grp_id);
            return 0;
        }

        @Override
        public int onGwDevScene(int action, String dev_id, String grp_id, String sce_id) {
            Log.d(TAG, "ccc onGwDevScene action: " + action + " dev_id: " + dev_id + " grp_id: " + grp_id + " sce_id: " + sce_id);
            return 0;
        }

        @Override
        public void onGwIfm(String dev_id, int op_ret) {
            Log.d(TAG, "ccc onGwIfm dev_id: " + dev_id + " op_ret: " + op_ret);
        }

        @Override
        public void onGwDevSigmeshTopoUpdate(String dev_id) {
            Log.d(TAG, "ccc onGwDevSigmeshTopoUpdate dev_id: " + dev_id);
        }

        @Override
        public void onGwDevSigmeshDevCacheDel(String dev_id) {
            Log.d(TAG, "ccc onGwDevSigmeshDevCacheDel dev_id: " + dev_id);
        }

        @Override
        public void onGwDevWakeup(String dev_id, int duration) {
            Log.d(TAG, "ccc onGwDevWakeup dev_id: " + dev_id + " duration: " + duration);
        }

        @Override
        public void onGwDevSigmeshConn(String sigmesh_dev_conn_inf_json) {
            Log.d(TAG, "ccc onGwDelDev sigmesh_dev_conn_inf_json: " + sigmesh_dev_conn_inf_json);
        }
    };

    private void setGatewayCallbacks(HomeSecurityCallbacks homeSecurityCallbacks, IoTGwMiscCallbacks ioTGwMiscCallbacks, IoTGwCallbacks ioTGwCallbacks) {

        IGatewayManager gatewayManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.GATEWAY_SERVICE);

        gatewayManager.setHomeSecurityCallbacks(homeSecurityCallbacks);

        gatewayManager.setIoTGwMiscCallbacks(ioTGwMiscCallbacks);

        gatewayManager.setIoTGwCallbacks(ioTGwCallbacks);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        findViewById(R.id.reset).setOnClickListener(v -> IPCServiceManager.getInstance().reset());

        findViewById(R.id.record).setOnClickListener(v -> {

            IMediaTransManager transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
            transManager.startCloudStorage();
        });

        //低功耗
        findViewById(R.id.lowpower).setOnClickListener(v -> {
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

                int ret = mediaTransManager.sendDoorBellCallForPress(file, Common.NOTIFICATION_CONTENT_TYPE_E.NOTIFICATION_CONTENT_JPEG);
                Log.w(TAG, "sendDoorBellCallForPress ret: " + ret);

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        findViewById(R.id.traversal_device).setOnClickListener(v -> {
            IGatewayManager gatewayManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.GATEWAY_SERVICE);

            DevDescIf devDescIf = null;

            while (true) {
                devDescIf = gatewayManager.IotGateWayDevTraversal();
                if (devDescIf == null) {
                    Log.d(TAG, "Traversal done ");
                    break;
                } else {
                    Log.d(TAG, "device: " + devDescIf.toString());
                    if (devDescIf.mAttr != null) {
                        Log.d(TAG, "devDescIf.mAttr.length: " + devDescIf.mAttr.length);
                        for (int i = 0; i < devDescIf.mAttr.length; i++)
                            Log.d(TAG, "attr[" + i + "]: "+ devDescIf.mAttr[i].toString());
                    }
                }
            }
        });

        PermissionUtil.check(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.RECORD_AUDIO
        }, () -> {
            initSDK();

            startConfig();
        });

        setGatewayCallbacks(mHomeSecurityCallbacks, mIoTGwMiscCallbacks, mIoTGwCallbacks);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // IPCSDK.closeWriteLog();
    }

    private void initSDK() {
        // IPCSDK.setLevelOfLog(10);
        IPCSDK.initSDK(MainActivity.this);
        // IPCLog.setConsoleLogOpen(false);
        // IPCSDK.openWriteLog(MainActivity.this, "/sdcard/tuya_log/ipc/", 3, false);
        LoadParamConfig();

        //注册简单处理DP的接口
        IControllerManager controllerManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.CONTROLLER_SERVICE);
        controllerManager.setDpEventSimpleCallback((cid, v, dpid, time_stamp) -> {
            //处理dp
            if (dpid == TUYA_DP_LIGHT) {
                return new DPConst.DPResult(true, DPConst.Type.PROP_BOOL);
            }

            Log.d(TAG, "ccc cid: " + cid + " dpid: " + dpid);

            if (dpid == 1) {
                return new DPConst.DPResult(v, DPConst.Type.PROP_BOOL);
            }

            return null;
        });

        IPCServiceManager.getInstance().setResetHandler(isHardward -> {

            //restart
            Intent mStartActivity = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (mStartActivity != null) {
                int mPendingIntentId = 123456;
                PendingIntent mPendingIntent = PendingIntent.getActivity(MainActivity.this, mPendingIntentId
                        , mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                Runtime.getRuntime().exit(0);
            }
        });

        //开始配网，目前支持蓝牙\二维码\MQTT
        INetConfigManager iNetConfigManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.NET_CONFIG_SERVICE);
        SurfaceView surfaceView = findViewById(R.id.surface);

        iNetConfigManager.setPID(BuildConfig.PID);
        iNetConfigManager.setUserId(BuildConfig.UUID);
        iNetConfigManager.setAuthorKey(BuildConfig.AUTHOR_KEY);
        Log.d(TAG, "ccc PID: " + BuildConfig.PID);
        Log.d(TAG, "ccc UUID: " + BuildConfig.UUID);
        Log.d(TAG, "ccc AUTHOR_KEY: " + BuildConfig.AUTHOR_KEY);

        iNetConfigManager.config(INetConfigManager.QR_OUTPUT, surfaceView.getHolder());

        iNetConfigManager.config(INetConfigManager.QR_PARAMETERS, (INetConfigManager.OnParameterSetting) (p, camera) -> {
            camera.setDisplayOrientation(90);
        });

        ConfigProvider.enableMQTT(false);

        TuyaNetConfig.setDebug(true);

    }

    private void startConfig() {

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

                IPCLog.w(TAG, "configOver: token" + token);
                IMediaTransManager transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);

                IMqttProcessManager mqttProcessManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MQTT_SERVICE);

                transManager.setP2PEventCallback((event, value) -> {
                    switch (event) {
                        case TRAN_VIDEO_CLARITY_SET:
                            int val = Integer.valueOf(value.toString());
                            IPCLog.d(TAG, "TRAN_VIDEO_CLARITY_SET " + val);
                            if (val == IMediaTransManager.TRAN_VIDEO_CLARITY_VALUE.HIGH) {
                            } else if (val == IMediaTransManager.TRAN_VIDEO_CLARITY_VALUE.STANDARD) {

                            }
                            break;
                        case TRANS_LIVE_VIDEO_START:
                            break;
                        case TRANS_LIVE_VIDEO_STOP:
                            break;
                    }
                });

                mqttProcessManager.setMqttStatusChangedCallback(status -> {
                    IPCLog.w("onMqttStatus", status + "");

                    if (status == Common.MqttConnectStatus.STATUS_CLOUD_CONN) { // 连接到云
                        transManager.startMultiMediaTrans(5);

                        PermissionUtil.check(MainActivity.this, new String[]{
                                Manifest.permission.RECORD_AUDIO,
                        }, () -> {
                            videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
                            audioCapture = new AudioCapture();
                            videoCapture.startVideoCapture();
                            audioCapture.startCapture();
                        });
                    }
                });

                transManager.initTransSDK(token, "/sdcard/", "/sdcard/", BuildConfig.PID, BuildConfig.UUID, BuildConfig.AUTHOR_KEY);

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

    private static void syncTimeZone() {
        int rawOffset = TransJNIInterface.getInstance().getAppTimezoneBySecond();
        String[] availableIDs = TimeZone.getAvailableIDs(rawOffset * 1000);
        if (availableIDs.length > 0) {
            Log.d(TAG, "syncTimeZone: " + rawOffset + " , " + availableIDs[0] + " ,  ");
        }
    }

    private void LoadParamConfig() {
        IParamConfigManager configManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_PARAM_SERVICE);

        /**
         * 主码流参数配置
         * */
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 30);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);


        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 15);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_BIT_RATE, 512000);

        /**
         * 音频流参数
         * */
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);
    }
}
