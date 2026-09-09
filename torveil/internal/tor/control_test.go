package tor

import (
	"strings"
	"testing"
)

func TestSplitQuoted(t *testing.T) {
	tests := []struct {
		in   string
		want []string
	}{
		{`NOTICE BOOTSTRAP PROGRESS=25 TAG=enough_dirinfo SUMMARY="Loading networkstatus"`,
			[]string{"NOTICE", "BOOTSTRAP", "PROGRESS=25", "TAG=enough_dirinfo", `SUMMARY="Loading networkstatus"`}},
		{`AUTH METHODS=COOKIE,SAFECOOKIE COOKIEFILE="C:\\Users\\a b\\control_auth_cookie"`,
			[]string{"AUTH", "METHODS=COOKIE,SAFECOOKIE", `COOKIEFILE="C:\\Users\\a b\\control_auth_cookie"`}},
		{"", nil},
		{"   ", nil},
	}
	for _, tc := range tests {
		got := splitQuoted(tc.in)
		if len(got) != len(tc.want) {
			t.Fatalf("splitQuoted(%q) = %q, want %q", tc.in, got, tc.want)
		}
		for i := range got {
			if got[i] != tc.want[i] {
				t.Errorf("splitQuoted(%q)[%d] = %q, want %q", tc.in, i, got[i], tc.want[i])
			}
		}
	}
}

func TestUnquote(t *testing.T) {
	tests := map[string]string{
		`"Loading networkstatus"`: "Loading networkstatus",
		`"C:\\Tor\\data"`:         `C:\Tor\data`,
		`"line\nbreak"`:           "line\nbreak",
		`bare`:                    "bare",
		`"unterminated`:           `"unterminated`,
		`"say \"hi\""`:            `say "hi"`,
	}
	for in, want := range tests {
		if got := unquote(in); got != want {
			t.Errorf("unquote(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestReplyText(t *testing.T) {
	r := &Reply{Lines: []ReplyLine{
		{Code: "250", Text: "ns/all=", Data: "r Nick abc def 2024-01-01 00:00:00 1.2.3.4 9001 0"},
		{Code: "250", Text: "OK"},
	}}
	if !r.IsOK() {
		t.Fatal("expected reply to be OK")
	}
	if !strings.Contains(r.Text(), "1.2.3.4") {
		t.Errorf("Text() lost the data payload: %q", r.Text())
	}
}

func TestParseBridgeLine(t *testing.T) {
	tests := []struct {
		name      string
		line      string
		transport string
		address   string
		fpr       string
		wantErr   bool
	}{
		{
			name:      "snowflake with fingerprint argument",
			line:      "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://example.invalid/",
			transport: "snowflake",
			address:   "192.0.2.3:80",
			fpr:       "2B280B23E1107BB62ABFC40DDCC8824814F80A72",
		},
		{
			name:      "obfs4 with cert",
			line:      "obfs4 198.51.100.7:9443 8DDE47B3E8B0F4C2A0F4C3D3B1E4A5F60718293A cert=abc iat-mode=0",
			transport: "obfs4",
			address:   "198.51.100.7:9443",
			fpr:       "8DDE47B3E8B0F4C2A0F4C3D3B1E4A5F60718293A",
		},
		{
			name:      "plain bridge without transport",
			line:      "203.0.113.9:443 8DDE47B3E8B0F4C2A0F4C3D3B1E4A5F60718293A",
			transport: "",
			address:   "203.0.113.9:443",
			fpr:       "8DDE47B3E8B0F4C2A0F4C3D3B1E4A5F60718293A",
		},
		{
			name:      "lowercase fingerprint is normalised",
			line:      "obfs4 198.51.100.7:9443 8dde47b3e8b0f4c2a0f4c3d3b1e4a5f60718293a cert=x",
			transport: "obfs4",
			address:   "198.51.100.7:9443",
			fpr:       "8DDE47B3E8B0F4C2A0F4C3D3B1E4A5F60718293A",
		},
		{name: "empty", line: "   ", wantErr: true},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			b, err := ParseBridgeLine(tc.line)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("expected an error for %q", tc.line)
				}
				return
			}
			if err != nil {
				t.Fatalf("ParseBridgeLine(%q): %v", tc.line, err)
			}
			if b.Transport != tc.transport {
				t.Errorf("transport = %q, want %q", b.Transport, tc.transport)
			}
			if b.Address != tc.address {
				t.Errorf("address = %q, want %q", b.Address, tc.address)
			}
			if b.Fingerprint != tc.fpr {
				t.Errorf("fingerprint = %q, want %q", b.Fingerprint, tc.fpr)
			}
		})
	}
}

// TestDefaultSnowflakeBridgesParse guards against a typo in the shipped bridge
// lines, which would otherwise only surface as a failure to connect.
func TestDefaultSnowflakeBridgesParse(t *testing.T) {
	if len(DefaultSnowflakeBridges) == 0 {
		t.Fatal("no default snowflake bridges are configured")
	}
	for _, line := range DefaultSnowflakeBridges {
		b, err := ParseBridgeLine(line)
		if err != nil {
			t.Fatalf("default bridge line does not parse: %v", err)
		}
		if b.Transport != "snowflake" {
			t.Errorf("transport = %q, want snowflake", b.Transport)
		}
		if b.Fingerprint == "" {
			t.Errorf("default bridge %q has no fingerprint; the circuit builder needs it as hop 1", b.Address)
		}
		if !strings.Contains(line, "url=") {
			t.Errorf("default bridge %q has no broker url", b.Address)
		}
	}
}
