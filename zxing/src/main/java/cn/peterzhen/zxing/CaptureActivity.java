/*
 *  Copyright(c) 2017 lizhaotailang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.peterzhen.zxing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.orhanobut.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import cn.peterzhen.zxing.camera.CameraManager;
import cn.peterzhen.zxing.decode.DecodeFormatManager;
import cn.peterzhen.zxing.utils.BeepManager;
import cn.peterzhen.zxing.utils.InactivityTimer;


/**
 * zxing
 *
 * @author zhenyanjun
 * @date 2018/12/25 09:48
 */

public class CaptureActivity extends Activity implements SurfaceHolder.Callback {

    public static final String KEY_RESULT = "result";

    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private SurfaceView scanPreview = null;
    private RelativeLayout scanContainer;
    private RelativeLayout scanCropView;
    private ImageView scanLine;

    private Rect mCropRect = null;
    private long mStartTime;

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    private boolean isHasSurface = false;

    /**
     * 请求扫描二维码
     *
     * @param activity
     * @param requestCode
     */
    public static void requestScan(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, CaptureActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }
    /**
     * 请求扫描二维码
     *
     * @param fragment
     * @param requestCode
     */
    public static void requestScan(Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getActivity(), CaptureActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    /**
     * 获取结果
     *
     * @param intent
     * @return
     */
    public static String obtainResult(Intent intent) {
        if (intent.hasExtra(KEY_RESULT)) {
            return intent.getStringExtra(KEY_RESULT);
        } else {
            return "";
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        initData(savedInstanceState);
    }

    public void initData(Bundle savedInstanceState) {
        scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
        scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
        scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
        scanLine = (ImageView) findViewById(R.id.capture_scan_line);

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);

        TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.9f);
        animation.setDuration(4500);
        animation.setRepeatCount(-1);
        animation.setRepeatMode(Animation.RESTART);
        scanLine.startAnimation(animation);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());

        handler = null;

        if (isHasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(scanPreview.getHolder());
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            scanPreview.getHolder().addCallback(this);
        }

