// Package mailgw is the mail gateway of a device: the part of a mesh that speaks Internet mail. It receives letters for the
// mailboxes of a domain (SMTP), turns them into letters of the mesh (internal/mail), and sends the letters that the devices write
// to addresses on the Internet (internal/inetmail). It is off until its owner sets it up, and it never relays: it takes letters for
// its own domain only and sends only what a device of the mesh, allowed to use the mailbox, wrote.
package mailgw

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
)

// Mailbox is an address of the domain and the devices that have it: they receive what comes to it and may write from it.
type Mailbox struct {
	Name    string        `json:"name"` // "andrey": the part before @
	Devices []identity.ID `json:"devices"`
}

// RelayConfig is a mail service the letters go out through (see inetmail.Relay). The password is never sent back to the interface.
type RelayConfig struct {
	Host       string `json:"host"`
	Port       int    `json:"port"`
	Username   string `json:"username,omitempty"`
	Password   string `json:"password,omitempty"`
	Mode       string `json:"mode"`                 // "starttls" (587), "tls" (465) or "plain"
	SPFInclude string `json:"spfInclude,omitempty"` // what to include in the SPF record of the domain for this service
}

// Config is the setup of the gateway. It lives in <data dir>/mailgw/config.json (private to the user).
type Config struct {
	Enabled      bool         `json:"enabled"`
	Domain       string       `json:"domain"`
	Host         string       `json:"host,omitempty"`   // the name of the mail server (the MX record points at it); mail.<domain> by default
	Listen       string       `json:"listen,omitempty"` // where the SMTP server listens; ":25" by default
	Mailboxes    []Mailbox    `json:"mailboxes"`
	DKIMSelector string       `json:"dkimSelector,omitempty"`
	Relay        *RelayConfig `json:"relay,omitempty"`
	// PublicIPv4 is the address the Internet sees the gateway at, when the node cannot tell by itself.
	PublicIPv4 string `json:"publicIPv4,omitempty"`
}

// DefaultListen is the port that mail servers talk to each other on.
const DefaultListen = ":25"

func (c Config) host() string {
	if c.Host != "" {
		return c.Host
	}
	if c.Domain != "" {
		return "mail." + c.Domain
	}
	return ""
}

func (c Config) listen() string {
	if c.Listen != "" {
		return c.Listen
	}
	return DefaultListen
}

func (c Config) selector() string {
	if c.DKIMSelector != "" {
		return c.DKIMSelector
	}
	return "mesh"
}

// mailbox finds a mailbox by name (case does not matter); postmaster and abuse go to the first mailbox, as RFC 5321 wants.
func (c Config) mailbox(name string) (Mailbox, bool) {
	name = strings.ToLower(name)
	for _, mb := range c.Mailboxes {
		if mb.Name == name {
			return mb, true
		}
	}
	if (name == "postmaster" || name == "abuse") && len(c.Mailboxes) > 0 {
		return c.Mailboxes[0], true
	}
	return Mailbox{}, false
}

// addressesOf lists the addresses of the mailboxes that a device has.
func (c Config) addressesOf(dev identity.ID) []string {
	var out []string
	for _, mb := range c.Mailboxes {
		for _, d := range mb.Devices {
			if d == dev {
				out = append(out, mb.Name+"@"+c.Domain)
				break
			}
		}
	}
	return out
}

