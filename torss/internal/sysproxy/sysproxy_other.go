//go:build !windows

package sysproxy

import "torss/internal/config"

func Backup() (*config.SysProxyBackup, error) { return &config.SysProxyBackup{}, nil }
func Set(port int) error                      { return nil }
func Restore(b *config.SysProxyBackup) error  { return nil }
