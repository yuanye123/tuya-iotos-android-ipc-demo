package com.tuya.ai.ipcsdkdemo.video;

import android.content.Context;
import android.util.Log;

import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

public class FileVideoCapture {

    private final static int VIDEO_BUF_SIZE = 1024 * 400;

    private byte[] videoBuffer;
    private byte[] infoBuffer;

    private File videoFile;
    private File infoFile;

    private String videoPath;
    private String videoInfoPath;

    private InputStream videoFis;
    private InputStream infoFis;

    IMediaTransManager transManager;

    Context context;

    private int mChannel;

    public FileVideoCapture(Context context, int channel) {

        this.mChannel = channel;
        this.context = context;
        transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);


//        videoBuffer = new byte[VIDEO_BUF_SIZE];
        infoBuffer = new byte[128];

//        videoFile = new File(videoPath);
//        infoFile = new File(videoInfoPath);

        try {
            videoFis = context.getAssets().open("rawfiles/video_multi/beethoven_240.multi/frames.bin");
            infoFis = context.getAssets().open("rawfiles/video_multi/beethoven_240.multi/frames.info");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void startVideoCapture() {


        new Thread(new Runnable() {
            @Override
            public void run() {

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(infoFis));

                int frameRate = 30;
                int sleepTick = 1000 / frameRate;
                try {
                    //FPS
                    reader.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int lastPos = 0;
                while (true) {
                    try {
                        String content = reader.readLine();
                        if (content == null || content.isEmpty()) {
                            Log.d("FileTest", "run: ");
                            lastPos = 0;
                            reader.reset();
                            videoFis.reset();
                            reader.readLine();
                            continue;
//                            break;
                        }
                        Scanner scanner = new Scanner(content);
                        String frame_type = scanner.next();

                        int frame_pos = scanner.nextInt();
                        int frame_size = scanner.nextInt();
//
                        int nRet = 0;

                        scanner.close();

                        assert (videoFis.skip(frame_pos -lastPos) == frame_pos -lastPos);
                        lastPos = frame_pos;
                        videoBuffer = new byte[frame_size];
                        nRet = videoFis.read(videoBuffer,0,frame_size);
                        if (nRet < frame_size) {
                            Log.d("FileTest", "nRet < frame_size");
//                            lastPos = 0;
//                            videoFis.reset();
//                            infoFis.reset();
////                            reader.reset();
//                            reader.readLine();
//                            continue;
                        }
                        if (frame_type.equals("I")) {
                            transManager.pushMediaStream(mChannel, Common.NAL_TYPE.NAL_TYPE_IDR, videoBuffer);
                        } else {
                            transManager.pushMediaStream(mChannel, Common.NAL_TYPE.NAL_TYPE_PB, videoBuffer);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep(sleepTick);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }).start();

        Log.d("FileTest", "startVideoCapture: ");
    }

    public void stopFileCapture() {


    }

}