// Validate cleans a configuration and says what is wrong with it. known tells whether a device is a member of the mesh.
func (c *Config) Validate(known func(identity.ID) bool) error {
	if c.Domain != "" {
		d, err := inetmail.CleanDomain(c.Domain)
		if err != nil {
			return err
		}
		c.Domain = d
	}
	if c.Host != "" {
		h, err := inetmail.CleanDomain(c.Host)
		if err != nil {
			return fmt.Errorf("the name of the mail server: %w", err)
		}
		c.Host = h
	}
	if c.Listen != "" {
		if _, port, err := net.SplitHostPort(c.Listen); err != nil {
			return errors.New("the address the server listens on is host:port, for example :25")
		} else if n, err := strconv.Atoi(port); err != nil || n < 0 || n > 65535 { // (0: any free port, which is what the tests use)
			return errors.New("the port the server listens on is a number from 1 to 65535")
		}
	}
	if c.DKIMSelector != "" {
		c.DKIMSelector = strings.ToLower(c.DKIMSelector)
		for _, r := range c.DKIMSelector {
			if !(r >= 'a' && r <= 'z' || r >= '0' && r <= '9' || r == '-') || len(c.DKIMSelector) > 40 {
				return errors.New("the selector of the signature is letters, digits and dashes")
			}
		}
	}
	if c.PublicIPv4 != "" {
		ip := net.ParseIP(strings.TrimSpace(c.PublicIPv4))
		if ip == nil || ip.To4() == nil {
			return errors.New("the public address is an IPv4 address like 203.0.113.7")
		}
		c.PublicIPv4 = ip.String()
	}
	seen := map[string]bool{}
	for i := range c.Mailboxes {
		mb := &c.Mailboxes[i]
		name, err := inetmail.CleanMailboxName(mb.Name)
		if err != nil {
			return err
		}
		if seen[name] {
			return fmt.Errorf("the mailbox %q is listed twice", name)
		}
		seen[name] = true
		mb.Name = name
		if len(mb.Devices) == 0 {
			return fmt.Errorf("the mailbox %q has no devices: choose who receives its mail", name)
		}
		var devs []identity.ID
		dup := map[identity.ID]bool{}
		for _, d := range mb.Devices {
			if dup[d] {
				continue
			}
			dup[d] = true
			if known != nil && !known(d) {
				return fmt.Errorf("the mailbox %q names a device that is not in the mesh", name)
			}
			devs = append(devs, d)
		}
		sort.Slice(devs, func(a, b int) bool { return devs[a].String() < devs[b].String() })
		mb.Devices = devs
	}
	if r := c.Relay; r != nil {
		r.Host = strings.TrimSpace(r.Host)
		if r.Host == "" {
			c.Relay = nil
		} else {
			if _, err := inetmail.CleanDomain(r.Host); err != nil && net.ParseIP(r.Host) == nil {
				return fmt.Errorf("the mail service: %w", err)
			}
			if r.Mode == "" {
				r.Mode = "starttls"
			}
			switch r.Mode {
			case "starttls", "tls", "plain":
			default:
				return errors.New("the connection to the mail service is starttls, tls or plain")
			}
			if r.Port == 0 {
				r.Port = map[string]int{"starttls": 587, "tls": 465, "plain": 25}[r.Mode]
			}
			if r.Port < 1 || r.Port > 65535 {
				return errors.New("the port of the mail service is a number from 1 to 65535")
			}
			if r.SPFInclude != "" {
				d, err := inetmail.CleanDNSName(r.SPFInclude)
				if err != nil {
					return fmt.Errorf("the SPF name of the mail service: %w", err)
				}
				r.SPFInclude = d
			}
		}
	}
	if c.Enabled {
		if c.Domain == "" {
			return errors.New("name the domain the mailboxes are on")
		}
		if len(c.Mailboxes) == 0 {
			return errors.New("make at least one mailbox")
		}
	}
	return nil
}

// ---- files ----

func configPath(dir string) string { return filepath.Join(dir, "config.json") }

func loadConfig(dir string) (Config, error) {
	b, err := os.ReadFile(configPath(dir))
	if errors.Is(err, os.ErrNotExist) {
		return Config{}, nil
	}
	if err != nil {
		return Config{}, err
	}
	var c Config
	if err := json.Unmarshal(b, &c); err != nil {
		return Config{}, fmt.Errorf("mailgw: %s is not readable: %w", configPath(dir), err)
	}
	return c, nil
}

func saveConfig(dir string, c Config) error {
	b, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return identity.WriteFileAtomic(configPath(dir), append(b, '\n'), 0o600)
}

func dkimPath(dir, selector string) string { return filepath.Join(dir, "dkim-"+selector+".pem") }

// loadOrCreateKey returns the key that signs the letters of the domain, making it on the first use.
func loadOrCreateKey(dir, selector string) (*inetmail.DKIMKey, error) {
	path := dkimPath(dir, selector)
	if b, err := os.ReadFile(path); err == nil {
		return inetmail.ParseDKIMKey(selector, b)
	}
	k, err := inetmail.NewDKIMKey(selector)
	if err != nil {
		return nil, err
	}
	if err := identity.WriteFileAtomic(path, k.PEM(), 0o600); err != nil {
		return nil, err
	}
	return k, nil
}
