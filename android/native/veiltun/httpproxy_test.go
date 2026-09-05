package veiltun

import (
	"crypto/tls"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"
)

// A SOCKS5 server that resolves names from a table, standing in for tor.
// Names are the point: the proxy must hand them over rather than resolve
// them, and a name that only this table knows proves it did.
func fakeSocks(t *testing.T, names map[string]string) string {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { listener.Close() })
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go serveFakeSocks(conn, names)
		}
	}()
	return listener.Addr().String()
}

func serveFakeSocks(conn net.Conn, names map[string]string) {
	defer conn.Close()
	buf := make([]byte, 300)
	if _, err := io.ReadFull(conn, buf[:2]); err != nil || buf[0] != 5 {
		return
	}
	if _, err := io.ReadFull(conn, buf[:int(buf[1])]); err != nil {
		return
	}
	conn.Write([]byte{5, 0})

	if _, err := io.ReadFull(conn, buf[:4]); err != nil || buf[1] != 1 {
		return
	}
	var host string
	switch buf[3] {
	case 1:
		io.ReadFull(conn, buf[:4])
		host = net.IP(buf[:4]).String()
	case 3:
		io.ReadFull(conn, buf[:1])
		n := int(buf[0])
		io.ReadFull(conn, buf[:n])
		host = string(buf[:n])
	case 4:
		io.ReadFull(conn, buf[:16])
		host = net.IP(buf[:16]).String()
	default:
		return
	}
	io.ReadFull(conn, buf[:2])
	port := int(buf[0])<<8 | int(buf[1])

	target := net.JoinHostPort(host, strconv.Itoa(port))
	if mapped, ok := names[host]; ok {
		target = mapped
	} else if net.ParseIP(host) == nil {
		// An unknown name: "host unreachable", as tor would say.
		conn.Write([]byte{5, 4, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	up, err := net.Dial("tcp", target)
	if err != nil {
		conn.Write([]byte{5, 5, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	defer up.Close()
	conn.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0})
	done := make(chan struct{}, 2)
	go func() { io.Copy(up, conn); done <- struct{}{} }()
	go func() { io.Copy(conn, up); done <- struct{}{} }()
	<-done
}

func startProxy(t *testing.T, socks, blocklist string) *url.URL {
	t.Helper()
	port, err := StartHTTPProxy("tcp", socks, blocklist)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(StopHTTPProxy)
	return &url.URL{Scheme: "http", Host: "127.0.0.1:" + strconv.Itoa(port)}
}

func hostOf(u string) string {
	parsed, _ := url.Parse(u)
	return parsed.Host
}

func TestHTTPProxyForwardsPlainRequestsByName(t *testing.T) {
	origin := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Seen-Host", r.Host)
		w.Header().Set("X-Seen-Path", r.URL.Path)
		io.WriteString(w, "plain body")
	}))
	defer origin.Close()

	socks := fakeSocks(t, map[string]string{"origin.test": hostOf(origin.URL)})
	proxyURL := startProxy(t, socks, "")

	client := &http.Client{Transport: &http.Transport{Proxy: http.ProxyURL(proxyURL)}}
	resp, err := client.Get("http://origin.test/some/path?q=1")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 || string(body) != "plain body" {
		t.Fatalf("got %d %q", resp.StatusCode, body)
	}
	if resp.Header.Get("X-Seen-Host") != "origin.test" {
		t.Fatalf("origin saw host %q; the name did not travel through", resp.Header.Get("X-Seen-Host"))
	}
	if resp.Header.Get("X-Seen-Path") != "/some/path" {
		t.Fatalf("origin saw path %q", resp.Header.Get("X-Seen-Path"))
	}
}

func TestHTTPProxyTunnelsConnect(t *testing.T) {
	origin := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, "tls body for "+r.Host)
	}))
	defer origin.Close()

	socks := fakeSocks(t, map[string]string{"secure.test": hostOf(origin.URL)})
	proxyURL := startProxy(t, socks, "")

	client := &http.Client{Transport: &http.Transport{
		Proxy:           http.ProxyURL(proxyURL),
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}}
	resp, err := client.Get("https://secure.test/")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 || string(body) != "tls body for secure.test" {
		t.Fatalf("got %d %q", resp.StatusCode, body)
	}
}

func TestHTTPProxyRefusesBlockedNames(t *testing.T) {
	origin := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, "should not be reached")
	}))
	defer origin.Close()

	list := filepath.Join(t.TempDir(), "hosts")
	if err := os.WriteFile(list, []byte("0.0.0.0 ads.test\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	socks := fakeSocks(t, map[string]string{
		"ads.test":         hostOf(origin.URL),
		"tracker.ads.test": hostOf(origin.URL),
		"fine.test":        hostOf(origin.URL),
	})
	proxyURL := startProxy(t, socks, list)
	client := &http.Client{Transport: &http.Transport{
		Proxy:           http.ProxyURL(proxyURL),
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}}

	before := stats.dnsBlocked.Load()
	for _, target := range []string{"http://ads.test/", "http://tracker.ads.test/x"} {
		resp, err := client.Get(target)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusForbidden {
			t.Fatalf("%s: got %d, want 403", target, resp.StatusCode)
		}
	}
	// A CONNECT to a blocked name is refused before anything is dialled.
	if _, err := client.Get("https://ads.test/"); err == nil || !strings.Contains(err.Error(), "Forbidden") {
		t.Fatalf("CONNECT to a blocked name: got %v, want Forbidden", err)
	}
	if got := stats.dnsBlocked.Load() - before; got != 3 {
		t.Fatalf("counted %d refusals, want 3", got)
	}

	resp, err := client.Get("http://fine.test/")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != 200 {
		t.Fatalf("an unlisted name got %d", resp.StatusCode)
	}
}

func TestHTTPProxyRejectsRequestsToItself(t *testing.T) {
	socks := fakeSocks(t, nil)
	proxyURL := startProxy(t, socks, "")
	resp, err := http.Get(proxyURL.String() + "/anything")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("got %d, want 400", resp.StatusCode)
	}
}

func TestHTTPProxyReportsAnUnreachableOrigin(t *testing.T) {
	socks := fakeSocks(t, nil)
	proxyURL := startProxy(t, socks, "")
	client := &http.Client{
		Transport: &http.Transport{Proxy: http.ProxyURL(proxyURL)},
		Timeout:   10 * time.Second,
	}
	resp, err := client.Get("http://nowhere.test/")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusBadGateway {
		t.Fatalf("got %d, want 502", resp.StatusCode)
	}
}

func TestHTTPProxyStartsOnceAndStops(t *testing.T) {
	socks := fakeSocks(t, nil)
	port, err := StartHTTPProxy("tcp", socks, "")
	if err != nil {
		t.Fatal(err)
	}
	if HTTPProxyPort() != port {
		t.Fatalf("port %d reported as %d", port, HTTPProxyPort())
	}
	if _, err := StartHTTPProxy("tcp", socks, ""); err == nil {
		t.Fatal("a second proxy started beside the first")
	}
	StopHTTPProxy()
	if HTTPProxyPort() != 0 {
		t.Fatal("still reported as running after stop")
	}
	if _, err := net.DialTimeout("tcp", "127.0.0.1:"+strconv.Itoa(port), time.Second); err == nil {
		t.Fatal("the port still accepts connections after stop")
	}
}
