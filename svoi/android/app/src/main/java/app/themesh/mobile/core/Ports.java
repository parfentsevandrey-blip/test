package app.themesh.mobile.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * Выбор порта для веб-интерфейса узла на 127.0.0.1. Сначала 8777 (чтобы адрес интерфейса, а с ним
 * и сохранённые страницей настройки, не менялись), затем порт, которым пользовались в прошлый
 * раз, и наконец любой свободный.
 */
public final class Ports {
    public static final int DEFAULT = 8777;

    private Ports() {
    }

    /** Свободен ли порт на 127.0.0.1. */
    public static boolean isFree(int port) {
        if (port <= 0 || port > 65535) {
            return false;
        }
        try (ServerSocket s = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort() == port;
        } catch (IOException e) {
            return false;
        }
    }

    /** Любой свободный порт на 127.0.0.1 или 0, если не вышло. */
    public static int anyFree() {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * @param preferred первый, кого пробуем (8777)
     * @param last      порт прошлого запуска (0, если не помним)
     * @return порт, который был свободен, или 0
     */
    public static int pick(int preferred, int last) {
        if (isFree(preferred)) {
            return preferred;
        }
        if (last != preferred && isFree(last)) {
            return last;
        }
        return anyFree();
    }
}
