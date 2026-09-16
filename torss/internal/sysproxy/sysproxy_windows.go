//go:build windows

// Package sysproxy toggles the Windows (WinINET) system proxy.
package sysproxy

import (
	"fmt"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"

	"torss/internal/config"
)

const keyPath = `Software\Microsoft\Windows\CurrentVersion\Internet Settings`

var (
	wininet                       = windows.NewLazySystemDLL("wininet.dll")
	procInternetSetOption         = wininet.NewProc("InternetSetOptionW")
	internetOptionSettingsChanged = uintptr(39)
	internetOptionRefresh         = uintptr(37)
)

func notify() {
	if err := procInternetSetOption.Find(); err != nil {
		return
	}
	procInternetSetOption.Call(0, internetOptionSettingsChanged, 0, 0)
	procInternetSetOption.Call(0, internetOptionRefresh, 0, 0)
}

// Backup reads the current proxy settings.
func Backup() (*config.SysProxyBackup, error) {
	k, err := registry.OpenKey(registry.CURRENT_USER, keyPath, registry.QUERY_VALUE)
	if err != nil {
		return nil, err
	}
	defer k.Close()
	b := &config.SysProxyBackup{}
	if v, _, err := k.GetIntegerValue("ProxyEnable"); err == nil {
		b.Enable = uint32(v)
	}
	b.Server, _, _ = k.GetStringValue("ProxyServer")
	b.Override, _, _ = k.GetStringValue("ProxyOverride")
	return b, nil
}

// Set points the system proxy at 127.0.0.1:port (HTTP+HTTPS, sing-box mixed inbound).
func Set(port int) error {
	k, err := registry.OpenKey(registry.CURRENT_USER, keyPath, registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer k.Close()
	if err := k.SetDWordValue("ProxyEnable", 1); err != nil {
		return err
	}
	if err := k.SetStringValue("ProxyServer", fmt.Sprintf("127.0.0.1:%d", port)); err != nil {
		return err
	}
	if err := k.SetStringValue("ProxyOverride", "<local>;localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.2*;172.30.*;172.31.*;192.168.*"); err != nil {
		return err
	}
	notify()
	return nil
}

// Restore puts the backed-up values back (or disables the proxy if b is nil).
func Restore(b *config.SysProxyBackup) error {
	k, err := registry.OpenKey(registry.CURRENT_USER, keyPath, registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer k.Close()
	if b == nil {
		b = &config.SysProxyBackup{}
	}
	if err := k.SetDWordValue("ProxyEnable", b.Enable); err != nil {
		return err
	}
	if b.Server == "" {
		_ = k.DeleteValue("ProxyServer")
	} else if err := k.SetStringValue("ProxyServer", b.Server); err != nil {
		return err
	}
	if b.Override == "" {
		_ = k.DeleteValue("ProxyOverride")
	} else if err := k.SetStringValue("ProxyOverride", b.Override); err != nil {
		return err
	}
	notify()
	return nil
}
