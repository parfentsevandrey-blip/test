APP_OPTIM := release
APP_CFLAGS := -O2 -fvisibility=hidden -ffunction-sections -fdata-sections
APP_LDFLAGS := -Wl,--gc-sections -Wl,--build-id=sha1
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
APP_STRIP_MODE := --strip-unneeded
