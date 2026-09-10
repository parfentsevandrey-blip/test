//go:build windows && !production

package main

// Wails only links its real application implementation when the "production"
// build tag is set. Without it, wails.Run() falls through to a stub that opens
// an error dialog ("Wails applications will not build without the correct
// build tags") instead of a window.
//
// That is a runtime failure, not a build failure, so an untagged build looks
// like it worked and only breaks on the user's machine. Referencing an
// undefined identifier here turns it into a compile error that names the fix.
var _ = TorVeil_must_be_built_with_tags_production___use_build_build_sh_or_go_build_tags_production
