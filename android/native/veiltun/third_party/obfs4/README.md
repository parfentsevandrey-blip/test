# Vendored obfs4

A copy of `github.com/refraction-networking/obfs4` v0.1.2 (BSD-2, copyright
Yawning Angel and the Refraction Networking authors); `LICENSE` here is theirs.

It is present for one reason. Conjure's dialer reaches this library through
Conjure's transport registry, and this application also carries the Tor
Project's lyrebird, which contains its own descendant of the same original
obfs4proxy code. Both register a command-line flag named `obfs4-distBias` in a
package initialiser. Registering one flag name twice panics, package
initialisers run when the library is loaded, and on Android that means the app
dies at launch — whether or not anybody has selected Conjure.

The single change from upstream is the removal of that flag registration in
`transports/obfs4/obfs4.go`. The variable it set keeps its default, which is the
same default upstream has; nothing on any path this app takes would read it from
a command line, because an Android library has none.

Only the packages Conjure actually reaches are copied. The `obfs4proxy`
executable, the deprecated obfs2, obfs3 and ScrambleSuit transports, and the
tests are not.
