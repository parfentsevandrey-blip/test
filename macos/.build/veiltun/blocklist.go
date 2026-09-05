package veiltun

// The ad blocker, which is a list of names that DNS answers "no such name"
// for, before the query ever reaches the tunnel.
//
// Every name an application resolves passes through this file's resolver —
// that is what stops DNS from leaking to the local network — so it is also the
// natural place to refuse the names that advertising and tracking come from.
// Refusing them here is worth more than it would be on a normal connection:
// every request an advertisement would have made is one that is not paid for
// over a path that runs through a volunteer's browser at a hundred kilobytes a
// second, and a page that loads a dozen trackers over Tor is a page that
// takes ten seconds instead of two.
//
// The list ships in the APK — a snapshot of Steven Black's unified hosts
// list, some eighty thousand names — because nothing here may depend on a
// server, and a list fetched at runtime on a censored network is a list that
// is usually not there. A name is blocked if it, or any parent of it, is on
// the list; the answer is NXDOMAIN, which applications treat as "there is no
// such host" and give up on at once rather than retrying elsewhere.

import (
	"bufio"
	"os"
	"strings"
)

type blocklist struct {
	names map[string]struct{}
}

// loadBlocklist reads a list of names, one per line. Lines in hosts-file form
// ("0.0.0.0 name") are accepted too, so an unprocessed list works.
func loadBlocklist(path string) (*blocklist, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	b := &blocklist{names: make(map[string]struct{}, 100000)}
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 64*1024), 64*1024)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || line[0] == '#' {
			continue
		}
		fields := strings.Fields(line)
		name := fields[0]
		if len(fields) >= 2 && (fields[0] == "0.0.0.0" || fields[0] == "127.0.0.1") {
			name = fields[1]
		}
		name = strings.ToLower(strings.TrimSuffix(name, "."))
		if name == "" || name == "localhost" || name == "0.0.0.0" || !strings.Contains(name, ".") {
			continue
		}
		b.names[name] = struct{}{}
	}
	return b, scanner.Err()
}

func (b *blocklist) size() int {
	if b == nil {
		return 0
	}
	return len(b.names)
}

// blocked reports whether a name, or any parent domain of it, is listed.
func (b *blocklist) blocked(name string) bool {
	if b == nil || len(b.names) == 0 {
		return false
	}
	name = strings.ToLower(strings.TrimSuffix(name, "."))
	for name != "" {
		if _, ok := b.names[name]; ok {
			return true
		}
		dot := strings.IndexByte(name, '.')
		if dot < 0 {
			return false
		}
		name = name[dot+1:]
		// Never block a bare top-level domain, whatever the list says.
		if !strings.Contains(name, ".") {
			return false
		}
	}
	return false
}

// nxDomain answers a query with "no such name": the header, the question
// copied back, and nothing else.
func nxDomain(query []byte) []byte {
	if len(query) < 12 {
		return nil
	}
	end, ok := skipName(query, 12)
	if !ok || end+4 > len(query) {
		return nil
	}
	end += 4 // QTYPE and QCLASS
	resp := make([]byte, end)
	copy(resp, query[:end])
	resp[2] = 0x81 | (query[2] & 0x01) // QR=1, opcode 0, RD as asked
	resp[3] = 0x83                     // RA=1, RCODE=3 (NXDOMAIN)
	resp[4], resp[5] = 0, 1            // one question
	resp[6], resp[7] = 0, 0
	resp[8], resp[9] = 0, 0
	resp[10], resp[11] = 0, 0
	return resp
}
