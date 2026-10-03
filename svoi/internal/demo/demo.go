// Package demo runs a small simulated mesh - laptop, phone, home server and NAS
// - inside one process, on top of the in-process NAT simulator, with sample
// files, mail and chat. It lets anyone try the web interface (and watch NAT
// traversal, relaying and offline/online changes) without four real devices.
package demo

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
	"github.com/parfentsevandrey-blip/test/svoi/internal/web"
)

// Options configure the demo.
type Options struct {
	// Dir holds the data of all four devices (default: a temporary directory).
	Dir string
	// UIPort is the port of the laptop's web interface; the other devices use
	// the following ports.
	UIPort int
	Logger *slog.Logger
	// Quiet disables the periodic simulated activity (used by tests).
	Quiet bool
}

// Device is one simulated device.
type Device struct {
	Name string
	App  *app.App
	UI   *api.Server
}

// LoginURL returns a fresh single-use link (valid ten minutes) that signs a
// browser in to this device's interface. The master token stays in
// <data dir>/ui.token, as on a real device.
func (d *Device) LoginURL() string { return d.UI.URL() }

// LocalLoginURL is LoginURL for a browser this process starts itself: the link goes
// through a command line, so it only works for the user running the process.
func (d *Device) LocalLoginURL() string { return d.UI.LocalURL() }

// Demo is a running simulation.
type Demo struct {
	Devices map[string]*Device
	Net     *netsim.Network
	cancel  context.CancelFunc
	dir     string
	cleanup []func()
}

// Close stops everything.
func (d *Demo) Close() {
	d.cancel()
	for _, dev := range d.Devices {
		dev.UI.Close()
		_ = os.Remove(filepath.Join(dev.App.Dir(), "ui.addr"))
		dev.App.Close()
	}
	for _, f := range d.cleanup {
		f()
	}
}

