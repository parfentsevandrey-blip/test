# Veil — build helpers. Requires macOS with Xcode 26; `xcodegen` (brew install xcodegen).
SHELL := /bin/bash
TOR_DIR := build/tor
APP := build/Veil.app
VERSION := $(shell sed -n 's/.*MARKETING_VERSION: "\(.*\)"/\1/p' project.yml)
DMG := build/Veil-$(VERSION).dmg
XCODEBUILD_FLAGS := -project Veil.xcodeproj -scheme Veil -configuration Release \
	-derivedDataPath build/DerivedData ONLY_ACTIVE_ARCH=NO ARCHS="arm64 x86_64" \
	CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""

.PHONY: all tor xcodeproj app test dmg run icon clean

all: dmg

## Download the Tor Expert Bundle (arm64 + x86_64) and merge it into universal binaries.
tor: $(TOR_DIR)/tor

$(TOR_DIR)/tor: scripts/fetch-tor.sh TOR_VERSION
	scripts/fetch-tor.sh $(TOR_DIR)

## Generate Veil.xcodeproj from project.yml.
xcodeproj: Veil.xcodeproj/project.pbxproj

Veil.xcodeproj/project.pbxproj: project.yml
	xcodegen generate

## Build the universal, unsigned app and embed Tor into it.
app: xcodeproj tor
	mkdir -p build
	set -o pipefail; xcodebuild $(XCODEBUILD_FLAGS) build 2>&1 | tee build/xcodebuild.log | grep -E --line-buffered 'error:|warning: .*Veil/|BUILD (SUCCEEDED|FAILED)'
	rm -rf $(APP)
	cp -R build/DerivedData/Build/Products/Release/Veil.app $(APP)
	scripts/embed-tor.sh $(TOR_DIR) $(APP)

## Run the unit tests (Debug build, hosted by the app).
test: xcodeproj tor
	mkdir -p build
	set -o pipefail; xcodebuild test -project Veil.xcodeproj -scheme Veil -destination 'platform=macOS' \
		-derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" 2>&1 \
		| tee build/xcodebuild-test.log | grep -E --line-buffered 'error:|warning: .*Veil/|Test Case .* (passed|failed)|Executed|TEST (SUCCEEDED|FAILED)|BUILD FAILED'

## Ad-hoc sign the app and wrap it into a DMG.
dmg: app
	scripts/package-dmg.sh $(APP) $(DMG)

## Run the UI from the Swift package (demo mode unless build/tor exists).
run:
	swift run Veil

## Regenerate the app icon PNGs.
icon:
	python3 scripts/make-icon.py

clean:
	rm -rf build Veil.xcodeproj .build
