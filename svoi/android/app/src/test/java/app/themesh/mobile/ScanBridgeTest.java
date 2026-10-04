package app.themesh.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

/** {@code window.themeshApp}: что видит страница и когда её просьбу открыть сканер выполняют. */
public class ScanBridgeTest {
    private static final String ORIGIN = "http://127.0.0.1:8777";

    /** «Главный поток»: все его задачи идут в одном потоке с этим именем. */
    private final ExecutorService mainThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-main"));
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicReference<String> page = new AtomicReference<>(ORIGIN + "/#/home");
    private final List<String> askedFrom = Collections.synchronizedList(new ArrayList<>());

    @After
    public void shutDown() {
        mainThread.shutdownNow();
    }

    private ScanBridge bridge(boolean camera, String origin) {
        return new ScanBridge(camera, mainThread,
                () -> {
                    askedFrom.add(Thread.currentThread().getName());
                    return page.get();
                },
                () -> {
                    askedFrom.add(Thread.currentThread().getName());
                    return origin;
                },
                () -> {
                    askedFrom.add(Thread.currentThread().getName());
                    opened.incrementAndGet();
                });
    }

    private void waitForTheMainThread() throws Exception {
        mainThread.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void thePageLearnsWhetherThereIsACamera() {
        assertTrue(bridge(true, ORIGIN).canScan());
        assertFalse(bridge(false, ORIGIN).canScan());
    }

    @Test
    public void theScannerIsOpenedInTheMainThreadAndOnlyThere() throws Exception {
        ScanBridge bridge = bridge(true, ORIGIN);
        bridge.scanInvite(); // вызывает служебный поток WebView, здесь — поток теста
        waitForTheMainThread();
        assertEquals(1, opened.get());
        assertFalse("адрес страницы и открытие сканера — не в вызывающем потоке: " + askedFrom, askedFrom.isEmpty());
        for (String thread : askedFrom) {
            assertEquals("fake-main", thread);
        }
    }

    @Test
    public void nothingHappensBeforeTheMainThreadGetsToIt() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        ScanBridge bridge = new ScanBridge(true, queue::add, () -> ORIGIN + "/", () -> ORIGIN, opened::incrementAndGet);
        bridge.scanInvite();
        assertEquals("сканер не открывается в потоке вызова", 0, opened.get());
        assertEquals(1, queue.size());
        queue.poll().run();
        assertEquals(1, opened.get());
    }

    @Test
    public void onlyTheNodesOwnInterfaceMayOpenTheScanner() throws Exception {
        ScanBridge bridge = bridge(true, ORIGIN);
        for (String ok : new String[] {ORIGIN, ORIGIN + "/", ORIGIN + "/#/chat/p", ORIGIN + "/?x=1", ORIGIN + "#/a"}) {
            page.set(ok);
            bridge.scanInvite();
            waitForTheMainThread();
        }
        assertEquals("со страницы узла — открывается", 5, opened.get());

        opened.set(0);
        for (String bad : new String[] {
                "https://evil.example/", "http://evil.example/", "http://127.0.0.1:8778/", "http://127.0.0.1:87771/", "https://127.0.0.1:8777/",
                "http://127.0.0.1:8777@evil.example/", "http://127.0.0.1:8777.evil.example/", "http://localhost:8777/", "about:blank",
                "data:text/html,<script></script>", "file:///etc/passwd", "javascript:alert(1)", "", null}) {
            page.set(bad);
            bridge.scanInvite();
            waitForTheMainThread();
        }
        assertEquals("с любой другой страницы — нет", 0, opened.get());
    }

    @Test
    public void withoutTheNodesAddressNothingIsOpened() throws Exception {
        for (String origin : new String[] {"", null}) {
            bridge(true, origin).scanInvite();
            waitForTheMainThread();
        }
        assertEquals(0, opened.get());
    }

    @Test
    public void aPhoneWithoutACameraNeverOpensTheScanner() throws Exception {
        bridge(false, ORIGIN).scanInvite();
        waitForTheMainThread();
        assertEquals(0, opened.get());
    }

    @Test
    public void thePageSeesExactlyTwoMethods() {
        // WebView отдаёт странице только методы с @JavascriptInterface: их должно быть ровно два, и других публичных нет
        TreeSet<String> exposed = new TreeSet<>();
        TreeSet<String> publicOnes = new TreeSet<>();
        for (Method m : ScanBridge.class.getMethods()) {
            if (m.getDeclaringClass() != ScanBridge.class) {
                continue;
            }
            if (m.isAnnotationPresent(JavascriptInterface.class)) {
                exposed.add(m.getName());
            }
            if (Modifier.isPublic(m.getModifiers())) {
                publicOnes.add(m.getName());
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList("canScan", "scanInvite")), exposed);
        assertEquals(exposed, publicOnes);
        assertEquals("themeshApp", ScanBridge.NAME);
    }
}