// Start builds the simulated network, brings the four devices up, joins them
// into one mesh and seeds sample content.
func Start(ctx context.Context, opts Options) (*Demo, error) {
	if opts.UIPort == 0 {
		opts.UIPort = 8777
	}
	if opts.Dir == "" {
		dir, err := os.MkdirTemp("", "themesh-demo-")
		if err != nil {
			return nil, err
		}
		opts.Dir = dir
	}
	if opts.Logger == nil {
		opts.Logger = slog.New(slog.NewTextHandler(io.Discard, nil))
	}
	ctx, cancel := context.WithCancel(ctx)
	d := &Demo{Devices: map[string]*Device{}, cancel: cancel, dir: opts.Dir}

	// The Internet: a public home server, a home router (cone NAT) with the
	// laptop and the NAS behind it, and a phone on a mobile carrier (symmetric
	// NAT: nothing can punch through it, so its traffic is relayed).
	nw := netsim.New()
	nw.SetLatency(7 * time.Millisecond)
	d.Net = nw
	inet := nw.Internet()
	homeRouter := inet.NewNAT(netip.MustParseAddr("203.0.113.1"), netsim.PortRestricted)
	carrier := inet.NewNAT(netip.MustParseAddr("203.0.113.3"), netsim.Symmetric)
	hosts := map[string]*netsim.Host{
		"home-server": inet.NewHost(netip.MustParseAddr("203.0.113.10")),
		"laptop":      homeRouter.Inner().NewHost(netip.MustParseAddr("192.168.1.20")),
		"nas":         homeRouter.Inner().NewHost(netip.MustParseAddr("192.168.1.30")),
		"phone":       carrier.Inner().NewHost(netip.MustParseAddr("10.20.0.5")),
	}
	platform := map[string]string{
		"home-server": "linux/amd64", "laptop": "darwin/arm64", "nas": "linux/arm64", "phone": "android/arm64",
	}
	owner := map[string]string{"home-server": "Андрей", "laptop": "Андрей", "nas": "Андрей", "phone": "Анна"}
	order := []string{"home-server", "laptop", "nas", "phone"}

	// The laptop is "this device": its interface gets the first port.
	uiOffset := map[string]int{"laptop": 0, "phone": 1, "nas": 2, "home-server": 3}
	for _, name := range order {
		host := hosts[name]
		a, err := app.Open(app.Options{
			Dir:    filepath.Join(opts.Dir, name, "data"), // keys and settings; never inside a shared folder
			Logger: opts.Logger.With("device", name),
			Mesh: mesh.Config{
				Listen:     func(port int) (net.PacketConn, error) { return host.ListenPacket(uint16(port)) },
				LocalAddrs: func() []netip.Addr { return host.Addrs() },
				LANPort:    -1,
				Platform:   platform[name],
			},
			DeviceName: name,
			Owner:      owner[name],
		})
		if err != nil {
			d.Close()
			return nil, err
		}
		off := false
		dl := filepath.Join(opts.Dir, name, "Downloads")
		if _, err := a.UpdateSettings(app.SettingsPatch{STUNEnabled: &off, PortMap: &off, DownloadDir: &dl}); err != nil {
			d.Close()
			return nil, err
		}
		srv := api.New(a, web.UI())
		ln, err := srv.Listen(fmt.Sprintf("127.0.0.1:%d", opts.UIPort+uiOffset[name]), true)
		if err != nil {
			d.Close()
			return nil, fmt.Errorf("cannot start the interface of %s: %w", name, err)
		}
		go srv.Serve(ln)
		// Like `themesh up`: `themesh open --dir <this data dir>` finds the device.
		_ = os.WriteFile(filepath.Join(a.Dir(), "ui.addr"), []byte(ln.Addr().String()+"\n"), 0o600)
		d.Devices[name] = &Device{Name: name, App: a, UI: srv}
	}
	// One mesh: the home server founds it; the laptop joins as an administrator
	// (this is "the user's device"); the NAS and the phone join normally.
	hs := d.Devices["home-server"].App
	if err := hs.CreateMesh("Дом", "home-server", "Андрей"); err != nil {
		d.Close()
		return nil, err
	}
	join := func(name string, admin bool) error {
		inv, err := hs.NewInvite(admin, 10*time.Minute, owner[name])
		if err != nil {
			return err
		}
		jctx, cancelJoin := context.WithTimeout(ctx, 30*time.Second)
		defer cancelJoin()
		return d.Devices[name].App.JoinMesh(jctx, inv.Code, name)
	}
	for _, step := range []struct {
		name  string
		admin bool
	}{{"laptop", true}, {"nas", false}, {"phone", false}} {
		if err := join(step.name, step.admin); err != nil {
			d.Close()
			return nil, fmt.Errorf("demo: %s could not join: %w", step.name, err)
		}
	}

	if err := d.seed(ctx, opts); err != nil {
		d.Close()
		return nil, err
	}
	if !opts.Quiet {
		go d.life(ctx)
	}
	return d, nil
}

