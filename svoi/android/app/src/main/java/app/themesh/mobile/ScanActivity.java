package app.themesh.mobile;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import app.themesh.mobile.core.ScanEvent;
import app.themesh.mobile.core.ScanFilter;

/**
 * Сканер QR-кода приглашения: камера на весь экран, QR распознаётся тут же, на телефоне ({@code QrDecoder}, ZXing), — кадры
 * не сохраняются и никуда не передаются. Открывается из окна ({@code MainActivity}) по просьбе страницы интерфейса
 * ({@link ScanBridge}) и закрывается, как только прочитано приглашение ({@code RESULT_OK}, {@link #EXTRA_INVITE}) или
 * человек вышел (кнопка «Отмена», «Назад» — {@code RESULT_CANCELED}). Нет разрешения на камеру — вместо превью объяснение с
 * кнопкой «Открыть настройки»; камеры нет или она не открылась — экран сразу закрывается. Причина ({@link #EXTRA_ERROR}):
 * {@link ScanEvent#DENIED} или {@link ScanEvent#UNAVAILABLE}, без неё — человек просто вышел.
 *
 * <p>Другой QR-код (ссылка, чужое приглашение) экран не закрывает: «Это QR-код не от The Mesh», не чаще раза в две секунды,
 * сканирование идёт дальше ({@link ScanFilter}). Камера отпускается в {@code onStop} и {@code onDestroy}.
 */
public class ScanActivity extends ComponentActivity {
    /** Приглашение в обычном виде («MESH1-…» заглавными, без дефисов и пробелов); в ответе с {@code RESULT_OK}. */
    static final String EXTRA_INVITE = "app.themesh.mobile.extra.SCAN_INVITE";
    /** {@link ScanEvent#DENIED} или {@link ScanEvent#UNAVAILABLE}; в ответе с {@code RESULT_CANCELED}, если дело не в человеке. */
    static final String EXTRA_ERROR = "app.themesh.mobile.extra.SCAN_ERROR";

    private static final String TAG = "themesh";
    private static final String STATE_ASKED = "asked";
    /** Сколько висит «Это QR-код не от The Mesh». */
    private static final long NOTICE_MS = 2_500;
    /** Кадр для анализа: плотный QR с экрана ноутбука читается, только если пикселей на модуль достаточно. */
    private static final Size ANALYSIS_SIZE = new Size(1920, 1080);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean delivered = new AtomicBoolean();
    private final ScanFilter filter = new ScanFilter();
    private final Runnable hideNotice = this::hideNotice;
    private final Observer<CameraState> cameraStateObserver = this::onCameraState;
    private final QrAnalyzer analyzer = new QrAnalyzer(new QrAnalyzer.Listener() {
        private boolean logged;

        @Override
        public void onText(String text) {
            onDecoded(text);
        }

        @Override
        public void onFailure(RuntimeException e) {
            if (!logged) { // кадров много: одной записи хватит
                logged = true;
                Log.w(TAG, "a camera frame could not be analysed: " + e);
            }
        }
    });

    private ExecutorService analysisExecutor;
    private ActivityResultLauncher<String> permissionLauncher;
    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private Camera camera;
    private LiveData<CameraState> cameraState;
    private boolean asked; // разрешение уже спрашивали (запоминается, чтобы после пересоздания окна не спрашивать снова)
    private boolean requesting; // системный запрос разрешения на экране
    private boolean denied; // разрешения нет: вместо превью — объяснение
    private volatile int deliveredCode = RESULT_CANCELED; // что отдано окну: см. deliveredData()
    private volatile Intent deliveredData;

