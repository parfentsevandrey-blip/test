// Package conjurereg carries Conjure's client-side registrars.
//
// Vendored from github.com/refraction-networking/conjure (BSD-3, copyright the
// Refraction Networking authors); the LICENSE file in this directory is theirs.
// Four files are copied verbatim apart from the package name: the API,
// AMP-cache and DNS registrars, and their shared configuration.
//
// The fifth file of the upstream package, a seventeen-line wrapper around the
// decoy registrar, is deliberately not copied. That wrapper imports Conjure's
// transport registry, the registry imports a second and forked copy of obfs4,
// and both copies of obfs4 register a command-line flag named
// `obfs4-distBias` in their package initialiser. Two registrations of one flag
// name panic, and package initialisers run as the library loads — so on Android
// the app would die on launch whether or not anyone had asked for Conjure.
//
// Leaving out the one registrar this app does not use removes the registry, the
// duplicate obfs4, and a forked DTLS library along with it.
package conjurereg