// seed gives the devices content: shares, services, mail, chat and an offer.
func (d *Demo) seed(ctx context.Context, opts Options) error {
	nas, hs, laptop, phone := d.Devices["nas"].App, d.Devices["home-server"].App, d.Devices["laptop"].App, d.Devices["phone"].App

	photos, music, docs, err := seedNAS(filepath.Join(opts.Dir, "nas", "storage"))
	if err != nil {
		return err
	}
	for _, s := range []files.Share{
		{Name: "Фото", Path: photos, Mode: "ro", Allow: []string{"*"}},
		{Name: "Музыка", Path: music, Mode: "ro", Allow: []string{"*"}},
		{Name: "Документы", Path: docs, Mode: "rw", Allow: []string{"*"}},
	} {
		if _, err := nas.SaveShare("", s); err != nil {
			return err
		}
	}
	// A few real TCP services on this machine, published by the home server and the NAS.
	sshAddr, err := d.serve(banner("SSH-2.0-themesh-demo\r\n"))
	if err != nil {
		return err
	}
	webAddr, err := d.serve(webPage("Домашняя страница (демо)"))
	if err != nil {
		return err
	}
	nasWeb, err := d.serve(webPage("Панель NAS (демо)"))
	if err != nil {
		return err
	}
	for _, s := range []struct {
		app *app.App
		sv  [3]string
	}{
		{hs, [3]string{"ssh", sshAddr, "Удалённый терминал"}},
		{hs, [3]string{"web", webAddr, "Домашний сайт"}},
		{nas, [3]string{"nas-ui", nasWeb, "Веб-панель NAS"}},
	} {
		if _, err := s.app.SaveService("", servicesFrom(s.sv)); err != nil {
			return err
		}
	}

	// Wait for everyone to see everyone, then seed conversations.
	wctx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()
	if err := d.waitConnected(wctx); err != nil {
		return err
	}
	lp := laptop.Node().ID()
	phoneID, nasID, hsID := phone.Node().ID(), nas.Node().ID(), hs.Node().ID()
	chat := func(from *app.App, to identity.ID, text string) {
		_, _ = from.Mail().Send(mail.SendInput{Kind: "chat", To: []identity.ID{to}, Body: text})
		time.Sleep(1100 * time.Millisecond) // distinct timestamps
	}
	chat(phone, lp, "Привет! Я скинула фотографии с дачи 🌿")
	chat(laptop, phoneID, "Ура, посмотрю вечером")
	chat(phone, lp, "Ужин сегодня в 19:00?")
	chat(hs, lp, "Сертификат сайта обновлён, всё работает")

	send := func(from *app.App, to identity.ID, subject, body string, attach ...string) {
		_, _ = from.Mail().Send(mail.SendInput{Kind: "mail", To: []identity.ID{to}, Subject: subject, Body: body, Attach: attach})
	}
	report := d.blob(nas, "backup-report.txt", []byte("Резервное копирование завершено\n\nФото: 1 284 файла, 8,4 ГБ\nДокументы: 312 файлов, 0,3 ГБ\nОшибок: 0\n"))
	send(nas, lp, "Отчёт резервного копирования", "Привет!\n\nНочная копия завершилась успешно, отчёт во вложении.\nСвободно на диске: 1,8 ТБ.\n\n— NAS", report)
	send(hs, lp, "Продление домена", "Домен home.example оплачен до 2027 года.\nСсылка на квитанцию: https://example.com/receipt/8841")
	send(phone, lp, "Список покупок", "Молоко, хлеб, кофе, яблоки.\nПожалуйста, не забудь зарядку для ноутбука.")
	_ = nasID
	_ = hsID

	// An incoming file offer waiting for the laptop's decision: the phone belongs
	// to Анна, so it is not auto-accepted (auto-accept covers one's own devices).
	pic := filepath.Join(opts.Dir, "phone", "IMG_20241005_dacha.png")
	if err := writeFile(pic, scenePNG(1280, 853, 99, true)); err != nil {
		return err
	}
	if _, err := phone.Files().SendPath([]identity.ID{lp}, pic); err != nil {
		return err
	}
	return nil
}

func (d *Demo) blob(a *app.App, name string, data []byte) string {
	sha, size, err := a.Blobs().Put(bytesReader(data))
	if err != nil {
		return ""
	}
	_ = a.Mail().RegisterUpload(sha, name, "text/plain; charset=utf-8", size)
	return sha
}

func (d *Demo) waitConnected(ctx context.Context) error {
	for {
		all := true
		for _, a := range d.Devices {
			for _, other := range d.Devices {
				if a == other {
					continue
				}
				p := a.App.Node().Peer(other.App.Node().ID())
				if p == nil || !p.Online() {
					all = false
				}
			}
		}
		if all {
			return nil
		}
		select {
		case <-ctx.Done():
			return fmt.Errorf("demo: the simulated devices did not connect in time")
		case <-time.After(300 * time.Millisecond):
		}
	}
}

// serve starts a throw-away TCP service on loopback and returns its address.
func (d *Demo) serve(h func(net.Conn)) (string, error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return "", err
	}
	d.cleanup = append(d.cleanup, func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go h(c)
		}
	}()
	return ln.Addr().String(), nil
}

func banner(text string) func(net.Conn) {
	return func(c net.Conn) {
		defer c.Close()
		_, _ = c.Write([]byte(text))
		_, _ = io.Copy(c, c)
	}
}

func webPage(title string) func(net.Conn) {
	return func(c net.Conn) {
		defer c.Close()
		_ = c.SetDeadline(time.Now().Add(10 * time.Second))
		buf := make([]byte, 2048)
		_, _ = c.Read(buf)
		body := fmt.Sprintf("<!doctype html><meta charset=utf-8><title>%s</title><body style=\"font:20px system-ui;margin:3rem\"><h1>%s</h1><p>Эту страницу отдал сервис, опубликованный через themesh.</p></body>", title, title)
		fmt.Fprintf(c, "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s", len(body), body)
	}
}
