//go:build windows

package tunnel

import (
	"errors"
	"fmt"
	"net/netip"
	"sort"
	"strings"
	"sync"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
	"golang.zx2c4.com/wireguard/windows/tunnel/winipcfg"
)

var (
	modIPHlpAPI             = windows.NewLazySystemDLL("iphlpapi.dll")
	procGetExtendedTCPTable = modIPHlpAPI.NewProc("GetExtendedTcpTable")
)

// TCP_TABLE_OWNER_PID_ALL from iprtrmib.h.
const tcpTableOwnerPIDAll = 5

// mibTCPRowOwnerPID mirrors MIB_TCPROW_OWNER_PID. Addresses and ports are in
// network byte order.
type mibTCPRowOwnerPID struct {
	State      uint32
	LocalAddr  uint32
	LocalPort  uint32
	RemoteAddr uint32
	RemotePort uint32
	OwningPID  uint32
}

// bypassPollInterval is how often the watcher looks for new connections.
//
// A connection opened between two polls is briefly routed into the tunnel and
// its first SYN is lost. TCP retransmits, and by then the bypass route exists,
// so the connection establishes a moment late instead of failing.
const bypassPollInterval = 250 * time.Millisecond

// bypassWatcher keeps Tor's own traffic off the tunnel Tor is carrying.
//
// The problem it solves is circular: once the default route points at the TUN
// adapter, tor.exe's connection to its guard would itself be routed into the
// tunnel, which cannot carry it because the tunnel depends on that very
// connection. So every address tor and its pluggable transport talk to needs a
// host route back out over the physical interface.
//
// Those addresses cannot be known in advance — a bridge is chosen at runtime,
// and Snowflake's rendezvous is a CDN — so the watcher discovers them by
// asking Windows which remote endpoints those processes currently hold.
//
// This covers TCP. Windows exposes no remote address for UDP sockets, so
// Snowflake's WebRTC transport cannot be discovered this way; see the
// limitation documented in README.md.
type bypassWatcher struct {
	processNames map[string]bool
	luid         winipcfg.LUID
	gateway      netip.Addr
	tunnelSubnet netip.Prefix
	log          LogFunc

	mu    sync.Mutex
	added map[netip.Prefix]bool

	stopCh chan struct{}
	wg     sync.WaitGroup
	once   sync.Once
}

func newBypassWatcher(names []string, luid winipcfg.LUID, gateway netip.Addr, tunnelSubnet netip.Prefix, log LogFunc) *bypassWatcher {
	set := make(map[string]bool, len(names))
	for _, n := range names {
		set[strings.ToLower(n)] = true
	}
	return &bypassWatcher{
		processNames: set,
		luid:         luid,
		gateway:      gateway,
		tunnelSubnet: tunnelSubnet,
		log:          log,
		added:        make(map[netip.Prefix]bool),
		stopCh:       make(chan struct{}),
	}
}

func (w *bypassWatcher) logf(level, format string, args ...any) {
	if w.log != nil {
		w.log(level, fmt.Sprintf(format, args...))
	}
}

func (w *bypassWatcher) start() {
	w.wg.Add(1)
	go w.run()
}

func (w *bypassWatcher) run() {
	defer w.wg.Done()
	ticker := time.NewTicker(bypassPollInterval)
	defer ticker.Stop()
	for {
		w.sweep()
		select {
		case <-w.stopCh:
			return
		case <-ticker.C:
		}
	}
}

// sweep adds a host route for every new remote endpoint the watched processes
// hold.
func (w *bypassWatcher) sweep() {
	pids, err := processIDsByName(w.processNames)
	if err != nil || len(pids) == 0 {
		return
	}
	addrs, err := remoteTCPAddrsForPIDs(pids)
	if err != nil {
		return
	}
	for _, addr := range addrs {
		w.addRoute(addr)
	}
}

// AddStatic pins a bypass route that is known up front, such as a configured
// bridge address.
func (w *bypassWatcher) AddStatic(prefix netip.Prefix) {
	w.addPrefix(prefix)
}

func (w *bypassWatcher) addRoute(addr netip.Addr) {
	if !addr.Is4() || addr.IsLoopback() || addr.IsUnspecified() || addr.IsMulticast() || addr.IsLinkLocalUnicast() {
		return
	}
	if w.tunnelSubnet.IsValid() && w.tunnelSubnet.Contains(addr) {
		return
	}
	w.addPrefix(netip.PrefixFrom(addr, 32))
}

func (w *bypassWatcher) addPrefix(prefix netip.Prefix) {
	if !prefix.IsValid() {
		return
	}
	w.mu.Lock()
	if w.added[prefix] {
		w.mu.Unlock()
		return
	}
	w.added[prefix] = true
	w.mu.Unlock()

	if err := w.luid.AddRoute(prefix, w.gateway, 0); err != nil {
		// ERROR_OBJECT_ALREADY_EXISTS is expected when a route already
		// covers the address; anything else is worth knowing about.
		if !isAlreadyExists(err) {
			w.logf("warn", "add bypass route %s: %v", prefix, err)
		}
		return
	}
	w.logf("info", "bypass route added for %s (Tor's own traffic)", prefix)
}

