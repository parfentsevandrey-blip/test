package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Экран сканера QR-кода ({@link ScanActivity}) на устройстве или в эмуляторе: камера открывается и превью идёт, отмена и «Назад»
 * закрывают экран с {@code RESULT_CANCELED}, камера отпускается, поворот экрана её не перезапускает, прочитанное приглашение
 * закрывает экран с {@code RESULT_OK}, чужой код только сообщается; без разрешения вместо превью — объяснение с кнопкой
 * настроек. Кадр с настоящим QR-кодом эмулятору не показать, поэтому «прочитанное камерой» подставляется вызовом
 * {@code ScanActivity.onDecoded}; распознавание кадров проверено отдельно (модульные тесты {@code QrDecoder}, {@code QrAnalyzer}).
 *
 * <p>Ответ экрана читается у самого экрана ({@code deliveredCode}, {@code deliveredData}), а не через
 * {@code launchActivityForResult().getResult()}: на эмуляторе в CI тот ждал ответ около 45 секунд после каждого закрытия (таймаут
 * {@code ActivityScenario}), и прогон из секунд превращался в четверть часа. То, что окно получает ответ экрана, проверяет
 * {@code SmokeTest} (настоящее окно открывает настоящий сканер).
 *
 * <p>Порядок тестов важен: разрешение на камеру, выданное один раз, из процесса приложения не отозвать (система убивает
 * процесс, а в нём идёт тест: проверено, «Process crashed»), поэтому первым идёт тест без разрешения, а остальные выдают его
 * себе сами. Если разрешение уже выдано (повторный прогон на том же устройстве, другой тест), первый тест честно пропускается.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ScanActivityTest {
    private static final String PACKAGE = "app.themesh.mobile";
    private static final String TAG = "themesh-test";
    /** Приглашение настоящей длины (250 знаков), без дефисов. */
    private static final String INVITE = TestInvite.CODE;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final UiAutomation automation = instrumentation.getUiAutomation();
    /** Сколько ждать камеру и превью; на медленном эмуляторе можно увеличить: -e waitSeconds 240. */
    private final long waitMs = Long.parseLong(InstrumentationRegistry.getArguments().getString("waitSeconds", "60")) * 1000;

    // ---- без разрешения ---------------------------------------------------------------------

    @Test
    public void test1_withoutThePermissionTheScreenExplainsAndOffersTheSettings() throws Exception {
        assumeTrue("у устройства нет камеры (так и должно закрываться: см. последний тест)", hasCamera());
        assumeFalse("разрешение на камеру уже выдано (другим тестом или человеком), а отозвать его из процесса приложения нельзя: "
                + "система убивает процесс, в котором идёт тест", hasPermission());
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity scanner = activityOf(scenario);
            // система спрашивает, человек (тест) отвечает «Не разрешать» — и вместо превью появляется объяснение
            long end = SystemClock.elapsedRealtime() + waitMs;
            String screen = "";
            boolean clicked = false;
            while (SystemClock.elapsedRealtime() < end && !on(scenario, a -> a.findViewById(R.id.scan_denied).getVisibility() == View.VISIBLE)) {
                if (!clicked) {
                    PermissionDialog.Result r = PermissionDialog.deny(automation);
                    clicked = r.clicked;
                    screen = r.screen;
                }
                Thread.sleep(300);
            }
            assertTrue("объяснение про разрешение не появилось; на экране: " + screen,
                    on(scenario, a -> a.findViewById(R.id.scan_denied).getVisibility() == View.VISIBLE));
            scenario.onActivity(a -> {
                assertEquals("вместо превью — объяснение", View.GONE, a.findViewById(R.id.scan_preview).getVisibility());
                assertEquals(View.GONE, a.findViewById(R.id.scan_bottom).getVisibility());
                View settings = a.findViewById(R.id.scan_settings);
                assertEquals(View.VISIBLE, settings.getVisibility());
                assertTrue(settings.isEnabled() && settings.isClickable());
                assertEquals(a.getString(R.string.scan_open_settings), ((TextView) settings).getText().toString());
                assertEquals(a.getString(R.string.scan_denied_title), ((TextView) a.findViewById(R.id.scan_denied_title)).getText().toString());
                assertEquals(View.VISIBLE, a.findViewById(R.id.scan_denied_cancel).getVisibility());
            });
            assertFalse("разрешения по-прежнему нет", hasPermission());
            scenario.onActivity(a -> a.findViewById(R.id.scan_denied_cancel).performClick());
            Answer answer = awaitAnswer(scanner);
            assertEquals(Activity.RESULT_CANCELED, answer.code);
            assertEquals("странице сказано, что камера запрещена", "denied", answer.data.getStringExtra(ScanActivity.EXTRA_ERROR));
            assertNull(answer.data.getStringExtra(ScanActivity.EXTRA_INVITE));
        }
    }

    // ---- камера -----------------------------------------------------------------------------

    @Test
    public void test2_thePreviewStreamsAndCancelClosesTheScreen() throws Exception {
        assumeCameraWorks();
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            waitForStreaming(scenario, screen);
            // кадры доходят и до распознавания (а не только до превью): плоскость яркости годится, строки и размеры верны
            waitFor("кадры камеры не дошли до распознавания", waitMs, () -> on(scenario, a -> a.framesAnalysed()) > 0);
            scenario.onActivity(a -> {
                assertEquals("рамка и подсказка на месте", View.VISIBLE, a.findViewById(R.id.scan_frame).getVisibility());
                assertEquals(View.VISIBLE, a.findViewById(R.id.scan_bottom).getVisibility());
                assertEquals("объяснения про разрешение нет", View.GONE, a.findViewById(R.id.scan_denied).getVisibility());
                assertEquals(a.getString(R.string.scan_hint), ((TextView) a.findViewById(R.id.scan_hint)).getText().toString());
                String privacy = ((TextView) a.findViewById(R.id.scan_privacy)).getText().toString();
                assertEquals(a.getString(R.string.scan_privacy), privacy);
                assertFalse(privacy.isEmpty());
            });
            // нажатие на превью (фокус) ничего не ломает, камера продолжает идти
            scenario.onActivity(a -> {
                PreviewView preview = a.findViewById(R.id.scan_preview);
                long t = SystemClock.uptimeMillis();
                float x = preview.getWidth() / 2f;
                float y = preview.getHeight() / 2f;
                preview.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
                preview.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0));
            });
            Thread.sleep(1000);
            assertEquals(PreviewView.StreamState.STREAMING, streamState(scenario));

            scenario.onActivity(a -> a.findViewById(R.id.scan_cancel).performClick());
            Answer answer = awaitAnswer(screen);
            assertEquals(Activity.RESULT_CANCELED, answer.code);
            assertNull("человек просто вышел: без приглашения", answer.data.getStringExtra(ScanActivity.EXTRA_INVITE));
            assertNull("и без ошибки", answer.data.getStringExtra(ScanActivity.EXTRA_ERROR));
        }
    }

    @Test
    public void test3_backClosesTheScreenToo() throws Exception {
        assumeCameraWorks();
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            waitForStreaming(scenario, screen);
            scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
            Answer answer = awaitAnswer(screen);
            assertEquals(Activity.RESULT_CANCELED, answer.code);
            assertNull(answer.data.getStringExtra(ScanActivity.EXTRA_ERROR));
        }
    }

    @Test
    public void test4_theCameraIsReleasedWhenTheScreenCloses() throws Exception {
        assumeCameraWorks();
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            waitForStreaming(scenario, screen);
            String during = cameraClients();
            assumeTrue("в выводе dumpsys media.camera нет нашего приложения среди тех, кто держит камеру, пока она работает; "
                    + "проверять отпускание не с чем:\n" + during, during.contains(PACKAGE));
            scenario.onActivity(a -> a.findViewById(R.id.scan_cancel).performClick());
            awaitAnswer(screen);
        }
        long end = SystemClock.elapsedRealtime() + 15_000;
        String after = cameraClients();
        while (after.contains(PACKAGE) && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(500);
            after = cameraClients();
        }
        assertFalse("камера осталась занятой после закрытия экрана:\n" + after, after.contains(PACKAGE));
    }

    @Test
    public void test5_turningThePhoneKeepsTheSameScreenAndTheCamera() throws Exception {
        assumeCameraWorks();
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            waitForStreaming(scenario, screen);
            AtomicReference<Activity> before = new AtomicReference<>();
            scenario.onActivity(before::set);
            int was = on(scenario, a -> a.getResources().getConfiguration().orientation);
            int target = was == Configuration.ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            try {
                scenario.onActivity(a -> a.setRequestedOrientation(target));
                long end = SystemClock.elapsedRealtime() + 15_000;
                while (SystemClock.elapsedRealtime() < end && on(scenario, a -> a.getResources().getConfiguration().orientation) == was) {
                    Thread.sleep(300);
                }
                assumeTrue("устройство не повернулось (повороты выключены?)", on(scenario, a -> a.getResources().getConfiguration().orientation) != was);
                Thread.sleep(1500);
                AtomicReference<Activity> after = new AtomicReference<>();
                scenario.onActivity(after::set);
                assertSame("поворот не пересоздаёт экран (configChanges)", before.get(), after.get());
                assertEquals(Lifecycle.State.RESUMED, scenario.getState());
                assertEquals("и камера продолжает идти", PreviewView.StreamState.STREAMING, streamState(scenario));
            } finally {
                scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
            }
            scenario.onActivity(a -> a.findViewById(R.id.scan_cancel).performClick());
            awaitAnswer(screen);
        }
    }

    // ---- прочитанное ------------------------------------------------------------------------

    @Test
    public void test6_aReadInviteClosesTheScreenWithIt() throws Exception {
        assumeTrue("у устройства нет камеры", hasCamera());
        grant();
        for (String shown : new String[] {INVITE, "themesh://join?code=" + INVITE.toLowerCase(), TestInvite.grouped(INVITE)}) {
            try (ActivityScenario<ScanActivity> scenario = open()) {
                ScanActivity screen = activityOf(scenario);
                scenario.onActivity(a -> a.onDecoded(shown));
                Answer answer = awaitAnswer(screen);
                assertEquals(shown, Activity.RESULT_OK, answer.code);
                assertEquals("приглашение отдано в обычном виде", INVITE, answer.data.getStringExtra(ScanActivity.EXTRA_INVITE));
                assertNull(answer.data.getStringExtra(ScanActivity.EXTRA_ERROR));
            }
        }
    }

    @Test
    public void test7_aForeignCodeIsAnnouncedAndScanningGoesOn() throws Exception {
        assumeTrue("у устройства нет камеры", hasCamera());
        grant();
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            scenario.onActivity(a -> a.onDecoded("https://example.com/not-the-mesh"));
            waitFor("«Это QR-код не от The Mesh» не появилось", 10_000,
                    () -> on(scenario, a -> a.findViewById(R.id.scan_notice).getVisibility() == View.VISIBLE));
            scenario.onActivity(a -> assertEquals(a.getString(R.string.scan_not_ours), ((TextView) a.findViewById(R.id.scan_notice)).getText().toString()));
            assertEquals("экран не закрылся", Lifecycle.State.RESUMED, scenario.getState());
            // сообщение пропадает само, и экран по-прежнему принимает приглашение
            waitFor("сообщение не пропало само", 10_000, () -> on(scenario, a -> a.findViewById(R.id.scan_notice).getVisibility() != View.VISIBLE));
            scenario.onActivity(a -> a.onDecoded(INVITE));
            Answer answer = awaitAnswer(screen);
            assertEquals(Activity.RESULT_OK, answer.code);
            assertEquals(INVITE, answer.data.getStringExtra(ScanActivity.EXTRA_INVITE));
        }
    }

    @Test
    public void test8_theResultIsDeliveredOnceAndTheFirstOneWins() throws Exception {
        assumeTrue("у устройства нет камеры", hasCamera());
        grant();
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            scenario.onActivity(a -> {
                a.findViewById(R.id.scan_cancel).performClick(); // человек успел нажать «Отмена»
                a.onDecoded(INVITE); // а камера прочитала код в тот же миг
                a.onDecoded(INVITE);
                a.findViewById(R.id.scan_cancel).performClick();
            });
            Answer answer = awaitAnswer(screen);
            assertEquals("первым был «Отмена»", Activity.RESULT_CANCELED, answer.code);
            assertNull(answer.data.getStringExtra(ScanActivity.EXTRA_INVITE));
        }
        try (ActivityScenario<ScanActivity> scenario = open()) {
            ScanActivity screen = activityOf(scenario);
            scenario.onActivity(a -> {
                a.onDecoded(INVITE); // приглашение первым, потом ещё раз то же, потом «Отмена»
                a.onDecoded(INVITE);
            });
            Answer answer = awaitAnswer(screen);
            assertEquals(Activity.RESULT_OK, answer.code);
            assertEquals(INVITE, answer.data.getStringExtra(ScanActivity.EXTRA_INVITE));
        }
    }

    // ---- без камеры -------------------------------------------------------------------------

    @Test
    public void test9_aPhoneWithoutACameraClosesAtOnceAsUnavailable() throws Exception {
        assumeFalse("у устройства есть камера: экран работает по-настоящему (тесты выше)", hasCamera());
        // Здесь экран закрывается ещё в onCreate, ActivityScenario.launch его уже не застаёт, поэтому ответ берётся у системы.
        try (ActivityScenario<ScanActivity> scenario = ActivityScenario.launchActivityForResult(ScanActivity.class)) {
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
            assertEquals("unavailable", result.getResultData().getStringExtra(ScanActivity.EXTRA_ERROR));
        }
    }

    // ---- общее ------------------------------------------------------------------------------

    private boolean hasCamera() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void grant() {
        automation.grantRuntimePermission(PACKAGE, Manifest.permission.CAMERA);
    }

    /** Камера есть и сама по себе (без нас) открывается: иначе дело в эмуляторе (нет веб-камеры, нет образа камеры), а не в экране. */
    private void assumeCameraWorks() throws Exception {
        assumeTrue("у устройства нет камеры", hasCamera());
        grant();
        assumeTrue("камера не открывается и без нашего экрана (в эмуляторе нет камеры?)", CameraProbe.opens(context));
    }

    /**
     * Экран сканера, запущенный как есть (не через {@code launchActivityForResult}: см. описание класса). Ответ экрана читается
     * у самого экрана, {@link #awaitAnswer}.
     */
    private ActivityScenario<ScanActivity> open() {
        return ActivityScenario.launch(ScanActivity.class);
    }

    /** Сам экран: после закрытия у {@code ActivityScenario} его уже не спросить, а ответ экрана лежит в нём. */
    private ScanActivity activityOf(ActivityScenario<ScanActivity> scenario) {
        return on(scenario, a -> a);
    }

    /** Ответ экрана окну: код и данные. */
    private static final class Answer {
        final int code;
        final Intent data;

        Answer(int code, Intent data) {
            this.code = code;
            this.data = data;
        }
    }

    /** Ждёт, пока экран отдаст ответ и закроется (камера при этом отпускается), и возвращает ответ. */
    private Answer awaitAnswer(ScanActivity screen) throws Exception {
        long begin = SystemClock.elapsedRealtime();
        waitFor("экран не отдал ответ", Math.min(waitMs, 60_000), () -> screen.deliveredData() != null);
        long answered = SystemClock.elapsedRealtime();
        waitFor("экран не закрылся после ответа", Math.min(waitMs, 60_000), screen::isDestroyed);
        Log.i(TAG, "the answer after " + (answered - begin) + " ms, the screen closed after " + (SystemClock.elapsedRealtime() - begin) + " ms");
        Intent data = screen.deliveredData();
        assertNotNull("ответ отдан", data);
        return new Answer(screen.deliveredCode(), data);
    }

    private <T> T on(ActivityScenario<ScanActivity> scenario, Function<ScanActivity, T> read) {
        AtomicReference<T> out = new AtomicReference<>();
        scenario.onActivity(a -> out.set(read.apply(a)));
        return out.get();
    }

    private PreviewView.StreamState streamState(ActivityScenario<ScanActivity> scenario) {
        return on(scenario, a -> ((PreviewView) a.findViewById(R.id.scan_preview)).getPreviewStreamState().getValue());
    }

    private interface Check {
        boolean ok() throws Exception;
    }

    private void waitFor(String message, long timeoutMs, Check check) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(200);
        }
        fail(message + " за " + timeoutMs / 1000 + " с");
    }

    /** Ждёт, пока превью камеры не пойдёт ({@code STREAMING}); если экран закрылся сам, объясняет почему. */
    private void waitForStreaming(ActivityScenario<ScanActivity> scenario, ScanActivity screen) throws Exception {
        long end = SystemClock.elapsedRealtime() + waitMs;
        String last = "ещё не спрашивали";
        while (SystemClock.elapsedRealtime() < end) {
            if (screen.isDestroyed()) {
                Intent data = screen.deliveredData();
                fail("экран закрылся сам, не дождавшись превью: код " + screen.deliveredCode() + ", ошибка «"
                        + (data == null ? "ответа не было" : data.getStringExtra(ScanActivity.EXTRA_ERROR)) + "», хотя камера сама по себе открывается");
            }
            try {
                PreviewView.StreamState state = streamState(scenario);
                last = String.valueOf(state);
                if (state == PreviewView.StreamState.STREAMING) {
                    return;
                }
            } catch (RuntimeException e) {
                last = e.toString(); // экран как раз закрывается: следующий круг объяснит
            }
            Thread.sleep(250);
        }
        fail("превью камеры не пошло за " + waitMs / 1000 + " с (состояние: " + last + ")");
    }

    /** Кто сейчас держит камеру, по {@code dumpsys media.camera}: раздел «Active Camera Clients»; пусто, если такого раздела нет. */
    private String cameraClients() throws Exception {
        ParcelFileDescriptor out = automation.executeShellCommand("dumpsys media.camera");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(out)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, n);
            }
        }
        String dump = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        int from = dump.indexOf("Active Camera Clients");
        if (from < 0) {
            return "";
        }
        int to = dump.indexOf("Allowed user IDs", from);
        return dump.substring(from, to > from ? to : Math.min(dump.length(), from + 2000));
    }
}
