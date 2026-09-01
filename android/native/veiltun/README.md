# veiltun

The native half of the tunnel: it takes the file descriptor that Android's
`VpnService` hands out and terminates TCP, UDP and DNS on it in userspace, then
re-originates the payload through a SOCKS5 proxy.

It exists because Android gives a VPN app a raw IP stream and nothing else.
Something has to speak TCP to the applications on the device, and tor speaks
SOCKS5, so a userspace TCP/IP stack sits between the two. gVisor's netstack,
wrapped by `xjasonlyu/tun2socks`, does the stack; the rest of this module is the
policy that a circumvention tool needs and a generic tun2socks does not:

* **UDP is dropped by default.** Tor carries TCP only. Letting UDP out would be
  both a leak and a fingerprint, so non-DNS UDP is discarded at the edge and
  counted. When an engine that does support UDP is used instead, the same code
  path relays it over a SOCKS5 `UDP ASSOCIATE`, implemented by hand because
  `golang.org/x/net/proxy` only does `CONNECT`.
* **DNS never leaves as plain UDP.** Queries are answered through tor's own
  `DNSPort` on loopback, or over TCP or RFC 8484 DoH carried by the proxy. All
  three work directly on the DNS wire format, so nothing here parses DNS.
* **Per-flow SOCKS credentials.** Tor's `IsolateSOCKSAuth` gives a separate
  circuit per credential pair, so the isolation policy the user picks in the app
  is expressed as a choice of what to derive those credentials from.

## Building

Requires Go 1.26+ and the Android NDK.

```bash
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/28.2.13676358
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
gomobile bind -target=android/arm64,android/arm,android/amd64 -androidapi 24 \
  -javapkg=app.veil.tun -ldflags="-s -w" -o ../../app/libs/veiltun.aar .
```

The resulting `veiltun.aar` is committed so the Android project builds without
a Go toolchain.

## Проверка на десктопе

`cmd/ptlab` поднимает те же транспорты через тот же контроллер, но на обычном
Linux, и печатает порты, на которых они слушают:

```
go run ./cmd/ptlab obfs4 snowflake
# PORT obfs4 40407
# PORT snowflake 40053
```

Дальше на них можно натравить настоящий tor с такой же строкой
`ClientTransportPlugin <name> socks5 127.0.0.1:<порт>`, какую пишет приложение,
и посмотреть на bootstrap через управляющий порт. Именно так проверялось, что
внешний SOCKS5-плагин и переключение маршрута по `SETCONF` работают: без этого
единственным способом отличить свою ошибку от блокировки остаётся телефон в
цензурируемой сети.

В сборку для Android этот пакет не попадает — `gomobile bind` собирает пакет
`veiltun`, а не `./...`.
