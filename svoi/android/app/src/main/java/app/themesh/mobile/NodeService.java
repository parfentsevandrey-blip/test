package app.themesh.mobile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import app.themesh.mobile.core.AppLog;
import app.themesh.mobile.core.DeviceName;
import app.themesh.mobile.core.EventWatcher;
import app.themesh.mobile.core.LocalAddrs;
import app.themesh.mobile.core.NodeApi;
import app.themesh.mobile.core.NodeState;
import app.themesh.mobile.core.NodeSupervisor;
import app.themesh.mobile.core.Notice;
import app.themesh.mobile.core.Notices;
import app.themesh.mobile.core.Platform;
import app.themesh.mobile.core.ReceivedFiles;
import app.themesh.mobile.core.SeenKeys;
import app.themesh.mobile.core.StatusLine;

/**
 * Служба переднего плана, которая держит узел (программу themesh) запущенным, пока приложение живо: запускает
 * и останавливает процесс, держит блокировку многоадресных пакетов Wi-Fi, пишет адреса телефона для Go-части,
 * читает поток событий узла и показывает уведомления. Пока работает, висит постоянное уведомление
 * «The Mesh работает» со строкой «На связи 2 из 3» и действиями «Открыть» и «Выйти».
 */
public class NodeService extends Service {
    static final String ACTION_START = "app.themesh.mobile.action.START";
    static final String ACTION_RESTART = "app.themesh.mobile.action.RESTART";
    static final String ACTION_QUIT = "app.themesh.mobile.action.QUIT";

    private static final String TAG = "themesh";
    /** Поколение запуска: поздние вызовы наблюдателя прежней службы не должны портить состояние новой. */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    // ---- запуск и остановка снаружи ---------------------------------------------------------

    /** Запустить узел (или убедиться, что он запущен). Вызывается из окна и при загрузке системы. */
    static void start(Context context) {
        NodeRuntime.get().requestStart();
        send(context, ACTION_START);
    }

    /** «Запустить ещё раз»: без паузы перед новой попыткой. */
    static void restart(Context context) {
        NodeRuntime.get().requestStart();
        send(context, ACTION_RESTART);
    }

    /** Остановить узел и службу (как кнопка «Выйти» в уведомлении). */
    static void quit(Context context) {
        context.getApplicationContext().startService(new Intent(context, NodeService.class).setAction(ACTION_QUIT));
    }

    private static void send(Context context, String action) {
        Intent i = new Intent(context, NodeService.class).setAction(action);
        try {
            context.getApplicationContext().startForegroundService(i);
        } catch (RuntimeException e) {
            // ForegroundServiceStartNotAllowedException: запуск из фона без права; окно потом попробует снова
            Log.w(TAG, "cannot start the service: " + e);
        }
    }

    // ---- состояние службы -------------------------------------------------------------------

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SeenKeys seen = new SeenKeys();
    private final Map<Network, List<LocalAddrs.Addr>> linkAddrs = new ConcurrentHashMap<>();
    private final Runnable scheduledRefresh = this::runScheduledRefresh;

    private Prefs prefs;
    private AppLog log;
    private AndroidTexts texts;
    private ExecutorService addrWorker;
    private ExecutorService eventWorker;
    private ExecutorService copyWorker; // копии больших файлов не должны задерживать остальные уведомления
    private ReceivedFiles received;

    private int generation;
    private boolean started;
    private boolean shutDown;
    private NodeSupervisor supervisor;
    private WifiManager.MulticastLock multicast;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private String lastAddrs; // null: файл ещё не писали (первый раз пишем всегда, даже пустой)
    private String lastStatus = "";

