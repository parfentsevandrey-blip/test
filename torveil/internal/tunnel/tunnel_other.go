//go:build !windows

package tunnel

// Tunnel is the non-Windows placeholder. Proxy mode is fully functional on
// every platform; only the TUN path is Windows-specific, so this file exists
// so the engine, its tests and the development host all build normally.
type Tunnel struct{}

// Start reports that full-tunnel mode is unavailable.
func Start(Options) (*Tunnel, error) { return nil, ErrUnsupported }

// Stop is a no-op.
func (t *Tunnel) Stop() error { return nil }

// Status reports a stopped tunnel.
func (t *Tunnel) Status() Status { return Status{} }

// EngageKillSwitch is unavailable away from Windows.
func (t *Tunnel) EngageKillSwitch() error { return ErrUnsupported }

// RecoverStaleState is a no-op away from Windows.
func RecoverStaleState(string, LogFunc) {}
