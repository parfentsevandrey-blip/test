MODULE   := github.com/parfentsevandrey-blip/test/svoi
GO       ?= go
DIST     ?= dist

# A tagged commit (v1.2.3) puts its version into the binary; other builds keep
# the default from internal/mesh/node.go.
VERSION  ?= $(shell git describe --tags --exact-match 2>/dev/null | sed 's/^v//')
LDFLAGS  := -s -w
ifneq ($(VERSION),)
LDFLAGS  += -X $(MODULE)/internal/mesh.Version=$(VERSION)
endif

# os/arch pairs built by `make dist`.
PLATFORMS := linux/amd64 linux/arm64 linux/arm linux/riscv64 \
             darwin/amd64 darwin/arm64 \
             windows/amd64 windows/arm64 \
             freebsd/amd64 android/arm64

export CGO_ENABLED = 0

.PHONY: all build run demo test race vet fmt check lab dist install clean

all: build

# The single self-contained binary (the web interface is embedded in it).
build:
	$(GO) build -trimpath -ldflags "$(LDFLAGS)" -o svoi ./cmd/svoi

run: build
	./svoi up

# Four simulated devices in one process: the quickest way to look around.
demo: build
	./svoi demo

test:
	$(GO) test ./... -count=1

race:
	CGO_ENABLED=1 $(GO) test ./... -race -count=1

vet:
	$(GO) vet ./...
	$(GO) vet -tags lab ./lab

fmt:
	@test -z "$$(gofmt -l .)" || { echo "gofmt needed:"; gofmt -l .; exit 1; }

# What CI runs.
check: fmt vet race

# Real NAT behaviour in network namespaces (Linux, needs root, iptables,
# conntrack and iproute2). See lab/natlab.sh.
lab:
	sudo -E $(GO) test -tags lab ./lab -count=1 -v -timeout 600s

# Release archives for every platform in $(PLATFORMS), with checksums.
dist:
	rm -rf $(DIST) && mkdir -p $(DIST)
	@set -e; for p in $(PLATFORMS); do \
		os=$${p%/*}; arch=$${p#*/}; \
		ext=""; [ $$os = windows ] && ext=".exe"; \
		name=svoi-$$os-$$arch; \
		echo "  $$name"; \
		mkdir -p $(DIST)/$$name; \
		GOOS=$$os GOARCH=$$arch $(GO) build -trimpath -ldflags "$(LDFLAGS)" -o $(DIST)/$$name/svoi$$ext ./cmd/svoi; \
		cp README.md $(DIST)/$$name/; \
		if [ $$os = windows ]; then (cd $(DIST) && zip -qr $$name.zip $$name); \
		else tar -C $(DIST) -czf $(DIST)/$$name.tar.gz $$name; fi; \
		rm -rf $(DIST)/$$name; \
	done
	cd $(DIST) && sha256sum * > SHA256SUMS

install: build
	install -Dm755 svoi $(DESTDIR)/usr/local/bin/svoi

clean:
	rm -rf svoi $(DIST)