    private EventWatcher watcher;
    private NodeApi watcherApi;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        texts = new AndroidTexts(this);
        log = new AppLog(new File(getFilesDir(), "themesh.log"), (level, message) ->
                Log.println(level == 'E' ? Log.ERROR : level == 'W' ? Log.WARN : Log.INFO, TAG, message));
        Notifier.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_QUIT.equals(action)) {
            quitNow();
            return START_NOT_STICKY;
        }
        if (!enterForeground()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (shutDown) {
            // Эта служба уже останавливается («Выйти» и тут же значок на экране): новая начнёт работу чуть позже.
            stopSelf();
            Context app = getApplicationContext();
            main.postDelayed(() -> send(app, action == null ? ACTION_START : action), 700);
            return START_NOT_STICKY;
        }
        ensureStarted();
        if (ACTION_RESTART.equals(action) && supervisor != null) {
            supervisor.restartNow();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        shutdown(false);
        super.onDestroy();
    }

    // ---- служба переднего плана -------------------------------------------------------------

    private boolean enterForeground() {
        try {
            Notification n = Notifier.node(this, StatusLine.text(texts, NodeRuntime.get().status(), NodeRuntime.get().state()));
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.ID_NODE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notifier.ID_NODE, n);
            }
            return true;
        } catch (RuntimeException e) {
            // Например, ForegroundServiceStartNotAllowedException после перезапуска системой из фона.
            log.e("cannot enter the foreground", e);
            return false;
        }
    }

    private void quitNow() {
        log.i("quit requested by the user");
        shutdown(true);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private synchronized void shutdown(boolean byUser) {
        if (shutDown) {
            return;
        }
        shutDown = true;
        GENERATION.incrementAndGet(); // всё, что ещё придёт от этого запуска, игнорируем
        main.removeCallbacks(scheduledRefresh);
        stopWatcher();
        if (networkCallback != null) {
            try {
                connectivity.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
                // уже снят
            }
            networkCallback = null;
        }
        if (multicast != null && multicast.isHeld()) {
            multicast.release();
        }
        if (supervisor != null) {
            supervisor.requestStop(); // закрывает stdin узла сразу; дальше он сам завершится
        }
        if (addrWorker != null) {
            addrWorker.shutdown();
        }
        if (eventWorker != null) {
            eventWorker.shutdown();
        }
        if (copyWorker != null) {
            copyWorker.shutdown(); // уже начатая копия дойдёт до конца
        }
        NodeRuntime.get().stopped(byUser);
    }

    // ---- запуск узла ------------------------------------------------------------------------

    private void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        generation = GENERATION.incrementAndGet();
        NodeRuntime.get().starting();
        addrWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "themesh-addrs"));
        eventWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "themesh-notify"));
        copyWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "themesh-copy"));
        received = new ReceivedFiles(new ReceivedStore(this), prefs::copyReceived, getFilesDir(), new File(getFilesDir(), "themesh"));
        log.i("service started, app " + BuildInfo.versionName(this) + ", sdk " + Build.VERSION.SDK_INT
                + ", abi " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?"));
        acquireMulticastLock();
        registerNetworkCallback();
        refreshAddresses(); // до запуска узла: он читает этот файл при старте
        supervisor = new NodeSupervisor(spec(), log, new SupervisorListener(generation));
        supervisor.start();
    }

    private NodeSupervisor.Spec spec() {
        NodeSupervisor.Spec spec = new NodeSupervisor.Spec();
        spec.binary = new File(getApplicationInfo().nativeLibraryDir, "libthemesh.so");
        spec.filesDir = getFilesDir();
        spec.cacheDir = getCacheDir();
        spec.dataDir = new File(getFilesDir(), "themesh");
        spec.logFile = log.file();
        spec.addrsFile = new File(getFilesDir(), "local-addrs.txt");
        spec.platform = Platform.android(Build.SUPPORTED_ABIS);
        spec.lastPort = prefs::lastPort;
        spec.deviceName = DeviceName.choose(settingsDeviceName(), Build.MODEL);
        return spec;
    }

    /** «Имя устройства» из настроек Android ({@code Settings.Global.DEVICE_NAME}); на части телефонов его нет. */
    private String settingsDeviceName() {
        try {
            return Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** События наблюдателя за процессом. Вызываются из его потока. */
    private final class SupervisorListener implements NodeSupervisor.Listener {
        private final int gen;

        SupervisorListener(int gen) {
            this.gen = gen;
        }

        private boolean current() {
            return gen == GENERATION.get();
        }

        @Override
        public void onStarting(int attempt) {
            if (!current()) {
                return;
            }
            stopWatcher();
            NodeRuntime.get().starting();
            updateNotification();
        }

        @Override
        public void onReady(NodeApi api, int port) {
            if (!current()) {
                return;
            }
            prefs.setLastPort(port);
            NodeState state = new NodeState();
            EventWatcher w = new EventWatcher(api, state, log, new Events());
            synchronized (NodeService.this) {
                stopWatcher();
                watcher = w;
                watcherApi = api;
            }
            NodeRuntime.get().ready(api, state);
            w.start();
            updateNotification();
        }

        @Override
        public void onFailed(NodeSupervisor.Failure failure, long retryInMs) {
            if (!current()) {
                return;
            }
            stopWatcher();
            NodeRuntime.get().failed(failure);
            updateNotification();
        }

        @Override
        public void onStopped() {
            // уже сообщено из shutdown(): служба решает, когда она остановлена
        }
    }

    /** События узла: состояние сети и то, о чём стоит уведомить. */
    private final class Events implements EventWatcher.Listener {
        @Override
        public void onState(NodeState state) {
            NodeRuntime.get().changed();
            updateNotification();
        }

        @Override
        public void onEvent(String kind, JSONObject data) {
            ExecutorService worker = ReceivedFiles.isFinishedIncoming(kind, data) ? copyWorker : eventWorker;
            if (worker == null || worker.isShutdown()) {
                return;
            }
            try {
                worker.execute(() -> notifyAbout(kind, data));
            } catch (RuntimeException ignored) {
                // служба останавливается
            }
        }

        @Override
        public void onConnection(boolean connected) {
            // интерфейс сам переподключается; журналировать каждый обрыв незачем
        }
    }

    private synchronized void stopWatcher() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
            watcherApi = null;
        }
    }

    // ---- уведомления ------------------------------------------------------------------------

    /** Обновляет строку в постоянном уведомлении, только если она изменилась. */
    private synchronized void updateNotification() {
        if (shutDown) {
            return;
        }
        String text = StatusLine.text(texts, NodeRuntime.get().status(), NodeRuntime.get().state());
        if (text.equals(lastStatus)) {
            return;
        }
        lastStatus = text;
        getSystemService(NotificationManager.class).notify(Notifier.ID_NODE, Notifier.node(this, text));
    }

    private void notifyAbout(String kind, JSONObject data) {
        final EventWatcher w;
        final NodeApi api;
        final NodeState state;
        synchronized (this) {
            w = watcher;
            api = watcherApi;
            state = NodeRuntime.get().state();
        }
        if (api == null) {
            return;
        }
        Notices.Lookup lookup = new Notices.Lookup() {
            @Override
            public String peerName(String peerId) {
                String name = state.peerName(peerId);
                if (name == null && w != null) {
                    // устройство, которое только что вошло в сеть, может написать раньше, чем придёт список
                    try {
                        w.refresh();
                    } catch (IOException ignored) {
                        // имя — мелочь
                    }
                    name = state.peerName(peerId);
                }
                return name == null ? "" : name;
            }

            @Override
            public JSONObject mail(String id) throws IOException {
                return api.getObject("/api/mail/" + Uri.encode(id));
            }
        };
        Notice notice = Notices.forEvent(kind, data, texts, lookup);
        if (notice == null || !seen.add(notice.key)) {
            return;
        }
        if (notice.kind == Notice.Kind.RECEIVED) {
            notice = withCopyResult(notice, kind, data); // копия делается, даже если уведомление не покажем
        }
        if (NodeRuntime.get().uiForeground()) {
            return; // человек смотрит на окно: интерфейс показывает это сам
        }
        Notifier.post(this, notice);
    }

    /**
     * Копирует полученный файл в «Загрузки/The Mesh» (если человек этого не выключил) и дописывает к уведомлению
     * «Файл получен», куда делась копия или почему её нет. Имена файлов в журнал не пишутся.
     */
    private Notice withCopyResult(Notice notice, String kind, JSONObject data) {
        ReceivedFiles files = received;
        if (files == null) {
            return notice;
        }
        ReceivedFiles.Result result = files.onEvent(kind, data);
        if (result == null) {
            return notice;
        }
        switch (result.outcome) {
            case COPIED:
                log.i("a received file was copied to Downloads/" + ReceivedFiles.FOLDER);
                break;
            case NO_PERMISSION:
                log.w("a received file was not copied to Downloads: no permission to write to storage");
                break;
            case FAILED:
                log.w("a received file could not be copied to Downloads: " + result.error);
                break;
            case SKIPPED:
                log.w("a received file was not copied to Downloads: " + result.error);
                break;
            default:
                break;
        }
        String line = ReceivedFiles.describe(result, texts);
        return line.isEmpty() ? notice : notice.withBody(notice.body + "\n" + line);
    }

    // ---- сеть телефона ----------------------------------------------------------------------

    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicast = wifi.createMulticastLock("themesh");
                multicast.setReferenceCounted(false);
                multicast.acquire();
            }
        } catch (RuntimeException e) {
            log.w("multicast lock: " + e);
        }
    }

    private void registerNetworkCallback() {
        connectivity = getSystemService(ConnectivityManager.class);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                trackNetwork(network, connectivity.getLinkProperties(network));
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                trackNetwork(network, properties);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                // Wi-Fi это или мобильный интернет, становится известно не сразу: перечитываем адреса сети
                trackNetwork(network, connectivity.getLinkProperties(network));
            }

            @Override
            public void onLost(Network network) {
                linkAddrs.remove(network);
                scheduleAddrRefresh();
            }
        };
        try {
            connectivity.registerNetworkCallback(new NetworkRequest.Builder().build(), networkCallback);
        } catch (RuntimeException e) {
            log.w("network callback: " + e);
            networkCallback = null;
        }
    }

    private void trackNetwork(Network network, LinkProperties properties) {
        if (properties != null) {
            // Другие устройства бывают в Wi-Fi и в проводной сети (и в точке доступа, но она — не «сеть» Android,
            // её адреса видны только через NetworkInterface); в мобильном интернете и в VPN их нет.
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            boolean shared = caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            List<LocalAddrs.Addr> list = new ArrayList<>();
            for (LinkAddress a : properties.getLinkAddresses()) {
                list.add(new LocalAddrs.Addr(a.getAddress(), a.getPrefixLength(), shared));
            }
            linkAddrs.put(network, list);
        }
        scheduleAddrRefresh();
    }

    /** Сеть меняется пачкой событий: ждём, пока они утихнут, и перечитываем адреса один раз. */
    private void scheduleAddrRefresh() {
        main.removeCallbacks(scheduledRefresh);
        main.postDelayed(scheduledRefresh, 400);
    }

    private void runScheduledRefresh() {
        ExecutorService e = addrWorker;
        if (e != null && !e.isShutdown()) {
            e.execute(this::refreshAddresses);
        }
    }

    /**
     * Пишет local-addrs.txt: по строке на адрес, кроме петлевых и link-local; для адресов в общей сети (Wi-Fi,
     * Ethernet, точка доступа) с длиной префикса — по ним программа ищет другие устройства (см. LocalAddrs).
     * Адреса берутся и из NetworkInterface (как просили), и из LinkProperties сетей (надёжнее на новых Android).
     */
    private synchronized void refreshAddresses() {
        if (shutDown) {
            return;
        }
        List<LocalAddrs.Addr> all = new ArrayList<>();
        try {
            all.addAll(LocalAddrs.scan());
        } catch (SocketException | RuntimeException e) {
            log.w("cannot list the network interfaces: " + e);
        }
        for (List<LocalAddrs.Addr> list : linkAddrs.values()) {
            all.addAll(list);
        }
        List<String> lines = LocalAddrs.lines(all);
        String content = LocalAddrs.format(lines);
        if (content.equals(lastAddrs)) {
            return;
        }
        try {
            LocalAddrs.write(new File(getFilesDir(), "local-addrs.txt"), content);
            lastAddrs = content;
            log.i("local addresses updated (" + lines.size() + "): " + String.join(" ", lines));
        } catch (IOException e) {
            log.w("cannot write local-addrs.txt: " + e);
        }
    }
}
