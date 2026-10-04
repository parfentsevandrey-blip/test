package app.themesh.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Открывается ли камера устройства сама по себе, без нашего экрана. В эмуляторе камера бывает «есть» (FEATURE_CAMERA_ANY), но не
 * открывается (нет веб-камеры, нет образа камеры): тогда проверять сканер на настоящей камере не на чем, и тест пропускается, а не
 * падает. Нужно разрешение CAMERA.
 */
final class CameraProbe {
    private CameraProbe() {
    }

    @SuppressLint("MissingPermission")
    static boolean opens(Context context) throws Exception {
        CameraManager manager = context.getSystemService(CameraManager.class);
        String[] ids = manager.getCameraIdList();
        if (ids.length == 0) {
            return false;
        }
        String id = ids[0];
        for (String candidate : ids) {
            Integer facing = manager.getCameraCharacteristics(candidate).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                id = candidate;
                break;
            }
        }
        HandlerThread thread = new HandlerThread("camera-probe");
        thread.start();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<CameraDevice> device = new AtomicReference<>();
        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    device.set(camera);
                    done.countDown();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    done.countDown();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    done.countDown();
                }
            }, new Handler(thread.getLooper()));
            done.await(30, TimeUnit.SECONDS);
        } catch (RuntimeException | CameraAccessException e) {
            return false;
        } finally {
            CameraDevice opened = device.get();
            if (opened != null) {
                opened.close();
                Thread.sleep(1500); // камера отпускается не мгновенно
            }
            thread.quitSafely();
        }
        return device.get() != null;
    }
}
