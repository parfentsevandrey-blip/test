//go:build !unix

package files

// oNonblock: there are no FIFOs to wait on here.
const oNonblock = 0