    private PreviewView preview;
    private View frame;
    private View cancel;
    private View bottom;
    private View deniedPanel;
    private TextView notice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT), // фон камеры чёрный: значки панелей светлые
                SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        SystemBars.fit(findViewById(R.id.scan_overlay)); // превью — под панелями, а кнопки и подсказка — в безопасной области
        preview = findViewById(R.id.scan_preview);
        frame = findViewById(R.id.scan_frame);
        cancel = findViewById(R.id.scan_cancel);
        bottom = findViewById(R.id.scan_bottom);
        deniedPanel = findViewById(R.id.scan_denied);
        notice = findViewById(R.id.scan_notice);
        asked = savedInstanceState != null && savedInstanceState.getBoolean(STATE_ASKED);

        cancel.setOnClickListener(v -> cancel());
        findViewById(R.id.scan_denied_cancel).setOnClickListener(v -> cancel());
        findViewById(R.id.scan_settings).setOnClickListener(v -> openSettings());
        preview.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
                v.performClick();
            }
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancel();
            }
        });
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            requesting = false;
            if (granted) {
                showScanner();
                startCamera();
            } else {
                showDenied();
            }
        });
        analysisExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "themesh-scan"));

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            fail(ScanEvent.UNAVAILABLE); // телефон без камеры; страница такого не просит, но Intent мог прийти откуда угодно
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_ASKED, asked);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (delivered.get() || requesting) {
            return;
        }
        if (hasPermission()) {
            showScanner(); // в том числе после возвращения из настроек, где человек разрешил камеру
            startCamera();
        } else if (!asked) {
            asked = true;
            requesting = true;
            permissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            showDenied();
        }
    }

    @Override
    protected void onStop() {
        stopCamera(); // камера не должна оставаться занятой, пока экрана не видно
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopCamera();
        analyzer.stop();
        main.removeCallbacksAndMessages(null);
        analysisExecutor.shutdown();
        super.onDestroy();
    }

    // ---- результат --------------------------------------------------------------------------

    /** Отдаёт результат окну и закрывается — ровно один раз, что бы ни пришло первым: приглашение, «Отмена» или ошибка. */
    private void deliver(int resultCode, String error, String invite) {
        if (!delivered.compareAndSet(false, true)) {
            return;
        }
        analyzer.stop();
        Intent data = new Intent();
        if (invite != null) {
            data.putExtra(EXTRA_INVITE, invite);
        }
        if (error != null) {
            data.putExtra(EXTRA_ERROR, error);
        }
        deliveredCode = resultCode;
        deliveredData = data;
        setResult(resultCode, data);
        finish();
    }

    /**
     * Ответ, отданный окну ({@code null}, пока ничего не отдано), и его код. Доступно пакету — для тестов на устройстве: через
     * {@code ActivityScenario.launchActivityForResult().getResult()} на эмуляторе в CI каждый ответ ждался около 45 секунд
     * (таймаут самого {@code ActivityScenario}), а здесь он виден сразу. Что окно получает этот ответ, проверяет {@code SmokeTest}.
     */
    Intent deliveredData() {
        return deliveredData;
    }

    int deliveredCode() {
        return deliveredCode;
    }

    /** Человек вышел. Если он вышел из объяснения про разрешение — это «denied»: странице есть что сказать. */
    private void cancel() {
        deliver(RESULT_CANCELED, denied ? ScanEvent.DENIED : null, null);
    }

    private void fail(String error) {
        deliver(RESULT_CANCELED, error, null);
    }

    // ---- распознанное -----------------------------------------------------------------------

    /**
     * Из потока анализа кадров: что делать с прочитанным текстом, решает {@link ScanFilter}. Доступно пакету: тесты на устройстве
     * подставляют сюда текст так, как будто его прочитала камера (кадр с настоящим QR-кодом эмулятору не показать).
     */
    void onDecoded(String text) {
        ScanFilter.Verdict verdict = filter.accept(text, SystemClock.elapsedRealtime());
        switch (verdict.kind) {
            case INVITE:
                main.post(() -> found(verdict.invite));
                break;
            case NOT_OURS:
                main.post(() -> showNotice(R.string.scan_not_ours, true));
                break;
            default:
                break;
        }
    }

    /** Сколько кадров камеры дошло до распознавания; доступно пакету — для тестов на устройстве. */
    int framesAnalysed() {
        return analyzer.frames();
    }

    private void found(String invite) {
        if (delivered.get()) {
            return;
        }
        // Лёгкая отдача, без разрешения VIBRATE; если человек выключил её в системе, её и не будет.
        preview.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        deliver(RESULT_OK, null, invite);
    }

    private void showNotice(int text, boolean temporary) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
        main.removeCallbacks(hideNotice);
        if (temporary) {
            main.postDelayed(hideNotice, NOTICE_MS);
        }
    }

    private void hideNotice() {
        notice.setVisibility(View.GONE);
    }

    // ---- разрешение -------------------------------------------------------------------------

    private boolean hasPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void showDenied() {
        denied = true;
        stopCamera();
        main.removeCallbacks(hideNotice);
        preview.setVisibility(View.GONE);
        frame.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);
        bottom.setVisibility(View.GONE);
        notice.setVisibility(View.GONE);
        deniedPanel.setVisibility(View.VISIBLE);
    }

    private void showScanner() {
        if (!denied) {
            return;
        }
        denied = false;
        deniedPanel.setVisibility(View.GONE);
        preview.setVisibility(View.VISIBLE);
        frame.setVisibility(View.VISIBLE);
        cancel.setVisibility(View.VISIBLE);
        bottom.setVisibility(View.VISIBLE);
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "no settings screen to open: " + e);
        }
    }

    // ---- камера -----------------------------------------------------------------------------

    private void startCamera() {
        if (provider != null) {
            bindCamera();
            return;
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                provider = future.get();
            } catch (ExecutionException | RuntimeException e) {
                Log.w(TAG, "the camera service is not available: " + e);
                fail(ScanEvent.UNAVAILABLE);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(ScanEvent.UNAVAILABLE);
                return;
            }
            bindCamera();
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        ProcessCameraProvider cameras = provider;
        if (cameras == null || camera != null || denied || delivered.get() || isFinishing() || isDestroyed()
                || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            return;
        }
        CameraSelector selector = pickCamera(cameras);
        if (selector == null) {
            fail(ScanEvent.UNAVAILABLE);
            return;
        }
        // Превью и анализ — одного формата кадра (16:9), чтобы то, что видно на экране, и то, что читается, совпадало.
        ResolutionSelector resolution = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(new ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        Preview previewUseCase = new Preview.Builder().setResolutionSelector(resolution).build();
        previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
        ImageAnalysis analysisUseCase = new ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        analysisUseCase.setAnalyzer(analysisExecutor, analyzer);
        try {
            cameras.unbindAll();
            camera = cameras.bindToLifecycle(this, selector, previewUseCase, analysisUseCase);
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot bind the camera: " + e);
            fail(ScanEvent.UNAVAILABLE);
            return;
        }
        analysis = analysisUseCase;
        cameraState = camera.getCameraInfo().getCameraState();
        cameraState.observe(this, cameraStateObserver);
    }

    /** Задняя камера, а если её нет (планшет, Chromebook) — передняя; {@code null} — камер нет совсем. */
    private CameraSelector pickCamera(ProcessCameraProvider cameras) {
        try {
            if (cameras.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                return CameraSelector.DEFAULT_BACK_CAMERA;
            }
            if (cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                return CameraSelector.DEFAULT_FRONT_CAMERA;
            }
        } catch (CameraInfoUnavailableException e) {
            Log.w(TAG, "cannot list the cameras: " + e);
        }
        return null;
    }

    /** Состояние камеры: неисправимая ошибка — выходим; временная (камеру держит другое приложение) — говорим об этом. */
    private void onCameraState(CameraState state) {
        CameraState.StateError error = state.getError();
        if (error != null && error.getType() == CameraState.ErrorType.CRITICAL) {
            Log.w(TAG, "the camera cannot be used, error " + error.getCode());
            fail(ScanEvent.UNAVAILABLE);
        } else if (error != null) {
            showNotice(R.string.scan_camera_busy, false);
        } else if (state.getType() == CameraState.Type.OPEN) {
            hideNotice();
        }
    }

    /** Отпускает камеру: превью и анализ отвязываются, система получает камеру обратно. */
    private void stopCamera() {
        if (cameraState != null) {
            cameraState.removeObserver(cameraStateObserver);
            cameraState = null;
        }
        if (analysis != null) {
            analysis.clearAnalyzer();
            analysis = null;
        }
        camera = null;
        ProcessCameraProvider cameras = provider;
        if (cameras != null) {
            try {
                cameras.unbindAll();
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot release the camera: " + e);
            }
        }
    }

    /** Нажатие на превью — навести резкость и экспозицию на это место. */
    private void focusAt(float x, float y) {
        Camera current = camera;
        if (current == null) {
            return;
        }
        try {
            MeteringPoint point = preview.getMeteringPointFactory().createPoint(x, y);
            current.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).build());
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot focus: " + e); // превью ещё не готово или камера уже закрывается
        }
    }
}
