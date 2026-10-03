//go:build !linux

package tun

import (
	"errors"
	"net"
)

type device struct{}

func createDevice(name string) (*device, error) {
	return nil, errors.New("TUN mode is only available on Linux for now; use port forwards or the SOCKS5 proxy on this system")
}

func (d *device) Name() string                { return "" }
func (d *device) Read(p []byte) (int, error)  { return 0, errors.New("unsupported") }
func (d *device) Write(p []byte) (int, error) { return 0, errors.New("unsupported") }
func (d *device) Close() error                { return nil }
func (d *device) configure(v4, v6 *net.IPNet, mtu int) (bool, error) {
	return false, errors.New("unsupported")
}

// Supported reports whether TUN mode exists on this platform.
const Supported = false
