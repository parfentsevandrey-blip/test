package app.themesh.mobile;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.themesh.mobile.core.AcceptTypes;
import app.themesh.mobile.core.Json;
import app.themesh.mobile.core.NodeApi;
import app.themesh.mobile.core.NodeStatus;
import app.themesh.mobile.core.NodeSupervisor;
import app.themesh.mobile.core.OriginPolicy;
import app.themesh.mobile.core.PageLook;
import app.themesh.mobile.core.Route;
import app.themesh.mobile.core.ScanEvent;
import app.themesh.mobile.core.StatusLine;
import app.themesh.mobile.core.ThemeColor;
import app.themesh.mobile.core.WebViewVersion;

/**
 * Единственное окно приложения: веб-интерфейс узла (тот же, что открывается в браузере), в который окно
 * входит само — одноразовым кодом из {@code POST /api/login/code}. Пока узел не готов, видна заставка
 * («Запускаем The Mesh…»), а если он не запустился — страница ошибки с кнопками «Запустить ещё раз» и
 * «Показать журнал». Внутри окна открывается только адрес узла на 127.0.0.1; ссылки на сайты уходят в браузер.
 * Закрытие окна узел не останавливает: он живёт в {@link NodeService}.
 */
public class MainActivity extends ComponentActivity implements NodeRuntime.Listener {
    /** Куда перейти в интерфейсе («#/chat/<id>»); значение проверяется {@link Route#isValid} перед выполнением. */
    static final String EXTRA_ROUTE = "app.themesh.mobile.extra.ROUTE";

    private static final String TAG = "themesh";
    private static final String STATE_ROUTE = "route";

    /** Цвета двух оформлений интерфейса (css/tokens.css): окно подстраивается под выбранное на странице. */
    private static final class Palette {
        final int bg;
        final int fg;
        final int muted;
        final int accent;
        final int onAccent;
        final int err;

        Palette(int bg, int fg, int muted, int accent, int onAccent, int err) {
            this.bg = bg;
            this.fg = fg;
            this.muted = muted;
            this.accent = accent;
            this.onAccent = onAccent;
            this.err = err;
        }
    }

    private static final Palette LIGHT = new Palette(ThemeColor.LIGHT_BG, 0xFF1C2421, 0xFF46525A, 0xFF0E7A5A, 0xFFFFFFFF, 0xFFB3261E);
    private static final Palette DARK = new Palette(ThemeColor.DARK_BG, 0xFFE8EEEB, 0xFFABB7B2, 0xFF4FE0B0, 0xFF05231A, 0xFFFF8A80);

