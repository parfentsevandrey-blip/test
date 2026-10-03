package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class LocalAddrsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static InetAddress ip(String s) throws Exception {
        return InetAddress.getByName(s);
    }

    @Test
    public void onlyUsableAddressesAreSelected() throws Exception {
        List<InetAddress> all = Arrays.asList(
                ip("192.168.1.5"), ip("10.0.0.7"), ip("100.64.1.2"), ip("2001:db8::1"), ip("fd12:3456::1"),
                ip("127.0.0.1"), ip("::1"), ip("169.254.10.10"), ip("fe80::1"), ip("224.0.0.1"), ip("ff02::1"), ip("0.0.0.0"), ip("::"));
        assertEquals(Arrays.asList("10.0.0.7", "100.64.1.2", "192.168.1.5", "2001:db8:0:0:0:0:0:1", "fd12:3456:0:0:0:0:0:1"), LocalAddrs.select(all));
    }

    @Test
    public void duplicatesAndNullsAreDropped() throws Exception {
        assertEquals(Arrays.asList("192.168.1.5"), LocalAddrs.select(Arrays.asList(ip("192.168.1.5"), null, ip("192.168.1.5"))));
    }

    @Test
    public void aScopeIdIsCutOff() throws Exception {
        Inet6Address scoped = Inet6Address.getByAddress(null, ip("2001:db8::5").getAddress(), 3);
        assertEquals("2001:db8:0:0:0:0:0:5", LocalAddrs.text(scoped));
        assertEquals("192.168.0.1", LocalAddrs.text(ip("192.168.0.1")));
    }

    @Test
    public void theFileHasOneAddressPerLine() {
        assertEquals("", LocalAddrs.format(Arrays.asList()));
        assertEquals("10.0.0.1\n192.168.1.2\n", LocalAddrs.format(Arrays.asList("10.0.0.1", "192.168.1.2")));
    }

    @Test
    public void writeReplacesTheFileWholeAndLeavesNoTemporaryFile() throws Exception {
        File f = new File(tmp.getRoot(), "local-addrs.txt");
        LocalAddrs.write(f, "10.0.0.1\n");
        assertEquals("10.0.0.1\n", new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        LocalAddrs.write(f, "192.168.1.2\n10.0.0.9\n");
        assertEquals("192.168.1.2\n10.0.0.9\n", new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        LocalAddrs.write(f, "");
        assertEquals(0, f.length());
        assertEquals(Arrays.asList("local-addrs.txt"), Arrays.asList(tmp.getRoot().list()));
    }

    @Test
    public void scanNeverReturnsLoopbackAndTheFileIsWritten() throws Exception {
        for (InetAddress a : LocalAddrs.scan()) {
            // scan() отдаёт всё с работающих интерфейсов; loopback-интерфейс пропущен целиком
            assertFalse(a.toString(), a.isLoopbackAddress());
        }
        File f = new File(tmp.getRoot(), "a.txt");
        List<String> written = LocalAddrs.refresh(f);
        assertTrue(f.isFile());
        assertEquals(LocalAddrs.format(written), new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        for (String line : written) {
            assertFalse(line.startsWith("127."));
            assertFalse(line.startsWith("fe80"));
        }
    }
}