// routes returns the bypass routes currently installed, for display.
func (w *bypassWatcher) routes() []string {
	w.mu.Lock()
	defer w.mu.Unlock()
	out := make([]string, 0, len(w.added))
	for p := range w.added {
		out = append(out, p.String())
	}
	sort.Strings(out)
	return out
}

// stop halts the watcher and removes every route it installed.
func (w *bypassWatcher) stop() {
	w.once.Do(func() { close(w.stopCh) })
	w.wg.Wait()

	w.mu.Lock()
	prefixes := make([]netip.Prefix, 0, len(w.added))
	for p := range w.added {
		prefixes = append(prefixes, p)
	}
	w.added = make(map[netip.Prefix]bool)
	w.mu.Unlock()

	for _, p := range prefixes {
		if err := w.luid.DeleteRoute(p, w.gateway); err != nil && !isNotFound(err) {
			w.logf("warn", "remove bypass route %s: %v", p, err)
		}
	}
}

// processIDsByName returns the PIDs of running processes whose executable name
// is in names (compared case-insensitively).
func processIDsByName(names map[string]bool) (map[uint32]bool, error) {
	if len(names) == 0 {
		return nil, nil
	}
	snap, err := windows.CreateToolhelp32Snapshot(windows.TH32CS_SNAPPROCESS, 0)
	if err != nil {
		return nil, fmt.Errorf("process snapshot: %w", err)
	}
	defer windows.CloseHandle(snap)

	var entry windows.ProcessEntry32
	entry.Size = uint32(unsafe.Sizeof(entry))
	if err := windows.Process32First(snap, &entry); err != nil {
		return nil, fmt.Errorf("enumerate processes: %w", err)
	}

	pids := make(map[uint32]bool)
	for {
		name := strings.ToLower(windows.UTF16ToString(entry.ExeFile[:]))
		if names[name] {
			pids[entry.ProcessID] = true
		}
		if err := windows.Process32Next(snap, &entry); err != nil {
			break
		}
	}
	return pids, nil
}

// remoteTCPAddrsForPIDs returns the remote addresses of IPv4 TCP connections
// owned by the given processes.
func remoteTCPAddrsForPIDs(pids map[uint32]bool) ([]netip.Addr, error) {
	var size uint32
	// First call learns the required buffer size.
	_, _, _ = procGetExtendedTCPTable.Call(
		0,
		uintptr(unsafe.Pointer(&size)),
		0,
		uintptr(windows.AF_INET),
		uintptr(tcpTableOwnerPIDAll),
		0,
	)
	if size == 0 {
		return nil, nil
	}

	buf := make([]byte, size)
	ret, _, _ := procGetExtendedTCPTable.Call(
		uintptr(unsafe.Pointer(&buf[0])),
		uintptr(unsafe.Pointer(&size)),
		0,
		uintptr(windows.AF_INET),
		uintptr(tcpTableOwnerPIDAll),
		0,
	)
	if ret != 0 {
		return nil, fmt.Errorf("GetExtendedTcpTable: %w", windows.Errno(ret))
	}

	count := *(*uint32)(unsafe.Pointer(&buf[0]))
	rowSize := unsafe.Sizeof(mibTCPRowOwnerPID{})
	if uintptr(len(buf)) < unsafe.Sizeof(uint32(0))+uintptr(count)*rowSize {
		return nil, fmt.Errorf("GetExtendedTcpTable returned a short table")
	}
	rows := unsafe.Slice((*mibTCPRowOwnerPID)(unsafe.Pointer(&buf[4])), count)

	var out []netip.Addr
	for i := range rows {
		row := &rows[i]
		if !pids[row.OwningPID] || row.RemoteAddr == 0 {
			continue
		}
		out = append(out, addrFromDWORD(row.RemoteAddr))
	}
	return out, nil
}

// addrFromDWORD converts an in_addr (network byte order in a little-endian
// DWORD) into a netip.Addr.
func addrFromDWORD(v uint32) netip.Addr {
	return netip.AddrFrom4([4]byte{byte(v), byte(v >> 8), byte(v >> 16), byte(v >> 24)})
}

func isAlreadyExists(err error) bool {
	var errno windows.Errno
	if errors.As(err, &errno) {
		return errno == windows.ERROR_OBJECT_ALREADY_EXISTS
	}
	return strings.Contains(strings.ToLower(err.Error()), "already exists")
}

func isNotFound(err error) bool {
	var errno windows.Errno
	if errors.As(err, &errno) {
		return errno == windows.ERROR_NOT_FOUND || errno == windows.ERROR_FILE_NOT_FOUND
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "not found") || strings.Contains(msg, "cannot find")
}
