# Native build for the TUN -> SOCKS5 bridge (hev-socks5-tunnel, vendored with the
# Opal patch in patches/). Invoked by AGP through externalNativeBuild.ndkBuild.
TOP_LOCAL_PATH := $(call my-dir)
include $(TOP_LOCAL_PATH)/hev-socks5-tunnel/Android.mk
