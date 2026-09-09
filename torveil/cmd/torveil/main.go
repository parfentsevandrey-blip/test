// Command torveil is the TorVeil desktop application: a Windows client that
// routes traffic over Tor with Snowflake obfuscation, user-controlled
// multi-hop circuits, and DAITA-style traffic shaping.
package main

import (
	"embed"
	"log"

	"github.com/parfentsevandrey-blip/torveil/internal/config"
	"github.com/parfentsevandrey-blip/torveil/internal/core"
	"github.com/parfentsevandrey-blip/torveil/internal/logging"
	"github.com/parfentsevandrey-blip/torveil/internal/tunnel"
	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	"github.com/wailsapp/wails/v2/pkg/options/windows"
)

//go:embed all:frontend/dist
var assets embed.FS

func main() {
	logs := logging.New(4000)

	cfg, err := config.Load()
	if err != nil {
		// A broken configuration file should not stop the application: it
		// falls back to defaults and says so, so the user can fix or delete it.
		logs.Logf("warn", "%v (continuing with default settings)", err)
	}

	// A previous run that was killed rather than closed may have left firewall
	// rules behind. Clearing them here means a crash costs connectivity once,
	// not until someone finds the rules by hand.
	tunnel.RecoverStaleState(cfg.DataDir, logs.Func())

	engine := core.New(cfg, logs)
	app := NewApp(engine, logs)

	logs.Log("info", "TorVeil started")

	err = wails.Run(&options.App{
		Title:            "TorVeil",
		Width:            1180,
		Height:           780,
		MinWidth:         940,
		MinHeight:        620,
		AssetServer:      &assetserver.Options{Assets: assets},
		BackgroundColour: &options.RGBA{R: 14, G: 16, B: 22, A: 1},
		OnStartup:        app.startup,
		OnShutdown:       app.shutdown,
		Bind:             []any{app},
		Windows: &windows.Options{
			WebviewIsTransparent: false,
			WindowIsTranslucent:  false,
		},
	})
	if err != nil {
		log.Fatalf("TorVeil failed to start: %v", err)
	}
}
