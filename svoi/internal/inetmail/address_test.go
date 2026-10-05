package inetmail

import (
	"strings"
	"testing"
)

func TestParseAddress(t *testing.T) {
	ok := map[string]Address{
		"alice@gmail.com":                  {Local: "alice", Domain: "gmail.com"},
		"  Alice <Alice@Gmail.COM> ":       {Name: "Alice", Local: "Alice", Domain: "gmail.com"},
		"Андрей <andrey@пример.рф>":        {Name: "Андрей", Local: "andrey", Domain: "xn--e1afmkfd.xn--p1ai"},
		"first.last+tag@sub.example.co.uk": {Local: "first.last+tag", Domain: "sub.example.co.uk"},
		`"Q, Name" <q@example.org>`:        {Name: "Q, Name", Local: "q", Domain: "example.org"},
	}
	for in, want := range ok {
		got, err := ParseAddress(in)
		if err != nil {
			t.Errorf("%q: %v", in, err)
			continue
		}
		if got != want {
			t.Errorf("%q: got %+v, want %+v", in, got, want)
		}
	}
	bad := []string{
		"", "alice", "alice@", "@gmail.com", "alice@localhost", "alice@[1.2.3.4]", "alice@1.2.3.4",
		`"quoted local"@example.com`, "a..b@example.com", ".a@example.com", "a.@example.com", "a@-bad.com",
		"a@bad-.com", "a@exa mple.com", "a@example.123", "алиса@example.com", strings.Repeat("a", 65) + "@example.com",
		"a@" + strings.Repeat("b", 64) + ".com", "a b@example.com", "a@example..com",
	}
	for _, in := range bad {
		if a, err := ParseAddress(in); err == nil {
			t.Errorf("%q should be refused, got %+v", in, a)
		}
	}
}

func TestParseAddressList(t *testing.T) {
	l, err := ParseAddressList(`a@example.com, "B, B" <b@example.org>; `)
	if err == nil {
		t.Fatalf("a semicolon is not a separator, got %+v", l)
	}
	l, err = ParseAddressList(`a@example.com, "B, B" <b@example.org>`)
	if err != nil || len(l) != 2 || l[1].Name != "B, B" || l[1].Domain != "example.org" {
		t.Fatalf("got %+v, %v", l, err)
	}
	if l, err := ParseAddressList("  "); err != nil || l != nil {
		t.Fatalf("empty list: %+v %v", l, err)
	}
	if _, err := ParseAddressList("a@example.com, nonsense"); err == nil {
		t.Fatal("a list with a bad address must be refused as a whole")
	}
}

func TestAddressHeader(t *testing.T) {
	a := Address{Name: "Андрей", Local: "andrey", Domain: "example.org"}
	if h := a.Header(); !strings.Contains(h, "=?utf-8?") || !strings.HasSuffix(h, "<andrey@example.org>") {
		t.Errorf("a name with non-ASCII letters must be encoded: %q", h)
	}
	if h := (Address{Local: "x", Domain: "example.org"}).Header(); h != "<x@example.org>" {
		t.Errorf("no name: %q", h)
	}
	if !(Address{Local: "Alice", Domain: "example.org"}).Same(Address{Local: "alice", Domain: "example.org"}) {
		t.Error("the local part is compared without regard to case")
	}
}

func TestCleanMailboxName(t *testing.T) {
	for in, want := range map[string]string{"Andrey": "andrey", " a.b-c_d ": "a.b-c_d", "x1": "x1"} {
		if got, err := CleanMailboxName(in); err != nil || got != want {
			t.Errorf("%q: %q %v", in, got, err)
		}
	}
	for _, in := range []string{"", "a", "-a", "a-", "a..b", "Postmaster", "root", "а", "a b", "a@b", strings.Repeat("a", 33)} {
		if got, err := CleanMailboxName(in); err == nil {
			t.Errorf("%q should be refused, got %q", in, got)
		}
	}
}
