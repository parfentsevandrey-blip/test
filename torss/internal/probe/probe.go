// Package probe checks that the tunnel really carries traffic, through the
// local SOCKS proxy (so it tests exactly the path applications use).
package probe

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	"golang.org/x/net/proxy"
)

// Result of a single probe.
type Result struct {
	URL     string
	Status  int
	IsTor   bool // only meaningful for check.torproject.org
	Elapsed time.Duration
	Err     error
}

// Client probes through a SOCKS5 proxy.
type Client struct {
	socksAddr string
	timeout   time.Duration
}

// New creates a probe client that dials through socksAddr (host:port).
func New(socksAddr string, timeout time.Duration) *Client {
	return &Client{socksAddr: socksAddr, timeout: timeout}
}

func (c *Client) httpClient() (*http.Client, error) {
	d, err := proxy.SOCKS5("tcp", c.socksAddr, nil, &net.Dialer{Timeout: 5 * time.Second})
	if err != nil {
		return nil, err
	}
	cd, ok := d.(proxy.ContextDialer)
	if !ok {
		return nil, fmt.Errorf("socks dialer lacks context support")
	}
	tr := &http.Transport{
		DialContext:           cd.DialContext,
		TLSClientConfig:       &tls.Config{MinVersion: tls.VersionTLS12},
		TLSHandshakeTimeout:   c.timeout,
		ResponseHeaderTimeout: c.timeout,
		DisableKeepAlives:     true,
		ForceAttemptHTTP2:     true,
		Proxy:                 nil, // never use the system proxy for probes
	}
	return &http.Client{Transport: tr, Timeout: c.timeout}, nil
}

// Fetch requests url through the proxy.
func (c *Client) Fetch(ctx context.Context, url string) Result {
	start := time.Now()
	r := Result{URL: url}
	client, err := c.httpClient()
	if err != nil {
		r.Err = err
		return r
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		r.Err = err
		return r
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36")
	resp, err := client.Do(req)
	r.Elapsed = time.Since(start)
	if err != nil {
		r.Err = err
		return r
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64*1024))
	r.Status = resp.StatusCode
	if strings.Contains(url, "check.torproject.org") {
		r.IsTor = strings.Contains(strings.ReplaceAll(string(body), " ", ""), `"IsTor":true`)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 400 {
		r.Err = fmt.Errorf("http %d", resp.StatusCode)
	}
	return r
}

// Check tries urls in order and returns nil as soon as one works. When
// wantTor is set, a check.torproject.org answer must say IsTor=true.
func (c *Client) Check(ctx context.Context, urls []string, wantTor bool) (Result, error) {
	var last Result
	for _, u := range urls {
		if ctx.Err() != nil {
			return last, ctx.Err()
		}
		r := c.Fetch(ctx, u)
		last = r
		if r.Err != nil {
			continue
		}
		if wantTor && strings.Contains(u, "check.torproject.org") && !r.IsTor {
			r.Err = fmt.Errorf("check.torproject.org says we are NOT on Tor")
			last = r
			continue
		}
		return r, nil
	}
	if last.Err == nil {
		last.Err = fmt.Errorf("no probe URLs")
	}
	return last, last.Err
}
