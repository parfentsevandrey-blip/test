// Package ui is the system tray menu.
package ui

import (
	"fmt"
	"path/filepath"
	"time"

	"fyne.io/systray"

	"torss/internal/config"
	"torss/internal/engine"
	"torss/internal/winutil"
)

// Tray wires the engine to a systray menu.
type Tray struct {
	eng     *engine.Engine
	dataDir string
	cfgPath string
	logf    func(string, ...any)
	onQuit  func()

	status    *systray.MenuItem
	toggle    *systray.MenuItem
	modeItems map[config.Mode]*systray.MenuItem
	newID     *systray.MenuItem
	ks        *systray.MenuItem
	autostart *systray.MenuItem
	resetTor  *systray.MenuItem
}

// New creates the tray controller. onQuit is called after the engine stopped.
func New(eng *engine.Engine, dataDir, cfgPath string, logf func(string, ...any), onQuit func()) *Tray {
	return &Tray{eng: eng, dataDir: dataDir, cfgPath: cfgPath, logf: logf, onQuit: onQuit}
}

// Run blocks until the user quits.
func (t *Tray) Run() {
	systray.Run(t.onReady, func() {})
}

// Update refreshes the icon and status line. Safe from any goroutine.
func (t *Tray) Update(s engine.Status) {
	if t.status == nil {
		return
	}
	switch s.Phase {
	case engine.Connected:
		systray.SetIcon(iconGreen)
	case engine.Connecting:
		systray.SetIcon(iconYellow)
	case engine.Failed:
		systray.SetIcon(iconRed)
	default:
		systray.SetIcon(iconGrey)
	}
	line := fmt.Sprintf("%s: %s", s.Phase, s.Detail)
	if s.Phase == engine.Connecting && s.Progress > 0 && s.Progress < 100 {
		line = fmt.Sprintf("%s [%d%%]: %s", s.Phase, s.Progress, s.Detail)
	}
	t.status.SetTitle(line)
	tip := "TorSS — " + s.Phase.String()
	if s.Phase == engine.Connected {
		tip += " (" + s.Mode.Title() + ")"
	}
	if len(tip) > 120 {
		tip = tip[:120]
	}
	systray.SetTooltip(tip)
	if t.eng.Running() {
		t.toggle.SetTitle("Отключить")
	} else {
		t.toggle.SetTitle("Подключить")
	}
	if s.Phase == engine.Connected && s.Mode.UsesTor() {
		t.newID.Enable()
	} else {
		t.newID.Disable()
	}
	if t.eng.Running() {
		t.resetTor.Disable()
	} else {
		t.resetTor.Enable()
	}
}

func (t *Tray) onReady() {
	systray.SetIcon(iconGrey)
	systray.SetTitle("TorSS")
	systray.SetTooltip("TorSS")

	t.status = systray.AddMenuItem("Отключено: готов", "")
	t.status.Disable()
	systray.AddSeparator()
	t.toggle = systray.AddMenuItem("Подключить", "Подключить / отключить")

	modeMenu := systray.AddMenuItem("Режим", "Способ подключения")
	t.modeItems = map[config.Mode]*systray.MenuItem{}
	cfg := t.eng.Config()
	for _, m := range append([]config.Mode{config.ModeAuto}, config.AllModes...) {
		it := modeMenu.AddSubMenuItemCheckbox(m.Title(), "", cfg.Mode == m)
		t.modeItems[m] = it
		go t.modeLoop(m, it)
	}

	t.newID = systray.AddMenuItem("Новая личность Tor", "Построить новые цепочки")
	t.newID.Disable()
	systray.AddSeparator()

	t.ks = systray.AddMenuItemCheckbox("Kill switch (файрвол)", "Блокировать весь трафик мимо туннеля", cfg.KillSwitch)
	t.autostart = systray.AddMenuItemCheckbox("Автозапуск при входе", "Планировщик задач, с правами администратора", winutil.AutostartEnabled())
	settings := systray.AddMenuItem("Настройки", "")
	openCfg := settings.AddSubMenuItem("Открыть config.json", "")
	openLog := settings.AddSubMenuItem("Открыть лог", "")
	openDir := settings.AddSubMenuItem("Открыть папку данных", "")
	reload := settings.AddSubMenuItem("Перечитать config.json и переподключиться", "")
	t.resetTor = settings.AddSubMenuItem("Сбросить состояние Tor", "Удалить кэш консенсуса и guard-узлов")
	systray.AddSeparator()
	quit := systray.AddMenuItem("Выход", "")

	go func() {
		for {
			select {
			case <-t.toggle.ClickedCh:
				if t.eng.Running() {
					go t.eng.Disconnect()
				} else {
					t.eng.Connect()
				}
			case <-t.newID.ClickedCh:
				if err := t.eng.NewIdentity(); err != nil {
					t.logf("new identity: %v", err)
				}
			case <-t.ks.ClickedCh:
				on := !t.ks.Checked()
				if err := t.eng.SetKillSwitch(on); err != nil {
					t.logf("kill switch: %v", err)
					continue
				}
				if on {
					t.ks.Check()
				} else {
					t.ks.Uncheck()
				}
			case <-t.autostart.ClickedCh:
				on := !t.autostart.Checked()
				if err := winutil.SetAutostart(on); err != nil {
					t.logf("autostart: %v", err)
					winutil.MessageBox("TorSS", "Не удалось изменить автозапуск: "+err.Error(), true)
					continue
				}
				if on {
					t.autostart.Check()
				} else {
					t.autostart.Uncheck()
				}
			case <-openCfg.ClickedCh:
				_ = winutil.OpenPath(t.cfgPath)
			case <-openLog.ClickedCh:
				_ = winutil.OpenPath(filepath.Join(t.dataDir, "torss.log"))
			case <-openDir.ClickedCh:
				_ = winutil.OpenPath(t.dataDir)
			case <-reload.ClickedCh:
				t.reloadConfig()
			case <-t.resetTor.ClickedCh:
				if err := t.eng.ResetTorState(); err != nil {
					winutil.MessageBox("TorSS", err.Error(), true)
				} else {
					winutil.MessageBox("TorSS", "Состояние Tor сброшено. При следующем подключении Tor выберет новые guard-узлы.", false)
				}
			case <-quit.ClickedCh:
				t.status.SetTitle("Выход...")
				done := make(chan struct{})
				go func() { t.eng.Disconnect(); close(done) }()
				select {
				case <-done:
				case <-time.After(20 * time.Second):
				}
				if t.onQuit != nil {
					t.onQuit()
				}
				systray.Quit()
				return
			}
		}
	}()
	t.Update(t.eng.Status())
}

func (t *Tray) modeLoop(m config.Mode, it *systray.MenuItem) {
	for range it.ClickedCh {
		if err := t.eng.SetMode(m); err != nil {
			t.logf("set mode: %v", err)
			continue
		}
		for mm, item := range t.modeItems {
			if mm == m {
				item.Check()
			} else {
				item.Uncheck()
			}
		}
	}
}

func (t *Tray) reloadConfig() {
	cfg, err := config.Load(t.cfgPath)
	if err != nil {
		winutil.MessageBox("TorSS", "Ошибка в config.json:\n"+err.Error(), true)
		return
	}
	old := t.eng.Config()
	*old = *cfg
	for mm, item := range t.modeItems {
		if mm == cfg.Mode {
			item.Check()
		} else {
			item.Uncheck()
		}
	}
	if cfg.KillSwitch {
		t.ks.Check()
	} else {
		t.ks.Uncheck()
	}
	if t.eng.Running() {
		go t.eng.Reconnect()
	}
}
