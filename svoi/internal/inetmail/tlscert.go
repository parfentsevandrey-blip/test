package inetmail

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"errors"
	"math/big"
	"os"
	"path/filepath"
	"time"
)

// Mail servers talk TLS to each other without checking certificates (there is no one to ask which name a mail server
// should have: the MX record says it, and the MX record is as trustworthy as the DNS), so the certificate of the gateway
// is one that it makes for itself. A real one (from a certificate authority) can be put in its place with the same
// two files.

// LoadOrCreateCert returns the certificate of the mail host kept in dir (mail-cert.pem, mail-key.pem), making a new one
// when there is none, when it is for another name or when it is about to expire.
func LoadOrCreateCert(dir, host string) (tls.Certificate, error) {
	certPath, keyPath := filepath.Join(dir, "mail-cert.pem"), filepath.Join(dir, "mail-key.pem")
	if c, err := tls.LoadX509KeyPair(certPath, keyPath); err == nil && len(c.Certificate) > 0 {
		if leaf, err := x509.ParseCertificate(c.Certificate[0]); err == nil &&
			time.Until(leaf.NotAfter) > 30*24*time.Hour && leaf.VerifyHostname(host) == nil {
			return c, nil
		}
	}
	certPEM, keyPEM, err := SelfSignedCert(host)
	if err != nil {
		return tls.Certificate{}, err
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return tls.Certificate{}, err
	}
	if err := os.WriteFile(keyPath, keyPEM, 0o600); err != nil {
		return tls.Certificate{}, err
	}
	if err := os.WriteFile(certPath, certPEM, 0o644); err != nil {
		return tls.Certificate{}, err
	}
	return tls.X509KeyPair(certPEM, keyPEM)
}

// SelfSignedCert makes a certificate (P-256, five years) for host.
func SelfSignedCert(host string) (certPEM, keyPEM []byte, err error) {
	host = fqdn(host)
	if host == "" {
		return nil, nil, errors.New("the mail host has no name")
	}
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, nil, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 100))
	if err != nil {
		return nil, nil, err
	}
	tpl := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: host, Organization: []string{"The Mesh"}},
		DNSNames:     []string{host},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(5 * 365 * 24 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, &key.PublicKey, key)
	if err != nil {
		return nil, nil, err
	}
	kb, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return nil, nil, err
	}
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}),
		pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: kb}), nil
}