    /** Когда (elapsedRealtime) падал процесс отрисовки WebView: на весь процесс, окно при этом пересоздаётся. */
    private static final ArrayDeque<Long> RENDERER_CRASHES = new ArrayDeque<>();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "themesh-ui-io"));
    private final ArrayDeque<Long> reloginTimes = new ArrayDeque<>();
    private final Runnable reloginTask = this::relogin;
    private final Runnable themePoll = this::pollTheme;

    private Prefs prefs;
    private AndroidTexts texts;

    private View root;
    private View bar;
    private View splash;
    private View splashOrb;
    private View errorCard;
    private View errorPanel;
    private WebView web;
    private ImageView barLogo;
    private ImageView splashLogo;
    private TextView barTitle;
    private TextView barStatus;
    private TextView splashText;
    private TextView errorTitle;
    private TextView errorMessage;
    private TextView errorHint;
    private Button errorRestart;
    private Button errorLog;
    private ImageButton barMenu;
    private ObjectAnimator pulse;

    private ActivityResultLauncher<Intent> chooserLauncher;
    private ActivityResultLauncher<String> notificationsLauncher;
    private ActivityResultLauncher<String> storageLauncher;
    private ActivityResultLauncher<Intent> scanLauncher;
    private AlertDialog notificationsDialog;
    private ValueCallback<Uri[]> chooserCallback;
    private String[] pendingDownload;
    private boolean scanOpen; // экран сканера QR-кода на экране: второй раз не открываем
    private String pendingScan; // скрипт с событием «themesh-scan», пока окно не вернулось на экран

    private String loadedOrigin = "";
    private boolean loginInFlight;
    private boolean pageReady;
    private boolean mainFrameFailed; // WebView после onReceivedError всё равно зовёт onPageFinished: ошибку им не стираем
    private boolean clearHistory;
    private String pageError;
    private int loadRetries;
    private boolean webDead; // WebView падает снова и снова: окно его больше не пересоздаёт само
    private boolean showingWeb; // сейчас видна страница интерфейса (а не заставка или ошибка)
    private final Runnable retryLoad = this::retryLoad;
    private final Runnable loadWatchdog = this::onLoadTimeout;
    private String currentUrl = "";
    private String pendingRoute;
    private boolean glassLook = true; // у приложения стеклянный вид по умолчанию; обычный — если человек выбрал его в настройках интерфейса
    private Boolean lightPage; // светлая ли тема страницы; null — страница ещё не сказала, берём тему системы
    private int pageBg = ThemeColor.NONE; // фон страницы при обычном виде (при стеклянном страница прозрачна)
    private PageLook look; // вид, под который окно покрашено сейчас
    private AuroraDrawable auroraLight;
    private AuroraDrawable auroraDark;
    private boolean resumed;
    private Dialog menuPopup; // меню «⋮», пока оно открыто

    // ---- жизненный цикл ---------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        texts = new AndroidTexts(this);
        setUpLaunchers();
        setContentView(R.layout.activity_main); // здесь создаётся WebView: на очень медленных устройствах это десятки секунд
        bindViews();
        SystemBars.fit(root);
        setUpWebView();
        // Службу просим запускаться только теперь. Её обратные вызовы выполняются в этом же главном потоке и пока он занят
        // созданием WebView, стоят в очереди, а система ждёт их 20 секунд и потом убивает приложение («executing service»,
        // ANR): так упал дымовой тест на эмуляторе без KVM, где WebView создавался около минуты.
        startNode();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });
        barMenu.setOnClickListener(v -> showMenu());
        errorRestart.setOnClickListener(v -> onRestartClicked());
        findViewById(R.id.error_log).setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));

        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_ROUTE);
            if (Route.isValid(saved)) {
                pendingRoute = saved;
            }
        }
        handleIntent(getIntent());
        applyPalette();
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        // Значок на экране после «Выйти»: окно уже есть, а узел остановлен — запускаем его снова (до onStart, который
        // иначе принял бы «вышли» за просьбу закрыться).
        startNode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        NodeRuntime.get().addListener(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        NodeRuntime.get().setUiForeground(true);
        web.onResume();
        main.removeCallbacks(themePoll);
        main.post(themePoll);
        main.post(this::flushScan); // сканер закрылся: страница узнаёт результат, когда окно снова на экране
    }

    @Override
    protected void onPause() {
        resumed = false;
        NodeRuntime.get().setUiForeground(false);
        dismissMenu();
        main.removeCallbacks(themePoll);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        NodeRuntime.get().removeListener(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String route = currentRoute();
        if (route != null) {
            outState.putString(STATE_ROUTE, route);
        }
    }

    @Override
    protected void onDestroy() {
        dismissMenu();
        pendingScan = null;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (pulse != null) {
            pulse.cancel();
        }
        if (chooserCallback != null) {
            chooserCallback.onReceiveValue(null);
            chooserCallback = null;
        }
        NodeRuntime.get().removeListener(this);
        NodeRuntime.get().setUiForeground(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(null);
        web.setDownloadListener(null);
        if (web.getParent() instanceof ViewGroup) {
            ((ViewGroup) web.getParent()).removeView(web);
        }
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyPalette(); // тема системы могла смениться, а страница ещё не спрошена
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Состояние узла изменилось (в главном потоке). */
    @Override
    public void onNodeChanged() {
        render();
    }

    // ---- вид --------------------------------------------------------------------------------

    private void bindViews() {
        root = findViewById(R.id.root);
        bar = findViewById(R.id.bar);
        splash = findViewById(R.id.splash);
        splashOrb = findViewById(R.id.splash_orb);
        errorCard = findViewById(R.id.error_card);
        errorPanel = findViewById(R.id.error_panel);
        web = findViewById(R.id.web);
        barLogo = findViewById(R.id.bar_logo);
        splashLogo = findViewById(R.id.splash_logo);
        barTitle = findViewById(R.id.bar_title);
        barStatus = findViewById(R.id.bar_status);
        splashText = findViewById(R.id.splash_text);
        errorTitle = findViewById(R.id.error_title);
        errorMessage = findViewById(R.id.error_message);
        errorHint = findViewById(R.id.error_hint);
        errorRestart = findViewById(R.id.error_restart);
        errorLog = findViewById(R.id.error_log);
        barMenu = findViewById(R.id.bar_menu);
    }

    /** Что показывать: интерфейс, заставку или ошибку; зависит от состояния узла и загрузки страницы. */
    private void render() {
        NodeRuntime rt = NodeRuntime.get();
        NodeStatus status = rt.status();
        barStatus.setText(StatusLine.text(texts, status, rt.state()));
        if (status == NodeStatus.STOPPED && rt.quitRequested()) {
            finishAndRemoveTask(); // «Выйти» в уведомлении
            return;
        }

        boolean showWeb = false;
        String problem = null;
        NodeApi api = rt.api();
        if (webDead) {
            problem = getString(R.string.err_webview_crash);
        } else if (api != null) {
            if (pageError != null) {
                problem = getString(R.string.err_page, pageError);
            } else {
                ensureLoaded(api);
                showWeb = pageReady;
            }
        } else {
            if (pageError != null) { // следующий запуск начнёт загрузку заново
                pageError = null;
                pageReady = false;
                loadedOrigin = "";
            }
            if (status == NodeStatus.FAILED || (status == NodeStatus.STARTING && rt.failures() > 0)) {
                problem = failureText(rt.failure());
            } else if (status == NodeStatus.STOPPED) {
                problem = getString(R.string.err_stopped);
            }
        }

        // WebView остаётся видимым всегда: скрытая страница считается фоновой (таймеры и отрисовка замирают, процесс
        // отрисовки получает низкий приоритет). Пока интерфейс не готов, его закрывают заставка или ошибка.
        showingWeb = showWeb;
        bar.setVisibility(showWeb ? View.GONE : View.VISIBLE); // у страницы своя верхняя полоса, меню приложения открывает её кнопка «⋮»
        web.setImportantForAccessibility(showWeb ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        errorPanel.setVisibility(problem != null ? View.VISIBLE : View.GONE);
        splash.setVisibility(!showWeb && problem == null ? View.VISIBLE : View.GONE);
        if (problem != null) {
            errorMessage.setText(problem);
        }
        animateSplash(splash.getVisibility() == View.VISIBLE);
    }

    private void animateSplash(boolean on) {
        if (on) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(splashLogo, View.ALPHA, 0.35f, 1f);
                pulse.setDuration(700);
                pulse.setRepeatCount(ObjectAnimator.INFINITE);
                pulse.setRepeatMode(ObjectAnimator.REVERSE);
            }
            if (!pulse.isStarted()) {
                pulse.start();
            }
        } else if (pulse != null && pulse.isStarted()) {
            pulse.cancel();
            splashLogo.setAlpha(1f);
        }
    }

    private String failureText(NodeSupervisor.Failure f) {
        if (f == null) {
            return getString(R.string.err_stopped);
        }
        switch (f.kind) {
            case BINARY_MISSING:
                return getString(R.string.err_binary_missing, Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
            case NOT_EXECUTABLE:
                return getString(R.string.err_not_executable, f.detail);
            case NO_PORT:
                return getString(R.string.err_no_port);
            case SPAWN_FAILED:
                return getString(R.string.err_spawn_failed, f.detail);
            case EXITED_AT_START:
                return getString(R.string.err_exited_at_start, f.exitCode);
            case NO_ANSWER:
                return getString(R.string.err_no_answer);
            default:
                return getString(R.string.err_crashed, f.exitCode);
        }
    }

    private boolean systemIsDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Красит окно под вид страницы, который она сообщила (или тему системы, пока страница молчит). */
    private void applyLook(PageLook newLook) {
        if (newLook == null || newLook.equals(look)) {
            return;
        }
        look = newLook;
        glassLook = newLook.glass;
        lightPage = newLook.light;
        pageBg = newLook.background;
        applyPalette();
    }

    private AuroraDrawable aurora(boolean light) {
        if (light) {
            if (auroraLight == null) {
                auroraLight = new AuroraDrawable(true);
            }
            return auroraLight;
        }
        if (auroraDark == null) {
            auroraDark = new AuroraDrawable(false);
        }
        return auroraDark;
    }

    private static GradientDrawable pill(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(color);
        return d;
    }

    /**
     * Красит окно: при стеклянном виде за прозрачной страницей лежит «северное сияние», которое рисует окно (и под
     * системными панелями тоже, поэтому панели и страница — одна картинка), при обычном — сплошной цвет страницы.
     * Заставка, ошибка и меню в обоих видах стеклянные.
     */
    private void applyPalette() {
        boolean light = lightPage != null ? lightPage : !systemIsDark();
        Palette p = light ? LIGHT : DARK;
        int bg = glassLook ? (light ? ThemeColor.GLASS_LIGHT_BG : ThemeColor.GLASS_DARK_BG) : (pageBg != ThemeColor.NONE ? pageBg : p.bg);
        root.setBackground(glassLook ? aurora(light) : new ColorDrawable(bg));
        splash.setBackgroundColor(Color.TRANSPARENT);
        errorPanel.setBackgroundColor(Color.TRANSPARENT);
        web.setBackgroundColor(glassLook ? Color.TRANSPARENT : bg);
        float density = getResources().getDisplayMetrics().density;
        splashOrb.setBackground(Glass.panel(this, light, 52, false));
        errorCard.setBackground(Glass.panel(this, light, 28, false));
        barTitle.setTextColor(p.fg);
        barStatus.setTextColor(p.muted);
        barMenu.setColorFilter(p.muted);
        barLogo.setColorFilter(p.accent);
        splashLogo.setColorFilter(p.accent);
        splashText.setTextColor(p.muted);
        errorTitle.setTextColor(p.fg);
        errorMessage.setTextColor(p.err);
        errorHint.setTextColor(p.muted);
        errorRestart.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), pill(p.accent, 28 * density), null));
        errorRestart.setTextColor(p.onAccent);
        errorLog.setBackground(new RippleDrawable(ColorStateList.valueOf((p.accent & 0x00FFFFFF) | 0x33000000), null, pill(Color.WHITE, 28 * density)));
        errorLog.setTextColor(p.accent);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), root);
        bars.setAppearanceLightStatusBars(light);
        bars.setAppearanceLightNavigationBars(light);
    }

    /** Спрашивает у страницы её вид: человек мог переключить тему или вид в самом интерфейсе. */
    private void pollTheme() {
        if (!resumed) {
            return;
        }
        if (pageReady && showingWeb) {
            web.evaluateJavascript(PageLook.SCRIPT, value -> applyLook(PageLook.parse(ThemeColor.unquote(value))));
        }
        main.postDelayed(themePoll, 2000);
    }

    // ---- запуск узла и вход -----------------------------------------------------------------

    /** Запускает службу; на Android 13+ сначала один раз вежливо просит разрешение на уведомления. */
    private void startNode() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.notifAsked()) {
            // До запуска службы: она создаёт каналы уведомлений, и тогда система спросила бы сама, без объяснения.
            if (notificationsDialog != null && notificationsDialog.isShowing()) {
                return;
            }
            notificationsDialog = showGlassDialog(new AlertDialog.Builder(this)
                    .setTitle(R.string.perm_notif_title)
                    .setMessage(R.string.perm_notif_text)
                    .setCancelable(false)
                    .setPositiveButton(R.string.perm_notif_allow, (d, w) -> {
                        prefs.setNotifAsked();
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    })
                    .setNegativeButton(R.string.perm_notif_later, (d, w) -> {
                        prefs.setNotifAsked();
                        NodeService.start(this);
                    }));
            return;
        }
        NodeService.start(this);
    }

    private void onRestartClicked() {
        if (webDead) {
            recreate(); // новое окно — новый WebView
            return;
        }
        loadRetries = 0;
        pageError = null;
        pageReady = false;
        loadedOrigin = "";
        NodeService.restart(this);
        render();
    }

    /** Если интерфейс ещё не загружен (или адрес узла сменился) — входит и загружает его. */
    private void ensureLoaded(NodeApi api) {
        if (api.origin().equals(loadedOrigin) && (pageReady || loginInFlight)) {
            return;
        }
        startLogin(api, true);
    }

    /**
     * Получает у узла одноразовый код входа и загружает {@code <адрес>/?t=<код>}: узел ставит cookie сессии
     * (HttpOnly, 14 дней) и перенаправляет на «/». Мастер-токен в адрес не попадает; адрес с кодом не журналируется.
     */
    private void startLogin(NodeApi api, boolean fresh) {
        loginInFlight = true;
        main.removeCallbacks(loadWatchdog);
        main.postDelayed(loadWatchdog, 30_000);
        io.execute(() -> {
            try {
                String url = api.loginUrl();
                main.post(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    if (fresh) {
                        loadedOrigin = api.origin();
                        pageReady = false;
                        pageError = null;
                        clearHistory = true;
                    } else if (pendingRoute == null) {
                        pendingRoute = currentRoute(); // вход заново ведёт на «/»: вернёмся туда, где человек был
                    }
                    web.loadUrl(url);
                    render();
                });
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "login failed: " + e);
                main.post(() -> {
                    loginInFlight = false;
                    showPageError(String.valueOf(e.getMessage()));
                });
            }
        });
    }

    /** Страница не загрузилась за 30 секунд (ни готова, ни ошибки): не висеть на заставке вечно. */
    private void onLoadTimeout() {
        if (loginInFlight && !pageReady) {
            loginInFlight = false;
            showPageError(getString(R.string.err_page_timeout));
        }
    }

    /** Интерфейс не открылся: показываем ошибку и через несколько секунд пробуем снова (узел мог ещё не успеть). */
    private void showPageError(String message) {
        pageError = message;
        render();
        main.removeCallbacks(retryLoad);
        if (loadRetries < 5) {
            main.postDelayed(retryLoad, 3000);
        }
    }

    private void retryLoad() {
        if (pageError == null || NodeRuntime.get().api() == null) {
            return;
        }
        loadRetries++;
        pageError = null;
        pageReady = false;
        loadedOrigin = "";
        render();
    }

    /** Страница получила 401: сессии нет. Входим заново, но не чаще трёх раз в минуту. */
    private void relogin() {
        NodeApi api = NodeRuntime.get().api();
        if (api == null || loginInFlight || !api.origin().equals(loadedOrigin)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        while (!reloginTimes.isEmpty() && now - reloginTimes.peekFirst() > 60_000) {
            reloginTimes.removeFirst();
        }
        if (reloginTimes.size() >= 3) {
            Log.w(TAG, "too many sign-ins in a minute, giving up");
            return;
        }
        reloginTimes.addLast(now);
        Log.i(TAG, "the session ended, signing in again");
        startLogin(api, false);
    }

    // ---- WebView ----------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled") // страница — собственный интерфейс узла на 127.0.0.1, больше ничего не грузится
    private void setUpWebView() {
        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setGeolocationEnabled(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSafeBrowsingEnabled(false);
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(false); // у интерфейса своя тёмная тема
        }
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                startDownload(url, contentDisposition, mimetype));
        // Приложение называет себя в user agent: так страница узнаёт, что она в окне телефона, и начинает со стеклянного вида
        // (js/boot.js; человек может выбрать обычный в настройках интерфейса).
        s.setUserAgentString(s.getUserAgentString() + " TheMeshAndroid/" + BuildInfo.versionName(this) + " (android; skin=glass)");
        // Объект window.themeshShell: меню приложения и вид страницы для окраски системных панелей.
        web.addJavascriptInterface(new ShellBridge(main::post, () -> web.getUrl(), () -> loadedOrigin, new ShellBridge.Host() {
            @Override
            public void openMenu() {
                showMenu();
            }

            @Override
            public void look(PageLook pageLook) {
                applyLook(pageLook);
            }
        }), ShellBridge.NAME);
        // Сканер QR-кода приглашения: объект window.themeshApp есть в странице, только если у телефона есть камера.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            web.addJavascriptInterface(new ScanBridge(true, main::post, () -> web.getUrl(), () -> loadedOrigin, this::openScanner),
                    ScanBridge.NAME);
        }
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (OriginPolicy.isInternal(loadedOrigin, url)) {
                return false;
            }
            if (OriginPolicy.isWebLink(url)) {
                openInBrowser(request.getUrl());
            } // остальные схемы (mailto:, intent:, file:…) игнорируются
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (OriginPolicy.allowsRequest(loadedOrigin, request.getUrl().toString())) {
                return null;
            }
            // Страница ничего не должна брать из сети, кроме узла: The Mesh не ходит в интернет.
            return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                    Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (OriginPolicy.isInternal(loadedOrigin, url)) {
                currentUrl = url;
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mainFrameFailed = false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!OriginPolicy.isInternal(loadedOrigin, url) || mainFrameFailed) {
                return;
            }
            Log.i(TAG, "the interface page has loaded");
            loginInFlight = false;
            main.removeCallbacks(loadWatchdog);
            pageError = null;
            pageReady = true;
            loadRetries = 0;
            if (clearHistory) {
                clearHistory = false;
                view.clearHistory(); // «Назад» не должно возвращать на страницу входа
            }
            String route = pendingRoute;
            pendingRoute = null;
            if (route != null) {
                main.postDelayed(() -> navigate(route), 300);
            }
            render();
            main.removeCallbacks(themePoll);
            main.post(themePoll);
            warnIfWebViewIsOld();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame() && OriginPolicy.isInternal(loadedOrigin, request.getUrl().toString())) {
                Log.w(TAG, "the interface page failed to load: " + error.getDescription());
                mainFrameFailed = true;
                loginInFlight = false;
                showPageError(String.valueOf(error.getDescription()));
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (errorResponse.getStatusCode() != 401) {
                return;
            }
            Uri u = request.getUrl();
            String path = u.getPath();
            if (OriginPolicy.isInternal(loadedOrigin, u.toString()) && path != null
                    && path.startsWith("/api/") && !path.equals("/api/handshake")) {
                main.removeCallbacks(reloginTask);
                main.postDelayed(reloginTask, 800);
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            // Процесс отрисовки убила система (нехватка памяти) или он упал: окно пересоздаём, а не падаем сами.
            // Если это повторяется (сломанный WebView), крутить окно по кругу не надо: покажем ошибку.
            Log.w(TAG, "the WebView renderer is gone (crashed=" + detail.didCrash() + ")");
            long now = SystemClock.elapsedRealtime();
            boolean loop;
            synchronized (RENDERER_CRASHES) {
                while (!RENDERER_CRASHES.isEmpty() && now - RENDERER_CRASHES.peekFirst() > 120_000) {
                    RENDERER_CRASHES.removeFirst();
                }
                RENDERER_CRASHES.addLast(now);
                loop = RENDERER_CRASHES.size() > 3;
            }
            if (loop) {
                main.post(() -> {
                    webDead = true;
                    render();
                });
            } else {
                main.post(MainActivity.this::recreate);
            }
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (chooserCallback != null) {
                chooserCallback.onReceiveValue(null);
            }
            chooserCallback = callback;
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
            List<String> types = AcceptTypes.mimeTypes(params.getAcceptTypes(),
                    ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext));
            if (types.size() == 1) {
                pick.setType(types.get(0));
            } else {
                pick.setType("*/*");
                if (types.size() > 1) {
                    pick.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                }
            }
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                chooserLauncher.launch(pick);
            } catch (ActivityNotFoundException e) {
                chooserCallback = null;
                callback.onReceiveValue(null);
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny(); // ни камера, ни микрофон интерфейсу не нужны
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }
    }

    private void setUpLaunchers() {
        chooserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            ValueCallback<Uri[]> callback = chooserCallback;
            chooserCallback = null;
            if (callback != null) {
                callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
            }
        });
        notificationsLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                granted -> NodeService.start(this)); // разрешили или нет — узел всё равно запускаем
        storageLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            String[] job = pendingDownload;
            pendingDownload = null;
            if (!granted) {
                Toast.makeText(this, R.string.toast_no_storage_permission, Toast.LENGTH_LONG).show();
            } else if (job != null) {
                Downloads.start(this, job[0], job[1], job[2]);
            } // разрешение выдано без скачивания (включили «Сохранять полученные файлы в «Загрузки»»): больше ничего не нужно
        });
        scanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> onScanResult(result.getResultCode(), result.getData()));
    }

    private void openInBrowser(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    /** Скачивание из интерфейса: только с адреса узла (туда и уйдут cookie окна). */
    private void startDownload(String url, String contentDisposition, String mimeType) {
        if (!OriginPolicy.isInternal(loadedOrigin, url)) {
            Toast.makeText(this, R.string.toast_download_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        if (Downloads.needsLegacyPermission() && !hasStoragePermission()) {
            pendingDownload = new String[] {url, contentDisposition, mimeType};
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        Downloads.start(this, url, contentDisposition, mimeType);
    }

    // ---- сканер QR-кода ---------------------------------------------------------------------

    /** Страница попросила сканер (в главном потоке; что страница — интерфейс узла, уже проверил {@link ScanBridge}). */
    private void openScanner() {
        if (scanOpen || isFinishing() || isDestroyed()) {
            return;
        }
        scanOpen = true;
        try {
            scanLauncher.launch(new Intent(this, ScanActivity.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot open the scanner: " + e);
            scanOpen = false;
            pendingScan = ScanEvent.failed(ScanEvent.UNAVAILABLE);
            flushScan();
        }
    }

    /**
     * Сканер закрылся: приглашение, отмена, нет разрешения или нет камеры. Страница узнаёт это событием «themesh-scan».
     * Доступно пакету: тесты на устройстве вызывают это так, как будто сканер закрылся.
     */
    void onScanResult(int resultCode, Intent data) {
        scanOpen = false;
        String invite = resultCode == RESULT_OK && data != null ? data.getStringExtra(ScanActivity.EXTRA_INVITE) : null;
        pendingScan = invite != null
                ? ScanEvent.found(invite)
                : ScanEvent.failed(data == null ? null : data.getStringExtra(ScanActivity.EXTRA_ERROR));
        main.post(this::flushScan);
    }

    /**
     * Отдаёт странице ждущее событие — когда окно на экране, а в нём интерфейс узла. Если окно за это время пересоздано (страница
     * загружена заново, форма, которая ждала ответа, уже не открыта), событие никому не нужно и пропадает.
     */
    private void flushScan() {
        String script = pendingScan;
        if (script == null || !resumed) {
            return; // окно ещё не вернулось на экран: onResume вызовет это снова
        }
        pendingScan = null;
        if (pageReady && showingWeb && OriginPolicy.isInternal(loadedOrigin, web.getUrl())) {
            web.evaluateJavascript(script, null);
        }
    }

    // ---- переходы ---------------------------------------------------------------------------

    private void onBack() {
        if (pageReady && showingWeb && web.canGoBack()) {
            web.goBack();
        } else {
            moveTaskToBack(true); // не закрываем: узел продолжает работать
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String route = intent.getStringExtra(EXTRA_ROUTE);
        if (route == null) {
            return;
        }
        intent.removeExtra(EXTRA_ROUTE);
        if (!Route.isValid(route)) { // Intent мог прислать кто угодно
            Log.w(TAG, "ignored an invalid route");
            return;
        }
        navigate(route);
    }

    /** Переводит интерфейс на маршрут без перезагрузки: {@code location.hash = "#/chat/…"}. */
    private void navigate(String route) {
        String script = Route.script(route);
        if (script == null) {
            return;
        }
        if (pageReady) {
            web.evaluateJavascript(script, null);
        } else {
            pendingRoute = route;
        }
    }

    /** Маршрут, на котором сейчас интерфейс (для восстановления после пересоздания окна). */
    private String currentRoute() {
        int hash = currentUrl.indexOf('#');
        if (hash < 0) {
            return pendingRoute;
        }
        String route = currentUrl.substring(hash);
        return Route.isValid(route) ? route : null;
    }

    // ---- меню -------------------------------------------------------------------------------

    private void showMenu() {
        // PopupMenu здесь нужен только как готовый Menu с пунктами из XML: сам он не показывается (он обрезал бы длинные пункты)
        PopupMenu source = new PopupMenu(this, barMenu);
        source.getMenuInflater().inflate(R.menu.main, source.getMenu());
        Menu menu = source.getMenu();
        menu.findItem(R.id.menu_autostart).setChecked(prefs.autostart());
        menu.findItem(R.id.menu_battery).setChecked(isIgnoringBatteryOptimizations());
        menu.findItem(R.id.menu_copy_received).setChecked(copiesWillBeMade());
        dismissMenu();
        boolean light = lightPage != null ? lightPage : !systemIsDark();
        Palette p = light ? LIGHT : DARK;
        // под строкой состояния и (если окно показывает страницу, а не заставку) под её верхней панелью, у кнопки «⋮»
        float density = getResources().getDisplayMetrics().density;
        int top = root.getPaddingTop() + (bar.getVisibility() == View.VISIBLE ? bar.getHeight() : (int) (52 * density));
        menuPopup = MenuPopup.show(this, menu, top, light, p.fg, p.accent, this::onMenuItem);
    }

    /** Окно-вопрос в виде стеклянной панели, в цветах страницы. */
    private AlertDialog showGlassDialog(AlertDialog.Builder builder) {
        boolean light = lightPage != null ? lightPage : !systemIsDark();
        Palette p = light ? LIGHT : DARK;
        return GlassDialogs.show(this, builder, light, p.fg, p.muted, p.accent);
    }

    private void dismissMenu() {
        if (menuPopup != null) {
            menuPopup.dismiss();
            menuPopup = null;
        }
    }

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_autostart) {
            boolean on = !prefs.autostart();
            prefs.setAutostart(on);
            Toast.makeText(this, on ? R.string.toast_autostart_on : R.string.toast_autostart_off, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.menu_battery) {
            openBatterySettings();
        } else if (id == R.id.menu_copy_received) {
            toggleCopyReceived();
        } else if (id == R.id.menu_log) {
            startActivity(new Intent(this, LogActivity.class));
        } else if (id == R.id.menu_about) {
            showAbout();
        } else {
            return false;
        }
        return true;
    }

    /**
     * Галочка «Сохранять полученные файлы в «Загрузки»»: копии делаются, если настройка включена и (на Android 8–9)
     * приложению разрешено писать в память. Без разрешения галочка не стоит, хотя настройка по умолчанию включена, —
     * нажатие на неё тогда просит разрешение (фоновая служба просить его не может).
     */
    private boolean copiesWillBeMade() {
        return prefs.copyReceived() && (!Downloads.needsLegacyPermission() || hasStoragePermission());
    }

    private void toggleCopyReceived() {
        boolean turnOn = !copiesWillBeMade();
        prefs.setCopyReceived(turnOn);
        if (turnOn && Downloads.needsLegacyPermission() && !hasStoragePermission()) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private boolean hasStoragePermission() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    /**
     * Системный запрос «не ограничивать»; если уже разрешено — общий список, где это можно отменить. Это главная
     * функция приложения (оставаться на связи в фоне), и просьба показывается только по выбору человека в меню.
     */
    @SuppressLint("BatteryLife")
    private void openBatterySettings() {
        Intent intent = isIgnoringBatteryOptimizations()
                ? new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                : new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {
                // на этом телефоне нет и этого
            }
        }
    }

    /** «О программе»: версии приложения и ядра ({@code GET /api/state} → {@code self.version}). */
    private void showAbout() {
        String known = NodeRuntime.get().state().version();
        NodeApi api = NodeRuntime.get().api();
        if (!known.isEmpty() || api == null) {
            showAboutDialog(known);
            return;
        }
        io.execute(() -> {
            String version = "";
            try {
                JSONObject state = api.getObject("/api/state");
                version = Json.str(Json.obj(state, "self"), "version");
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "cannot read the core version: " + e);
            }
            String v = version;
            main.post(() -> {
                if (!isDestroyed()) {
                    showAboutDialog(v);
                }
            });
        });
    }

    private void showAboutDialog(String coreVersion) {
        String core = coreVersion == null || coreVersion.isEmpty() ? getString(R.string.about_core_unknown) : coreVersion;
        String app = BuildInfo.versionName(this) + " (" + BuildInfo.versionCode(this) + ")";
        String webView = webViewVersion();
        String text = getString(R.string.about_text, app, core, webView.isEmpty() ? getString(R.string.about_core_unknown) : webView);
        if (WebViewVersion.isTooOld(webView)) {
            text += "\n\n" + getString(R.string.webview_old_text, WebViewVersion.MIN_MAJOR);
        }
        showGlassDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(text)
                .setPositiveButton(R.string.about_close, null));
    }

    /** Версия системного WebView («113.0.5672.136») или пустая строка, если узнать нельзя. */
    private static String webViewVersion() {
        try {
            PackageInfo pkg = WebView.getCurrentWebViewPackage();
            return pkg == null || pkg.versionName == null ? "" : pkg.versionName;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Интерфейсу нужен Chromium 111 или новее ({@link WebViewVersion}): на более старом WebView страницы могут
     * выглядеть сломанными. Предупреждаем один раз для каждой версии, уже после загрузки страницы: к этому времени
     * человек ответил на вопрос про уведомления, и окна не наслаиваются.
     */
    private void warnIfWebViewIsOld() {
        String version = webViewVersion();
        if (!WebViewVersion.isTooOld(version) || version.equals(prefs.webViewWarned()) || isFinishing() || isDestroyed()) {
            return;
        }
        prefs.setWebViewWarned(version);
        showGlassDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.webview_old_title)
                .setMessage(getString(R.string.webview_old_text, WebViewVersion.MIN_MAJOR))
                .setPositiveButton(R.string.about_close, null));
    }
}