        inactivityTimer.onResume();
        mStartTime = Calendar.getInstance().getTimeInMillis();
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!isHasSurface) {
            scanPreview.getHolder().removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Logger.e("*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!isHasSurface) {
            isHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult The contents of the barcode.
     * @param bundle    The extras
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();
        long endTime = Calendar.getInstance().getTimeInMillis();
        Logger.d("扫码内容："+rawResult.getText()+","+"扫码速度："+(endTime-mStartTime)+"ms");

        Intent resultIntent = new Intent();
        bundle.putInt("width", mCropRect.width());
        bundle.putInt("height", mCropRect.height());
        bundle.putString(KEY_RESULT, rawResult.getText());
        resultIntent.putExtras(bundle);

        this.setResult(RESULT_OK, resultIntent);
        this.finish();
    }

    /**
     * Init the camera.
     *
     * @param surfaceHolder The surface holder.
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Logger.w( "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(cameraManager, DecodeThread.ALL_MODE);
//                handler = new CaptureActivityHandler(this, cameraManager, DecodeThread.QRCODE_MODE);
            }

            initCrop();
        } catch (IOException ioe) {
            Logger.w(ioe.getMessage());
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Logger.w(e.getMessage()+"Unexpected error initializing camera");
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        // camera error
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setMessage(getString(R.string.unable_to_open_camera));
        dialog.setTitle(getString(R.string.error));
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
    }

    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * Init the interception rectangle area
     * 调整扫描采样区域
     */
    private void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;

        // Obtain the location information of the scanning frame in layout
        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1] - getStatusBarHeight();

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        // Obtain the height and width of layout container.
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        // Compute the coordinate of the top-left vertex x
        // of the final interception rectangle.
        int x = cropLeft * cameraWidth / containerWidth;
        // Compute the coordinate of the top-left vertex y
        // of the final interception rectangle.
        int y = cropTop * cameraHeight / containerHeight;

        // Compute the width of the final interception rectangle.
        int width = cropWidth * cameraWidth / containerWidth;
        // Compute the height of the final interception rectangle.
        int height = cropHeight * cameraHeight / containerHeight;

        // Generate the final interception rectangle.
        mCropRect = new Rect(x, y, width + x, height + y);
    }

    private int getStatusBarHeight() {
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            return getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 为了解耦，把三个耦合的类复制到内部来
     */

    /**
     * This thread does all the heavy lifting of decoding the images.
     *
     * @author dswitkin@google.com (Daniel Switkin)
     */
    public class DecodeThread extends Thread {

        public static final String BARCODE_BITMAP = "barcode_bitmap";

        public static final int BARCODE_MODE = 0X100;
        public static final int QRCODE_MODE = 0X200;
        public static final int ALL_MODE = 0X300;

        private final Map<DecodeHintType, Object> hints;
        private Handler handler;
        private final CountDownLatch handlerInitLatch;

        public DecodeThread(int decodeMode) {

            handlerInitLatch = new CountDownLatch(1);

            hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

            Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
            //        decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
            //        decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));

            switch (decodeMode) {
                case BARCODE_MODE:
                    decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                    break;

                case QRCODE_MODE:
                    decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                    break;

                case ALL_MODE:
                    decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                    decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                    break;

                default:
                    break;
            }

            hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        }


        public Handler getHandler() {
            try {
                handlerInitLatch.await();
            } catch (InterruptedException ie) {
                // continue?
            }
            return handler;
        }

        @Override
        public void run() {
            Looper.prepare();
            handler = new DecodeHandler(hints);
            handlerInitLatch.countDown();
            Looper.loop();
        }

    }

    public class DecodeHandler extends Handler {

        private final MultiFormatReader multiFormatReader;
        private boolean running = true;

        public DecodeHandler( Map<DecodeHintType, Object> hints) {
            multiFormatReader = new MultiFormatReader();
            multiFormatReader.setHints(hints);
        }

        @Override
        public void handleMessage(Message message) {
            if (!running) {
                return;
            }
            if (message.what == R.id.decode) {
                decode((byte[]) message.obj, message.arg1, message.arg2);
            } else if (message.what == R.id.quit) {
                Looper.myLooper().quit();
            }
        }

        /**
         * Decode the data within the viewfinder rectangle, and time how long it
         * took. For efficiency, reuse the same reader objects from one decode to
         * the next.
         *
         * @param data The YUV preview frame.
         * @param width The width of the preview frame.
         * @param height The height of the preview frame.
         */
        private void decode(byte[] data, int width, int height) {
            Camera.Size size = getCameraManager().getPreviewSize();

            // 这里需要将获取的data翻转一下，因为相机默认拿的的横屏的数据
            byte[] rotatedData = new byte[data.length];
            for (int y = 0; y < size.height; y++) {
                for (int x = 0; x < size.width; x++) {
                    rotatedData[x * size.height + size.height - y - 1] = data[x + y * size.width];
                }
            }

            // 宽高也要调整
            int tmp = size.width;
            size.width = size.height;
            size.height = tmp;

            Result rawResult = null;
            PlanarYUVLuminanceSource source = buildLuminanceSource(rotatedData, size.width, size.height);
            if (source != null) {
                //you can use HybridBinarizer or GlobalHistogramBinarizer
                //but in most of situations HybridBinarizer is shit
                //优化
                BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
                //			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                try {
                    rawResult = multiFormatReader.decodeWithState(bitmap);
                } catch (ReaderException re) {
                    // continue
                } finally {
                    multiFormatReader.reset();
                }
            }

            Handler handler = getHandler();
            if (rawResult != null) {
                // Don't log the barcode contents for security.
                if (handler != null) {
                    Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
                    Bundle bundle = new Bundle();
                    bundleThumbnail(source, bundle);
                    message.setData(bundle);
                    message.sendToTarget();
                }
            } else {
                if (handler != null) {
                    Message message = Message.obtain(handler, R.id.decode_failed);
                    message.sendToTarget();
                }
            }

        }

        private void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
            int[] pixels = source.renderThumbnail();
            int width = source.getThumbnailWidth();
            int height = source.getThumbnailHeight();
            Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
            bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
        }

        /**
         * A factory method to build the appropriate LuminanceSource object based on
         * the format of the preview buffers, as described by Camera.Parameters.
         *
         * @param data A preview frame.
         * @param width The width of the image.
         * @param height The height of the image.
         * @return A PlanarYUVLuminanceSource instance.
         */
        public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
            Rect rect = getCropRect();
            if (rect == null) {
                return null;
            }
            // Go ahead and assume it's YUV rather than die.
            return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
            // 直接返回整幅图像的数据，而不计算聚焦框大小。
            //return new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
        }

    }

    private enum State {
        PREVIEW, SUCCESS, DONE
    }

    /**
     * This class handles all the messaging which comprises the state machine for
     * capture.
     *
     * @author dswitkin@google.com (Daniel Switkin)
     */
    public class CaptureActivityHandler extends Handler {

        private final DecodeThread decodeThread;
        private final CameraManager cameraManager;
        private State state;

        public CaptureActivityHandler(CameraManager cameraManager, int decodeMode) {
            decodeThread = new DecodeThread(decodeMode);
            decodeThread.start();
            state = State.SUCCESS;

            // Start ourselves capturing previews and decoding.
            this.cameraManager = cameraManager;
            cameraManager.startPreview();
            restartPreviewAndDecode();
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == R.id.restart_preview) {
                restartPreviewAndDecode();

            } else if (message.what == R.id.decode_succeeded) {
                state = State.SUCCESS;
                Bundle bundle = message.getData();
                handleDecode((Result) message.obj, bundle);

            } else if (message.what == R.id.decode_failed) {// We're decoding as fast as possible, so when one decode fails,
                // start another.
                state = State.PREVIEW;
                cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);

            } else if (message.what == R.id.return_scan_result) {
                setResult(Activity.RESULT_OK, (Intent) message.obj);
                finish();

            }
        }

        public void quitSynchronously() {
            state = State.DONE;
            cameraManager.stopPreview();
            Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
            quit.sendToTarget();
            try {
                // Wait at most half a second; should be enough time, and onPause()
                // will timeout quickly
                decodeThread.join(500L);
            } catch (InterruptedException e) {
                // continue
            }

            // Be absolutely sure we don't send any queued up messages
            removeMessages(R.id.decode_succeeded);
            removeMessages(R.id.decode_failed);
        }

        public void restartPreviewAndDecode() {
            if (state == State.SUCCESS) {
                state = State.PREVIEW;
                cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
            }
        }

    }


}
