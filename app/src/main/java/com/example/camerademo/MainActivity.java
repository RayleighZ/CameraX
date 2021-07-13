package com.example.camerademo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private ImageReader imageReader;
    private CameraManager cameraManager;
    private String curCameraId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CameraDevice cameraDevice;
    private TextureView preview;
    private Handler cameraHandler;
    private CameraCaptureSession captureSession;
    private TextView iso;
    private TextView gray;
    private TextView time;
    private MHandler mHandler;

    int wi = 0;
    int he = 0;
    int isoValue = 2;
    long exploreTime = 1000;
    CameraCharacteristics characteristics;

    class MHandler extends Handler{
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            gray.setText("灰度: "+msg.obj.toString());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iso = findViewById(R.id.iso);
        gray = findViewById(R.id.gray);
        time = findViewById(R.id.time);
        mHandler = new MHandler();
        HandlerThread thread = new HandlerThread("camera");
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        wi = wm.getDefaultDisplay().getWidth();
        he = wm.getDefaultDisplay().getHeight();
        thread.start();
        cameraHandler = new Handler(
                thread.getLooper()
        );
        //拿到CameraManager
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        preview = findViewById(R.id.preview);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                //获取相机信息
                characteristics = cameraManager.getCameraCharacteristics(cameraId);

                Integer in = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (null != in && in == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                curCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        imageReader = ImageReader.newInstance(1000, 1000, ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(
                imageReader -> {
                    cameraHandler.post(
                            () -> {
                                Log.d("RayleighZ", "CAP");
                                //存储图像
                                Image image = imageReader.acquireLatestImage();
                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                byte[] data = new byte[buffer.remaining()];
                                buffer.get(data);
                                image.close();
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                                int cropWidth = 100;// 裁切后所取的正方形区域边长

                                Bitmap center =  Bitmap.createBitmap(bitmap, (bitmap.getWidth() - cropWidth) / 2,
                                        (bitmap.getHeight() - cropWidth) / 2, cropWidth, cropWidth);

                                Message m = new Message();
                                m.obj = (getAverGrey(bitmap) + "").substring(0, 8);

                                mHandler.sendMessage(
                                        m
                                );
                            }
                    );
                },
                cameraHandler
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                cameraManager.openCamera(curCameraId, mStateCallback, mainHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                cameraManager.openCamera(curCameraId, mStateCallback, mainHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    //打开相机的回调函数
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            preview();
//				  mCameraOpenCloseLock.release();
//				  mCameraDevice = camera;
//				  createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
//				  mCameraOpenCloseLock.release();
//				  camera.close();
//				  mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
//				  mCameraOpenCloseLock.release();
//				  camera.close();
//				  mCameraDevice = null;
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
        }
    };

    private void preview() {
        preview.setSurfaceTextureListener(
                new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {

                        texture.setDefaultBufferSize(he, wi);
                        @SuppressLint("Recycle") Surface surface = new Surface(texture);
                        try {
                            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            builder.addTarget(surface);
                            cameraDevice.createCaptureSession(
                                    Arrays.asList(surface, imageReader.getSurface()),
                                    new CameraCaptureSession.StateCallback() {
                                        @Override
                                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                            captureSession = cameraCaptureSession;
                                            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                                            int max = (int) (characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getUpper() * characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).doubleValue());
                                            builder.set(CaptureRequest.SENSOR_SENSITIVITY, -max);
                                            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 8000000L);
                                            time.setText("曝光时间: "+"80 ms");
                                            iso.setText("ISO: "+max+"ev");
                                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                            try {
                                                cameraCaptureSession.setRepeatingRequest(
                                                        builder.build(),
                                                        null,
                                                        cameraHandler
                                                );
                                                findViewById(R.id.cap).setOnClickListener(
                                                        (v) -> {
                                                            try {
                                                                builder.addTarget(imageReader.getSurface());
                                                                captureSession.capture(builder.build(), null, null);
                                                            } catch (CameraAccessException e) {
                                                                e.printStackTrace();
                                                            }
                                                        }
                                                );
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }

                                        @Override
                                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                        }
                                    },
                                    null
                            );
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                        return false;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

                    }
                }
        );
    }

    private boolean isPreview = true;

    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (!isPreview) {
                //处理图像

            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }
    };

    private float getAverGrey(Bitmap img) {
        int width = img.getWidth();         //获取位图的宽
        int height = img.getHeight();       //获取位图的高

        float total = 0f;

        int[] pixels = new int[width * height]; //通过位图的大小创建像素点数组

        img.getPixels(pixels, 0, width, 0, 0, width, height);
        int alpha = 0xFF << 24;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];

                int red = ((grey & 0x00FF0000) >> 16);
                int green = ((grey & 0x0000FF00) >> 8);
                int blue = (grey & 0x000000FF);

                grey = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                grey = alpha | (grey << 16) | (grey << 8) | grey;
                pixels[width * i + j] = grey;
                total += grey;
            }
        }
        return total/ (height * width) / 100000;
//        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        result.setPixels(pixels, 0, width, 0, 0, width, height);
//        return result;
    }
}