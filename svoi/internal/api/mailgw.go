package api

import (
	"net/http"
	"strings"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mailgw"
)

// The mail gateway of the device (Internet mail). Reading and changing it is what the setup screen "Own address" does; an
// administrator can do it for another device through /api/d/{peer}/mailgw (see remote.go).

type mailgwBox struct {
	Name    string   `json:"name"`
	Address string   `json:"address,omitempty"`
	Devices []string `json:"devices"`
}

type mailgwRelay struct {
	Host        string `json:"host"`
	Port        int    `json:"port"`
	Username    string `json:"username,omitempty"`
	Password    string `json:"password,omitempty"` // only ever sent to the device, never from it
	PasswordSet bool   `json:"passwordSet,omitempty"`
	Mode        string `json:"mode"`
	SPFInclude  string `json:"spfInclude,omitempty"`
}

type mailgwBody struct {
	Enabled      bool         `json:"enabled"`
	Domain       string       `json:"domain"`
	Host         string       `json:"host"`
	Listen       string       `json:"listen"`
	DKIMSelector string       `json:"dkimSelector"`
	PublicIPv4   string       `json:"publicIPv4"`
	Mailboxes    []mailgwBox  `json:"mailboxes"`
	Relay        *mailgwRelay `json:"relay"`
}

type mailgwView struct {
	mailgwBody
	Status   mailgw.Status `json:"status"`
	Defaults struct {
		Host   string `json:"host"`
		Listen string `json:"listen"`
	} `json:"defaults"`
}

func (s *Server) mailgwView() mailgwView {
	g := s.app.MailGW()
	c := g.Config()
	v := mailgwView{Status: g.Status()}
	v.Enabled, v.Domain, v.Host, v.Listen, v.DKIMSelector, v.PublicIPv4 = c.Enabled, c.Domain, c.Host, c.Listen, c.DKIMSelector, c.PublicIPv4
	v.Mailboxes = []mailgwBox{}
	for _, mb := range c.Mailboxes {
		b := mailgwBox{Name: mb.Name, Devices: []string{}}
		if c.Domain != "" {
			b.Address = mb.Name + "@" + c.Domain
		}
		for _, d := range mb.Devices {
			b.Devices = append(b.Devices, d.String())
		}
		v.Mailboxes = append(v.Mailboxes, b)
	}
	if r := c.Relay; r != nil {
		v.Relay = &mailgwRelay{Host: r.Host, Port: r.Port, Username: r.Username, PasswordSet: g.RelayPasswordSet(), Mode: r.Mode, SPFInclude: r.SPFInclude}
	}
	v.Defaults.Host, v.Defaults.Listen = "mail.<domain>", mailgw.DefaultListen
	if c.Domain != "" {
		v.Defaults.Host = "mail." + c.Domain
	}
	return v
}

func (s *Server) handleMailGWGet(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, s.mailgwView())
}

func (s *Server) handleMailGWPut(w http.ResponseWriter, r *http.Request) {
	var in mailgwBody
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	c := mailgw.Config{
		Enabled: in.Enabled, Domain: strings.TrimSpace(in.Domain), Host: strings.TrimSpace(in.Host), Listen: strings.TrimSpace(in.Listen),
		DKIMSelector: strings.TrimSpace(in.DKIMSelector), PublicIPv4: strings.TrimSpace(in.PublicIPv4),
	}
	for _, b := range in.Mailboxes {
		mb := mailgw.Mailbox{Name: b.Name}
		for _, d := range b.Devices {
			id, err := s.app.PeerID(d)
			if err != nil {
				writeError(w, err)
				return
			}
			mb.Devices = append(mb.Devices, id)
		}
		c.Mailboxes = append(c.Mailboxes, mb)
	}
	if rl := in.Relay; rl != nil {
		c.Relay = &mailgw.RelayConfig{Host: rl.Host, Port: rl.Port, Username: rl.Username, Password: rl.Password, Mode: rl.Mode, SPFInclude: rl.SPFInclude}
	}
	if err := s.app.MailGW().SetConfig(c); err != nil {
		writeError(w, errCode("invalid", err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, s.mailgwView())
}

func (s *Server) handleMailGWDNS(w http.ResponseWriter, r *http.Request) {
	v, err := s.app.MailGW().DNS(r.Context())
	if err != nil {
		writeError(w, errCode("invalid", err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, v)
}

func (s *Server) handleMailGWQueue(w http.ResponseWriter, r *http.Request) {
	q := s.app.MailGW().Queue()
	writeJSON(w, http.StatusOK, map[string]any{"items": q})
}

func (s *Server) handleMailGWRetry(w http.ResponseWriter, r *http.Request) {
	s.app.MailGW().RetryNow()
	ok(w)
}

func (s *Server) handleMailGWCancel(w http.ResponseWriter, r *http.Request) {
	if !s.app.MailGW().Cancel(r.PathValue("id")) {
		writeError(w, errCode("notfound", "no such letter in the queue"))
		return
	}
	ok(w)
}
