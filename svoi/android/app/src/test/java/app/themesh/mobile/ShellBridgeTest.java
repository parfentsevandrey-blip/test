package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.webkit.JavascriptInterface;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import app.themesh.mobile.core.PageLook;

/** {@code window.themeshShell}: что страница может попросить у окна и когда её просьбу выполняют. */
public class ShellBridgeTest {
    private static final String ORIGIN = "http://127.0.0.1:8777";

    /** «Главный поток»: все его задачи идут в одном потоке с этим именем. */
    private final ExecutorService mainThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-main"));
    private final AtomicInteger menus = new AtomicInteger();
    private final List<String> looks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> skies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> haptics = Collections.synchronizedList(new ArrayList<>());
    private final List<String> threads = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<String> page = new AtomicReference<>(ORIGIN + "/#/home");

    @After
    public void shutDown() {
        mainThread.shutdownNow();
    }

    private final ShellBridge.Host host = new ShellBridge.Host() {
        @Override
        public void openMenu() {
            threads.add(Thread.currentThread().getName());
            menus.incrementAndGet();
        }

        @Override
        public void look(PageLook look) {
            threads.add(Thread.currentThread().getName());
            looks.add(look.toString());
        }

        @Override
        public void sky(int top, int bottom) {
            threads.add(Thread.currentThread().getName());
            skies.add(String.format("%08X/%08X", top, bottom));
        }

        @Override
        public void haptic(String kind) {
            threads.add(Thread.currentThread().getName());
            haptics.add(kind);
        }
    };

    private ShellBridge bridge(String origin) {
        return new ShellBridge(mainThread, page::get, () -> origin, host);
    }

    private void waitForTheMainThread() throws Exception {
        mainThread.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void theMenuIsOpenedInTheMainThread() throws Exception {
        bridge(ORIGIN).menu(); // вызывает служебный поток WebView, здесь — поток теста
        waitForTheMainThread();
        assertEquals(1, menus.get());
        assertEquals(Collections.singletonList("fake-main"), threads);
    }

    @Test
    public void nothingHappensBeforeTheMainThreadGetsToIt() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        ShellBridge bridge = new ShellBridge(queue::add, () -> ORIGIN + "/", () -> ORIGIN, host);
        bridge.menu();
        bridge.look("dark", "glass");
        assertEquals(0, menus.get());
        assertTrue(looks.isEmpty());
        assertEquals(2, queue.size());
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
        assertEquals(1, menus.get());
        assertEquals(Collections.singletonList("glass/dark"), looks);
    }

    @Test
    public void theLookOfThePageIsPassedOnAndNonsenseIsNot() throws Exception {
        ShellBridge bridge = bridge(ORIGIN);
        bridge.look("light", "glass");
        bridge.look("dark", "classic");
        bridge.look("auto", "glass");  // страница сообщает уже выбранную тему, а не «авто»
        bridge.look("dark", "neon");
        bridge.look(null, "glass");
        bridge.look("dark", null);
        bridge.look("", "");
        waitForTheMainThread();
        assertEquals(Arrays.asList("glass/light", "classic/dark"), looks);
    }

    @Test
    public void theRosaLookAndItsSkyArePassedOn() throws Exception {
        ShellBridge bridge = bridge(ORIGIN);
        bridge.look("dark", "rosa");
        bridge.look("light", "rosa");
        bridge.sky("#2c76d8", "#9bcbf6", false);
        bridge.sky("#05081A", "#121637", true);
        bridge.sky("rgb(1, 2, 3)", "#9bcbf6", false);  // CSS-цвета, которые страница не посылает, тоже понятны
        // не цвета и пустое — игнорируется
        bridge.sky("blue", "#9bcbf6", false);
        bridge.sky("#2c76d8", "", false);
        bridge.sky(null, null, false);
        bridge.sky("#2c76d8", "javascript:alert(1)", false);
        waitForTheMainThread();
        assertEquals(Arrays.asList("rosa/dark", "rosa/light"), looks);
        assertEquals(Arrays.asList("FF2C76D8/FF9BCBF6", "FF05081A/FF121637", "FF010203/FF9BCBF6"), skies);
    }

    @Test
    public void hapticsAreAskedForByName() throws Exception {
        ShellBridge bridge = bridge(ORIGIN);
        for (String kind : new String[] {"tick", "press", "select", "vibrate", "TICK", "", null, "tick; rm -rf /"}) {
            bridge.haptic(kind);
        }
        waitForTheMainThread();
        assertEquals(Arrays.asList("tick", "press", "select"), haptics);
        haptics.clear();
        for (String bad : new String[] {"https://evil.example/", "about:blank", "", null}) {
            page.set(bad);
            bridge.haptic("tick");
            bridge.sky("#2c76d8", "#9bcbf6", false);
        }
        waitForTheMainThread();
        assertTrue("с чужой страницы — ни отклика, ни цвета", haptics.isEmpty() && skies.isEmpty());
    }

    @Test
    public void onlyTheNodesOwnInterfaceIsListenedTo() throws Exception {
        ShellBridge bridge = bridge(ORIGIN);
        for (String ok : new String[] {ORIGIN, ORIGIN + "/", ORIGIN + "/#/chat/p", ORIGIN + "/?x=1"}) {
            page.set(ok);
            bridge.menu();
            bridge.look("light", "glass");
            waitForTheMainThread();
        }
        assertEquals("со страницы узла — выполняется", 4, menus.get());
        assertEquals(4, looks.size());

        menus.set(0);
        looks.clear();
        for (String bad : new String[] {
                "https://evil.example/", "http://127.0.0.1:8778/", "http://127.0.0.1:87771/", "https://127.0.0.1:8777/",
                "http://127.0.0.1:8777@evil.example/", "http://localhost:8777/", "about:blank", "data:text/html,<script></script>",
                "file:///etc/passwd", "javascript:alert(1)", "", null}) {
            page.set(bad);
            bridge.menu();
            bridge.look("light", "glass");
            waitForTheMainThread();
        }
        assertEquals("с любой другой страницы — нет", 0, menus.get());
        assertTrue(looks.isEmpty());
    }

    @Test
    public void withoutTheNodesAddressNothingHappens() throws Exception {
        for (String origin : new String[] {"", null}) {
            bridge(origin).menu();
            bridge(origin).look("dark", "glass");
            waitForTheMainThread();
        }
        assertEquals(0, menus.get());
        assertTrue(looks.isEmpty());
    }

    @Test
    public void thePageSeesExactlyFourMethods() {
        // WebView отдаёт странице только методы с @JavascriptInterface: их должно быть ровно четыре, и других публичных нет
        TreeSet<String> exposed = new TreeSet<>();
        TreeSet<String> publicOnes = new TreeSet<>();
        for (Method m : ShellBridge.class.getMethods()) {
            if (m.getDeclaringClass() != ShellBridge.class) {
                continue;
            }
            if (m.isAnnotationPresent(JavascriptInterface.class)) {
                exposed.add(m.getName());
            }
            if (Modifier.isPublic(m.getModifiers())) {
                publicOnes.add(m.getName());
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList("haptic", "look", "menu", "sky")), exposed);
        assertEquals(exposed, publicOnes);
        assertEquals("themeshShell", ShellBridge.NAME);
    }
}
